package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import org.bukkit.Bukkit;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ItemTrackingManager {
    private final Main plugin;
    private final File trackingFile;
    private FileConfiguration trackingConfig;
    private final NamespacedKey magmaItemIdKey;
    private final NamespacedKey magmaItemKey;
    private Map<String, String> itemTrackingCache = new HashMap();

    public ItemTrackingManager(Main plugin) {
        this.plugin = plugin;
        this.magmaItemIdKey = new NamespacedKey(plugin, "magma_item_id");
        this.magmaItemKey = new NamespacedKey(plugin, "magma_item");
        this.trackingFile = new File(plugin.getDataFolder(), "item-tracking.yml");
        this.loadTracking();
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
    }

    public void saveTracking() {
        try {
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

    public void registerItem(String uniqueId, String playerUuid, String playerName, String itemName, String originalOwnerFromLore) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());
        String trackingData = originalOwnerFromLore + ";" + itemName + ";" + playerName + ";" + playerUuid + ";" + playerUuid + ";" + timestamp + ";" + timestamp + ";UNKNOWN";
        this.trackingConfig.set("Tracked-Items." + uniqueId, trackingData);
        this.itemTrackingCache.put(uniqueId, trackingData);
        this.saveTracking();
    }

    public void updateItemOwner(String uniqueId, String playerName, String playerUuid) {
        String currentData = (String)this.itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            if (parts.length >= 6) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String updateTimestamp = sdf.format(new Date());
                String newData = parts[0] + ";" + parts[1] + ";" + playerName + ";" + parts[3] + ";" + playerUuid + ";" + parts[5] + ";" + updateTimestamp;

                if (parts.length >= 8) {
                    newData = newData + ";" + parts[7];
                } else if (parts.length >= 7) {
                    newData = newData + ";UNKNOWN";
                } else {
                    newData = newData + ";UNKNOWN";
                }

                this.trackingConfig.set("Tracked-Items." + uniqueId, newData);
                this.itemTrackingCache.put(uniqueId, newData);
                this.saveTracking();
            }
        }

    }

    public void updateItemMaterial(String uniqueId, Material material) {
        String currentData = (String)this.itemTrackingCache.get(uniqueId);
        if (currentData != null) {
            String[] parts = currentData.split(";");
            if (parts.length >= 6) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String updateTimestamp = sdf.format(new Date());
                String newData;

                String materialString = material != null ? material.toString() : "UNKNOWN";

                newData = parts[0] + ";" + parts[1] + ";" + parts[2] + ";" + parts[3] + ";" + parts[4] + ";" + parts[5] + ";" + updateTimestamp + ";" + materialString;

                this.trackingConfig.set("Tracked-Items." + uniqueId, newData);
                this.itemTrackingCache.put(uniqueId, newData);
                this.saveTracking();
            }
        }

    }

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
                info.setCreationTime(parts[5]);

                if (parts.length >= 7) {
                    info.setLastUpdateTime(parts[6]);
                }

                if (parts.length >= 8) {
                    try {
                        info.setItemMaterial(Material.valueOf(parts[7]));
                    } catch (IllegalArgumentException var7) {
                        info.setItemMaterial(Material.PAPER); // Fallback
                    }
                } else if (parts.length == 7) {
                    info.setItemMaterial(Material.PAPER);
                } else {
                    info.setItemMaterial(Material.PAPER);
                }

                return info;
            }
        }
    }

    public boolean idExists(String uniqueId) {
        return this.itemTrackingCache.containsKey(uniqueId);
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

    public void checkAllMagmaItems() {
        this.plugin.getLogger().info("Verificando MagmaItems en el servidor...");
        Map<String, Integer> itemCounts = new HashMap();
        int newItemsRegistered = 0;
        int itemsUpdated = 0;
        int magmaItemsFound = 0;
        int magmaIdItemsFound = 0;

        for(Player player : Bukkit.getOnlinePlayers()) {
            for(ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    if (container.has(this.magmaItemIdKey, PersistentDataType.STRING)) {
                        String itemId = (String)container.get(this.magmaItemIdKey, PersistentDataType.STRING);
                        ++magmaIdItemsFound;
                        this.updateItemMaterial(itemId, item.getType());
                        if (!this.idExists(itemId)) {
                            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().toString();
                            String originalOwner = this.extractOriginalOwnerFromLore(meta);
                            this.registerItem(itemId, player.getUniqueId().toString(), player.getName(), itemName, originalOwner);
                            ++newItemsRegistered;
                        } else {
                            this.updateItemOwner(itemId, player.getName(), player.getUniqueId().toString());
                            ++itemsUpdated;
                        }

                        itemCounts.put(itemId, (Integer)itemCounts.getOrDefault(itemId, 0) + 1);
                    } else if (container.has(this.magmaItemKey, PersistentDataType.STRING)) {
                        ++magmaItemsFound;
                    }
                }
            }
        }

        this.plugin.getLogger().info("Verificación completa: " + newItemsRegistered + " nuevos items registrados, " + itemsUpdated + " items actualizados");
        this.plugin.getLogger().info("Items encontrados: " + magmaIdItemsFound + " con magma_item_id, " + magmaItemsFound);
        if (!itemCounts.isEmpty()) {
            this.checkForDuplicates(itemCounts);
        }

    }

    private void checkForDuplicates(Map<String, Integer> itemCounts) {
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
                player.getInventory().setContents(contents);
                player.updateInventory();
            }
        }

        for(Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                op.sendMessage("§c[MagmaItems] §4¡Alerta! §cSe han detectado §4" + count + "§c items duplicados con ID: §4" + itemId);
            }
        }

    }


    public static class ItemInfo {
        private String itemId;
        private String originalOwnerName;
        private String itemName;
        private String currentOwnerName;
        private String originalOwnerUUID;
        private String currentOwnerUUID;
        private String creationTime;
        private String lastUpdateTime;
        private Material itemMaterial;

        public String getItemId() {
            return this.itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getOriginalOwnerName() {
            return this.originalOwnerName;
        }

        public void setOriginalOwnerName(String originalOwnerName) {
            this.originalOwnerName = originalOwnerName;
        }

        public String getItemName() {
            return this.itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getCurrentOwnerName() {
            return this.currentOwnerName;
        }

        public void setCurrentOwnerName(String currentOwnerName) {
            this.currentOwnerName = currentOwnerName;
        }

        public String getOriginalOwnerUUID() {
            return this.originalOwnerUUID;
        }

        public void setOriginalOwnerUUID(String originalOwnerUUID) {
            this.originalOwnerUUID = originalOwnerUUID;
        }

        public String getCurrentOwnerUUID() {
            return this.currentOwnerUUID;
        }

        public void setCurrentOwnerUUID(String currentOwnerUUID) {
            this.currentOwnerUUID = currentOwnerUUID;
        }

        public String getCreationTime() {
            return this.creationTime;
        }

        public void setCreationTime(String creationTime) {
            this.creationTime = creationTime;
        }

        public String getLastUpdateTime() {
            return this.lastUpdateTime;
        }

        public void setLastUpdateTime(String lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        public Material getItemMaterial() {
            return this.itemMaterial;
        }

        public void setItemMaterial(Material itemMaterial) {
            this.itemMaterial = itemMaterial;
        }
    }
}