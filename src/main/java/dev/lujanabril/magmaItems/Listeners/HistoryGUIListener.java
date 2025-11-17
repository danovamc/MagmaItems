package dev.lujanabril.magmaItems.Listeners;

// --- Importaciones necesarias ---
import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit; // <-- AÑADIDO
import org.bukkit.ChatColor;
import org.bukkit.Location; // <-- AÑADIDO
import org.bukkit.NamespacedKey;
import org.bukkit.World; // <-- AÑADIDO
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList; // <-- AÑADIDO
import java.util.List;
import java.util.stream.Collectors;

public class HistoryGUIListener implements Listener {
    private final Main plugin;
    private final ItemTrackingManager trackingManager;
    private final MiniMessage miniMessage;
    private final String prefix;

    public HistoryGUIListener(Main plugin, MiniMessage miniMessage, String prefix) {
        this.plugin = plugin;
        this.trackingManager = plugin.getItemTrackingManager();
        this.miniMessage = miniMessage;
        this.prefix = prefix;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("Historial - [") && event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
            Player player = (Player)event.getWhoClicked();

            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                if (event.getSlot() >= 9 && event.getSlot() < 36 && event.getCurrentItem() != null) {

                    List<String> whitelist = plugin.getConfig().getStringList("Id-remover-whitelist");
                    if (!whitelist.contains(player.getName())) {
                        player.sendMessage(miniMessage.deserialize(prefix + "<red>No tienes permiso para eliminar registros del historial."));
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO.key(), Source.MASTER, 1.0F, 1.0F));
                        return;
                    }

                    ItemTrackingManager.ItemInfo info = getItemInfoFromClickedItem(event.getCurrentItem());
                    if (info != null) {
                        plugin.getHistoryGUI().setSwitchingMenu(player, true);
                        plugin.getHistoryGUI().openDeleteConfirmation(player, info);
                    }
                }
                return;
            }

            if (event.getCurrentItem() != null) {
                switch (event.getSlot()) {
                    case 3:
                        if (event.getCurrentItem().getType().toString().equals("HOPPER")) {
                            this.plugin.getHistoryGUI().cycleSortType(player);
                        }
                        break;
                    case 38:
                        if (event.getCurrentItem().getType().toString().equals("ARROW")) {
                            this.plugin.getHistoryGUI().openPreviousPage(player);
                        }
                        return;
                    case 5:
                        if (event.getCurrentItem().getType().toString().equals("OAK_SIGN") || event.getCurrentItem().getType().toString().equals("RED_CANDLE")) {
                            this.handleSearchButton(player);
                        }
                        return;
                    case 40:
                        if (event.getCurrentItem().getType().toString().equals("BARRIER")) {
                            player.closeInventory();
                            player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
                        }
                        return;
                    case 42:
                        if (event.getCurrentItem().getType().toString().equals("ARROW")) {
                            this.plugin.getHistoryGUI().openNextPage(player);
                        }
                        return;
                    default:
                        if (event.getSlot() >= 9 && event.getSlot() < 36) {
                            this.processItemClick(player, event);
                        }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player)event.getPlayer();

            if (plugin.getHistoryGUI().isSwitchingMenu(player)) {
                plugin.getHistoryGUI().setSwitchingMenu(player, false);
                return;
            }

            String title = event.getView().getTitle();
            if (title.contains("Historial - [") && this.plugin.getHistoryGUI().isInSearchMode(player)) {
                this.plugin.getHistoryGUI().clearSearchGUI(player);
            }
        }
    }

    private void handleSearchButton(Player player) {
        if (!this.plugin.getHistoryGUI().isInSearchMode(player) && !this.hasActiveSearch(player)) {
            this.plugin.getHistoryGUI().startSearchMode(player);
        } else {
            this.plugin.getHistoryGUI().clearSearch(player);
            String message = "<red>¡Has <yellow>reiniciado <white>la busqueda, ahora se muestran todos los items!";
            player.sendMessage(this.miniMessage.deserialize(this.prefix + message));
        }

        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
    }

    private boolean hasActiveSearch(Player player) {
        if (player.getOpenInventory() != null) {
            return player.getOpenInventory().getTitle().contains("[Búsqueda]");
        }
        return false;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.getHistoryGUI().isInSearchMode(player)) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.plugin.getHistoryGUI().processSearch(player, message));
        }
    }

    // --- INICIO DE LA MODIFICACIÓN (MÉTODO REESCRITO) ---
    /**
     * Procesa el clic en un item del historial (slots 9-35).
     * Teletransporta al jugador a la última ubicación conocida del item.
     */
    private void processItemClick(Player player, InventoryClickEvent event) {
        // Reutilizamos el método que ya extrae la ID del item
        ItemTrackingManager.ItemInfo info = getItemInfoFromClickedItem(event.getCurrentItem());

        if (info != null) {
            // 1. Obtener los datos de ubicación del item
            String worldName = info.getWorld();
            double x = info.getX();
            double y = info.getY();
            double z = info.getZ();

            // 2. Verificar que el mundo exista
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(miniMessage.deserialize(prefix + "<red>Error: El mundo '<yellow>" + worldName + "<red>' no existe o no está cargado."));
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO.key(), Source.MASTER, 1.0F, 1.0F));
                return;
            }

            // 3. Crear la ubicación y teletransportar
            Location targetLocation = new Location(world, x, y, z);

            player.teleport(targetLocation);
            player.closeInventory();

            player.sendMessage(miniMessage.deserialize(prefix + "<green>Teletransportado a la última ubicación del item."));
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT.key(), Source.MASTER, 1.0F, 1.0F));

        } else {
            // Fallback por si acaso no se pudo parsear el item
            player.sendMessage(miniMessage.deserialize(prefix + "<red>Error al leer los datos del item."));
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO.key(), Source.MASTER, 1.0F, 1.0F));
        }
    }
    // --- FIN DE LA MODIFICACIÓN ---

    private ItemTrackingManager.ItemInfo getItemInfoFromClickedItem(ItemStack clickedItem) {
        if (clickedItem == null) return null;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String idLine = null;
                for (String line : lore) {
                    String strippedLine = ChatColor.stripColor(line);
                    if (strippedLine.startsWith("ID: ")) {
                        idLine = strippedLine;
                        break;
                    }
                }

                if (idLine != null) {
                    try {
                        String itemId = idLine.split(":")[1].trim();
                        return this.trackingManager.getItemInfo(itemId);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error al parsear ItemID desde el lore del historial: " + idLine);
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onDeleteConfirmClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HistoryGUI.DeleteConfirmHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey actionKey = new NamespacedKey(plugin, "confirm-action");

        if (container.has(actionKey, PersistentDataType.STRING)) {
            String action = container.get(actionKey, PersistentDataType.STRING);
            ItemTrackingManager.ItemInfo info = plugin.getHistoryGUI().getItemPendingDeletion().remove(player.getUniqueId());

            if ("delete".equals(action)) {
                if (info != null) {
                    trackingManager.logDeletion(player, info);
                    trackingManager.removeItemTracking(info.getItemId());
                    trackingManager.addItemToRemovalList(info.getItemId());

                    player.sendMessage(miniMessage.deserialize(prefix + "<green>Registro <yellow>" + info.getItemId() + "<green> eliminado exitosamente."));
                    player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key(), Source.MASTER, 0.5F, 1.2F));

                    player.closeInventory();

                    HistoryGUI historyGUI = plugin.getHistoryGUI();
                    boolean wasSearching = historyGUI.isInSearchMode(player);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (wasSearching) {
                            String lastSearch = historyGUI.getLastSearchTerm(player.getUniqueId());
                            if (lastSearch != null) {
                                historyGUI.processSearch(player, lastSearch);
                            } else {
                                historyGUI.openHistoryMenu(player, 1);
                            }
                        } else {
                            historyGUI.openHistoryMenu(player, 1);
                        }
                    });

                } else {
                    player.sendMessage(miniMessage.deserialize(prefix + "<red>Error. No se encontró el item a eliminar. Inténtalo de nuevo."));
                    player.closeInventory();
                }
            } else if ("cancel".equals(action)) {
                player.closeInventory();
                player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getHistoryGUI().openHistoryMenu(player, plugin.getHistoryGUI().getCurrentPage(player.getUniqueId()));
                });
            }
        }
    }

    @EventHandler
    public void onBulkDeleteConfirmClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HistoryGUI.BulkDeleteConfirmHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey actionKey = new NamespacedKey(plugin, "confirm-action");

        if (container.has(actionKey, PersistentDataType.STRING)) {
            String action = container.get(actionKey, PersistentDataType.STRING);
            List<ItemTrackingManager.ItemInfo> items = plugin.getHistoryGUI().getItemsPendingBulkDeletion().remove(player.getUniqueId());

            if ("bulk-delete".equals(action)) {
                if (items != null && !items.isEmpty()) {

                    List<String> itemIds = items.stream()
                            .map(ItemTrackingManager.ItemInfo::getItemId)
                            .collect(Collectors.toList());

                    trackingManager.logBulkDeletion(player, items);
                    trackingManager.removeItemsTracking(itemIds);
                    trackingManager.addItemsToRemovalList(itemIds);

                    player.sendMessage(miniMessage.deserialize(prefix + "<green>Registros de <yellow>" + items.size() + "<green> items eliminados exitosamente."));
                    player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key(), Source.MASTER, 0.5F, 1.2F));

                    player.closeInventory();

                    HistoryGUI historyGUI = plugin.getHistoryGUI();
                    boolean wasSearching = historyGUI.isInSearchMode(player);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (wasSearching) {
                            String lastSearch = historyGUI.getLastSearchTerm(player.getUniqueId());
                            if (lastSearch != null) {
                                historyGUI.processSearch(player, lastSearch);
                            } else {
                                historyGUI.openHistoryMenu(player, 1);
                            }
                        } else {
                            historyGUI.openHistoryMenu(player, 1);
                        }
                    });

                } else {
                    player.sendMessage(miniMessage.deserialize(prefix + "<red>Error. No se encontraron los items a eliminar. Inténtalo de nuevo."));
                    player.closeInventory();
                }
            } else if ("cancel".equals(action)) {
                player.closeInventory();
                player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getHistoryGUI().openHistoryMenu(player, plugin.getHistoryGUI().getCurrentPage(player.getUniqueId()));
                });
            }
        }
    }

    @EventHandler
    public void onDeleteConfirmClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof HistoryGUI.DeleteConfirmHolder) {
            plugin.getHistoryGUI().getItemPendingDeletion().remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onBulkDeleteConfirmClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof HistoryGUI.BulkDeleteConfirmHolder) {
            plugin.getHistoryGUI().getItemsPendingBulkDeletion().remove(event.getPlayer().getUniqueId());
        }
    }
}