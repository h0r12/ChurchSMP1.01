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
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null && attr.getBaseValue() >= 40.0) {
            attr.setBaseValue(20.0);
            if (player.getHealth() > 20.0) player.setHealth(20.0);
        }
        Sorrowess.checkInventoryHearts(player, plugin);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
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
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>✦ Your hotbar is sealed by Taxing Ray! ✦</red>"));
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
        Mayim.restoreOffhand(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Mayim.restoreOffhand(event.getPlayer());
        plugin.getBossBarManager().removeBossBar(event.getPlayer());
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
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<blue>✦ [HEAVY RHYTHM] Sloth forbids villager trading! ✦</blue>"));
                return;
            }
            if (entity instanceof org.bukkit.entity.Animals) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held != null && held.getType().isEdible()) {
                    event.setCancelled(true);
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<blue>✦ [HEAVY RHYTHM] Sloth forbids animal breeding! ✦</blue>"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

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
}

