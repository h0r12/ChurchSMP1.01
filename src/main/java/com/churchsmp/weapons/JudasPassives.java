package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JudasPassives implements Listener {

    private static final PotionEffectType[] JUDAS_GIFT_POOL = {
            PotionEffectType.NAUSEA, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.BLINDNESS
    };

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Long> biteCooldown = new HashMap<>();
    private final Map<UUID, Long> lastGiftCheck = new HashMap<>();

    public JudasPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    private boolean isHoldingJudas(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.BLADE_OF_JUDAS;
    }

    /** Passive 1, Bloodfeast: while holding the weapon, you cannot regenerate at all — natural or potion-based. */
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHoldingJudas(player)) return;
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.MAGIC
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /**
     * Passive 2, Unfree, is time-based rather than event-based, so it runs
     * its own repeating scan rather than hooking into an attack/damage event.
     */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tickUnfree(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // check every second
    }

    /**
     * Passive 2, Unfree: while holding the weapon, there's a periodic
     * small chance of receiving a random debuff ("Judas's gift") — checked
     * roughly every 10s per player, ~20% chance each check. Neither the
     * interval nor the odds were pinned down in the spec, so these are a
     * reasonable default rather than an exact number.
     */
    public void tickUnfree(Player player) {
        if (!isHoldingJudas(player)) return;
        long now = System.currentTimeMillis();
        long last = lastGiftCheck.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 10_000L) return;
        lastGiftCheck.put(player.getUniqueId(), now);
        if (Math.random() >= 0.2) return;

        PotionEffectType gift = JUDAS_GIFT_POOL[(int) (Math.random() * JUDAS_GIFT_POOL.length)];
        player.addPotionEffect(new PotionEffect(gift, 100, 0)); // 5s
        player.sendActionBar(Component.text("Judas leaves you a gift...", NamedTextColor.DARK_GRAY));
    }

    /**
     * Every Judas attack pops the vanilla "hit heart" particle
     * (damage_indicator) plus a crit sparkle, purely cosmetic.
     * Passive 3, Bite, is layered on top: a 5% chance per hit to inflict
     * Wither + Nausea + Blindness, gated by a 90s cooldown after it fires.
     */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isHoldingJudas(player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                target.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.1);

        long now = System.currentTimeMillis();
        long last = biteCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 90_000L) return;
        if (Math.random() >= 0.05) return; // 5% chance to hit

        biteCooldown.put(player.getUniqueId(), now);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        player.sendActionBar(Component.text("Bite sinks in.", NamedTextColor.DARK_RED));
    }

    /**
     * True stun: repeatedly pins the entity to the spot it was in when the
     * stun began (zeroing velocity and cancelling fall distance each tick)
     * rather than relying on a Slowness amplifier, which can still be
     * shoved around by knockback or drift while airborne.
     */
    static void stun(LivingEntity entity, long durationTicks, ChurchSMP plugin) {
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

    /**
     * Hemorrhaged Mold's wither skull never behaves like a normal one —
     * this intercepts the impact entirely. A direct hit on an entity stuns
     * them for 1.5s and rewards the shooter with Strength III for 5s; a
     * miss (hits a block instead) strikes lightning and applies Slowness
     * II to anything caught in a small 3x3 area around the impact.
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof WitherSkull skull)) return;
        String shooterId = skull.getPersistentDataContainer()
                .get(plugin.getWeaponAbilities().getJudasSkullKey(), PersistentDataType.STRING);
        if (shooterId == null) return; // not one of ours — leave real wither skulls alone

        event.setCancelled(true); // no vanilla explosion/fire/wither-effect

        Player shooter = plugin.getServer().getPlayer(UUID.fromString(shooterId));
        Location impact = skull.getLocation();

        impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
        impact.getWorld().spawnParticle(Particle.SQUID_INK, impact, 60, 1.5, 1, 1.5, 0.06);
        impact.getWorld().playSound(impact, Sound.ENTITY_WITHER_HURT, 1f, 0.6f);

        if (event.getHitEntity() instanceof LivingEntity target && shooter != null) {
            stun(target, 30L, plugin); // 1.5s, can't move even in the air
            shooter.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 2)); // 5s, Strength III
        } else {
            double radius = 1.5; // approximates "3x3"
            impact.getWorld().strikeLightningEffect(impact);
            for (Entity e : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
                if (e instanceof LivingEntity le) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1)); // 4s, Slowness II
                }
            }
        }

        skull.remove();
    }
}
