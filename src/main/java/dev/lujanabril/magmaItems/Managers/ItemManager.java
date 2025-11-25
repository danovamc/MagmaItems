package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import net.advancedplugins.ae.api.AEAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class ItemManager {
    private final Main plugin;
    private final NamespacedKey key;
    private final NamespacedKey totemUsesKey;
    private final NamespacedKey uniqueIdKey;
    private final NamespacedKey autosellToggleKey;
    private final Map<String, ItemData> itemDataCache = new HashMap<>();
    private final File itemsFolder;

    public ItemManager(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "magma_item");
        this.uniqueIdKey = new NamespacedKey(plugin, "magma_item_id");
        this.itemsFolder = new File(plugin.getDataFolder(), "Items");
        this.totemUsesKey = new NamespacedKey(plugin, "totem_uses");
        this.autosellToggleKey = new NamespacedKey(plugin, "magma_autosell_toggle");

        if (!this.itemsFolder.exists()) {
            this.itemsFolder.mkdirs();
        }

        this.loadItems();
    }

    private void loadItems() {
        this.itemDataCache.clear();
        this.loadItemsFromMainConfig();
        this.loadItemsFromFolder();
        this.plugin.getLogger().info("Cargados " + this.itemDataCache.size() + " items en total.");
    }

    private void loadItemsFromMainConfig() {
        ConfigurationSection itemsSection = this.plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            this.plugin.getLogger().info("Loading items from main config.yml");
            int count = this.loadItemsFromSection(itemsSection);
            this.plugin.getLogger().info("Loaded " + count + " items from main config.yml");
        }
    }

    private void loadItemsFromFolder() {
        if (this.itemsFolder.exists() && this.itemsFolder.isDirectory()) {
            File[] files = this.itemsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files != null && files.length != 0) {
                for (File file : files) {
                    try {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        ConfigurationSection itemsSection = config.getConfigurationSection("items");
                        if (itemsSection != null) {
                            this.plugin.getLogger().info("Cargando items del " + file.getName());
                            int count = this.loadItemsFromSection(itemsSection);
                            this.plugin.getLogger().info("Cargados " + count + " items del " + file.getName());
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.SEVERE, "Error loading items from " + file.getName(), e);
                    }
                }
            } else {
                this.plugin.getLogger().info("No item files found in Items folder");
            }
        }
    }

    private int loadItemsFromSection(ConfigurationSection itemsSection) {
        int loadedCount = 0;

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(itemId);
            if (section != null) {
                try {
                    Material material = Material.valueOf(section.getString("type", "STONE"));
                    ItemStack item = new ItemStack(material);
                    ItemMeta meta = item.getItemMeta();
                    String rawName = section.getString("name", itemId);
                    String nameWithReset = "<!italic>" + rawName;
                    Component parsedName = MiniMessage.miniMessage().deserialize(nameWithReset);
                    meta.displayName(parsedName);

                    List<Component> originalCustomLore = new ArrayList<>();
                    int totemUses = section.getInt("totem-uses", 0);

                    for (String line : section.getStringList("lore")) {
                        String lineWithReset = "<!italic>" + line;
                        if (totemUses > 0) {
                            lineWithReset = lineWithReset.replace("{uses}", String.valueOf(totemUses));
                        }
                        originalCustomLore.add(MiniMessage.miniMessage().deserialize(lineWithReset));
                    }

                    this.applyLeatherColor(meta, section, material, itemId);
                    this.applyArmorTrim(meta, section, material, itemId);
                    this.applyItemFlags(meta, section, itemId);
                    this.applyUnbreakable(meta, section, itemId);
                    meta.addItemFlags(ItemFlag.values());
                    item.setItemMeta(meta);

                    // Atributos
                    ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
                    if (attributesSection != null) {
                        for (String attributeKey : attributesSection.getKeys(false)) {
                            ConfigurationSection attributeConfig = attributesSection.getConfigurationSection(attributeKey);
                            if (attributeConfig != null) {
                                try {
                                    String attributeName = attributeConfig.getString("attribute");
                                    if (attributeName != null) {
                                        Attribute attribute = Attribute.valueOf(attributeName);
                                        double amount = attributeConfig.getDouble("amount", 0.0);
                                        String operation = attributeConfig.getString("operation", "ADD_NUMBER");
                                        AttributeModifier.Operation op = Operation.valueOf(operation);
                                        String slotName = attributeConfig.getString("slot", "HAND");
                                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName);
                                        UUID uuid = UUID.randomUUID();
                                        AttributeModifier modifier = new AttributeModifier(uuid, "magmaitem_" + itemId + "_" + attributeKey, amount, op, slot);
                                        meta.addAttributeModifier(attribute, modifier);
                                    }
                                } catch (IllegalArgumentException e) {
                                    this.plugin.getLogger().warning("Invalid attribute configuration for item " + itemId + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                    item.setItemMeta(meta);

                    // Encantamientos
                    for (String enchantStr : section.getStringList("enchants")) {
                        if (enchantStr.startsWith("*")) {
                            enchantStr = enchantStr.substring(1).trim();
                        }
                        String[] parts = enchantStr.split(";", 2);
                        if (parts.length >= 1) {
                            String enchantName = parts[0].trim();
                            int level = 1;
                            try {
                                level = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                            } catch (NumberFormatException ignored) {}

                            String bukkitEnchantName = enchantName.toUpperCase();
                            Enchantment bukkitEnchant = Enchantment.getByName(bukkitEnchantName);

                            if (bukkitEnchant != null) {
                                try {
                                    item.addUnsafeEnchantment(bukkitEnchant, level);
                                    meta = item.getItemMeta();
                                } catch (Exception e) {
                                    this.plugin.getLogger().warning("Error applying VANILLA enchantment: " + enchantName);
                                }
                            } else {
                                if (this.plugin.isAdvancedEnchantmentsLoaded()) {
                                    try {
                                        ItemStack originalItem = item.clone();
                                        // Mantenemos 'false' por si acaso AE respeta la opción, aunque no lo haga siempre.
                                        item = AEAPI.applyEnchant(enchantName, level, originalItem, false);
                                        meta = item.getItemMeta();
                                    } catch (Exception e) {
                                        this.plugin.getLogger().warning("Error applying CUSTOM enchant: " + enchantName);
                                    }
                                }
                            }
                        }
                    }
                    meta = item.getItemMeta();

                    // --- ARREGLO LORE ---
                    // Ignoramos completamente el lore que tenga el ítem en este punto (ej. basura de AE)
                    // y forzamos ÚNICAMENTE el lore que cargamos desde la config.
                    meta.lore(originalCustomLore);
                    // --------------------

                    boolean shouldGlow = section.getBoolean("glow", false);
                    if (shouldGlow) {
                        if (meta.getEnchants().isEmpty()) {
                            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        }
                    }
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                    boolean droppable = section.getBoolean("droppable", true);
                    boolean keepOnDeath = section.getBoolean("keep-on-death", false);
                    boolean interactable = section.getBoolean("interactable", true);
                    List<String> totemEffects = section.getStringList("totem-effects");

                    meta.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, itemId);
                    if (totemUses > 0) {
                        meta.getPersistentDataContainer().set(this.totemUsesKey, PersistentDataType.INTEGER, totemUses);
                    }
                    item.setItemMeta(meta);

                    // Acciones
                    List<Action> actions = new ArrayList<>();
                    for (String action : section.getStringList("actions")) {
                        String[] parts = action.split(" ", 2);
                        Action parsedAction = new Action(parts[0], parts.length > 1 ? parts[1] : "");
                        actions.add(parsedAction);
                    }

                    List<Action> shiftClickActions = new ArrayList<>();
                    for (String action : section.getStringList("shift-actions")) {
                        String[] parts = action.split(" ", 2);
                        Action parsedAction = new Action(parts[0], parts.length > 1 ? parts[1] : "");
                        shiftClickActions.add(parsedAction);
                    }

                    boolean requireConfirmation = section.getBoolean("requireConfirmation", false);

                    boolean autoUpdate = section.getBoolean("auto-update", false);

                    this.itemDataCache.put(itemId, new ItemData(item, actions, shiftClickActions, requireConfirmation, droppable, keepOnDeath, interactable, totemUses, totemEffects, autoUpdate));
                    loadedCount++;
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Error loading item " + itemId, e);
                }
            }
        }
        return loadedCount;
    }

    public ItemStack updateItem(ItemStack oldItem) {
        if (oldItem == null || !isMagmaItem(oldItem)) return null;

        String itemId = getItemId(oldItem);
        ItemData data = itemDataCache.get(itemId);

        if (data == null || !data.isAutoUpdate()) return null;

        ItemStack newItem = createItem(itemId);
        if (newItem == null) return null;

        ItemMeta oldMeta = oldItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();
        PersistentDataContainer oldPDC = oldMeta.getPersistentDataContainer();
        PersistentDataContainer newPDC = newMeta.getPersistentDataContainer();

        // 1. Restaurar ID Único
        String uniqueId = null;
        if (oldPDC.has(this.uniqueIdKey, PersistentDataType.STRING)) {
            uniqueId = oldPDC.get(this.uniqueIdKey, PersistentDataType.STRING);
            newPDC.set(this.uniqueIdKey, PersistentDataType.STRING, uniqueId);
        }

        // 2. Restaurar Usos
        int currentUses = 0;
        if (oldPDC.has(this.totemUsesKey, PersistentDataType.INTEGER)) {
            currentUses = oldPDC.get(this.totemUsesKey, PersistentDataType.INTEGER);
            newPDC.set(this.totemUsesKey, PersistentDataType.INTEGER, currentUses);
        }

        // 3. Restaurar Autosell
        if (oldPDC.has(this.autosellToggleKey, PersistentDataType.BYTE)) {
            newPDC.set(this.autosellToggleKey, PersistentDataType.BYTE, oldPDC.get(this.autosellToggleKey, PersistentDataType.BYTE));
        }

        // 4. Restaurar Dueño
        List<Component> newLore = newMeta.hasLore() ? newMeta.lore() : new ArrayList<>();
        List<Component> updatedLore = new ArrayList<>();

        String originalOwner = "Desconocido";

        if (uniqueId != null && plugin.getItemTrackingManager() != null) {
            var info = plugin.getItemTrackingManager().getItemInfo(uniqueId);
            if (info != null && info.getOriginalOwnerName() != null) {
                originalOwner = info.getOriginalOwnerName();
            }
        }

        if (originalOwner.equals("Desconocido")) {
            originalOwner = extractOwnerFromLoreLegacy(oldMeta);
        }

        for (Component line : newLore) {
            String text = plugin.getMiniMessage().serialize(line);

            text = text.replace("%player%", originalOwner)
                    .replace("%original_owner%", originalOwner);

            if (currentUses > 0) {
                text = text.replace("{uses}", String.valueOf(currentUses));
            }

            updatedLore.add(plugin.getMiniMessage().deserialize(text));
        }

        // 5. Restaurar ID visual al final (CORREGIDO PARA NO USAR CURSIVA)
        if (uniqueId != null) {
            // Usamos MiniMessage para aplicar <!italic> explícitamente
            String idLine = "<!italic><dark_gray>[ID-" + uniqueId + "]";
            updatedLore.add(plugin.getMiniMessage().deserialize(idLine));
        }

        newMeta.lore(updatedLore);
        newItem.setItemMeta(newMeta);

        // 6. Verificar cambios
        if (oldItem.isSimilar(newItem)) {
            return null;
        }

        List<String> oldLoreStr = oldMeta.hasLore() ? oldMeta.getLore() : new ArrayList<>();
        List<String> newLoreStr = newMeta.getLore();
        if (oldLoreStr.equals(newLoreStr) && oldItem.getType() == newItem.getType()) {
            return null;
        }

        return newItem;
    }

    private String extractOwnerFromLoreLegacy(ItemMeta meta) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (int i = 0; i < lore.size(); i++) {
                String line = ChatColor.stripColor(lore.get(i));

                if (line.contains("Dueño")) {
                    String sameLine = line.replace("Dueño", "").replace(":", "").trim();
                    if (!sameLine.isEmpty()) {
                        return sameLine;
                    }

                    if (i + 1 < lore.size()) {
                        String nextLine = ChatColor.stripColor(lore.get(i + 1));
                        return nextLine.replace("✎", "")
                                .replace("▎", "")
                                .trim();
                    }
                }
            }
        }
        return "Desconocido";
    }

    private void applyUnbreakable(ItemMeta meta, ConfigurationSection section, String itemId) {
        if (section.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }
    }

    private void applyLeatherColor(ItemMeta meta, ConfigurationSection section, Material material, String itemId) {
        if (meta instanceof LeatherArmorMeta leatherMeta && isLeatherArmor(material)) {
            String colorString = section.getString("color");
            if (colorString != null) {
                Color color = parseColorString(colorString);
                if (color != null) {
                    leatherMeta.setColor(color);
                    return;
                }
            }
            ConfigurationSection colorSection = section.getConfigurationSection("color");
            if (colorSection != null) {
                int red = Math.max(0, Math.min(255, colorSection.getInt("red", 0)));
                int green = Math.max(0, Math.min(255, colorSection.getInt("green", 0)));
                int blue = Math.max(0, Math.min(255, colorSection.getInt("blue", 0)));
                leatherMeta.setColor(Color.fromRGB(red, green, blue));
            }
        }
    }

    private void applyItemFlags(ItemMeta meta, ConfigurationSection section, String itemId) {
        List<String> itemFlags = section.getStringList("item-flags");
        for (String flagName : itemFlags) {
            try {
                if (flagName.equalsIgnoreCase("ANY") || flagName.equalsIgnoreCase("ALL")) {
                    meta.addItemFlags(ItemFlag.values());
                    break;
                }
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid ItemFlag: " + flagName);
            }
        }
    }

    private boolean isLeatherArmor(Material material) {
        return material == Material.LEATHER_HELMET || material == Material.LEATHER_CHESTPLATE ||
                material == Material.LEATHER_LEGGINGS || material == Material.LEATHER_BOOTS;
    }

    private Color parseColorString(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) return null;
        colorString = colorString.trim();
        try {
            if (colorString.matches("\\d+,\\d+,\\d+")) {
                String[] rgb = colorString.split(",");
                return Color.fromRGB(
                        Math.min(255, Integer.parseInt(rgb[0].trim())),
                        Math.min(255, Integer.parseInt(rgb[1].trim())),
                        Math.min(255, Integer.parseInt(rgb[2].trim()))
                );
            } else if (colorString.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(colorString.substring(1), 16));
            }
        } catch (Exception e) { return null; }
        return null;
    }

    private void applyArmorTrim(ItemMeta meta, ConfigurationSection section, Material material, String itemId) {
        if (meta instanceof ArmorMeta armorMeta && isArmorItem(material)) {
            ConfigurationSection trimSection = section.getConfigurationSection("trim");
            if (trimSection != null) {
                String patternKey = trimSection.getString("pattern");
                String materialKey = trimSection.getString("material");
                if (patternKey != null && materialKey != null) {
                    try {
                        TrimPattern pattern = getTrimPattern(patternKey);
                        TrimMaterial trimMat = getTrimMaterial(materialKey);
                        if (pattern != null && trimMat != null) {
                            armorMeta.setTrim(new ArmorTrim(trimMat, pattern));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid trim configuration for item " + itemId);
                    }
                }
            }
        }
    }

    private boolean isArmorItem(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private TrimPattern getTrimPattern(String key) {
        for (TrimPattern p : Registry.TRIM_PATTERN) if (p.getKey().getKey().equalsIgnoreCase(key)) return p;
        return null;
    }

    private TrimMaterial getTrimMaterial(String key) {
        for (TrimMaterial m : Registry.TRIM_MATERIAL) if (m.getKey().getKey().equalsIgnoreCase(key)) return m;
        return null;
    }

    public ItemStack createItem(String itemId) {
        ItemData data = itemDataCache.get(itemId);
        if (data == null) return null;

        ItemStack item = data.item.clone();
        if (data.getInitialTotemUses() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(totemUsesKey, PersistentDataType.INTEGER, data.getInitialTotemUses());
                List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
                List<Component> finalLore = new ArrayList<>();
                for (Component c : lore) {
                    String text = plugin.getMiniMessage().serialize(c);
                    if (text.contains("{uses}")) {
                        finalLore.add(plugin.getMiniMessage().deserialize(text.replace("{uses}", String.valueOf(data.getInitialTotemUses()))));
                    } else {
                        finalLore.add(c);
                    }
                }
                meta.lore(finalLore);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public List<Action> getActions(String itemId) {
        ItemData data = itemDataCache.get(itemId);
        return data != null ? data.actions : new ArrayList<>();
    }

    public List<Action> getShiftClickActions(String itemId) {
        ItemData data = itemDataCache.get(itemId);
        return data != null ? data.shiftClickActions : new ArrayList<>();
    }

    public boolean requiresConfirmation(String itemId) {
        ItemData data = itemDataCache.get(itemId);
        return data != null && data.requireConfirmation;
    }

    public boolean isMagmaItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public String getItemId(ItemStack item) {
        return isMagmaItem(item) ? item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING) : null;
    }

    public boolean isDroppable(ItemStack item) {
        if (!isMagmaItem(item)) return true;
        ItemData data = itemDataCache.get(getItemId(item));
        return data != null ? data.isDroppable() : true;
    }

    public boolean shouldKeepOnDeath(ItemStack item) {
        if (!isMagmaItem(item)) return false;
        ItemData data = itemDataCache.get(getItemId(item));
        return data != null ? data.shouldKeepOnDeath() : false;
    }

    public boolean isInteractable(ItemStack item) {
        if (!isMagmaItem(item)) return true;
        ItemData data = itemDataCache.get(getItemId(item));
        return data != null ? data.isInteractable() : true;
    }

    public void reload() {
        loadItems();
    }

    public List<String> getAllItemIds() {
        return new ArrayList<>(itemDataCache.keySet());
    }

    public List<String> getTotemEffects(String itemId) {
        ItemData data = itemDataCache.get(itemId);
        return data != null ? data.getTotemEffects() : new ArrayList<>();
    }

    public NamespacedKey getAutosellToggleKey() {
        return autosellToggleKey;
    }

    public static class ItemData {
        private final ItemStack item;
        private final List<Action> actions;
        private final List<Action> shiftClickActions;
        private final boolean requireConfirmation;
        private final boolean droppable;
        private final boolean keepOnDeath;
        private final boolean interactable;
        private final int initialTotemUses;
        private final List<String> totemEffects;
        private final boolean autoUpdate;

        public ItemData(ItemStack item, List<Action> actions, List<Action> shiftClickActions, boolean requireConfirmation,
                        boolean droppable, boolean keepOnDeath, boolean interactable, int initialTotemUses,
                        List<String> totemEffects, boolean autoUpdate) {
            this.item = item;
            this.actions = actions;
            this.shiftClickActions = shiftClickActions;
            this.requireConfirmation = requireConfirmation;
            this.droppable = droppable;
            this.keepOnDeath = keepOnDeath;
            this.interactable = interactable;
            this.initialTotemUses = initialTotemUses;
            this.totemEffects = totemEffects;
            this.autoUpdate = autoUpdate;
        }

        public boolean isDroppable() { return droppable; }
        public boolean shouldKeepOnDeath() { return keepOnDeath; }
        public boolean isInteractable() { return interactable; }
        public int getInitialTotemUses() { return initialTotemUses; }
        public List<String> getTotemEffects() { return totemEffects; }
        public boolean isAutoUpdate() { return autoUpdate; }
    }

    public static class Action {
        private final String type;
        private final String rawValue;
        private String rawTitle = "";
        private Component parsedValue;
        private String rawSubtitle = "";
        private Component parsedSubtitle;
        private int fadeIn = 10;
        private int stay = 20;
        private int fadeOut = 10;
        private List<int[]> colors = new ArrayList<>();
        private int[] fadeColor = null;
        private String fireworkType = "STAR";
        private int power = 1;
        private boolean hasTrail = false;

        public Action(String type, String value) {
            this.type = type;
            this.rawValue = value;
            if (type.equals("[MESSAGE]") || type.equals("[ACTIONBAR]")) {
                this.parsedValue = MiniMessage.miniMessage().deserialize(value);
            }

            if (type.equals("[TITLE]")) {
                String[] parts = value.split(";");
                if (parts.length > 0) {
                    this.rawTitle = parts[0];
                    this.parsedValue = !rawTitle.isEmpty() ? MiniMessage.miniMessage().deserialize(rawTitle) : Component.empty();
                }
                if (parts.length > 1) {
                    this.rawSubtitle = parts[1];
                    this.parsedSubtitle = !rawSubtitle.isEmpty() ? MiniMessage.miniMessage().deserialize(rawSubtitle) : Component.empty();
                }
                if (parts.length > 2) try { this.fadeIn = Integer.parseInt(parts[2]); } catch (Exception e) {}
                if (parts.length > 3) try { this.stay = Integer.parseInt(parts[3]); } catch (Exception e) {}
                if (parts.length > 4) try { this.fadeOut = Integer.parseInt(parts[4]); } catch (Exception e) {}
            }

            if (type.equals("[FIREWORK]")) {
                String[] parts = value.split(";");
                if (parts.length > 0) {
                    for (String c : parts[0].split("\\|")) colors.add(parseColor(c));
                }
                if (parts.length > 1 && !parts[1].isEmpty()) fadeColor = parseColor(parts[1]);
                if (parts.length > 2) fireworkType = parts[2].toUpperCase();
                if (parts.length > 3) try { power = Integer.parseInt(parts[3]); } catch (Exception e) {}
                if (parts.length > 4) hasTrail = Boolean.parseBoolean(parts[4]);
            }
        }

        private int[] parseColor(String s) {
            try {
                String[] rgb = s.split(",");
                return new int[]{Integer.parseInt(rgb[0].trim()), Integer.parseInt(rgb[1].trim()), Integer.parseInt(rgb[2].trim())};
            } catch (Exception e) { return new int[]{255, 255, 255}; }
        }

        public String getType() { return type; }
        public String getRawValue() { return rawValue; }
        public String getRawTitle() { return rawTitle; }
        public Component getParsedValue() { return parsedValue; }
        public String getRawSubtitle() { return rawSubtitle; }
        public Component getParsedSubtitle() { return parsedSubtitle; }
        public int getFadeIn() { return fadeIn; }
        public int getStay() { return stay; }
        public int getFadeOut() { return fadeOut; }
        public List<int[]> getColors() { return colors; }
        public int[] getFadeColor() { return fadeColor; }
        public String getFireworkType() { return fireworkType; }
        public int getPower() { return power; }
        public boolean hasTrail() { return hasTrail; }
    }
}