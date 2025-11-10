package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Managers.BombManager;
import dev.lujanabril.magmaItems.Managers.CustomItemManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BombListener implements Listener {
    private final CustomItemManager customItemManager;
    private final BombManager bombManager;

    public BombListener(CustomItemManager customItemManager, BombManager bombManager) {
        this.customItemManager = customItemManager;
        this.bombManager = bombManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && this.customItemManager.isBombItem(item)) {
                event.setCancelled(true);
                boolean isMainHand = event.getHand() == EquipmentSlot.HAND;
                this.bombManager.throwBomb(event.getPlayer(), item, isMainHand);
            }
        }
    }
}
