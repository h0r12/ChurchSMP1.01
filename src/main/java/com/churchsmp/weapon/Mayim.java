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
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class Mayim extends LegendaryWeapon {

    public Mayim(ChurchSMP plugin) {
        super(plugin,
                "mayim",
                new String[]{"staff_of_moses"},
                Component.text("Mayim", TextColor.color(0x00BFFF)).decorate(TextDecoration.BOLD),
                Material.STICK,
                Alignment.GOOD,
                "Frost Edge",
                "Entangle Freeze");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.AQUA));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Water Mighty: ", NamedTextColor.YELLOW).append(Component.text("Heightened speed & power in water or rainfall.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Rust: ", NamedTextColor.YELLOW).append(Component.text("Strikes severely degrade target's armor durability.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Icy Path: ", NamedTextColor.YELLOW).append(Component.text("Walk effortlessly atop water on frosted ice.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Frost Edge: ", NamedTextColor.AQUA).append(Component.text("Infuses strikes with escalating sub-zero chill.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Entangle Freeze: ", NamedTextColor.AQUA).append(Component.text("Flash freezes all surrounding foes solid in frost.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.AQUA));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
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

        int cd = plugin.getConfig().getInt("weapons.mayim.primary_cooldown", 15);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 8);
        plugin.getBossBarManager().showActiveCountdown(player, "Frost Edge", BossBar.Color.BLUE, 8);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.6f);
        player.sendMessage(Component.text("✦ Frost Edge activated! Strikes freeze enemies solid.", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.mayim.secondary_cooldown", 28);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Entangle Freeze", BossBar.Color.BLUE, 4);

        Location center = player.getLocation();
        center.getWorld().playSound(center, Sound.BLOCK_POWDER_SNOW_FALL, 2.0f, 0.5f);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.add(0, 1, 0), 100, 3.5, 1.0, 3.5, 0.1);

        List<Block> frostedBlocks = new ArrayList<>();
        // Freeze surrounding targets & create frosted circle
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 7.0)) {
            if (e.equals(player)) continue;
            e.setFreezeTicks(200); // 10 seconds of freezing
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 5, false, false));
            e.damage(10.0, player);
            e.sendMessage(Component.text("❄ You are entangled in glacial ice!", NamedTextColor.AQUA));
        }

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Water Mighty passive
        if (attacker.isInWater() || attacker.getWorld().hasStorm()) {
            target.damage(4.0);
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        }

        // Rust passive: degrade armor
        if (target instanceof Player victim) {
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor != null && armor.getItemMeta() instanceof Damageable dmg) {
                    dmg.setDamage(dmg.getDamage() + 8);
                    armor.setItemMeta(dmg);
                }
            }
        }

        // Active Frost Edge effect
        String primaryKey = id + "_primary";
        if (plugin.getCooldownManager().isActive(attacker, primaryKey)) {
            target.setFreezeTicks(Math.min(300, target.getFreezeTicks() + 100));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
