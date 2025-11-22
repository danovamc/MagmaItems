package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemManager;
import net.kyori.adventure.text.Component; // <--- IMPORTANTE: AÑADIR ESTO
import nl.marido.deluxecombat.events.CombatlogEvent;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ItemEventListener implements Listener {
    private final Main plugin;
    private final ItemManager itemManager;
    private final Map<UUID, List<ItemStack>> pendingItems = new HashMap();
    private boolean canSendDropMessage = true;

    public ItemEventListener(Main plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (this.itemManager.isMagmaItem(item) && !this.itemManager.isDroppable(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            // --- CORRECCIÓN INICIO ---
            String rawMsg = this.plugin.getConfig().getString("messages.cannot-drop", "<red><b>ERROR</b> <dark_gray>▸</dark_gray> <white>¡No puedes tirar este ítem!</white>");
            Component message = this.plugin.getMiniMessage().deserialize(rawMsg);

            player.sendActionBar(message);
            if (this.canSendDropMessage) {
                player.sendMessage(message);
                this.canSendDropMessage = false;
                this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, () -> this.canSendDropMessage = true, 60L);
            }
            // --- CORRECCIÓN FIN ---
        }
    }

    @EventHandler
    public void onWindChargeDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof WindCharge) {
            World world = event.getEntity().getWorld();
            if (world.getName().equalsIgnoreCase("mina")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerCombatlog(CombatlogEvent e) {
        Player player = e.getCombatlogger();
        List<ItemStack> keepItems = new ArrayList();
        PlayerInventory inventory = player.getInventory();

        for(int i = 0; i < inventory.getSize(); ++i) {
            ItemStack item = inventory.getItem(i);
            if (item != null && this.itemManager.isMagmaItem(item) && this.itemManager.shouldKeepOnDeath(item)) {
                keepItems.add(item.clone());
                inventory.setItem(i, (ItemStack)null);
            }
        }

        if (!keepItems.isEmpty()) {
            UUID playerId = player.getUniqueId();
            ((List)this.pendingItems.computeIfAbsent(playerId, (k) -> new ArrayList())).addAll(keepItems);
            this.saveItemsToDatabase(playerId, keepItems);
        }

    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<ItemStack> keepItems = new ArrayList();
        Iterator<ItemStack> iter = event.getDrops().iterator();

        while(iter.hasNext()) {
            ItemStack item = (ItemStack)iter.next();
            if (this.itemManager.isMagmaItem(item) && this.itemManager.shouldKeepOnDeath(item)) {
                keepItems.add(item);
                iter.remove();
            }
        }

        if (!keepItems.isEmpty()) {
            Player player = event.getEntity();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                for(ItemStack item : keepItems) {
                    player.getInventory().addItem(new ItemStack[]{item});
                }

            });
        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        List<ItemStack> items = (List)this.pendingItems.remove(playerId);
        if (items != null && !items.isEmpty()) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                for(ItemStack item : items) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[]{item});
                    if (!leftover.isEmpty()) {
                        for(ItemStack leftoverItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                        }
                    }
                }
                // Nota: Aquí también deberías usar MiniMessage si quieres colores modernos,
                // pero como usas § (legacy), esto funcionará por ahora.
                player.sendMessage("§x§E§F§0§0§0§0§lETERNAL §8➡ §f¡Habilidad §e§neterna§f activada! Has §e§nrecuperado§f tu pico desde el inframundo.");
            }, 20L);
        }

        this.loadItemsFromDatabase(playerId);
    }

    private void saveItemsToDatabase(UUID playerId, List<ItemStack> items) {
        try {
            File dataFolder = new File(this.plugin.getDataFolder(), "pending_items");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File playerFile = new File(dataFolder, playerId.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            for(int i = 0; i < items.size(); ++i) {
                config.set("items." + i, items.get(i));
            }

            config.save(playerFile);
        } catch (IOException var7) {
            this.plugin.getLogger().warning("Error guardando items para " + String.valueOf(playerId));
        }

    }

    private void loadItemsFromDatabase(UUID playerId) {
        try {
            File playerFile = new File(new File(this.plugin.getDataFolder(), "pending_items"), playerId.toString() + ".yml");
            if (!playerFile.exists()) {
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
            List<ItemStack> items = new ArrayList();
            if (config.contains("items")) {
                for(String key : config.getConfigurationSection("items").getKeys(false)) {
                    ItemStack item = config.getItemStack("items." + key);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception var8) {
            this.plugin.getLogger().warning("Error cargando items para " + String.valueOf(playerId));
        }

    }

    public void clearPendingItems() {
        this.pendingItems.clear();
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player)event.getWhoClicked();
            InventoryType type = event.getInventory().getType();
            if (type == InventoryType.ANVIL || type == InventoryType.GRINDSTONE) {

                // --- CORRECCIÓN INICIO (Preparar mensaje) ---
                String rawMsg = this.plugin.getConfig().getString("messages.cannot-interact", "<red><b>ERROR</b> <dark_gray>▸</dark_gray> <white>¡No puedes interactuar con este ítem!</white>");
                Component message = this.plugin.getMiniMessage().deserialize(rawMsg);
                // --- CORRECCIÓN FIN ---

                ItemStack item = event.getCurrentItem();
                if (item != null && this.itemManager.isMagmaItem(item) && !this.itemManager.isInteractable(item)) {
                    event.setCancelled(true);
                    player.sendMessage(message); // Usar el componente deserializado
                    return;
                }

                ItemStack cursorItem = event.getCursor();
                if (cursorItem != null && this.itemManager.isMagmaItem(cursorItem) && !this.itemManager.isInteractable(cursorItem)) {
                    event.setCancelled(true);
                    player.sendMessage(message); // Usar el componente deserializado
                }
            }

        }
    }
}