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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

public class Judas extends LegendaryWeapon {

    public Judas(ChurchSMP plugin) {
        super(plugin,
                "judas",
                new String[]{"blade_of_judas", "dagger_of_betrayal"},
                Component.text("Judas", TextColor.color(0x8B0000)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_DAGGER == null ? Material.NETHERITE_SWORD : Material.NETHERITE_SWORD,
                Alignment.EVIL,
                "Hemorrhaged Mold",
                "Thirty Pieces of Silver");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_RED));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Bloodfeast: ", NamedTextColor.GOLD).append(Component.text("Feast upon enemy health on strike.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Unfree: ", NamedTextColor.GOLD).append(Component.text("Inflicts crippling darkness and slowness.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Bite: ", NamedTextColor.GOLD).append(Component.text("Inflicts deep hemorrhaging bleedout.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Hemorrhaged Mold: ", NamedTextColor.DARK_RED).append(Component.text("Fire 3 explosive wither skulls.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Thirty Pieces of Silver: ", NamedTextColor.DARK_RED).append(Component.text("Permanently docks target's real max health.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_RED));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.BANE_OF_ARTHROPODS, 5, true);
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.judas.primary_cooldown", 16);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.8f);

        // Fire 3 wither skulls in rapid succession
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || count >= 3) {
                    cancel();
                    return;
                }
                Vector dir = player.getEyeLocation().getDirection().normalize().multiply(1.4);
                WitherSkull skull = player.launchProjectile(WitherSkull.class, dir);
                skull.setCharged(count == 2); // 3rd one is charged!
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 1.2f);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Target enemy in front
        LivingEntity target = null;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 6.0)) {
            if (e.equals(player)) continue;
            target = e;
            break;
        }

        if (target == null) {
            player.sendMessage(Component.text("No victim nearby to claim Thirty Pieces of Silver!", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("weapons.judas.secondary_cooldown", 45);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Thirty Pieces of Silver", BossBar.Color.RED, 5);

        // Docks real max health
        AttributeInstance maxHealthAttr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            double currentBase = maxHealthAttr.getBaseValue();
            double dockedBase = Math.max(6.0, currentBase - 4.0); // Docks 2 full hearts (4 HP)
            maxHealthAttr.setBaseValue(dockedBase);

            target.damage(6.0, player);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.5f, 0.5f);
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);

            player.sendMessage(Component.text("✦ You claimed Thirty Pieces of Silver! Docked 2 max hearts from " + target.getName(), NamedTextColor.DARK_RED));
            target.sendMessage(Component.text("⚔ Your soul was betrayed! Judas docked your maximum health!", NamedTextColor.DARK_RED));
        }

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Bloodfeast: Lifesteal
        double heal = Math.min(attacker.getMaxHealth(), attacker.getHealth() + (damage * 0.25));
        attacker.setHealth(heal);
        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.5, 0), 2);

        // Unfree: Darkness & Slowness
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));

        // Bite: Bleedout over time
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || ticks > 4) {
                    cancel();
                    return;
                }
                target.damage(1.5, attacker);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 3);
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
}
