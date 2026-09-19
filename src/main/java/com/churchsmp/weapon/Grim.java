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
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
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

public class Grim extends LegendaryWeapon {

    private final NamespacedKey killCountKey;
    private final Map<UUID, Integer> darkParticleHearts = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hollowedOutArmed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> darkParticleArmed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> failedActionTarget = new ConcurrentHashMap<>();

    public Grim(ChurchSMP plugin) {
        super(plugin,
                "grim",
                new String[]{"scythe_of_cain"},
                Component.text("Grim", TextColor.color(0x2F4F4F)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_SWORD,
                Alignment.EVIL,
                "HollowedOut",
                "Dark Particle");
        this.killCountKey = new NamespacedKey(plugin, "grim_kills");

        // Passive 1: Disgusts - nearby entities receive Nausea + Poison for 2s every 3s
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (isHoldingGrim(player)) {
                        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 5.0)) {
                            if (e.equals(player)) continue;
                            e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 60L);
    }

    private boolean isHoldingGrim(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        LegendaryWeapon w = plugin.getWeaponManager().getWeapon(item);
        return w instanceof Grim;
    }

    @Override
    public ItemStack createItem() {
        return createItemWithKills(0);
    }

    public ItemStack createItemWithKills(int startingKills) {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("And Soul Cycle Spirals Again", TextColor.color(0x708090)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Disgusts: ", NamedTextColor.GRAY).append(Component.text("Nearby entities receive Nausea & Poison for 2s.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Soultaking: ", NamedTextColor.GRAY).append(Component.text("Throw the sword to steal 2 hearts, teleport behind target, and blind. (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Reaper: ", NamedTextColor.GRAY).append(Component.text("Each kill permanently adds +1 max heart. Potion effects +20s. Sneak to view kills.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] HollowedOut: ", NamedTextColor.DARK_GRAY).append(Component.text("Next hit inflicts Darkness + Slowness II + 40% action fail chance (15s); throw replaced by 6s charging sonic boom. (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Dark Particle: ", NamedTextColor.DARK_GRAY).append(Component.text("Sharpness X on next hit; attacks give +1 max health (resets if hit); active 25s. (80s CD)", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("☠ Souls Reaped: " + startingKills, NamedTextColor.DARK_RED));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(killCountKey, PersistentDataType.INTEGER, startingKills);
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

        int cd = plugin.getConfig().getInt("weapons.grim.primary_cooldown", 60);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "HollowedOut Armed", BossBar.Color.PURPLE, 15);

        hollowedOutArmed.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.5f);
        player.sendMessage(Component.text("✦ HollowedOut armed! Next hit inflicts crippling curse & 40% failure chance.", NamedTextColor.DARK_PURPLE));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.grim.secondary_cooldown", 80);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 25);
        plugin.getBossBarManager().showActiveCountdown(player, "Dark Particle Surge", BossBar.Color.PURPLE, 25);

        darkParticleArmed.put(player.getUniqueId(), true);
        darkParticleHearts.put(player.getUniqueId(), 0);

        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.2f, 1.2f);
        player.sendMessage(Component.text("✦ Dark Particle active (25s)! Attacks grant bonus max health and Sharpness X!", NamedTextColor.DARK_RED));
        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // HollowedOut execution
        if (Boolean.TRUE.equals(hollowedOutArmed.remove(attacker.getUniqueId()))) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0)); // 15s
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 1));
            failedActionTarget.put(target.getUniqueId(), System.currentTimeMillis() + 15000L);

            target.sendMessage(Component.text("⚔ Your actions have a 40% chance to fail for 15s from HollowedOut!", NamedTextColor.DARK_PURPLE));
            attacker.sendMessage(Component.text("✦ HollowedOut curse planted on " + target.getName() + "!", NamedTextColor.DARK_PURPLE));
        }

        // Dark Particle execution: +1 max health per hit, orbiting soul sand
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isActive(attacker, key)) {
            int bonus = darkParticleHearts.getOrDefault(attacker.getUniqueId(), 0) + 1;
            darkParticleHearts.put(attacker.getUniqueId(), bonus);

            AttributeInstance attr = attacker.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(Math.min(40.0, attr.getBaseValue() + 2.0)); // +1 heart (2 HP)
            }

            // Orbiting soul sand particles
            Location loc = attacker.getLocation().add(0, 1.0, 0);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 15, 0.5, 0.5, 0.5, 0.05);
            attacker.sendMessage(Component.text("✦ Dark Particle: +1 Max Heart! (Total bonus: " + bonus + " hearts)", NamedTextColor.DARK_RED));
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Dark Particle: resets bonus hearts if attacked
        Integer bonus = darkParticleHearts.remove(victim.getUniqueId());
        if (bonus != null && bonus > 0) {
            AttributeInstance attr = victim.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(Math.max(20.0, attr.getBaseValue() - (bonus * 2.0)));
            }
            victim.sendMessage(Component.text("✦ Dark Particle hearts shattered by damage!", NamedTextColor.RED));
        }
    }

    @Override
    public void onCrouch(Player player, boolean isSneaking) {
        if (!isSneaking) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.hasItemMeta()) {
            int kills = item.getItemMeta().getPersistentDataContainer().getOrDefault(killCountKey, PersistentDataType.INTEGER, 0);
            player.sendMessage(Component.text("✦ Grim Sword Kills: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(kills + " souls", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)));
        }
    }

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

        // Reaper: +1 max heart per kill permanently
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(attr.getBaseValue() + 2.0);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VEX_DEATH, 1.2f, 0.6f);
        player.sendMessage(Component.text("✦ Reaper harvested soul #" + kills + "! +1 Permanent Max Heart!", NamedTextColor.DARK_RED));
    }
}
