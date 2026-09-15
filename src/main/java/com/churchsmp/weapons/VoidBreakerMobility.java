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
    private final Map<UUID, Integer> sinRemaining = new HashMap<>(); // counts down; starts at 7 + fractureStacksUsed
    private final Map<UUID, Integer> fractureStacksUsed = new HashMap<>(); // permanent +1 per Fractured use

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
            int globalTick = 0;

            @Override
            public void run() {
                globalTick += 10;
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

                    if (holding && isCrumbleReady(player)) {
                        showCrumbleReadyIndicator(player, globalTick);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    /** True once the next qualifying slam will be the empowered hit. */
    private boolean isCrumbleReady(Player player) {
        UUID id = player.getUniqueId();
        int total = 7 + fractureStacksUsed.getOrDefault(id, 0);
        return sinRemaining.getOrDefault(id, total) <= 1;
    }

    /** A small ball orbits the player's feet with a short trail while Crumble is charged and ready. */
    private void showCrumbleReadyIndicator(Player player, int tick) {
        double angle = tick * 0.15;
        double radius = 0.6;
        Location base = player.getLocation();
        for (int i = 0; i < 5; i++) { // a short trailing tail behind the ball
            double trailAngle = angle - i * 0.25;
            Location p = base.clone().add(radius * Math.cos(trailAngle), 0.1, radius * Math.sin(trailAngle));
            player.getWorld().spawnParticle(Particle.DUST, p, 2, 0.02, 0.02, 0.02, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 200), i == 0 ? 1.3f : 0.8f));
        }
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
     *
     * Counting model: sinRemaining starts at 7 plus 1 for every time
     * Fractured has been used (persists — Fractured is a permanent
     * investment, not a one-off), and just counts down by 1 per
     * qualifying slam rather than showing as a X/Y fraction. Hitting 0
     * triggers the empowered hit.
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

        int fractureStacks = fractureStacksUsed.getOrDefault(id, 0);
        int total = 7 + fractureStacks;
        int remaining = sinRemaining.getOrDefault(id, total);

        if (remaining > 1) {
            sinRemaining.put(id, remaining - 1);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    String.valueOf(remaining - 1), net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            return;
        }

        // The empowering hit: double damage (the direct hit already dealt
        // its normal mace damage — this adds one more full instance as an
        // Aftershock against nearby opponents only). Crumble never hurts
        // the caster — if there's nobody else around, the Aftershock
        // simply has nothing to hit. The hit itself is deferred until a
        // particle animation plays out: expand outward, close back in,
        // then the actual explosion/damage.
        sinRemaining.put(id, total);
        double maceDamage = event.getDamage();
        playCrumbleAftershockAnimation(player, target, maceDamage);
    }

    private void playCrumbleAftershockAnimation(Player player, LivingEntity target, double maceDamage) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                Location center = target.getLocation().add(0, 1, 0);

                if (tick <= 10) {
                    // Phase 1: expand outward
                    double radius = (tick / 10.0) * 3.0;
                    ringParticle(center, radius);
                } else if (tick <= 20) {
                    // Phase 2: close back in
                    double radius = 3.0 - ((tick - 10) / 10.0) * 3.0;
                    ringParticle(center, radius);
                } else {
                    // Phase 3: the actual explosion + damage
                    Location impact = target.getLocation();
                    impact.getWorld().spawnParticle(Particle.EXPLOSION, impact.add(0, 1, 0), 1);
                    impact.getWorld().playSound(impact, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);

                    // Rock/debris crumbling and flying up off the target, matching the name.
                    for (int i = 0; i < 40; i++) {
                        double x = (Math.random() - 0.5) * 1.5;
                        double z = (Math.random() - 0.5) * 1.5;
                        Location debrisSpot = target.getLocation().add(x, Math.random() * 0.5, z);
                        target.getWorld().spawnParticle(Particle.BLOCK, debrisSpot, 1, 0.1, 0.3, 0.1, 0.15,
                                org.bukkit.Material.COBBLESTONE.createBlockData());
                    }

                    // A ring of alternating black/white/gray pips orbits whoever got
                    // hit for 3s, visualizing the Sin that was just cashed in.
                    orbitPipRing(target);

                    for (Entity e : target.getNearbyEntities(3, 3, 3)) {
                        if (e instanceof LivingEntity le && !le.equals(player) && !le.equals(target)) {
                            le.damage(maceDamage, player);
                        }
                    }

                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "Crumble unleashed!", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void ringParticle(Location center, double radius) {
        for (int i = 0; i < 28; i++) {
            double angle = (2 * Math.PI / 28) * i;
            Location p = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            center.getWorld().spawnParticle(Particle.CRIT, p, 2, 0.03, 0.03, 0.03, 0);
        }
    }

    private void orbitPipRing(LivingEntity target) {
        org.bukkit.Color[] pipColors = {
                org.bukkit.Color.BLACK, org.bukkit.Color.WHITE, org.bukkit.Color.fromRGB(140, 140, 140)
        };
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 60 || !target.isValid() || target.isDead()) { // 3 seconds
                    cancel();
                    return;
                }
                int pips = 7;
                for (int i = 0; i < pips; i++) {
                    double angle = tick * 0.25 + (2 * Math.PI / pips) * i;
                    Location point = target.getLocation().add(
                            0.9 * Math.cos(angle), 0.15, 0.9 * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(pipColors[i % pipColors.length], 1.3f);
                    target.getWorld().spawnParticle(Particle.DUST, point, 3, 0.03, 0.03, 0.03, 0, dust);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Taking damage of any kind resets the Sin counter back to its current full total. */
    @EventHandler
    public void onAnyDamageResetsCrumble(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        sinRemaining.put(id, 7 + fractureStacksUsed.getOrDefault(id, 0));
    }

    /** Called by Fractured (ability 1, Density mode): permanently adds +1 to Crumble's total requirement. */
    void armFractured(Player player) {
        UUID id = player.getUniqueId();
        int stacks = fractureStacksUsed.merge(id, 1, Integer::sum);
        sinRemaining.put(id, 7 + stacks);
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
