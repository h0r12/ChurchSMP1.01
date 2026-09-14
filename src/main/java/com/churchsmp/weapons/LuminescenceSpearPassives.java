package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Luminescence Spear's three passives:
 *  1. Bolt — landing after a 3+ block fall triggers a mace-style impact
 *     blast scaled to how far you fell (non-block-breaking, can't grief).
 *     60s cooldown so it can't be spammed off small ledges.
 *  2. LightStealing — a thrown hit that lands on an entity inflicts
 *     Darkness for 10s. Also 60s cooldown.
 *  3. BurningBones — melee hits normally deal the trident's own (lower)
 *     damage, but there's a separate cooldown gate that, once ready,
 *     upgrades your next hit to sword-tier damage before going back on
 *     cooldown. The exact multiplier/cooldown length weren't pinned down
 *     in the spec, so this uses a 1.4x bump on a 6s gate — flag it if you
 *     had different numbers in mind.
 */
public class LuminescenceSpearPassives implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Double> lastFallDistance = new HashMap<>();
    private final Map<UUID, Long> boltCooldown = new HashMap<>();
    private final Map<UUID, Long> lightStealingCooldown = new HashMap<>();
    private final Map<UUID, Long> burningBonesReadyAt = new HashMap<>();

    public LuminescenceSpearPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    private boolean isHoldingSpear(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.SWORD_OF_DAVID;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingSpear(player)) {
                        lastFallDistance.remove(player.getUniqueId());
                        continue;
                    }
                    if (player.isOnGround()) {
                        Double stored = lastFallDistance.remove(player.getUniqueId());
                        if (stored != null && stored >= 3) {
                            triggerFallExplosion(player, stored);
                        }
                    } else {
                        double fd = player.getFallDistance();
                        if (fd > 0) lastFallDistance.put(player.getUniqueId(), fd);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    /** Passive 1, Bolt. */
    private void triggerFallExplosion(Player player, double fallDistance) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - boltCooldown.getOrDefault(id, 0L) < 60_000L) return;
        boltCooldown.put(id, now);

        double power = Math.min(4.0, fallDistance / 4.0);
        Location loc = player.getLocation();

        // Visual/sound explosion + our own controlled damage — deliberately
        // NOT calling world.createExplosion() so it can never break blocks.
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 40, 1, 0.3, 1, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

        double radius = 2 + power;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.equals(player)) {
                le.damage(power * 2, player);
            }
        }
    }

    /** Passive 2, LightStealing. */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (weaponManager.getWeaponType(trident.getItem()) != WeaponType.SWORD_OF_DAVID) return;
        if (!(trident.getShooter() instanceof Player shooter)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        UUID id = shooter.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lightStealingCooldown.getOrDefault(id, 0L) < 60_000L) return;
        lightStealingCooldown.put(id, now);

        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0)); // 10s
        target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.02);
    }

    /** Passive 3, BurningBones. */
    @EventHandler
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isHoldingSpear(player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now >= burningBonesReadyAt.getOrDefault(id, 0L)) {
            event.setDamage(event.getDamage() * 1.4); // sword-tier burst
            burningBonesReadyAt.put(id, now + 6_000L);
            player.getWorld().spawnParticle(Particle.FLAME, event.getEntity().getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
        }
        // else: normal (lower) trident-tier damage goes through unmodified
    }
}
