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
    private final Set<UUID> spiralBoomStrikes = new java.util.HashSet<>();
    private final Set<UUID> bloodyRainActive = new java.util.HashSet<>();
    private final java.util.Map<UUID, Long> lastBloodyRainDash = new java.util.HashMap<>();

    private static final Set<Material> GOLDEN_FOODS = EnumSet.of(
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_CARROT);

    public WeaponAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentManager = plugin.getAlignmentManager();
        this.judasSkullKey = new org.bukkit.NamespacedKey(plugin, "judas_skull");
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
                    return 45;
                } else {
                    altarsPin(player);
                    return 80;
                }
            case SWORD_OF_DAVID:
                if (ability == 1) unseenPierce(player); else giantSlayer(player);
                break;
            case STAFF_OF_MOSES:
                if (ability == 1) {
                    frostEdge(player);
                    return 0; // cooldown is applied manually once the 20s active window ends (or ends early)
                } else {
                    entangleFreeze(player);
                    return 45;
                }
            case SCYTHE_OF_CAIN:
                if (ability == 1) lifestealStrike(player); else markOfCain(player);
                break;
            case SORROWESS:
                if (ability == 1) {
                    griefShards(player);
                } else {
                    leviathanRoar(player);
                    return 0; // cooldown is applied manually once the 30s buff ends
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
                    return 5;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.containsEnchantment(Enchantment.BREACH)) {
                    lightlessPhos(player);
                    return 60;
                } else {
                    spiralBoom(player);
                    return 15;
                }
        }
        return plugin.getWeaponManager().getConfiguredCooldown(ability);
    }

    // ---------------- Excalibur (formerly Blade of the Archangel) ----------------

    /**
     * Ability 1, Accelerated Nova. A 4s charge (boss bar + swirling
     * particles), then releases a radial true-damage burst around the
     * player: 5 hearts to everything nearby, bumped to 7.5 against Evil-tier
     * players and undead, with a heavy outward-shooting particle nova.
     */
    private void acceleratedNova(Player player) {
        int chargeTicks = 80; // 4 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, chargeTicks + 5, 1));
        org.bukkit.boss.BossBar bar = Bukkit.createBossBar(
                player.getName() + " is charging Accelerated Nova...",
                org.bukkit.boss.BarColor.WHITE, org.bukkit.boss.BarStyle.SOLID);
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
        Location center = player.getLocation().add(0, 1, 0);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.6f);

        // MANY MANY particles shooting outward in a full ring burst, in expanding waves.
        new BukkitRunnable() {
            int wave = 0;

            @Override
            public void run() {
                double radius = 0.6 + wave * 0.9;
                int points = 40;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    Vector offset = new Vector(radius * Math.cos(angle), Math.sin(wave * 0.8) * 0.3, radius * Math.sin(angle));
                    center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(offset), 1, 0, 0, 0, 0.02);
                    center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(offset), 0);
                }
                wave++;
                if (wave >= 5) cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);

        double radius = 5;
        double baseDamage = 10; // 5 hearts, ignoring armor entirely
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || target.equals(player)) continue;

            boolean bonus = (target instanceof Player p && alignmentManager.getTier(p).isEvil()) || isUndead(target);
            double damage = bonus ? 15 : baseDamage; // bonus = 7.5 hearts through armor

            double registerAmount = Math.min(0.5, damage);
            target.damage(registerAmount, player);
            target.setHealth(Math.max(0, target.getHealth() - (damage - registerAmount)));
            target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1, Color.WHITE);
        }
        msg(player, "Accelerated Nova erupts around you.");
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
                for (int i = 0; i < 12; i++) {
                    double angle = (tick * 0.3) + (2 * Math.PI / 12) * i;
                    Vector offset = new Vector(Math.cos(angle) * 1.4, 0.1, Math.sin(angle) * 1.4);
                    feet.getWorld().spawnParticle(Particle.PORTAL, feet.clone().add(offset), 1, 0, 0, 0, 0);
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
        msg(player, "Altar's Pin draws everything toward you.");
    }

    private void smashSword(Player player) {
        Location center = player.getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.3);
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 3, 0), 60, 0.3, 1.5, 0.3, 0.05);
        center.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.6f);
        center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);

        double radius = 4;
        double trueDamage = 14; // 7 hearts, ignoring armor entirely
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || target.equals(player)) continue;
            double registerAmount = Math.min(0.5, trueDamage);
            target.damage(registerAmount, player);
            target.setHealth(Math.max(0, target.getHealth() - (trueDamage - registerAmount)));
        }
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

    /**
     * Ability 1 (Unseen Pierce). Blinds the target, then teleports the
     * caster behind them 4 times in fast succession, lunging for a small
     * hit each time (totalling 4.5 damage) with a visual-only lightning
     * bolt on every landing.
     */
    private void unseenPierce(Player player) {
        LivingEntity target = resolveForgivingTarget(player, 15);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
        double[] hitDamages = {1.0, 1.0, 1.0, 1.5}; // sums to 4.5

        new BukkitRunnable() {
            int hit = 0;

            @Override
            public void run() {
                try {
                    if (hit >= 4 || target.isDead() || !target.isValid()) {
                        cancel();
                        return;
                    }
                    Vector behind = target.getLocation().getDirection().normalize().multiply(-1.3);
                    Location dest = target.getLocation().add(behind);
                    dest.setDirection(target.getLocation().toVector().subtract(dest.toVector()));
                    player.teleport(dest);

                    target.damage(hitDamages[hit], player);
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    player.getWorld().spawnParticle(Particle.END_ROD, dest, 15, 0.2, 0.3, 0.2, 0.02);
                    player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
                    hit++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Unseen Pierce error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 3L, 3L); // fast pace — 4 hits in under a second

        msg(player, "Unseen Pierce begins!");
    }

    private void giantSlayer(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
        LivingEntity target = getTargetedEntity(player, 4);
        if (target != null) {
            target.damage(player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue() * 2, player);
        }
        msg(player, "Giant Slayer empowers your strike.");
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
        CooldownBarDisplay.show(plugin, player, "Frost Edge", 20);
        msg(player, "Frost Edge awakens in your blade.");

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
        if (player.isOnline()) msg(player, "Frost Edge fades.");
        plugin.getWeaponManager().putOnCooldown(player, WeaponType.STAFF_OF_MOSES, 1, 30);
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
        msg(victim, "Frost Edge shatters early.");
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
            msg(player, "Entangle Freeze is already forming.");
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
                        msg(player, "Entangle Freeze pins " + target.getName() + " in place.");
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

    private void lifestealStrike(Player player) {
        LivingEntity target = getTargetedEntity(player, 4);
        if (target == null) {
            msg(player, "No target in reach.");
            return;
        }
        double dmg = 5;
        target.damage(dmg, player);
        player.setHealth(Math.min(player.getHealth() + dmg * 0.5, player.getMaxHealth()));
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 10);
        msg(player, "You drain " + target.getName() + "'s life force.");
    }

    private void markOfCain(Player player) {
        LivingEntity target = resolveForgivingTarget(player, 15);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 140, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 140, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));

        // Much more visual weight: a dark swirling mark on the target.
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                try {
                    if (tick >= 40 || target.isDead() || !target.isValid()) {
                        cancel();
                        return;
                    }
                    double angle = tick * 0.6;
                    Location point = target.getLocation().add(
                            Math.cos(angle) * 0.6, 1 + Math.sin(tick * 0.2) * 0.3, Math.sin(angle) * 0.6);
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(90, 0, 0), 1.2f);
                    target.getWorld().spawnParticle(Particle.DUST, point, 2, 0, 0, 0, 0, dust);
                    tick++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Mark of Cain animation error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 0.6f);
        msg(player, "The Mark of Cain is placed upon " + target.getName() + ".");
    }

    /**
     * Ability 1 (Grief Shards). Summons 5 floating red daggers around the
     * caster, then fires them one after another at whatever's under the
     * crosshair, each dealing 1 damage (5 total if all connect).
     */
    private void griefShards(Player player) {
        LivingEntity target = resolveForgivingTarget(player, 20);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }

        // Floating daggers ring briefly around the caster before launching.
        for (int i = 0; i < 5; i++) {
            double angle = (2 * Math.PI / 5) * i;
            Location point = player.getLocation().add(Math.cos(angle) * 0.9, 1.2, Math.sin(angle) * 0.9);
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.3f);
            player.getWorld().spawnParticle(Particle.DUST, point, 3, 0.03, 0.03, 0.03, 0, dust);
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

                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1f);
                    for (double d = 0; d < distance; d += 0.5) {
                        Location trailPoint = from.clone().add(direction.clone().multiply(d));
                        player.getWorld().spawnParticle(Particle.DUST, trailPoint, 1, 0, 0, 0, 0, dust);
                    }
                    target.damage(1, player);
                    player.playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 0.6f);
                    shard++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Grief Shards error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);

        msg(player, "Grief Shards fly at " + target.getName() + "!");
    }

    /**
     * Ability 2 (Bloody Rain). A 30-second state: cherry-leaf "rain" falls
     * in a 5x5 area around the caster (following them), anything that
     * enters that area gets Wither+Darkness, and — as close as the public
     * API allows to true vanilla Riptide-anywhere — right-clicking with
     * Sorrowess during this window manually launches a riptide-style dash
     * regardless of whether you're actually wet. Cooldown (60s) only
     * starts once the 30 seconds run out, not on activation.
     */
    private void leviathanRoar(Player player) {
        UUID id = player.getUniqueId();
        if (bloodyRainActive.contains(id)) {
            msg(player, "Bloody Rain is already falling.");
            return;
        }
        bloodyRainActive.add(id);
        msg(player, "Bloody Rain begins to fall.");
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.7f, 0.6f);

        new BukkitRunnable() {
            int tick = 0; // advances by 4 each run (every 4 ticks)

            @Override
            public void run() {
                try {
                    if (tick >= 600 || !player.isOnline()) {
                        bloodyRainActive.remove(id);
                        cancel();
                        plugin.getWeaponManager().putOnCooldown(player, WeaponType.SORROWESS, 2, 60);
                        if (player.isOnline()) msg(player, "Bloody Rain fades.");
                        return;
                    }
                    Location center = player.getLocation();
                    for (int i = 0; i < 4; i++) {
                        double x = (Math.random() - 0.5) * 5;
                        double z = (Math.random() - 0.5) * 5;
                        center.getWorld().spawnParticle(Particle.CHERRY_LEAVES, center.clone().add(x, 2.5, z), 1, 0, 0, 0, 0);
                    }
                    for (Entity e : player.getNearbyEntities(2.5, 2.5, 2.5)) {
                        if (e instanceof LivingEntity le && !le.equals(player)) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
                            le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0));
                        }
                    }
                    tick += 4;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Bloody Rain error: " + ex);
                    ex.printStackTrace();
                    bloodyRainActive.remove(id);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * Approximates "riptide anywhere" during Bloody Rain — vanilla gates
     * real Riptide behind an actual wet/rain check deep in game code that
     * isn't exposed to plugins, so this manually launches the same kind of
     * dash instead of trying to bypass that check.
     */
    @org.bukkit.event.EventHandler
    public void onBloodyRainRiptideAttempt(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!bloodyRainActive.contains(player.getUniqueId())) return;
        if (plugin.getWeaponManager().getWeaponType(player.getInventory().getItemInMainHand()) != WeaponType.SORROWESS) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        long now = System.currentTimeMillis();
        long last = lastBloodyRainDash.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500) return;
        lastBloodyRainDash.put(player.getUniqueId(), now);

        Vector dash = player.getLocation().getDirection().normalize().multiply(1.8);
        player.setVelocity(dash);
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation(), 30, 0.3, 0.3, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
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
            msg(player, "No wither skulls left — recharging.");
            return;
        }
        long now = System.currentTimeMillis();
        long lastShot = judasLastShot.getOrDefault(id, 0L);
        if (now - lastShot < 8_000L) {
            msg(player, "Hemorrhaged Mold is still recharging that shot.");
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
        msg(player, "Hemorrhaged Mold streaks toward its mark. (" + charges + "/3 left)");

        if (charges == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    judasCharges.put(id, 3);
                    if (player.isOnline()) msg(player, "Hemorrhaged Mold fully recharges.");
                }
            }.runTaskLater(plugin, 60 * 20L);
        }
    }

    /**
     * Ability 2. Fixed 3-heart sacrifice, Strength III for 15s, and the
     * sacrificed hearts are handed back automatically 20 seconds later
     * (regardless of whether the player has since healed some of it back
     * naturally — this always tops them up by the sacrificed amount).
     */
    private void thirtyPiecesOfSilver(Player player) {
        double cost = 6; // 3 hearts
        if (player.getHealth() <= cost) {
            msg(player, "Too weak to pay the price.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1f);
        player.setHealth(player.getHealth() - cost);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 2));
        LivingEntity target = getTargetedEntity(player, 4);
        if (target != null) {
            target.damage(10, player);
        }
        msg(player, "You pay in blood for power.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double max = attribute != null ? attribute.getValue() : 20;
                player.setHealth(Math.min(max, player.getHealth() + cost));
                player.sendActionBar(Component.text("Your sacrifice is repaid.", NamedTextColor.DARK_RED));
            }
        }.runTaskLater(plugin, 400L); // 20 seconds
    }

    // ---------------- NULLIFIED (VoidBreaker) ----------------

    /**
     * Density-mode Ability 1. Draws a custom particle "lightning spiral" at
     * whatever the player is looking at (entity or block), pulsing damage
     * and a Blindness+Slowness III debuff to anything in a ~4x4 area there
     * over 5 seconds. This is fully custom particle work — no vanilla
     * LightningBolt entity is summoned.
     */
    private void spiralBoom(Player player) {
        Location epicenter = resolveCrosshairLocation(player, 20);
        player.playSound(epicenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
        player.playSound(epicenter, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1f);
        msg(player, "Spiral Boom crackles to life!");

        double areaRadius = 3.5; // approximates the requested "7x7" area
        double areaDamagePerPulse = plugin.getConfig().getDouble("voidbreaker.spiral-boom-damage-per-pulse", 6);

        new BukkitRunnable() {
            int tick = 0; // counts in 2-tick steps, 50 steps = 100 ticks = 5s

            @Override
            public void run() {
                try {
                    if (tick >= 50 || epicenter.getWorld() == null
                            || !epicenter.getWorld().isChunkLoaded(epicenter.getBlockX() >> 4, epicenter.getBlockZ() >> 4)) {
                        cancel();
                        return;
                    }
                    double angle = tick * 0.7;
                    double radius = 1.3;
                    double height = ((tick % 20) / 20.0) * 2.5;
                    Location point = epicenter.clone().add(radius * Math.cos(angle), height, radius * Math.sin(angle));

                    // ELECTRIC_SPARK alone is tiny and easy to miss — layer a bright
                    // END_ROD trail on top so the spiral is obvious at a glance.
                    epicenter.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 6, 0.05, 0.05, 0.05, 0.02);
                    epicenter.getWorld().spawnParticle(Particle.END_ROD, point, 2, 0.02, 0.02, 0.02, 0.01);

                    if (tick % 5 == 0) {
                        epicenter.getWorld().spawnParticle(Particle.FLASH, epicenter.clone().add(0, 1, 0), 1, Color.WHITE);

                        // A REAL lightning bolt entity (not just the visual effect) —
                        // vanilla lightning damage is capped to exactly 1 via
                        // onSpiralBoomLightningDamage() below, keyed to this specific strike.
                        double bx = (Math.random() - 0.5) * areaRadius * 2;
                        double bz = (Math.random() - 0.5) * areaRadius * 2;
                        Location boltSpot = epicenter.clone().add(bx, 0, bz);
                        var strike = epicenter.getWorld().strikeLightning(boltSpot);
                        if (strike != null) {
                            spiralBoomStrikes.add(strike.getUniqueId());
                        }

                        for (Entity e : epicenter.getWorld().getNearbyEntities(epicenter, areaRadius, 3, areaRadius)) {
                            if (e instanceof LivingEntity le && !le.equals(player)) {
                                le.damage(areaDamagePerPulse, player);
                                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2));
                            }
                        }
                    }
                    tick++;
                } catch (Exception ex) {
                    // A silently-dying repeating task looks exactly like "it did
                    // nothing" — log it loudly instead so it shows up in console.
                    plugin.getLogger().warning("Spiral Boom animation error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Breach-mode Ability 1. Not a one-shot burst anymore — this opens a
     * dark aura that follows the caster for 20 seconds, pulsing a flat
     * 5-heart true-damage hit (armor is ignored entirely) to anything
     * within an 8x8 area around them every 2 seconds, while "nullifying"
     * whatever every nearby player is holding each pulse.
     */
    private void lightlessPhos(Player player) {
        double range = 4; // approximates "8x8"
        double trueDamage = 10; // 5 hearts per pulse, ignoring armor entirely

        msg(player, "Lightless Ph\u014ds consumes the light around you.");

        new BukkitRunnable() {
            int pulse = 0; // one pulse every 40 ticks (2s) — 10 pulses across 20s

            @Override
            public void run() {
                try {
                    if (pulse >= 10 || !player.isOnline() || player.isDead()) {
                        cancel();
                        return;
                    }
                    Location center = player.getLocation();
                    center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
                    center.getWorld().playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.9f);

                    for (int i = 0; i < 70; i++) {
                        double x = (Math.random() - 0.5) * range * 2;
                        double y = Math.random() * 3;
                        double z = (Math.random() - 0.5) * range * 2;
                        Location p = center.clone().add(x, y, z);
                        switch (i % 6) {
                            case 0 -> center.getWorld().spawnParticle(Particle.SCULK_SOUL, p, 1, 0, 0, 0, 0);
                            case 1 -> center.getWorld().spawnParticle(Particle.SQUID_INK, p, 1, 0, 0, 0, 0);
                            case 2 -> center.getWorld().spawnParticle(Particle.LARGE_SMOKE, p, 1, 0, 0, 0, 0);
                            case 3 -> center.getWorld().spawnParticle(Particle.ASH, p, 1, 0, 0, 0, 0);
                            case 4 -> center.getWorld().spawnParticle(Particle.SOUL, p, 1, 0, 0, 0, 0);
                            default -> center.getWorld().spawnParticle(Particle.WITCH, p, 1, 0, 0, 0, 0);
                        }
                    }

                    for (Entity e : player.getNearbyEntities(range, range, range)) {
                        if (e.equals(player) || !(e instanceof LivingEntity le)) continue;

                        // Register real kill-credit with a negligible normal hit, then
                        // apply the rest directly to health so armor can't reduce it.
                        double registerAmount = Math.min(0.5, trueDamage);
                        le.damage(registerAmount, player);
                        double remaining = trueDamage - registerAmount;
                        le.setHealth(Math.max(0, le.getHealth() - remaining));

                        if (e instanceof Player target) {
                            nullifyHeldItem(target);
                        }
                    }
                    pulse++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Lightless Ph\u014ds error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
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
     * Ability 2. Toggles VoidBreaker between Density mode (default) and
     * Breach mode by swapping the real vanilla enchantments on the item —
     * this lets the game engine handle the actual mace smash-attack math,
     * rather than the plugin re-implementing it. Also gives a short
     * forward dash for a bit of mobility. No cooldown.
     */
    private void spacedBound(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean wasBreach = item.containsEnchantment(Enchantment.BREACH);

        if (wasBreach) {
            item.removeEnchantment(Enchantment.BREACH);
            item.addUnsafeEnchantment(Enchantment.DENSITY, 6);
            item.addUnsafeEnchantment(Enchantment.WIND_BURST, 1);
            msg(player, "VoidBreaker shifts into Density mode.");
        } else {
            item.removeEnchantment(Enchantment.DENSITY);
            item.removeEnchantment(Enchantment.WIND_BURST);
            item.addUnsafeEnchantment(Enchantment.BREACH, 6);
            msg(player, "VoidBreaker shifts into Breach mode.");
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

    /** Caps damage from Spiral Boom's real lightning bolts to exactly 1, regardless of vanilla's normal ~5. */
    @org.bukkit.event.EventHandler
    public void onSpiralBoomLightningDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.LightningStrike strike
                && spiralBoomStrikes.remove(strike.getUniqueId())) {
            event.setDamage(1.0);
        }
    }

    private void title(Player player, String text) {
        player.showTitle(Title.title(Component.empty(),
                Component.text(text, NamedTextColor.DARK_PURPLE)));
    }
}
