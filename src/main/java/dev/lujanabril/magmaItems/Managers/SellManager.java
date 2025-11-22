package dev.lujanabril.magmaItems.Managers;

import dev.lujanabril.magmaItems.Main;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.logging.Logger;

// Importaciones de Adventure
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer; // <--- IMPORTANTE
import java.time.Duration;

public class SellManager {

    private final Main plugin;
    private Economy economy;
    private final DecimalFormat itemFormatter = new DecimalFormat("#,###");

    public SellManager(Main plugin) {
        this.plugin = plugin;
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
                this.economy = rsp.getProvider();
            }
        }
    }

    /**
     * Método auxiliar seguro para procesar mensajes.
     * Si detecta '§', usa el serializador Legacy. Si no, usa MiniMessage.
     */
    private Component parseMessage(String message) {
        if (message == null) return Component.empty();
        if (message.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(message);
        }
        return this.plugin.getMiniMessage().deserialize(message);
    }

    public void sellItemsToShop(Player player, Map<Material, Integer> itemsToSell, String chatMessageKey, String titleMessageKey, String subtitleMessageKey) {
        if (itemsToSell.isEmpty() || this.economy == null || !this.isShopGuiPlusEnabled()) {
            return;
        }

        double totalEarnings = 0.0;
        int totalItemsSold = 0;

        for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
            Material material = entry.getKey();
            int quantity = entry.getValue();

            try {
                double sellPricePerItem = ShopGuiPlusApi.getItemStackPriceSell(player, new ItemStack(material, 1));
                if (sellPricePerItem > 0.0) {
                    double totalPrice = sellPricePerItem * quantity;
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

            // Mensaje de Chat
            String chatMessageFormat = this.plugin.getConfig().getString(chatMessageKey);
            if (chatMessageFormat != null && !chatMessageFormat.isEmpty()) {
                String rawChat = chatMessageFormat
                        .replace("{items}", this.itemFormatter.format(totalItemsSold))
                        .replace("{money}", this.formatMoney(totalEarnings));

                // Usar método seguro
                player.sendMessage(parseMessage(rawChat));
            }

            // Mensajes de Título
            String titleMessageFormat = this.plugin.getConfig().getString(titleMessageKey);
            String subtitleMessageFormat = this.plugin.getConfig().getString(subtitleMessageKey);

            boolean hasTitle = titleMessageFormat != null && !titleMessageFormat.isEmpty();
            boolean hasSubtitle = subtitleMessageFormat != null && !subtitleMessageFormat.isEmpty();

            if (hasTitle || hasSubtitle) {
                String rawTitle = "";
                if (hasTitle) {
                    rawTitle = titleMessageFormat
                            .replace("{money}", this.formatMoney(totalEarnings));
                }

                String rawSubtitle = "";
                if (hasSubtitle) {
                    rawSubtitle = subtitleMessageFormat
                            .replace("{items}", String.valueOf(totalItemsSold))
                            .replace("{money}", this.formatMoney(totalEarnings));
                }

                // Usar método seguro para ambos componentes
                Component titleComp = parseMessage(rawTitle);
                Component subtitleComp = parseMessage(rawSubtitle);

                Title title = Title.title(
                        titleComp,
                        subtitleComp,
                        Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
                );
                player.showTitle(title);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    public String formatMoney(double amount) {
        if (amount >= 1.0E9) {
            double billions = amount / 1.0E9;
            return (billions == Math.floor(billions) ? String.format("%.0fB", billions) : String.format("%.1fB", billions)).replaceAll("\\.0", "");
        } else if (amount >= 1000000.0) {
            double millions = amount / 1000000.0;
            return (millions == Math.floor(millions) ? String.format("%.0fM", millions) : String.format("%.1fM", millions)).replaceAll("\\.0", "");
        } else if (amount >= 1000.0) {
            double thousands = amount / 1000.0;
            return (thousands == Math.floor(thousands) ? String.format("%.0fk", thousands) : String.format("%.1fk", thousands)).replaceAll("\\.0", "");
        } else {
            return (amount == Math.floor(amount) ? String.format("%.0f", amount) : String.format("%.2f", amount));
        }
    }

    public boolean isShopGuiPlusEnabled() {
        return this.plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus") != null;
    }
}