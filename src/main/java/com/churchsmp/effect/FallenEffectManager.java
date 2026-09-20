package com.churchsmp.effect;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FallenEffectManager {

    private final ChurchSMP plugin;
    // UUID -> Expiration Timestamp in millis
    private final Map<UUID, Long> activeFallen = new ConcurrentHashMap<>();

    public FallenEffectManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the Fallen debuff to an entity for 5 seconds.
     * Effect:
     * - Disables ChurchSMP Gems and Legends
     * - Armor weakened by 60% (damage taken increased by 60%)
     * - Enchants halved
     * - Cannot get Absorption or Regeneration
     * - Drains all hunger
     * - Lasts for 5s
     * - Thick orbiting soul sand & dark particles
     */
    public void applyFallen(LivingEntity target) {
        long expireAt = System.currentTimeMillis() + 5000L;
        activeFallen.put(target.getUniqueId(), expireAt);

        // Strip absorption and regeneration
        target.removePotionEffect(PotionEffectType.ABSORPTION);
        target.removePotionEffect(PotionEffectType.REGENERATION);

        if (target instanceof Player player) {
            player.setFoodLevel(0);
            player.sendMessage(Component.text("â˜  YOU HAVE BEEN STRUCK WITH FALLEN!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
            player.sendMessage(Component.text("Gems & Legends disabled, armor weakened by 60%, hunger drained!", NamedTextColor.RED));
        }

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.6f);

        // Orbiting particles task for 5 seconds
        new BukkitRunnable() {
            int ticks = 100; // 5 seconds
            double angle = 0;

            @Override
            public void run() {
                if (!target.isValid() || ticks <= 0 || !isFallen(target)) {
                    activeFallen.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                Location loc = target.getLocation().add(0, 1.0, 0);
                angle += Math.PI / 8;

                // Thick orbiting soul sand and dark particles
                for (int i = 0; i < 3; i++) {
                    double currentAngle = angle + (i * (2 * Math.PI / 3));
                    double x = Math.cos(currentAngle) * 1.2;
                    double z = Math.sin(currentAngle) * 1.2;

                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, 0, z), 2, 0.05, 0.05, 0.05, 0.01);
                    loc.getWorld().spawnParticle(Particle.SQUID_INK, loc.clone().add(x, 0, z), 2, 0.05, 0.05, 0.05, 0.01);
                    loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(x, 0, z), 3, 0.1, 0.1, 0.1, 0.05);
                }

                ticks -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public boolean isFallen(LivingEntity entity) {
        if (entity == null) return false;
        Long expire = activeFallen.get(entity.getUniqueId());
        if (expire == null) return false;
        if (System.currentTimeMillis() > expire) {
            activeFallen.remove(entity.getUniqueId());
            return false;
        }
        return true;
    }
}
