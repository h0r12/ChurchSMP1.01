package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    public Mayim(ChurchSMP plugin) {
        super(plugin,
                "mayim",
                new String[]{"staff_of_moses"},
                Component.text("Mayim", TextColor.color(0x00BFFF)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_SWORD,
                Alignment.GOOD,
                "Cold",
                "Frostbite");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.AQUA));
            lore.add(Component.text("The Leviathan's Abandoned Shadow.", TextColor.color(0x00FFFF)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Finfuel: ", NamedTextColor.YELLOW).append(Component.text("Strength I on land, Strength III in water.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Rust: ", NamedTextColor.YELLOW).append(Component.text("Chance to steal 5% armor durability with ice break sound.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Honor: ", NamedTextColor.YELLOW).append(Component.text("Cannot hold any offhand item while holding Mayim.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Cold: ", NamedTextColor.AQUA).append(Component.text("Attacks escalate enemy slowness; resets if hit; grants Resistance III for time kept. (20s active, 30s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Frostbite: ", NamedTextColor.AQUA).append(Component.text("Forms a 5-block wide slash; stuns and freezes; explodes in 5x5 on impact with Slowness II for 10s. (45s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.AQUA));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.5f);
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

        // Forming 5-block wide slash in front
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getInventory().getItemInMainHand().getType() != baseMaterial) {
                    frostbiteTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Render 5-block wide freezing slash in front of player
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().setY(0).normalize();
                Vector cross = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

                for (double offset = -2.5; offset <= 2.5; offset += 0.5) {
                    Location p = eye.clone().add(dir.clone().multiply(3.0)).add(cross.clone().multiply(offset));
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 3, 0.1, 0.1, 0.1, 0.01);
                    p.getWorld().spawnParticle(Particle.BLOCK, p, 2, 0.1, 0.1, 0.1, Material.ICE.createBlockData());

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

        // Projectile slash moves forward until hitting a wall or reaching 15 blocks
        new BukkitRunnable() {
            Location curr = start.clone();
            int dist = 0;

            @Override
            public void run() {
                curr.add(dir.clone().multiply(1.5));
                curr.getWorld().spawnParticle(Particle.SNOWFLAKE, curr, 10, 0.5, 0.5, 0.5, 0.05);

                boolean hitWall = curr.getBlock().getType().isSolid();
                boolean hitEntity = !curr.getWorld().getNearbyLivingEntities(curr, 2.0, e -> !e.equals(player)).isEmpty();

                dist++;
                if (hitWall || hitEntity || dist >= 12) {
                    // Explode in 5x5 giving Slowness II for 10s
                    curr.getWorld().playSound(curr, Sound.BLOCK_GLASS_BREAK, 1.8f, 0.7f);
                    curr.getWorld().spawnParticle(Particle.EXPLOSION, curr, 2);
                    curr.getWorld().spawnParticle(Particle.SNOWFLAKE, curr, 80, 2.5, 1.0, 2.5, 0.1);

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
                victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 1.5f);
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 1.5f);
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
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
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
                victim.sendMessage(Component.text("✦ Your Cold streak was broken!", NamedTextColor.RED));

                // Grant Resistance III for how long you kept the ability
                Long start = coldStartTime.get(victim.getUniqueId());
                if (start != null) {
                    int keptSeconds = (int) Math.min(20, (System.currentTimeMillis() - start) / 1000L);
                    if (keptSeconds > 0) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, keptSeconds * 20, 2));
                        victim.sendMessage(Component.text("✦ Granted Resistance III for " + keptSeconds + "s!", NamedTextColor.GOLD));
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
