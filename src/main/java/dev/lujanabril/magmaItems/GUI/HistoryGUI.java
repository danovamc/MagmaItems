package dev.lujanabril.magmaItems.GUI;

// --- Importaciones Requeridas ---
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration; // <-- ¡¡IMPORTACIÓN CLAVE PARA LA ITÁLICA!!
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
    private final Map<UUID, ItemTrackingManager.ItemInfo> itemPendingDeletion = new HashMap();

    private final MiniMessage miniMessage;

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

        String titleStr = this.plugin.getConfig().getString("menu.history-title", "Historial - [%page%/%total%]")
                .replace("%page%", String.valueOf(page))
                .replace("%total%", String.valueOf(totalPages));
        String title = titleStr + searchIndicator;

        Inventory gui = Bukkit.createInventory((InventoryHolder)null, 45, this.formatMessage(title));

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

        // --- CORRECCIÓN DE ITÁLICA ---
        String sortTitleStr = "§fMenú &8▸ §x§b§a§d§3§d§5Orden";
        String legacyFormattedTitle = this.formatMessage(sortTitleStr);
        Component sortTitleComponent = LegacyComponentSerializer.legacySection().deserialize(legacyFormattedTitle);

        // Aquí forzamos que no sea itálica
        sortMeta.displayName(sortTitleComponent.style(style -> style.decoration(TextDecoration.ITALIC, false)));
        // --- FIN CORRECCIÓN ---

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
        this.setCurrentPage(playerId, 1);
        player.sendMessage(miniMessage.deserialize(plugin.getPrefix() + "<red>Modo de búsqueda cancelado"));
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
        fillerMeta.displayName(Component.text(" "));
        this.addAllItemFlags(fillerMeta);
        filler.setItemMeta(fillerMeta);

        for(int i = startSlot; i <= endSlot; ++i) {
            if (i != 3 && i != 5) { // Evitar sobreescribir el botón de sort (3) y search (5)
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

        // --- CORRECCIÓN DE ITÁLICA ---
        // 1. Añadimos "<!i>" (MiniMessage para no-itálica) o "&r" (legacy) al inicio
        //    Usaremos "&r" ya que formatMessage usa ChatColor.
        String displayNameStr = this.plugin.getConfig().getString("menu.item-display-name", "&r&6%item_name%")
                .replace("%item_name%", info.getItemName())
                .replace("%item_id%", info.getItemId());

        // 2. Formateamos y deserializamos a Componente
        Component displayNameComponent = LegacyComponentSerializer.legacySection().deserialize(this.formatMessage(displayNameStr));

        // 3. Forzamos que no sea itálica
        meta.displayName(displayNameComponent.style(style -> style.decoration(TextDecoration.ITALIC, false)));
        // --- FIN CORRECCIÓN ---

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

        // El lore está bien, el usuario dijo que no estaba en itálica
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

        // --- Botón Confirmar ---
        Material confirmMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.confirm-material", "LIME_STAINED_GLASS_PANE"));
        ItemStack confirmButton = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirmButton.getItemMeta();

        String confirmText = plugin.getConfig().getString("delete-confirmation.confirm-text", "<!i><#33FF33><b>CONFIRMAR BORRADO");
        confirmMeta.displayName(miniMessage.deserialize(confirmText)); // MiniMessage quita la itálica con <!i>

        List<Component> confirmLoreComponents = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("delete-confirmation.confirm-lore")) {
            confirmLoreComponents.add(miniMessage.deserialize(line)); // MiniMessage quita la itálica con <!i>
        }
        confirmMeta.lore(confirmLoreComponents);

        confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "delete");
        addAllItemFlags(confirmMeta);
        confirmButton.setItemMeta(confirmMeta);

        // --- Botón Cancelar ---
        Material cancelMat = Material.valueOf(plugin.getConfig().getString("delete-confirmation.cancel-material", "RED_STAINED_GLASS_PANE"));
        ItemStack cancelButton = new ItemStack(cancelMat);
        ItemMeta cancelMeta = cancelButton.getItemMeta();

        String cancelText = plugin.getConfig().getString("delete-confirmation.cancel-text", "<!i><#FF1313><b>CANCELAR");
        cancelMeta.displayName(miniMessage.deserialize(cancelText)); // MiniMessage quita la itálica con <!i>

        List<Component> cancelLoreComponents = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("delete-confirmation.cancel-lore")) {
            cancelLoreComponents.add(miniMessage.deserialize(line)); // MiniMessage quita la itálica con <!i>
        }
        cancelMeta.lore(cancelLoreComponents);

        cancelMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm-action"), PersistentDataType.STRING, "cancel");
        addAllItemFlags(cancelMeta);
        cancelButton.setItemMeta(cancelMeta);

        // --- Item a borrar (Display) ---
        ItemStack displayItem = createDisplayItem(info); // Esto ya no tendrá itálica por la corrección de arriba

        // --- Setear items ---
        gui.setItem(0, confirmButton);
        gui.setItem(2, displayItem);
        gui.setItem(4, cancelButton);

        player.openInventory(gui);
    }


    private void addNavigationButtons(Inventory inventory, int currentPage, int totalPages, UUID playerId) {
        if (currentPage > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            // --- CORRECCIÓN ITÁLICA ---
            prevMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.previous-page", "&fPágina &8▸ &cAnterior")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            // --- FIN CORRECCIÓN ---
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
        // --- CORRECCIÓN ITÁLICA ---
        closeMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.close", "&fMenú &8▸ §x§f§f§5§8§4§1Cerrar")))
                .style(style -> style.decoration(TextDecoration.ITALIC, false)));
        // --- FIN CORRECCIÓN ---
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
            // --- CORRECCIÓN ITÁLICA ---
            nextMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.next-page", "&fPágina &8▸ &aSiguiente")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            // --- FIN CORRECCIÓN ---
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
            // --- CORRECCIÓN ITÁLICA ---
            searchMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.clear-search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            // --- FIN CORRECCIÓN ---
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
            // --- CORRECCIÓN ITÁLICA ---
            searchMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(this.formatMessage("&r" + this.plugin.getConfig().getString("menu.search", "&fMenú &8▸ §x§7§6§c§e§f§fBuscar")))
                    .style(style -> style.decoration(TextDecoration.ITALIC, false)));
            // --- FIN CORRECCIÓN ---
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

    /**
     * Este método traduce códigos legacy (&) y hex (#) a códigos '§'.
     * Es necesario para los métodos de Bukkit que esperan un String formateado.
     */
    private String formatMessage(String message) {
        if (message == null) {
            return "";
        } else {
            String prefix = this.plugin.getConfig().getString("prefix", "&7[&cMagmaItems&7] ");
            // Reemplaza '§' por '&' primero, para que ChatColor pueda parsear todo
            message = message.replace("§", "&");
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

    /**
     * Holder para el inventario de confirmación de borrado.
     */
    public class DeleteConfirmHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // Correcto
        }
    }
}