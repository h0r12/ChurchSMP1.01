package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VoidBreaker: The Abandoned Unknowing. Handles the three passives
 * (Voidfeels, Crumble, Rifted) and the Fallen debuff's spread/tracking for
 * the Infection ability (thrown/return logic lives in WeaponAbilities.java).
 */
public class VoidBreakerMobility implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;

    // ---- Voidfeels / Rifted (shared double-jump input) ----
    private final Set<UUID> grantedFlight = new HashSet<>();
    private final Map<UUID, Long> lastVoidfeelsJump = new HashMap<>();
    private final Map<UUID, Long> riftedReadyAt = new HashMap<>();

    // ---- Crumble ----
    private final Map<UUID, Integer> sinCount = new HashMap<>();
    private final Map<UUID, Integer> sinRequirement = new HashMap<>(); // default 7, doubled once by Fractured
    private final Set<UUID> fracturedArmed = new HashSet<>();

    // ---- Fallen tracking (for Infection) ----
    private final org.bukkit.NamespacedKey fallenArmorKey;
    private final Set<UUID> fallenNow = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<UUID, Set<UUID>> infectedByThrower = new HashMap<>();
    private final Map<UUID, ItemStack> pendingReturnItem = new HashMap<>();

    public VoidBreakerMobility(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.fallenArmorKey = new org.bukkit.NamespacedKey(plugin, "fallen_armor_weaken");
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    boolean holding = isHoldingVoidBreaker(player);
                    boolean eligible = player.getGameMode() == GameMode.SURVIVAL
                            || player.getGameMode() == GameMode.ADVENTURE;

                    if (holding && eligible && !player.getAllowFlight()) {
                        player.setAllowFlight(true);
                        grantedFlight.add(player.getUniqueId());
                    } else if (!holding && grantedFlight.remove(player.getUniqueId())) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private boolean isHoldingVoidBreaker(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.VOIDBREAKER;
    }

    // ============================================================
    // Voidfeels (plain double jump, 5s cd) + Rifted (sneak variant,
    // launches toward crosshair, 30s cd, halved by each Crumble slam)
    // ============================================================

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isHoldingVoidBreaker(player)) return;
        if (!grantedFlight.contains(player.getUniqueId())) return; // wasn't our doing — leave real flight alone

        event.setCancelled(true);
        player.setFlying(false);
        UUID id = player.getUniqueId();

        if (player.isSneaking()) {
            long readyAt = riftedReadyAt.getOrDefault(id, 0L);
            if (System.currentTimeMillis() < readyAt) return; // still on cooldown, do nothing
            riftedReadyAt.put(id, System.currentTimeMillis() + 30_000L);

            Vector launch = player.getEyeLocation().getDirection().normalize().multiply(2.2);
            player.setVelocity(launch);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation(), 30, 0.3, 0.3, 0.3, 0.05);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.7f);
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastVoidfeelsJump.getOrDefault(id, 0L);
        if (now - last < 5_000L) return;
        lastVoidfeelsJump.put(id, now);

        Vector boost = player.getVelocity();
        player.setVelocity(new Vector(boost.getX(), 0.9, boost.getZ()));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
    }

    // ============================================================
    // Crumble
    // ============================================================

    /**
     * Fired from the main hit listener in WeaponAbilities (kept there so
     * all combat-hit logic funnels through one place). A "slam" is a mace
     * hit landed while the player has fallen 10+ blocks and hasn't
     * touched ground since — real vanilla mace-smash conditions.
     */
    void onPossibleSlam(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (player.getFallDistance() < 10 || player.isOnGround()) return;

        // Every slam halves whatever's left on Rifted's cooldown.
        UUID id = player.getUniqueId();
        long readyAt = riftedReadyAt.getOrDefault(id, 0L);
        long now = System.currentTimeMillis();
        if (readyAt > now) {
            riftedReadyAt.put(id, now + (readyAt - now) / 2);
        }

        int required = sinRequirement.getOrDefault(id, 7);
        int current = sinCount.getOrDefault(id, 0);
        boolean forcedByFractured = fracturedArmed.remove(id);

        if (!forcedByFractured && current < required - 1) {
            sinCount.put(id, current + 1);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Sin absorbed (" + (current + 1) + "/" + required + ")",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            return;
        }

        // The empowering hit: double damage (the direct hit already dealt
        // its normal mace damage — this adds one more full instance as an
        // Aftershock), then reset. If Fractured forced this early, the
        // "miss" case below deals full mace damage back at you instead of
        // half, and the requirement doubles for the next natural cycle.
        sinCount.put(id, 0);
        sinRequirement.put(id, forcedByFractured ? required * 2 : 7);

        double maceDamage = event.getDamage();
        Location impact = target.getLocation();
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact.add(0, 1, 0), 1);
        impact.getWorld().playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);

        java.util.List<LivingEntity> nearby = new java.util.ArrayList<>();
        for (Entity e : target.getNearbyEntities(3, 3, 3)) {
            if (e instanceof LivingEntity le && !le.equals(player) && !le.equals(target)) nearby.add(le);
        }

        if (!nearby.isEmpty()) {
            for (LivingEntity le : nearby) {
                le.damage(maceDamage, player);
            }
        } else {
            // Aftershock had nothing else to hit — it rebounds on the caster.
            double rebound = forcedByFractured ? maceDamage : maceDamage / 2.0;
            player.damage(rebound);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 1);
        }

        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Crumble unleashed!", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
    }

    /** Taking damage of any kind resets the Sin counter. */
    @EventHandler
    public void onAnyDamageResetsCrumble(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        sinCount.put(player.getUniqueId(), 0);
    }

    /** Called by Fractured (ability 1, Density mode) to arm the next slam as an empowered one. */
    void armFractured(Player player) {
        fracturedArmed.add(player.getUniqueId());
    }

    // ============================================================
    // Fallen (applied/spread by Infection)
    // ============================================================

    /** Starts tracking an infection chain for the thrower, marking the first target. */
    void startInfection(Player thrower, LivingEntity firstTarget, ItemStack thrownItem) {
        Set<UUID> infected = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        infectedByThrower.put(thrower.getUniqueId(), infected);
        pendingReturnItem.put(thrower.getUniqueId(), thrownItem);
        applyFallen(firstTarget, thrower);
    }

    /** Applies Fallen to a target and tracks it against whichever thrower's chain is still open. */
    void applyFallen(LivingEntity target, Player thrower) {
        UUID targetId = target.getUniqueId();
        Set<UUID> infected = infectedByThrower.get(thrower.getUniqueId());
        if (infected != null) infected.add(targetId);
        fallenNow.add(targetId);

        // "Enchants halved" is reworked into: knock every currently active
        // potion effect down one amplifier level (removing it if it was
        // already at level 1), since Minecraft has no non-destructive way
        // to temporarily halve an item's actual enchantment levels.
        for (PotionEffect existing : new java.util.ArrayList<>(target.getActivePotionEffects())) {
            target.removePotionEffect(existing.getType());
            if (existing.getAmplifier() > 0) {
                target.addPotionEffect(new PotionEffect(existing.getType(), existing.getDuration(), existing.getAmplifier() - 1));
            }
        }

        var armorAttr = target.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.addModifier(new AttributeModifier(fallenArmorKey, -0.6, AttributeModifier.Operation.ADD_SCALAR));
        }
        if (target instanceof Player p) {
            p.setFoodLevel(0);
        }

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_AGITATED, 0.8f, 0.6f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= 200 || !target.isValid() || target.isDead()) { // 10 seconds
                    var attr = target.getAttribute(Attribute.ARMOR);
                    if (attr != null) attr.removeModifier(fallenArmorKey);
                    fallenNow.remove(targetId);
                    checkInfectionCleared(thrower);
                    cancel();
                    return;
                }
                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 0.2, 0), 3, 0.4, 0.1, 0.4, 0.01);
                target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
                elapsed += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** No Absorption or Regeneration while Fallen. */
    @EventHandler
    public void onFallenBlocksAbsorptionRegen(EntityPotionEffectEvent event) {
        if (event.getNewEffect() == null || !(event.getEntity() instanceof LivingEntity le)) return;
        if (!fallenNow.contains(le.getUniqueId())) return;
        if (event.getNewEffect().getType().equals(org.bukkit.potion.PotionEffectType.ABSORPTION)
                || event.getNewEffect().getType().equals(org.bukkit.potion.PotionEffectType.REGENERATION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallenBlocksRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (!fallenNow.contains(le.getUniqueId())) return;
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.MAGIC
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /** Attacking a Fallen entity catches you the same infection. */
    @EventHandler
    public void onFallenSpread(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !fallenNow.contains(victim.getUniqueId())) return;
        if (!(event.getDamager() instanceof LivingEntity attacker) || fallenNow.contains(attacker.getUniqueId())) return;

        for (Map.Entry<UUID, Set<UUID>> entry : infectedByThrower.entrySet()) {
            if (entry.getValue().contains(victim.getUniqueId())) {
                Player thrower = plugin.getServer().getPlayer(entry.getKey());
                if (thrower != null) applyFallen(attacker, thrower);
                break;
            }
        }
    }

    private void checkInfectionCleared(Player thrower) {
        Set<UUID> infected = infectedByThrower.get(thrower.getUniqueId());
        if (infected == null) return;
        infected.removeIf(id -> !fallenNow.contains(id));
        if (infected.isEmpty()) {
            infectedByThrower.remove(thrower.getUniqueId());
            ItemStack returned = pendingReturnItem.remove(thrower.getUniqueId());
            if (returned != null && thrower.isOnline()) {
                var leftovers = thrower.getInventory().addItem(returned);
                leftovers.values().forEach(item -> thrower.getWorld().dropItem(thrower.getLocation(), item));
                thrower.sendActionBar(net.kyori.adventure.text.Component.text(
                        "VoidBreaker returns to your hand.", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            }
        }
    }

    @EventHandler
    public void onHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!isHoldingVoidBreaker(damager)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        event.getEntity().getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                event.getEntity().getLocation().add(0, 1, 0), 1);
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.3f);

        onPossibleSlam(damager, target, event);
    }
}
