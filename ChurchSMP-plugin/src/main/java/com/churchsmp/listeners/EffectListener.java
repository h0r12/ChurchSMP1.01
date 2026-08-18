package com.churchsmp.listeners;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Handles the passive side of alignment: periodic buffs for Good tiers,
 * periodic debuffs for Evil tiers, holy-ground interactions, and fall-damage
 * negation for Righteous/Saint players.
 */
public class EffectListener implements Listener {

    private final ChurchSMP plugin;
    private final AlignmentManager alignment;
    private final Random random = new Random();
    private final NamespacedKey fallenHeartsKey;
    private AttributeModifier fallenHeartsModifier;

    public EffectListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignment = plugin.getAlignmentManager();
        this.fallenHeartsKey = new NamespacedKey(plugin, "fallen_hearts_penalty");
    }

    /** Call once from onEnable to start the passive-effect tick loop. */
    public void startTicking() {
        int intervalSeconds = plugin.getConfig().getInt("effects.tick-interval-seconds", 5);
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    applyPassiveEffects(player);
                }
            }
        }.runTaskTimer(plugin, 20L, intervalSeconds * 20L);
    }

    private void applyPassiveEffects(Player player) {
        if (plugin.getNullifiedZoneManager().isNullified(player)) {
            return; // Wisdom's Verdict suppresses passive effects too
        }

        AlignmentTier tier = alignment.getTier(player);
        boolean onHolyGround = plugin.getChurchRegionManager().isHolyGround(player.getLocation());
        int durationTicks = plugin.getConfig().getInt("effects.tick-interval-seconds", 5) * 20 + 20;

        switch (tier) {
            case SAINT -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 0, true, false));
                if (random.nextInt(4) == 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, 0, true, false));
                }
                removeFallenHeartsPenalty(player);
            }
            case RIGHTEOUS -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 0, true, false));
                removeFallenHeartsPenalty(player);
            }
            case NULLIFIED -> removeFallenHeartsPenalty(player);
            case WICKED -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, durationTicks, 0, true, false));
                if (onHolyGround) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 1, true, false));
                }
                removeFallenHeartsPenalty(player);
            }
            case FALLEN -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 0, true, false));
                applyFallenHeartsPenalty(player);
                if (onHolyGround && plugin.getConfig().getBoolean("effects.fallen.holy-ground-burns", true)
                        && player.getWorld().getTime() < 12000) {
                    player.setFireTicks(60);
                }
            }
        }
    }

    private void applyFallenHeartsPenalty(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null || attr.getModifier(fallenHeartsKey) != null) return;
        int heartsRemoved = plugin.getConfig().getInt("effects.fallen.extra-hearts-removed", 2);
        fallenHeartsModifier = new AttributeModifier(fallenHeartsKey, -heartsRemoved * 2,
                AttributeModifier.Operation.ADD_NUMBER);
        attr.addModifier(fallenHeartsModifier);
    }

    private void removeFallenHeartsPenalty(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        var existing = attr.getModifier(fallenHeartsKey);
        if (existing != null) {
            attr.removeModifier(existing);
        }
    }

    // Righteous/Saint occasionally negate fall damage ("angel wings")
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        AlignmentTier tier = alignment.getTier(player);
        if (!tier.isGood()) return;

        double chance = tier == AlignmentTier.SAINT ? 0.5
                : plugin.getConfig().getDouble("effects.righteous.fall-damage-negate-chance", 0.25);
        if (random.nextDouble() < chance) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.02);
        }
    }
}
