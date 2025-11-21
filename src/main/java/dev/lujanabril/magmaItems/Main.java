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

// --- AÑADIR ESTAS IMPORTACIONES ---
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
// --- FIN DE IMPORTACIONES ---

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

    // --- CAMPO MODIFICADO (YA NO SE NECESITA EL MAPA GLOBAL) ---
    private int playerCheckIndex = 0;
    // --- FIN DE CAMPO MODIFICADO ---

    @Override
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

        this.bombManager = new BombManager(this, this.customItemManager, this.sellManager);
        this.itemListener = new ItemListener(this);
        this.customItemListener = new CustomItemListener(this, this.customItemManager, this.sellManager);

        this.getServer().getPluginManager().registerEvents(new BombListener(this.customItemManager, this.bombManager), this);
        this.getServer().getPluginManager().registerEvents(this.itemListener, this);
        this.getServer().getPluginManager().registerEvents(this.customItemListener, this);
        this.getServer().getPluginManager().registerEvents(new ItemEventListener(this, this.itemManager), this);
        this.getServer().getPluginManager().registerEvents(new HistoryGUIListener(this, this.miniMessage, this.prefix), this);
        this.getServer().getPluginManager().registerEvents(new TotemListener(this), this);

        this.itemCommand = new ItemCommand(this);
        this.getCommand("magmaitems").setExecutor(this.itemCommand);
        this.getCommand("magmaitems").setTabCompleter(this.itemCommand);

        // --- TAREAS MODIFICADAS ---
        // Tarea LIGERA (cada 2 ticks) para blacklist y ubicación
        this.startPlayerScanTask();

        // Tarea PESADA (cada 10 minutos) para duplicados
        this.startDuplicateCheckTask();
        // --- FIN TAREAS MODIFICADAS ---
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
        this.placeholders = new HashMap();
        ConfigurationSection placeholderSection = this.getConfig().getConfigurationSection("placeholders");
        if (placeholderSection != null) {
            for(String key : placeholderSection.getKeys(false)) {
                this.placeholders.put(key, placeholderSection.getString(key));
            }
        }
    }

    // --- MÉTODO MODIFICADO (YA NO REVISA DUPLICADOS) ---
    private void startPlayerScanTask() {
        // Tarea SÍNCRONA (runTaskTimer) que se ejecuta cada 2 ticks.
        // Escanea 1 jugador cada 2 ticks para blacklist y ubicación.
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                playerCheckIndex = 0; // Resetear si no hay jugadores
                return;
            }

            // Asegurarse de que el índice esté dentro de los límites
            if (playerCheckIndex >= players.size()) {
                playerCheckIndex = 0; // Reiniciar el ciclo
            }

            if (players.isEmpty()) return;
            if (playerCheckIndex >= players.size()) playerCheckIndex = 0;

            // Obtener UN jugador de la lista
            Player playerToCheck = players.get(playerCheckIndex);

            // Ejecutar las comprobaciones LIGERAS
            if (playerToCheck != null && playerToCheck.isOnline()) {
                // Actualiza ubicación y dueño (nuevo método ligero)
                this.itemTrackingManager.updatePlayerItemData(playerToCheck);
                // Revisa si tiene items de la lista negra
                this.itemTrackingManager.checkAndRemoveBlacklistedItems(playerToCheck);
            }

            // Avanzar al siguiente jugador para la próxima ejecución (en 2 ticks)
            playerCheckIndex++;

        }, 200L, 2L); // Empezar después de 10s, y correr cada 2 ticks
    }

    // --- MÉTODO NUEVO AÑADIDO ---
    /**
     * Tarea pesada que busca duplicados. Se ejecuta con poca frecuencia.
     */
    private void startDuplicateCheckTask() {
        // Se ejecuta Síncrono (para acceder a inventarios de forma segura)
        // Se ejecuta 10 minutos (12000 ticks) después de iniciar el server,
        // y se repite cada 10 minutos.
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            getLogger().info("Iniciando chequeo programado de MagmaItems duplicados...");

            // Esta llamada ahora es segura porque es síncrona
            // y el método interno fue modificado para manejar su propia lógica de conteo.
            this.itemTrackingManager.checkAllMagmaItems();

        }, 12000L, 12000L); // 12000 ticks = 10 minutos
    }
    // --- FIN MÉTODO NUEVO AÑADIDO ---

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