package dev.lujanabril.magmaItems;

import dev.lujanabril.magmaItems.Commands.ItemCommand;
import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Listeners.*;
import dev.lujanabril.magmaItems.Managers.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin {
    private Map<String, String> placeholders;
    private ItemManager itemManager;
    private CustomItemManager customItemManager;
    private ItemStorageManager itemStorageManager;
    private ItemTrackingManager itemTrackingManager;
    private ItemCommand itemCommand;
    private HistoryGUI historyGUI;
    private String prefix;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void onEnable() {
        this.saveDefaultConfig();
        this.loadPlaceholders();

        this.prefix = this.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");

        this.historyGUI = new HistoryGUI(this);
        this.itemManager = new ItemManager(this);
        this.customItemManager = new CustomItemManager(this, this.itemManager);
        this.itemStorageManager = new ItemStorageManager(this);

        this.itemTrackingManager = new ItemTrackingManager(this);

        BombManager bombManager = new BombManager(this, this.customItemManager);

        this.getServer().getPluginManager().registerEvents(new BombListener(this.customItemManager, bombManager), this);
        this.getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        this.getServer().getPluginManager().registerEvents(new CustomItemListener(this, this.customItemManager), this);
        this.getServer().getPluginManager().registerEvents(new ItemEventListener(this, this.itemManager), this);
        this.getServer().getPluginManager().registerEvents(new HistoryGUIListener(this, this.miniMessage, this.prefix), this);

        this.itemCommand = new ItemCommand(this);
        this.getCommand("magmaitems").setExecutor(this.itemCommand);
        this.getCommand("magmaitems").setTabCompleter(this.itemCommand);

        this.startDuplicateCheckTask();
    }

    private void loadPlaceholders() {
        this.placeholders = new HashMap();
        ConfigurationSection placeholderSection = this.getConfig().getConfigurationSection("placeholders");
        if (placeholderSection != null) {
            for(String key : placeholderSection.getKeys(false)) {
                this.placeholders.put(key, placeholderSection.getString(key));
            }
        }
    }

    private void startDuplicateCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> this.itemTrackingManager.checkAllMagmaItems(), 600L, 600L);
    }

    // 💡 MÉTODO AÑADIDO PARA LA VERIFICACIÓN DE ADVANCEDENCHANTMENTS
    /**
     * Verifica si el plugin AdvancedEnchantments está cargado y habilitado.
     * @return true si el plugin está presente, false en caso contrario.
     */
    public boolean isAdvancedEnchantmentsLoaded() {
        // La ID del plugin AdvancedEnchantments es "AdvancedEnchantments"
        return Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
    }
    // ----------------------------------------------------------------------

    public HistoryGUI getHistoryGUI() {
        return this.historyGUI;
    }

    public ItemManager getItemManager() {
        return this.itemManager;
    }

    public CustomItemManager getCustomItemManager() {
        return this.customItemManager;
    }

    public ItemStorageManager getItemStorageManager() {
        return this.itemStorageManager;
    }

    public ItemTrackingManager getItemTrackingManager() {
        return this.itemTrackingManager;
    }

    public MiniMessage getMiniMessage() {
        return this.miniMessage;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void reloadConfig() {
        super.reloadConfig();
        this.loadPlaceholders();

        this.prefix = this.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");

        if (this.itemCommand != null) {
            this.itemCommand.loadPrefix();
        }

        if (this.itemManager != null) {
            this.itemManager.reload();
        }

        if (this.customItemManager != null) {
            this.customItemManager.reload();
        }

        if (this.itemTrackingManager != null) {
            this.itemTrackingManager.reloadTracking();
        }

        if (this.itemStorageManager != null) {
            this.itemStorageManager.reloadStorage();
        }
    }
}