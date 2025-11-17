package dev.lujanabril.magmaItems.Commands;

import dev.lujanabril.magmaItems.GUI.HistoryGUI;
import dev.lujanabril.magmaItems.Listeners.ItemListener;
import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.CustomItemManager;
import dev.lujanabril.magmaItems.Managers.ItemManager;
import dev.lujanabril.magmaItems.Managers.ItemStorageManager;
import dev.lujanabril.magmaItems.Managers.ItemTrackingManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class ItemCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final ItemManager itemManager;
    private final CustomItemManager customItemManager;
    private final ItemListener itemListener;
    private final ItemTrackingManager itemTrackingManager;
    private final ItemStorageManager itemStorageManager;
    private final HistoryGUI historyGUI;
    private String prefix;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey itemIdKey;
    private boolean duplicateCheckTaskRunning = false;

    public ItemCommand(Main plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        this.customItemManager = plugin.getCustomItemManager();
        this.itemListener = new ItemListener(plugin);
        this.itemTrackingManager = plugin.getItemTrackingManager();
        this.itemStorageManager = plugin.getItemStorageManager();
        this.historyGUI = plugin.getHistoryGUI();
        this.itemIdKey = new NamespacedKey(plugin, "magma_item_id");
        this.loadPrefix();
    }

    public void loadPrefix() {
        this.prefix = this.plugin.getConfig().getString("prefix", "<gray>[<red>MagmaItems</red>] ");
    }

    private String parsePlaceholders(String message) {
        if (message == null) {
            return null;
        } else {
            Map<String, Object> placeholderSection = (Map<String, Object>) (this.plugin.getConfig().getConfigurationSection("placeholders") != null ? this.plugin.getConfig().getConfigurationSection("placeholders").getValues(false) : new HashMap());
            String parsed = message;

            for (Map.Entry<String, Object> entry : placeholderSection.entrySet()) {
                if (entry.getValue() instanceof String) {
                    parsed = parsed.replace((CharSequence) entry.getKey(), (String) entry.getValue());
                }
            }

            return parsed;
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String noPermMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command."));
        if (!sender.hasPermission("magmaitems.admin")) {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noPermMsg));
            return true;
        } else if (args.length == 0) {
            String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.usage", "<gray>Usage: /magmaitem [give|reload|applyid|storage|history|checkduplicates]"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
            return true;
        } else {
            switch (args[0].toLowerCase()) {
                case "give":
                    return this.handleGiveCommand(sender, args);
                case "reload":
                    return this.handleReloadCommand(sender);
                case "applyid":
                    return this.handleApplyIdCommand(sender);
                case "storage":
                    return this.handleStorageCommand(sender, args);
                case "history":
                    return this.handleHistoryCommand(sender, args);
                case "checkduplicates":
                    return this.handleCheckDuplicatesCommand(sender);
                case "unblacklist":
                    return this.handleUnblacklistCommand(sender, args);
                default:
                    String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.usage", "<gray>Usage: /magmaitem [give|reload|applyid|storage|history|checkduplicates]"));
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
                    return true;
            }
        }
    }

    private boolean handleUnblacklistCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + "<gray>Uso: /magmaitem unblacklist <ID>"));
            return true;
        }

        String itemId = args[1];

        if (this.itemTrackingManager.removeItemFromRemovalList(itemId)) {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + "<green>ID <yellow>" + itemId + "<green> eliminada de la lista de borrado. Los items físicos ya no se eliminarán."));
        } else {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + "<red>Esa ID no se encontró en la lista de borrado."));
        }
        return true;
    }

    private boolean handleHistoryCommand(CommandSender sender, String[] args) {
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("open"))) {
            if (sender instanceof Player player) {
                int currentPage = this.historyGUI.getCurrentPage(player.getUniqueId());
                this.historyGUI.openHistoryMenu(player, currentPage);
                return true;
            } else {
                String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
                return true;
            }
        }

        if (args.length >= 2) {
            String subCommand = args[1].toLowerCase();

            switch (subCommand) {
                case "remove":
                    // --- LÓGICA MODIFICADA ---
                    if (args.length < 3) {
                        String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-remove-usage", "<gray>Usage: /magmaitem history [open|remove <ID|hand|inventory>]"));
                        sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
                        return true;
                    }

                    String target = args[2].toLowerCase();
                    if (target.equals("hand")) {
                        return this.handleHistoryRemoveHand(sender);
                    } else if (target.equals("inventory")) {
                        // Llamamos al nuevo método para 'inventory'
                        return this.handleHistoryRemoveInventory(sender);
                    } else {
                        // Llamamos al método antiguo para '<ID>'
                        return this.handleHistoryRemoveId(sender, args);
                    }
                    // --- FIN DE LA MODIFICACIÓN ---
                default:
                    String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-usage", "<gray>Usage: /magmaitem history [open|remove]"));
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
                    return true;
            }
        }

        String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-usage", "<gray>Usage: /magmaitem history [open|remove]"));
        sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
        return true;
    }

    private boolean handleHistoryRemoveId(CommandSender sender, String[] args) {
        // Check 1: Debe ser un jugador para abrir la GUI
        if (!(sender instanceof Player)) {
            String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
            return true;
        }
        Player player = (Player) sender;

        // Check 2: El jugador debe estar en la whitelist
        List<String> whitelist = plugin.getConfig().getStringList("Id-remover-whitelist");
        if (!whitelist.contains(player.getName())) {
            String noPermMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-permission", "<red>No tienes permiso para usar este comando."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noPermMsg));
            return true;
        }

        // Check 3: Uso correcto del comando
        if (args.length < 3) {
            String usage = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-remove-usage", "<gray>Usage: /magmaitem history remove <ID>"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usage));
            return true;
        }

        String uniqueId = args[2];

        // Check 4: La ID debe existir
        ItemTrackingManager.ItemInfo info = this.itemTrackingManager.getItemInfo(uniqueId);
        if (info == null) {
            String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-id-not-found", "<red>Item tracking ID '<yellow>%id%<red>' not found."))
                    .replace("%id%", uniqueId);
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
            return true;
        }

        // Acción: Abrir el menú de confirmación
        this.historyGUI.openDeleteConfirmation(player, info);
        return true;
    }

    // --- MÉTODO NUEVO AÑADIDO ---
    private boolean handleHistoryRemoveHand(CommandSender sender) {
        // Check 1: Debe ser un jugador
        if (!(sender instanceof Player player)) {
            String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
            return true;
        }

        // Check 2: El jugador debe estar en la whitelist
        List<String> whitelist = plugin.getConfig().getStringList("Id-remover-whitelist");
        if (!whitelist.contains(player.getName())) {
            String noPermMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-permission", "<red>No tienes permiso para usar este comando."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noPermMsg));
            return true;
        }

        // Check 3: Debe tener un item en la mano
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType().isAir()) {
            String noItemMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-item-in-hand", "<red>Debes sostener un ítem en tu mano principal."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noItemMsg));
            return true;
        }

        // Check 4: El item debe tener un ID único
        String uniqueId = this.getUniqueId(heldItem); // Usamos el helper que creamos
        if (uniqueId == null) {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + "<red>El ítem que sostienes no tiene una ID única registrada."));
            return true;
        }

        // Check 5: La ID debe existir en el historial
        ItemTrackingManager.ItemInfo info = this.itemTrackingManager.getItemInfo(uniqueId);
        if (info == null) {
            String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.history-id-not-found", "<red>ID de seguimiento '<yellow>%id%<red>' no encontrada."))
                    .replace("%id%", uniqueId);
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
            return true;
        }

        // Acción: Abrir el menú de confirmación
        this.historyGUI.openDeleteConfirmation(player, info);
        return true;
    }

    private boolean handleHistoryRemoveInventory(CommandSender sender) {
        // Check 1: Debe ser un jugador
        if (!(sender instanceof Player player)) {
            String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
            return true;
        }

        // Check 2: El jugador debe estar en la whitelist
        List<String> whitelist = plugin.getConfig().getStringList("Id-remover-whitelist");
        if (!whitelist.contains(player.getName())) {
            String noPermMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-permission", "<red>No tienes permiso para usar este comando."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noPermMsg));
            return true;
        }

        // Check 3: Escanear inventario
        List<ItemTrackingManager.ItemInfo> itemsToScan = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String uniqueId = this.getUniqueId(item); // Usamos el helper que ya teníamos
            if (uniqueId != null) {
                ItemTrackingManager.ItemInfo info = this.itemTrackingManager.getItemInfo(uniqueId);
                if (info != null) {
                    itemsToScan.add(info);
                }
            }
        }

        // Check 4: Verificar si se encontraron items
        if (itemsToScan.isEmpty()) {
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + "<red>No se encontraron items con ID única en tu inventario."));
            return true;
        }

        // Acción: Abrir el menú de confirmación masiva
        this.historyGUI.openBulkDeleteConfirmation(player, itemsToScan);
        return true;
    }

    private boolean handleCheckDuplicatesCommand(CommandSender sender) {
        if (this.duplicateCheckTaskRunning) {
            String alreadyRunning = this.parsePlaceholders(this.plugin.getConfig().getString("messages.duplicate-check-running", "<yellow>A duplicate check is already running."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + alreadyRunning));
            return true;
        } else {
            String startingCheck = this.parsePlaceholders(this.plugin.getConfig().getString("messages.starting-duplicate-check", "<green>Starting check for duplicate MagmaItems..."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + startingCheck));
            this.duplicateCheckTaskRunning = true;
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    this.itemTrackingManager.checkAllMagmaItems();
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        String checkComplete = this.parsePlaceholders(this.plugin.getConfig().getString("messages.duplicate-check-complete", "<green>Duplicate check completed. Check console for details."));
                        sender.sendMessage(this.miniMessage.deserialize(this.prefix + checkComplete));
                        this.duplicateCheckTaskRunning = false;
                    });
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Error during duplicate check: " + e.getMessage());
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        String checkError = this.parsePlaceholders(this.plugin.getConfig().getString("messages.duplicate-check-error", "<red>An error occurred during the duplicate check."));
                        sender.sendMessage(this.miniMessage.deserialize(this.prefix + checkError));
                        this.duplicateCheckTaskRunning = false;
                    });
                }

            });
            return true;
        }
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        boolean applyId = false;
        List<String> filteredArgs = new ArrayList();

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--applyid")) {
                applyId = true;
            } else {
                filteredArgs.add(arg);
            }
        }

        String[] processedArgs = (String[]) filteredArgs.toArray(new String[0]);
        if (processedArgs.length >= 3 && processedArgs.length <= 4) {
            Player target = Bukkit.getPlayer(processedArgs[1]);
            if (target == null) {
                String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-not-found", "<red>Player '%player%' not found or is offline.")).replace("%player%", processedArgs[1]);
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
                return true;
            } else {
                String itemPattern = processedArgs[2];
                List<String> matchingItems = this.getMatchingItems(itemPattern);
                if (matchingItems.isEmpty()) {
                    String itemNotFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.item-not-found", "<red>Item '%item%' does.not exist.")).replace("%item%", itemPattern);
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + itemNotFound));
                    return true;
                } else {
                    int amount = 1;
                    if (processedArgs.length == 4) {
                        try {
                            amount = Integer.parseInt(processedArgs[3]);
                            if (amount <= 0 || amount > 64) {
                                String invalidAmount = this.parsePlaceholders(this.plugin.getConfig().getString("messages.invalid-amount", "<red>Amount must be between 1 and 64."));
                                sender.sendMessage(this.miniMessage.deserialize(this.prefix + invalidAmount));
                                return true;
                            }
                        } catch (NumberFormatException var15) {
                            String invalidNumber = this.parsePlaceholders(this.plugin.getConfig().getString("messages.invalid-number", "<red>Invalid amount: Must be a number."));
                            sender.sendMessage(this.miniMessage.deserialize(this.prefix + invalidNumber));
                            return true;
                        }
                    }

                    int totalItemsGiven = 0;
                    List<String> givenItems = new ArrayList();

                    for (String itemId : matchingItems) {
                        ItemStack item = this.createItemWithPlayerPlaceholder(itemId, target);
                        if (item != null) {
                            item.setAmount(amount);
                            if (applyId) {
                                this.applyUniqueId(item, target, itemId);
                            }

                            target.getInventory().addItem(new ItemStack[]{item});
                            ++totalItemsGiven;
                            givenItems.add(itemId);
                        }
                    }

                    if (totalItemsGiven > 0) {
                        String success;
                        if (totalItemsGiven == 1) {
                            success = this.parsePlaceholders(this.plugin.getConfig().getString("messages.give-success", "<green>Successfully gave %amount%x %item% to %player%.")).replace("%amount%", String.valueOf(amount)).replace("%item%", (CharSequence) givenItems.get(0)).replace("%player%", target.getName());
                        } else {
                            success = this.parsePlaceholders(this.plugin.getConfig().getString("messages.give-multiple-success", "<green>Successfully gave %total% items (%amount%x each) to %player%: %items%")).replace("%total%", String.valueOf(totalItemsGiven)).replace("%amount%", String.valueOf(amount)).replace("%player%", target.getName()).replace("%items%", String.join(", ", givenItems));
                        }

                        if (applyId) {
                            success = success + " " + this.parsePlaceholders(this.plugin.getConfig().getString("messages.id-applied", "<green>Items have been assigned unique IDs."));
                        }

                        sender.sendMessage(this.miniMessage.deserialize(this.prefix + success));
                    }

                    return true;
                }
            }
        } else {
            String giveUsage = this.parsePlaceholders(this.plugin.getConfig().getString("messages.give-usage", "<gray>Usage: /magmaitem give <player> <item> [amount] [--applyid]"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + giveUsage));
            return true;
        }
    }

    private List<String> getMatchingItems(String pattern) {
        List<String> allItems = this.itemManager.getAllItemIds();
        List<String> matchingItems = new ArrayList();
        if (pattern.contains("*")) {
            String regex = pattern.replace("*", ".*");

            for (String itemId : allItems) {
                if (itemId.matches(regex)) {
                    matchingItems.add(itemId);
                }
            }
        } else if (allItems.contains(pattern)) {
            matchingItems.add(pattern);
        }

        return matchingItems;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        this.plugin.reloadConfig();
        this.loadPrefix();
        this.itemTrackingManager.reloadTracking();
        this.itemStorageManager.reloadStorage();
        String reloadSuccess = this.parsePlaceholders(this.plugin.getConfig().getString("messages.reload-success", "<green>Configuration reloaded successfully."));
        sender.sendMessage(this.miniMessage.deserialize(this.prefix + reloadSuccess));
        return true;
    }

    private boolean handleApplyIdCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
            return true;
        } else {
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem != null && !heldItem.getType().isAir()) {
                if (this.hasUniqueId(heldItem)) {
                    String alreadyHasId = this.parsePlaceholders(this.plugin.getConfig().getString("messages.already-has-id", "<red>This item already has a unique ID."));
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + alreadyHasId));
                    return true;
                } else {
                    String itemName = heldItem.hasItemMeta() && heldItem.getItemMeta().hasDisplayName() ? heldItem.getItemMeta().getDisplayName().toString() : heldItem.getType().toString();
                    this.applyUniqueId(heldItem, player, itemName);
                    String idApplied = this.parsePlaceholders(this.plugin.getConfig().getString("messages.id-applied", "<green>Unique ID has been applied to the item in your hand."));
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + idApplied));
                    return true;
                }
            } else {
                String noItemMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-item-in-hand", "<red>You must hold an item in your main hand."));
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + noItemMsg));
                return true;
            }
        }
    }

    private boolean handleStorageCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("magmaitems.admin")) {
            String noPermMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + noPermMsg));
            return true;
        }

        if (args.length == 1) {
            String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-usage", "<gray>Usage: /magmaitem storage [save|give|list|remove]"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
            return true;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "save":
                return this.handleStorageSave(sender, args);
            case "list":
                return this.handleStorageList(sender);
            case "give":
                return this.handleStorageGive(sender, args);
            case "remove":
                return this.handleStorageRemove(sender, args);
            default:
                String usageMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-usage", "<gray>Usage: /magmaitem storage [save|give|list|remove]"));
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + usageMsg));
                return true;
        }
    }

    private boolean handleStorageSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            String playerOnly = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-only", "<red>This command can only be used by players."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + playerOnly));
            return true;
        } else if (args.length < 3) {
            String usage = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-save-usage", "<gray>Usage: /magmaitem storage save <name>"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usage));
            return true;
        } else {
            String itemName = args[2];
            ItemStack heldItem = player.getInventory().getItemInMainHand();

            if (heldItem == null || heldItem.getType().isAir()) {
                String noItemMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.no-item-in-hand", "<red>You must hold an item in your main hand."));
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + noItemMsg));
                return true;
            } else if (this.itemStorageManager.hasItem(itemName)) {
                String alreadyExists = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-item-exists", "<red>An item with the name '%name%' already exists."))
                        .replace("%name%", itemName);
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + alreadyExists));
                return true;
            } else {
                if (this.itemStorageManager.saveItem(itemName, heldItem)) {
                    String success = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-save-success", "<green>Successfully saved item as '%name%.'"))
                            .replace("%name%", itemName);
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + success));
                } else {
                    String error = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-save-error", "<red>Failed to save item. Check console."));
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + error));
                }
                return true;
            }
        }
    }

    private boolean handleStorageList(CommandSender sender) {
        List<String> items = this.itemStorageManager.getStoredItemNames();

        String listHeader = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-list-header", "<gold>Stored Items (%count%):"))
                .replace("%count%", String.valueOf(items.size()));
        sender.sendMessage(this.miniMessage.deserialize(this.prefix + listHeader));

        if (items.isEmpty()) {
            String emptyList = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-list-empty", "<yellow>There are no items currently stored."));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + emptyList));
        } else {
            String itemListMsg = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-list-content", "<green>Stored Items: <white>%items%"));
            itemListMsg = itemListMsg.replace("%items%", String.join(", ", items));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + itemListMsg));
        }

        return true;
    }

    private boolean handleStorageGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            String usage = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-give-usage", "<gray>Usage: /magmaitem storage give <player> <item_name> [amount] [--applyid]"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usage));
            return true;
        }

        String targetName = args[2];
        String itemName = args[3];

        int amount = 1;
        boolean applyId = false;

        for (int i = 4; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--applyid")) {
                applyId = true;
            } else {
                try {
                    int parsedAmount = Integer.parseInt(arg);
                    if (parsedAmount > 0 && parsedAmount <= 64) {
                        amount = parsedAmount;
                    } else {
                        String invalidAmount = this.parsePlaceholders(this.plugin.getConfig().getString("messages.invalid-amount", "<red>Amount must be between 1 and 64."));
                        sender.sendMessage(this.miniMessage.deserialize(this.prefix + invalidAmount));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    String invalidNumber = this.parsePlaceholders(this.plugin.getConfig().getString("messages.invalid-number", "<red>Invalid argument '%arg%'. Must be a number or --applyid."))
                            .replace("%arg%", arg);
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + invalidNumber));
                    return true;
                }
            }
        }

        ItemStack storedItem = this.itemStorageManager.getItem(itemName);

        if (storedItem == null) {
            String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-item-not-found", "<red>Item '%name%' not found in storage."))
                    .replace("%name%", itemName);
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.player-not-found", "<red>Player '%player%' not found or is offline."))
                    .replace("%player%", targetName);
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
            return true;
        } else {
            ItemStack itemToGive = storedItem.clone();
            itemToGive.setAmount(amount);

            if (applyId) {
                this.applyUniqueId(itemToGive, target, itemName);
            }

            target.getInventory().addItem(itemToGive);

            String success = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-give-success", "<green>Successfully gave %amount%x %item% from storage to %player%."))
                    .replace("%item%", itemName)
                    .replace("%player%", target.getName())
                    .replace("%amount%", String.valueOf(amount));

            if (applyId) {
                success += " " + this.parsePlaceholders(this.plugin.getConfig().getString("messages.id-applied", "<green>Item has been assigned a unique ID."));
            }

            sender.sendMessage(this.miniMessage.deserialize(this.prefix + success));
            return true;
        }
    }

    private boolean handleStorageRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            String usage = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-remove-usage", "<gray>Usage: /magmaitem storage remove <item_name>"));
            sender.sendMessage(this.miniMessage.deserialize(this.prefix + usage));
            return true;
        } else {
            String itemName = args[2];

            if (!this.itemStorageManager.hasItem(itemName)) {
                String notFound = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-item-not-found", "<red>Item '%name%' not found in storage."))
                        .replace("%name%", itemName);
                sender.sendMessage(this.miniMessage.deserialize(this.prefix + notFound));
                return true;
            } else {
                if (this.itemStorageManager.removeItem(itemName)) {
                    String success = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-remove-success", "<green>Item '%name%' successfully removed from storage."))
                            .replace("%name%", itemName);
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + success));
                } else {
                    String error = this.parsePlaceholders(this.plugin.getConfig().getString("messages.storage-save-error", "<red>An error occurred while removing item '%name%'. Check console."))
                            .replace("%name%", itemName);
                    sender.sendMessage(this.miniMessage.deserialize(this.prefix + error));
                }
                return true;
            }
        }
    }

    private void applyUniqueId(ItemStack item, Player player, String itemName) {
        if (item != null && item.hasItemMeta()) {
            String uniqueId = this.generateUniqueId();
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(this.itemIdKey, PersistentDataType.STRING, uniqueId);
            List<String> lore = (List<String>) (meta.hasLore() ? meta.getLore() : new ArrayList());
            if (lore == null) {
                lore = new ArrayList();
            }

            lore.add("");
            lore.add("§8[ID-" + uniqueId + "]");
            meta.setLore(lore);
            item.setItemMeta(meta);
            String originalOwner = this.extractOriginalOwnerFromLore(meta);
            if (originalOwner.equals("Desconocido")) {
                originalOwner = player.getName();
            }

            this.itemTrackingManager.registerItem(uniqueId, player.getUniqueId().toString(), player.getName(), itemName, originalOwner);
            this.itemTrackingManager.updateItemMaterial(uniqueId, item.getType());
        }

    }

    private String extractOriginalOwnerFromLore(ItemMeta meta) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore.size() >= 4) {
                String loreLine = (String) lore.get(3);
                if (loreLine != null && !loreLine.isEmpty()) {

                    net.kyori.adventure.text.Component component = LegacyComponentSerializer.legacySection().deserialize(loreLine);
                    String plainText = PlainTextComponentSerializer.plainText().serialize(component);
                    return plainText.replaceAll("✎", "").trim();
                }
            }
        }
        return "Desconocido";
    }


    private boolean hasUniqueId(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            return container.has(this.itemIdKey, PersistentDataType.STRING);
        } else {
            return false;
        }
    }

    private String getUniqueId(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(this.itemIdKey, PersistentDataType.STRING)) {
                return container.get(this.itemIdKey, PersistentDataType.STRING);
            }
        }
        return null;
    }

    // --- MÉTODO CORREGIDO ---
    private String generateUniqueId() {
        StringBuilder idBuilder = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();

        for (int i = 0; i < 5; ++i) {
            idBuilder.append(chars.charAt(random.nextInt(chars.length())));
        }

        String generatedId = idBuilder.toString();

        // Comprobar si existe en el tracking O en la lista negra
        if (this.itemTrackingManager.idExists(generatedId) || this.itemTrackingManager.idIsBlacklisted(generatedId)) {
            return this.generateUniqueId(); // Si existe en cualquiera de las dos, generar uno nuevo
        } else {
            return generatedId; // Está libre
        }
    }
    // --- FIN CORRECCIÓN ---

    private ItemStack createItemWithPlayerPlaceholder(String itemId, Player player) {
        ItemStack itemBase = this.itemManager.createItem(itemId);
        if (itemBase == null) {
            return null;
        } else {
            if (itemBase.hasItemMeta() && itemBase.getItemMeta().hasLore()) {
                ItemMeta meta = itemBase.getItemMeta();
                List<String> lore = meta.getLore();
                if (lore != null) {
                    List<String> newLore = new ArrayList();

                    for (String line : lore) {
                        String processedLine = line.replace("%player%", player.getName());
                        processedLine = processedLine.replace("%player_display%", player.getDisplayName());
                        processedLine = processedLine.replace("%player_uuid%", player.getUniqueId().toString());
                        newLore.add(processedLine);
                    }

                    meta.setLore(newLore);
                    itemBase.setItemMeta(meta);
                }
            }

            return itemBase;
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("magmaitems.admin")) {
            return new ArrayList();
        } else {
            List<String> completions = new ArrayList();

            if (args.length == 1) {
                completions.addAll(Arrays.asList("give", "reload", "applyid", "storage", "history", "checkduplicates", "unblacklist"));

            } else if (args[0].equalsIgnoreCase("give")) {
                if (args.length == 2) {
                    completions.addAll((Collection) Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                } else if (args.length == 3) {
                    completions.addAll(this.itemManager.getAllItemIds());
                    completions.add("*");
                } else if (args.length == 4) {
                    completions.addAll(Arrays.asList("1", "16", "32", "64"));
                } else if (args.length == 5) {
                    completions.add("--applyid");
                }

            } else if (args[0].equalsIgnoreCase("storage")) {
                if (args.length == 2) {
                    completions.addAll(Arrays.asList("save", "give", "list", "remove"));
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("give")) {
                        completions.addAll((Collection) Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                    } else if (args[1].equalsIgnoreCase("remove")) {
                        completions.addAll(this.itemStorageManager.getStoredItemNames());
                    }
                } else if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
                    completions.addAll(this.itemStorageManager.getStoredItemNames());
                } else if (args.length == 5 && args[1].equalsIgnoreCase("give")) {
                    completions.addAll(Arrays.asList("1", "64", "--applyid"));
                } else if (args.length == 6 && args[1].equalsIgnoreCase("give")) {
                    if (!args[4].equalsIgnoreCase("--applyid")) {
                        completions.add("--applyid");
                    }
                }
            } else if (args[0].equalsIgnoreCase("history")) {
                if (args.length == 2) {
                    completions.addAll(Arrays.asList("open", "remove"));
                } else if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                    // Añadimos 'hand' e 'inventory'
                    completions.add("hand");
                    completions.add("inventory"); // <-- AÑADIDO
                    completions.addAll(this.itemTrackingManager.getAllItemIds());
                }
            } else if (args[0].equalsIgnoreCase("unblacklist")) {
                if (args.length == 2) {
                    completions.addAll(this.itemTrackingManager.getIdsToRemoveCache());
                }
            }

            return (List) completions.stream().filter((s) -> {
                return s.toLowerCase().startsWith(args[args.length - 1].toLowerCase());
            }).collect(Collectors.toList());
        }
    }
}