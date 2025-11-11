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

    private static final String USES_LORE_PREFIX_FORMAT = "<!i><#FF3458>☄</#FF3458> <white>Usos restantes: </white><#FFD700>";
    private static final String USES_LORE_STRIPPED_PREFIX = "Usos restantes:";
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

    private boolean isTotemAllowedInWorld(String worldName) {
        List<String> allowedWorlds = plugin.getConfig().getStringList("totem.allowed-worlds");
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return allowedWorlds.contains(worldName);
    }

    /**
     * Lógica principal: Intercepta el daño letal.
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

            PlayerInventory inventory = player.getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            ItemStack offHand = inventory.getItemInOffHand();

            if (isVanillaTotem(mainHand) || isVanillaTotem(offHand)) {
                return;
            }

            int totemSlot = findMultiUseTotemSlot(player);

            if (totemSlot != -1) {
                event.setCancelled(true);

                player.setHealth(10.0);
                player.setFireTicks(0);

                ItemStack totem = player.getInventory().getItem(totemSlot);
                String itemId = plugin.getItemManager().getItemId(totem);
                applyTotemEffects(player, itemId);

                // Aplicar partículas desde el config
                spawnTotemParticles(player);

                updateTotemUses(player, totemSlot);
            }
        }
    }


    /**
     * Fallback
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = (Player) event.getEntity();

        if (!isTotemAllowedInWorld(player.getWorld().getName())) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();
        ItemStack offHand = inventory.getItemInOffHand();

        if (isVanillaTotem(mainHand) || isVanillaTotem(offHand)) {
            return;
        }

        int totemSlot = findMultiUseTotemSlot(player);

        if (totemSlot != -1) {
            event.setCancelled(true);
            player.setHealth(10.0);
            player.setFireTicks(0);

            ItemStack totem = player.getInventory().getItem(totemSlot);
            String itemId = plugin.getItemManager().getItemId(totem);
            applyTotemEffects(player, itemId);

            // Aplicar partículas desde el config
            spawnTotemParticles(player);

            updateTotemUses(player, totemSlot);
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
            // Eliminación final
            player.getInventory().setItem(totemSlot, null);
            message = plugin.getConfig().getString("messages.totem-consumed",
                    "<red>El <gold>Tótem de Usos Múltiples</gold> se ha consumido por completo.");

            playSoundFromConfig(player, "totem.sounds.consumed", Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);

        } else {
            // Actualizar NBT
            container.set(usesKey, PersistentDataType.INTEGER, newUses);

            // 1. Definir la nueva línea de contador como Component
            String newCounterLineFormat = USES_LORE_PREFIX_FORMAT + newUses;
            Component newCounterComponent = plugin.getMiniMessage().deserialize(newCounterLineFormat);

            // 2. Obtener el lore actual como Componentes (moderno)
            List<Component> currentLoreComponents = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
            List<Component> finalClientLoreComponents = new ArrayList<>();

            // 3. FILTRAR: Copiar solo las líneas que NO son contadores.
            for (Component component : currentLoreComponents) {

                // Convertir el Component a texto plano.
                String strippedLine = PLAIN_TEXT_SERIALIZER.serialize(component);

                // Buscar la posición de la subcadena "Usos restantes"
                int index = strippedLine.indexOf(USES_LORE_STRIPPED_PREFIX);

                // Si se encuentra "Usos restantes" Y está cerca del inicio (índice 0 a 5), es nuestra línea.
                if (index != -1 && index < 5) {
                    // Si ES un contador, se ignora (no se añade a la nueva lista).
                    continue;
                }

                // Si la línea NO es un contador, la mantenemos.
                finalClientLoreComponents.add(component);
            }

            // 4. Añadir: Añadimos la nueva línea de usos (ej. 9) al final de la lista filtrada.
            finalClientLoreComponents.add(newCounterComponent);

            meta.lore(finalClientLoreComponents); // Guardar la List<Component>
            liveTotem.setItemMeta(meta);

            // Forzar la reinserción del ítem actualizado en el slot.
            player.getInventory().setItem(totemSlot, liveTotem);

            // Mensaje de uso exitoso
            String saveMsg = plugin.getConfig().getString("messages.totem-save",
                    "<green>¡El <gold>Tótem de Usos Múltiples</gold> te ha salvado! <gray>Usos restantes: <yellow>{uses}");
            message = saveMsg.replace("{uses}", String.valueOf(newUses));

            playSoundFromConfig(player, "totem.sounds.use", Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.2F);
        }

        player.sendMessage(plugin.getMiniMessage().deserialize(message));
        player.updateInventory();
    }

    /**
     * <<<< NUEVO MÉTODO AUXILIAR PARA SONIDOS >>>>
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
     * <<<< NUEVO MÉTODO AUXILIAR PARA PARTÍCULAS >>>>
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