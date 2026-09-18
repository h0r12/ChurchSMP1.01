package com.churchsmp.relics;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

    // Greed
    private final Map<String, Long> goldSiphonReadyAt = new HashMap<>(); // keyed "attackerUUID:victimUUID"
    private final Set<UUID> sealedHotbar = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public RelicAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.relicManager = plugin.getRelicManager();
    }

    private boolean hasWrath(Player player) {
        return relicManager.getAssignedRelic(player) == RelicType.WRATH;
    }

    private boolean hasGreed(Player player) {
        return relicManager.getAssignedRelic(player) == RelicType.GREED;
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

    // ============================================================
    // Greed
    // ============================================================

    private static final Material[] SIPHONABLE = {Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.ENDER_PEARL};

    /** Passive 2, Gold Siphon: 2% chance per hit to steal a Golden Apple or Ender Pearl from the target's hotbar. */
    @EventHandler
    public void onGoldSiphon(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !hasGreed(player)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        String pairKey = player.getUniqueId() + ":" + victim.getUniqueId();
        Long readyAt = goldSiphonReadyAt.get(pairKey);
        if (readyAt != null && readyAt > System.currentTimeMillis()) return;
        if (Math.random() >= 0.02) return;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = victim.getInventory().getItem(slot);
            if (item == null) continue;
            for (Material siphonable : SIPHONABLE) {
                if (item.getType() == siphonable) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().addItem(new ItemStack(siphonable));
                    goldSiphonReadyAt.put(pairKey, System.currentTimeMillis() + 20_000L);
                    player.sendMessage(Component.text("Gold Siphon pulls " + siphonable.name().toLowerCase().replace('_', ' ')
                            + " from " + victim.getName() + "'s hand.", NamedTextColor.GOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.7f);
                    return;
                }
            }
        }
    }

    /**
     * Ability 1, Taxing Ray. RMB, 32s cd. Fires an 8-block gold beam; the
     * first player it hits has their current hotbar slot and shield/offhand
     * blocking locked for 4s.
     */
    @EventHandler
    public void onTaxingRayTrigger(PlayerInteractEvent event) {
        if (event.isCancelled()) return; // a legendary weapon ability already consumed this click
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!hasGreed(player) || player.isSneaking()) return;
        if (relicManager.isOnCooldown(player, 1)) return;

        relicManager.putOnCooldown(player, 1, 32);
        event.setCancelled(true);

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = 8;
        Particle.DustOptions gold = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 30), 1.2f);

        for (double d = 0; d < range; d += 0.4) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            point.getWorld().spawnParticle(Particle.DUST, point, 3, 0.05, 0.05, 0.05, 0, gold);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);

        for (double d = 0; d < range; d += 0.5) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            for (Entity e : point.getWorld().getNearbyEntities(point, 1, 1, 1)) {
                if (e instanceof Player target && !target.equals(player)) {
                    sealHotbar(target);
                    player.sendMessage(Component.text("Taxing Ray seals " + target.getName() + "'s hand.", NamedTextColor.GOLD));
                    return;
                }
            }
        }
    }

    private void sealHotbar(Player target) {
        UUID id = target.getUniqueId();
        sealedHotbar.add(id);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 30), 1.2f));

        new BukkitRunnable() {
            @Override
            public void run() {
                sealedHotbar.remove(id);
            }
        }.runTaskLater(plugin, 80L); // 4 seconds
    }

    /** Prevents switching hotbar slots while sealed by Taxing Ray. */
    @EventHandler
    public void onSealedSlotSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!sealedHotbar.contains(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Prevents shield/offhand blocking while sealed by Taxing Ray. */
    @EventHandler
    public void onSealedBlockAttempt(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!sealedHotbar.contains(player.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (item.getType() == Material.SHIELD) {
            event.setCancelled(true);
        }
    }

    /**
     * Ability 2, Taken. Sneak+RMB, 120s cd. Consumes whatever /ritual
     * staged and applies the matching effect.
     */
    @EventHandler
    public void onTakenTrigger(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!hasGreed(player) || !player.isSneaking()) return;
        if (relicManager.isOnCooldown(player, 2)) return;

        String preload = relicManager.getGreedPreload(player);
        if (preload == null) {
            player.sendMessage(Component.text("Nothing is primed — use /ritual first.", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        relicManager.putOnCooldown(player, 2, 120);
        relicManager.clearGreedPreload(player);

        switch (preload) {
            case "ORES" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 3)); // 10s, Absorption IV
                player.sendMessage(Component.text("Taken grants Absorption IV.", NamedTextColor.GOLD));
            }
            case "SWORD" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 150, 2)); // 7.5s, Strength III
                player.sendMessage(Component.text("Taken grants Strength III.", NamedTextColor.GOLD));
            }
            case "HEAD" -> {
                LivingEntity target = resolveGreedTarget(player, 25);
                if (target == null) {
                    player.sendMessage(Component.text("No target in sight — the ritual is wasted.", NamedTextColor.RED));
                    return;
                }
                startDamageTether(player, target);
                player.sendMessage(Component.text("Taken tethers itself to " + target.getName() + ".", NamedTextColor.GOLD));
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.5f);
    }

    private LivingEntity resolveGreedTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || le.equals(player)) continue;
            Vector toTarget = le.getLocation().toVector().subtract(eye.toVector());
            double dist = toTarget.length();
            if (dist > range) continue;
            double angle = toTarget.normalize().angle(direction);
            if (angle < 0.3 && dist < bestDist) {
                best = le;
                bestDist = dist;
            }
        }
        return best;
    }

    /** The Player Head branch of Taken: 30s, siphoning 10% of the tracked target's dealt damage back as healing. */
    private final Map<UUID, UUID> activeTethers = new HashMap<>(); // casterUUID -> targetUUID

    private void startDamageTether(Player caster, LivingEntity target) {
        UUID casterId = caster.getUniqueId();
        UUID targetId = target.getUniqueId();
        activeTethers.put(casterId, targetId);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 600 || !caster.isOnline() || !target.isValid() || target.isDead()) { // 30 seconds
                    activeTethers.remove(casterId, targetId);
                    cancel();
                    return;
                }
                if (caster.getWorld().equals(target.getWorld())) {
                    Location from = caster.getLocation().add(0, 1, 0);
                    Location to = target.getLocation().add(0, 1, 0);
                    Vector step = to.toVector().subtract(from.toVector());
                    double dist = step.length();
                    if (dist > 0) {
                        step.normalize();
                        for (double d = 0; d < dist; d += 1.0) {
                            caster.getWorld().spawnParticle(Particle.DUST, from.clone().add(step.clone().multiply(d)),
                                    2, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 30), 1f));
                        }
                    }
                }
                tick += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** Whenever the tethered target deals damage, the caster is healed for 10% of it. */
    @EventHandler
    public void onTetherSiphon(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        for (var entry : activeTethers.entrySet()) {
            if (entry.getValue().equals(attacker.getUniqueId())) {
                Player caster = Bukkit.getPlayer(entry.getKey());
                if (caster != null && caster.isOnline()) {
                    double heal = event.getDamage() * 0.10;
                    caster.setHealth(Math.min(caster.getHealth() + heal,
                            caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
                }
                return;
            }
        }
    }
}
