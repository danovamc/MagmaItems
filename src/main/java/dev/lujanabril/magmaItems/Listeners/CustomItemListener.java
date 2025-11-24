package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.CustomItemManager;
import dev.lujanabril.magmaItems.Managers.ItemManager;
import dev.lujanabril.magmaItems.Managers.SellManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CustomItemListener implements Listener {
    private final Main plugin;
    private final CustomItemManager customItemManager;
    private final SellManager sellManager;
    private final ItemManager itemManager;

    private final int MAX_BLOCKS_PER_OPERATION = 130;

    // --- CAMPOS MODIFICADOS (ya no son 'final') ---
    private int drillBlocksPerBatch;
    private int drillDelayBetweenTicks;
    private List<String> autosellWorlds;

    private final NamespacedKey autosellToggleKey;
    private final Set<UUID> drillingPlayers = new HashSet<>();
    private final Map<UUID, DrillOperationData> activeDrillOperations = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public CustomItemListener(Main plugin, CustomItemManager customItemManager, SellManager sellManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
        this.sellManager = sellManager;

        this.itemManager = plugin.getItemManager();

        this.drillBlocksPerBatch = plugin.getConfig().getInt("drill.optimization.blocks-per-batch", 40);
        this.drillDelayBetweenTicks = plugin.getConfig().getInt("drill.optimization.delay-between-ticks", 1);
        this.autosellWorlds = plugin.getConfig().getStringList("drill.optimization.autosell-worlds");

        this.autosellToggleKey = this.itemManager.getAutosellToggleKey();
    }

    // --- MÉTODO NUEVO ---
    /**
     * Recarga los valores de configuración de esta clase.
     */
    public void reload() {
        this.drillBlocksPerBatch = plugin.getConfig().getInt("drill.optimization.blocks-per-batch", 40);
        this.drillDelayBetweenTicks = plugin.getConfig().getInt("drill.optimization.delay-between-ticks", 1);
        this.autosellWorlds = plugin.getConfig().getStringList("drill.optimization.autosell-worlds");
    }
    // --- FIN MÉTODO NUEVO ---

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (this.drillingPlayers.contains(playerUuid)) {
            return;
        }

        if (this.activeDrillOperations.containsKey(playerUuid)) {
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.getType() != Material.AIR) {
            if (this.customItemManager.isCustomItem(handItem)) {
                CustomItemManager.CustomItemData itemData = this.customItemManager.getCustomItemData(handItem);

                if (itemData != null && itemData.getType().equalsIgnoreCase("DRILL")) {
                    Block brokenBlock = event.getBlock();

                    if (itemData.getAllowedMaterials().contains(brokenBlock.getType())) {
                        BlockFace face = player.getFacing();
                        List<Block> blocksToBreak = this.getBlocksToBreak(brokenBlock, face, itemData);

                        if (blocksToBreak.size() > MAX_BLOCKS_PER_OPERATION) {
                            blocksToBreak = blocksToBreak.subList(0, MAX_BLOCKS_PER_OPERATION);
                        }

                        if (!blocksToBreak.isEmpty()) {

                            // --- INICIO LÓGICA DE AUTOSELL MODIFICADA ---
                            boolean itemIsCapableOfAutosell = itemData.isDrillAutoSell();
                            String currentWorld = player.getWorld().getName();
                            boolean worldIsAllowed = autosellWorlds.isEmpty() || autosellWorlds.contains(currentWorld);

                            // Revisar el NBT del ítem
                            boolean playerToggledOn = false;
                            ItemMeta meta = handItem.getItemMeta();
                            if (meta != null) {
                                PersistentDataContainer container = meta.getPersistentDataContainer();
                                playerToggledOn = container.getOrDefault(this.autosellToggleKey, PersistentDataType.BYTE, (byte)0) == (byte)1;
                            }

                            // La condición final
                            boolean performAutosell = itemIsCapableOfAutosell && worldIsAllowed && playerToggledOn;

                            // --- CORRECCIÓN APLICADA AQUÍ ---
                            if (performAutosell) {
                                // 1. Añadimos el bloque central (el que el jugador rompió) a la lista del drill
                                // para que sea procesado por el sistema de venta.
                                blocksToBreak.add(0, brokenBlock);

                                // 2. Cancelamos el evento original.
                                // Esto evita que Minecraft suelte el item al suelo, por lo que
                                // MagmaAutoPickup NO tendrá nada que recoger.
                                event.setCancelled(true);
                            }
                            // --------------------------------

                            DrillOperationData data = new DrillOperationData(
                                    player,
                                    handItem,
                                    itemData,
                                    new LinkedList<>(blocksToBreak),
                                    this.drillBlocksPerBatch,
                                    performAutosell
                            );

                            this.activeDrillOperations.put(playerUuid, data);
                            this.startDrillOperation(data);
                        }
                    }
                }
            }
        }
    }


    /**
     * Inicia la tarea BukkitRunnable que procesará la rotura de bloques en lotes.
     */
    private void startDrillOperation(DrillOperationData data) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!data.player.isOnline()) {
                    activeDrillOperations.remove(data.player.getUniqueId());
                    this.cancel();
                    return;
                }

                drillingPlayers.add(data.player.getUniqueId());

                int blocksProcessedThisBatch = 0;
                List<Block> validBlocksInBatch = new ArrayList<>();

                for (int i = 0; i < data.blocksPerBatch && !data.blocksToBreak.isEmpty(); i++) {
                    Block block = data.blocksToBreak.poll();

                    if (block.getChunk().isLoaded() && data.itemData.getAllowedMaterials().contains(block.getType())) {
                        validBlocksInBatch.add(block);
                    }
                }

                if (!validBlocksInBatch.isEmpty()) {
                    try {
                        for (Block block : validBlocksInBatch) {
                            BlockBreakEvent breakEvent = new BlockBreakEvent(block, data.player);
                            Bukkit.getPluginManager().callEvent(breakEvent);

                            if (!breakEvent.isCancelled()) {
                                if (data.player.getGameMode() != GameMode.CREATIVE) {

                                    if (data.performAutosell) {
                                        data.itemsToSell.put(block.getType(), data.itemsToSell.getOrDefault(block.getType(), 0) + 1);
                                        block.setType(Material.AIR);
                                    } else {
                                        // >>>>> INICIO CORRECCIÓN <<<<<
                                        // Verificamos si algún plugin (como AxMines) canceló los drops vanilla
                                        if (breakEvent.isDropItems()) {
                                            // Si nadie canceló los drops, rompemos normal (suelta items vanilla)
                                            block.breakNaturally(data.drillItem);
                                        } else {
                                            // Si AxMines dijo "sin drops" (setDropItems(false)),
                                            // solo quitamos el bloque sin soltar nada extra.
                                            block.setType(Material.AIR);
                                        }
                                        // >>>>> FIN CORRECCIÓN <<<<<
                                    }
                                    blocksProcessedThisBatch++;

                                } else {
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    } finally {
                        drillingPlayers.remove(data.player.getUniqueId());
                    }
                } else {
                    drillingPlayers.remove(data.player.getUniqueId());
                }

                if (data.player.getGameMode() != GameMode.CREATIVE) {
                    data.blocksBrokenSoFar += blocksProcessedThisBatch;
                }

                if (data.blocksToBreak.isEmpty()) {

                    if (data.performAutosell && data.itemsToSell != null && !data.itemsToSell.isEmpty()) {
                        CustomItemListener.this.sellManager.sellItemsToShop(data.player, data.itemsToSell, "messages.drill-sale-chat", "messages.drill-sale-title", "messages.drill-sale-subtitle");
                    }

                    if (data.blocksBrokenSoFar > 0 && data.player.getGameMode() != GameMode.CREATIVE) {
                        int totalDamage = data.itemData.isMaxDamage() ?
                                data.itemData.getDamage() :
                                data.blocksBrokenSoFar;

                        damageItem(data.player, data.drillItem, totalDamage);
                    }

                    activeDrillOperations.remove(data.player.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, this.drillDelayBetweenTicks); // Usa el campo de la clase
    }


    /**
     * Clase interna para guardar los datos de una operación de taladro.
     */
    private static class DrillOperationData {
        final Player player;
        final ItemStack drillItem;
        final CustomItemManager.CustomItemData itemData;
        final Queue<Block> blocksToBreak;
        final int blocksPerBatch;
        int blocksBrokenSoFar;

        final boolean itemCanAutosell;
        final boolean performAutosell;
        final Map<Material, Integer> itemsToSell;

        DrillOperationData(Player player, ItemStack drillItem, CustomItemManager.CustomItemData itemData, Queue<Block> blocksToBreak, int blocksPerBatch, boolean performAutosell) {
            this.player = player;
            this.drillItem = drillItem;
            this.itemData = itemData;
            this.blocksToBreak = blocksToBreak;
            this.blocksPerBatch = blocksPerBatch;
            this.blocksBrokenSoFar = 0;

            this.itemCanAutosell = itemData.isDrillAutoSell();
            this.performAutosell = performAutosell;
            this.itemsToSell = this.performAutosell ? new HashMap<>() : null;
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

                            // Lógica más segura:
                            if (player.getInventory().getItemInMainHand().equals(item)) {
                                player.getInventory().setItemInMainHand(null);
                            } else if (player.getInventory().getItemInOffHand().equals(item)) {
                                player.getInventory().setItemInOffHand(null);
                            } else {
                                player.getInventory().removeItem(item);
                            }

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