package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class HistoryGUIListener implements Listener {
    private final Main plugin;
    private final ItemTrackingManager trackingManager;
    private final MiniMessage miniMessage;
    private final String prefix;

    public HistoryGUIListener(Main plugin, MiniMessage miniMessage, String prefix) { // Añadimos MiniMessage y prefix al constructor
        this.plugin = plugin;
        this.trackingManager = plugin.getItemTrackingManager();
        this.miniMessage = miniMessage;
        this.prefix = prefix;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("Historial - [")) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player)event.getWhoClicked();
                if (event.getCurrentItem() != null) {
                    switch (event.getSlot()) {
                        case 3:
                            if (event.getCurrentItem().getType().toString().equals("HOPPER")) {
                                this.plugin.getHistoryGUI().cycleSortType(player);
                            }
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
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player)event.getPlayer();
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
            // Mensaje corregido usando MiniMessage
            String message = "<red>¡Has <yellow>reiniciado <white>la busqueda, ahora se muestran todos los items!";
            player.sendMessage(this.miniMessage.deserialize(this.prefix + message));
        }

        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
    }

    private boolean hasActiveSearch(Player player) {
        String title = "";
        return title.contains("[Búsqueda]");
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
                    // Si el lore se formó correctamente con MiniMessage o ChatColor, stripColor debería funcionar
                    // para eliminar los códigos de color antes de buscar el texto.
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
}