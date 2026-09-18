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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuminescenceSpear extends LegendaryWeapon {

    private final Map<UUID, Integer> blinkCharges = new ConcurrentHashMap<>();

    public LuminescenceSpear(ChurchSMP plugin) {
        super(plugin,
                "luminescence_spear",
                new String[]{"sword_of_david"},
                Component.text("Luminescence Spear", TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD),
                Material.TRIDENT,
                Alignment.GOOD,
                "Blink",
                "SunEclipse");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Bolt: ", NamedTextColor.YELLOW).append(Component.text("Electric strike discharges on melee hit.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • LightStealing: ", NamedTextColor.YELLOW).append(Component.text("Siphons life and luminance on hit.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • BurningBones: ", NamedTextColor.YELLOW).append(Component.text("Scorches evil souls and undead creatures.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Blink: ", NamedTextColor.AQUA).append(Component.text("3-charge instant forward dash with radiant rings.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] SunEclipse: ", NamedTextColor.AQUA).append(Component.text("Mark a foe for a delayed celestial orbital strike.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
            // Recharge
            blinkCharges.put(player.getUniqueId(), 3);
            currentCharges = 3;
        }

        // Perform Blink Dash
        Vector dash = player.getLocation().getDirection().normalize().multiply(1.8).setY(0.2);
        player.setVelocity(dash);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.6f);

        // Spawn ring particle indicator
        Location pLoc = player.getLocation();
        for (int degree = 0; degree < 360; degree += 20) {
            double rad = Math.toRadians(degree);
            double x = Math.cos(rad) * 1.5;
            double z = Math.sin(rad) * 1.5;
            player.getWorld().spawnParticle(Particle.WAX_ON, pLoc.clone().add(x, 1.0, z), 1);
        }

        currentCharges--;
        blinkCharges.put(player.getUniqueId(), currentCharges);

        if (currentCharges <= 0) {
            int cd = plugin.getConfig().getInt("weapons.luminescence_spear.primary_cooldown", 10);
            plugin.getCooldownManager().setCooldown(player, key, cd);
        } else {
            player.sendMessage(Component.text("✦ Blink Charges remaining: " + currentCharges + "/3", NamedTextColor.YELLOW));
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Target closest entity in line of sight
        LivingEntity target = null;
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(player.getLocation(), 15.0)) {
            if (entity.equals(player)) continue;
            Vector toEntity = entity.getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            if (player.getEyeLocation().getDirection().dot(toEntity) > 0.85) {
                target = entity;
                break;
            }
        }

        if (target == null) {
            player.sendMessage(Component.text("No target in sight to mark with SunEclipse!", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.secondary_cooldown", 30);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "SunEclipse Beacon", BossBar.Color.YELLOW, 3);

        final LivingEntity finalTarget = target;
        player.sendMessage(Component.text("✦ SunEclipse mark placed on " + target.getName() + "! Impact incoming...", NamedTextColor.GOLD));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 5;
                if (!finalTarget.isValid() || ticks >= 40) { // 2 seconds
                    Location hitLoc = finalTarget.getLocation();
                    hitLoc.getWorld().playSound(hitLoc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.0f);

                    // Delayed celestial beam striking from the sky
                    for (int y = 0; y < 25; y++) {
                        hitLoc.getWorld().spawnParticle(Particle.END_ROD, hitLoc.clone().add(0, y, 0), 4, 0.2, 0.2, 0.2, 0.01);
                    }
                    hitLoc.getWorld().spawnParticle(Particle.FLASH, hitLoc, 3);

                    for (LivingEntity e : hitLoc.getWorld().getNearbyLivingEntities(hitLoc, 5.0)) {
                        if (e.equals(player)) continue;
                        e.damage(18.0, player);
                        e.setFireTicks(100);
                    }
                    cancel();
                } else {
                    // Mark particles over head
                    finalTarget.getWorld().spawnParticle(Particle.GLOW, finalTarget.getLocation().add(0, 2.5, 0), 5, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Bolt passive: electric particle & 3 extra damage
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        target.damage(3.0);

        // LightStealing passive: heals attacker
        double newHealth = Math.min(attacker.getMaxHealth(), attacker.getHealth() + 2.0);
        attacker.setHealth(newHealth);
        attacker.getWorld().spawnParticle(Particle.WAX_ON, attacker.getLocation().add(0, 1, 0), 5);

        // BurningBones passive: burns undead or evil players
        if (target instanceof Monster || (target instanceof Player p && plugin.getAlignmentManager().getAlignment(p) == Alignment.EVIL)) {
            target.setFireTicks(80);
        }
    }
}
