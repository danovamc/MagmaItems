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

    private SellManager sellManager;

    // --- CAMPOS NUEVOS (para guardar las instancias) ---
    private BombManager bombManager;
    private CustomItemListener customItemListener;
    private ItemListener itemListener; // <-- También guardamos este por consistencia

    public void onEnable() {
        this.saveDefaultConfig();
        this.loadPlaceholders();

        this.prefix = this.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");

        // --- INICIALIZAR MANAGERS ---
        this.historyGUI = new HistoryGUI(this);
        this.itemManager = new ItemManager(this);
        this.customItemManager = new CustomItemManager(this, this.itemManager);
        this.itemStorageManager = new ItemStorageManager(this);
        this.itemTrackingManager = new ItemTrackingManager(this);
        this.sellManager = new SellManager(this);

        // --- CONSTRUCTORES MODIFICADOS (asignamos a los campos de la clase) ---
        this.bombManager = new BombManager(this, this.customItemManager, this.sellManager);
        this.itemListener = new ItemListener(this); // Asignamos
        this.customItemListener = new CustomItemListener(this, this.customItemManager, this.sellManager); // Asignamos

        this.getServer().getPluginManager().registerEvents(new BombListener(this.customItemManager, this.bombManager), this);
        this.getServer().getPluginManager().registerEvents(this.itemListener, this); // Usamos el campo
        this.getServer().getPluginManager().registerEvents(this.customItemListener, this); // Usamos el campo
        this.getServer().getPluginManager().registerEvents(new ItemEventListener(this, this.itemManager), this);
        this.getServer().getPluginManager().registerEvents(new HistoryGUIListener(this, this.miniMessage, this.prefix), this);
        this.getServer().getPluginManager().registerEvents(new TotemListener(this), this);
        // --- FIN MODIFICACIONES ---

        this.itemCommand = new ItemCommand(this);
        this.getCommand("magmaitems").setExecutor(this.itemCommand);
        this.getCommand("magmaitems").setTabCompleter(this.itemCommand);

        this.startCombinedCheckTask();
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

    private void startCombinedCheckTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.itemTrackingManager.checkAllMagmaItems();
            this.itemTrackingManager.checkAndRemoveBlacklistedItems();
        }, 600L, 600L);
    }

    public boolean isAdvancedEnchantmentsLoaded() {
        return Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
    }

    public String renderMiniMessageToLegacy(String mmString) {
        if (mmString == null) return null;
        String sanitizedString = mmString.replace('§', '&');
        net.kyori.adventure.text.Component component = this.miniMessage.deserialize(sanitizedString);
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

    public SellManager getSellManager() {
        return this.sellManager;
    }

    public MiniMessage getMiniMessage() {
        return this.miniMessage;
    }

    public String getPrefix() {
        return this.prefix;
    }

    // --- MÉTODO RELOADCONFIG MODIFICADO ---
    @Override
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

        // --- LLAMADAS A LOS NUEVOS MÉTODOS RELOAD ---
        if (this.bombManager != null) {
            this.bombManager.reload();
        }
        if (this.customItemListener != null) {
            this.customItemListener.reload();
        }
        // (ItemListener no parece tener config propia, así que no necesita reload)
        // --- FIN ---

        if (this.itemTrackingManager != null) {
            this.itemTrackingManager.reloadTracking();
            this.itemTrackingManager.loadRemovalList();
        }

        if (this.itemStorageManager != null) {
            this.itemStorageManager.reloadStorage();
        }
    }
}