package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material; // Importar Material
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class TotemListener implements Listener {

    private final Main plugin;
    private final NamespacedKey magmaItemKey;
    private final NamespacedKey usesKey;

    private static final String USES_LORE_PREFIX_FORMAT = "<!i><#777777>Usos restantes: <#FFD700>";
    private static final String USES_LORE_STRIPPED_PREFIX = "Usos restantes:";
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    public TotemListener(Main plugin) {
        this.plugin = plugin;
        this.magmaItemKey = new NamespacedKey(plugin, "magma_item");
        this.usesKey = new NamespacedKey(plugin, "totem_uses");
    }

    /**
     * Verifica si el tótem está permitido en el mundo actual.
     */
    private boolean isTotemAllowedInWorld(String worldName) {
        List<String> allowedWorlds = plugin.getConfig().getStringList("totem.allowed-worlds");
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return allowedWorlds.contains(worldName);
    }

    /**
     * Lógica principal: Intercepta el daño letal y ejecuta la salvación inmediatamente.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (!isTotemAllowedInWorld(player.getWorld().getName())) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {

            // <<<< LÓGICA DE PRIORIDAD INVERTIDA (CORREGIDA) >>>>
            // 1. Buscar NUESTRO tótem multiuso PRIMERO.
            int totemSlot = findMultiUseTotemSlot(player);

            if (totemSlot != -1) {
                // 2. Si lo encontramos, cancelamos el evento INMEDIATAMENTE.
                event.setCancelled(true);

                player.setHealth(player.getMaxHealth());
                player.setFireTicks(0);

                ItemStack totem = player.getInventory().getItem(totemSlot);
                String itemId = plugin.getItemManager().getItemId(totem);
                applyTotemEffects(player, itemId);

                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

                updateTotemUses(player, totemSlot);
            }
            // 3. Si no se encontró un tótem multiuso (totemSlot == -1),
            // no hacemos nada (no cancelamos el evento). La lógica de Vainilla
            // se ejecutará y consumirá el tótem normal de la mano secundaria.
        }
    }


    /**
     * Actúa como capa de seguridad si el EntityDamageEvent fue omitido o cancelado.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = (Player) event.getEntity();

        if (!isTotemAllowedInWorld(player.getWorld().getName())) {
            return;
        }

        // <<<< LÓGICA DE PRIORIDAD INVERTIDA (FALLBACK) >>>>
        int totemSlot = findMultiUseTotemSlot(player);

        if (totemSlot != -1) {
            event.setCancelled(true);
            player.setHealth(20.0);
            player.setFireTicks(0);

            ItemStack totem = player.getInventory().getItem(totemSlot);
            String itemId = plugin.getItemManager().getItemId(totem);
            applyTotemEffects(player, itemId);

            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

            updateTotemUses(player, totemSlot);
        }
        // Si no se encuentra, dejamos que el jugador muera (o que el tótem de vainilla lo salve si la lógica de onFatalDamage falló).
    }

    /**
     * Parsea y aplica los efectos de poción desde la configuración del ítem.
     */
    private void applyTotemEffects(Player player, String itemId) {
        if (itemId == null) return;

        List<String> effectStrings = plugin.getItemManager().getTotemEffects(itemId);
        if (effectStrings == null || effectStrings.isEmpty()) {
            return;
        }

        List<PotionEffect> effectsToApply = new ArrayList<>();
        for (String effectString : effectStrings) {
            try {
                String[] parts = effectString.split(";");
                if (parts.length == 3) {
                    PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                    int durationInSeconds = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]) - 1;

                    if (type != null) {
                        effectsToApply.add(new PotionEffect(type, durationInSeconds * 20, amplifier));
                    } else {
                        plugin.getLogger().warning("[TotemListener] Tipo de poción inválido: " + parts[0]);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[TotemListener] Error al parsear efecto del tótem: " + effectString);
            }
        }

        if (!effectsToApply.isEmpty()) {
            player.addPotionEffects(effectsToApply);
        }
    }


    /**
     * Busca un tótem multiuso y devuelve el SLOT de inventario donde se encuentra.
     */
    private int findMultiUseTotemSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

                if (container.has(magmaItemKey, PersistentDataType.STRING) && container.has(usesKey, PersistentDataType.INTEGER)) {
                    int uses = container.getOrDefault(usesKey, PersistentDataType.INTEGER, 0);
                    if (uses > 0) {
                        return i; // Devuelve el slot
                    }
                }
            }
        }
        return -1; // No encontrado
    }

    /**
     * Función crítica: Actualiza el ítem directamente en el slot del inventario.
     */
    private void updateTotemUses(Player player, int totemSlot) {

        ItemStack liveTotem = player.getInventory().getItem(totemSlot);

        if (liveTotem == null || !liveTotem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = liveTotem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        int currentUses = container.getOrDefault(usesKey, PersistentDataType.INTEGER, 0);

        if (currentUses <= 0) return;

        int newUses = currentUses - 1;
        String message;

        if (newUses <= 0) {
            player.getInventory().setItem(totemSlot, null);
            message = plugin.getConfig().getString("messages.totem-consumed",
                    "<red>El <gold>Tótem de Usos Múltiples</gold> se ha consumido por completo.");

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);

        } else {
            container.set(usesKey, PersistentDataType.INTEGER, newUses);

            String newCounterLineFormat = USES_LORE_PREFIX_FORMAT + newUses;
            List<Component> currentLoreComponents = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
            List<Component> finalClientLoreComponents = new ArrayList<>();
            boolean counterFound = false;

            for (Component component : currentLoreComponents) {

                String strippedLine = PLAIN_TEXT_SERIALIZER.serialize(component);

                if (strippedLine.startsWith(USES_LORE_STRIPPED_PREFIX) && !counterFound) {
                    Component renderedCounterComponent = plugin.getMiniMessage().deserialize(newCounterLineFormat);
                    finalClientLoreComponents.add(renderedCounterComponent);
                    counterFound = true;
                } else {
                    finalClientLoreComponents.add(component);
                }
            }

            if (!counterFound) {
                Component renderedCounterComponent = plugin.getMiniMessage().deserialize(newCounterLineFormat);
                finalClientLoreComponents.add(renderedCounterComponent);
            }

            meta.lore(finalClientLoreComponents);
            liveTotem.setItemMeta(meta);

            player.getInventory().setItem(totemSlot, liveTotem);

            String saveMsg = plugin.getConfig().getString("messages.totem-save",
                    "<green>¡El <gold>Tótem de Usos Múltiples</gold> te ha salvado! <gray>Usos restantes: <yellow>{uses}");
            message = saveMsg.replace("{uses}", String.valueOf(newUses));

            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.2F);
        }

        player.sendMessage(plugin.getMiniMessage().deserialize(message));
        player.updateInventory();
    }
}