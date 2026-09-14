package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Actual gameplay effects triggered by each weapon ability.
 * execute() returns how many seconds the trigger slot that was just used
 * should go on cooldown for — most weapons just echo the configured
 * default, but VoidBreaker needs a different cooldown per named ability
 * even though both share the same "ability 1" trigger.
 */
public class WeaponAbilities implements org.bukkit.event.Listener {

    private final ChurchSMP plugin;
    private final AlignmentManager alignmentManager;
    private final org.bukkit.NamespacedKey judasSkullKey;
    private final org.bukkit.NamespacedKey thirtyPiecesModifierKey;

    private static final Set<Material> GOLDEN_FOODS = EnumSet.of(
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_CARROT);

    public WeaponAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentManager = plugin.getAlignmentManager();
        this.judasSkullKey = new org.bukkit.NamespacedKey(plugin, "judas_skull");
        this.thirtyPiecesModifierKey = new org.bukkit.NamespacedKey(plugin, "thirty_pieces_sacrifice");
        this.gloomArmorKey = new org.bukkit.NamespacedKey(plugin, "gloom_depressed");
        this.sunEclipseTridentKey = new org.bukkit.NamespacedKey(plugin, "sun_eclipse_trident");
    }

    public org.bukkit.NamespacedKey getJudasSkullKey() {
        return judasSkullKey;
    }

    /** Returns the cooldown (in seconds) to apply to the trigger slot just used. */
    public int execute(WeaponType type, int ability, Player player) {
        switch (type) {
            case BLADE_OF_ARCHANGEL:
                if (ability == 1) {
                    acceleratedNova(player);
                    return 60;
                } else {
                    altarsPin(player);
                    return 105;
                }
            case SWORD_OF_DAVID:
                if (ability == 1) {
                    blink(player);
                    return 0; // charge-based; handled manually like Hemorrhaged Mold
                } else {
                    sunEclipse(player);
                    return 130;
                }
            case STAFF_OF_MOSES:
                if (ability == 1) {
                    frostEdge(player);
                    return 0; // cooldown is applied manually once the 20s active window ends (or ends early)
                } else {
                    entangleFreeze(player);
                    return 45;
                }
            case SCYTHE_OF_CAIN:
                if (ability == 1) {
                    hollowedOut(player);
                    return 60;
                } else {
                    darkParticle(player);
                    return 0; // manual — cooldown starts once the 25s active window ends
                }
            case SORROWESS:
                if (ability == 1) {
                    griefShards(player);
                } else {
                    gloom(player);
                    return 0; // cooldown is applied manually once the 10s window ends
                }
                break;
            case BLADE_OF_JUDAS:
                if (ability == 1) {
                    hemorrhagedMold(player);
                    return 0; // charge-based; cooldown is handled manually per shot/reload
                } else {
                    thirtyPiecesOfSilver(player);
                    return 50;
                }
            case VOIDBREAKER:
                if (ability == 2) {
                    spacedBound(player);
                    return 3;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.containsEnchantment(Enchantment.WIND_BURST)) {
                    fractured(player);
                    return 75;
                } else {
                    infection(player);
                    return 130;
                }
        }
        return plugin.getWeaponManager().getConfiguredCooldown(ability);
    }

    // ---------------- Excalibur (formerly Blade of the Archangel) ----------------

    /**
     * Ability 1, Accelerated Nova. A 10s charge (boss bar + swirling
     * particles, Resistance II throughout), then releases a directional
     * line attack — like the Warden's actual Sonic Boom, not a radial
     * burst — dealing 2.5 hearts of true damage to anything caught in a
     * narrow tube extending 15 blocks in front of you.
     */
    private void acceleratedNova(Player player) {
        int chargeTicks = 200; // 10 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, chargeTicks + 5, 1));
        org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                player.getName() + " is Accelerating the nova...",
                WeaponType.BLADE_OF_ARCHANGEL.getBarColor(), org.bukkit.boss.BarStyle.SOLID);
        bar.setProgress(0);
        for (Entity e : player.getNearbyEntities(15, 15, 15)) {
            if (e instanceof Player nearby) bar.addPlayer(nearby);
        }
        bar.addPlayer(player);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                try {
                    tick++;
                    bar.setProgress(Math.min(1.0, (double) tick / chargeTicks));
                    double angle = tick * 0.6;
                    Vector ring = new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(1.1);
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(ring).add(0, 1, 0), 2, 0, 0, 0, 0);
                    if (tick >= chargeTicks) {
                        bar.removeAll();
                        releaseNova(player);
                        cancel();
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Accelerated Nova charge error: " + ex);
                    ex.printStackTrace();
                    bar.removeAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void releaseNova(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = 15;
        double tubeRadius = 1.2;

        // A braided trail of gray/gold/white dust rides along the line, echoing
        // the earlier ring-burst's look but stretched into a beam shape.
        Particle.DustOptions grayDust = new Particle.DustOptions(Color.fromRGB(140, 140, 140), 1f);
        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 200, 60), 1f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1f);
        Particle.DustOptions[] strandColors = {grayDust, goldDust, whiteDust};

        for (double d = 0; d < range; d += 0.4) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 0);
            for (int strand = 0; strand < 3; strand++) {
                double angle = d * 1.5 + (2 * Math.PI / 3) * strand;
                Vector offset = new Vector(0.3 * Math.cos(angle), 0.3 * Math.sin(angle), 0);
                point.getWorld().spawnParticle(Particle.DUST, point.clone().add(offset), 1, 0, 0, 0, 0, strandColors[strand]);
            }
        }

        double trueDamage = 5; // 2.5 hearts, ignoring armor entirely
        Set<UUID> hit = new java.util.HashSet<>();
        for (double d = 0; d < range; d += 0.5) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            for (Entity e : point.getWorld().getNearbyEntities(point, tubeRadius, tubeRadius, tubeRadius)) {
                if (!(e instanceof LivingEntity target) || target.equals(player) || !hit.add(target.getUniqueId())) continue;
                double registerAmount = Math.min(0.5, trueDamage);
                target.damage(registerAmount, player);
                target.setHealth(Math.max(0, target.getHealth() - (trueDamage - registerAmount)));
                target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1, Color.WHITE);
            }
        }
        msg(player, "The light breaks out from the handle");
    }

    /**
     * Ability 2, Altar's Pin. A vortex particle forms at your feet for 5s,
     * dragging nearby entities toward you, then a giant sword crashes down
     * and smashes: 7.5 hearts through armor to everything caught nearby.
     */
    private void altarsPin(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 100) { // 5 seconds
                    if (player.isOnline()) smashSword(player);
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                double progress = tick / 100.0; // 0 -> 1 across the 5s window
                double radius = 0.8 + progress * 1.7; // grows outward over time, matching the sketch's 3 stages

                // Color shifts yellow -> orange -> red as the ring grows.
                int r = (int) (255 - progress * 35);
                int g = (int) (220 - progress * 180);
                int b = (int) (80 - progress * 60);
                Particle.DustOptions ringColor = new Particle.DustOptions(Color.fromRGB(
                        Math.max(0, r), Math.max(0, g), Math.max(0, b)), 1.2f);

                for (int i = 0; i < 16; i++) {
                    double angle = (tick * 0.3) + (2 * Math.PI / 16) * i;
                    Vector offset = new Vector(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                    feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(offset), 1, 0, 0, 0, 0, ringColor);
                }

                // A few particles kick outward and down off the ring's edge every so often, like the sketch's arrows.
                if (tick % 8 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        Vector edge = new Vector(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                        Vector kicked = edge.clone().add(edge.clone().normalize().multiply(0.6)).setY(edge.getY() - 0.4);
                        feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(kicked), 1, 0, 0, 0, 0, ringColor);
                    }
                }
                for (Entity e : player.getNearbyEntities(6, 4, 6)) {
                    if (e instanceof LivingEntity target && !target.equals(player)) {
                        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
                        pull.setY(Math.min(0.15, pull.getY()));
                        if (pull.length() > 0.3) pull.normalize().multiply(0.25);
                        target.setVelocity(target.getVelocity().add(pull));
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        msg(player, "The Excalibur is rising.");
    }

    /**
     * The multi-stage smash: 3 true damage + launch upward, debris
     * particles (visual only — no real blocks are touched, to avoid grief
     * risk on an effect this described-by-feel), then ~0.75s later a hard
     * slam back down, landing for 1 more true damage and a 3s stun.
     * 10x10 area (radius 5).
     */
    private void smashSword(Player player) {
        Location center = player.getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.3);
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 3, 0), 60, 0.3, 1.5, 0.3, 0.05);
        center.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.6f);
        center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);

        double radius = 5; // 10x10
        double firstHit = 6; // 3 hearts, ignoring armor entirely

        // "Blocks lunge upward... more power near Excalibur" — a visual-only
        // debris effect (no real block manipulation, to avoid grief/dupe
        // risk on an effect this loosely described): nearby ground blocks
        // get crack particles flung upward, stronger closer to the center.
        for (int i = 0; i < 60; i++) {
            double dx = (Math.random() - 0.5) * radius * 2;
            double dz = (Math.random() - 0.5) * radius * 2;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            double strength = 1.0 - (dist / radius); // closer = stronger
            Location groundSpot = center.clone().add(dx, 0, dz);
            groundSpot.setY(groundSpot.getWorld().getHighestBlockYAt(groundSpot) + 0.2);
            var blockData = groundSpot.clone().subtract(0, 1, 0).getBlock().getBlockData();
            center.getWorld().spawnParticle(Particle.BLOCK, groundSpot, (int) (3 + strength * 6),
                    0.2, 0.1, 0.2, 0.15 + strength * 0.35, blockData);
        }

        java.util.List<LivingEntity> caught = new java.util.ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || target.equals(player)) continue;
            double registerAmount = Math.min(0.5, firstHit);
            target.damage(registerAmount, player);
            target.setHealth(Math.max(0, target.getHealth() - (firstHit - registerAmount)));
            target.setVelocity(target.getVelocity().setY(0.9)); // launched into the air
            caught.add(target);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity target : caught) {
                    if (!target.isValid() || target.isDead()) continue;
                    target.setVelocity(target.getVelocity().setY(-1.4)); // slammed back down
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        double secondHit = 2; // 1 heart, ignoring armor entirely
                        for (LivingEntity target : caught) {
                            if (!target.isValid() || target.isDead()) continue;
                            double registerAmount = Math.min(0.5, secondHit);
                            target.damage(registerAmount, player);
                            target.setHealth(Math.max(0, target.getHealth() - (secondHit - registerAmount)));
                            JudasPassives.stun(target, 60L, plugin); // 3s
                            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 15, 0.3, 0.1, 0.3, 0.1);
                        }
                    }
                }.runTaskLater(plugin, 10L); // approximates landing after the slam-down velocity kicks in
            }
        }.runTaskLater(plugin, 15L); // ~0.75s of airtime before the slam-down
    }

    // ---------------- GOOD ----------------

    private void smiteBeam(Player player) {
        LivingEntity target = getTargetedEntity(player, 20);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }
        double bonus = (target instanceof Player p && alignmentManager.getTier(p).isEvil()) || isUndead(target) ? 6 : 3;
        target.damage(bonus, player);
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1.6f);
        msg(player, "Smite Beam strikes " + target.getName() + "!");
    }

    private final Map<UUID, Integer> blinkCharges = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlinkTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> cannotThrowProjectiles = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<UUID> sunEclipseArmed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final org.bukkit.NamespacedKey sunEclipseTridentKey;

    /**
     * Ability 1, Blink. 3 charges (like Hemorrhaged Mold): each activation
     * dashes you 6 blocks forward, stopping early if it hits a wall,
     * leaving a wavy red/white lightning trail behind. Anything caught in
     * the dash path takes 1 heart and can't throw projectiles for 5s. No
     * per-dash or recharge timing was specified, so this uses a 2s gate
     * between individual dashes and a 20s full recharge once all 3 are
     * spent — flag it if you had different numbers in mind.
     */
    private void blink(Player player) {
        UUID id = player.getUniqueId();
        int charges = blinkCharges.getOrDefault(id, 3);
        if (charges <= 0) {
            msg(player, "No Blink charges left.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBlinkTime.getOrDefault(id, 0L) < 2_000L) {
            msg(player, "Blink is still resetting.");
            return;
        }
        lastBlinkTime.put(id, now);
        blinkCharges.put(id, charges - 1);

        Location start = player.getLocation();
        Vector dir = start.getDirection().setY(0).normalize();
        Location dest = start.clone();

        for (int step = 1; step <= 6; step++) {
            Location next = start.clone().add(dir.clone().multiply(step));
            if (next.getBlock().getType().isSolid()) break;
            dest = next;
        }
        dest.setDirection(start.getDirection());
        player.teleport(dest);

        // Wavy red/white lightning trail along the path traveled.
        double distance = start.toVector().distance(dest.toVector());
        for (double d = 0; d < distance; d += 0.3) {
            Location point = start.clone().add(dir.clone().multiply(d));
            point.add(0, Math.sin(d * 3) * 0.3, 0); // the "wavy" wobble from the sketch
            Particle.DustOptions color = (((int) (d * 3)) % 2 == 0)
                    ? new Particle.DustOptions(Color.RED, 1f)
                    : new Particle.DustOptions(Color.WHITE, 1f);
            player.getWorld().spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0, color);

            for (Entity e : point.getWorld().getNearbyEntities(point, 1, 1, 1)) {
                if (e instanceof LivingEntity le && !le.equals(player) && !cannotThrowProjectiles.contains(le.getUniqueId())) {
                    le.damage(2, player); // 1 heart
                    cannotThrowProjectiles.add(le.getUniqueId());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            cannotThrowProjectiles.remove(le.getUniqueId());
                        }
                    }.runTaskLater(plugin, 100L); // 5s
                }
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
        showBlinkCharges(player, blinkCharges.get(id));

        if (blinkCharges.get(id) == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    blinkCharges.put(id, 3);
                    if (!player.isOnline()) return;
                    msg(player, "Blink fully recharges.");
                    animateBlinkRecharge(player);
                }
            }.runTaskLater(plugin, 20 * 20L);
        }
    }

    /** Rings fill back in starting from the inner ring outward, per the sketch. */
    private void animateBlinkRecharge(Player player) {
        new BukkitRunnable() {
            int ring = 0;

            @Override
            public void run() {
                if (ring >= 3 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double radius = 0.5 + ring * 0.4;
                Particle.DustOptions color = new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1f);
                for (int i = 0; i < 12; i++) {
                    double angle = (2 * Math.PI / 12) * i;
                    Location p = base.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
                    base.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, color);
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    /**
     * The "3/3" ring indicator from the sketch: one ring per remaining
     * charge, innermost = the charge you just spent. When you're fully
     * depleted, a burst plays and (once recharged) the rings refill
     * starting from the inner ring outward.
     */
    private void showBlinkCharges(Player player, int chargesLeft) {
        Location base = player.getLocation();
        if (chargesLeft == 0) {
            base.getWorld().spawnParticle(Particle.FLASH, base.clone().add(0, 1, 0), 1);
        }
        for (int ring = 0; ring < 3; ring++) {
            boolean filled = ring < chargesLeft;
            if (!filled) continue; // empty slots just aren't drawn — the "fading" IS the missing ring
            double radius = 0.5 + ring * 0.4;
            Particle.DustOptions color = new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1f);
            for (int i = 0; i < 12; i++) {
                double angle = (2 * Math.PI / 12) * i;
                Location p = base.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
                base.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, color);
            }
        }
    }

    /** Stops anyone caught in a Blink dash from throwing projectiles for the debuff's duration. */
    @org.bukkit.event.EventHandler
    public void onBlinkProjectileBlock(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity shooter
                && cannotThrowProjectiles.contains(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Ability 2, SunEclipse. Arms your next trident throw to mark whatever
     * it hits. 2s later — shown as 3 concentric rings under the target
     * fading one at a time starting from the innermost — a beam strikes:
     * 4 hearts of true damage, an expanding ground ring, a 2s stun and 4s
     * Darkness on nearby entities. 130s cooldown.
     */
    private void sunEclipse(Player player) {
        sunEclipseArmed.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        msg(player, "Your next throw marks the eclipse.");
    }

    @org.bukkit.event.EventHandler
    public void onSunEclipseThrow(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player) || !sunEclipseArmed.remove(player.getUniqueId())) return;
        trident.getPersistentDataContainer().set(sunEclipseTridentKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    @org.bukkit.event.EventHandler
    public void onSunEclipseHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident trident)) return;
        String throwerId = trident.getPersistentDataContainer().get(sunEclipseTridentKey, PersistentDataType.STRING);
        if (throwerId == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        Player thrower = plugin.getServer().getPlayer(UUID.fromString(throwerId));
        if (thrower == null) return;

        new BukkitRunnable() {
            int ring = 0; // 0 = inner, 1 = mid, 2 = outer; fades inner-first

            @Override
            public void run() {
                if (ring >= 3 || target.isDead() || !target.isValid()) {
                    if (ring >= 3) triggerEclipseBeam(thrower, target);
                    cancel();
                    return;
                }
                double radius = 0.6 + ring * 0.6;
                Location base = target.getLocation();
                for (int i = 0; i < 20; i++) {
                    double angle = (2 * Math.PI / 20) * i;
                    Location p = base.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
                    base.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                }
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 13L); // ~2s across 3 stages (13 ticks each)
    }

    private void triggerEclipseBeam(Player thrower, LivingEntity target) {
        Location base = target.getLocation();

        // The "sonic boom" spiral pillar from the sketch.
        for (int h = 0; h < 20; h++) {
            double angle = h * 0.9;
            Location p = base.clone().add(0.7 * Math.cos(angle), h * 0.25, 0.7 * Math.sin(angle));
            base.getWorld().spawnParticle(Particle.SONIC_BOOM, p, 0);
            base.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.01);
        }
        base.getWorld().playSound(base, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.2f);

        // Expanding ground ring.
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                double radius = step * 0.6;
                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI / 24) * i;
                    base.getWorld().spawnParticle(Particle.CRIT, base.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle)), 1);
                }
                step++;
                if (step >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);

        double trueDamage = 8; // 4 hearts, ignoring armor entirely
        double registerAmount = Math.min(0.5, trueDamage);
        target.damage(registerAmount, thrower);
        target.setHealth(Math.max(0, target.getHealth() - (trueDamage - registerAmount)));

        for (Entity e : base.getWorld().getNearbyEntities(base, 3, 3, 3)) {
            if (e instanceof LivingEntity le && !le.equals(thrower)) {
                JudasPassives.stun(le, 40L, plugin); // 2s
                le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0)); // 4s
            }
        }
    }

    // ---------------- Mayim (formerly Staff of Moses) ----------------

    private final Set<UUID> frostEdgeActive = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<UUID, Integer> frostEdgeHits = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> frostEdgeTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> entangleCharging = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Ability 1, Frost Edge. Toggles a 20s active window (Glowing + boss
     * bar countdown): every attack you land during it chills the target
     * with Slowness that gets stronger with each successive hit. Getting
     * hit back yourself — even by a projectile — cuts the window short
     * immediately. Either way, the 30s cooldown only starts once the
     * window ends (naturally or early).
     */
    private void frostEdge(Player player) {
        UUID id = player.getUniqueId();
        if (frostEdgeActive.contains(id)) {
            msg(player, "Frost Edge is already active.");
            return;
        }
        frostEdgeActive.add(id);
        frostEdgeHits.put(id, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0));
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        CooldownBarDisplay.show(plugin, player, WeaponType.STAFF_OF_MOSES, "Frost Edge", 20);
        msg(player, "The edge glows.");

        org.bukkit.scheduler.BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                endFrostEdge(player, id);
            }
        }.runTaskLater(plugin, 20 * 20L);
        frostEdgeTasks.put(id, task);
    }

    private void endFrostEdge(Player player, UUID id) {
        if (!frostEdgeActive.remove(id)) return;
        frostEdgeHits.remove(id);
        org.bukkit.scheduler.BukkitTask task = frostEdgeTasks.remove(id);
        if (task != null) task.cancel();
        if (player.isOnline()) msg(player, "Your blade return to normal state.");
        plugin.getWeaponManager().putOnCooldown(player, WeaponType.STAFF_OF_MOSES, 1, 30);
        if (player.isOnline()) CooldownBarDisplay.show(plugin, player, WeaponType.STAFF_OF_MOSES, "Frost Edge (cooldown)", 30);
    }

    /** Every hit landed while Frost Edge is active chills the target harder than the last. */
    @org.bukkit.event.EventHandler
    public void onFrostEdgeHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !frostEdgeActive.contains(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        int hits = frostEdgeHits.merge(player.getUniqueId(), 1, Integer::sum);
        int amplifier = Math.min(hits - 1, 3); // escalates, capped at Slowness IV
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, amplifier));
        target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0),
                25, 0.3, 0.4, 0.3, Material.ICE.createBlockData());
    }

    /** Getting hit back at all (melee or projectile) while Frost Edge is active ends the streak early. */
    @org.bukkit.event.EventHandler
    public void onFrostEdgeRetaliation(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !frostEdgeActive.contains(victim.getUniqueId())) return;
        if (event.getDamager().equals(victim)) return;

        endFrostEdge(victim, victim.getUniqueId());
        msg(victim, "Frost shattered early.");
    }

    /**
     * Ability 2, Entangle Freeze. Charges a blue slash-shaped bolt in the
     * air in front of you for 1s, then releases it: a direct hit on an
     * entity freezes and stuns them for 1.5s, while hitting a wall instead
     * detonates a 5x5 blast of Slowness II (plus the true damage and
     * powder-snow-style freeze from before, since nothing said to drop those).
     */
    private void entangleFreeze(Player player) {
        UUID id = player.getUniqueId();
        if (entangleCharging.contains(id)) {
            msg(player, "it is already forming.");
            return;
        }
        entangleCharging.add(id);
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location origin = player.getEyeLocation().add(dir.clone().multiply(1.2));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 20) {
                    cancel();
                    entangleCharging.remove(id);
                    if (player.isOnline()) launchEntangleBolt(player, origin, dir);
                    return;
                }
                Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(50, 120, 255), 1.2f);
                for (double s = -0.6; s <= 0.6; s += 0.3) {
                    origin.getWorld().spawnParticle(Particle.DUST, origin.clone().add(0, s, 0), 1, 0, 0, 0, 0, blue);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void launchEntangleBolt(Player player, Location start, Vector dir) {
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.4f);
        new BukkitRunnable() {
            Location point = start.clone();
            int steps = 0;

            @Override
            public void run() {
                steps++;
                point.add(dir.clone().multiply(1.3));
                Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(50, 120, 255), 1f);
                point.getWorld().spawnParticle(Particle.DUST, point, 4, 0.08, 0.08, 0.08, 0, blue);

                if (!point.getBlock().getType().isAir() && !point.getBlock().isPassable()) {
                    explodeEntangleFreeze(point);
                    cancel();
                    return;
                }
                for (Entity e : point.getWorld().getNearbyEntities(point, 0.8, 0.8, 0.8)) {
                    if (e instanceof LivingEntity target && !e.equals(player)) {
                        JudasPassives.stun(target, 30L, plugin);
                        point.getWorld().spawnParticle(Particle.FLASH, point, 1);
                        point.getWorld().playSound(point, Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                        msg(player, "Mayim pins " + target.getName() + " in the place.");
                        cancel();
                        return;
                    }
                }
                if (steps > 30) cancel(); // ~20 blocks, ran out of range
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void explodeEntangleFreeze(Location center) {
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 100, 2.5, 2.5, 2.5, 0.05);
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        center.getWorld().playSound(center, Sound.ITEM_BUCKET_EMPTY_POWDER_SNOW, 1f, 1f);

        double radius = 2.5; // approximates "5x5"
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target)) continue;

            double trueDamage = 10; // 5 hearts, ignoring armor entirely
            double registerAmount = Math.min(0.5, trueDamage);
            target.damage(registerAmount);
            target.setHealth(Math.max(0, target.getHealth() - (trueDamage - registerAmount)));

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            target.setFreezeTicks(target.getMaxFreezeTicks());
        }
    }

    // ---------------- EVIL ----------------

    // ---------------- Grim (formerly Luminous Cain / Scythe of Cain) ----------------

    private final Set<UUID> hollowedOutArmed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<UUID> actionFailCursed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<UUID> darkParticleActive = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Set<UUID> darkParticleFirstHitArmed = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<UUID, Integer> darkParticleStacks = new java.util.concurrent.ConcurrentHashMap<>();
    private final org.bukkit.NamespacedKey darkParticleHeartsKey = new org.bukkit.NamespacedKey(plugin, "dark_particle_hearts");

    /**
     * Ability 1, HollowedOut. Arms your next melee hit to apply Darkness +
     * Slowness II plus a new 40% chance for the target's own attacks to
     * simply fail, for 15s. While armed and waiting for that hit, pressing
     * the drop key (Grim's normal "throw" input for Soultaking) is
     * replaced by a charge-up Sonic Boom instead: charge up to 6s, then
     * sneak to release early — the resulting stun is half of whatever
     * charge time you actually used. This reuses the drop key rather than
     * adding a new bind, since the ability description says throwing
     * itself gets replaced during this window.
     */
    private void hollowedOut(Player player) {
        hollowedOutArmed.add(player.getUniqueId());
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.7f);
        msg(player, "Your next strike hollows them out.");
    }

    @org.bukkit.event.EventHandler
    public void onHollowedOutHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hollowedOutArmed.remove(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0)); // 15s
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 1)); // 15s, Slowness II
        UUID targetId = target.getUniqueId();
        actionFailCursed.add(targetId);
        new BukkitRunnable() {
            @Override
            public void run() {
                actionFailCursed.remove(targetId);
            }
        }.runTaskLater(plugin, 300L); // 15s
        msg(player, "HollowedOut takes hold.");
    }

    /** 40% chance for a cursed target's own attacks to simply fail while HollowedOut's curse is active. */
    @org.bukkit.event.EventHandler
    public void onActionFailCheck(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker) || !actionFailCursed.contains(attacker.getUniqueId())) return;
        if (Math.random() < 0.4) {
            event.setCancelled(true);
            attacker.getWorld().spawnParticle(Particle.SMOKE, attacker.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
        }
    }

    /** While HollowedOut is armed, the drop key charges a Sonic Boom instead of Soultaking's normal throw. */
    /** Called by GrimPassives to check whether the drop key should be intercepted for the Sonic Boom charge instead of Soultaking. */
    boolean isHollowedOutArmed(Player player) {
        return hollowedOutArmed.contains(player.getUniqueId());
    }

    void tryStartHollowedOutCharge(Player player) {
        if (!hollowedOutArmed.contains(player.getUniqueId())) return;
        org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                player.getName() + " is charging a Sonic Boom...", WeaponType.SCYTHE_OF_CAIN.getBarColor(), org.bukkit.boss.BarStyle.SOLID);
        bar.addPlayer(player);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (player.isSneaking() && tick > 10 || tick >= 120 || !player.isOnline()) {
                    bar.removeAll();
                    releaseHollowedOutSonicBoom(player, tick);
                    cancel();
                    return;
                }
                bar.setProgress(Math.min(1.0, tick / 120.0));
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation(), 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void releaseHollowedOutSonicBoom(Player player, int chargeTicks) {
        hollowedOutArmed.remove(player.getUniqueId());
        double stunSeconds = (chargeTicks / 20.0) / 2.0;
        LivingEntity target = resolveForgivingTarget(player, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
        if (target != null) {
            JudasPassives.stun(target, (long) (stunSeconds * 20), plugin);
            target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation(), 0);
        }
        msg(player, "Sonic Boom releases! (" + String.format("%.1f", stunSeconds) + "s stun)");
    }

    /**
     * Ability 2, Dark Particle. A 25s active window: your very next hit
     * gets a Sharpness-X-style damage spike (+10 flat true damage, since
     * "Sharpness X" itself doesn't map to a real enchant level you can
     * apply for one hit), and every hit during the window grants +1 max
     * heart, with each individual heart decaying back off 30s after it
     * was earned. Getting hit yourself clears every currently-held bonus
     * heart immediately (but doesn't end the window). Orbiting soul-sand
     * particles play the whole time. 80s cooldown starts once the window ends.
     */
    private void darkParticle(Player player) {
        UUID id = player.getUniqueId();
        darkParticleActive.add(id);
        darkParticleFirstHitArmed.add(id);
        msg(player, "Dark Particle stirs around your blade.");
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.7f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 500 || !player.isOnline()) { // 25 seconds
                    darkParticleActive.remove(id);
                    darkParticleFirstHitArmed.remove(id);
                    if (player.isOnline()) {
                        msg(player, "Dark Particle fades.");
                        plugin.getWeaponManager().putOnCooldown(player, WeaponType.SCYTHE_OF_CAIN, 2, 80);
                        CooldownBarDisplay.show(plugin, player, WeaponType.SCYTHE_OF_CAIN, "Dark Particle (cooldown)", 80);
                    }
                    cancel();
                    return;
                }
                double angle = tick * 0.5;
                Location p = player.getLocation().add(0.8 * Math.cos(angle), 1, 0.8 * Math.sin(angle));
                player.getWorld().spawnParticle(Particle.BLOCK, p, 2, 0.05, 0.05, 0.05, 0,
                        Material.SOUL_SAND.createBlockData());
                tick += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    @org.bukkit.event.EventHandler
    public void onDarkParticleHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !darkParticleActive.contains(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        UUID id = player.getUniqueId();

        if (darkParticleFirstHitArmed.remove(id)) {
            event.setDamage(event.getDamage() + 10); // the "Sharpness X" spike
            target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1);
        }

        int stacks = darkParticleStacks.merge(id, 1, Integer::sum);
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(darkParticleHeartsKey);
            attr.addModifier(new AttributeModifier(darkParticleHeartsKey, stacks * 2.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                darkParticleStacks.computeIfPresent(id, (k, v) -> Math.max(0, v - 1));
                var a = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (a == null) return;
                a.removeModifier(darkParticleHeartsKey);
                int remaining = darkParticleStacks.getOrDefault(id, 0);
                if (remaining > 0) {
                    a.addModifier(new AttributeModifier(darkParticleHeartsKey, remaining * 2.0, AttributeModifier.Operation.ADD_NUMBER));
                }
            }
        }.runTaskLater(plugin, 600L); // 30 seconds
    }

    /** Getting hit while Dark Particle is active immediately clears every current heart stack. */
    @org.bukkit.event.EventHandler
    public void onDarkParticleRetaliation(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !darkParticleActive.contains(victim.getUniqueId())) return;
        UUID id = victim.getUniqueId();
        darkParticleStacks.remove(id);
        var attr = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) attr.removeModifier(darkParticleHeartsKey);
    }


    /**
     * Ability 1 (Grief Shards). Summons 5 floating red daggers around the
     * caster, then fires them one after another at whatever's under the
     * crosshair, each dealing 1 damage (5 total if all connect).
     */
    private static final Material[] WHITE_ITEM_POOL = {
            Material.BONE, Material.WHITE_DYE, Material.PAPER, Material.FEATHER, Material.STRING
    };

    /**
     * Ability 1, Grief Shards. 5 random white items fly at whatever you're
     * looking at; when the volley lands it deals 2.5 hearts of true damage
     * total, then inflicts Bleedout — a DOT that strikes every 3 ticks for
     * 1 normal (armor-affected) damage and stuns for 0.5s on every single
     * strike. Since the strike interval (0.15s) is shorter than the stun
     * (0.5s), the stuns chain back-to-back — the target is effectively
     * locked in place for the whole duration. No Bleedout duration was
     * specified, so this defaults to 4 seconds (~26 strikes); tell me if
     * you want it longer/shorter.
     */
    private void griefShards(Player player) {
        LivingEntity target = resolveForgivingTarget(player, 20);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }

        // Floating white items ring briefly around the caster before launching.
        for (int i = 0; i < 5; i++) {
            double angle = (2 * Math.PI / 5) * i;
            Location point = player.getLocation().add(Math.cos(angle) * 0.9, 1.2, Math.sin(angle) * 0.9);
            ItemStack ring = new ItemStack(WHITE_ITEM_POOL[i % WHITE_ITEM_POOL.length]);
            player.getWorld().spawnParticle(Particle.ITEM, point, 3, 0.03, 0.03, 0.03, 0, ring);
        }
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 0.7f);

        new BukkitRunnable() {
            int shard = 0;

            @Override
            public void run() {
                try {
                    if (shard >= 5 || target.isDead() || !target.isValid()) {
                        cancel();
                        return;
                    }
                    Location from = player.getEyeLocation();
                    Location to = target.getLocation().add(0, target.getHeight() / 2, 0);
                    Vector direction = to.toVector().subtract(from.toVector());
                    double distance = direction.length();
                    direction.normalize();

                    ItemStack shardItem = new ItemStack(WHITE_ITEM_POOL[shard % WHITE_ITEM_POOL.length]);
                    for (double d = 0; d < distance; d += 0.5) {
                        Location trailPoint = from.clone().add(direction.clone().multiply(d));
                        player.getWorld().spawnParticle(Particle.ITEM, trailPoint, 1, 0, 0, 0, 0, shardItem);
                    }
                    player.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 0.6f);
                    shard++;

                    if (shard >= 5) {
                        double trueDamage = 5; // 2.5 hearts, ignoring armor entirely
                        double registerAmount = Math.min(0.5, trueDamage);
                        target.damage(registerAmount, player);
                        target.setHealth(Math.max(0, target.getHealth() - (trueDamage - registerAmount)));
                        startBleedout(player, target);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Grief Shards error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);

        msg(player, "Shards floats toward " + target.getName() + ".");
    }

    private void startBleedout(Player player, LivingEntity target) {
        new BukkitRunnable() {
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (elapsedTicks >= 80 || target.isDead() || !target.isValid()) { // 4 seconds
                    cancel();
                    return;
                }
                target.damage(1, player); // normal damage, armor applies
                JudasPassives.stun(target, 10L, plugin); // 0.5s, chains into the next strike
                target.getWorld().spawnParticle(Particle.DUST,
                        target.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1f));
                elapsedTicks += 3;
            }
        }.runTaskTimer(plugin, 3L, 3L);
    }

    private final org.bukkit.NamespacedKey gloomArmorKey;
    private final Set<UUID> gloomActive = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> depressedRemovalTasks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Ability 2, Gloom (replaces Bloody Rain). A 10s active window: your
     * crits deal reduced immediate damage (the crit bonus itself is
     * stripped back out, leaving roughly a normal hit) but inflict
     * "Depressed" on the target — their armor value drops 20% for 10s.
     * Being hit again while already Depressed just refreshes the 10s
     * timer rather than stacking. No active-window length was specified
     * in the brief itself, only the debuff's own 10s — I mirrored that
     * for the window too; flag it if you wanted something different.
     * 60s cooldown starts once the window ends.
     */
    private void gloom(Player player) {
        UUID id = player.getUniqueId();
        if (gloomActive.contains(id)) {
            msg(player, "Gloom already looms over you.");
            return;
        }
        gloomActive.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
        msg(player, "Gloom settles over your blade.");
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.6f);

        new BukkitRunnable() {
            @Override
            public void run() {
                gloomActive.remove(id);
                if (player.isOnline()) msg(player, "Gloom lifts.");
                plugin.getWeaponManager().putOnCooldown(player, WeaponType.SORROWESS, 2, 60);
                if (player.isOnline()) CooldownBarDisplay.show(plugin, player, WeaponType.SORROWESS, "Gloom (cooldown)", 60);
            }
        }.runTaskLater(plugin, 200L); // 10 seconds
    }

    /** While Gloom is active, crits get their bonus damage stripped and inflict Depressed instead. */
    @org.bukkit.event.EventHandler
    public void onGloomCrit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !gloomActive.contains(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!event.isCritical()) return; // not a crit — Gloom only touches crits

        // There's no public API to read/zero out just the critical portion
        // of the damage anymore (DamageModifier.CRITICAL was removed) — the
        // closest approximation is dividing back out vanilla's ~1.5x crit
        // multiplier, landing close to what a normal hit would have dealt.
        event.setDamage(event.getDamage() / 1.5);
        applyDepressed(player, target);
    }

    private void applyDepressed(Player player, LivingEntity target) {
        var armorAttr = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
        if (armorAttr == null) return;
        UUID targetId = target.getUniqueId();

        org.bukkit.scheduler.BukkitTask existing = depressedRemovalTasks.get(targetId);
        if (existing != null) {
            existing.cancel(); // already Depressed — just refresh the timer below
        } else {
            armorAttr.addModifier(new AttributeModifier(gloomArmorKey, -0.2, AttributeModifier.Operation.ADD_SCALAR));
            target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.02);
            player.sendActionBar(Component.text(target.getName() + " sinks into Depression.", NamedTextColor.DARK_GRAY));
        }

        depressedRemovalTasks.put(targetId, new BukkitRunnable() {
            @Override
            public void run() {
                var attr = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                if (attr != null) attr.removeModifier(gloomArmorKey);
                depressedRemovalTasks.remove(targetId);
            }
        }.runTaskLater(plugin, 200L)); // 10 seconds
    }

    private final Map<UUID, Integer> judasCharges = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> judasLastShot = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Ability 1 (Hemorrhaged Mold), reworked into a 3-charge system. Each
     * activation fires one WitherSkull (tagged so JudasPassives.java can
     * intercept its vanilla behavior on impact) as long as a charge is
     * available and the per-shot 8s cooldown has passed. Once all 3
     * charges are spent, it takes 60s before you get all 3 back.
     */
    private void hemorrhagedMold(Player player) {
        UUID id = player.getUniqueId();
        int charges = judasCharges.getOrDefault(id, 3);
        if (charges <= 0) {
            msg(player, "No more sins were avaible.");
            return;
        }
        long now = System.currentTimeMillis();
        long lastShot = judasLastShot.getOrDefault(id, 0L);
        if (now - lastShot < 8_000L) {
            msg(player, "your sin were reseting.");
            return;
        }
        judasLastShot.put(id, now);
        charges--;
        judasCharges.put(id, charges);

        Location eye = player.getEyeLocation();
        var skull = player.getWorld().spawn(eye, org.bukkit.entity.WitherSkull.class, s -> {
            s.setShooter(player);
            s.setVelocity(eye.getDirection().multiply(1.6));
            s.setCharged(false);
            s.getPersistentDataContainer().set(judasSkullKey, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1.1f);
        msg(player, "toward its mark. (" + charges + "/3 left)");

        if (charges == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    judasCharges.put(id, 3);
                    if (player.isOnline()) msg(player, "Sin Charged (3/3).");
                }
            }.runTaskLater(plugin, 60 * 20L);
        }
    }

    /**
     * Ability 2. Fixed 3-heart sacrifice — but now against max health, not
     * just current health, so you're genuinely capped lower (not just
     * bruised) for the duration — plus Strength III for 15s. Both your
     * missing hearts and your max health are restored together 20 seconds
     * later, regardless of whether you've healed some of it back naturally
     * in the meantime.
     */
    private void thirtyPiecesOfSilver(Player player) {
        double cost = 6; // 3 hearts
        if (player.getHealth() <= cost) {
            msg(player, "Too weak to pay the price.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 2));

        var maxHealthAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        AttributeModifier sacrifice = new AttributeModifier(thirtyPiecesModifierKey,
                -cost, AttributeModifier.Operation.ADD_NUMBER);
        if (maxHealthAttr != null) {
            maxHealthAttr.addModifier(sacrifice);
            player.setHealth(Math.max(1, Math.min(player.getHealth(), maxHealthAttr.getValue())));
        } else {
            player.setHealth(player.getHealth() - cost);
        }
        msg(player, "You feel withing Judas.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attribute != null) {
                    attribute.removeModifier(sacrifice);
                }
                double max = attribute != null ? attribute.getValue() : 20;
                player.setHealth(Math.min(max, player.getHealth() + cost));
                player.sendActionBar(Component.text("Your grip loosen.", NamedTextColor.DARK_RED));
            }
        }.runTaskLater(plugin, 400L); // 20 seconds
    }

    // ---------------- NULLIFIED (VoidBreaker) ----------------

    /**
     * Ability 1 (Density mode), Fractured. Arms the next Crumble-eligible
     * slam to trigger its empowered effect immediately, regardless of the
     * current Sin count. That slam's requirement for the following
     * natural cycle doubles, and if its Aftershock has nothing else to
     * hit, the full mace-damage rebound comes back at you (not halved).
     * The actual empowerment logic lives in VoidBreakerMobility since
     * that's where Crumble's state already lives. 75s cooldown.
     */
    /**
     * Ability 1 (Density mode), Fractured. Permanently adds +1 to
     * Crumble's total requirement (base 7, so after using this twice
     * it's 9) — a deliberate trade: Crumble's empowered hit takes longer
     * to build toward, in exchange for... whatever future scaling reward
     * you want to hang off a higher total later. The actual counter lives
     * in VoidBreakerMobility since that's where Crumble's state already
     * lives. 75s cooldown.
     */
    private void fractured(Player player) {
        plugin.getVoidBreakerMobility().armFractured(player);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1f, 0.7f);
        msg(player, "Crumble fractures further.");
    }

    /**
     * Ability 1 (Breach mode), Infection. Throws VoidBreaker at whatever
     * you're looking at, marking the first thing it hits with Fallen.
     * Fallen spreads to anyone who attacks a Fallen entity while it's
     * still active. You don't get the mace back until Fallen has fully
     * run its course on every infected entity (tracked in
     * VoidBreakerMobility). 130s cooldown.
     */
    private void infection(Player player) {
        LivingEntity target = resolveForgivingTarget(player, 20);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }

        ItemStack thrown = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        Location from = player.getEyeLocation();
        Location to = target.getLocation().add(0, target.getHeight() / 2, 0);
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += 0.5) {
            Location point = from.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 2, 0.05, 0.05, 0.05, 0.01);
        }
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);

        plugin.getVoidBreakerMobility().startInfection(player, target, thrown);
        msg(player, "Infection takes hold.");
    }


    private void nullifyHeldItem(Player target) {
        ItemStack held = target.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;

        if (held.getType() == Material.MACE) {
            plugin.getWeaponManager().putOnCooldown(target, WeaponType.VOIDBREAKER, 1, 120);
            plugin.getWeaponManager().putOnCooldown(target, WeaponType.VOIDBREAKER, 2, 120);
            title(target, "Nullifying yours pride, after all");
            return;
        }
        if (isWeaponMaterial(held.getType())) {
            damageDurability(target, held, 0.20);
            title(target, "Nullifying yours wrath");
            return;
        }
        if (isDiggingMaterial(held.getType())) {
            damageDurability(target, held, 0.20);
            title(target, "Nullifying yours greed");
            return;
        }
        if (held.getType().isEdible() && !GOLDEN_FOODS.contains(held.getType())) {
            held.setAmount(Math.max(0, held.getAmount() - 1));
            target.getInventory().setItemInMainHand(held);
            title(target, "Nullifying yours gluttony");
        }
    }

    private void damageDurability(Player target, ItemStack item, double percentOfMax) {
        if (!(item.getItemMeta() instanceof Damageable dmg)) return;
        if (item.getItemMeta().isUnbreakable()) return;
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;
        int addDamage = Math.max(1, (int) (maxDurability * percentOfMax));
        dmg.setDamage(Math.min(maxDurability, dmg.getDamage() + addDamage));
        item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
        target.getInventory().setItemInMainHand(item);
    }

    private boolean isWeaponMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || material == Material.TRIDENT;
    }

    private boolean isDiggingMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE") || name.endsWith("_HOE");
    }

    /**
     * Ability 2, Bound. Toggles VoidBreaker between Density mode (default)
     * and Breach mode by swapping the real vanilla enchantments on the
     * item — this lets the game engine handle the actual mace smash-attack
     * math, rather than the plugin re-implementing it. Also gives a short
     * forward dash for a bit of mobility. 3s cooldown.
     */
    private void spacedBound(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean wasBreach = item.containsEnchantment(Enchantment.BREACH);

        if (wasBreach) {
            item.removeEnchantment(Enchantment.BREACH);
            item.addUnsafeEnchantment(Enchantment.DENSITY, 6);
            item.addUnsafeEnchantment(Enchantment.WIND_BURST, 3);
            msg(player, "The VoidBreaker Feels Heavy.");
        } else {
            item.removeEnchantment(Enchantment.DENSITY);
            item.removeEnchantment(Enchantment.WIND_BURST);
            item.addUnsafeEnchantment(Enchantment.BREACH, 6);
            msg(player, "The VoidBreaker Feels Light");
        }
        player.getInventory().setItemInMainHand(item);

        Vector dash = player.getLocation().getDirection().normalize().multiply(1.4);
        dash.setY(0.35);
        player.setVelocity(dash);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);
    }

    // ---------------- helpers ----------------

    private boolean isUndead(LivingEntity le) {
        return switch (le.getType()) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY, WITHER_SKELETON,
                    PHANTOM, ZOMBIFIED_PIGLIN, WITHER -> true;
            default -> false;
        };
    }

    private LivingEntity getTargetedEntity(Player player, double range) {
        Entity target = player.getTargetEntity((int) range);
        return target instanceof LivingEntity le ? le : null;
    }

    /**
     * A much more forgiving version of getTargetedEntity — uses a ray trace
     * with a hitbox tolerance instead of a pixel-perfect line, so abilities
     * don't fail just because the crosshair was slightly off. Falls back to
     * the strict method if the ray trace finds nothing.
     */
    private LivingEntity resolveForgivingTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, range, 0.6,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity le) {
            return le;
        }
        return getTargetedEntity(player, range);
    }

    /** Entity under the crosshair if there is one, else the targeted block's location, else a point ahead of the player. */
    private Location resolveCrosshairLocation(Player player, double range) {
        LivingEntity entity = getTargetedEntity(player, range);
        if (entity != null) return entity.getLocation();
        Block block = player.getTargetBlockExact((int) range);
        if (block != null) return block.getLocation().add(0.5, 1, 0.5);
        return player.getLocation().add(player.getLocation().getDirection().multiply(5));
    }

    private void msg(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }

    private void title(Player player, String text) {
        player.showTitle(Title.title(Component.empty(),
                Component.text(text, NamedTextColor.DARK_PURPLE)));
    }
}
