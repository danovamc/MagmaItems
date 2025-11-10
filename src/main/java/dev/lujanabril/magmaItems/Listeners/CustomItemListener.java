package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.CustomItemManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CustomItemListener implements Listener {
    private final Main plugin;
    private final CustomItemManager customItemManager;
    private final int MAX_BLOCKS_PER_OPERATION = 130;
    private boolean isProcessingMultiBreak = false;
    private final Random random = new Random();

    public CustomItemListener(Main plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBlockBreak(BlockBreakEvent event) {
        if (!this.isProcessingMultiBreak) {
            Player player = event.getPlayer();
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && handItem.getType() != Material.AIR) {
                if (this.customItemManager.isCustomItem(handItem)) {
                    CustomItemManager.CustomItemData itemData = this.customItemManager.getCustomItemData(handItem);
                    if (itemData != null && itemData.getType().equalsIgnoreCase("DRILL")) {
                        Block brokenBlock = event.getBlock();
                        if (itemData.getAllowedMaterials().contains(brokenBlock.getType())) {
                            BlockFace face = player.getFacing();
                            List<Block> blocksToBreak = this.getBlocksToBreak(brokenBlock, face, itemData);
                            if (blocksToBreak.size() > 130) {
                                blocksToBreak = blocksToBreak.subList(0, 130);
                            }

                            this.processBlocksAsPlayer(player, handItem, blocksToBreak, itemData);
                        }
                    }
                }
            }
        }
    }

    private void processBlocksAsPlayer(Player player, ItemStack handItem, List<Block> blocksToBreak, CustomItemManager.CustomItemData itemData) {
        List<Block> validBlocks = new ArrayList();

        for(Block block : blocksToBreak) {
            if (block.getChunk().isLoaded() && itemData.getAllowedMaterials().contains(block.getType())) {
                validBlocks.add(block);
            }
        }

        if (!validBlocks.isEmpty()) {
            int blocksProcessed = 0;

            try {
                this.isProcessingMultiBreak = true;

                for(Block block : validBlocks) {
                    BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                    Bukkit.getPluginManager().callEvent(breakEvent);
                    if (!breakEvent.isCancelled()) {
                        if (player.getGameMode() != GameMode.CREATIVE) {
                            block.breakNaturally(handItem);
                            ++blocksProcessed;
                        } else {
                            block.setType(Material.AIR);
                        }
                    }
                }
            } finally {
                this.isProcessingMultiBreak = false;
            }

            if (blocksProcessed > 0 && player.getGameMode() != GameMode.CREATIVE) {
                int damageToApply;
                if (itemData.isMaxDamage()) {
                    damageToApply = itemData.getDamage();
                } else {
                    damageToApply = 1;
                }

                this.damageItem(player, handItem, damageToApply);
            }

        }
    }

    private List<Block> getBlocksToBreak(Block centerBlock, BlockFace face, CustomItemManager.CustomItemData itemData) {
        List<Block> blocks = new ArrayList();
        int width = Math.min(itemData.getWidth(), 7);
        int height = Math.min(itemData.getHeight(), 7);
        int depth = Math.min(itemData.getDepth(), 5);
        int widthRadius = width / 2;
        int heightRadius = height / 2;
        BlockFace[] directions = this.getDirectionsForFace(face);
        BlockFace horizontal = directions[0];
        BlockFace vertical = directions[1];

        for(int d = 0; d < depth; ++d) {
            Block depthBlock = centerBlock.getRelative(face, d);

            for(int h = -widthRadius; h <= widthRadius; ++h) {
                for(int v = -heightRadius; v <= heightRadius; ++v) {
                    if (h != 0 || v != 0 || d != 0) {
                        Block relativeBlock = depthBlock.getRelative(horizontal, h).getRelative(vertical, v);
                        blocks.add(relativeBlock);
                    }
                }
            }
        }

        return blocks;
    }

    private BlockFace[] getDirectionsForFace(BlockFace face) {
        if (face != BlockFace.NORTH && face != BlockFace.SOUTH) {
            if (face != BlockFace.EAST && face != BlockFace.WEST) {
                return face != BlockFace.UP && face != BlockFace.DOWN ? new BlockFace[]{BlockFace.EAST, BlockFace.UP} : new BlockFace[]{BlockFace.EAST, BlockFace.NORTH};
            } else {
                return new BlockFace[]{BlockFace.NORTH, BlockFace.UP};
            }
        } else {
            return new BlockFace[]{BlockFace.EAST, BlockFace.UP};
        }
    }

    private void damageItem(Player player, ItemStack item, int damage) {
        if (item != null && item.getType() != Material.AIR) {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (!meta.isUnbreakable()) {
                    if (meta instanceof Damageable) {
                        int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
                        if (unbreakingLevel > 0) {
                            int actualDamage = 0;

                            for(int i = 0; i < damage; ++i) {
                                if (this.random.nextInt(unbreakingLevel + 1) == 0) {
                                    ++actualDamage;
                                }
                            }

                            damage = actualDamage;
                        }

                        if (damage <= 0) {
                            return;
                        }

                        Damageable damageable = (Damageable)meta;
                        int currentDamage = damageable.getDamage();
                        int newDamage = currentDamage + damage;
                        if (newDamage >= item.getType().getMaxDurability()) {
                            player.getInventory().setItemInMainHand((ItemStack)null);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                        } else {
                            damageable.setDamage(newDamage);
                            item.setItemMeta(meta);
                        }
                    }

                }
            }
        }
    }
}
