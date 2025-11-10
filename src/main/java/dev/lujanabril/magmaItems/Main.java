package dev.lujanabril.magmaItems;

import dev.lujanabril.magmaItems.Commands.ItemCommand;
import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Listeners.*;
import dev.lujanabril.magmaItems.Managers.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        this.getServer().getPluginManager().registerEvents(new TotemListener(this), this);

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

    public boolean isAdvancedEnchantmentsLoaded() {
        return Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
    }

    public String renderMiniMessageToLegacy(String mmString) {
        if (mmString == null) return null;

        // Paso 1: Sanitiza el input de MiniMessage, convirtiendo § a & para evitar el crash del parser.
        String sanitizedString = mmString.replace('§', '&');

        // Paso 2: Deserializa el String (que ahora solo tiene tags MM y & codes) a un Component.
        net.kyori.adventure.text.Component component = this.miniMessage.deserialize(sanitizedString);

        // Paso 3: Serializa el Component a formato Ampersand (&), y luego fuerza la conversión final a Section codes (§).
        // Esto asegura que el cliente de Minecraft (que necesita §) lo lea correctamente.
        String legacyAmpersandString = LegacyComponentSerializer.legacyAmpersand().serialize(component);

        return legacyAmpersandString.replace('&', '§');
    }

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