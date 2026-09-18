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
import org.bukkit.entity.Arrow;
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

public class Sorrowess extends LegendaryWeapon {

    public Sorrowess(ChurchSMP plugin) {
        super(plugin,
                "sorrowess",
                new String[]{"blade_of_sorrow"},
                Component.text("Sorrowess", TextColor.color(0x4B0082)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_SWORD,
                Alignment.EVIL,
                "Grief Shards",
                "Gloom");
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
            lore.add(Component.text(" • Forming: ", NamedTextColor.GRAY).append(Component.text("Gathers sorrow momentum while maneuvering.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Brave: ", NamedTextColor.GRAY).append(Component.text("Gains formidable armor as health wanes.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Grief Shards: ", NamedTextColor.DARK_PURPLE).append(Component.text("Fires sorrow projectiles with Bleedout DOT.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Gloom: ", NamedTextColor.DARK_PURPLE).append(Component.text("Crits trade raw damage to shred enemy armor.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));

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

        int cd = plugin.getConfig().getInt("weapons.sorrowess.primary_cooldown", 20);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.6f);

        // Fire Grief Shards (cone of 5 sharp sorrow projectiles)
        Vector baseDir = player.getEyeLocation().getDirection().normalize();
        for (int i = -2; i <= 2; i++) {
            Vector dir = baseDir.clone().rotateAroundY(Math.toRadians(i * 8.0)).multiply(1.8);
            Arrow arrow = player.launchProjectile(Arrow.class, dir);
            arrow.setDamage(4.5);
            arrow.setCritical(true);
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.sorrowess.secondary_cooldown", 35);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 10);
        plugin.getBossBarManager().showActiveCountdown(player, "Gloom Aura", BossBar.Color.PURPLE, 10);

        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);
        player.sendMessage(Component.text("✦ Gloom active: Critical hits now shred target's armor defense!", NamedTextColor.DARK_PURPLE));
        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        String secondaryKey = id + "_secondary";
        if (plugin.getCooldownManager().isActive(attacker, secondaryKey)) {
            // Gloom armor shredding debuff
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
            target.sendMessage(Component.text("⚔ Your armor was shredded by Gloom!", NamedTextColor.DARK_PURPLE));
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Brave passive: if victim HP < 50%, grants Resistance II
        if (victim.getHealth() < (victim.getMaxHealth() * 0.5)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, false, false));
        }
    }
}
