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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Luminescence Spear's two passives:
 *  1. A thrown trident that lands on an entity grants Blindness + Glowing.
 *  2. Landing after a fall of 3+ blocks triggers a mace-style impact blast
 *     scaled to how far you fell (non-block-breaking, so it can't grief).
 */
public class LuminescenceSpearPassives implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Double> lastFallDistance = new HashMap<>();

    public LuminescenceSpearPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (weaponManager.getWeaponType(held) != WeaponType.SWORD_OF_DAVID) {
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

    private void triggerFallExplosion(Player player, double fallDistance) {
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

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (weaponManager.getWeaponType(trident.getItem()) != WeaponType.SWORD_OF_DAVID) return;
        if (event.getHitEntity() instanceof LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        }
    }
}
