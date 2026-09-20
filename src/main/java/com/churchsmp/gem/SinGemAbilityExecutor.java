package com.churchsmp.gem;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SinGemAbilityExecutor {

    private final ChurchSMP plugin;
    // Tracks Pride stacks: UUID -> Stacks (0-5)
    private final Map<UUID, Integer> prideStacks = new ConcurrentHashMap<>();
    // Tracks player standing still location for Sloth: UUID -> Location
    private final Map<UUID, Location> slothStillLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> slothStillTime = new ConcurrentHashMap<>();

    public SinGemAbilityExecutor(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Triggers the attuned active ability of a player's gem.
     */
    public boolean triggerGemAbility(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) {
            player.sendMessage(Component.text("You have not attuned to any Relic Gem yet!", NamedTextColor.RED));
            return false;
        }

        String abilityKey = "gem_" + gem.name().toLowerCase();
        if (plugin.getCooldownManager().isOnCooldown(player, abilityKey)) {
            double remaining = plugin.getCooldownManager().getRemainingCooldownSeconds(player, abilityKey);
            player.sendMessage(Component.text("Gem ability is on cooldown: " + remaining + "s", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("gems." + gem.name().toLowerCase() + ".cooldown", 35);
        int dur = plugin.getConfig().getInt("gems." + gem.name().toLowerCase() + ".duration", 8);

        plugin.getCooldownManager().setCooldown(player, abilityKey, cd);
        plugin.getCooldownManager().setActiveDuration(player, abilityKey, dur);
        plugin.getBossBarManager().showActiveCountdown(player, gem.getDisplayName() + " Surge", BossBar.Color.PURPLE, dur);

        switch (gem) {
            case WRATH -> activateWrath(player, dur);
            case GREED -> activateGreed(player, dur);
            case GLUTTONY -> activateGluttony(player, dur);
            case LUST -> activateLust(player, dur);
            case ENVY -> activateEnvy(player, dur);
            case PRIDE -> activatePride(player, dur);
            case SLOTH -> activateSloth(player, dur);
        }
        return true;
    }

    // -------------------------------------------------------------
    // WRATH
    // -------------------------------------------------------------
    private void activateWrath(Player player, int duration) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration * 20, 1, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.3f);

        new BukkitRunnable() {
            int ticks = duration * 20;

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // -------------------------------------------------------------
    // GREED
    // -------------------------------------------------------------
    private void activateGreed(Player player, int duration) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration * 20, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration * 20, 1, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
    }

    // -------------------------------------------------------------
    // GLUTTONY
    // -------------------------------------------------------------
    private void activateGluttony(Player player, int duration) {
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 0.9f);

        new BukkitRunnable() {
            int ticks = duration * 20;

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                player.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);

                for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(player.getLocation(), 6.0)) {
                    if (entity.equals(player)) continue;
                    entity.damage(2.0, player);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 2));

                    // Heal player slightly
                    double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + 1.0);
                    player.setHealth(newHealth);
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                    player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1);
                }

                ticks -= 20; // run drain every 1 second
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // -------------------------------------------------------------
    // LUST
    // -------------------------------------------------------------
    private void activateLust(Player player, int duration) {
        Location pLoc = player.getLocation();
        player.playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.HEART, pLoc.add(0, 1.5, 0), 30, 1.0, 1.0, 1.0, 0.1);

        double radius = plugin.getConfig().getDouble("gems.lust.pull_radius", 8.0);
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(player.getLocation(), radius)) {
            if (entity.equals(player)) continue;

            // Pull towards player
            Vector dir = player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
            dir.multiply(1.3).setY(0.4);
            entity.setVelocity(dir);

            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, 2));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration * 20, 1));
        }
    }

    // -------------------------------------------------------------
    // ENVY
    // -------------------------------------------------------------
    private void activateEnvy(Player player, int duration) {
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 25, 0.6, 0.6, 0.6, 0.05);

        // Find closest target
        LivingEntity target = null;
        double closest = 10.0;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 10.0)) {
            if (e.equals(player)) continue;
            double dist = e.getLocation().distance(player.getLocation());
            if (dist < closest) {
                closest = dist;
                target = e;
            }
        }

        if (target != null) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration * 20, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration * 20, 1));

            // Copy beneficial effects from target
            for (PotionEffect effect : target.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.SPEED) ||
                        effect.getType().equals(PotionEffectType.STRENGTH) ||
                        effect.getType().equals(PotionEffectType.RESISTANCE) ||
                        effect.getType().equals(PotionEffectType.REGENERATION)) {
                    player.addPotionEffect(new PotionEffect(effect.getType(), duration * 20, effect.getAmplifier()));
                }
            }
            player.sendMessage(Component.text("âœ¦ You siphoned power and positive buffs from " + target.getName() + "!", NamedTextColor.AQUA));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 1));
        }
    }

    // -------------------------------------------------------------
    // PRIDE
    // -------------------------------------------------------------
    private void activatePride(Player player, int duration) {
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 3);

        // Knockback burst
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 7.0)) {
            if (e.equals(player)) continue;
            Vector away = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.8).setY(0.5);
            e.setVelocity(away);
            e.damage(5.0, player);
        }

        // Set max pride stacks
        prideStacks.put(player.getUniqueId(), 5);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 1));
        player.sendMessage(Component.text("âœ¦ Monarch's Presence: Max Pride stacks gained! (Do not get hit!)", NamedTextColor.GOLD));
    }

    // -------------------------------------------------------------
    // SLOTH
    // -------------------------------------------------------------
    private void activateSloth(Player player, int duration) {
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = duration * 20;

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                player.getWorld().spawnParticle(Particle.PORTAL, loc.add(0, 0.5, 0), 20, 2.5, 0.5, 2.5, 0.05);

                for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(player.getLocation(), 6.5)) {
                    if (entity.equals(player)) continue;
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false));
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 2, false, false));
                }

                ticks -= 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // -------------------------------------------------------------
    // PASSIVE HANDLERS
    // -------------------------------------------------------------
    public void onPlayerHitEntity(Player player, LivingEntity target) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return;

        switch (gem) {
            case PRIDE -> {
                int current = prideStacks.getOrDefault(player.getUniqueId(), 0);
                if (current < 5) {
                    current++;
                    prideStacks.put(player.getUniqueId(), current);
                    player.sendMessage(Component.text("âœ¦ Pride Stack: " + current + "/5 (+ " + (current * 7) + "% damage)", NamedTextColor.GOLD));
                }
            }
            case GREED -> {
                // Chance to drop bonus gold nuggets
                if (Math.random() < 0.25) {
                    target.getWorld().dropItemNaturally(target.getLocation(), new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_NUGGET, 1));
                }
            }
        }
    }

    public void onPlayerDamaged(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return;

        if (gem == SinGemType.PRIDE) {
            int stacks = prideStacks.getOrDefault(player.getUniqueId(), 0);
            if (stacks > 0) {
                prideStacks.put(player.getUniqueId(), 0);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                player.sendMessage(Component.text("âœ¦ Your Pride was shattered! All stacks lost!", NamedTextColor.RED));
            }
        }
    }

    public double getDamageMultiplier(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return 1.0;

        double mult = 1.0;
        if (gem == SinGemType.PRIDE) {
            int stacks = prideStacks.getOrDefault(player.getUniqueId(), 0);
            mult += (stacks * 0.07);
        } else if (gem == SinGemType.WRATH) {
            String abilityKey = "gem_wrath";
            if (plugin.getCooldownManager().isActive(player, abilityKey)) {
                mult += (plugin.getConfig().getDouble("gems.wrath.damage_multiplier", 1.35) - 1.0);
            }
        }
        return mult;
    }
}
