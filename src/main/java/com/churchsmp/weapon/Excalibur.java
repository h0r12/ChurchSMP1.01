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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Excalibur extends LegendaryWeapon {

    public Excalibur(ChurchSMP plugin) {
        super(plugin,
                "excalibur",
                new String[]{"blade_of_the_archangel"},
                Component.text("Excalibur", TextColor.color(0x55FFFF)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_SWORD,
                Alignment.GOOD,
                "Accelerated Nova",
                "Altar Pining");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_AQUA));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Hopeful: ", NamedTextColor.YELLOW).append(Component.text("Strikes reveal foes with radiant glowing.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Wings: ", NamedTextColor.YELLOW).append(Component.text("Complete immunity to fall and kinetic damage.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Reveal: ", NamedTextColor.YELLOW).append(Component.text("Sneak to expose hidden/invisible entities nearby.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Accelerated Nova: ", NamedTextColor.AQUA).append(Component.text("Unleash a piercing sonic beam.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Altar Pining: ", NamedTextColor.AQUA).append(Component.text("Skyward leap crashing down in a holy shockwave.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_AQUA));

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

        int cd = plugin.getConfig().getInt("weapons.excalibur.primary_cooldown", 18);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Accelerated Nova", BossBar.Color.BLUE, 2);

        // Accelerated Nova (directional Warden sonic boom line attack)
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        player.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.2f);

        for (int i = 1; i <= 20; i++) {
            Location point = start.clone().add(direction.clone().multiply(i));
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1);

            for (LivingEntity e : player.getWorld().getNearbyLivingEntities(point, 1.8)) {
                if (e.equals(player)) continue;
                e.damage(14.0, player);
                e.setVelocity(direction.clone().multiply(1.5).setY(0.4));
            }
        }
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.excalibur.secondary_cooldown", 25);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Altar Pining", BossBar.Color.BLUE, 3);

        // Altar Pining: Launch upward -> Slam down (two-stage damage)
        player.setVelocity(new Vector(0, 1.4, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            boolean slammed = false;

            @Override
            public void run() {
                ticks++;
                if (ticks == 15 && !slammed) {
                    // Slam down
                    player.setVelocity(new Vector(0, -2.5, 0));
                    slammed = true;
                }

                if (slammed && (player.isOnGround() || ticks >= 35)) {
                    // Stage 2 impact
                    Location impact = player.getLocation();
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 3);
                    impact.getWorld().spawnParticle(Particle.END_ROD, impact, 60, 2.0, 0.5, 2.0, 0.1);

                    for (LivingEntity e : impact.getWorld().getNearbyLivingEntities(impact, 6.0)) {
                        if (e.equals(player)) continue;
                        e.damage(16.0, player);
                        e.setVelocity(e.getLocation().toVector().subtract(impact.toVector()).normalize().multiply(1.2).setY(0.5));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Hopeful passive: Glowing effect
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
        target.getWorld().spawnParticle(Particle.GLOW, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Wings passive: complete immunity to fall and kinetic (elytra wall slam) damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            event.setCancelled(true);
            victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation(), 10, 0.3, 0.1, 0.3, 0.02);
        }
    }

    @Override
    public void onCrouch(Player player, boolean isSneaking) {
        if (!isSneaking) return;
        // Reveal passive: outlines hidden / invisible entities nearby
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(player.getLocation(), 12.0)) {
            if (entity.equals(player)) continue;
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.5f);
    }
}
