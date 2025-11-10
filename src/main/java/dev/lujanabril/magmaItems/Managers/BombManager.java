package dev.lujanabril.magmaItems.Managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import dev.lujanabril.magmaItems.Main;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class BombManager {
    private final Main plugin;
    private final CustomItemManager customItemManager;
    private Economy economy;
    private final Map<UUID, Long> playerCooldowns = new HashMap();
    private final DecimalFormat itemFormatter = new DecimalFormat("#,###");
    private final int BLOCKS_PER_TICK;
    private final int EXPLOSION_DELAY_TICKS;
    private final Map<UUID, ExplosionData> activeExplosions = new ConcurrentHashMap();

    public BombManager(Main plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
        this.BLOCKS_PER_TICK = plugin.getConfig().getInt("bomb.optimization.blocks-per-tick", 500);
        this.EXPLOSION_DELAY_TICKS = plugin.getConfig().getInt("bomb.optimization.delay-between-ticks", 1);
        this.setupEconomy();
    }

    private void setupEconomy() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            this.plugin.getLogger().warning("Vault no está instalado!");
        } else {
            RegisteredServiceProvider<Economy> rsp = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                this.plugin.getLogger().warning("No se encontró proveedor de economía!");
            } else {
                this.economy = (Economy)rsp.getProvider();
            }
        }
    }

    public void throwBomb(Player player, ItemStack bombItem, boolean isMainHand) {
        if (this.customItemManager.isBombItem(bombItem)) {
            if (!this.isWorldAllowed(player.getWorld().getName())) {
                String message = this.plugin.getConfig().getString("messages.world-not-allowed", "§c§l[BOMBA] §7No puedes usar bombas en este mundo.");
                player.sendMessage(message);
                player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            } else {
                CustomItemManager.BombItemData bombData = (CustomItemManager.BombItemData)this.customItemManager.getCustomItemData(bombItem);
                if (bombData != null) {
                    if (this.hasActiveCooldown(player, bombData)) {
                        double remainingTime = this.getRemainingCooldownPrecise(player, bombData);
                        String formattedTime = this.formatCooldownTime(remainingTime);
                        String cooldownMessage = this.plugin.getConfig().getString("messages.bomb-cooldown", "§c§l[BOMBA] §7Debes esperar §e{time} §7antes de usar otra bomba.").replace("{time}", formattedTime);
                        player.sendMessage(cooldownMessage);
                        player.playSound(player, Sound.ENTITY_ARROW_HIT, 0.1F, 0.1F);
                    } else {
                        Location throwLocation = player.getEyeLocation();
                        Vector direction = throwLocation.getDirection().multiply(bombData.getThrowDistance());
                        ItemStack displayItem = bombItem.clone();
                        displayItem.setAmount(1);
                        Item thrownItem = player.getWorld().dropItem(throwLocation, displayItem);
                        thrownItem.setVelocity(direction);
                        thrownItem.setPickupDelay(Integer.MAX_VALUE);

                        // Remover solo de la mano que se usó
                        if (isMainHand) {
                            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                            if (mainHandItem.getAmount() > 1) {
                                mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                            } else {
                                player.getInventory().setItemInMainHand(null);
                            }
                        } else {
                            ItemStack offHandItem = player.getInventory().getItemInOffHand();
                            if (offHandItem.getAmount() > 1) {
                                offHandItem.setAmount(offHandItem.getAmount() - 1);
                            } else {
                                player.getInventory().setItemInOffHand(null);
                            }
                        }

                        this.setCooldown(player, bombData);
                        this.startBombCountdown(thrownItem, bombData, player);
                        player.playSound(player, Sound.ENTITY_TNT_PRIMED, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    private boolean hasActiveCooldown(Player player, CustomItemManager.BombItemData bombData) {
        if (bombData.getCooldown() <= 0) {
            return false;
        } else {
            UUID playerId = player.getUniqueId();
            if (!this.playerCooldowns.containsKey(playerId)) {
                return false;
            } else {
                long currentTime = System.currentTimeMillis();
                long lastUseTime = (Long)this.playerCooldowns.get(playerId);
                long cooldownMillis = (long)bombData.getCooldown() * 1000L;
                return currentTime - lastUseTime < cooldownMillis;
            }
        }
    }

    private double getRemainingCooldownPrecise(Player player, CustomItemManager.BombItemData bombData) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastUseTime = (Long)this.playerCooldowns.get(playerId);
        long cooldownMillis = (long)bombData.getCooldown() * 1000L;
        long remainingMillis = cooldownMillis - (currentTime - lastUseTime);
        return Math.max((double)0.0F, (double)remainingMillis / (double)1000.0F);
    }

    private String formatCooldownTime(double seconds) {
        if (seconds >= (double)1.0F) {
            return seconds == Math.floor(seconds) ? String.format("%.0fs", seconds) : String.format("%.1fs", seconds);
        } else {
            return String.format("%.2fs", seconds);
        }
    }

    private void setCooldown(Player player, CustomItemManager.BombItemData bombData) {
        if (bombData.getCooldown() > 0) {
            this.playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

    }

    private boolean isWorldAllowed(String worldName) {
        List<String> allowedWorlds = this.plugin.getConfig().getStringList("bomb.allowed-worlds");
        return allowedWorlds.isEmpty() || allowedWorlds.contains(worldName);
    }

    private void startBombCountdown(final Item thrownItem, final CustomItemManager.BombItemData bombData, final Player thrower) {
        (new BukkitRunnable() {
            private int ticksLeft = bombData.getTime();

            public void run() {
                if (!thrownItem.isDead() && thrownItem.isValid()) {
                    Location loc = thrownItem.getLocation();
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.02);
                    if (this.ticksLeft % 20 == 0 && this.ticksLeft > 0) {
                        loc.getWorld().playSound(loc, Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                    }

                    --this.ticksLeft;
                    if (this.ticksLeft <= 0) {
                        BombManager.this.explodeBombOptimized(thrownItem.getLocation(), bombData, thrower);
                        thrownItem.remove();
                        this.cancel();
                    }

                } else {
                    this.cancel();
                }
            }
        }).runTaskTimer(this.plugin, 0L, 1L);
    }

    private void explodeBombOptimized(Location center, CustomItemManager.BombItemData bombData, Player thrower) {
        World world = center.getWorld();
        if (world != null) {
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F);
            world.spawnParticle(Particle.FLASH, center, 1, (double)0.5F, (double)0.5F, (double)0.5F, (double)0.0F);
            world.spawnParticle(Particle.FLAME, center, 50, bombData.getRadius() / (double)2.0F, bombData.getRadius() / (double)2.0F, bombData.getRadius() / (double)2.0F, 0.1);
            List<Block> blocksToDestroy = this.getBlocksInShape(center, bombData.getRadius(), bombData.getShape());
            if (!blocksToDestroy.isEmpty()) {
                UUID explosionId = UUID.randomUUID();
                ExplosionData explosionData = new ExplosionData(blocksToDestroy, thrower, center);
                this.activeExplosions.put(explosionId, explosionData);
                this.startOptimizedDestruction(explosionId);
            }
        }
    }

    private void startOptimizedDestruction(final UUID explosionId) {
        (new BukkitRunnable() {
            public void run() {
                ExplosionData data = (ExplosionData)BombManager.this.activeExplosions.get(explosionId);
                if (data == null) {
                    this.cancel();
                } else {
                    List<Block> currentBatch = new ArrayList();
                    int blocksProcessed = 0;

                    while(blocksProcessed < BombManager.this.BLOCKS_PER_TICK && !data.remainingBlocks.isEmpty()) {
                        Block block = (Block)data.remainingBlocks.remove(0);
                        if (BombManager.this.isWorldGuardEnabled() && !BombManager.this.canBreakBlock(block.getLocation(), data.player)) {
                            ++blocksProcessed;
                        } else {
                            currentBatch.add(block);
                            ++blocksProcessed;
                        }
                    }

                    if (!currentBatch.isEmpty()) {
                        BombManager.this.processBatch(currentBatch, data);
                        if (!currentBatch.isEmpty()) {
                            Location batchCenter = ((Block)currentBatch.get(currentBatch.size() / 2)).getLocation();
                            batchCenter.getWorld().spawnParticle(Particle.LARGE_SMOKE, batchCenter, 3, (double)0.5F, (double)0.5F, (double)0.5F, 0.02);
                        }
                    }

                    if (data.remainingBlocks.isEmpty()) {
                        BombManager.this.finishExplosion(data);
                        BombManager.this.activeExplosions.remove(explosionId);
                        this.cancel();
                    }

                }
            }
        }).runTaskTimer(this.plugin, 0L, (long)this.EXPLOSION_DELAY_TICKS);
    }

    private void processBatch(List<Block> blocks, ExplosionData data) {
        for(Block block : blocks) {
            Material material = block.getType();
            data.itemsToSell.put(material, (Integer)data.itemsToSell.getOrDefault(material, 0) + 1);
            block.setType(Material.AIR);
        }

    }

    private void finishExplosion(ExplosionData data) {
        this.sellItemsToShop(data.player, data.itemsToSell);
        data.center.getWorld().spawnParticle(Particle.EXPLOSION, data.center, 1, (double)0.0F, (double)0.0F, (double)0.0F, (double)0.0F);
        data.center.getWorld().playSound(data.center, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.8F);
    }

    private List<Block> getBlocksInShape(Location center, double radius, String shape) {
        List<Block> blocks = new ArrayList();
        World world = center.getWorld();
        if (world == null) {
            return blocks;
        } else {
            int radiusInt = (int)Math.ceil(radius);

            for(int x = -radiusInt; x <= radiusInt; ++x) {
                for(int y = -radiusInt; y <= radiusInt; ++y) {
                    for(int z = -radiusInt; z <= radiusInt; ++z) {
                        Location blockLoc = center.clone().add((double)x, (double)y, (double)z);
                        boolean shouldInclude = false;
                        double distance = center.distance(blockLoc);
                        switch (shape.toUpperCase()) {
                            case "SPHERE" -> shouldInclude = distance <= radius;
                            case "CUBE" -> shouldInclude = (double)Math.abs(x) <= radius && (double)Math.abs(y) <= radius && (double)Math.abs(z) <= radius;
                            default -> shouldInclude = distance <= radius;
                        }

                        if (shouldInclude) {
                            Block block = world.getBlockAt(blockLoc);
                            if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                                blocks.add(block);
                            }
                        }
                    }
                }
            }

            return blocks;
        }
    }

    private void sellItemsToShop(Player player, Map<Material, Integer> itemsToSell) {
        if (!itemsToSell.isEmpty() && this.economy != null && this.isShopGuiPlusEnabled()) {
            double totalEarnings = (double)0.0F;
            int totalItemsSold = 0;

            for(Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
                Material material = (Material)entry.getKey();
                int quantity = (Integer)entry.getValue();

                try {
                    double sellPricePerItem = ShopGuiPlusApi.getItemStackPriceSell(player, new ItemStack(material, 1));
                    if (sellPricePerItem > (double)0.0F) {
                        double totalPrice = sellPricePerItem * (double)quantity;
                        this.economy.depositPlayer(player, totalPrice);
                        totalEarnings += totalPrice;
                        totalItemsSold += quantity;
                    }
                } catch (Exception e) {
                    Logger var10000 = this.plugin.getLogger();
                    String var10001 = material.name();
                    var10000.warning("Error al procesar venta de " + var10001 + " para " + player.getName() + ": " + e.getMessage());
                }
            }

            if (totalItemsSold > 0) {
                String chatMessage = this.plugin.getConfig().getString("messages.bomb-sale-chat", "§a§l[BOMBA] §7Has vendido §e{items} §7items por §a${money}").replace("{items}", this.itemFormatter.format((long)totalItemsSold)).replace("{money}", this.formatMoney(totalEarnings));
                player.sendMessage(chatMessage);
                String titleMessage = this.plugin.getConfig().getString("messages.bomb-sale-title", "§a+${money}").replace("{money}", this.formatMoney(totalEarnings));
                String subtitleMessage = this.plugin.getConfig().getString("messages.bomb-sale-subtitle", "§7(§e{items} items§7)").replace("{money}", this.formatMoney(totalEarnings));
                player.sendTitle(titleMessage, subtitleMessage, 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            }

        }
    }

    private String formatMoney(double amount) {
        if (amount >= (double)1.0E9F) {
            double billions = amount / (double)1.0E9F;
            return billions == Math.floor(billions) ? String.format("%.0fB", billions) : String.format("%.1fB", billions).replaceAll("\\.0", "");
        } else if (amount >= (double)1000000.0F) {
            double millions = amount / (double)1000000.0F;
            return millions == Math.floor(millions) ? String.format("%.0fM", millions) : String.format("%.1fM", millions).replaceAll("\\.0", "");
        } else if (amount >= (double)1000.0F) {
            double thousands = amount / (double)1000.0F;
            return thousands == Math.floor(thousands) ? String.format("%.0fk", thousands) : String.format("%.1fk", thousands).replaceAll("\\.0", "");
        } else {
            return amount == Math.floor(amount) ? String.format("%.0f", amount) : String.format("%.2f", amount);
        }
    }

    private boolean isWorldGuardEnabled() {
        return this.plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    private boolean isShopGuiPlusEnabled() {
        return this.plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus") != null;
    }

    private boolean canBreakBlock(Location location, Player player) {
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) {
                return true;
            } else {
                BlockVector3 blockVector = BukkitAdapter.asBlockVector(location);
                ApplicableRegionSet regions = regionManager.getApplicableRegions(blockVector);
                return regions.size() == 0 ? true : regions.testState(WorldGuardPlugin.inst().wrapPlayer(player), new StateFlag[]{Flags.BLOCK_BREAK});
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking WorldGuard permissions: " + e.getMessage());
            return false;
        }
    }

    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        this.playerCooldowns.entrySet().removeIf((entry) -> currentTime - (Long)entry.getValue() > 3600000L);
    }

    public void cleanupActiveExplosions() {
        this.activeExplosions.clear();
    }

    private static class ExplosionData {
        final List<Block> remainingBlocks;
        final Map<Material, Integer> itemsToSell;
        final Player player;
        final Location center;

        ExplosionData(List<Block> blocks, Player player, Location center) {
            this.remainingBlocks = new ArrayList(blocks);
            this.itemsToSell = new HashMap();
            this.player = player;
            this.center = center;
        }
    }
}