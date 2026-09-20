package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
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
import org.bukkit.entity.WitherSkull;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Judas extends LegendaryWeapon {

    private final Map<UUID, Integer> skullCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> biteCooldown = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Judas(ChurchSMP plugin) {
        super(plugin,
                "judas",
                new String[]{"blade_of_judas", "dagger_of_betrayal"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#4A0000:#FF0000:#4A0000><bold>Judas</bold></gradient>"),
                Material.NETHERITE_AXE,
                Alignment.EVIL,
                "Hemorrhaged",
                "Discipline");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Bloodlust", "Unfree", "Bite"), "Hemorrhaged", "Discipline"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        int currentCharges = skullCharges.getOrDefault(player.getUniqueId(), 3);

        if (currentCharges <= 0) {
            if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;
            skullCharges.put(player.getUniqueId(), 3);
            currentCharges = 3;
        }

        Vector dir = player.getEyeLocation().getDirection().normalize().multiply(1.3);
        WitherSkull skull = player.launchProjectile(WitherSkull.class, dir);
        skull.setCharged(currentCharges == 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);

        // Blood particle burst at launch
        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.6f);
        player.getWorld().spawnParticle(Particle.DUST, player.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0, bloodDust);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getEyeLocation(), 3, 0.2, 0.2, 0.2, 0.05);

        // Trail behind the skull
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!skull.isValid() || skull.isDead()) {
                    cancel();
                    return;
                }
                skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 4, 0.1, 0.1, 0.1, 0, bloodDust);
                skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);

        currentCharges--;
        skullCharges.put(player.getUniqueId(), currentCharges);

        if (currentCharges <= 0) {
            int cd = plugin.getConfig().getInt("weapons.judas.primary_cooldown", 16);
            plugin.getCooldownManager().setCooldown(player, key, cd);
        } else {
            player.sendMessage(Component.text("✦ Hemorrhaged Skull Charges: " + currentCharges + "/3", NamedTextColor.DARK_RED));
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.judas.secondary_cooldown", 35);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.9f, 1.4f);
        player.swingMainHand();

        Location center = player.getLocation();
        int radius = 7;
        Map<org.bukkit.block.Block, org.bukkit.block.data.BlockData> changedBlocks = new java.util.HashMap<>();

        // Gather souls from nearby colorful / lifeful blocks
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (changedBlocks.size() >= 35) break;
                    if (x * x + z * z > radius * radius) continue;
                    org.bukkit.block.Block b = center.getBlock().getRelative(x, y, z);
                    Material mat = b.getType();
                    if (isLifefulBlock(mat)) {
                        changedBlocks.put(b, b.getBlockData().clone());
                        b.setType(Material.DEAD_BRAIN_CORAL_BLOCK, false);
                        // Visual particle from block to player
                        Location bLoc = b.getLocation().add(0.5, 0.5, 0.5);
                        Vector toPlayer = player.getLocation().add(0, 1, 0).toVector().subtract(bLoc.toVector()).normalize().multiply(0.4);
                        b.getWorld().spawnParticle(Particle.DUST, bLoc, 3, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.2f));
                        b.getWorld().spawnParticle(Particle.SOUL, bLoc, 1, toPlayer.getX(), toPlayer.getY(), toPlayer.getZ(), 0.05);
                    }
                }
            }
        }

        // Revert blocks after 6 seconds (120 ticks)
        if (!changedBlocks.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<org.bukkit.block.Block, org.bukkit.block.data.BlockData> entry : changedBlocks.entrySet()) {
                        org.bukkit.block.Block b = entry.getKey();
                        if (b.getType() == Material.DEAD_BRAIN_CORAL_BLOCK) {
                            b.setBlockData(entry.getValue(), false);
                        }
                    }
                }
            }.runTaskLater(plugin, 120L);
        }

        // Gather souls from nearby mobs/entities
        List<LivingEntity> nearby = player.getWorld().getNearbyLivingEntities(center, radius, e -> !e.equals(player)).stream().toList();
        for (LivingEntity e : nearby) {
            Location eLoc = e.getLocation().add(0, 1, 0);
            e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, eLoc, 3, 0.2, 0.2, 0.2, 0.05);
            e.getWorld().spawnParticle(Particle.DUST, eLoc, 5, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.4f));
        }

        int vitality = changedBlocks.size() + (nearby.size() * 3);
        // Scale damage between 1 heart (2.0 HP) and 4 hearts (8.0 HP)
        double shockwaveDamage = Math.min(8.0, 2.0 + (vitality * 0.2));

        // Animated expanding shockwave ring
        Particle.DustOptions darkRed = new Particle.DustOptions(Color.fromRGB(74, 0, 0), 2.2f);
        Particle.DustOptions brightRed = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
        new BukkitRunnable() {
            int step = 1;
            @Override
            public void run() {
                if (step > 6) {
                    cancel();
                    return;
                }
                double r = step * 1.3;
                for (int deg = 0; deg < 360; deg += 12) {
                    double rad = Math.toRadians(deg);
                    Location pLoc = center.clone().add(Math.cos(rad) * r, 0.2, Math.sin(rad) * r);
                    center.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, (step % 2 == 0) ? brightRed : darkRed);
                    if (step % 2 == 0) {
                        center.getWorld().spawnParticle(Particle.SMOKE, pLoc, 1, 0, 0.05, 0, 0.01);
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Damage enemies in 7-block radius
        for (LivingEntity e : nearby) {
            e.damage(shockwaveDamage, player);
            e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
            Vector knockback = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.7).setY(0.3);
            e.setVelocity(knockback);
        }

        double hearts = shockwaveDamage / 2.0;
        player.sendMessage(Component.text("✦ Discipline gathered " + vitality + " vitality! Released " + String.format(java.util.Locale.US, "%.1f", hearts) + " hearts shockwave!", NamedTextColor.DARK_RED));
        return true;
    }

    private boolean isLifefulBlock(Material mat) {
        String name = mat.name();
        return name.endsWith("_LEAVES") || name.endsWith("_FLOWERS") || name.endsWith("_SAPLING") ||
                name.contains("GRASS") || name.contains("FERN") || name.contains("VINE") ||
                name.contains("MOSS") || name.contains("AZALEA") || name.contains("CORAL") ||
                name.contains("CARPET") || name.contains("WOOL") || name.contains("CONCRETE") ||
                name.contains("TERRACOTTA") || mat == Material.SUGAR_CANE || mat == Material.BAMBOO ||
                mat == Material.LILY_PAD || mat == Material.WHEAT || mat == Material.CARROTS ||
                mat == Material.POTATOES || mat == Material.BEETROOTS || mat == Material.NETHER_WART;
    }

    public void triggerJudasPassives(Player attacker, LivingEntity target) {
        // Bite: 25% chance to trigger debuff hit (wither, nausea, blindness), 30s cooldown
        long now = System.currentTimeMillis();
        long lastBite = biteCooldown.getOrDefault(attacker.getUniqueId(), 0L);

        if (now - lastBite > 30000L && random.nextDouble() < 0.25) {
            biteCooldown.put(attacker.getUniqueId(), now);

            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));

            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FOX_BITE, 1.5f, 0.6f);

            // Bite fang visual: closing crimson V-shape
            Location targetEye = target.getLocation().add(0, 1.2, 0);
            Particle.DustOptions biteDust = new Particle.DustOptions(Color.fromRGB(200, 10, 10), 1.6f);
            for (double d = -0.6; d <= 0.6; d += 0.2) {
                targetEye.getWorld().spawnParticle(Particle.DUST, targetEye.clone().add(d, Math.abs(d) * 0.8, 0), 2, 0, 0, 0, 0, biteDust);
                targetEye.getWorld().spawnParticle(Particle.DUST, targetEye.clone().add(d, -Math.abs(d) * 0.8, 0), 2, 0, 0, 0, 0, biteDust);
            }
            targetEye.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, targetEye, 6, 0.2, 0.2, 0.2, 0.05);

            attacker.sendMessage(Component.text("✦ Bite triggered! Target inflicted with Wither, Nausea & Blindness.", NamedTextColor.DARK_RED));
        }

        // Unfree: Judas gift (random debuff on victim or small drawback on attacker)
        if (random.nextDouble() < 0.15) {
            PotionEffectType[] curses = new PotionEffectType[]{
                    PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS, PotionEffectType.DARKNESS
            };
            PotionEffectType curse = curses[random.nextInt(curses.length)];
            attacker.addPotionEffect(new PotionEffect(curse, 80, 0));
            // Dark curse swirl around attacker
            Location aLoc = attacker.getLocation().add(0, 1.0, 0);
            aLoc.getWorld().spawnParticle(Particle.SOUL, aLoc, 10, 0.3, 0.5, 0.3, 0.03);
            attacker.sendMessage(Component.text("✦ Unfree: You received a gift from Judas...", NamedTextColor.DARK_GRAY));
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        triggerJudasPassives(attacker, target);
    }
}
