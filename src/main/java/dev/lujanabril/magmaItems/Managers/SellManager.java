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

/**
 * Clase centralizada para manejar la venta de items.
 * Interactúa con Vault y ShopGuiPlus.
 */
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
     * Vende un mapa de materiales a ShopGUIPlus y deposita el dinero al jugador.
     *
     * @param player      El jugador que vende.
     * @param itemsToSell El mapa de Materiales y cantidades a vender.
     */
    public void sellItemsToShop(Player player, Map<Material, Integer> itemsToSell) {
        if (itemsToSell.isEmpty() || this.economy == null || !this.isShopGuiPlusEnabled()) {
            return;
        }

        double totalEarnings = 0.0;
        int totalItemsSold = 0;

        for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
            Material material = entry.getKey();
            int quantity = entry.getValue();

            try {
                // Obtenemos el precio de venta por 1 unidad
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

        // Enviar mensajes de venta si se vendió algo
        if (totalItemsSold > 0) {
            // Reutilizamos los mensajes de la bomba, ya que son genéricos
            String chatMessage = this.plugin.getConfig().getString("messages.bomb-sale-chat", "§a§l[BOMBA] §7Has vendido §e{items} §7items por §a${money}")
                    .replace("{items}", this.itemFormatter.format(totalItemsSold))
                    .replace("{money}", this.formatMoney(totalEarnings));

            player.sendMessage(chatMessage);

            String titleMessage = this.plugin.getConfig().getString("messages.bomb-sale-title", "§a+${money}")
                    .replace("{money}", this.formatMoney(totalEarnings));

            String subtitleMessage = this.plugin.getConfig().getString("messages.bomb-sale-subtitle", "§7(§e{items} items§7)")
                    .replace("{items}", String.valueOf(totalItemsSold)) // Subtítulo simple
                    .replace("{money}", this.formatMoney(totalEarnings));

            player.sendTitle(titleMessage, subtitleMessage, 10, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    /**
     * Formatea una cantidad de dinero a un formato legible (k, M, B).
     *
     * @param amount La cantidad de dinero.
     * @return El String formateado.
     */
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

    /**
     * Comprueba si ShopGUIPlus está habilitado.
     *
     * @return true si está habilitado, false si no.
     */
    public boolean isShopGuiPlusEnabled() {
        return this.plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus") != null;
    }
}