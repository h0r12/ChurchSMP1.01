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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Grim extends LegendaryWeapon {

    private final NamespacedKey killCountKey;

    public Grim(ChurchSMP plugin) {
        super(plugin,
                "grim",
                new String[]{"scythe_of_cain"},
                Component.text("Grim", TextColor.color(0x2F4F4F)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_HOE,
                Alignment.EVIL,
                "HollowedOut",
                "Dark Particle");
        this.killCountKey = new NamespacedKey(plugin, "grim_kills");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Disgusts: ", NamedTextColor.GRAY).append(Component.text("Afflicts targets with sickening nausea.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Soultaking: ", NamedTextColor.GRAY).append(Component.text("Reaps enemy soul remnants on elimination.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Reaper: ", NamedTextColor.GRAY).append(Component.text("Harvested souls engraved permanently onto the scythe.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] HollowedOut: ", NamedTextColor.DARK_GRAY).append(Component.text("Slip into an ethereal invulnerable shadow phase.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Dark Particle: ", NamedTextColor.DARK_GRAY).append(Component.text("Cast a scythe-wave of reaping dark particles.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("☠ Souls Reaped: 0", NamedTextColor.DARK_RED));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(killCountKey, PersistentDataType.INTEGER, 0);
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

        int cd = plugin.getConfig().getInt("weapons.grim.primary_cooldown", 14);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 3);
        plugin.getBossBarManager().showActiveCountdown(player, "HollowedOut Form", BossBar.Color.PURPLE, 3);

        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.5f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false));
        player.sendMessage(Component.text("✦ HollowedOut: You phase through shadows, nearly invulnerable!", NamedTextColor.DARK_GRAY));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.grim.secondary_cooldown", 32);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);

        // Dark Particle scythe wave
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        for (int i = 1; i <= 12; i++) {
            Location p = eye.clone().add(dir.clone().multiply(i));
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p, 10, 0.3, 0.3, 0.3, 0.02);
            player.getWorld().spawnParticle(Particle.SQUID_INK, p, 5, 0.2, 0.2, 0.2, 0.01);

            for (LivingEntity e : player.getWorld().getNearbyLivingEntities(p, 2.0)) {
                if (e.equals(player)) continue;
                e.damage(14.0, player);
                e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
            }
        }

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Disgusts passive: Nausea & Weakness
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
    }

    /**
     * Increments kill counter in PDC and updates weapon lore.
     */
    public void addKill(Player player, ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) return;
        ItemMeta meta = weapon.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int kills = pdc.getOrDefault(killCountKey, PersistentDataType.INTEGER, 0) + 1;
        pdc.set(killCountKey, PersistentDataType.INTEGER, kills);

        List<Component> lore = meta.lore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(lore.get(i));
                if (plain.contains("Souls Reaped:")) {
                    lore.set(i, Component.text("☠ Souls Reaped: " + kills, NamedTextColor.DARK_RED));
                    break;
                }
            }
            meta.lore(lore);
        }
        weapon.setItemMeta(meta);
        player.playSound(player.getLocation(), Sound.ENTITY_VEX_DEATH, 1.0f, 0.5f);
        player.sendMessage(Component.text("✦ Grim harvested another soul! Total: " + kills, NamedTextColor.DARK_RED));
    }
}
