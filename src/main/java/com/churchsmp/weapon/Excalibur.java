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

        // Altar Pining: Pulls everyone into you, celestial Excalibur descends, slam impact
        final Location altarCenter = player.getLocation().clone();
        altarCenter.getWorld().playSound(altarCenter, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.4f);
        altarCenter.getWorld().playSound(altarCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.8f);
        altarCenter.getWorld().playSound(altarCenter, Sound.ENTITY_EVOKER_CAST_SPELL, 1.4f, 0.8f);

        final Particle.DustOptions ringYellow = new Particle.DustOptions(Color.fromRGB(255, 220, 0), 1.8f);
        final Particle.DustOptions ringOrange = new Particle.DustOptions(Color.fromRGB(255, 130, 0), 1.8f);
        final Particle.DustOptions ringRed = new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.8f);
        final Particle.DustOptions bladeGold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.2f);
        final Particle.DustOptions bladeWhite = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f);

        // Player ascends into the air to command the celestial descent
        player.setVelocity(new Vector(0, 1.35, 0));
        player.setFallDistance(0);

        new BukkitRunnable() {
            int ticks = 0;
            boolean slammed = false;

            @Override
            public void run() {
                ticks++;

                // Phase 1: Vortex Suction & Altar Runes (ticks 1 - 15)
                if (ticks <= 15) {
                    // Concentric spinning ground runes at altarCenter
                    double spin = Math.toRadians(ticks * 16);
                    for (int deg = 0; deg < 360; deg += 20) {
                        double rad = Math.toRadians(deg) + spin;
                        altarCenter.getWorld().spawnParticle(Particle.DUST, altarCenter.clone().add(Math.cos(rad) * 1.8, 0.1, Math.sin(rad) * 1.8), 1, 0, 0, 0, 0, ringYellow);
                        altarCenter.getWorld().spawnParticle(Particle.DUST, altarCenter.clone().add(Math.cos(rad) * 3.2, 0.1, Math.sin(rad) * 3.2), 1, 0, 0, 0, 0, ringOrange);
                        altarCenter.getWorld().spawnParticle(Particle.DUST, altarCenter.clone().add(Math.cos(rad) * 4.8, 0.1, Math.sin(rad) * 4.8), 1, 0, 0, 0, 0, ringRed);
                    }

                    // Upward light beams at altar boundary
                    for (double y = 0; y <= 3.0; y += 0.6) {
                        altarCenter.getWorld().spawnParticle(Particle.WAX_ON, altarCenter.clone().add(0, y, 0), 2, 0.1, 0.05, 0.1, 0.02);
                        altarCenter.getWorld().spawnParticle(Particle.END_ROD, altarCenter.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0.01);
                    }

                    // Irresistible Suction Pull: continuously drag all entities within 12 blocks into altar center
                    for (LivingEntity e : altarCenter.getWorld().getNearbyLivingEntities(altarCenter, 12.0, 8.0, 12.0)) {
                        if (e.equals(player)) continue;
                        Vector toCenter = altarCenter.toVector().subtract(e.getLocation().toVector());
                        double dist = toCenter.length();
                        if (dist > 0.8) {
                            Vector pull = toCenter.normalize().multiply(Math.min(1.4, 0.45 + (dist * 0.12))).setY(0.22);
                            e.setVelocity(pull);

                            // Tether particle connecting entity to center
                            Location eLoc = e.getLocation().add(0, 1.0, 0);
                            eLoc.getWorld().spawnParticle(Particle.DUST, eLoc, 2, 0.1, 0.1, 0.1, 0, ringYellow);
                        }
                    }
                }

                // Phase 2: Celestial Excalibur Descends from the Heavens (ticks 6 - 16)
                if (ticks >= 6 && ticks <= 16) {
                    double swordY = Math.max(0.0, 18.0 - (ticks - 6) * 1.8);
                    Location swordTip = altarCenter.clone().add(0, swordY, 0);

                    // Blade vertical spine (7 blocks length)
                    for (double y = 0; y <= 7.0; y += 0.4) {
                        Location p = swordTip.clone().add(0, y, 0);
                        p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.03, 0.03, 0.03, 0.01);
                        p.getWorld().spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0, (y > 4.5) ? bladeGold : bladeWhite);
                    }

                    // Crossguard at y = 5.0 (3 blocks wide)
                    for (double w = -1.5; w <= 1.5; w += 0.3) {
                        swordTip.getWorld().spawnParticle(Particle.DUST, swordTip.clone().add(w, 5.0, 0), 1, 0, 0, 0, 0, ringYellow);
                        swordTip.getWorld().spawnParticle(Particle.DUST, swordTip.clone().add(0, 5.0, w), 1, 0, 0, 0, 0, ringYellow);
                    }

                    // Sound of descending celestial blade
                    altarCenter.getWorld().playSound(swordTip, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 0.6f + (ticks * 0.05f));
                }

                // Phase 3: Player Plunge Dive (tick 13)
                if (ticks == 13 && !slammed) {
                    Vector dive = altarCenter.toVector().subtract(player.getLocation().toVector()).setY(0);
                    if (dive.length() > 0.1) dive.normalize().multiply(0.8);
                    dive.setY(-3.5);
                    player.setVelocity(dive);
                    player.setFallDistance(0);
                }

                // Phase 4: Divine Slam Impact (tick 16 or upon hitting ground)
                if (!slammed && (ticks >= 16 || (ticks > 13 && player.isOnGround()))) {
                    slammed = true;
                    player.setFallDistance(0);

                    // Celestial Excalibur & Player slam the ground
                    altarCenter.getWorld().playSound(altarCenter, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.75f);
                    altarCenter.getWorld().playSound(altarCenter, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
                    altarCenter.getWorld().playSound(altarCenter, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.1f);
                    altarCenter.getWorld().playSound(altarCenter, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 1.2f);

                    altarCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, altarCenter, 3);
                    altarCenter.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, altarCenter, 160, 3.5, 0.8, 3.5, 0.35);

                    // Rising holy light pillars
                    for (double y = 0; y <= 6.0; y += 0.5) {
                        altarCenter.getWorld().spawnParticle(Particle.END_ROD, altarCenter.clone().add(0, y, 0), 3, 0.2, 0.1, 0.2, 0.02);
                        altarCenter.getWorld().spawnParticle(Particle.DUST, altarCenter.clone().add(0, y, 0), 3, 0.15, 0.15, 0.15, 0, bladeGold);
                    }

                    // Multi-wave expanding ground shockwave rings (waves at 2.5, 5.0, 7.5, 10.0 blocks)
                    for (int wave = 1; wave <= 4; wave++) {
                        final double waveRadius = wave * 2.5;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                for (int d = 0; d < 360; d += 12) {
                                    double rad = Math.toRadians(d);
                                    altarCenter.getWorld().spawnParticle(Particle.DUST,
                                            altarCenter.clone().add(Math.cos(rad) * waveRadius, 0.15, Math.sin(rad) * waveRadius),
                                            1, 0, 0, 0, 0, ringYellow);
                                }
                            }
                        }.runTaskLater(plugin, wave * 2L);
                    }

                    // Slam damage & 3-second pin/stun on all caught enemies
                    for (LivingEntity e : altarCenter.getWorld().getNearbyLivingEntities(altarCenter, 9.0, 5.0, 9.0)) {
                        if (e.equals(player)) continue;
                        applyTrueDamage(e, 4.0, player); // 2 hearts true damage (4 HP)
                        // Stun for 3 seconds (Slowness 255 + Jump boost 128 completely immobilizes)
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 255, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 128, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                        e.setVelocity(new Vector(0, -0.6, 0)); // Pin firmly to ground
                    }

                    player.sendMessage(Component.text("✦ Excalibur impales the altar! Caught enemies pinned & stunned!", NamedTextColor.GOLD));
                    cancel();
                }

                if (ticks > 40) {
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
