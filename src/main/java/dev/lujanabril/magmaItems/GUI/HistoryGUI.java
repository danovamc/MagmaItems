package dev.lujanabril.magmaItems.GUI;

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

import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    public HistoryGUI(Main plugin) {
        this.plugin = plugin;
    }

    public int getCurrentPage(UUID playerId) {
        return (Integer)this.playerPages.getOrDefault(playerId, 1);
    }

    public void setCurrentPage(UUID playerId, int page) {
        this.playerPages.put(playerId, page);
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
        List<ItemTrackingManager.ItemInfo> items;
        if (this.playersInSearchMode.contains(player.getUniqueId()) && this.playerSearchResults.containsKey(player.getUniqueId())) {
            items = new ArrayList((Collection)this.playerSearchResults.get(player.getUniqueId()));
        } else {
            items = new ArrayList(this.trackingManager.getAllTrackedItems());
        }

        SortType sortType = this.getCurrentSortType(player.getUniqueId());
        this.sortItems(items, sortType);
        return items;
    }

    private void sortItems(List<ItemTrackingManager.ItemInfo> items, SortType sortType) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        switch (sortType.ordinal()) {
            case 0 -> items.sort((a, b) -> {
                try {
                    Date dateA = sdf.parse(a.getCreationTime());
                    Date dateB = sdf.parse(b.getCreationTime());
                    return dateB.compareTo(dateA);
                } catch (ParseException var8) {
                    try {
                        sdf.parse(a.getCreationTime());
                        return -1;
                    } catch (ParseException var7) {
                        try {
                            sdf.parse(b.getCreationTime());
                            return 1;
                        } catch (ParseException var6) {
                            return b.getCreationTime().compareTo(a.getCreationTime());
                        }
                    }
                }
            });
            case 1 -> items.sort((a, b) -> {
                try {
                    Date dateA = sdf.parse(a.getCreationTime());
                    Date dateB = sdf.parse(b.getCreationTime());
                    return dateA.compareTo(dateB);
                } catch (ParseException var8) {
                    try {
                        sdf.parse(a.getCreationTime());
                        return -1;
                    } catch (ParseException var7) {
                        try {
                            sdf.parse(b.getCreationTime());
                            return 1;
                        } catch (ParseException var6) {
                            return a.getCreationTime().compareTo(b.getCreationTime());
                        }
                    }
                }
            });
            case 2 -> items.sort((a, b) -> {
                try {
                    String lastUpdateA = a.getLastUpdateTime() != null ? a.getLastUpdateTime() : a.getCreationTime();
                    String lastUpdateB = b.getLastUpdateTime() != null ? b.getLastUpdateTime() : b.getCreationTime();
                    Date dateA = sdf.parse(lastUpdateA);
                    Date dateB = sdf.parse(lastUpdateB);
                    return dateB.compareTo(dateA);
                } catch (ParseException var10) {
                    String lastUpdateA = a.getLastUpdateTime() != null ? a.getLastUpdateTime() : a.getCreationTime();
                    String lastUpdateB = b.getLastUpdateTime() != null ? b.getLastUpdateTime() : b.getCreationTime();

                    try {
                        sdf.parse(lastUpdateA);
                        return -1;
                    } catch (ParseException var9) {
                        try {
                            sdf.parse(lastUpdateB);
                            return 1;
                        } catch (ParseException var8) {
                            return lastUpdateB.compareTo(lastUpdateA);
                        }
                    }
                }
            });
            case 3 -> items.sort((a, b) -> {
                try {
                    String lastUpdateA = a.getLastUpdateTime() != null ? a.getLastUpdateTime() : a.getCreationTime();
                    String lastUpdateB = b.getLastUpdateTime() != null ? b.getLastUpdateTime() : b.getCreationTime();
                    Date dateA = sdf.parse(lastUpdateA);
                    Date dateB = sdf.parse(lastUpdateB);
                    return dateA.compareTo(dateB);
                } catch (ParseException var10) {
                    String lastUpdateA = a.getLastUpdateTime() != null ? a.getLastUpdateTime() : a.getCreationTime();
                    String lastUpdateB = b.getLastUpdateTime() != null ? b.getLastUpdateTime() : b.getCreationTime();

                    try {
                        sdf.parse(lastUpdateA);
                        return -1;
                    } catch (ParseException var9) {
                        try {
                            sdf.parse(lastUpdateB);
                            return 1;
                        } catch (ParseException var8) {
                            return lastUpdateA.compareTo(lastUpdateB);
                        }
                    }
                }
            });
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
        String var10000 = this.formatMessage(this.plugin.getConfig().getString("menu.history-title", "Historial - [%page%/%total%]")).replace("%page%", String.valueOf(page)).replace("%total%", String.valueOf(totalPages));
        String title = var10000 + searchIndicator;
        Inventory gui = Bukkit.createInventory((InventoryHolder)null, 45, title);
        this.addGlassPaneFiller(gui, 0, 8);
        this.addGlassPaneFiller(gui, 36, 44);
        this.addSortButton(gui, playerId);
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

    private void addSortButton(Inventory inventory, UUID playerId) {
        ItemStack sortButton = new ItemStack(Material.HOPPER);
        ItemMeta sortMeta = sortButton.getItemMeta();
        SortType currentSort = this.getCurrentSortType(playerId);
        SortType nextSort = currentSort.next();
        sortMeta.setDisplayName(this.formatMessage("&fMenú &8▸ §x§b§a§d§3§d§5Orden"));
        List<String> sortLore = new ArrayList();
        sortLore.add("§8Descripción");
        sortLore.add("");

        for(SortType type : HistoryGUI.SortType.values()) {
            String prefix = type == currentSort ? "&a▶ " : "&7▶ ";
            sortLore.add(ChatColor.translateAlternateColorCodes('&', prefix + type.getDisplayName()));
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
                List<ItemTrackingManager.ItemInfo> results = (List)allItems.stream().filter((item) -> item.getItemName().toLowerCase().contains(lowerSearchTerm) || item.getOriginalOwnerName().toLowerCase().contains(lowerSearchTerm) || item.getCurrentOwnerName().toLowerCase().contains(lowerSearchTerm) || item.getItemId().toLowerCase().contains(lowerSearchTerm)).collect(Collectors.toList());
                this.playerSearchResults.put(playerId, results);
                this.setCurrentPage(playerId, 1);
                if (results.isEmpty()) {
                    player.sendMessage(this.formatMessage("&cNo se encontraron items que coincidan con: &f" + searchTerm));
                    player.sendMessage(this.formatMessage("&7Intenta con otro término o escribe 'cancelar'"));
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
        this.setCurrentPage(playerId, 1);
        player.sendMessage(this.formatMessage("&cModo de búsqueda cancelado"));
        this.openHistoryMenu(player, 1);
    }

    public void clearSearch(Player player) {
        UUID playerId = player.getUniqueId();
        this.playersInSearchMode.remove(playerId);
        this.playerSearchResults.remove(playerId);
        this.setCurrentPage(playerId, 1);
        this.openHistoryMenu(player, 1);
    }

    public void clearSearchGUI(Player player) {
        UUID playerId = player.getUniqueId();
        this.playersInSearchMode.remove(playerId);
        this.playerSearchResults.remove(playerId);
        this.setCurrentPage(playerId, 1);
    }

    public boolean isInSearchMode(Player player) {
        return this.playersInSearchMode.contains(player.getUniqueId());
    }

    private void addGlassPaneFiller(Inventory inventory, int startSlot, int endSlot) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        this.addAllItemFlags(fillerMeta);
        filler.setItemMeta(fillerMeta);

        for(int i = startSlot; i <= endSlot; ++i) {
            if (i != 3) {
                inventory.setItem(i, filler);
            }
        }

    }

    public void startSearchMode(Player player) {
        player.closeInventory();
        this.playersInSearchMode.add(player.getUniqueId());
        player.sendActionBar(this.formatMessage("§x§f§f§3§9§3§9§lITEMS §8➡ &f¡Escribe en el chat que quieres buscar!"));
    }

    private void addAllItemFlags(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.values());
    }

    private ItemStack createDisplayItem(ItemTrackingManager.ItemInfo info) {
        Material material = info.getItemMaterial() != null ? info.getItemMaterial() : Material.PAPER;
        ItemStack displayItem = new ItemStack(material);
        ItemMeta meta = displayItem.getItemMeta();
        String displayName = this.formatMessage(this.plugin.getConfig().getString("menu.item-display-name", "&6%item_name%")).replace("%item_name%", info.getItemName()).replace("%item_id%", info.getItemId());
        meta.setDisplayName(displayName);
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
        lore.add("&fFecha: &7" + info.getCreationTime());
        if (info.getLastUpdateTime() != null && !info.getLastUpdateTime().equals(info.getCreationTime())) {
            lore.add("&fÚltima actualización: &7" + info.getLastUpdateTime());
        }

        lore.add("");
        lore.add("&e➡ &e&n¡Click&r&e para examinar!");
        List<String> coloredLore = new ArrayList();

        for(String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(coloredLore);
        this.addAllItemFlags(meta);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private void addNavigationButtons(Inventory inventory, int currentPage, int totalPages, UUID playerId) {
        if (currentPage > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(this.formatMessage(this.plugin.getConfig().getString("menu.previous-page", "&fPágina &8▸ &cAnterior")));
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
        closeMeta.setDisplayName(this.formatMessage(this.plugin.getConfig().getString("menu.close", "&fMenú &8▸ §x§f§f§5§8§4§1Cerrar")));
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
            nextMeta.setDisplayName(this.formatMessage(this.plugin.getConfig().getString("menu.next-page", "&fPágina &8▸ &aSiguiente")));
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
            searchMeta.setDisplayName(this.formatMessage(this.plugin.getConfig().getString("menu.clear-search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")));
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
            searchMeta.setDisplayName(this.formatMessage(this.plugin.getConfig().getString("menu.search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")));
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
            return ChatColor.translateAlternateColorCodes('&', message.replace("%prefix%", prefix));
        }
    }

    public static enum SortType {
        CREATION_DATE_DESC("Fecha [Reciente]"),
        CREATION_DATE_ASC("Fecha [Antigua]"),
        LAST_UPDATE_DESC("Actualización [Reciente]"),
        LAST_UPDATE_ASC("Actualización [Antigua]");

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
}
