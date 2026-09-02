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
     * Passive 2. Every hit now visibly does something to the target (a
     * brief stagger), on top of a much more frequent bigger proc — the
     * old 15%-chance/30s-cooldown combo meant the big effect landed
     * roughly once every 3+ minutes of active combat, which is
     * indistinguishable from "does nothing."
     */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isHoldingJudas(player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Guaranteed baseline: every single hit staggers the target.
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 0));

        long now = System.currentTimeMillis();
        long last = procCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 8_000L) return;
        if (Math.random() >= 0.35) return;

        procCooldown.put(player.getUniqueId(), now);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
        target.getWorld().strikeLightningEffect(target.getLocation());
        player.sendActionBar(Component.text("Betrayal strikes true.", NamedTextColor.DARK_RED));
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
            target.damage(2, shooter);
            shooter.setHealth(Math.min(shooter.getHealth() + 2, shooter.getAttribute(
                    org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 250));
        }

        impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
        impact.getWorld().spawnParticle(Particle.LARGE_SMOKE, impact, 80, 2, 1, 2, 0.05);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);

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
