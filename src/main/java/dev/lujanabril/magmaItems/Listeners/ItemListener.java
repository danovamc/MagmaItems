package dev.lujanabril.magmaItems.Listeners;

import dev.lujanabril.magmaItems.Main;
import dev.lujanabril.magmaItems.Managers.ConfirmationManager;
import dev.lujanabril.magmaItems.Managers.ItemManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;

public class ItemListener implements Listener {
    private final Main plugin;
    private final ItemManager itemManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ConfirmationManager confirmationManager;
    private final NamespacedKey autosellToggleKey;

    private final Map<UUID, Long> actionCooldowns = new HashMap<>();
    private final long actionCooldownMillis;
    private final Map<UUID, BukkitTask> activeCooldownTasks = new HashMap<>();

    public ItemListener(Main plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager(); // <-- Corregido para usar el getter
        this.confirmationManager = new ConfirmationManager(plugin, this);
        // --- ESTA LÍNEA ESTÁ BIEN Y DEBE QUEDARSE ---
        this.autosellToggleKey = plugin.getItemManager().getAutosellToggleKey();

        this.actionCooldownMillis = (long) (plugin.getConfig().getDouble("global-action-cooldown-seconds", 3.0) * 1000);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT")) {
            ItemStack item = event.getItem();
            if (item != null && this.itemManager.isMagmaItem(item)) {
                String itemId = this.itemManager.getItemId(item);
                List<ItemManager.Action> actions = this.itemManager.getActions(itemId);
                List<ItemManager.Action> shiftActions = this.itemManager.getShiftClickActions(itemId);
                boolean requiresConfirmation = this.itemManager.requiresConfirmation(itemId);
                Player player = event.getPlayer();
                boolean isShifting = player.isSneaking();
                boolean hasActions = !actions.isEmpty() || !shiftActions.isEmpty() || requiresConfirmation;
                if (hasActions) {
                    event.setCancelled(true);

                    UUID playerUuid = player.getUniqueId();
                    long currentTime = System.currentTimeMillis();
                    long lastActionTime = this.actionCooldowns.getOrDefault(playerUuid, 0L);

                    // >>>>> INICIO LÓGICA COOLDOWN MODIFICADA <<<<<
                    if (currentTime - lastActionTime < this.actionCooldownMillis) {

                        // 1. Reproducir el sonido de error (VILLAGER_NO) en cada clic
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                        // 2. Si YA se está mostrando el contador, no creamos otro nuevo
                        if (activeCooldownTasks.containsKey(playerUuid)) {
                            return;
                        }

                        // 3. Crear la tarea que actualiza el Action Bar cada 2 ticks (0.1s)
                        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                            // Si el jugador se desconecta, cancelar
                            if (!player.isOnline()) {
                                BukkitTask t = activeCooldownTasks.remove(playerUuid);
                                if (t != null) t.cancel();
                                return;
                            }

                            // Calcular tiempo restante en este instante exacto
                            long now = System.currentTimeMillis();
                            long remainingMillis = this.actionCooldownMillis - (now - lastActionTime);

                            // Si el tiempo terminó
                            if (remainingMillis <= 0) {
                                BukkitTask t = activeCooldownTasks.remove(playerUuid);
                                if (t != null) t.cancel();
                                // Opcional: Limpiar el action bar o poner "¡Listo!"
                                player.sendActionBar(net.kyori.adventure.text.Component.empty());
                                return;
                            }

                            // Formatear y enviar mensaje
                            double remainingSeconds = remainingMillis / 1000.0;
                            String formattedTime = String.format("%.1f", remainingSeconds);

                            String cooldownMessage = plugin.getConfig().getString("messages.action-cooldown-message", "<red>¡Espera {time}s para cambiar de modo!");
                            cooldownMessage = cooldownMessage.replace("{time}", formattedTime);

                            player.sendActionBar(miniMessage.deserialize(cooldownMessage));

                        }, 0L, 2L); // Ejecutar inmediatamente (0L) y repetir cada 2 ticks (2L)

                        // Guardar la tarea
                        activeCooldownTasks.put(playerUuid, task);
                        return;
                    }
                    // >>>>> FIN LÓGICA COOLDOWN MODIFICADA <<<<<

                    // Si la acción fue exitosa (pasó el cooldown):
                    this.actionCooldowns.put(playerUuid, currentTime);

                    // Limpieza de seguridad: Si había una tarea corriendo, cancelarla
                    if (activeCooldownTasks.containsKey(playerUuid)) {
                        activeCooldownTasks.remove(playerUuid).cancel();
                    }

                    if (isShifting && !shiftActions.isEmpty()) {
                        this.executeShiftActions(player, item, itemId);
                    } else {
                        if (!isShifting) {
                            if (requiresConfirmation) {
                                this.confirmationManager.openConfirmationMenu(player, item, itemId);
                                return;
                            }

                            if (!actions.isEmpty()) {
                                this.executeItemActions(player, item, itemId);
                            }
                        }

                    }
                }
            }
        }
    }

    public void executeItemActions(Player player, ItemStack item, String itemId) {
        List<ItemManager.Action> actions = this.itemManager.getActions(itemId);
        if (actions != null && !actions.isEmpty()) {
            if (item == null) {
                item = this.findItemInInventory(player, itemId);
                if (item == null) {
                    player.sendMessage(this.miniMessage.deserialize("<red><b>ERROR</b> <dark_gray>▸</dark_gray> <white>¡No puedes hacer esto!</white> <gray><i>¿Que estas intentando? el Staff ha sido notificado</i></gray>"));
                    return;
                }
            }

            if (item.getAmount() < 1) {
                player.sendMessage(this.miniMessage.deserialize("<red><b>ERROR</b> <dark_gray>▸</dark_gray> <white>¡No puedes hacer esto!</white> <gray><i>¿Que estas intentando? el Staff ha sido notificado</i></gray>"));
            } else {
                for(ItemManager.Action action : actions) {
                    // --- LÍNEA MODIFICADA (Pasar el 'item') ---
                    this.executeAction(action, player, item);
                }

                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    public void executeShiftActions(Player player, ItemStack item, String itemId) {
        List<ItemManager.Action> shiftActions = this.itemManager.getShiftClickActions(itemId);
        if (shiftActions != null && !shiftActions.isEmpty()) {
            for(ItemManager.Action action : shiftActions) {
                // --- LÍNEA MODIFICADA (Pasar el 'item') ---
                this.executeAction(action, player, item);
            }

        }
    }

    private ItemStack findItemInInventory(Player player, String itemId) {
        for(ItemStack item : player.getInventory().getContents()) {
            if (item != null && this.itemManager.isMagmaItem(item) && itemId.equals(this.itemManager.getItemId(item))) {
                return item;
            }
        }

        return null;
    }

    private void executeAction(ItemManager.Action action, Player player, ItemStack item) {
        if (action != null) {
            String type = action.getType();
            String rawValue = this.parsePlaceholders(action.getRawValue().replace("%player%", player.getName()));

            // --- INICIO DE LÓGICA AÑADIDA ---
            if ("[TOGGLE_AUTOSELL]".equals(type)) {
                if (item == null || item.getType().isAir()) return;
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;

                PersistentDataContainer container = meta.getPersistentDataContainer();
                boolean isCurrentlyOn = container.getOrDefault(this.autosellToggleKey, PersistentDataType.BYTE, (byte)0) == (byte)1;

                String prefix_on = "messages.toggle-autosell-on-";
                String prefix_off = "messages.toggle-autosell-off-";
                String targetPrefix;

                if (isCurrentlyOn) {
                    // Está ON -> Desactivar
                    container.set(this.autosellToggleKey, PersistentDataType.BYTE, (byte)0);
                    targetPrefix = prefix_off;
                } else {
                    // Está OFF -> Activar
                    container.set(this.autosellToggleKey, PersistentDataType.BYTE, (byte)1);
                    targetPrefix = prefix_on;
                }

                item.setItemMeta(meta); // Guardar el NBT modificado en el item

                // Enviar Feedback
                String chatMsg = parsePlaceholders(plugin.getConfig().getString(targetPrefix + "chat", ""));
                String titleMsg = parsePlaceholders(plugin.getConfig().getString(targetPrefix + "title", ""));
                String subtitleMsg = parsePlaceholders(plugin.getConfig().getString(targetPrefix + "subtitle", ""));
                String actionbarMsg = parsePlaceholders(plugin.getConfig().getString(targetPrefix + "actionbar", ""));

                if (!chatMsg.isEmpty()) player.sendMessage(miniMessage.deserialize(chatMsg));
                if (!actionbarMsg.isEmpty()) player.sendActionBar(miniMessage.deserialize(actionbarMsg));

                if (!titleMsg.isEmpty() || !subtitleMsg.isEmpty()) {
                    Title.Times times = Times.times(Duration.ofMillis(500), Duration.ofMillis(1500), Duration.ofMillis(500));
                    Title title = Title.title(miniMessage.deserialize(titleMsg), miniMessage.deserialize(subtitleMsg), times);
                    player.showTitle(title);
                }

                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_WOLF_ARMOR_REPAIR, 0.5f, 1.0f);

                return; // Acción completada
            }
            // --- FIN DE LÓGICA AÑADIDA ---

            switch (type) {
                case "[CONSOLE]":
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rawValue);
                    break;
                case "[ACTIONBAR]":
                    String actionBarText = this.parsePlaceholders(action.getRawValue());
                    player.sendActionBar(this.miniMessage.deserialize(actionBarText));
                    break;
                case "[MESSAGE]":
                    String messageText = this.parsePlaceholders(action.getRawValue());
                    player.sendMessage(this.miniMessage.deserialize(messageText));
                    break;
                case "[TITLE]":
                    if (action.getFadeIn() < 0 || action.getStay() < 0 || action.getFadeOut() < 0) {
                        return;
                    }

                    Title.Times times = Times.times(Duration.ofMillis((long)(action.getFadeIn() * 50)), Duration.ofMillis((long)(action.getStay() * 50)), Duration.ofMillis((long)(action.getFadeOut() * 50)));
                    String titleText = this.parsePlaceholders(action.getRawTitle());
                    String subtitleText = this.parsePlaceholders(action.getRawSubtitle());
                    Title title = Title.title(this.miniMessage.deserialize(titleText), this.miniMessage.deserialize(subtitleText), times);
                    player.showTitle(title);
                    break;
                case "[SOUND]":
                    try {
                        String[] soundParts = rawValue.split(";");
                        if (soundParts.length >= 3) {
                            player.playSound(player.getLocation(), soundParts[0], Float.parseFloat(soundParts[1]), Float.parseFloat(soundParts[2]));
                        } else {
                            this.plugin.getLogger().warning("Sound action has invalid format: " + rawValue);
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Error executing sound action: " + e.getMessage());
                    }
                    break;
                case "[EFFECT]":
                    try {
                        String[] effectParts = rawValue.split(";");
                        if (effectParts.length >= 1) {
                            String effectName = effectParts[0];
                            int duration = effectParts.length >= 2 ? Integer.parseInt(effectParts[1]) : 60;
                            int amplifier = effectParts.length >= 3 ? Integer.parseInt(effectParts[2]) : 0;
                            boolean ambient = effectParts.length >= 4 ? Boolean.parseBoolean(effectParts[3]) : false;
                            boolean particles = effectParts.length >= 5 ? Boolean.parseBoolean(effectParts[4]) : true;
                            PotionEffectType effectType = PotionEffectType.getByName(effectName);
                            if (effectType != null) {
                                PotionEffect effect = new PotionEffect(effectType, duration * 20, amplifier, ambient, particles);
                                player.addPotionEffect(effect);
                            } else {
                                this.plugin.getLogger().warning("Invalid effect type: " + effectName);
                            }
                        } else {
                            this.plugin.getLogger().warning("Effect action has invalid format: " + rawValue);
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Error executing effect action: " + e.getMessage());
                    }
                    break;
                case "[FIREWORK]":
                    try {
                        final Firework firework = (Firework)player.getWorld().spawn(player.getLocation(), Firework.class);
                        FireworkMeta meta = firework.getFireworkMeta();
                        FireworkEffect.Builder effectBuilder = FireworkEffect.builder();

                        for(int[] rgb : action.getColors()) {
                            effectBuilder.withColor(Color.fromRGB(rgb[0], rgb[1], rgb[2]));
                        }

                        effectBuilder.with(Type.valueOf(action.getFireworkType()));
                        if (action.getFadeColor() != null) {
                            effectBuilder.withFade(Color.fromRGB(action.getFadeColor()[0], action.getFadeColor()[1], action.getFadeColor()[2]));
                        }

                        if (action.hasTrail()) {
                            effectBuilder.trail(true);
                        }

                        meta.addEffect(effectBuilder.build());
                        meta.setPower(action.getPower());
                        firework.setFireworkMeta(meta);
                        Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler
                            public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
                                if (event.getDamager().equals(firework)) {
                                    event.setCancelled(true);
                                }

                            }
                        }, this.plugin);
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Error executing firework action: " + e.getMessage());
                    }
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
}