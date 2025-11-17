package dev.lujanabril.magmaItems.Listeners;

// --- Importaciones necesarias ---
import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType; // Importante
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack; // Importante
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer; // Importante
import org.bukkit.persistence.PersistentDataType; // Importante

import java.util.List;

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

            // --- INICIO DEL NUEVO CÓDIGO (Detectar 'Q') ---
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                if (event.getSlot() >= 9 && event.getSlot() < 36 && event.getCurrentItem() != null) {

                    // 1. Chequeo de Seguridad (Whitelist)
                    List<String> whitelist = plugin.getConfig().getStringList("Id-remover-whitelist");
                    if (!whitelist.contains(player.getName())) {
                        player.sendMessage(miniMessage.deserialize(prefix + "<red>No tienes permiso para eliminar registros del historial."));
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO.key(), Source.MASTER, 1.0F, 1.0F));
                        return;
                    }

                    // 2. Obtener el ItemInfo
                    ItemTrackingManager.ItemInfo info = getItemInfoFromClickedItem(event.getCurrentItem());
                    if (info != null) {
                        // 3. Abrir menú de confirmación
                        plugin.getHistoryGUI().setSwitchingMenu(player, true);
                        plugin.getHistoryGUI().openDeleteConfirmation(player, info);
                    }
                }
                return; // Importante: Salir después de manejar el drop
            }
            // --- FIN DEL NUEVO CÓDIGO ---

            if (event.getCurrentItem() != null) {
                switch (event.getSlot()) {
                    case 3:
                        if (event.getCurrentItem().getType().toString().equals("HOPPER")) {
                            this.plugin.getHistoryGUI().cycleSortType(player);
                        }
                        break; // <-- AÑADIDO: Faltaba un break
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

            // --- INICIO DE LA MODIFICACIÓN ---
            // Verificamos si estamos cambiando de menú intencionalmente
            if (plugin.getHistoryGUI().isSwitchingMenu(player)) {
                // Si es así, simplemente reseteamos el flag y NO limpiamos la búsqueda
                plugin.getHistoryGUI().setSwitchingMenu(player, false);
                return;
            }
            // --- FIN DE LA MODIFICACIÓN ---

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
        // Corrección: Debe verificar el título del inventario actual del jugador
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

    private void processItemClick(Player player, InventoryClickEvent event) {
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String currentOwnerLine = null;

                for(String line : lore) {
                    if (ChatColor.stripColor(line).contains("Poseedor")) {
                        currentOwnerLine = ChatColor.stripColor(line);
                        break;
                    }
                }

                if (currentOwnerLine != null) {
                    String[] parts = currentOwnerLine.split(":");
                    if (parts.length >= 2) {
                        String ownerInfo = parts[1].trim();
                        String ownerName = ownerInfo.split("\\[")[0].trim();
                        boolean isOnline = ownerInfo.contains("[Online]");
                        if (isOnline) {
                            player.performCommand("tp " + ownerName);
                        } else {
                            player.performCommand("otp " + ownerName);
                        }

                        player.closeInventory();
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT.key(), Source.MASTER, 1.0F, 1.0F));
                    }
                }
            }
        }
    }

    // --- INICIO DE MÉTODOS AÑADIDOS ---

    /**
     * Extrae el ItemInfo de un item clickeado en la GUI de historial.
     */
    private ItemTrackingManager.ItemInfo getItemInfoFromClickedItem(ItemStack clickedItem) {
        if (clickedItem == null) return null;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String idLine = null;
                for (String line : lore) {
                    // Usa el prefijo exacto del lore para encontrar la ID
                    String strippedLine = ChatColor.stripColor(line);
                    if (strippedLine.startsWith("ID: ")) {
                        idLine = strippedLine;
                        break;
                    }
                }

                if (idLine != null) {
                    try {
                        // Extrae la ID después de "ID: "
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

    /**
     * Maneja los clics en el menú de confirmación de borrado.
     */
    @EventHandler
    public void onDeleteConfirmClick(InventoryClickEvent event) {
        // Comprueba si es el inventario de confirmación de borrado
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
                    // 1. Loggear la eliminación
                    trackingManager.logDeletion(player, info);

                    // 2. Eliminar el registro
                    trackingManager.removeItemTracking(info.getItemId());

                    // --- 3. AÑADIR A LA LISTA NEGRA (NUEVO) ---
                    trackingManager.addItemToRemovalList(info.getItemId());

                    // 4. Notificar y refrescar (esto ya lo tenías)
                    player.sendMessage(miniMessage.deserialize(prefix + "<green>Registro <yellow>" + info.getItemId() + "<green> eliminado exitosamente."));
                    player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key(), Source.MASTER, 0.5F, 1.2F));

                    // --- INICIO DE LA MODIFICACIÓN ---
                    player.closeInventory();

                    HistoryGUI historyGUI = plugin.getHistoryGUI();
                    // Verificamos si el jugador ESTABA en modo búsqueda
                    boolean wasSearching = historyGUI.isInSearchMode(player);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (wasSearching) {
                            // Si estaba buscando, obtenemos el término y re-procesamos la búsqueda
                            String lastSearch = historyGUI.getLastSearchTerm(player.getUniqueId());
                            if (lastSearch != null) {
                                // Esto actualizará la lista de resultados y reabrirá el menú
                                historyGUI.processSearch(player, lastSearch);
                            } else {
                                // Fallback por si acaso, aunque no debería pasar
                                historyGUI.openHistoryMenu(player, 1);
                            }
                        } else {
                            // Si no estaba buscando, solo refrescamos la página 1
                            historyGUI.openHistoryMenu(player, 1);
                        }
                    });
                    // --- FIN DE LA MODIFICACIÓN ---

                } else {
                    player.sendMessage(miniMessage.deserialize(prefix + "<red>Error. No se encontró el item a eliminar. Inténtalo de nuevo."));
                    player.closeInventory();
                }
            } else if ("cancel".equals(action)) {
                // Esta parte ya estaba bien, la dejamos como está.
                // Al cerrar, onInventoryClose NO se disparará (por el flag),
                // y al reabrir el menú, el modo búsqueda seguirá activo.
                player.closeInventory();
                player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
                // Reabrir el historial principal
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getHistoryGUI().openHistoryMenu(player, plugin.getHistoryGUI().getCurrentPage(player.getUniqueId()));
                });
            }
        }
    }

    /**
     * Limpia el item pendiente si se cierra el menú de confirmación.
     */
    @EventHandler
    public void onDeleteConfirmClose(InventoryCloseEvent event) {
        // Limpia el map si el jugador cierra el inventario de confirmación sin elegir
        if (event.getInventory().getHolder() instanceof HistoryGUI.DeleteConfirmHolder) {
            plugin.getHistoryGUI().getItemPendingDeletion().remove(event.getPlayer().getUniqueId());
        }
    }

    // --- FIN DE MÉTODOS AÑADIDOS ---
}