package com.churchsmp.relics;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Only Wrath is implemented so far — the other 6 relics are placeholders in
 * RelicType until they're built one at a time, same as the legendary weapons.
 */
public class RelicAbilities implements Listener {

    private final ChurchSMP plugin;
    private final RelicManager relicManager;

    private final Set<UUID> furyActive = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<UUID, Integer> revengeStacks = new HashMap<>();

    public RelicAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.relicManager = plugin.getRelicManager();
    }

    private boolean hasWrath(Player player) {
        return relicManager.getAssignedRelic(player) == RelicType.WRATH;
    }

    // ============================================================
    // Passives
    // ============================================================

    /** Passive 1, Bloodfeast: +0.75 attack damage per missing heart. */
    @EventHandler
    public void onBloodfeast(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hasWrath(player)) return;
        double missingHearts = (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() - player.getHealth()) / 2.0;
        if (missingHearts > 0) {
            event.setDamage(event.getDamage() + missingHearts * 0.75);
        }
    }

    /** Passive 2, Bloodlust: kills or ability triggers grant Speed III for 10s. */
    @EventHandler
    public void onBloodlustKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && hasWrath(killer)) {
            grantBloodlust(killer);
        }
    }

    private void grantBloodlust(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2)); // 10s, Speed III
    }

    /** Passive 3, BloodPrice: below -50 alignment, +15% melee damage dealt but +10% damage taken. */
    @EventHandler
    public void onBloodPriceDealt(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hasWrath(player)) return;
        if (plugin.getAlignmentManager().getScore(player) < -50) {
            event.setDamage(event.getDamage() * 1.15);
        }
    }

    @EventHandler
    public void onBloodPriceTaken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !hasWrath(victim)) return;
        if (plugin.getAlignmentManager().getScore(victim) < -50) {
            event.setDamage(event.getDamage() * 1.10);
        }
    }

    // ============================================================
    // Ability 1, Blood Scythe: Sneak + landing a hit, 40s cd.
    // ============================================================

    @EventHandler
    public void onBloodScytheTrigger(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hasWrath(player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        if (furyActive.contains(player.getUniqueId())) {
            applyFuryToHit(player, target, event);
            return;
        }

        if (!player.isSneaking()) return;
        if (relicManager.isOnCooldown(player, 1)) return;

        relicManager.putOnCooldown(player, 1, 40);
        UUID id = player.getUniqueId();
        furyActive.add(id);
        grantBloodlust(player);
        player.sendMessage(Component.text("Fury takes hold of your blade!", NamedTextColor.DARK_RED));
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.7f);

        new BukkitRunnable() {
            @Override
            public void run() {
                furyActive.remove(id);
                if (player.isOnline()) player.sendMessage(Component.text("Fury fades.", NamedTextColor.DARK_GRAY));
            }
        }.runTaskLater(plugin, 160L); // 8 seconds

        applyFuryToHit(player, target, event);
    }

    private void applyFuryToHit(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() * 1.5); // guaranteed "crit"
        target.setFireTicks(Math.max(target.getFireTicks(), 60)); // true fire damage via genuine fire, ignores armor's melee reduction
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.02);

        UUID id = player.getUniqueId();
        int stacks = revengeStacks.merge(id, 1, (a, b) -> Math.min(5, a + b));
        player.sendActionBar(Component.text("Revenge: " + stacks + "/5", NamedTextColor.DARK_RED));
    }

    // ============================================================
    // Ability 2, Overdrive: any hit with 2+ Revenge, 60s cd.
    // ============================================================

    @EventHandler
    public void onOverdriveTrigger(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hasWrath(player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        UUID id = player.getUniqueId();
        int stacks = revengeStacks.getOrDefault(id, 0);
        if (stacks < 2 || relicManager.isOnCooldown(player, 2)) return;

        relicManager.putOnCooldown(player, 2, 60);
        revengeStacks.put(id, stacks - 2);
        grantBloodlust(player);

        event.setDamage(event.getDamage() * 1.4); // the "enchanted" hit itself
        player.sendMessage(Component.text("Overdrive erupts!", NamedTextColor.DARK_RED));
        spawnFlameRing(target.getLocation(), player);
    }

    private void spawnFlameRing(Location center, Player caster) {
        double radius = 2.5; // approximates "5x5"
        center.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);

        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.equals(caster)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0));
                stun(le, 40L); // 2s
            }
        }

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 40) { // 2 seconds
                    cancel();
                    return;
                }
                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI / 24) * i;
                    Location p = center.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
                    center.getWorld().spawnParticle(Particle.FLAME, p, 2, 0.05, 0.1, 0.05, 0.01);
                }
                if (tick % 20 == 0) { // once per second
                    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (e instanceof LivingEntity le && !le.equals(caster)) {
                            double registerAmount = Math.min(0.5, 1.0);
                            le.damage(registerAmount, caster);
                            le.setHealth(Math.max(0, le.getHealth() - (1.0 - registerAmount)));
                        }
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** A true stun (pins the entity in place, ignoring knockback/gravity) — duplicated locally to avoid a cross-package dependency on the weapons module. */
    private void stun(LivingEntity entity, long durationTicks) {
        Location anchor = entity.getLocation();
        new BukkitRunnable() {
            long remaining = durationTicks;

            @Override
            public void run() {
                if (!entity.isValid() || remaining <= 0) {
                    cancel();
                    return;
                }
                Location current = entity.getLocation();
                entity.teleport(new Location(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(),
                        current.getYaw(), current.getPitch()));
                entity.setVelocity(new Vector(0, 0, 0));
                entity.setFallDistance(0);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
