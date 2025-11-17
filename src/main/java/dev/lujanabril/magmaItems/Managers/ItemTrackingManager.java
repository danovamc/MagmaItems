package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location; // <-- IMPORTADO
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.ParseException; // <-- IMPORTADO
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class ItemTrackingManager {
    private final Main plugin;
    private final File trackingFile;
    private FileConfiguration trackingConfig;
    private final File deletionLogFile;
    private FileConfiguration deletionLogConfig;
    private final NamespacedKey magmaItemIdKey;
    private final NamespacedKey magmaItemKey;
    private Map<String, String> itemTrackingCache = new ConcurrentHashMap();

    private final File removalListFile;
    private FileConfiguration removalListConfig;
    private Set<String> idsToRemoveCache = new HashSet<>();

    private final File physicalRemovalsLogFile;
    private FileConfiguration physicalRemovalsLogConfig;

    private final MiniMessage miniMessage;

    // --- FORMATO DE DATOS (12 CAMPOS) ---
    // 0:originalOwner; 1:itemName; 2:currentOwner; 3:originalUUID; 4:currentUUID;
    // 5:creationTime(long); 6:lastUpdateTime(long); 7:Material;
    // 8:World; 9:X; 10:Y; 11:Z
    private static final int DATA_FIELDS_COUNT = 12;
    private static final String DEFAULT_WORLD = "world";

    public ItemTrackingManager(Main plugin) {
        this.plugin = plugin;
        this.magmaItemIdKey = new NamespacedKey(plugin, "magma_item_id");
        this.magmaItemKey = new NamespacedKey(plugin, "magma_item");
        this.trackingFile = new File(plugin.getDataFolder(), "item-tracking.yml");
        this.miniMessage = plugin.getMiniMessage();

        File logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        this.deletionLogFile = new File(logsFolder, "deletions_log.yml");

        this.removalListFile = new File(logsFolder, "items_to_remove.yml");
        this.physicalRemovalsLogFile = new File(logsFolder, "physical_removals.log");

        this.loadTracking();
        this.loadDeletionLog();

        this.loadRemovalList();
        this.loadPhysicalRemovalsLog();
    }

    public void loadTracking() {
        if (!this.trackingFile.exists()) {
            try {
                this.trackingFile.createNewFile();
            } catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "No se pudo crear el archivo de seguimiento de items", e);
            }
        }

        this.trackingConfig = YamlConfiguration.loadConfiguration(this.trackingFile);
        this.cacheTrackingData();
    }

    public void reloadTracking() {
        this.loadTracking();
        this.loadDeletionLog();
        this.loadRemovalList();
        this.loadPhysicalRemovalsLog();
    }

    public void loadDeletionLog() {
        if (!this.deletionLogFile.exists()) {
            try {
                this.deletionLogFile.createNewFile();
            } catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "No se pudo crear el archivo de log de eliminaciones", e);
            }
        }
        this.deletionLogConfig = YamlConfiguration.loadConfiguration(this.deletionLogFile);
    }

    public void saveDeletionLog() {
        try {
            this.deletionLogConfig.save(this.deletionLogFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el archivo de log de eliminaciones", e);
        }
    }

    public void loadRemovalList() {
        if (!this.removalListFile.exists()) {
            try {
                this.removalListFile.createNewFile();
                this.removalListConfig = YamlConfiguration.loadConfiguration(this.removalListFile);
                this.removalListConfig.set("ids-to-remove", new ArrayList<String>());
                this.removalListConfig.save(this.removalListFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo crear el archivo de lista de borrado", e);
            }
        }
        this.removalListConfig = YamlConfiguration.loadConfiguration(this.removalListFile);
        this.idsToRemoveCache = new HashSet<>(this.removalListConfig.getStringList("ids-to-remove"));
        plugin.getLogger().info("Cargadas " + idsToRemoveCache.size() + " IDs en la lista de borrado físico.");
    }

    public void saveRemovalList() {
        try {
            this.removalListConfig.set("ids-to-remove", new ArrayList<>(this.idsToRemoveCache));
            this.removalListConfig.save(this.removalListFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el archivo de lista de borrado", e);
        }
    }

    public void addItemToRemovalList(String itemId) {
        if (this.idsToRemoveCache.add(itemId)) {
            saveRemovalList();
            plugin.getLogger().info("ID: " + itemId + " añadida a la lista de borrado físico.");
        }
    }

    public boolean removeItemFromRemovalList(String itemId) {
        if (this.idsToRemoveCache.remove(itemId)) {
            saveRemovalList();
            plugin.getLogger().info("ID: " + itemId + " eliminada de la lista de borrado físico.");
            return true;
        }
        return false;
    }

    public void logBulkDeletion(Player remover, List<ItemInfo> itemsInfo) {
        if (itemsInfo == null || itemsInfo.isEmpty()) return;

        // --- CAMBIO: Usar timestamp numérico ---
        long timestamp = System.currentTimeMillis();
        String removerName = remover.getName();
        String removerUuid = remover.getUniqueId().toString();
        // --- CAMBIO: Usar un formato de fecha estándar para el log ---
        String deletionDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));

        for (ItemInfo itemInfo : itemsInfo) {
            if (itemInfo == null) continue;

            String logKey = "deletions." + timestamp + "_" + itemInfo.getItemId();
            this.deletionLogConfig.set(logKey + ".deleter-name", removerName);
            this.deletionLogConfig.set(logKey + ".deleter-uuid", removerUuid);
            this.deletionLogConfig.set(logKey + ".item-id", itemInfo.getItemId());
            this.deletionLogConfig.set(logKey + ".item-name", itemInfo.getItemName());
            this.deletionLogConfig.set(logKey + ".original-owner", itemInfo.getOriginalOwnerName());
            this.deletionLogConfig.set(logKey + ".last-holder", itemInfo.getCurrentOwnerName());
            this.deletionLogConfig.set(logKey + ".deletion-date", deletionDate);
        }

        this.saveDeletionLog();
    }

    public void removeItemsTracking(List<String> uniqueIds) {
        if (uniqueIds == null || uniqueIds.isEmpty()) return;
        boolean changed = false;

        for (String uniqueId : uniqueIds) {
            if (this.itemTrackingCache.containsKey(uniqueId)) {
                this.trackingConfig.set("Tracked-Items." + uniqueId, null);
                this.itemTrackingCache.remove(uniqueId);
                changed = true;
            }
        }

        if (changed) {
            this.saveTracking();
        }
    }

    public void addItemsToRemovalList(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        boolean changed = false;

        for (String itemId : itemIds) {
            if (this.idsToRemoveCache.add(itemId)) {
                plugin.getLogger().info("ID: " + itemId + " añadida a la lista de borrado físico.");
                changed = true;
            }
        }

        if (changed) {
            saveRemovalList();
        }
    }

    public void loadPhysicalRemovalsLog() {
        if (!this.physicalRemovalsLogFile.exists()) {
            try {
                this.physicalRemovalsLogFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo crear el log de borrados físicos", e);
            }
        }
        this.physicalRemovalsLogConfig = YamlConfiguration.loadConfiguration(this.physicalRemovalsLogFile);
    }

    public void savePhysicalRemovalsLog() {
        try {
            this.physicalRemovalsLogConfig.save(this.physicalRemovalsLogFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el log de borrados físicos", e);
        }
    }

    public void logPhysicalRemoval(Player player, String itemId, String itemName) {
        // --- CAMBIO: Usar timestamp numérico y formato estándar ---
        long timestamp = System.currentTimeMillis();
        String timestampKey = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(timestamp));
        String logKey = "removals." + timestampKey + "_" + player.getName() + "_" + itemId;

        this.physicalRemovalsLogConfig.set(logKey + ".player-name", player.getName());
        this.physicalRemovalsLogConfig.set(logKey + ".player-uuid", player.getUniqueId().toString());
        this.physicalRemovalsLogConfig.set(logKey + ".item-id", itemId);
        this.physicalRemovalsLogConfig.set(logKey + ".item-name", itemName);
        this.physicalRemovalsLogConfig.set(logKey + ".removal-date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp)));

        savePhysicalRemovalsLog();
    }

    public void checkAndRemoveBlacklistedItems() {
        if (idsToRemoveCache.isEmpty()) {
            return;
        }

        int itemsRemoved = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean inventoryChanged = false;

            for (int i = 0; i < contents.length; ++i) {
                ItemStack item = contents[i];
                if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();

                    if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                        String currentId = (String)container.get(this.magmaItemIdKey, PersistentDataType.STRING);

                        if (this.idsToRemoveCache.contains(currentId)) {
                            String itemName = meta.hasDisplayName() ? meta.getDisplayName().toString() : item.getType().toString();
                            logPhysicalRemoval(player, currentId, itemName);

                            contents[i] = null;
                            inventoryChanged = true;
                            itemsRemoved++;
                        }
                    }
                }
            }

            if (inventoryChanged) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().setContents(contents);
                    player.updateInventory();
                    player.sendMessage(this.miniMessage.deserialize("<red>Algunos items en tu inventario han sido eliminados por un administrador."));
                });
            }
        }

        if (itemsRemoved > 0) {
            plugin.getLogger().info("Tarea de borrado completada. Se eliminaron " + itemsRemoved + " items físicos.");
        }
    }


    public void logDeletion(Player remover, ItemInfo itemInfo) {
        if (itemInfo == null) return;

        // --- CAMBIO: Usar timestamp numérico y formato estándar ---
        long timestamp = System.currentTimeMillis();
        String timestampKey = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(timestamp));
        String logKey = timestampKey + "_" + itemInfo.getItemId();

        String path = "deletions." + logKey;
        this.deletionLogConfig.set(path + ".deleter-name", remover.getName());
        this.deletionLogConfig.set(path + ".deleter-uuid", remover.getUniqueId().toString());
        this.deletionLogConfig.set(path + ".item-id", itemInfo.getItemId());
        this.deletionLogConfig.set(path + ".item-name", itemInfo.getItemName());
        this.deletionLogConfig.set(path + ".original-owner", itemInfo.getOriginalOwnerName());
        this.deletionLogConfig.set(path + ".last-holder", itemInfo.getCurrentOwnerName());
        this.deletionLogConfig.set(path + ".deletion-date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp)));

        this.saveDeletionLog();
    }

    public void saveTracking() {
        try {
            for (Map.Entry<String, String> entry : itemTrackingCache.entrySet()) {
                this.trackingConfig.set("Tracked-Items." + entry.getKey(), entry.getValue());
            }
            this.trackingConfig.save(this.trackingFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el archivo de seguimiento de items", e);
        }

    }

    private void cacheTrackingData() {
        this.itemTrackingCache.clear();
        if (this.trackingConfig.contains("Tracked-Items")) {
            for(String id : this.trackingConfig.getConfigurationSection("Tracked-Items").getKeys(false)) {
                this.itemTrackingCache.put(id, this.trackingConfig.getString("Tracked-Items." + id));
            }
        }

    }

    public void removeItemTracking(String uniqueId) {
        if (this.itemTrackingCache.containsKey(uniqueId)) {
            this.trackingConfig.set("Tracked-Items." + uniqueId, null);
            this.itemTrackingCache.remove(uniqueId);
            this.saveTracking();
        }
    }

    public List<String> getAllItemIds() {
        return new ArrayList<>(this.itemTrackingCache.keySet());
    }

    // --- MÉTODO MODIFICADO (para usar timestamp) ---
    public void registerItem(String uniqueId, Player player, String itemName, String originalOwnerFromLore) {
        // --- CAMBIO: Usar timestamp numérico ---
        long timestamp = System.currentTimeMillis();
        Location loc = player.getLocation();
        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();

        String trackingData = String.join(";",
                originalOwnerFromLore, // 0
                itemName,              // 1
                playerName,            // 2
                playerUuid,            // 3
                playerUuid,            // 4
                String.valueOf(timestamp), // 5 (long)
                String.valueOf(timestamp), // 6 (long)
                "UNKNOWN",             // 7
                loc.getWorld().getName(), // 8
                String.valueOf(loc.getX()), // 9
                String.valueOf(loc.getY()), // 10
                String.valueOf(loc.getZ())  // 11
        );

        this.trackingConfig.set("Tracked-Items." + uniqueId, trackingData);
        this.itemTrackingCache.put(uniqueId, trackingData);
        this.saveTracking();
    }

    // --- MÉTODO MODIFICADO (para usar timestamp) ---
    public void updateItemOwner(String uniqueId, String playerName, String playerUuid) {
        String currentData = (String)this.itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            String[] newParts = Arrays.copyOf(parts, DATA_FIELDS_COUNT);

            // --- CAMBIO: Usar timestamp numérico ---
            long updateTimestamp = System.currentTimeMillis();

            newParts[2] = playerName;
            newParts[4] = playerUuid;
            newParts[6] = String.valueOf(updateTimestamp); // Guardar como String

            // Llenar campos faltantes si es un item antiguo (migración)
            if (parts.length < 8) newParts[7] = "UNKNOWN";
            if (parts.length < 9) newParts[8] = DEFAULT_WORLD;
            if (parts.length < 10) newParts[9] = "0.0";
            if (parts.length < 11) newParts[10] = "0.0";
            if (parts.length < 12) newParts[11] = "0.0";

            // Migrar fechas de String a long
            if (parts.length > 5) parseOldDateString(newParts, 5); // creationTime
            if (parts.length > 6) parseOldDateString(newParts, 6); // lastUpdateTime (se sobrescribirá)

            String newData = String.join(";", newParts);

            if (!newData.equals(currentData)) {
                this.itemTrackingCache.put(uniqueId, newData);
            }
        }
    }

    // --- NUEVO MÉTODO HELPER (para migrar fechas) ---
    private void parseOldDateString(String[] parts, int index) {
        try {
            // Comprobar si ya es un long
            Long.parseLong(parts[index]);
        } catch (NumberFormatException e) {
            // No es un long, es una fecha String antigua
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date d = sdf.parse(parts[index]);
                parts[index] = String.valueOf(d.getTime());
            } catch (ParseException ex) {
                // Error de parseo, usar timestamp actual
                parts[index] = String.valueOf(System.currentTimeMillis());
            }
        }
    }

    public void updateAllItemLocations() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
                    PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                    if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                        String itemId = container.get(this.magmaItemIdKey, PersistentDataType.STRING);
                        if (itemId != null) {
                            this.updateItemLocation(itemId, loc);
                        }
                    }
                }
            }
        }
    }

    private void updateItemLocation(String uniqueId, Location loc) {
        String currentData = itemTrackingCache.get(uniqueId);
        if (currentData == null) return;

        String[] parts = currentData.split(";");
        String[] newParts = Arrays.copyOf(parts, DATA_FIELDS_COUNT);

        newParts[8] = loc.getWorld().getName();
        newParts[9] = String.valueOf(loc.getX());
        newParts[10] = String.valueOf(loc.getY());
        newParts[11] = String.valueOf(loc.getZ());

        if (parts.length < 8) newParts[7] = "UNKNOWN";

        // Migrar fechas de String a long
        if (parts.length > 5) parseOldDateString(newParts, 5); // creationTime
        if (parts.length > 6) parseOldDateString(newParts, 6); // lastUpdateTime

        String newData = String.join(";", newParts);

        if (!newData.equals(currentData)) {
            itemTrackingCache.put(uniqueId, newData);
        }
    }

    // --- MÉTODO MODIFICADO (para usar timestamp) ---
    public void updateItemMaterial(String uniqueId, Material material) {
        String currentData = (String)this.itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            String[] newParts = Arrays.copyOf(parts, DATA_FIELDS_COUNT);

            String materialString = material != null ? material.toString() : "UNKNOWN";
            newParts[7] = materialString;

            // Llenar campos faltantes si es un item antiguo (migración)
            if (parts.length < 9) newParts[8] = DEFAULT_WORLD;
            if (parts.length < 10) newParts[9] = "0.0";
            if (parts.length < 11) newParts[10] = "0.0";
            if (parts.length < 12) newParts[11] = "0.0";

            // Migrar fechas de String a long
            if (parts.length > 5) parseOldDateString(newParts, 5); // creationTime
            if (parts.length > 6) parseOldDateString(newParts, 6); // lastUpdateTime

            String newData = String.join(";", newParts);

            if (!newData.equals(currentData)) {
                this.itemTrackingCache.put(uniqueId, newData);
            }
        }
    }

    // --- MÉTODO MODIFICADO (para parsear timestamp) ---
    public ItemInfo getItemInfo(String itemId) {
        String data = (String)this.itemTrackingCache.get(itemId);
        if (data == null) {
            return null;
        } else {
            String[] parts = data.split(";");
            if (parts.length < 6) {
                return null;
            } else {
                ItemInfo info = new ItemInfo();
                info.setItemId(itemId);
                info.setOriginalOwnerName(parts[0]);
                info.setItemName(parts[1]);
                info.setCurrentOwnerName(parts[2]);
                info.setOriginalOwnerUUID(parts[3]);
                info.setCurrentOwnerUUID(parts[4]);

                // --- INICIO: LÓGICA DE MIGRACIÓN DE FECHA ---
                long creationTime;
                try {
                    creationTime = Long.parseLong(parts[5]);
                } catch (NumberFormatException e) {
                    try {
                        creationTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(parts[5]).getTime();
                    } catch (ParseException ex) {
                        creationTime = 0;
                    }
                }
                info.setCreationTime(creationTime);

                long lastUpdateTime;
                if (parts.length >= 7) {
                    try {
                        lastUpdateTime = Long.parseLong(parts[6]);
                    } catch (NumberFormatException e) {
                        try {
                            lastUpdateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(parts[6]).getTime();
                        } catch (ParseException ex) {
                            lastUpdateTime = creationTime;
                        }
                    }
                } else {
                    lastUpdateTime = creationTime;
                }
                info.setLastUpdateTime(lastUpdateTime);
                // --- FIN: LÓGICA DE MIGRACIÓN DE FECHA ---

                if (parts.length >= 8) {
                    try {
                        info.setItemMaterial(Material.valueOf(parts[7]));
                    } catch (IllegalArgumentException var7) {
                        info.setItemMaterial(Material.PAPER);
                    }
                } else {
                    info.setItemMaterial(Material.PAPER);
                }

                if (parts.length >= 12) {
                    info.setWorld(parts[8]);
                    try {
                        info.setX(Double.parseDouble(parts[9]));
                        info.setY(Double.parseDouble(parts[10]));
                        info.setZ(Double.parseDouble(parts[11]));
                    } catch (NumberFormatException e) {
                        info.setX(0); info.setY(0); info.setZ(0);
                    }
                } else {
                    info.setWorld(DEFAULT_WORLD);
                    info.setX(0); info.setY(0); info.setZ(0);
                }

                return info;
            }
        }
    }

    public boolean idExists(String uniqueId) {
        return this.itemTrackingCache.containsKey(uniqueId);
    }

    public boolean idIsBlacklisted(String uniqueId) {
        return this.idsToRemoveCache.contains(uniqueId);
    }

    public List<ItemInfo> getAllTrackedItems() {
        List<ItemInfo> result = new ArrayList();

        for(String itemId : this.itemTrackingCache.keySet()) {
            ItemInfo info = this.getItemInfo(itemId);
            if (info != null) {
                result.add(info);
            }
        }

        return result;
    }

    private String extractOriginalOwnerFromLore(ItemMeta meta) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore.size() >= 4) {
                String loreLine = (String)lore.get(3);
                if (loreLine != null && !loreLine.isEmpty()) {
                    return loreLine.replaceAll("&[0-9a-fk-or]|✎", "").trim();
                }
            }
        }

        return "Desconocido";
    }

    public void checkPlayerMagmaItems(Player player, Map<String, Integer> itemCounts) {
        // Esta tarea también actualiza la ubicación del jugador
        Location playerLocation = player.getLocation();

        for(ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                    String itemId = (String)container.get(this.magmaItemIdKey, PersistentDataType.STRING);

                    if (this.idsToRemoveCache.contains(itemId)) {
                        continue; // Este item será eliminado por la otra tarea
                    }

                    // Actualizar material (ligero)
                    this.updateItemMaterial(itemId, item.getType());

                    // Actualizar dueño y UBICACIÓN (ligero)
                    if (this.idExists(itemId)) {
                        this.updateItemOwner(itemId, player.getName(), player.getUniqueId().toString());
                        this.updateItemLocation(itemId, playerLocation); // <-- ¡AQUÍ ACTUALIZAMOS LA UBICACIÓN!
                    }

                    // Registrar para el conteo de duplicados
                    itemCounts.put(itemId, (Integer)itemCounts.getOrDefault(itemId, 0) + 1);
                }
            }
        }
    }

    /**
     * Revisa el inventario de UN solo jugador en busca de items en lista negra.
     * Esta tarea es SÍNCRONA y se llama desde el round-robin de Main.java.
     */
    public void checkAndRemoveBlacklistedItems(Player player) {
        if (idsToRemoveCache.isEmpty()) {
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        boolean inventoryChanged = false;

        for (int i = 0; i < contents.length; ++i) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();

                if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                    String currentId = (String)container.get(this.magmaItemIdKey, PersistentDataType.STRING);

                    if (this.idsToRemoveCache.contains(currentId)) {
                        String itemName = meta.hasDisplayName() ? meta.getDisplayName().toString() : item.getType().toString();
                        logPhysicalRemoval(player, currentId, itemName);

                        contents[i] = null;
                        inventoryChanged = true;
                    }
                }
            }
        }

        if (inventoryChanged) {
            // Ya estamos en el hilo síncrono, no necesitamos Bukkit.getScheduler().runTask()
            player.getInventory().setContents(contents);
            player.updateInventory();
            player.sendMessage(this.miniMessage.deserialize("<red>Algunos items en tu inventario han sido eliminados por un administrador."));
        }
    }


    // --- AÑADIR ESTE MÉTODO NUEVO (PARA ARREGLAR EL COMANDO) ---
    /**
     * Este método es para el COMANDO /mi checkduplicates.
     * Causa un pico de lag momentáneo, pero es un comando manual de admin.
     * Ejecuta el escaneo de TODOS los jugadores en el hilo principal AHORA.
     */
    public void checkAllMagmaItems() {
        this.plugin.getLogger().info("Iniciando chequeo MANUAL de MagmaItems duplicados...");
        Map<String, Integer> itemCounts = new HashMap<>();

        // Esto es seguro porque el comando /mi checkduplicates lo llama desde una tarea SÍNCRONA
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerMagmaItems(player, itemCounts); // Reutilizamos la lógica de escaneo
        }

        this.plugin.getLogger().info("Chequeo de duplicados manual completado.");
        if (!itemCounts.isEmpty()) {
            this.checkForDuplicates(itemCounts);
        }
    }

    public void checkForDuplicates(Map<String, Integer> itemCounts) {
        int duplicatesFound = 0;

        for(Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if ((Integer)entry.getValue() > 1) {
                Logger var10000 = this.plugin.getLogger();
                String var10001 = (String)entry.getKey();
                var10000.warning("Item duplicado encontrado con ID: " + var10001 + " (Cantidad: " + String.valueOf(entry.getValue()) + ")");
                this.handleDuplicateItem((String)entry.getKey(), (Integer)entry.getValue());
                ++duplicatesFound;
            }
        }

        if (duplicatesFound > 0) {
            this.plugin.getLogger().info("Verificación de duplicados completa: " + duplicatesFound + " items con magma_item_id duplicados encontrados y tratados");
        } else {
            this.plugin.getLogger().info("Verificación de duplicados completa: No se encontraron items con magma_item_id duplicados");
        }

    }

    private void handleDuplicateItem(String itemId, int count) {
        ItemInfo itemInfo = this.getItemInfo(itemId);
        if (itemInfo != null) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = itemInfo.getOriginalOwnerName();
            var10000.warning("Info de item duplicado: Dueño original: " + var10001 + ", Item: " + itemInfo.getItemName() + ", Último dueño: " + itemInfo.getCurrentOwnerName());
        }

        boolean foundFirst = false;

        for(Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean inventoryChanged = false;

            for(int i = 0; i < contents.length; ++i) {
                ItemStack item = contents[i];
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                        String currentId = (String)container.get(this.magmaItemIdKey, PersistentDataType.STRING);
                        if (currentId.equals(itemId)) {
                            if (!foundFirst) {
                                foundFirst = true;
                            } else {
                                contents[i] = null;
                                inventoryChanged = true;
                            }
                        }
                    }
                }
            }

            if (inventoryChanged) {
                final ItemStack[] finalContents = contents;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().setContents(finalContents);
                    player.updateInventory();
                });
            }
        }

        for(Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                op.sendMessage("§c[MagmaItems] §4¡Alerta! §cSe han detectado §4" + count + "§c items duplicados con ID: §4" + itemId);
            }
        }

    }


    // --- CLASE INTERNA MODIFICADA (timestamps son long) ---
    public static class ItemInfo {
        private String itemId;
        private String originalOwnerName;
        private String itemName;
        private String currentOwnerName;
        private String originalOwnerUUID;
        private String currentOwnerUUID;
        // --- CAMBIO: String a long ---
        private long creationTime;
        private long lastUpdateTime;
        // ---
        private Material itemMaterial;
        private String world;
        private double x;
        private double y;
        private double z;

        public String getItemId() { return this.itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public String getOriginalOwnerName() { return this.originalOwnerName; }
        public void setOriginalOwnerName(String originalOwnerName) { this.originalOwnerName = originalOwnerName; }
        public String getItemName() { return this.itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCurrentOwnerName() { return this.currentOwnerName; }
        public void setCurrentOwnerName(String currentOwnerName) { this.currentOwnerName = currentOwnerName; }
        public String getOriginalOwnerUUID() { return this.originalOwnerUUID; }
        public void setOriginalOwnerUUID(String originalOwnerUUID) { this.originalOwnerUUID = originalOwnerUUID; }
        public String getCurrentOwnerUUID() { return this.currentOwnerUUID; }
        public void setCurrentOwnerUUID(String currentOwnerUUID) { this.currentOwnerUUID = currentOwnerUUID; }

        // --- CAMBIO: Getters/Setters para long ---
        public long getCreationTime() { return this.creationTime; }
        public void setCreationTime(long creationTime) { this.creationTime = creationTime; }
        public long getLastUpdateTime() { return this.lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        // ---

        public Material getItemMaterial() { return this.itemMaterial; }
        public void setItemMaterial(Material itemMaterial) { this.itemMaterial = itemMaterial; }
        public String getWorld() { return world; }
        public void setWorld(String world) { this.world = world; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }

        // --- MÉTODO NUEVO: Para calcular distancia ---
        public double distanceTo(Location loc) {
            if (loc == null || !loc.getWorld().getName().equals(this.world)) {
                return Double.MAX_VALUE; // Si están en mundos diferentes, están "infinitamente" lejos
            }
            // Cálculo de distancia simple (pitágoras en 3D)
            return Math.sqrt(
                    Math.pow(this.x - loc.getX(), 2) +
                            Math.pow(this.y - loc.getY(), 2) +
                            Math.pow(this.z - loc.getZ(), 2)
            );
        }
    }

    public Set<String> getIdsToRemoveCache() {
        return this.idsToRemoveCache;
    }
}