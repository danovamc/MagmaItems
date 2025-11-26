package dev.lujanabril.magmaItems;

import dev.lujanabril.magmaItems.Commands.ItemCommand;
import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Listeners.*;
import dev.lujanabril.magmaItems.Managers.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private BombManager bombManager;
    private CustomItemListener customItemListener;
    private ItemListener itemListener;

    private boolean debugMode = false;

    private int playerCheckIndex = 0;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.loadPlaceholders();

        this.prefix = this.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");
        this.debugMode = this.getConfig().getBoolean("debug", false);

        // Inicialización de Managers
        this.historyGUI = new HistoryGUI(this);
        this.itemManager = new ItemManager(this);
        this.customItemManager = new CustomItemManager(this, this.itemManager);
        this.itemStorageManager = new ItemStorageManager(this);
        this.itemTrackingManager = new ItemTrackingManager(this);
        this.sellManager = new SellManager(this);

        this.bombManager = new BombManager(this, this.customItemManager, this.sellManager);
        this.itemListener = new ItemListener(this);
        this.customItemListener = new CustomItemListener(this, this.customItemManager, this.sellManager);

        // Registro de Eventos
        this.getServer().getPluginManager().registerEvents(new BombListener(this.customItemManager, this.bombManager), this);
        this.getServer().getPluginManager().registerEvents(this.itemListener, this);
        this.getServer().getPluginManager().registerEvents(this.customItemListener, this);
        this.getServer().getPluginManager().registerEvents(new ItemEventListener(this, this.itemManager), this);
        this.getServer().getPluginManager().registerEvents(new HistoryGUIListener(this, this.miniMessage, this.prefix), this);
        this.getServer().getPluginManager().registerEvents(new TotemListener(this), this);

        // Comandos
        this.itemCommand = new ItemCommand(this);
        this.getCommand("magmaitems").setExecutor(this.itemCommand);
        this.getCommand("magmaitems").setTabCompleter(this.itemCommand);

        // Tareas Programadas
        this.startPlayerScanTask();
        this.startDuplicateCheckTask();

        // --- CARGA DIFERIDA (SOLUCIÓN RACE CONDITION) ---
        // 100 ticks = 5 segundos DESPUÉS de que el servidor diga "Done".
        Bukkit.getScheduler().runTaskLater(this, () -> {
            logDebug("=== INICIO CARGA DIFERIDA (5s post-inicio) ===");

            if (isAdvancedEnchantmentsLoaded()) {
                logDebug("✅ API de AdvancedEnchantments detectada.");
            } else {
                if (debugMode) getLogger().warning("[DEBUG] ❌ AdvancedEnchantments NO se detectó.");
            }

            // Recargar para aplicar cambios
            this.itemManager.reload();
            this.customItemManager.reload();

            logDebug("=== FIN CARGA DIFERIDA ===");
        }, 100L);
    }

    public void logDebug(String message) {
        if (this.debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    @Override
    public void onDisable() {
        if (itemTrackingManager != null) {
            getLogger().info("Guardando datos de seguimiento de MagmaItems...");
            itemTrackingManager.saveTracking();
            getLogger().info("¡Datos de seguimiento guardados!");
        }
    }

    private void loadPlaceholders() {
        this.placeholders = new HashMap<>();
        ConfigurationSection placeholderSection = this.getConfig().getConfigurationSection("placeholders");
        if (placeholderSection != null) {
            for (String key : placeholderSection.getKeys(false)) {
                this.placeholders.put(key, placeholderSection.getString(key));
            }
        }
    }

    private void startPlayerScanTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                playerCheckIndex = 0;
                return;
            }

            if (playerCheckIndex >= players.size()) {
                playerCheckIndex = 0;
            }

            Player playerToCheck = players.get(playerCheckIndex);

            if (playerToCheck != null && playerToCheck.isOnline()) {
                this.itemTrackingManager.updatePlayerItemData(playerToCheck);
                this.itemTrackingManager.checkAndRemoveBlacklistedItems(playerToCheck);

                ItemStack[] contents = playerToCheck.getInventory().getContents();
                boolean inventoryChanged = false;

                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item != null && !item.getType().isAir()) {
                        ItemStack updatedItem = this.itemManager.updateItem(item);
                        if (updatedItem != null) {
                            contents[i] = updatedItem;
                            inventoryChanged = true;
                        }
                    }
                }

                if (inventoryChanged) {
                    playerToCheck.getInventory().setContents(contents);
                }
            }

            playerCheckIndex++;

        }, 200L, 2L);
    }

    private void startDuplicateCheckTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            getLogger().info("Iniciando chequeo programado de MagmaItems duplicados...");
            this.itemTrackingManager.checkAllMagmaItems();
        }, 12000L, 12000L);
    }

    public boolean isAdvancedEnchantmentsLoaded() {
        return Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments");
    }

    public String renderMiniMessageToLegacy(String mmString) {
        if (mmString == null) return null;
        String sanitizedString = mmString.replace('§', '&');
        net.kyori.adventure.text.Component component = this.miniMessage.deserialize(sanitizedString);
        String legacy = LegacyComponentSerializer.legacyAmpersand().serialize(component);
        return legacy.replace('&', '§');
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

    public boolean isDebugMode() {
        return this.debugMode;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.loadPlaceholders();

        this.prefix = this.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");
        this.debugMode = this.getConfig().getBoolean("debug", false);

        if (this.itemCommand != null) {
            this.itemCommand.loadPrefix();
        }

        if (this.itemManager != null) {
            this.itemManager.reload();
        }

        if (this.customItemManager != null) {
            this.customItemManager.reload();
        }

        if (this.bombManager != null) {
            this.bombManager.reload();
        }
        if (this.customItemListener != null) {
            this.customItemListener.reload();
        }

        if (this.itemTrackingManager != null) {
            this.itemTrackingManager.reloadTracking();
            this.itemTrackingManager.loadRemovalList();
        }

        if (this.itemStorageManager != null) {
            this.itemStorageManager.reloadStorage();
        }
    }
}