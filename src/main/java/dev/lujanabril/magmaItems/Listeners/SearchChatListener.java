package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SearchChatListener implements Listener {
    private final Main plugin;

    public SearchChatListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.getHistoryGUI().isInSearchMode(player)) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            player.sendMessage(this.formatMessage("&7Buscando: &f" + message + "&7..."));
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.plugin.getHistoryGUI().processSearch(player, message));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.getHistoryGUI().isInSearchMode(player)) {
            this.plugin.getHistoryGUI().cancelSearchMode(player);
        }

    }

    private String formatMessage(String message) {
        if (message == null) {
            return "";
        } else {
            String prefix = this.plugin.getConfig().getString("prefix", "&7[&cMagmaItems&7] ");
            return ChatColor.translateAlternateColorCodes('&', message.replace("%prefix%", prefix));
        }
    }
}
