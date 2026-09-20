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
import org.bukkit.inventory.meta.Damageable;
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

public class Mayim extends LegendaryWeapon {

    // Cold streak: UUID -> Stacks (0 - 5)
    private final Map<UUID, Integer> coldStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> coldStartTime = new ConcurrentHashMap<>();

    // Frostbite charging state: UUID -> Runnable
    private final Map<UUID, BukkitRunnable> frostbiteTasks = new ConcurrentHashMap<>();

    // Stashed offhand items for Honor passive
    public static final Map<UUID, ItemStack> stashedOffhand = new ConcurrentHashMap<>();

    public Mayim(ChurchSMP plugin) {
        super(plugin,
                "mayim",
                new String[]{"staff_of_moses"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#FFFFFF:#00DFFF:#FFFFFF><bold>Mayim</bold></gradient>"),
                Material.NETHERITE_SWORD,
                Alignment.GOOD,
                "Cold",
                "Frostbite");
    }

    public static void checkAndStashOffhand(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            if (!stashedOffhand.containsKey(player.getUniqueId())) {
                stashedOffhand.put(player.getUniqueId(), offhand.clone());
                player.getInventory().setItemInOffHand(null);
                player.sendMessage(Component.text("✦ Mayim Honor temporarily stowed your offhand item.", NamedTextColor.AQUA));
            }
        }
    }

    public static void restoreOffhand(Player player) {
        ItemStack stashed = stashedOffhand.remove(player.getUniqueId());
        if (stashed != null && stashed.getType() != Material.AIR) {
            ItemStack currentOffhand = player.getInventory().getItemInOffHand();
            if (currentOffhand == null || currentOffhand.getType() == Material.AIR) {
                player.getInventory().setItemInOffHand(stashed);
            } else {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stashed);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            player.sendMessage(Component.text("✦ Mayim Honor returned your offhand item.", NamedTextColor.AQUA));
        }
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Finfuel", "Rust", "Honor"), "Cold", "Frostbite"));
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

        int cd = plugin.getConfig().getInt("weapons.mayim.primary_cooldown", 30);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 20);
        plugin.getBossBarManager().showActiveCountdown(player, "Cold Flow", BossBar.Color.BLUE, 20);

        coldStreak.put(player.getUniqueId(), 0);
        coldStartTime.put(player.getUniqueId(), System.currentTimeMillis());

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.4f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_FALL, 1.5f, 0.6f);

        // Cold activation: frost ring at feet + snowflake burst + ice cracks
        Location feet = player.getLocation();
        Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(0, 220, 255), 1.6f);
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(Math.cos(rad) * 1.8, 0.1, Math.sin(rad) * 1.8), 1, 0, 0, 0, 0, cyanDust);
        }
        feet.getWorld().spawnParticle(Particle.SNOWFLAKE, feet.clone().add(0, 1.0, 0), 30, 0.8, 0.5, 0.8, 0.05);
        feet.getWorld().spawnParticle(Particle.BLOCK, feet, 20, 0.6, 0.1, 0.6, 0.1, Material.BLUE_ICE.createBlockData());

        player.sendMessage(Component.text("✦ Cold activated! Consecutive hits escalate enemy slowness.", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        if (frostbiteTasks.containsKey(player.getUniqueId())) {
            return false;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_FALL, 1.2f, 0.8f);
        player.sendMessage(Component.text("✦ Frostbite forming (4s)... Will release on wall hit or when attacked!", NamedTextColor.AQUA));
        plugin.getBossBarManager().showActiveCountdown(player, "Frostbite Forming", BossBar.Color.WHITE, 4);

        // Forming 5-block wide curved slash in front
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getInventory().getItemInMainHand().getType() != baseMaterial) {
                    frostbiteTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Render 5-block wide freezing curved crescent slash in front of player
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().setY(0).normalize();
                Vector cross = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                Particle.DustOptions frostDust = new Particle.DustOptions(Color.fromRGB(180, 240, 255), 1.4f);

                for (double offset = -2.5; offset <= 2.5; offset += 0.4) {
                    double curve = Math.cos((offset / 2.5) * (Math.PI / 2.0)) * 0.8; // Bowed outward curve
                    Location p = eye.clone().add(dir.clone().multiply(3.0 + curve)).add(cross.clone().multiply(offset));
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 2, 0.05, 0.05, 0.05, 0.01);
                    p.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, frostDust);
                    p.getWorld().spawnParticle(Particle.BLOCK, p, 1, 0.05, 0.05, 0.05, 0.05, Material.ICE.createBlockData());

                    for (LivingEntity target : p.getWorld().getNearbyLivingEntities(p, 1.2)) {
                        if (target.equals(player)) continue;
                        target.setFreezeTicks(100);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, false, false));
                    }
                }

                ticks += 5;
                if (ticks >= 80) { // 4 seconds reached
                    releaseFrostbite(player);
                    cancel();
                }
            }
        };

        frostbiteTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L);

        int cd = plugin.getConfig().getInt("weapons.mayim.secondary_cooldown", 45);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        return true;
    }

    public void releaseFrostbite(Player player) {
        BukkitRunnable task = frostbiteTasks.remove(player.getUniqueId());
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Particle.DustOptions iceDust = new Particle.DustOptions(Color.fromRGB(0, 220, 255), 1.8f);

        // Projectile slash moves forward until hitting a wall or reaching 15 blocks
        new BukkitRunnable() {
            Location curr = start.clone();
            int dist = 0;

            @Override
            public void run() {
                curr.add(dir.clone().multiply(1.5));

                // Wide crescent projectile slice
                for (double o = -1.8; o <= 1.8; o += 0.4) {
                    Location sliceP = curr.clone().add(right.clone().multiply(o));
                    sliceP.getWorld().spawnParticle(Particle.SNOWFLAKE, sliceP, 2, 0.1, 0.1, 0.1, 0.02);
                    sliceP.getWorld().spawnParticle(Particle.DUST, sliceP, 1, 0, 0, 0, 0, iceDust);
                }

                boolean hitWall = curr.getBlock().getType().isSolid();
                boolean hitEntity = !curr.getWorld().getNearbyLivingEntities(curr, 2.0, e -> !e.equals(player)).isEmpty();

                dist++;
                if (hitWall || hitEntity || dist >= 12) {
                    // Explode in 5x5 giving Slowness II for 10s
                    curr.getWorld().playSound(curr, Sound.BLOCK_GLASS_BREAK, 1.8f, 0.7f);
                    curr.getWorld().playSound(curr, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.6f);
                    curr.getWorld().spawnParticle(Particle.FLASH, curr, 2, Color.WHITE);
                    curr.getWorld().spawnParticle(Particle.EXPLOSION, curr, 2);
                    curr.getWorld().spawnParticle(Particle.SNOWFLAKE, curr, 100, 2.5, 1.0, 2.5, 0.15);
                    curr.getWorld().spawnParticle(Particle.BLOCK, curr, 50, 2.0, 0.8, 2.0, 0.2, Material.BLUE_ICE.createBlockData());

                    // Expanding frost shockwave on the ground
                    for (int d = 0; d < 360; d += 15) {
                        double rad = Math.toRadians(d);
                        curr.getWorld().spawnParticle(Particle.DUST, curr.clone().add(Math.cos(rad) * 3.5, 0.15, Math.sin(rad) * 3.5), 1, 0, 0, 0, 0, iceDust);
                    }

                    for (LivingEntity e : curr.getWorld().getNearbyLivingEntities(curr, 5.0, 3.0, 5.0)) {
                        if (e.equals(player)) continue;
                        e.damage(8.0, player);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1)); // Slowness II for 10s
                        e.setFreezeTicks(160);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Finfuel: strength updates
        if (attacker.isInWater()) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 2, false, false)); // Strength III in water
            attacker.getWorld().spawnParticle(Particle.DRIPPING_WATER, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3);
        } else {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, false)); // Strength I on land
        }

        // Rust: 5% chance to steal opponent armor durability
        if (Math.random() < 0.20 && target instanceof Player victim) {
            boolean stole = false;
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor != null && armor.getItemMeta() instanceof Damageable dmg) {
                    int maxDur = armor.getType().getMaxDurability();
                    int stealAmount = (int) (maxDur * 0.05);
                    dmg.setDamage(dmg.getDamage() + stealAmount);
                    armor.setItemMeta(dmg);
                    stole = true;
                }
            }
            if (stole) {
                Location vLoc = victim.getLocation().add(0, 1.0, 0);
                victim.getWorld().playSound(vLoc, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.5f);
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 1.5f);
                // Rust particle effect
                Particle.DustOptions rustDust = new Particle.DustOptions(Color.fromRGB(180, 80, 20), 1.5f);
                victim.getWorld().spawnParticle(Particle.DUST, vLoc, 25, 0.4, 0.5, 0.4, 0, rustDust);
                victim.getWorld().spawnParticle(Particle.BLOCK, vLoc, 15, 0.3, 0.4, 0.3, 0.1, Material.IRON_BLOCK.createBlockData());
                attacker.sendMessage(Component.text("✦ Rust stole 5% armor durability!", NamedTextColor.AQUA));
            }
        }

        // Cold ability escalating streak
        String key = id + "_primary";
        if (plugin.getCooldownManager().isActive(attacker, key)) {
            int current = coldStreak.getOrDefault(attacker.getUniqueId(), 0) + 1;
            coldStreak.put(attacker.getUniqueId(), current);

            int amplifier = Math.min(4, current - 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, amplifier));

            // Escalating frost circle around target's feet
            Location tFeet = target.getLocation();
            double ringRadius = 0.7 + (current * 0.2);
            Particle.DustOptions frostRing = new Particle.DustOptions(Color.fromRGB(100, 220, 255), 1.2f + (current * 0.2f));
            for (int d = 0; d < 360; d += 25) {
                double rad = Math.toRadians(d);
                tFeet.getWorld().spawnParticle(Particle.DUST, tFeet.clone().add(Math.cos(rad) * ringRadius, 0.1, Math.sin(rad) * ringRadius), 1, 0, 0, 0, 0, frostRing);
            }
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15 + (current * 5), 0.3, 0.4, 0.3, 0.05);
            attacker.sendMessage(Component.text("✦ Cold streak: " + current + " (Slowness level " + (amplifier + 1) + ")", NamedTextColor.AQUA));
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Reset cold streak when attacked back
        if (coldStreak.containsKey(victim.getUniqueId())) {
            int streak = coldStreak.getOrDefault(victim.getUniqueId(), 0);
            if (streak > 0) {
                coldStreak.put(victim.getUniqueId(), 0);
                victim.sendMessage(Component.text("âœ¦ Your Cold streak was broken!", NamedTextColor.RED));

                // Grant Resistance III for how long you kept the ability
                Long start = coldStartTime.get(victim.getUniqueId());
                if (start != null) {
                    int keptSeconds = (int) Math.min(20, (System.currentTimeMillis() - start) / 1000L);
                    if (keptSeconds > 0) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, keptSeconds * 20, 2));
                        victim.sendMessage(Component.text("âœ¦ Granted Resistance III for " + keptSeconds + "s!", NamedTextColor.GOLD));
                    }
                }
            }
        }

        // Interrupt frostbite forming if attacked
        if (frostbiteTasks.containsKey(victim.getUniqueId())) {
            releaseFrostbite(victim);
        }
    }
}
