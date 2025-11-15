package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TotemListener implements Listener {

    private final Main plugin;
    private final NamespacedKey magmaItemKey;
    private final NamespacedKey usesKey;

    // <<<< MODIFICADO: Ahora guarda una LISTA de tótems a restaurar >>>>
    private final Map<UUID, List<ItemStack>> totemToRestore = new HashMap<>();

    private static final String USES_LORE_STRIPPED_PREFIX = "• Usos ";
    private static final PlainTextComponentSerializer PLAIN_TEXT_SERIALIZER = PlainTextComponentSerializer.plainText();

    public TotemListener(Main plugin) {
        this.plugin = plugin;
        this.magmaItemKey = new NamespacedKey(plugin, "magma_item");
        this.usesKey = new NamespacedKey(plugin, "totem_uses");
    }

    private boolean isVanillaTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (container.has(usesKey, PersistentDataType.INTEGER)) {
            return false;
        }
        return true;
    }

    // <<<< NUEVO: Método para chequear si un item es un tótem custom con usos >>>>
    private boolean isCustomTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (container.has(magmaItemKey, PersistentDataType.STRING) && container.has(usesKey, PersistentDataType.INTEGER)) {
            return container.getOrDefault(usesKey, PersistentDataType.INTEGER, 0) > 0;
        }
        return false;
    }


    private boolean isTotemAllowedInWorld(String worldName) {
        List<String> allowedWorlds = plugin.getConfig().getStringList("totem.allowed-worlds");
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return allowedWorlds.contains(worldName);
    }

    /**
     * Lógica principal: Intercepta el daño letal.
     * Prioridad HIGHEST para ejecutarse antes que otros plugins y vanilla.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Solo nos importa si el daño es fatal
        if (player.getHealth() - event.getFinalDamage() <= 0) {

            PlayerInventory inventory = player.getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            ItemStack offHand = inventory.getItemInOffHand();

            // 1. Si es un tótem vanilla REAL en CUALQUIER MANO, dejamos que Minecraft se encargue.
            if (isVanillaTotem(offHand) || isVanillaTotem(mainHand)) {
                return;
            }

            // 2. <<<< LÓGICA MODIFICADA >>>>
            // Comprobamos si el mundo está permitido
            if (isTotemAllowedInWorld(player.getWorld().getName())) {

                // 3a. MUNDO PERMITIDO: Buscamos un tótem para usar, respetando la prioridad
                int totemSlot = findPriorityTotemSlot(player);

                if (totemSlot != -1) {
                    // ¡Encontrado! Salvamos al jugador
                    event.setCancelled(true); // Cancela el daño fatal

                    player.setHealth(10.0);
                    player.setFireTicks(0);

                    // Pasa el slot específico para consumir el tótem correcto
                    ItemStack totem = player.getInventory().getItem(totemSlot);
                    String itemId = plugin.getItemManager().getItemId(totem);
                    applyTotemEffects(player, itemId);

                    spawnTotemParticles(player);
                    updateTotemUses(player, totemSlot); // Consume un uso
                }
                // Si no se encuentra un tótem custom, no hacemos nada y dejamos que muera.

            } else {
                // 3b. MUNDO NO PERMITIDO:
                // Debemos encontrar y quitar TODOS los tótems custom para evitar el pop vanilla.

                Map<Integer, ItemStack> totemsToRemove = findAllCustomTotems(player);

                if (totemsToRemove.isEmpty()) {
                    // No hay tótems custom, dejamos que muera.
                    return;
                }

                // Guardamos TODOS los tótems para restaurarlos
                totemToRestore.put(player.getUniqueId(), new ArrayList<>(totemsToRemove.values()));

                // Quitamos TODOS los tótems del inventario AHORA
                for (Integer slot : totemsToRemove.keySet()) {
                    player.getInventory().setItem(slot, null);
                }

                // NO cancelamos el evento. El jugador ahora morirá
                // 100% normal, sin tótems, y DeluxeCombat lo anunciará UNA vez.
            }
        }
    }


    /**
     * <<<< EVENTO onPlayerRespawn MODIFICADO >>>>
     * Ahora restaura una LISTA de tótems.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        List<ItemStack> totems = totemToRestore.remove(player.getUniqueId());

        if (totems != null && !totems.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Devuelve todos los tótems que se quitaron
                for (ItemStack totem : totems) {
                    player.getInventory().addItem(totem);
                }
            }, 1L);
        }
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

    // <<<< MÉTODO `findMultiUseTotemSlot` ELIMINADO >>>>
    // (Reemplazado por `findPriorityTotemSlot` y `findAllCustomTotems`)


    /**
     * <<<< NUEVO MÉTODO >>>>
     * Busca un tótem custom con usos, respetando la prioridad de Vanilla:
     * 1. Mano Secundaria
     * 2. Mano Principal
     * 3. Hotbar (de izquierda a derecha)
     * 4. Inventario (de arriba a abajo, izquierda a derecha)
     */
    private int findPriorityTotemSlot(Player player) {
        PlayerInventory inv = player.getInventory();

        // 1. Mano Secundaria (Slot 40)
        if (isCustomTotem(inv.getItemInOffHand())) {
            return 40;
        }

        // 2. Mano Principal (Slot actual)
        if (isCustomTotem(inv.getItemInMainHand())) {
            return inv.getHeldItemSlot();
        }

        // 3. Hotbar (Slots 0-8)
        for (int i = 0; i < 9; i++) {
            if (i == inv.getHeldItemSlot()) continue; // Ya chequeamos la mano principal
            if (isCustomTotem(inv.getItem(i))) {
                return i;
            }
        }

        // 4. Inventario principal (Slots 9-35)
        for (int i = 9; i <= 35; i++) {
            if (isCustomTotem(inv.getItem(i))) {
                return i;
            }
        }

        return -1; // No encontrado
    }

    /**
     * <<<< NUEVO MÉTODO >>>>
     * Busca TODOS los tótems custom con usos en el inventario.
     * Devuelve un Mapa de <Slot, ItemStack>
     */
    private Map<Integer, ItemStack> findAllCustomTotems(Player player) {
        Map<Integer, ItemStack> totems = new HashMap<>();
        ItemStack[] contents = player.getInventory().getContents(); // Incluye armadura, inventario y manos

        // Revisamos inventario principal (0-35) y mano secundaria (40)
        for (int i = 0; i < contents.length; i++) {
            // El slot 36-39 son armadura. El 40 es offhand.
            if (i >= 36 && i <= 39) continue;

            ItemStack item = contents[i];
            if (isCustomTotem(item)) {
                totems.put(i, item);
            }
        }
        return totems;
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
            // Eliminación final
            player.getInventory().setItem(totemSlot, null);
            message = plugin.getConfig().getString("messages.totem-consumed",
                    "<red>El <gold>Tótem de Usos Múltiples</gold> se ha consumido por completo.");

            playSoundFromConfig(player, "totem.sounds.consumed", Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);

        } else {
            // Actualizar NBT
            container.set(usesKey, PersistentDataType.INTEGER, newUses);

            // LÓGICA DE ACTUALIZACIÓN DE LORE CORREGIDA
            List<Component> currentLoreComponents = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
            List<Component> finalClientLoreComponents = new ArrayList<>();

            // Iterar y REEMPLAZAR la línea de usos
            for (Component component : currentLoreComponents) {

                String strippedLine = PLAIN_TEXT_SERIALIZER.serialize(component).trim();

                if (strippedLine.startsWith(USES_LORE_STRIPPED_PREFIX)) {
                    String originalMiniMessage = plugin.getMiniMessage().serialize(component);

                    // CORRECCIÓN: Reemplazar la ÚLTIMA instancia del número de usos
                    String usesAsString = String.valueOf(currentUses);
                    int lastIndex = originalMiniMessage.lastIndexOf(usesAsString);

                    if (lastIndex != -1) {
                        String before = originalMiniMessage.substring(0, lastIndex);
                        String after = originalMiniMessage.substring(lastIndex + usesAsString.length());
                        String updatedMiniMessage = before + newUses + after;

                        finalClientLoreComponents.add(plugin.getMiniMessage().deserialize(updatedMiniMessage));
                    } else {
                        finalClientLoreComponents.add(component);
                    }

                } else {
                    finalClientLoreComponents.add(component);
                }
            }


            meta.lore(finalClientLoreComponents);
            liveTotem.setItemMeta(meta);

            player.getInventory().setItem(totemSlot, liveTotem);

            String saveMsg = plugin.getConfig().getString("messages.totem-save",
                    "<green>¡El <gold>Tótem de Usos Múltiples</gold> te ha salvado! <gray>Usos restantes: <yellow>{uses}");
            message = saveMsg.replace("{uses}", String.valueOf(newUses));

            playSoundFromConfig(player, "totem.sounds.use", Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.2F);
        }

        player.sendMessage(plugin.getMiniMessage().deserialize(message));
        player.updateInventory();
    }

    /**
     * Reproduce un sonido basado en la config.yml, con valores por defecto.
     */
    private void playSoundFromConfig(Player player, String configPath, Sound defaultSound, float defaultVolume, float defaultPitch) {
        try {
            String soundName = plugin.getConfig().getString(configPath + ".name", defaultSound.name());
            float volume = (float) plugin.getConfig().getDouble(configPath + ".volume", defaultVolume);
            float pitch = (float) plugin.getConfig().getDouble(configPath + ".pitch", defaultPitch);

            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);

        } catch (Exception e) {
            plugin.getLogger().warning("[TotemListener] Error al reproducir sonido desde la config '" + configPath + "'. Usando sonido por defecto.");
            player.playSound(player.getLocation(), defaultSound, defaultVolume, defaultPitch);
        }
    }

    /**
     * Genera partículas basadas en la config.yml.
     */
    private void spawnTotemParticles(Player player) {
        try {
            String particleName = plugin.getConfig().getString("totem.particles.type", "TOTEM_OF_UNDYING");
            int count = plugin.getConfig().getInt("totem.particles.count", 50);
            double offset = plugin.getConfig().getDouble("totem.particles.offset", 0.5);
            double speed = plugin.getConfig().getDouble("totem.particles.speed", 0.1);

            Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), count, offset, offset, offset, speed);

        } catch (Exception e) {
            plugin.getLogger().warning("[TotemListener] Error al generar partículas desde la config. Usando partículas por defecto.");
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        }
    }
}