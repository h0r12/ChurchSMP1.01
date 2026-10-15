package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.Grim;
import com.churchsmp.weapon.LegendaryWeapon;
import com.churchsmp.weapon.Mayim;
import com.churchsmp.weapon.Sorrowess;
import com.churchsmp.weapon.VoidBreaker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final ChurchSMP plugin;

    public PlayerListener(ChurchSMP plugin) {
        this.plugin = plugin;
        startAlignmentPassivesTask();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSinGemManager().getGemType(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (attr != null && attr.getBaseValue() >= 40.0) {
            attr.setBaseValue(20.0);
            if (player.getHealth() > 20.0) player.setHealth(20.0);
        }
        Sorrowess.checkInventoryHearts(player, plugin);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            plugin.getGemAbilityExecutor().handleLuminateSneakQTE(player);
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            weapon.onCrouch(player, event.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);

        if (weapon instanceof VoidBreaker vb) {
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);
            vb.handleDoubleJump(player);
            // Flight is re-enabled inside handleDoubleJump via reEnableFlightDelayed()
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);

        // Allow flight whenever holding Voidbreaker so double jump triggers mid-air
        if (weapon instanceof VoidBreaker) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }

        // Mayim Honor check: stows offhand while holding Mayim
        if (weapon instanceof Mayim) {
            Mayim.checkAndStashOffhand(player);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGemAbilityExecutor().isSealed(player)) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Your hotbar is sealed by Taxing Ray.</red>"));
            return;
        }

        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack current = player.getInventory().getItem(event.getNewSlot());

        LegendaryWeapon prevW = plugin.getWeaponManager().getWeapon(previous);
        LegendaryWeapon currW = plugin.getWeaponManager().getWeapon(current);

        // Mayim Honor: restore when switching away, stash when equipped
        if (prevW instanceof Mayim && !(currW instanceof Mayim)) {
            Mayim.restoreOffhand(player);
        } else if (currW instanceof Mayim) {
            Mayim.checkAndStashOffhand(player);
        }

        // Disable flight if switching away from Voidbreaker
        if (prevW instanceof VoidBreaker && !(currW instanceof VoidBreaker)) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Sorrowess.checkInventoryHearts(player, plugin);
            }
        }, 1L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if (plugin.getGemAbilityExecutor().isHoldingUndyingSharp(player) || (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "undying_sharp"), org.bukkit.persistence.PersistentDataType.BYTE))) {
            event.setCancelled(true);
            plugin.getGemAbilityExecutor().revertUndyingSharp(player);
            return;
        }

        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);
        if (weapon instanceof Mayim) {
            Mayim.restoreOffhand(player);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Sorrowess.checkInventoryHearts(player, plugin);
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    Sorrowess.checkInventoryHearts(player, plugin);
                    ItemStack held = player.getInventory().getItemInMainHand();
                    LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(held);
                    if (weapon instanceof Mayim) {
                        Mayim.checkAndStashOffhand(player);
                    } else if (Mayim.stashedOffhand.containsKey(player.getUniqueId())) {
                        Mayim.restoreOffhand(player);
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    Sorrowess.checkInventoryHearts(player, plugin);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        plugin.getGemAbilityExecutor().revertUndyingSharp(event.getPlayer());
        Mayim.restoreOffhand(event.getPlayer());
        if (plugin.getDoppelgangerManager() != null) {
            plugin.getDoppelgangerManager().cleanup(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGemAbilityExecutor().revertUndyingSharp(event.getPlayer());
        Mayim.restoreOffhand(event.getPlayer());
        plugin.getBossBarManager().removeBossBar(event.getPlayer());
        if (plugin.getDoppelgangerManager() != null) {
            plugin.getDoppelgangerManager().cleanup(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onExpChange(org.bukkit.event.player.PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getAlignmentManager().getAlignmentScore(player) > 0) {
            double multiplier = plugin.getAlignmentManager().getExpMultiplier(player);
            if (multiplier > 1.0) {
                int bonus = (int) Math.round(event.getAmount() * (multiplier - 1.0));
                event.setAmount(event.getAmount() + bonus);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        com.churchsmp.gem.SinGemType gem = plugin.getSinGemManager().getHeldGem(player);
        if (gem == com.churchsmp.gem.SinGemType.GLUTTONY) {
            plugin.getGemAbilityExecutor().handleGluttonyFood(player, event.getItem(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        com.churchsmp.gem.SinGemType gem = plugin.getSinGemManager().getHeldGem(player);
        if (gem == com.churchsmp.gem.SinGemType.SLOTH) {
            org.bukkit.entity.Entity entity = event.getRightClicked();
            if (entity instanceof org.bukkit.entity.Villager) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<blue>[Sloth] Villager trading is forbidden.</blue>"));
                return;
            }
            if (entity instanceof org.bukkit.entity.Animals) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held != null && held.getType().isEdible()) {
                    event.setCancelled(true);
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<blue>[Sloth] Animal breeding is forbidden.</blue>"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Grace Passive: ZEN Gem holder converts negative effects into Resistance III, and doubles holy/positive buff duration (+100%)
        if (plugin.getSinGemManager() != null && plugin.getSinGemManager().isHoldingGem(player, com.churchsmp.gem.SinGemType.ZEN)) {
            org.bukkit.potion.PotionEffect effect = event.getNewEffect();
            org.bukkit.potion.PotionEffectType type = event.getModifiedType();
            if (isHarmfulEffect(type)) {
                event.setCancelled(true);
                int duration = (effect != null && effect.getDuration() > 0) ? Math.min(effect.getDuration(), 72000) : 120;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.RESISTANCE, duration, 2, false, false, true
                        ));
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 10, 0.3, 0.4, 0.3, 0.05);
                        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<#d8b4fe>Grace: <white>" + type.getKey().getKey() + "</white> ➔ <gold>Resistance III</gold></#d8b4fe>"));
                    }
                });
                return;
            } else if (effect != null && event.getAction() == org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED) {
                if (effect.getDuration() < 72000) {
                    int newDuration = (int) Math.min(72000, effect.getDuration() * 2L);
                    org.bukkit.potion.PotionEffect doubled = new org.bukkit.potion.PotionEffect(
                            effect.getType(), newDuration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()
                    );
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.addPotionEffect(doubled);
                        }
                    });
                }
            }
        }

        // Bloodfeast Passive: SIN Gem holder is totally immune to negative effects; converts them into Strength III for same duration!
        if (plugin.getSinGemManager() != null && plugin.getSinGemManager().isHoldingGem(player, com.churchsmp.gem.SinGemType.SIN)) {
            org.bukkit.potion.PotionEffect effect = event.getNewEffect();
            org.bukkit.potion.PotionEffectType type = event.getModifiedType();
            if (isHarmfulEffect(type)) {
                event.setCancelled(true);
                int duration = (effect != null && effect.getDuration() > 0) ? Math.min(effect.getDuration(), 72000) : 120;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.STRENGTH, duration, 2, false, false, true
                        ));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.7f, 1.6f);
                        player.getWorld().spawnParticle(org.bukkit.Particle.CRIMSON_SPORE, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.5, 0.3, 0.05);
                        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<dark_red>[Bloodfeast] Debuff converted to Strength III.</dark_red>"));
                    }
                });
                return;
            }
        }

        // Dark Resilience: Depraved players (score <= -50) are immune to Darkness & Wither
        if (plugin.getAlignmentManager().hasDarkResilience(player)) {
            org.bukkit.potion.PotionEffectType type = event.getModifiedType();
            if (type == org.bukkit.potion.PotionEffectType.DARKNESS || type == org.bukkit.potion.PotionEffectType.WITHER) {
                event.setCancelled(true);
                return;
            }
        }

        // Holy Grace: Positive potion effects last up to 50% longer for Good players
        if (event.getAction() == org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED) {
            org.bukkit.potion.PotionEffect effect = event.getNewEffect();
            if (effect != null && plugin.getAlignmentManager().getAlignmentScore(player) > 0) {
                double mult = plugin.getAlignmentManager().getPotionDurationMultiplier(player);
                if (mult > 1.0) {
                    org.bukkit.potion.PotionEffectType type = effect.getType();
                    boolean isPositive = type == org.bukkit.potion.PotionEffectType.SPEED
                            || type == org.bukkit.potion.PotionEffectType.HASTE
                            || type == org.bukkit.potion.PotionEffectType.STRENGTH
                            || type == org.bukkit.potion.PotionEffectType.REGENERATION
                            || type == org.bukkit.potion.PotionEffectType.RESISTANCE
                            || type == org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE
                            || type == org.bukkit.potion.PotionEffectType.WATER_BREATHING
                            || type == org.bukkit.potion.PotionEffectType.INVISIBILITY
                            || type == org.bukkit.potion.PotionEffectType.NIGHT_VISION
                            || type == org.bukkit.potion.PotionEffectType.ABSORPTION;

                    if (isPositive && effect.getDuration() < 72000) {
                        int newDuration = (int) Math.round(effect.getDuration() * mult);
                        org.bukkit.potion.PotionEffect extended = new org.bukkit.potion.PotionEffect(
                                type, newDuration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()
                        );
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.addPotionEffect(extended);
                            }
                        });
                    }
                }
            }
        }
    }

    private void startAlignmentPassivesTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!player.isValid() || player.isDead()) continue;

                // Holy Radiance: Regeneration for score >= 50
                if (plugin.getAlignmentManager().hasHolyRadiance(player)) {
                    if (!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION)) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.REGENERATION, 80, 0, false, false, true
                        ));
                    }
                }
                // Absolute Balance: +10% Speed for Neutral players (score == 0)
                else if (plugin.getAlignmentManager().getAlignmentScore(player) == 0) {
                    if (!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED)) {
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SPEED, 80, 0, false, false, true
                        ));
                    }
                }
            }
        }, 40L, 40L);
    }

    private boolean isHarmfulEffect(org.bukkit.potion.PotionEffectType type) {
        if (type == null) return false;
        try {
            return type.getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL;
        } catch (Throwable ignored) {
            return type == org.bukkit.potion.PotionEffectType.SLOWNESS
                    || type == org.bukkit.potion.PotionEffectType.MINING_FATIGUE
                    || type == org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE
                    || type == org.bukkit.potion.PotionEffectType.NAUSEA
                    || type == org.bukkit.potion.PotionEffectType.BLINDNESS
                    || type == org.bukkit.potion.PotionEffectType.HUNGER
                    || type == org.bukkit.potion.PotionEffectType.WEAKNESS
                    || type == org.bukkit.potion.PotionEffectType.POISON
                    || type == org.bukkit.potion.PotionEffectType.WITHER
                    || type == org.bukkit.potion.PotionEffectType.LEVITATION
                    || type == org.bukkit.potion.PotionEffectType.UNLUCK
                    || type == org.bukkit.potion.PotionEffectType.BAD_OMEN
                    || type == org.bukkit.potion.PotionEffectType.DARKNESS;
        }
    }
}

