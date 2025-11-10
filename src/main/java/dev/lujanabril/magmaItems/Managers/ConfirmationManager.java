package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Listeners.ItemListener;
import dev.lujanabril.magmaItems.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationManager implements Listener {
    private final Main plugin;
    private final ItemListener itemListener;
    private final ItemManager itemManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey ITEM_ID_KEY;
    private final Map<UUID, ConfirmationData> activeConfirmations = new ConcurrentHashMap();
    private final Component confirmTitle;
    private ItemStack confirmButtonCache;
    private ItemStack cancelButtonCache;

    public ConfirmationManager(Main plugin, ItemListener itemListener) {
        this.plugin = plugin;
        this.itemListener = itemListener;
        this.itemManager = plugin.getItemManager();
        this.ACTION_KEY = new NamespacedKey(plugin, "confirm_action");
        this.ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String titleStr = this.parsePlaceholders(plugin.getConfig().getString("confirmation.title", "<dark_green>Confirmar"));
        this.confirmTitle = this.miniMessage.deserialize(titleStr);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openConfirmationMenu(Player player, ItemStack originalItem, String itemId) {
        int itemSlot = this.findItemSlot(player, originalItem, itemId);
        ConfirmMenuHolder holder = new ConfirmMenuHolder(player.getUniqueId());
        this.activeConfirmations.put(player.getUniqueId(), new ConfirmationData(originalItem, itemId, itemSlot));
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, this.confirmTitle);
            holder.setInventory(inventory);
            ItemStack confirmButton = this.getConfirmButton();
            ItemStack cancelButton = this.getCancelButton();
            ItemStack itemDisplay = originalItem.clone();
            inventory.setItem(0, confirmButton);
            inventory.setItem(2, itemDisplay);
            inventory.setItem(4, cancelButton);
            player.openInventory(inventory);
        });
    }

    private int findItemSlot(Player player, ItemStack originalItem, String itemId) {
        ItemStack[] contents = player.getInventory().getContents();

        for(int i = 0; i < contents.length; ++i) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(originalItem) && this.itemManager.isMagmaItem(item) && itemId.equals(this.itemManager.getItemId(item))) {
                return i;
            }
        }

        return -1;
    }

    private boolean playerStillHasItem(Player player, ConfirmationData data) {
        if (data.getOriginalSlot() >= 0) {
            ItemStack itemInSlot = player.getInventory().getItem(data.getOriginalSlot());
            if (this.isMatchingItem(itemInSlot, data.getItemId())) {
                return true;
            }
        }

        return this.findItemInInventory(player, data.getItemId()) != null;
    }

    private boolean isMatchingItem(ItemStack item, String itemId) {
        return item != null && this.itemManager.isMagmaItem(item) && itemId.equals(this.itemManager.getItemId(item));
    }

    private ItemStack findItemInInventory(Player player, String itemId) {
        for(ItemStack item : player.getInventory().getContents()) {
            if (this.isMatchingItem(item, itemId)) {
                return item;
            }
        }

        return null;
    }

    private ItemStack getConfirmButton() {
        if (this.confirmButtonCache == null) {
            String confirmText = this.parsePlaceholders(this.plugin.getConfig().getString("confirmation.confirm-text", "<green>Confirmar"));
            List<String> confirmLore = this.getConfigLore("confirmation.confirm-lore", "<green>Haz clic para confirmar");
            Material confirmMaterial = Material.valueOf(this.plugin.getConfig().getString("confirmation.confirm-material", "GREEN_WOOL"));
            this.confirmButtonCache = this.createButton(confirmMaterial, confirmText, confirmLore, "confirm");
        }

        return this.confirmButtonCache.clone();
    }

    private ItemStack getCancelButton() {
        if (this.cancelButtonCache == null) {
            String cancelText = this.parsePlaceholders(this.plugin.getConfig().getString("confirmation.cancel-text", "<red>Cancelar"));
            List<String> cancelLore = this.getConfigLore("confirmation.cancel-lore", "<red>Haz clic para cancelar");
            Material cancelMaterial = Material.valueOf(this.plugin.getConfig().getString("confirmation.cancel-material", "RED_WOOL"));
            this.cancelButtonCache = this.createButton(cancelMaterial, cancelText, cancelLore, "cancel");
        }

        return this.cancelButtonCache.clone();
    }

    private ItemStack createButton(Material material, String displayName, List<String> lore, String action) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(this.miniMessage.deserialize(displayName));
            List<Component> parsedLore = new ArrayList();

            for(String loreLine : lore) {
                parsedLore.add(this.miniMessage.deserialize(this.parsePlaceholders(loreLine)));
            }

            meta.lore(parsedLore);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(this.ACTION_KEY, PersistentDataType.STRING, action);
            button.setItemMeta(meta);
        }

        return button;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            if (event.getInventory().getHolder() instanceof ConfirmMenuHolder) {
                event.setCancelled(true);
                Player player = (Player)event.getWhoClicked();
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta()) {
                    ItemMeta meta = clicked.getItemMeta();
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    if (container.has(this.ACTION_KEY, PersistentDataType.STRING)) {
                        String action = (String)container.get(this.ACTION_KEY, PersistentDataType.STRING);
                        ConfirmationData data = (ConfirmationData)this.activeConfirmations.get(player.getUniqueId());
                        if (data != null) {
                            Bukkit.getScheduler().runTask(this.plugin, () -> {
                                player.closeInventory();
                                if ("confirm".equals(action)) {
                                    if (this.playerStillHasItem(player, data)) {
                                        ItemStack actualItem = this.findItemInInventory(player, data.getItemId());
                                        this.itemListener.executeItemActions(player, actualItem, data.getItemId());
                                    } else {
                                        String message = this.parsePlaceholders(this.plugin.getConfig().getString("confirmation.item-missing", "<red><b>ERROR</b> <dark_gray>▸</dark_gray> <white>¡No puedes hacer esto!</white> <gray><i>¿Que estas intentando? el Staff ha sido notificado</i></gray>"));
                                        player.sendMessage(this.miniMessage.deserialize(message));
                                    }
                                }

                            });
                        }
                    }

                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            if (event.getInventory().getHolder() instanceof ConfirmMenuHolder) {
                Player player = (Player)event.getPlayer();
                this.activeConfirmations.remove(player.getUniqueId());
            }
        }
    }

    private String parsePlaceholders(String message) {
        if (message == null) {
            return null;
        } else {
            Map<String, Object> placeholderSection = (Map<String, Object>)(this.plugin.getConfig().getConfigurationSection("placeholders") != null ? this.plugin.getConfig().getConfigurationSection("placeholders").getValues(false) : new HashMap());
            String parsed = message;

            for(Map.Entry<String, Object> entry : placeholderSection.entrySet()) {
                if (entry.getValue() instanceof String) {
                    parsed = parsed.replace((CharSequence)entry.getKey(), (String)entry.getValue());
                }
            }

            return parsed;
        }
    }

    private List<String> getConfigLore(String path, String... defaults) {
        List<String> lore = this.plugin.getConfig().getStringList(path);
        return lore.isEmpty() && defaults.length > 0 ? Arrays.asList(defaults) : lore;
    }

    private static class ConfirmationData {
        private final ItemStack originalItem;
        private final String itemId;
        private final int originalSlot;

        public ConfirmationData(ItemStack originalItem, String itemId, int originalSlot) {
            this.originalItem = originalItem.clone();
            this.itemId = itemId;
            this.originalSlot = originalSlot;
        }

        public ItemStack getOriginalItem() {
            return this.originalItem;
        }

        public String getItemId() {
            return this.itemId;
        }

        public int getOriginalSlot() {
            return this.originalSlot;
        }
    }

    public class ConfirmMenuHolder implements InventoryHolder {
        private final UUID playerUuid;
        private Inventory inventory;

        public ConfirmMenuHolder(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        public Inventory getInventory() {
            return this.inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID getPlayerUuid() {
            return this.playerUuid;
        }
    }
}
