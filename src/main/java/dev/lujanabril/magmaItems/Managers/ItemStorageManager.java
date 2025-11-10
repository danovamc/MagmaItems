package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemStorageManager {
    private final Main plugin;
    private final File storageFile;
    private FileConfiguration storageConfig;
    private final Map<String, ItemStack> storedItems;

    public ItemStorageManager(Main plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "items-storage.yml");
        this.storedItems = new HashMap();
        this.loadStorage();
    }

    public void loadStorage() {
        if (!this.storageFile.exists()) {
            try {
                this.storageFile.createNewFile();
            } catch (IOException e) {
                this.plugin.getLogger().severe("Could not create items-storage.yml: " + e.getMessage());
                return;
            }
        }

        this.storageConfig = YamlConfiguration.loadConfiguration(this.storageFile);
        this.storedItems.clear();
        ConfigurationSection itemsSection = this.storageConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for(String key : itemsSection.getKeys(false)) {
                ItemStack item = itemsSection.getItemStack(key);
                if (item != null) {
                    this.storedItems.put(key, item);
                }
            }
        }

        this.plugin.getLogger().info("Loaded " + this.storedItems.size() + " stored items.");
    }

    public void reloadStorage() {
        this.loadStorage();
    }

    public boolean saveItem(String name, ItemStack item) {
        if (this.storedItems.containsKey(name)) {
            return false;
        } else {
            this.storedItems.put(name, item.clone());
            this.storageConfig.set("items." + name, item);

            try {
                this.storageConfig.save(this.storageFile);
                return true;
            } catch (IOException e) {
                this.plugin.getLogger().severe("Could not save item to storage: " + e.getMessage());
                this.storedItems.remove(name);
                return false;
            }
        }
    }

    public ItemStack getItem(String name) {
        ItemStack item = (ItemStack)this.storedItems.get(name);
        return item != null ? item.clone() : null;
    }

    public boolean removeItem(String name) {
        if (!this.storedItems.containsKey(name)) {
            return false;
        } else {
            this.storedItems.remove(name);
            this.storageConfig.set("items." + name, (Object)null);

            try {
                this.storageConfig.save(this.storageFile);
                return true;
            } catch (IOException e) {
                this.plugin.getLogger().severe("Could not remove item from storage: " + e.getMessage());
                this.loadStorage();
                return false;
            }
        }
    }

    public List<String> getStoredItemNames() {
        return new ArrayList(this.storedItems.keySet());
    }

    public boolean hasItem(String name) {
        return this.storedItems.containsKey(name);
    }

    public int getStoredItemCount() {
        return this.storedItems.size();
    }
}
