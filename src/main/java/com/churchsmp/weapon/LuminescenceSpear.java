package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuminescenceSpear extends LegendaryWeapon {

    private final Map<UUID, Long> boltCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lightStealCooldown = new ConcurrentHashMap<>();

    // 5-hit attack counter for Orbital Lightning Bolt
    private final Map<UUID, Integer> attackCounter = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> orbitalReady = new ConcurrentHashMap<>();

    // SawRay: marks on target, UUID of attacker -> UUID of marked target + count
    private final Map<UUID, UUID> markedTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> markCount = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sawReady = new ConcurrentHashMap<>();
    private final Map<UUID, Item> chargedSaw = new ConcurrentHashMap<>();

    // Re-entrancy guard to prevent ability damage from triggering onHit recursive loops
    private final Set<UUID> abilityDamageActive = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // CrescentEclipse mark key on entities
    private final NamespacedKey crescentMarkKey;

    public LuminescenceSpear(ChurchSMP plugin) {
        super(plugin,
                "luminescence_spear",
                new String[]{"sword_of_david"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#FFFFFF:#00008B:#FFFFFF><bold>Luminescence Spear</bold></gradient>"),
                Material.TRIDENT,
                Alignment.GOOD,
                "Dash",
                "SawRay");
        this.crescentMarkKey = new NamespacedKey(plugin, "crescent_mark");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = super.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.LOYALTY, 3, true);

            // Trident damage equivalent to Netherite Sword Sharpness 7 (+12.0 base damage modifier)
            NamespacedKey dmgKey = new NamespacedKey(plugin, "spear_damage");
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(dmgKey, 12.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

            // BurningBones: attack speed of sword (1.6)
            NamespacedKey speedKey = new NamespacedKey(plugin, "spear_speed");
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.setCustomModelData(1002);

            item.setItemMeta(meta);
        }
        return item;
    }

    // ─── PRIMARY: Dash (20s cooldown) ─────────────────────────────────
    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // 20s cooldown as requested
        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.primary_cooldown", 20);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        Vector dir = player.getLocation().getDirection().normalize();
        Location start = player.getLocation().clone();

        player.setVelocity(dir.clone().multiply(2.8).setY(0.25));
        player.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.4f);
        player.playSound(start, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.8f);

        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 1.0, 0.1, Math.sin(rad) * 1.0), 1, 0, 0, 0, 0, whiteDust);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 2.2, 0.1, Math.sin(rad) * 2.2), 1, 0, 0, 0, 0, goldDust);
        }

        java.util.Set<java.util.UUID> hitVictims = new java.util.HashSet<>();
        Vector up = new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(up).normalize();
        boolean markedOne = false;

        for (double dist = 0; dist <= 6.0; dist += 0.25) {
            double wave = Math.sin(dist * 2.5) * 0.45;
            Location p = start.clone().add(dir.clone().multiply(dist)).add(right.clone().multiply(wave)).add(0, 0.9 + Math.cos(dist * 2.5) * 0.2, 0);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 3, 0.05, 0.05, 0.05, 0.02);
            p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.01);

            for (LivingEntity victim : p.getWorld().getNearbyLivingEntities(p, 1.5)) {
                if (victim.equals(player)) continue;
                if (!hitVictims.add(victim.getUniqueId())) continue; // Hit only once per target

                victim.damage(4.0, player);
                victim.getPersistentDataContainer().set(crescentMarkKey, PersistentDataType.STRING, player.getUniqueId().toString());
                victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));

                // Mark with EXACTLY ONE orbital lightning bolt (only the first target hit)
                if (!markedOne) {
                    markedOne = true;
                    triggerOrbitalLightning(victim.getLocation(), player, true);
                    player.sendMessage(Component.text("✦ Dash hit! Target marked with Orbital Lightning Bolt.", NamedTextColor.YELLOW));
                }

                Location vFeet = victim.getLocation();
                for (int vd = 0; vd < 360; vd += 30) {
                    double vrad = Math.toRadians(vd);
                    vFeet.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, vFeet.clone().add(Math.cos(vrad) * 0.9, 0.1, Math.sin(vrad) * 0.9), 1, 0, 0, 0, 0);
                }
            }
        }

        player.sendMessage(Component.text("✦ Dash! (20s cooldown)", NamedTextColor.AQUA));
        return true;
    }

    // ─── SECONDARY: SawRay ───────────────────────────────────────────────────────
    @Override
    public boolean executeSecondary(Player player) {
        if (Boolean.TRUE.equals(sawReady.get(player.getUniqueId()))) {
            fireSaw(player);
            return true;
        }

        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        plugin.getBossBarManager().showActiveCountdown(player, "SawRay — Mark target 3x", BossBar.Color.BLUE, 15);
        plugin.getCooldownManager().setActive(player, key, 15);
        markCount.put(player.getUniqueId(), 0);
        markedTarget.remove(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        player.sendMessage(Component.text("✦ SawRay — Attack a marked target 3 times to charge the Antimatter Saw!", NamedTextColor.GOLD));
        return true;
    }

    public void onMeleeHit(Player attacker, LivingEntity target) {
        long now = System.currentTimeMillis();
        String key = id + "_secondary";

        // LightStealing: Darkness (60s CD)
        long lastSteal = lightStealCooldown.getOrDefault(attacker.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(attacker.getUniqueId(), now);
            plugin.getBossBarManager().showPassiveCooldown(attacker, this, "LightStealing", 60);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            attacker.sendMessage(Component.text("✦ LightStealing inflicted Darkness!", NamedTextColor.GRAY));
        }

        // Attack 5 times -> Orbital Lightning Bolt on next hit or throw
        if (Boolean.TRUE.equals(orbitalReady.remove(attacker.getUniqueId()))) {
            triggerOrbitalLightning(target.getLocation(), attacker, false);
            attacker.sendMessage(Component.text("✦ Unleashed Orbital Lightning Bolt!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        } else {
            int currentHits = attackCounter.getOrDefault(attacker.getUniqueId(), 0) + 1;
            if (currentHits >= 5) {
                attackCounter.put(attacker.getUniqueId(), 0);
                orbitalReady.put(attacker.getUniqueId(), true);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.6f);
                attacker.sendMessage(Component.text("✦ Orbital Lightning Bolt Armed! Next hit or throw unleashes it!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                plugin.getBossBarManager().showActiveCountdown(attacker, this, "Orbital Bolt Ready", 10);
            } else {
                attackCounter.put(attacker.getUniqueId(), currentHits);
                attacker.sendMessage(Component.text("✦ Orbital Charge: [" + currentHits + "/5]", NamedTextColor.AQUA));
            }
        }

        // SawRay mark accumulation
        if (!plugin.getCooldownManager().isActive(attacker, key)) return;
        if (sawReady.getOrDefault(attacker.getUniqueId(), false)) return;

        String markerStr = target.getPersistentDataContainer().get(crescentMarkKey, PersistentDataType.STRING);
        if (markerStr == null || !markerStr.equals(attacker.getUniqueId().toString())) return;

        int marks = markCount.getOrDefault(attacker.getUniqueId(), 0) + 1;
        markCount.put(attacker.getUniqueId(), marks);

        Location hitLoc = target.getLocation().add(0, 1.5, 0);
        Particle.DustOptions markDust = new Particle.DustOptions(Color.fromRGB(0, 0, 200), 1.3f);
        for (int d = 0; d < 360; d += 45) {
            double rad = Math.toRadians(d);
            hitLoc.getWorld().spawnParticle(Particle.DUST, hitLoc.clone().add(Math.cos(rad) * 0.6, 0, Math.sin(rad) * 0.6), 1, 0, 0, 0, 0, markDust);
        }
        hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f + (marks * 0.3f));

        attacker.sendMessage(Component.text("✦ SawRay Mark: " + marks + "/3", NamedTextColor.BLUE));

        if (marks >= 3) {
            markCount.put(attacker.getUniqueId(), 0);
            markedTarget.put(attacker.getUniqueId(), target.getUniqueId());
            chargeSaw(attacker);
        }
    }

    private void chargeSaw(Player player) {
        sawReady.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 2.0f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.8f);
        player.sendMessage(Component.text("✦ Antimatter Saw Charged! Right-click to FIRE!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        Location spawnLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        ItemStack sawItem = new ItemStack(Material.DISC_FRAGMENT_5);
        Item floatingSaw = player.getWorld().dropItem(spawnLoc, sawItem);
        floatingSaw.setGravity(false);
        floatingSaw.setPickupDelay(Integer.MAX_VALUE);
        floatingSaw.setCanMobPickup(false);
        floatingSaw.setVelocity(new Vector(0, 0, 0));
        chargedSaw.put(player.getUniqueId(), floatingSaw);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!sawReady.getOrDefault(player.getUniqueId(), false) || !player.isOnline()) {
                    floatingSaw.remove();
                    chargedSaw.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                Location handLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.2));
                floatingSaw.teleport(handLoc);

                Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(0, 0, 255), 1.5f);
                for (int d = 0; d < 360; d += 60) {
                    double rad = Math.toRadians(d + (ticks * 20));
                    handLoc.getWorld().spawnParticle(Particle.DUST,
                            handLoc.clone().add(Math.cos(rad) * 0.4, Math.sin(rad) * 0.4, 0),
                            1, 0, 0, 0, 0, blueDust);
                }

                if (ticks > 200) { // 10 seconds timeout
                    sawReady.remove(player.getUniqueId());
                    floatingSaw.remove();
                    chargedSaw.remove(player.getUniqueId());
                    player.sendMessage(Component.text("✦ Antimatter Saw discharged.", NamedTextColor.GRAY));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireSaw(Player player) {
        sawReady.remove(player.getUniqueId());
        Item saw = chargedSaw.remove(player.getUniqueId());
        if (saw != null) saw.remove();

        String key = id + "_secondary";
        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.secondary_cooldown", 30);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location fireLoc = player.getEyeLocation().clone();

        player.playSound(fireLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        player.playSound(fireLoc, Sound.ITEM_TRIDENT_THROW, 1.5f, 0.8f);

        Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(0, 0, 220), 1.8f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f);

        new BukkitRunnable() {
            double dist = 0;
            boolean hit = false;

            @Override
            public void run() {
                if (dist > 25 || hit) {
                    cancel();
                    return;
                }
                dist += 0.6;
                Location curr = fireLoc.clone().add(dir.clone().multiply(dist));

                for (int i = 0; i < 3; i++) {
                    double angle = Math.toRadians(dist * 45 + i * 120);
                    curr.getWorld().spawnParticle(Particle.DUST, curr.clone().add(Math.cos(angle) * 0.5, Math.sin(angle) * 0.5, 0), 1, 0, 0, 0, 0, blueDust);
                }
                curr.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, curr, 2, 0.1, 0.1, 0.1, 0.08);
                curr.getWorld().spawnParticle(Particle.DUST, curr, 2, 0.1, 0.1, 0.1, 0, whiteDust);

                for (LivingEntity e : curr.getWorld().getNearbyLivingEntities(curr, 1.0)) {
                    if (e.equals(player)) continue;
                    hit = true;

                    // 2 hearts total + CrescentEclipse mark
                    e.damage(4.0, player);
                    e.getPersistentDataContainer().set(crescentMarkKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                    // SawRay releases the MINI version of the orbital lightning bolt (0.5 damage)
                    triggerOrbitalLightning(e.getLocation(), player, true);

                    player.sendMessage(Component.text("✦ Antimatter Saw hit! Mini Orbital Bolt unleashed!", NamedTextColor.BLUE));
                    cancel();
                    return;
                }

                if (curr.getBlock().getType().isSolid()) {
                    curr.getWorld().spawnParticle(Particle.DUST, curr, 10, 0.3, 0.3, 0.3, 0, blueDust);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Orbital Lightning Bolt:
     * Full version (attack 5 times): does 1 damage (1.0 HP) to target and nearby entities.
     * Mini version (SawRay release): does half damage (0.5 HP) to target and nearby entities.
     * Protected by abilityDamageActive re-entrancy guard to prevent recursive onHit cascades!
     */
    public void triggerOrbitalLightning(Location targetLoc, Player attacker, boolean isMini) {
        double height = isMini ? 12.0 : 28.0;
        double damage = isMini ? 0.5 : 1.0;

        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, isMini ? 0.8f : 1.2f, isMini ? 1.4f : 1.0f);

        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), isMini ? 1.2f : 2.0f);
        Particle.DustOptions aquaDust = new Particle.DustOptions(Color.fromRGB(0, 220, 255), isMini ? 1.0f : 1.8f);

        // Vertical lightning pillar descending from the sky
        for (double y = 0; y <= height; y += 0.6) {
            Location p = targetLoc.clone().add(0, y, 0);
            p.getWorld().spawnParticle(Particle.DUST, p, isMini ? 1 : 2, 0.1, 0.05, 0.1, 0, whiteDust);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, isMini ? 1 : 2, 0.1, 0.1, 0.1, 0.02);
        }

        // Single ground impact shockwave & sonic boom
        targetLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, targetLoc, 1, 0, 0, 0, 0);
        double ringRadius = isMini ? 2.5 : 4.5;
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            targetLoc.getWorld().spawnParticle(Particle.DUST,
                    targetLoc.clone().add(Math.cos(rad) * ringRadius, 0.1, Math.sin(rad) * ringRadius),
                    1, 0, 0, 0, 0, aquaDust);
        }

        // Damage target and nearby with re-entrancy guard
        double range = isMini ? 3.0 : 5.0;
        try {
            abilityDamageActive.add(attacker.getUniqueId());
            for (LivingEntity e : targetLoc.getWorld().getNearbyLivingEntities(targetLoc, range)) {
                if (e.equals(attacker)) continue;
                e.damage(damage, attacker);
                e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1.0, 0), 4, 0.2, 0.2, 0.2, 0.05);
            }
        } finally {
            abilityDamageActive.remove(attacker.getUniqueId());
        }
    }

    public void onTridentThrowHit(Player thrower, LivingEntity target) {
        long now = System.currentTimeMillis();
        long lastSteal = lightStealCooldown.getOrDefault(thrower.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(thrower.getUniqueId(), now);
            plugin.getBossBarManager().showPassiveCooldown(thrower, this, "LightStealing", 60);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            thrower.sendMessage(Component.text("✦ LightStealing inflicted Darkness on target!", NamedTextColor.GRAY));
        }

        // Throw also triggers Orbital Lightning if charged!
        if (Boolean.TRUE.equals(orbitalReady.remove(thrower.getUniqueId()))) {
            triggerOrbitalLightning(target.getLocation(), thrower, false);
            thrower.sendMessage(Component.text("✦ Trident Throw unleashed Orbital Lightning Bolt!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Ignore recursive ability damage events
        if (abilityDamageActive.contains(attacker.getUniqueId())) return;
        onMeleeHit(attacker, target);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Bolt: Falling creates explosion based on fall distance (60s CD) — TWICE AS MUCH DAMAGE
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            long now = System.currentTimeMillis();
            long lastBolt = boltCooldown.getOrDefault(victim.getUniqueId(), 0L);

            if (now - lastBolt > 60000L) {
                boltCooldown.put(victim.getUniqueId(), now);
                plugin.getBossBarManager().showPassiveCooldown(victim, this, "Bolt", 60);
                float fallDist = victim.getFallDistance();

                if (fallDist > 3.0f) {
                    Location loc = victim.getLocation();
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 1.5f);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);

                    for (double y = 0; y <= 6.0; y += 0.5) {
                        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, y, 0), 6, 0.3, 0.1, 0.3, 0.05);
                    }
                    for (int d = 0; d < 360; d += 20) {
                        double rad = Math.toRadians(d);
                        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(Math.cos(rad) * 3.5, 0.2, Math.sin(rad) * 3.5), 2, 0, 0, 0, 0.05);
                    }

                    // "your slam do twice as much" -> doubled from before (max 12.0)
                    double dmg = Math.min(12.0, (2.0 + (fallDist * 0.4)) * 2.0);
                    for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 5.0)) {
                        if (e.equals(victim)) continue;
                        e.damage(dmg, victim);
                    }
                    victim.sendMessage(Component.text("✦ Bolt slam discharged from your fall! (" + String.format(java.util.Locale.US, "%.1f", dmg) + " damage)", NamedTextColor.YELLOW));
                }
            }
        }
    }
}
