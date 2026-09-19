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

    private final Map<UUID, Integer> blinkCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boltCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lightStealCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> crescentEclipseQueued = new ConcurrentHashMap<>();

    public LuminescenceSpear(ChurchSMP plugin) {
        super(plugin,
                "luminescence_spear",
                new String[]{"sword_of_david"},
                Component.text("Luminescence Spear", TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD),
                Material.TRIDENT,
                Alignment.GOOD,
                "Blink",
                "CrescentEclipse");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));
            lore.add(Component.text("As so the shadow fall behinds.", TextColor.color(0xFFE4B5)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("âœ¦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" â€¢ Bolt: ", NamedTextColor.YELLOW).append(Component.text("Falling creates an explosion scaling with velocity (like mace). (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" â€¢ LightStealing: ", NamedTextColor.YELLOW).append(Component.text("Throwing inflicts Darkness for 10s. (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" â€¢ BurningBones: ", NamedTextColor.YELLOW).append(Component.text("Sword attack speed (1.6) with trident impact power.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Blink: ", NamedTextColor.AQUA).append(Component.text("Dash 6 blocks (3/3 charges); foes caught take 1 heart & cannot throw projectiles for 8s.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] CrescentEclipse: ", NamedTextColor.AQUA).append(Component.text("Next throw marks target for stun + 4-heart orbital beam after 2s; ground shockwave stuns for 2s with Darkness for 4s. (130s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // BurningBones: attack speed of sword (1.6)
            NamespacedKey speedKey = new NamespacedKey(plugin, "spear_speed");
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        int currentCharges = blinkCharges.getOrDefault(player.getUniqueId(), 3);

        if (currentCharges <= 0) {
            if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;
            blinkCharges.put(player.getUniqueId(), 3);
            currentCharges = 3;
        }

        // Dash 6 blocks forward
        Vector dir = player.getLocation().getDirection().normalize();
        Location start = player.getLocation();
        Location end = start.clone().add(dir.clone().multiply(6.0));

        player.teleport(end);
        player.playSound(end, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.4f);
        player.playSound(start, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.8f);

        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

        // Ground circles at start position (inner circle first, then outer ring)
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 1.0, 0.1, Math.sin(rad) * 1.0), 1, 0, 0, 0, 0, whiteDust);
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(Math.cos(rad) * 2.2, 0.1, Math.sin(rad) * 2.2), 1, 0, 0, 0, 0, goldDust);
        }

        // Ground circle at destination where player lands
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            end.getWorld().spawnParticle(Particle.DUST, end.clone().add(Math.cos(rad) * 1.2, 0.1, Math.sin(rad) * 1.2), 1, 0, 0, 0, 0, goldDust);
        }

        // Wavy electric / lightning trail between start and end (matching user drawing)
        Vector up = new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(up).normalize();
        for (double d = 0; d <= 6.0; d += 0.25) {
            double wave = Math.sin(d * 2.5) * 0.45;
            Location p = start.clone().add(dir.clone().multiply(d)).add(right.clone().multiply(wave)).add(0, 0.9 + Math.cos(d * 2.5) * 0.2, 0);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 3, 0.05, 0.05, 0.05, 0.02);
            p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.01);

            for (LivingEntity victim : p.getWorld().getNearbyLivingEntities(p, 1.5)) {
                if (victim.equals(player)) continue;
                victim.damage(2.0, player); // 1 heart
                // Cannot throw projectiles for 8s (Slowness/Weakness)
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 160, 2));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 1));

                // Particle circle at victim's feet (matching drawing)
                Location vFeet = victim.getLocation();
                for (int vd = 0; vd < 360; vd += 30) {
                    double vrad = Math.toRadians(vd);
                    vFeet.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, vFeet.clone().add(Math.cos(vrad) * 0.9, 0.1, Math.sin(vrad) * 0.9), 1, 0, 0, 0, 0);
                }
            }
        }

        currentCharges--;
        blinkCharges.put(player.getUniqueId(), currentCharges);

        if (currentCharges <= 0) {
            int cd = plugin.getConfig().getInt("weapons.luminescence_spear.primary_cooldown", 12);
            plugin.getCooldownManager().setCooldown(player, key, cd);
        } else {
            player.sendMessage(Component.text("✦ Blink Charges: " + currentCharges + "/3", NamedTextColor.YELLOW));
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.secondary_cooldown", 130);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "CrescentEclipse Armed", BossBar.Color.YELLOW, 10);

        crescentEclipseQueued.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        player.sendMessage(Component.text("✦ CrescentEclipse armed! Your next throw will mark the target for an orbital celestial beam.", NamedTextColor.GOLD));
        return true;
    }

    public void onTridentThrowHit(Player thrower, LivingEntity target) {
        long now = System.currentTimeMillis();

        // LightStealing: Throwing inflicts entity with Darkness for 10s (60s CD)
        long lastSteal = lightStealCooldown.getOrDefault(thrower.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(thrower.getUniqueId(), now);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            thrower.sendMessage(Component.text("✦ LightStealing inflicted Darkness on target!", NamedTextColor.GRAY));
        }

        // CrescentEclipse trigger on throw
        if (Boolean.TRUE.equals(crescentEclipseQueued.remove(thrower.getUniqueId()))) {
            triggerCrescentEclipseBeam(thrower, target);
        }
    }

    private void triggerCrescentEclipseBeam(Player thrower, LivingEntity target) {
        target.sendMessage(Component.text("⚔ You are locked by CrescentEclipse! Stunned!", NamedTextColor.GOLD));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // Stun

        Location targetLoc = target.getLocation();
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f);
        Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(30, 144, 255), 1.8f);

        // Ground triangle runic glyph under feet (matching drawing)
        for (int i = 0; i < 3; i++) {
            double angle1 = i * (2 * Math.PI / 3);
            double angle2 = (i + 1) * (2 * Math.PI / 3);
            Location p1 = targetLoc.clone().add(Math.cos(angle1) * 2.0, 0.1, Math.sin(angle1) * 2.0);
            Location p2 = targetLoc.clone().add(Math.cos(angle2) * 2.0, 0.1, Math.sin(angle2) * 2.0);
            Vector edge = p2.toVector().subtract(p1.toVector());
            for (double step = 0; step <= 1.0; step += 0.15) {
                targetLoc.getWorld().spawnParticle(Particle.DUST, p1.clone().add(edge.clone().multiply(step)), 1, 0, 0, 0, 0, whiteDust);
            }
        }

        // Inner circle starts first at feet (matching drawing)
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            targetLoc.getWorld().spawnParticle(Particle.DUST, targetLoc.clone().add(Math.cos(rad) * 1.2, 0.1, Math.sin(rad) * 1.2), 1, 0, 0, 0, 0, whiteDust);
        }

        // 2s delay -> 4 hearts beam with Sonic Beam + spiraling blue rings wrapping around
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid()) return;

                Location loc = target.getLocation();
                loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.9f);
                loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.2f);

                // Sonic Boom burst at ground
                loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.5, 0), 1);
                loc.getWorld().spawnParticle(Particle.FLASH, loc.clone().add(0, 1.0, 0), 3);

                // Massive vertical beam column with blue spiral wrapping around it (matching drawing)
                for (double y = 0; y < 30; y += 0.4) {
                    // Center column of light
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, y, 0), 3, 0.15, 0.1, 0.15, 0.01);

                    // Blue spiral rings wrapping around the beam column
                    double spiralAngle = y * 0.75;
                    double r = 1.3;
                    Location blueP = loc.clone().add(Math.cos(spiralAngle) * r, y, Math.sin(spiralAngle) * r);
                    blueP.getWorld().spawnParticle(Particle.DUST, blueP, 2, 0, 0, 0, 0, blueDust);
                    blueP.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, blueP, 1, 0.02, 0.02, 0.02, 0.01);
                }

                // 4 hearts damage (8 HP)
                target.damage(8.0, thrower);

                // Expanding ground shockwave rings (animated outward over 3 ticks)
                for (int wave = 1; wave <= 3; wave++) {
                    final double waveRadius = wave * 2.2;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (int deg = 0; deg < 360; deg += 15) {
                                double rad = Math.toRadians(deg);
                                loc.getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(Math.cos(rad) * waveRadius, 0.15, Math.sin(rad) * waveRadius), 1);
                                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(rad) * waveRadius, 0.15, Math.sin(rad) * waveRadius), 1, 0, 0, 0, 0, whiteDust);
                            }
                        }
                    }.runTaskLater(plugin, wave * 2L);
                }

                // Stun 2s with Darkness for 4s
                for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 5.0)) {
                    if (e.equals(thrower)) continue;
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Bolt: Falling creates explosion based on fall distance / velocity (60s CD)
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            long now = System.currentTimeMillis();
            long lastBolt = boltCooldown.getOrDefault(victim.getUniqueId(), 0L);

            if (now - lastBolt > 60000L) {
                boltCooldown.put(victim.getUniqueId(), now);
                float fallDist = victim.getFallDistance();

                if (fallDist > 3.0f) {
                    Location loc = victim.getLocation();
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 1.5f);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);

                    // Lightning sparks column + electric ring at ground
                    for (double y = 0; y <= 6.0; y += 0.5) {
                        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, y, 0), 6, 0.3, 0.1, 0.3, 0.05);
                    }
                    for (int d = 0; d < 360; d += 20) {
                        double rad = Math.toRadians(d);
                        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(Math.cos(rad) * 3.5, 0.2, Math.sin(rad) * 3.5), 2, 0, 0, 0, 0.05);
                    }

                    double dmg = Math.min(20.0, fallDist * 1.5);
                    for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 5.0)) {
                        if (e.equals(victim)) continue;
                        e.damage(dmg, victim);
                    }
                    victim.sendMessage(Component.text("âœ¦ Bolt slam discharged from your fall! (" + String.format(java.util.Locale.US, "%.1f", dmg) + " damage)", NamedTextColor.YELLOW));
                }
            }
        }
    }
}
