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

public class VoidBreaker extends LegendaryWeapon {

    // Crumble charge stacks: UUID -> Stacks (0 - 10)
    private final Map<UUID, Integer> crumbleStacks = new ConcurrentHashMap<>();

    public VoidBreaker(ChurchSMP plugin) {
        super(plugin,
                "voidbreaker",
                new String[]{"void_breaker", "abyssal_shatter"},
                Component.text("VoidBreaker", TextColor.color(0x9400D3)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_AXE,
                Alignment.NULLIFIED,
                "Fractured",
                "Bound & Infection");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Voidfeels: ", NamedTextColor.DARK_AQUA).append(Component.text("Absolute immunity to Levitation and Void blindness.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Crumble: ", NamedTextColor.DARK_AQUA).append(Component.text("Builds to an empowered slam (expand→close→explode).", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Rifted: ", NamedTextColor.DARK_AQUA).append(Component.text("Crits rip spacetime to warp behind target.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Fractured: ", NamedTextColor.LIGHT_PURPLE).append(Component.text("Shatter the floor with erupting void fissures.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Bound & Infection: ", NamedTextColor.LIGHT_PURPLE).append(Component.text("Binds target and inflicts the Fallen debuff.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
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

        int cd = plugin.getConfig().getInt("weapons.voidbreaker.primary_cooldown", 22);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);

        // Fractured: ground void fissures in a forward line
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().setY(0).normalize();
        for (int i = 1; i <= 14; i++) {
            Location p = eye.clone().add(dir.clone().multiply(i)).subtract(0, 1.2, 0);
            p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p, 15, 0.4, 0.2, 0.4, 0.02);
            p.getWorld().spawnParticle(Particle.PORTAL, p, 10, 0.3, 0.3, 0.3, 0.1);

            for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p, 1.6)) {
                if (e.equals(player)) continue;
                e.damage(12.0, player);
                e.setVelocity(new Vector(0, 0.8, 0)); // knock up
            }
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        LivingEntity target = null;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 10.0)) {
            if (e.equals(player)) continue;
            target = e;
            break;
        }

        if (target == null) {
            player.sendMessage(Component.text("No target found within void range!", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("weapons.voidbreaker.secondary_cooldown", 40);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Bound: Fallen Infection", BossBar.Color.PURPLE, 6);

        // Bound & Infection: Binds and inflicts Fallen debuff
        target.setVelocity(new Vector(0, 0, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 10, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1, false, false));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);

        player.sendMessage(Component.text("✦ " + target.getName() + " was infected with the Fallen debuff! (Healing cut, bound in place)", NamedTextColor.DARK_PURPLE));
        target.sendMessage(Component.text("⚔ The Void binds you! Fallen debuff applied!", NamedTextColor.DARK_PURPLE));

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Voidfeels: remove negative effects if any
        attacker.removePotionEffect(PotionEffectType.LEVITATION);
        attacker.removePotionEffect(PotionEffectType.DARKNESS);

        // Build Crumble Stacks
        int stacks = crumbleStacks.getOrDefault(attacker.getUniqueId(), 0) + 1;
        if (stacks >= 10) {
            // Trigger Crumble Slam: expand -> close -> explode sequence
            triggerCrumbleSlam(attacker);
            crumbleStacks.put(attacker.getUniqueId(), 0);
        } else {
            crumbleStacks.put(attacker.getUniqueId(), stacks);
            if (stacks >= 7) {
                // "Ready" trail indicator
                attacker.getWorld().spawnParticle(Particle.PORTAL, attacker.getLocation().add(0, 0.2, 0), 15, 0.2, 0.1, 0.2, 0.05);
                attacker.sendMessage(Component.text("✦ Crumble charging: " + stacks + "/10", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    private void triggerCrumbleSlam(Player player) {
        Location center = player.getLocation();
        player.sendMessage(Component.text("✦ CRUMBLE SLAM TRIGGERED!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));

        // Animated expand -> close -> explode sequence
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step == 1) {
                    // Expand
                    for (int deg = 0; deg < 360; deg += 15) {
                        double rad = Math.toRadians(deg);
                        center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(Math.cos(rad) * 4.0, 0.5, Math.sin(rad) * 4.0), 1);
                    }
                    center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.0f);
                } else if (step == 2) {
                    // Close
                    for (int deg = 0; deg < 360; deg += 15) {
                        double rad = Math.toRadians(deg);
                        center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(Math.cos(rad) * 1.5, 0.5, Math.sin(rad) * 1.5), 2);
                    }
                } else if (step == 3) {
                    // Explode
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                    center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);

                    for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, 6.0)) {
                        if (e.equals(player)) continue;
                        e.damage(16.0, player);
                        e.setVelocity(e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.4).setY(0.4));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
