package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuminescenceSpear extends LegendaryWeapon {

    // Dash has 1 charge (simple cooldown), no charge counter needed
    private final Map<UUID, Long> boltCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lightStealCooldown = new ConcurrentHashMap<>();

    // SawRay: marks on a specific target, UUID of attacker -> UUID of marked target + count
    private final Map<UUID, UUID> markedTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> markCount = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sawReady = new ConcurrentHashMap<>();
    private final Map<UUID, Item> chargedSaw = new ConcurrentHashMap<>();

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
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Bolt", "LightStealing", "BurningBones"), "Dash", "SawRay"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            meta.addEnchant(Enchantment.LOYALTY, 3, true);

            // BurningBones: attack speed of sword (1.6)
            NamespacedKey speedKey = new NamespacedKey(plugin, "spear_speed");
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.setCustomModelData(1002);

            item.setItemMeta(meta);
        }
        return item;
    }

    // ─── PRIMARY: Dash (1 charge, 12s cooldown) ─────────────────────────────────
    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.primary_cooldown", 12);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        // Dash 6 blocks forward
        Vector dir = player.getLocation().getDirection().normalize();
        Location start = player.getLocation().clone();
        Location end = start.clone().add(dir.clone().multiply(6.0));

        // Check path for collisions and move player via velocity (smooth dash, not teleport)
        player.setVelocity(dir.clone().multiply(2.8).setY(0.25));
        player.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.4f);
        player.playSound(start, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.8f);

        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

        // Ground ring at start
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 1.0, 0.1, Math.sin(rad) * 1.0), 1, 0, 0, 0, 0, whiteDust);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 2.2, 0.1, Math.sin(rad) * 2.2), 1, 0, 0, 0, 0, goldDust);
        }

        // Wavy electric trail + deal 2 hearts (4 HP) damage to entities in path
        Vector up = new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(up).normalize();
        for (double dist = 0; dist <= 6.0; dist += 0.25) {
            double wave = Math.sin(dist * 2.5) * 0.45;
            Location p = start.clone().add(dir.clone().multiply(dist)).add(right.clone().multiply(wave)).add(0, 0.9 + Math.cos(dist * 2.5) * 0.2, 0);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 3, 0.05, 0.05, 0.05, 0.02);
            p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.01);

            for (LivingEntity victim : p.getWorld().getNearbyLivingEntities(p, 1.5)) {
                if (victim.equals(player)) continue;
                // Deal 2 hearts (4 HP) dash impact damage
                victim.damage(4.0, player);
                // Mark with CrescentEclipse mark for SawRay
                victim.getPersistentDataContainer().set(crescentMarkKey, PersistentDataType.STRING, player.getUniqueId().toString());
                victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false));

                // Apply disable effects
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));

                // Foot circle at victim
                Location vFeet = victim.getLocation();
                for (int vd = 0; vd < 360; vd += 30) {
                    double vrad = Math.toRadians(vd);
                    vFeet.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, vFeet.clone().add(Math.cos(vrad) * 0.9, 0.1, Math.sin(vrad) * 0.9), 1, 0, 0, 0, 0);
                }

                player.sendMessage(Component.text("✦ Dash hit! Target marked with CrescentEclipse.", NamedTextColor.YELLOW));
            }
        }

        player.sendMessage(Component.text("✦ Dash! (12s cooldown)", NamedTextColor.AQUA));
        return true;
    }

    // ─── SECONDARY: SawRay ───────────────────────────────────────────────────────
    // F-key activates SawRay mode — melee attacks on marked targets add marks;
    // after 3 marks a charged Antimatter Saw spawns in front of player.
    // Press F again to fire the saw.
    @Override
    public boolean executeSecondary(Player player) {
        // If saw is ready and waiting, fire it
        if (Boolean.TRUE.equals(sawReady.get(player.getUniqueId()))) {
            fireSaw(player);
            return true;
        }

        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Activate SawRay mode — marks will now accumulate on next melee attacks
        plugin.getBossBarManager().showActiveCountdown(player, "SawRay — Mark target 3x", BossBar.Color.BLUE, 15);
        plugin.getCooldownManager().setActiveDuration(player, key, 15);
        plugin.getCooldownManager().setCooldown(player, key, plugin.getConfig().getInt("weapons.luminescence_spear.secondary_cooldown", 130));

        markCount.put(player.getUniqueId(), 0);
        markedTarget.remove(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        player.sendMessage(Component.text("✦ SawRay — Attack a marked target 3 times to charge the Antimatter Saw!", NamedTextColor.GOLD));
        return true;
    }

    // Called by CombatListener when player hits with this weapon
    public void onMeleeHit(Player attacker, LivingEntity target) {
        long now = System.currentTimeMillis();
        String key = id + "_secondary";

        // LightStealing: Throwing inflicts Darkness (60s CD)
        long lastSteal = lightStealCooldown.getOrDefault(attacker.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(attacker.getUniqueId(), now);
            plugin.getBossBarManager().showPassiveCooldown(attacker, this, "LightStealing", 60);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            attacker.sendMessage(Component.text("✦ LightStealing inflicted Darkness!", NamedTextColor.GRAY));
        }

        // SawRay mark accumulation (only if SawRay is active)
        if (!plugin.getCooldownManager().isActive(attacker, key)) return;
        if (sawReady.getOrDefault(attacker.getUniqueId(), false)) return;

        // Check if target is marked
        String markerStr = target.getPersistentDataContainer().get(crescentMarkKey, PersistentDataType.STRING);
        if (markerStr == null || !markerStr.equals(attacker.getUniqueId().toString())) return;

        // Accumulate marks
        int marks = markCount.getOrDefault(attacker.getUniqueId(), 0) + 1;
        markCount.put(attacker.getUniqueId(), marks);

        // Visual mark indicator
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

    // Spawns the Antimatter Saw in front of the player
    private void chargeSaw(Player player) {
        sawReady.put(player.getUniqueId(), true);
        Location sawLoc = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(1.5));

        // Saw item entity (spinning golden sword as placeholder)
        ItemStack sawItem = new ItemStack(Material.GOLDEN_SWORD);
        Item saw = player.getWorld().dropItem(sawLoc, sawItem);
        saw.setPickupDelay(Integer.MAX_VALUE);
        saw.setCanMobPickup(false);
        saw.setGravity(false);
        saw.setVelocity(new Vector(0, 0, 0));
        chargedSaw.put(player.getUniqueId(), saw);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 2.0f);
        plugin.getBossBarManager().showActiveCountdown(player, "✦ Antimatter Saw — Press F to fire!", BossBar.Color.YELLOW, 8);

        // Spinning particle effect around saw
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                if (!saw.isValid() || !player.isOnline() || t > 160 || !sawReady.getOrDefault(player.getUniqueId(), false)) {
                    saw.remove();
                    sawReady.remove(player.getUniqueId());
                    chargedSaw.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                Location loc = saw.getLocation();
                Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(0, 100, 255), 1.5f);
                Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);
                for (int i = 0; i < 3; i++) {
                    double angle = Math.toRadians(t * 20 + i * 120);
                    double r = 0.6;
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r), 1, 0, 0, 0, 0, blueDust);
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(angle + Math.PI) * r * 0.5, 0.2, Math.sin(angle + Math.PI) * r * 0.5), 1, 0, 0, 0, 0, whiteDust);
                }
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage(Component.text("✦ Antimatter Saw charged! Press F to fire!", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
    }

    // Fires the saw forward
    private void fireSaw(Player player) {
        sawReady.remove(player.getUniqueId());
        Item saw = chargedSaw.remove(player.getUniqueId());
        if (saw != null && saw.isValid()) {
            saw.remove();
        }

        Location fireLoc = player.getEyeLocation().clone();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        player.playSound(fireLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.4f);
        player.playSound(fireLoc, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.8f);

        Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(0, 100, 255), 1.5f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

        // Projectile simulation — travels 20 blocks forward, damages everything in path
        new BukkitRunnable() {
            double dist = 0;
            boolean hit = false;

            @Override
            public void run() {
                if (hit || dist > 20.0) {
                    cancel();
                    return;
                }
                dist += 0.6;
                Location curr = fireLoc.clone().add(dir.clone().multiply(dist));

                // Spinning saw particle trail
                for (int i = 0; i < 3; i++) {
                    double angle = Math.toRadians(dist * 45 + i * 120);
                    curr.getWorld().spawnParticle(Particle.DUST, curr.clone().add(Math.cos(angle) * 0.5, Math.sin(angle) * 0.5, 0), 1, 0, 0, 0, 0, blueDust);
                }
                curr.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, curr, 2, 0.1, 0.1, 0.1, 0.08);
                curr.getWorld().spawnParticle(Particle.DUST, curr, 2, 0.1, 0.1, 0.1, 0, whiteDust);

                // Hit check
                for (LivingEntity e : curr.getWorld().getNearbyLivingEntities(curr, 1.0)) {
                    if (e.equals(player)) continue;
                    hit = true;

                    // 2 hearts (4 HP) total
                    e.damage(4.0, player);
                    // Apply CrescentEclipse mark
                    e.getPersistentDataContainer().set(crescentMarkKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                    // Impact burst
                    curr.getWorld().playSound(curr, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);
                    curr.getWorld().spawnParticle(Particle.FLASH, curr, 1, Color.WHITE);
                    curr.getWorld().spawnParticle(Particle.SONIC_BOOM, curr, 1);
                    for (int d = 0; d < 360; d += 20) {
                        double rad = Math.toRadians(d);
                        curr.getWorld().spawnParticle(Particle.DUST, curr.clone().add(Math.cos(rad) * 1.5, 0.1, Math.sin(rad) * 1.5), 1, 0, 0, 0, 0, blueDust);
                    }
                    player.sendMessage(Component.text("✦ Antimatter Saw hit! CrescentEclipse mark applied.", NamedTextColor.BLUE));
                    cancel();
                    return;
                }

                // Wall check
                if (curr.getBlock().getType().isSolid()) {
                    curr.getWorld().spawnParticle(Particle.DUST, curr, 10, 0.3, 0.3, 0.3, 0, blueDust);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // Called from CombatListener when trident projectile hits (legacy LightStealing hook)
    public void onTridentThrowHit(Player thrower, LivingEntity target) {
        long now = System.currentTimeMillis();
        long lastSteal = lightStealCooldown.getOrDefault(thrower.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(thrower.getUniqueId(), now);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            thrower.sendMessage(Component.text("✦ LightStealing inflicted Darkness on target!", NamedTextColor.GRAY));
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Delegate to melee hit handler for SawRay mark accumulation
        onMeleeHit(attacker, target);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Bolt: Falling creates explosion based on fall distance (60s CD)
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

                    double dmg = Math.min(6.0, 2.0 + (fallDist * 0.4));
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
