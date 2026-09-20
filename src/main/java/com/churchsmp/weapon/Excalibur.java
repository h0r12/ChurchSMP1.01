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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
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

public class Excalibur extends LegendaryWeapon {

    // Tracks ongoing charging for Acceleration Nova: UUID -> BukkitRunnable
    private final Map<UUID, BukkitRunnable> chargingTasks = new ConcurrentHashMap<>();

    public Excalibur(ChurchSMP plugin) {
        super(plugin,
                "excalibur",
                new String[]{"blade_of_the_archangel"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#FFFFFF:#FFD700:#55FFFF><bold>Excalibur</bold></gradient>"),
                Material.NETHERITE_SWORD,
                Alignment.GOOD,
                "Acceleration Nova",
                "Altar Pining");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Hopeful", "Wings"), "Acceleration Nova", "Altar Pining"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Check if already charging
        if (chargingTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("✦ You are already charging Acceleration Nova!", NamedTextColor.YELLOW));
            return false;
        }

        player.sendMessage(Component.text("✦ Charging Acceleration Nova (10s)... Keep steady!", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.2f, 1.5f);
        plugin.getBossBarManager().showActiveCountdown(player, "Acceleration Nova Charging", BossBar.Color.BLUE, 10);

        // Grant Resistance II while charging
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 210, 1, false, false));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getInventory().getItemInMainHand().getType() != baseMaterial) {
                    chargingTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Charging particles: rotating aura rings around waist & body (matching design)
                Location center = player.getLocation().add(0, 0.9, 0);
                double baseAngle = (ticks * 0.25);
                double radius = 0.9 + (ticks / 200.0) * 0.5; // Expands slightly as power builds

                Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.4f);
                Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f);

                for (int i = 0; i < 4; i++) {
                    double a = baseAngle + (i * Math.PI / 2.0);
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, 0, z), 1, 0, 0, 0, 0, goldDust);
                    center.getWorld().spawnParticle(Particle.DUST, center.clone().add(-x, 0.3 * Math.sin(a), -z), 1, 0, 0, 0, 0, whiteDust);
                }
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 0.2, 0), 1, 0.2, 0.2, 0.2, 0.02);
                center.getWorld().spawnParticle(Particle.END_ROD, center, 2, 0.3, 0.3, 0.3, 0.03);

                ticks += 5;
                if (ticks >= 200) { // 10 seconds reached
                    chargingTasks.remove(player.getUniqueId());

                    // Fire Acceleration Nova blast: Triple braided intertwined beam (Gold + White + Blue)
                    Location start = player.getEyeLocation();
                    Vector dir = start.getDirection().normalize();
                    player.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.0f);
                    player.getWorld().spawnParticle(Particle.SONIC_BOOM, start.clone().add(dir.clone().multiply(1.5)), 1);

                    // Compute perpendicular vectors for the spiral helix
                    Vector up = new Vector(0, 1, 0);
                    if (Math.abs(dir.getY()) > 0.95) up = new Vector(1, 0, 0);
                    Vector right = dir.clone().crossProduct(up).normalize();
                    Vector orthoUp = right.clone().crossProduct(dir).normalize();

                    Particle.DustOptions blastGold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f);
                    Particle.DustOptions blastWhite = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f);

                    java.util.Set<UUID> damagedEntities = new java.util.HashSet<>();

                    for (int step = 1; step <= 22; step++) {
                        Location centerP = start.clone().add(dir.clone().multiply(step));
                        double theta = step * 0.7;
                        double r = 0.65;

                        // Stream 1: Gold spiral
                        Vector off1 = right.clone().multiply(Math.cos(theta) * r).add(orthoUp.clone().multiply(Math.sin(theta) * r));
                        centerP.getWorld().spawnParticle(Particle.DUST, centerP.clone().add(off1), 3, 0.05, 0.05, 0.05, 0, blastGold);

                        // Stream 2: White spiral (offset by 2*PI/3)
                        Vector off2 = right.clone().multiply(Math.cos(theta + 2.094) * r).add(orthoUp.clone().multiply(Math.sin(theta + 2.094) * r));
                        centerP.getWorld().spawnParticle(Particle.DUST, centerP.clone().add(off2), 2, 0.05, 0.05, 0.05, 0, blastWhite);
                        centerP.getWorld().spawnParticle(Particle.END_ROD, centerP.clone().add(off2), 1, 0.02, 0.02, 0.02, 0.01);

                        // Stream 3: Blue Soul Flame spiral (offset by 4*PI/3)
                        Vector off3 = right.clone().multiply(Math.cos(theta + 4.188) * r).add(orthoUp.clone().multiply(Math.sin(theta + 4.188) * r));
                        centerP.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, centerP.clone().add(off3), 2, 0.03, 0.03, 0.03, 0.02);

                        // Core critical spark
                        centerP.getWorld().spawnParticle(Particle.CRIT, centerP, 3, 0.15, 0.15, 0.15, 0.05);

                        for (LivingEntity e : centerP.getWorld().getNearbyLivingEntities(centerP, 2.8)) {
                            if (e.equals(player) || damagedEntities.contains(e.getUniqueId())) continue;
                            damagedEntities.add(e.getUniqueId());
                            applyTrueDamage(e, 5.0, player); // 2.5 true damage (5 HP) once!
                            e.setVelocity(dir.clone().multiply(1.5).setY(0.4));
                        }
                    }

                    // 45s CD
                    int cd = plugin.getConfig().getInt("weapons.excalibur.primary_cooldown", 45);
                    plugin.getCooldownManager().setCooldown(player, key, cd);
                    cancel();
                }
            }
        };

        chargingTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L);
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.excalibur.secondary_cooldown", 80);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Altar Pining", BossBar.Color.YELLOW, 4);

        // Altar Pining: Pulls everyone into you, Excalibur ascends, slams down
        Location feet = player.getLocation();
        feet.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.6f);
        feet.getWorld().playSound(feet, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.4f);

        Particle.DustOptions ringYellow = new Particle.DustOptions(Color.fromRGB(255, 220, 0), 1.8f);
        Particle.DustOptions ringOrange = new Particle.DustOptions(Color.fromRGB(255, 130, 0), 1.8f);
        Particle.DustOptions ringRed = new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.8f);

        // Concentric rings at feet
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(Math.cos(rad) * 1.5, 0.1, Math.sin(rad) * 1.5), 1, 0, 0, 0, 0, ringYellow);
        }
        for (int deg = 0; deg < 360; deg += 12) {
            double rad = Math.toRadians(deg);
            feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(Math.cos(rad) * 3.0, 0.1, Math.sin(rad) * 3.0), 1, 0, 0, 0, 0, ringOrange);
        }
        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(Math.cos(rad) * 4.5, 0.1, Math.sin(rad) * 4.5), 1, 0, 0, 0, 0, ringRed);
        }

        // Particle sword ascending aura
        for (double y = 0; y <= 3.5; y += 0.3) {
            feet.getWorld().spawnParticle(Particle.WAX_ON, feet.clone().add(0, y, 0), 3, 0.1, 0.05, 0.1, 0.02);
            feet.getWorld().spawnParticle(Particle.END_ROD, feet.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0.01);
        }

        // Pull nearby entities in 10-block radius in towards the player
        for (LivingEntity e : feet.getWorld().getNearbyLivingEntities(feet, 10.0, 6.0, 10.0)) {
            if (e.equals(player)) continue;
            Vector pull = feet.toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.3).setY(0.35);
            e.setVelocity(pull);
        }

        // Player ascends slightly to prepare descend
        player.setVelocity(new Vector(0, 1.4, 0));

        // Descend & Slam impact after 16 ticks
        new BukkitRunnable() {
            int ticks = 0;
            boolean slammed = false;

            @Override
            public void run() {
                ticks++;
                if (ticks == 15 && !slammed) {
                    player.setVelocity(new Vector(0, -3.0, 0));
                    slammed = true;
                }

                if (slammed && (player.isOnGround() || ticks >= 35)) {
                    Location impact = player.getLocation();
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.8f);
                    impact.getWorld().playSound(impact, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.7f);
                    impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 2);
                    impact.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, impact, 120, 3.5, 0.6, 3.5, 0.3);

                    // Expanding ground shockwave rings (animated outward over 3 ticks)
                    for (int wave = 1; wave <= 3; wave++) {
                        final double waveRadius = wave * 2.5;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                for (int d = 0; d < 360; d += 15) {
                                    double rad = Math.toRadians(d);
                                    impact.getWorld().spawnParticle(Particle.DUST,
                                            impact.clone().add(Math.cos(rad) * waveRadius, 0.15, Math.sin(rad) * waveRadius),
                                            1, 0, 0, 0, 0, ringYellow);
                                }
                            }
                        }.runTaskLater(plugin, wave * 2L);
                    }

                    for (LivingEntity e : impact.getWorld().getNearbyLivingEntities(impact, 8.0, 4.0, 8.0)) {
                        if (e.equals(player)) continue;
                        applyTrueDamage(e, 4.0, player); // 2 true damage (4 HP)
                        // Stun for 3 seconds (Slowness 255 + Jump boost 128)
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 255, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 128, false, false));
                        e.setVelocity(new Vector(0, 0, 0));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Hopeful: Every attack gives entity glowing; color based on alignment
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
        Alignment al = plugin.getAlignmentManager().getAlignment(attacker);
        Particle particle = switch (al) {
            case GOOD -> Particle.GLOW;
            case EVIL -> Particle.SOUL_FIRE_FLAME;
            case NULLIFIED -> Particle.SMOKE;
        };
        Location hitLoc = target.getLocation().add(0, 1, 0);
        hitLoc.getWorld().spawnParticle(particle, hitLoc, 20, 0.4, 0.5, 0.4, 0.08);

        // Alignment aura ring at victim's feet
        Location feet = target.getLocation();
        for (int d = 0; d < 360; d += 30) {
            double rad = Math.toRadians(d);
            feet.getWorld().spawnParticle(particle, feet.clone().add(Math.cos(rad) * 0.9, 0.1, Math.sin(rad) * 0.9), 1, 0, 0, 0, 0.02);
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Wings: Complete immunity to fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            Location loc = victim.getLocation();
            victim.getWorld().playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.2f, 1.2f);
            victim.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.5, 0.2, 0.5, 0.05);
            victim.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 10, 0.4, 0.3, 0.4, 0.05);

            // Wing arc shapes behind player
            Vector dir = loc.getDirection().setY(0).normalize();
            Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
            for (double s = 0.3; s <= 1.5; s += 0.3) {
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(right.clone().multiply(s)).add(dir.clone().multiply(-0.4)).add(0, 0.8 + s * 0.4, 0), 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(right.clone().multiply(-s)).add(dir.clone().multiply(-0.4)).add(0, 0.8 + s * 0.4, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    private void applyTrueDamage(LivingEntity entity, double amount, Player source) {
        double newHp = entity.getHealth() - amount;
        if (newHp <= 0) {
            entity.setHealth(0);
            entity.damage(1.0, source); // trigger kill credit
        } else {
            entity.setHealth(newHp);
            entity.damage(0.01, source);
        }
    }
}
