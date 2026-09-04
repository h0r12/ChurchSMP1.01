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
import org.bukkit.event.entity.EntityPotionEffectEvent;
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

    private static final PotionEffectType[] BLOCKED_EFFECTS = {
            PotionEffectType.WITHER, PotionEffectType.POISON, PotionEffectType.DARKNESS,
            PotionEffectType.BLINDNESS, PotionEffectType.REGENERATION
    };

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Long> procCooldown = new HashMap<>();

    public JudasPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    private boolean isHoldingJudas(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.BLADE_OF_JUDAS;
    }

    /** Passive 1: immune to Wither, Poison, Darkness, Blindness — and Regeneration can't land either. */
    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getNewEffect() == null) return;
        if (!isHoldingJudas(player)) return;

        for (PotionEffectType blocked : BLOCKED_EFFECTS) {
            if (event.getNewEffect().getType().equals(blocked)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Every Judas attack pops the vanilla "hit heart" particle
     * (damage_indicator) plus a crit sparkle, purely cosmetic.
     * Passive 2 is layered on top: a small chance per hit to afflict
     * Wither + Darkness for 5s and strike a (visual) lightning bolt,
     * gated by a 30s cooldown so it can't proc back-to-back.
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
        long last = procCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 30_000L) return;
        if (Math.random() >= 0.15) return; // small chance

        procCooldown.put(player.getUniqueId(), now);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
        target.getWorld().strikeLightningEffect(target.getLocation());
        player.sendActionBar(Component.text("Betrayal strikes true.", NamedTextColor.DARK_RED));
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
     * this intercepts the impact entirely and replaces it with: steal a
     * heart + stun on a direct entity hit, then a dark blast wherever it
     * lands (blind + visual lightning on everything within ~6 blocks).
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

        if (event.getHitEntity() instanceof LivingEntity target && shooter != null) {
            target.damage(2, shooter); // steal one heart
            shooter.setHealth(Math.min(shooter.getHealth() + 2, shooter.getAttribute(
                    org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            stun(target, 50L, plugin); // 2.5s, can't move even in the air
        }

        impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
        impact.getWorld().spawnParticle(Particle.SQUID_INK, impact, 120, 2.5, 1.5, 2.5, 0.08);
        impact.getWorld().spawnParticle(Particle.LARGE_SMOKE, impact, 80, 2, 1, 2, 0.05);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        impact.getWorld().playSound(impact, Sound.ENTITY_WITHER_HURT, 1f, 0.6f);

        double radius = 3; // approximates "6x6"
        for (Entity e : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                le.getWorld().strikeLightningEffect(le.getLocation());
            }
        }

        skull.remove();
    }
}
