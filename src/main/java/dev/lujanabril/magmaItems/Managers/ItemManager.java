package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import net.advancedplugins.ae.api.AEAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class ItemManager {
    private final Main plugin;
    private final NamespacedKey key;
    private final NamespacedKey totemUsesKey;
    private final Map<String, ItemData> itemDataCache = new HashMap();
    private final File itemsFolder;

    public ItemManager(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "magma_item");
        this.itemsFolder = new File(plugin.getDataFolder(), "Items");
        this.totemUsesKey = new NamespacedKey(plugin, "totem_uses");
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
                for(File file : files) {
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

        for(String itemId : itemsSection.getKeys(false)) {
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

                    // --- 1. CAPTURAR EL LORE CUSTOM ORIGINAL PERO NO APLICARLO AÚN ---
                    List<Component> originalCustomLore = new ArrayList();
                    for(String line : section.getStringList("lore")) {
                        String lineWithReset = "<!italic>" + line;
                        originalCustomLore.add(MiniMessage.miniMessage().deserialize(lineWithReset));
                    }

                    // *** SE ELIMINA el meta.lore(lore) INICIAL aquí ***

                    this.applyLeatherColor(meta, section, material, itemId);
                    this.applyArmorTrim(meta, section, material, itemId);
                    this.applyItemFlags(meta, section, itemId);
                    this.applyUnbreakable(meta, section, itemId);
                    meta.addItemFlags(ItemFlag.values());
                    item.setItemMeta(meta);

                    // --- LÓGICA DE ATTRIBUTES ---
                    ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
                    if (attributesSection != null) {
                        for(String attributeKey : attributesSection.getKeys(false)) {
                            ConfigurationSection attributeConfig = attributesSection.getConfigurationSection(attributeKey);
                            if (attributeConfig != null) {
                                try {
                                    String attributeName = attributeConfig.getString("attribute");
                                    if (attributeName != null) {
                                        Attribute attribute = Attribute.valueOf(attributeName);
                                        double amount = attributeConfig.getDouble("amount", (double)0.0F);
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

                    // --- INICIO: LÓGICA DE ENCHANTMENTS (VANILLA Y AE) ---
                    for(String enchantStr : section.getStringList("enchants")) {
                        if (enchantStr.startsWith("*")) {
                            enchantStr = enchantStr.substring(1).trim();
                        }

                        String[] parts = enchantStr.split(";");
                        if (parts.length >= 1) {
                            String enchantName = parts[0].trim();
                            int level = 1;

                            try {
                                level = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                            } catch (NumberFormatException ignored) {}

                            String bukkitEnchantName = enchantName.toUpperCase();
                            boolean appliedSuccessfully = false;

                            // 1. INTENTAR CON BUKKIT (VANILLA)
                            try {
                                Enchantment enchant = Enchantment.getByName(bukkitEnchantName);
                                if (enchant != null) {
                                    // 🟢 CAMBIO CLAVE: Usar addUnsafeEnchantment para altos niveles
                                    item.addUnsafeEnchantment(enchant, level);

                                    // Sincronizar 'meta' para reflejar los encantamientos aplicados
                                    meta = item.getItemMeta();
                                    appliedSuccessfully = true;
                                }
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Error applying vanilla enchantment via Bukkit: " + enchantName);
                            }

                            // 2. FALLBACK: INTENTAR CON ADVANCEDENCHANTMENTS (CUSTOM)
                            if (!appliedSuccessfully) {
                                if (this.plugin.isAdvancedEnchantmentsLoaded()) {
                                    try {
                                        ItemStack originalItem = item.clone();

                                        // Aplicar el encantamiento custom, indicando TRUE para ocultar el LORE.
                                        item = AEAPI.applyEnchant(enchantName, level, originalItem, true);

                                        if (!originalItem.equals(item)) {
                                            // AE lo aplicó. Sincronizamos 'meta' para capturar sus metadatos.
                                            meta = item.getItemMeta();
                                            appliedSuccessfully = true;

                                        } else {
                                            this.plugin.getLogger().warning("AE API failed to apply: " + enchantName);
                                        }

                                    } catch (Exception e) {
                                        this.plugin.getLogger().warning("Error applying AdvancedEnchantment '" + enchantName + "': " + e.getMessage());
                                    }
                                } else {
                                    if (Enchantment.getByName(bukkitEnchantName) == null) {
                                        this.plugin.getLogger().warning("Invalid custom enchantment: " + enchantName + " (AdvancedEnchantments not loaded)");
                                    }
                                }
                            }
                        }
                    }

                    meta = item.getItemMeta(); // Sincronización final antes del manejo de lore/glow

                    // --- 2. LIMPIEZA AGRESIVA Y RECONSTRUCCIÓN DEL LORE ---

                    // 1. OBTENEMOS EL LORE RESIDUAL (Si hay, de atributos u otras cosas)
                    List<Component> residualLore = meta.hasLore() ? meta.lore() : new ArrayList<>();

                    // 2. ELIMINAR CUALQUIER LORE (Reset Agresivo)
                    meta.setLore(new ArrayList<>()); // Borrar todo el lore de la meta
                    item.setItemMeta(meta); // Forzar la aplicación del meta sin lore
                    meta = item.getItemMeta(); // Volver a sincronizar

                    // 3. RE-LIMPIAR EL LORE DE AE (Seguridad)
                    if (this.plugin.isAdvancedEnchantmentsLoaded()) {
                        List<String> aeEnchants = section.getStringList("enchants").stream()
                                .filter(e -> !e.startsWith("*"))
                                .toList();

                        if (!aeEnchants.isEmpty()) {
                            List<Component> currentLore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                            for (String enchantStr : aeEnchants) {
                                String cleanName = enchantStr.split(";")[0].trim().toLowerCase();
                                currentLore.removeIf(component -> {
                                    String plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
                                    return plainText.toLowerCase().contains(cleanName.replace(" ", ""));
                                });
                            }
                            meta.lore(currentLore);
                        }
                    }

                    // 4. CONSTRUCCIÓN DEL LORE FINAL
                    List<Component> finalLore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    finalLore.addAll(originalCustomLore);
                    meta.lore(finalLore);

                    // 🟢 LÓGICA DE GLOW (Brillo de ítem)
                    // El glow ahora debería funcionar porque los encantamientos Vanilla se aplicaron con item.addUnsafeEnchantment()
                    boolean shouldGlow = section.getBoolean("glow", false);
                    if (shouldGlow) {
                        // Si el ítem debe brillar y no tiene encantamientos APLICADOS (incluyendo los de AE)
                        if (meta.getEnchants().isEmpty()) {
                            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        }
                    }

                    // OCULTAR LORE NATIVO DE ENCHANTMENTS y otras flags
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                    // PROPIEDADES FINALES Y PDC
                    boolean droppable = section.getBoolean("droppable", true);
                    boolean keepOnDeath = section.getBoolean("keep-on-death", false);
                    boolean interactable = section.getBoolean("interactable", true);

                    // <<<< LECTURA DE CAMPOS TOTEM >>>>
                    int totemUses = section.getInt("totem-uses", 0);
                    List<String> totemEffects = section.getStringList("totem-effects");
                    // <<<< FIN LECTURA >>>>

                    // APLICAR EL IDENTIFICADOR MAGMA_ITEM (PDC)
                    meta.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, itemId);

                    // ESTABLECER LA META UNA SOLA VEZ AL FINAL DE TODA LA LÓGICA
                    item.setItemMeta(meta);

                    // --- RESTO DEL CÓDIGO (ACTIONS) ---

                    List<Action> actions = new ArrayList();

                    for(String action : section.getStringList("actions")) {
                        String[] parts = action.split(" ", 2);
                        String type = parts[0];
                        String value = parts.length > 1 ? parts[1] : "";
                        Action parsedAction = new Action(type, value);
                        actions.add(parsedAction);
                    }

                    List<Action> shiftClickActions = new ArrayList();

                    for(String action : section.getStringList("shift-actions")) {
                        String[] parts = action.split(" ", 2);
                        String type = parts[0];
                        String value = parts.length > 1 ? parts[1] : "";
                        Action parsedAction = new Action(type, value);
                        shiftClickActions.add(parsedAction);
                    }

                    boolean requireConfirmation = section.getBoolean("requireConfirmation", false);

                    // MODIFICAR CONSTRUCTOR DE ITEMDATA
                    this.itemDataCache.put(itemId, new ItemData(item, actions, shiftClickActions, requireConfirmation, droppable, keepOnDeath, interactable, totemUses, totemEffects));
                    ++loadedCount;
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Error loading item " + itemId, e);
                }
            }
        }

        return loadedCount;
    }

    private void applyUnbreakable(ItemMeta meta, ConfigurationSection section, String itemId) {
        boolean unbreakable = section.getBoolean("unbreakable", false);
        if (unbreakable) {
            meta.setUnbreakable(true);
        }

    }

    private void applyLeatherColor(ItemMeta meta, ConfigurationSection section, Material material, String itemId) {
        if (meta instanceof LeatherArmorMeta leatherMeta && this.isLeatherArmor(material)) {
            String colorString = section.getString("color");
            if (colorString != null) {
                Color color = this.parseColorString(colorString);
                if (color != null) {
                    leatherMeta.setColor(color);
                    return;
                }
            }

            ConfigurationSection colorSection = section.getConfigurationSection("color");
            if (colorSection != null) {
                int red = colorSection.getInt("red", 0);
                int green = colorSection.getInt("green", 0);
                int blue = colorSection.getInt("blue", 0);
                red = Math.max(0, Math.min(255, red));
                green = Math.max(0, Math.min(255, green));
                blue = Math.max(0, Math.min(255, blue));
                Color color = Color.fromRGB(red, green, blue);
                leatherMeta.setColor(color);
            }

        }
    }

    private void applyItemFlags(ItemMeta meta, ConfigurationSection section, String itemId) {
        List<String> itemFlags = section.getStringList("item-flags");
        if (!itemFlags.isEmpty()) {
            for(String flagName : itemFlags) {
                try {
                    if (flagName.equalsIgnoreCase("ANY") || flagName.equalsIgnoreCase("ALL")) {
                        meta.addItemFlags(ItemFlag.values());
                        break;
                    }

                    ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase());
                    meta.addItemFlags(new ItemFlag[]{flag});
                } catch (IllegalArgumentException var8) {
                    this.plugin.getLogger().warning("Invalid ItemFlag for item " + itemId + ": " + flagName);
                }
            }

        }
    }

    private boolean isLeatherArmor(Material material) {
        return material == Material.LEATHER_HELMET || material == Material.LEATHER_CHESTPLATE || material == Material.LEATHER_LEGGINGS || material == Material.LEATHER_BOOTS;
    }

    private Color parseColorString(String colorString) {
        if (colorString != null && !colorString.trim().isEmpty()) {
            colorString = colorString.trim();
            if (colorString.matches("\\d+,\\d+,\\d+")) {
                try {
                    String[] rgb = colorString.split(",");
                    int red = Math.max(0, Math.min(255, Integer.parseInt(rgb[0].trim())));
                    int green = Math.max(0, Math.min(255, Integer.parseInt(rgb[1].trim())));
                    int blue = Math.max(0, Math.min(255, Integer.parseInt(rgb[2].trim())));
                    return Color.fromRGB(red, green, blue);
                } catch (NumberFormatException var6) {
                    return null;
                }
            } else if (colorString.startsWith("#") && colorString.length() == 7) {
                try {
                    int rgb = Integer.parseInt(colorString.substring(1), 16);
                    return Color.fromRGB(rgb);
                } catch (NumberFormatException var7) {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void applyArmorTrim(ItemMeta meta, ConfigurationSection section, Material material, String itemId) {
        if (meta instanceof ArmorMeta armorMeta && this.isArmorItem(material)) {
            ConfigurationSection trimSection = section.getConfigurationSection("trim");
            if (trimSection != null) {
                String patternKey = trimSection.getString("pattern");
                String materialKey = trimSection.getString("material");
                if (patternKey != null && materialKey != null) {
                    try {
                        TrimPattern pattern = this.getTrimPattern(patternKey);
                        if (pattern == null) {
                            this.plugin.getLogger().warning("trim invalido para item " + itemId + ": " + patternKey);
                            return;
                        }

                        TrimMaterial trimMaterial = this.getTrimMaterial(materialKey);
                        if (trimMaterial == null) {
                            this.plugin.getLogger().warning("trim invalido para item" + itemId + ": " + materialKey);
                            return;
                        }

                        ArmorTrim armorTrim = new ArmorTrim(trimMaterial, pattern);
                        armorMeta.setTrim(armorTrim);
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.SEVERE, "trim invalido para item" + itemId, e);
                    }

                } else {
                    this.plugin.getLogger().warning("trim invalido para item " + itemId + " variable faltante");
                }
            }
        }
    }

    private boolean isArmorItem(Material material) {
        return material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE") || material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS");
    }

    private TrimPattern getTrimPattern(String patternKey) {
        for(TrimPattern pattern : Registry.TRIM_PATTERN) {
            if (pattern.getKey().getKey().equalsIgnoreCase(patternKey)) {
                return pattern;
            }
        }

        return null;
    }

    private TrimMaterial getTrimMaterial(String materialKey) {
        for(TrimMaterial material : Registry.TRIM_MATERIAL) {
            if (material.getKey().getKey().equalsIgnoreCase(materialKey)) {
                return material;
            }
        }

        return null;
    }

    public ItemStack createItem(String itemId) {
        ItemData data = (ItemData)this.itemDataCache.get(itemId);
        if (data == null) {
            return null;
        }

        ItemStack item = data.item.clone();

        // LÓGICA DE INICIALIZACIÓN AUTOMÁTICA DEL TOTEM
        if (data.getInitialTotemUses() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {

                // 1. Aplicar NBT de usos
                meta.getPersistentDataContainer().set(this.totemUsesKey, PersistentDataType.INTEGER, data.getInitialTotemUses());

                // 2. Renderizar y aplicar el Lore
                String usesLorePrefixFormat = "<!i><#777777>Usos restantes: <#FFD700>";

                // Obtenemos la línea renderizada usando el método auxiliar de Main
                String renderedUsesLine = plugin.renderMiniMessageToLegacy(usesLorePrefixFormat + data.getInitialTotemUses());

                // Obtener el Lore como Component (moderno)
                List<Component> currentLoreComponents = meta.lore() != null ? meta.lore() : new ArrayList<>();
                List<Component> finalLoreComponents = new ArrayList<>();
                String strippedPrefix = "Usos restantes:";

                // Iterar sobre los Componentes
                for (Component component : currentLoreComponents) {
                    // Convertir el Component a texto plano para la comparación
                    String strippedLine = PlainTextComponentSerializer.plainText().serialize(component);

                    // Omitimos la línea antigua de usos si existe
                    if (!strippedLine.startsWith(strippedPrefix)) {
                        finalLoreComponents.add(component); // Mantiene las líneas originales
                    }
                }

                // Añadimos la nueva línea de usos como Component
                finalLoreComponents.add(plugin.getMiniMessage().deserialize(usesLorePrefixFormat + data.getInitialTotemUses()));

                meta.lore(finalLoreComponents); // Usar meta.lore() (moderno)

                item.setItemMeta(meta);
            }
        }

        return item;
    }

    public List<Action> getActions(String itemId) {
        ItemData data = (ItemData)this.itemDataCache.get(itemId);
        return (List<Action>)(data != null ? data.actions : new ArrayList());
    }

    public List<Action> getShiftClickActions(String itemId) {
        ItemData data = (ItemData)this.itemDataCache.get(itemId);
        return (List<Action>)(data != null ? data.shiftClickActions : new ArrayList());
    }

    public boolean requiresConfirmation(String itemId) {
        ItemData data = (ItemData)this.itemDataCache.get(itemId);
        return data != null && data.requireConfirmation;
    }

    public boolean isMagmaItem(ItemStack item) {
        return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().has(this.key, PersistentDataType.STRING) : false;
    }

    public String getItemId(ItemStack item) {
        return !this.isMagmaItem(item) ? null : (String)item.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
    }

    public boolean isDroppable(ItemStack item) {
        if (!this.isMagmaItem(item)) {
            return true;
        } else {
            String itemId = this.getItemId(item);
            ItemData data = (ItemData)this.itemDataCache.get(itemId);
            return data != null ? data.isDroppable() : true;
        }
    }

    public boolean shouldKeepOnDeath(ItemStack item) {
        if (!this.isMagmaItem(item)) {
            return false;
        } else {
            String itemId = this.getItemId(item);
            ItemData data = (ItemData)this.itemDataCache.get(itemId);
            return data != null ? data.shouldKeepOnDeath() : false;
        }
    }

    public boolean isInteractable(ItemStack item) {
        if (!this.isMagmaItem(item)) {
            return true;
        } else {
            String itemId = this.getItemId(item);
            ItemData data = (ItemData)this.itemDataCache.get(itemId);
            return data != null ? data.isInteractable() : true;
        }
    }

    public void reload() {
        this.loadItems();
    }

    public List<String> getAllItemIds() {
        return new ArrayList(this.itemDataCache.keySet());
    }

    // Método para que el TotemListener obtenga los efectos
    public List<String> getTotemEffects(String itemId) {
        ItemData data = (ItemData)this.itemDataCache.get(itemId);
        return (data != null) ? data.getTotemEffects() : new ArrayList<>();
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

        public ItemData(ItemStack item, List<Action> actions, List<Action> shiftClickActions, boolean requireConfirmation, boolean droppable, boolean keepOnDeath, boolean interactable, int initialTotemUses, List<String> totemEffects) {
            this.item = item;
            this.actions = actions;
            this.shiftClickActions = shiftClickActions;
            this.requireConfirmation = requireConfirmation;
            this.droppable = droppable;
            this.keepOnDeath = keepOnDeath;
            this.interactable = interactable;
            this.initialTotemUses = initialTotemUses;
            this.totemEffects = totemEffects;
        }

        public boolean isDroppable() {
            return this.droppable;
        }

        public boolean shouldKeepOnDeath() {
            return this.keepOnDeath;
        }

        public boolean isInteractable() {
            return this.interactable;
        }

        public int getInitialTotemUses() {
            return this.initialTotemUses;
        }

        public List<String> getTotemEffects() {
            return this.totemEffects;
        }
    }

    public static class Action {
        private final String type;
        private final String rawValue;
        private String rawTitle;
        private Component parsedValue;
        private String rawSubtitle;
        private Component parsedSubtitle;
        private int fadeIn;
        private int stay;
        private int fadeOut;
        private List<int[]> colors;
        private int[] fadeColor;
        private String fireworkType;
        private int power;
        private boolean hasTrail;

        public Action(String type, String value) {
            this.type = type;
            this.rawValue = value;
            this.parsedValue = !type.equals("[MESSAGE]") && !type.equals("[ACTIONBAR]") ? null : MiniMessage.miniMessage().deserialize(value);
            this.rawTitle = "";
            this.rawSubtitle = "";
            this.parsedSubtitle = null;
            this.fadeIn = 10;
            this.stay = 20;
            this.fadeOut = 10;
            this.colors = new ArrayList();
            this.fadeColor = null;
            this.fireworkType = "STAR";
            this.power = 1;
            this.hasTrail = false;
            if (type.equals("[TITLE]")) {
                String[] titleParts = value.split(";");
                this.rawTitle = titleParts.length > 0 ? titleParts[0] : "";
                this.parsedValue = (Component)(!this.rawTitle.isEmpty() ? MiniMessage.miniMessage().deserialize(this.rawTitle) : Component.empty());
                this.rawSubtitle = titleParts.length > 1 ? titleParts[1] : "";
                this.parsedSubtitle = (Component)(!this.rawSubtitle.isEmpty() ? MiniMessage.miniMessage().deserialize(this.rawSubtitle) : Component.empty());
                this.fadeIn = titleParts.length > 2 ? Integer.parseInt(titleParts[2]) : 10;
                this.stay = titleParts.length > 3 ? Integer.parseInt(titleParts[3]) : 20;
                this.fadeOut = titleParts.length > 4 ? Integer.parseInt(titleParts[4]) : 10;
            }

            if (type.equals("[FIREWORK]")) {
                String[] fireworkParts = value.split(";");
                String colorStr = fireworkParts[0];
                String[] colorParts = colorStr.split("\\|");

                for(String colorPart : colorParts) {
                    this.colors.add(this.parseColor(colorPart));
                }

                this.fadeColor = fireworkParts.length > 1 && !fireworkParts[1].isEmpty() ? this.parseColor(fireworkParts[1]) : null;
                this.fireworkType = fireworkParts.length > 2 ? fireworkParts[2].toUpperCase() : "STAR";
                this.power = fireworkParts.length > 3 ? Integer.parseInt(fireworkParts[3]) : 1;
                this.hasTrail = fireworkParts.length > 4 ? Boolean.parseBoolean(fireworkParts[4]) : false;
            }

        }

        private int[] parseColor(String colorStr) {
            try {
                String[] rgb = colorStr.split(",");
                return new int[]{Integer.parseInt(rgb[0].trim()), Integer.parseInt(rgb[1].trim()), Integer.parseInt(rgb[2].trim())};
            } catch (Exception var3) {
                return new int[]{255, 255, 255};
            }
        }

        public String getType() {
            return this.type;
        }

        public String getRawValue() {
            return this.rawValue;
        }

        public String getRawTitle() {
            return this.rawTitle;
        }

        public Component getParsedValue() {
            return this.parsedValue;
        }

        public String getRawSubtitle() {
            return this.rawSubtitle;
        }

        public Component getParsedSubtitle() {
            return this.parsedSubtitle;
        }

        public int getFadeIn() {
            return this.fadeIn;
        }

        public int getStay() {
            return this.stay;
        }

        public int getFadeOut() {
            return this.fadeOut;
        }

        public List<int[]> getColors() {
            return this.colors;
        }

        public int[] getFadeColor() {
            return this.fadeColor;
        }

        public String getFireworkType() {
            return this.fireworkType;
        }

        public int getPower() {
            return this.power;
        }

        public boolean hasTrail() {
            return this.hasTrail;
        }
    }
}