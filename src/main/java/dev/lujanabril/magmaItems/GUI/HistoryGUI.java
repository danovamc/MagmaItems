package dev.lujanabril.magmaItems.GUI;

// --- Importaciones Requeridas ---
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
// --- Fin Importaciones ---

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryGUI {
    private final Main plugin;
    private ItemTrackingManager trackingManager;
    private static final int ITEMS_PER_PAGE = 27;
    private final Map<UUID, Integer> playerPages = new HashMap();
    private final Map<UUID, List<ItemTrackingManager.ItemInfo>> playerSearchResults = new HashMap();
    private final Set<UUID> playersInSearchMode = new HashSet();
    private final Map<UUID, SortType> playerSortTypes = new HashMap();
    private final Map<UUID, ItemTrackingManager.ItemInfo> itemPendingDeletion = new HashMap();
    private final Map<UUID, List<ItemTrackingManager.ItemInfo>> itemsPendingBulkDeletion = new HashMap<>();

    private final Set<UUID> isSwitchingMenu = new HashSet<>();
    private final Map<UUID, String> playerLastSearch = new HashMap<>();

    private final MiniMessage miniMessage;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public HistoryGUI(Main plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
    }

    public int getCurrentPage(UUID playerId) {
        return (Integer)this.playerPages.getOrDefault(playerId, 1);
    }

    public void setCurrentPage(UUID playerId, int page) {
        this.playerPages.put(playerId, page);
    }

    public Map<UUID, ItemTrackingManager.ItemInfo> getItemPendingDeletion() {
        return this.itemPendingDeletion;
    }

    public Map<UUID, List<ItemTrackingManager.ItemInfo>> getItemsPendingBulkDeletion() {
        return this.itemsPendingBulkDeletion;
    }

    public SortType getCurrentSortType(UUID playerId) {
        return (SortType)this.playerSortTypes.getOrDefault(playerId, HistoryGUI.SortType.CREATION_DATE_DESC);
    }

    public void setCurrentSortType(UUID playerId, SortType sortType) {
        this.playerSortTypes.put(playerId, sortType);
    }

    public void cycleSortType(Player player) {
        UUID playerId = player.getUniqueId();
        SortType currentSort = this.getCurrentSortType(playerId);
        SortType nextSort = currentSort.next();
        this.setCurrentSortType(playerId, nextSort);
        this.setCurrentPage(playerId, 1);
        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
        this.openHistoryMenu(player, 1);
    }

    private void ensureTrackingManager() {
        if (this.trackingManager == null) {
            this.trackingManager = this.plugin.getItemTrackingManager();
            if (this.trackingManager == null) {
                throw new IllegalStateException("ItemTrackingManager is not initialized in the main plugin!");
            }
        }

    }

    private List<ItemTrackingManager.ItemInfo> getItemsToDisplay(Player player) {
        this.ensureTrackingManager();
        List<ItemTrackingManager.ItemInfo> items;
        if (this.playersInSearchMode.contains(player.getUniqueId()) && this.playerSearchResults.containsKey(player.getUniqueId())) {
            items = new ArrayList((Collection)this.playerSearchResults.get(player.getUniqueId()));
        } else {
            items = new ArrayList(this.trackingManager.getAllTrackedItems());
        }

        SortType sortType = this.getCurrentSortType(player.getUniqueId());
        this.sortItems(items, sortType, player);
        return items;
    }

    private void sortItems(List<ItemTrackingManager.ItemInfo> items, SortType sortType, Player player) {
        switch (sortType) {
            case CREATION_DATE_DESC:
                items.sort((a, b) -> Long.compare(b.getCreationTime(), a.getCreationTime()));
                break;
            case CREATION_DATE_ASC:
                items.sort((a, b) -> Long.compare(a.getCreationTime(), b.getCreationTime()));
                break;
            case LAST_UPDATE_DESC:
                items.sort((a, b) -> Long.compare(b.getLastUpdateTime(), a.getLastUpdateTime()));
                break;
            case LAST_UPDATE_ASC:
                items.sort((a, b) -> Long.compare(a.getLastUpdateTime(), b.getLastUpdateTime()));
                break;
            case ALPHABETICAL_ASC:
                items.sort((a, b) -> a.getItemName().compareToIgnoreCase(b.getItemName()));
                break;
            case ALPHABETICAL_DESC:
                items.sort((a, b) -> b.getItemName().compareToIgnoreCase(a.getItemName()));
                break;
            case PROXIMITY_ASC:
                Location playerLoc = player.getLocation();
                items.sort((a, b) -> Double.compare(a.distanceTo(playerLoc), b.distanceTo(playerLoc)));
                break;
            case PROXIMITY_DESC:
                Location playerLocDesc = player.getLocation();
                items.sort((a, b) -> Double.compare(b.distanceTo(playerLocDesc), a.distanceTo(playerLocDesc)));
                break;
        }
    }

    public void openHistoryMenu(Player player, int page) {
        this.ensureTrackingManager();
        UUID playerId = player.getUniqueId();
        List<ItemTrackingManager.ItemInfo> itemsToShow = this.getItemsToDisplay(player);
        int totalPages = Math.max(1, (int)Math.ceil((double)itemsToShow.size() / (double)27.0F));
        if (page < 1) {
            page = 1;
        }

        if (page > totalPages) {
            page = totalPages;
        }

        this.setCurrentPage(playerId, page);
        String searchIndicator = this.playersInSearchMode.contains(playerId) ? " [Búsqueda]" : "";

        String titleStr = this.plugin.getConfig().getString("menu.history-title", "Historial - [%page%/%total%]")
                .replace("%page%", String.valueOf(page))
                .replace("%total%", String.valueOf(totalPages));
        String title = titleStr + searchIndicator;

        Inventory gui = Bukkit.createInventory((InventoryHolder)null, 45, this.formatMessage(title));

        this.addGlassPaneFiller(gui, 0, 8);
        this.addGlassPaneFiller(gui, 36, 44);

        this.addSortButton(gui, player);

        int startIndex = (page - 1) * 27;
        int endIndex = Math.min(startIndex + 27, itemsToShow.size());

        for(int i = startIndex; i < endIndex && i < itemsToShow.size(); ++i) {
            ItemTrackingManager.ItemInfo info = (ItemTrackingManager.ItemInfo)itemsToShow.get(i);
            ItemStack displayItem = this.createDisplayItem(info);
            gui.setItem(i - startIndex + 9, displayItem);
        }

        this.addNavigationButtons(gui, page, totalPages, playerId);
        player.openInventory(gui);
    }

    private void addSortButton(Inventory inventory, Player player) {
        UUID playerId = player.getUniqueId();

        ItemStack sortButton = new ItemStack(Material.HOPPER);
        ItemMeta sortMeta = sortButton.getItemMeta();
        SortType currentSort = this.getCurrentSortType(playerId);

        String sortTitleStr = "§fMenú &8▸ §x§b§a§d§3§d§5Orden";
        String legacyFormattedTitle = this.formatMessage(sortTitleStr);
        Component sortTitleComponent = LegacyComponentSerializer.legacySection().deserialize(legacyFormattedTitle);

        sortMeta.displayName(sortTitleComponent.style(style -> style.decoration(TextDecoration.ITALIC, false)));

        List<String> sortLore = new ArrayList();
        sortLore.add("§8Descripción");
        sortLore.add("");

        for(SortType type : HistoryGUI.SortType.values()) {
            String prefix = type == currentSort ? "&a▶ " : "&7▶ ";

            if (type == SortType.PROXIMITY_ASC || type == SortType.PROXIMITY_DESC) {
                if (player != null && player.isOnline() && player.getWorld() != null) {
                    sortLore.add(ChatColor.translateAlternateColorCodes('&', prefix + type.getDisplayName()));
                }
            } else {
                sortLore.add(ChatColor.translateAlternateColorCodes('&', prefix + type.getDisplayName()));
            }
        }

        sortLore.add("");
        sortLore.add(ChatColor.translateAlternateColorCodes('&', "&e➡ &e&n¡Click&r&e para cambiar orden!"));
        sortMeta.setLore(sortLore);
        this.addAllItemFlags(sortMeta);
        sortButton.setItemMeta(sortMeta);
        inventory.setItem(3, sortButton);
    }

    public void openNextPage(Player player) {
        UUID playerId = player.getUniqueId();
        int currentPage = this.getCurrentPage(playerId);
        this.openHistoryMenu(player, currentPage + 1);
        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
    }

    public void openPreviousPage(Player player) {
        UUID playerId = player.getUniqueId();
        int currentPage = this.getCurrentPage(playerId);
        this.openHistoryMenu(player, currentPage - 1);
        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK.key(), Source.MASTER, 1.0F, 1.0F));
    }

    public void processSearch(Player player, String searchTerm) {
        UUID playerId = player.getUniqueId();
        if (this.playersInSearchMode.contains(playerId)) {
            if (searchTerm.equalsIgnoreCase("cancelar")) {
                this.cancelSearchMode(player);
            } else {
                this.ensureTrackingManager();
                List<ItemTrackingManager.ItemInfo> allItems = this.trackingManager.getAllTrackedItems();
                String lowerSearchTerm = searchTerm.toLowerCase();

                this.playerLastSearch.put(playerId, lowerSearchTerm);

                List<ItemTrackingManager.ItemInfo> results = (List)allItems.stream().filter((item) -> item.getItemName().toLowerCase().contains(lowerSearchTerm) || item.getOriginalOwnerName().toLowerCase().contains(lowerSearchTerm) || item.getCurrentOwnerName().toLowerCase().contains(lowerSearchTerm) || item.getItemId().toLowerCase().contains(lowerSearchTerm)).collect(Collectors.toList());
                this.playerSearchResults.put(playerId, results);
                this.setCurrentPage(playerId, 1);
                if (results.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize(plugin.getPrefix() + "<red>No se encontraron items que coincidan con: <white>" + searchTerm));
                    player.sendMessage(miniMessage.deserialize(plugin.getPrefix() + "<gray>Intenta con otro término o escribe 'cancelar'"));
                } else {
                    player.playSound(player, org.bukkit.Sound.ENTITY_VILLAGER_WORK_LIBRARIAN, 1.0F, 1.0F);
                    this.openHistoryMenu(player, 1);
                }

            }
        }
    }

    public void cancelSearchMode(Player player) {
        UUID playerId = player.getUniqueId();
        this.playersInSearchMode.remove(playerId);
        this.playerSearchResults.remove(playerId);
        this.playerLastSearch.remove(playerId);
        this.setCurrentPage(playerId, 1);
        player.sendMessage(miniMessage.deserialize(plugin.getPrefix() + "<red>Modo de búsqueda cancelado"));
        this.openHistoryMenu(player, 1);
    }

    public void clearSearch(Player player) {
        UUID playerId = player.getUniqueId();
        this.playersInSearchMode.remove(playerId);
        this.playerSearchResults.remove(playerId);
        this.playerLastSearch.remove(playerId);
        this.setCurrentPage(playerId, 1);
        this.openHistoryMenu(player, 1);
    }

    public void clearSearchGUI(Player player) {
        UUID playerId = player.getUniqueId();
        this.playersInSearchMode.remove(playerId);
        this.playerSearchResults.remove(playerId);
        this.playerLastSearch.remove(playerId);
        this.setCurrentPage(playerId, 1);
    }

    public void setSwitchingMenu(Player player, boolean switching) {
        if (switching) {
            isSwitchingMenu.add(player.getUniqueId());
        } else {
            isSwitchingMenu.remove(player.getUniqueId());
        }
    }

    public boolean isSwitchingMenu(Player player) {
        return isSwitchingMenu.contains(player.getUniqueId());
    }

    public String getLastSearchTerm(UUID playerId) {
        return this.playerLastSearch.get(playerId);
    }

    public boolean isInSearchMode(Player player) {
        return this.playersInSearchMode.contains(player.getUniqueId());
    }

    private void addGlassPaneFiller(Inventory inventory, int startSlot, int endSlot) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        this.addAllItemFlags(fillerMeta);
        filler.setItemMeta(fillerMeta);

        for(int i = startSlot; i <= endSlot; ++i) {
            if (i != 3 && i != 5) {
                inventory.setItem(i, filler);
            }
        }

    }

    public void startSearchMode(Player player) {
        player.closeInventory();
        this.playersInSearchMode.add(player.getUniqueId());
        player.sendActionBar(miniMessage.deserialize(this.formatMessage("§x§f§f§3§9§3§9§lITEMS §8➡ &f¡Escribe en el chat que quieres buscar!")));
    }

    private void addAllItemFlags(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.values());
    }

    private ItemStack createDisplayItem(ItemTrackingManager.ItemInfo info) {
        Material material = info.getItemMaterial() != null ? info.getItemMaterial() : Material.PAPER;
        ItemStack displayItem = new ItemStack(material);
        ItemMeta meta = displayItem.getItemMeta();

        String displayNameStr = this.plugin.getConfig().getString("menu.item-display-name", "&r&6%item_name%")
                .replace("%item_name%", info.getItemName())
                .replace("%item_id%", info.getItemId());

        Component displayNameComponent = LegacyComponentSerializer.legacySection().deserialize(this.formatMessage(displayNameStr));
        meta.displayName(displayNameComponent.style(style -> style.decoration(TextDecoration.ITALIC, false)));

        meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(UUID.randomUUID(), "nigga", (double)1.0F, Operation.ADD_NUMBER, EquipmentSlot.BODY));

        List<String> lore = new ArrayList();
        boolean isOriginalOwnerOnline = Bukkit.getPlayer(UUID.fromString(info.getOriginalOwnerUUID())) != null;
        String originalOwnerStatus = isOriginalOwnerOnline ? "&a[Online]" : "&c[Offline]";
        boolean isCurrentOwnerOnline = Bukkit.getPlayer(UUID.fromString(info.getCurrentOwnerUUID())) != null;
        String currentOwnerStatus = isCurrentOwnerOnline ? "&a[Online]" : "&c[Offline]";
        lore.add("§8Descripción");
        lore.add("");
        String var10001 = info.getOriginalOwnerName();
        lore.add("&fDueño: &7" + var10001 + " " + originalOwnerStatus);
        var10001 = info.getCurrentOwnerName();
        lore.add("&fPoseedor: &7" + var10001 + " " + currentOwnerStatus);
        lore.add("&fID: &7" + info.getItemId());

        lore.add("&fFecha: &7" + sdf.format(new Date(info.getCreationTime())));
        if (info.getLastUpdateTime() != 0 && info.getLastUpdateTime() != info.getCreationTime()) {
            lore.add("&fÚltima actualización: &7" + sdf.format(new Date(info.getLastUpdateTime())));
        }

        lore.add("");
        lore.add("&fÚltima Ubicación:");
        lore.add("&8• &fMundo: &7" + (info.getWorld() != null ? info.getWorld() : "Desconocido"));
        lore.add("&8• &fX: &7" + String.format("%.1f", info.getX()));
        lore.add("&8• &fY: &7" + String.format("%.1f", info.getY()));
        lore.add("&8• &fZ: &7" + String.format("%.1f", info.getZ()));

        lore.add("");
        // --- INICIO DE LA MODIFICACIÓN ---
        lore.add("&e➡ &e&n¡Click para última ubicación!");
        // --- FIN DE LA MODIFICACIÓN ---

        List<String> coloredLore = new ArrayList();
        for(String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);

        this.addAllItemFlags(meta);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    public void openDeleteConfirmation(Player player, ItemTrackingManager.ItemInfo info) {
        this.itemPendingDeletion.put(player.getUniqueId(), info);

        String titleStr = plugin.getConfig().getString("delete-confirmation.title", "        ᴇʟɪᴍɪɴᴀʀ ʀᴇɢɪsᴛʀᴏ");
        Inventory gui = Bukkit.createInventory(new DeleteConfirmHolder(), InventoryType.HOPPER, miniMessage.deserialize(titleStr));

        Material confirmMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.confirm-material", "LIME_STAINED_GLASS_PANE"));
        ItemStack confirmButton = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        String confirmText = plugin.getConfig().getString("delete-confirmation.confirm-text", "<!i><#33FF33><b>CONFIRMAR BORRADO");
        confirmMeta.displayName(miniMessage.deserialize(confirmText));
        List<Component> confirmLoreComponents = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("delete-confirmation.confirm-lore")) {
            confirmLoreComponents.add(miniMessage.deserialize(line));
        }
        confirmMeta.lore(confirmLoreComponents);
        confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "delete");
        addAllItemFlags(confirmMeta);
        confirmButton.setItemMeta(confirmMeta);

        Material cancelMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.cancel-material", "RED_STAINED_GLASS_PANE"));
        ItemStack cancelButton = new ItemStack(cancelMat);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        String cancelText = plugin.getConfig().getString("delete-confirmation.cancel-text", "<!i><#FF1313><b>CANCELAR");
        cancelMeta.displayName(miniMessage.deserialize(cancelText));
        List<Component> cancelLoreComponents = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("delete-confirmation.cancel-lore")) {
            cancelLoreComponents.add(miniMessage.deserialize(line));
        }
        cancelMeta.lore(cancelLoreComponents);
        cancelMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "cancel");
        addAllItemFlags(cancelMeta);
        cancelButton.setItemMeta(cancelMeta);

        ItemStack displayItem = createDisplayItem(info);
        gui.setItem(0, confirmButton);
        gui.setItem(2, displayItem);
        gui.setItem(4, cancelButton);

        player.openInventory(gui);
    }

    public void openBulkDeleteConfirmation(Player player, List<ItemTrackingManager.ItemInfo> items) {
        this.itemsPendingBulkDeletion.put(player.getUniqueId(), items);

        String titleStr = "      ᴇʟɪᴍɪɴᴀʀ ʀᴇɢɪsᴛʀᴏs (x" + items.size() + ")";
        Inventory gui = Bukkit.createInventory(new BulkDeleteConfirmHolder(), InventoryType.HOPPER, miniMessage.deserialize(titleStr));

        Material confirmMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.confirm-material", "LIME_STAINED_GLASS_PANE"));
        ItemStack confirmButton = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        confirmMeta.displayName(miniMessage.deserialize("<!i><#33FF33><b>CONFIRMAR BORRADO MASIVO"));
        List<Component> confirmLore = new ArrayList<>();
        confirmLore.add(miniMessage.deserialize("<!i><red>⚠ ¡Esta acción es permanente!"));
        confirmLore.add(miniMessage.deserialize("<!i><gray>Se eliminará el registro de <yellow>" + items.size() + "<gray> items."));
        confirmLore.add(miniMessage.deserialize("<!i><gray>Los items físicos serán eliminados."));
        confirmMeta.lore(confirmLore);
        confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "bulk-delete");
        addAllItemFlags(confirmMeta);
        confirmButton.setItemMeta(confirmMeta);

        Material cancelMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.cancel-material", "RED_STAINED_GLASS_PANE"));
        ItemStack cancelButton = new ItemStack(cancelMat);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.displayName(miniMessage.deserialize("<!i><#FF1313><b>CANCELAR"));
        List<Component> cancelLore = new ArrayList<>();
        cancelLore.add(miniMessage.deserialize("<!i><gray>Regresarás al historial de items."));
        cancelMeta.lore(cancelLore);
        cancelMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "cancel");
        addAllItemFlags(cancelMeta);
        cancelButton.setItemMeta(cancelMeta);

        ItemStack infoItem = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(miniMessage.deserialize("<!i>Información > Múltiples Objetos"));
        int amount = Math.max(1, Math.min(64, items.size()));
        infoItem.setAmount(amount);
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(miniMessage.deserialize("<!i><gray>Items encontrados: <white>" + items.size()));
        infoLore.add(miniMessage.deserialize("<!i><gray>Se eliminará el registro de todos"));
        infoLore.add(miniMessage.deserialize("<!i><gray>los items con ID de tu inventario."));
        infoMeta.lore(infoLore);
        addAllItemFlags(infoMeta);
        infoItem.setItemMeta(infoMeta);

        gui.setItem(0, confirmButton);
        gui.setItem(2, infoItem);
        gui.setItem(4, cancelButton);

        plugin.getHistoryGUI().setSwitchingMenu(player, true);
        player.openInventory(gui);
    }


    private void addNavigationButtons(Inventory inventory, int currentPage, int totalPages, UUID playerId) {
        if (currentPage > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.previous-page", "&fPágina &8▸ &cAnterior")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            List<String> prevLore = new ArrayList();
            prevLore.add("§8Descripción");
            prevLore.add("");
            prevLore.add(ChatColor.translateAlternateColorCodes('&', "&e➡ &e&n¡Click&r&e para ir!"));
            prevMeta.setLore(prevLore);
            this.addAllItemFlags(prevMeta);
            prevButton.setItemMeta(prevMeta);
            inventory.setItem(38, prevButton);
        }

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.close", "&fMenú &8▸ §x§f§f§5§8§4§1Cerrar")))
                .style(style -> style.decoration(TextDecoration.ITALIC, false)));
        List<String> closeLore = new ArrayList();
        closeLore.add("§8Descripción");
        closeLore.add("");
        closeLore.add(ChatColor.translateAlternateColorCodes('&', "&c✘ &c&n¡Click&r&c para cerrar!"));
        closeMeta.setLore(closeLore);
        this.addAllItemFlags(closeMeta);
        closeButton.setItemMeta(closeMeta);
        inventory.setItem(40, closeButton);

        if (currentPage < totalPages) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.next-page", "&fPágina &8▸ &aSiguiente")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            List<String> nextLore = new ArrayList();
            nextLore.add("§8Descripción");
            nextLore.add("");
            nextLore.add(ChatColor.translateAlternateColorCodes('&', "&e➡ &e&n¡Click&r&e para ir!"));
            nextMeta.setLore(nextLore);
            this.addAllItemFlags(nextMeta);
            nextButton.setItemMeta(nextMeta);
            inventory.setItem(42, nextButton);
        }

        boolean isInSearch = this.playersInSearchMode.contains(playerId) || this.playerSearchResults.containsKey(playerId);
        if (isInSearch) {
            ItemStack search = new ItemStack(Material.RED_CANDLE);
            ItemMeta searchMeta = search.getItemMeta();
            searchMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.clear-search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            List<String> clearLore = new ArrayList();
            clearLore.add("§8Descripción");
            clearLore.add("");
            clearLore.add(ChatColor.translateAlternateColorCodes('&', "&c✘ &c&n¡Click&r&c para reiniciar!"));
            searchMeta.setLore(clearLore);
            this.addAllItemFlags(searchMeta);
            search.setItemMeta(searchMeta);
            inventory.setItem(5, search);
        } else {
            ItemStack search = new ItemStack(Material.OAK_SIGN);
            ItemMeta searchMeta = search.getItemMeta();
            searchMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            List<String> searchLore = new ArrayList();
            searchLore.add("§8Descripción");
            searchLore.add("");
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&7ℹ Buscar items por:"));
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&8• &fNombre del item"));
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&8• &fDueño original"));
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&8• &fÚltimo poseedor"));
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&8• &fID único"));
            searchLore.add("");
            searchLore.add(ChatColor.translateAlternateColorCodes('&', "&e➡ &e&n¡Click&r&e para buscar!"));
            searchMeta.setLore(searchLore);
            this.addAllItemFlags(searchMeta);
            search.setItemMeta(searchMeta);
            inventory.setItem(5, search);
        }

    }

    private String formatMessage(String message) {
        if (message == null) {
            return "";
        } else {
            String prefix = this.plugin.getConfig().getString("prefix", "&7[&cMagmaItems&7] ");
            message = message.replace("§", "&");
            return ChatColor.translateAlternateColorCodes('&', message.replace("%prefix%", prefix));
        }
    }

    public static enum SortType {
        CREATION_DATE_DESC("Fecha [Reciente]"),
        CREATION_DATE_ASC("Fecha [Antigua]"),
        LAST_UPDATE_DESC("Actualización [Reciente]"),
        LAST_UPDATE_ASC("Actualización [Antigua]"),
        ALPHABETICAL_ASC("Alfabético [A-Z]"),
        ALPHABETICAL_DESC("Alfabético [Z-A]"),
        PROXIMITY_ASC("Proximidad [Cercano]"),
        PROXIMITY_DESC("Proximidad [Lejano]");

        private final String displayName;

        private SortType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public SortType next() {
            SortType[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public class DeleteConfirmHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public class BulkDeleteConfirmHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}