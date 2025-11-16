package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class CustomItemManager {
    private final Main plugin;
    private final ItemManager itemManager;
    private final Map<String, CustomItemData> customItemDataCache = new HashMap();
    private final NamespacedKey customItemKey;
    private final File customItemsFolder;

    public CustomItemManager(Main plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.customItemKey = new NamespacedKey(plugin, "magma_custom_item");
        this.customItemsFolder = new File(plugin.getDataFolder(), "Items");
        if (!this.customItemsFolder.exists()) {
            this.customItemsFolder.mkdirs();
        }

        this.loadCustomItems();
    }

    private void loadCustomItems() {
        this.customItemDataCache.clear();
        this.loadCustomItemsFromMainConfig();
        this.loadCustomItemsFromFolder();
        this.plugin.getLogger().info("Loaded " + this.customItemDataCache.size() + " custom items total.");
    }

    private void loadCustomItemsFromMainConfig() {
        ConfigurationSection customItemsSection = this.plugin.getConfig().getConfigurationSection("custom-items");
        if (customItemsSection != null) {
            this.plugin.getLogger().info("Cargando items del main config.yml...");
            int count = this.loadCustomItemsFromSection(customItemsSection);
            this.plugin.getLogger().info("Cargados " + count + " items del config.yml");
        }

    }

    private void loadCustomItemsFromFolder() {
        if (this.customItemsFolder.exists() && this.customItemsFolder.isDirectory()) {
            File[] files = this.customItemsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files != null && files.length != 0) {
                for(File file : files) {
                    try {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        ConfigurationSection customItemsSection = config.getConfigurationSection("custom-items");
                        if (customItemsSection != null) {
                            this.plugin.getLogger().info("Cargando items de " + file.getName());
                            int count = this.loadCustomItemsFromSection(customItemsSection);
                            this.plugin.getLogger().info("Cargados " + count + " items de " + file.getName());
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.SEVERE, "Error cargando items de " + file.getName(), e);
                    }
                }

            } else {
                this.plugin.getLogger().info("[MagmaItems] No se han encontrado items en el folder");
            }
        }
    }

    private int loadCustomItemsFromSection(ConfigurationSection customItemsSection) {
        int loadedCount = 0;

        for(String itemId : customItemsSection.getKeys(false)) {
            ConfigurationSection section = customItemsSection.getConfigurationSection(itemId);
            if (section != null) {
                try {
                    String type = section.getString("type", "DRILL");
                    if ("BOMB".equalsIgnoreCase(type)) {
                        double radius = section.getDouble("radius", (double)5.0F);
                        String shape = section.getString("shape", "SPHERE");
                        int time = section.getInt("time", 20);
                        int cooldown = section.getInt("cooldown", 0);
                        double throwDistance = section.getDouble("throw-distance", 1.2);

                        // --- CÓDIGO NUEVO ---
                        // Cargar la lista de drops custom
                        Map<Material, CustomDrop> customDrops = new HashMap<>();
                        List<String> dropStrings = section.getStringList("custom-drops");
                        for (String dropString : dropStrings) {
                            try {
                                String[] parts = dropString.split(":");
                                if (parts.length == 3) {
                                    Material mat = Material.valueOf(parts[0].toUpperCase());
                                    String dropItemId = parts[1];
                                    int amount = Integer.parseInt(parts[2]);
                                    customDrops.put(mat, new CustomDrop(dropItemId, amount));
                                }
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Error parseando custom-drop '" + dropString + "' para el item " + itemId);
                            }
                        }
                        // --- FIN CÓDIGO NUEVO ---

                        ItemStack baseItem = this.itemManager.createItem(itemId);
                        if (baseItem == null) {
                            this.plugin.getLogger().warning("Failed to load custom item: " + itemId + " - Base item not found");
                            continue;
                        }

                        // --- Pasar el Map al constructor ---
                        this.customItemDataCache.put(itemId, new BombItemData(type, radius, shape, time, cooldown, throwDistance, customDrops));

                    } else { // Si es DRILL
                        int width = section.getInt("width", 3);
                        int height = section.getInt("height", 3);
                        int depth = section.getInt("depth", 1);
                        boolean maxDamage = section.getBoolean("maxDamage", true);
                        int damage = section.getInt("damage", 1);
                        List<String> allowedMaterialNames = section.getStringList("allowedMaterials");
                        EnumSet<Material> allowedMats = EnumSet.noneOf(Material.class);

                        for(String matName : allowedMaterialNames) {
                            try {
                                allowedMats.add(Material.valueOf(matName.toUpperCase()));
                            } catch (IllegalArgumentException var17) {
                                this.plugin.getLogger().warning("Invalid material name: " + matName);
                            }
                        }

                        ItemStack baseItem = this.itemManager.createItem(itemId);
                        if (baseItem == null) {
                            this.plugin.getLogger().warning("Failed to load custom item: " + itemId + " - Base item not found");
                            continue;
                        }

                        this.customItemDataCache.put(itemId, new CustomItemData(type, width, height, depth, maxDamage, damage, allowedMats));
                    }

                    ++loadedCount;
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Error loading custom item " + itemId, e);
                }
            }
        }

        return loadedCount;
    }

    public boolean isCustomItem(ItemStack item) {
        String itemId = this.itemManager.getItemId(item);
        return itemId != null && this.customItemDataCache.containsKey(itemId);
    }

    public boolean isBombItem(ItemStack item) {
        String itemId = this.itemManager.getItemId(item);
        if (itemId == null) {
            return false;
        } else {
            CustomItemData data = (CustomItemData)this.customItemDataCache.get(itemId);
            return data != null && "BOMB".equalsIgnoreCase(data.getType());
        }
    }

    public CustomItemData getCustomItemData(ItemStack item) {
        String itemId = this.itemManager.getItemId(item);
        return this.customItemData(itemId);
    }

    public CustomItemData customItemData(String itemId) {
        return (CustomItemData)this.customItemDataCache.get(itemId);
    }

    public void reload() {
        this.loadCustomItems();
    }

    public static class CustomItemData {
        protected final String type;
        private final int width;
        private final int height;
        private final int depth;
        private final boolean maxDamage;
        private final int damage;
        private final EnumSet<Material> allowedMaterials;

        public CustomItemData(String type, int width, int height, int depth, boolean maxDamage, int damage, EnumSet<Material> allowedMaterials) {
            this.type = type;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.maxDamage = maxDamage;
            this.damage = damage;
            this.allowedMaterials = allowedMaterials;
        }

        public String getType() {
            return this.type;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public int getDepth() {
            return this.depth;
        }

        public boolean isMaxDamage() {
            return this.maxDamage;
        }

        public int getDamage() {
            return this.damage;
        }

        public EnumSet<Material> getAllowedMaterials() {
            return this.allowedMaterials;
        }
    }

    // --- CLASE NUEVA ---
    // Pequeña clase para guardar la info del drop custom
    public static class CustomDrop {
        private final String itemId;
        private final int amount;

        public CustomDrop(String itemId, int amount) {
            this.itemId = itemId;
            this.amount = amount;
        }

        public String getItemId() { return itemId; }
        public int getAmount() { return amount; }
    }
    // --- FIN CLASE NUEVA ---

    public static class BombItemData extends CustomItemData {
        private final double radius;
        private final String shape;
        private final int time;
        private final int cooldown;
        private final double throwDistance;
        // --- CAMPO NUEVO ---
        private final Map<Material, CustomDrop> customDrops;

        // --- CONSTRUCTOR MODIFICADO ---
        public BombItemData(String type, double radius, String shape, int time, int cooldown, double throwDistance, Map<Material, CustomDrop> customDrops) {
            super(type, 0, 0, 0, false, 0, EnumSet.noneOf(Material.class));
            this.radius = radius;
            this.shape = shape;
            this.time = time;
            this.cooldown = cooldown;
            this.throwDistance = throwDistance;
            this.customDrops = customDrops; // Seteamos el nuevo campo
        }

        public double getRadius() {
            return this.radius;
        }

        public String getShape() {
            return this.shape;
        }

        public int getTime() {
            return this.time;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public double getThrowDistance() {
            return this.throwDistance;
        }

        // --- MÉTODO NUEVO ---
        public Map<Material, CustomDrop> getCustomDrops() {
            return this.customDrops;
        }
    }
}