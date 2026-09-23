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
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#004d00:#556B2F:#004d00><bold>Grim Scythe</bold></gradient>"),
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
                        Location pLoc = player.getLocation().add(0, 1.0, 0);
                        pLoc.getWorld().spawnParticle(Particle.SMOKE, pLoc, 6, 0.4, 0.3, 0.4, 0.02);

                        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 5.0)) {
                            if (e.equals(player)) continue;
                            e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                            e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 1.0, 0), 3, 0.2, 0.3, 0.2, 0.02);
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
            List<Component> lore = new ArrayList<>(buildCleanLore(List.of("Disgusts", "Soultaking", "Reaper"), "HollowedOut", "Dark Particle"));
            lore.add(Component.text("☠ Souls Reaped: " + startingKills, NamedTextColor.DARK_RED));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(killCountKey, PersistentDataType.INTEGER, startingKills);
            applyStandardEnchants(meta);
            meta.setCustomModelData(1007);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void throwScythe(Player player) {
        // Alignment check
        if (!plugin.getAlignmentManager().canWield(player, requiredAlignment)) {
            player.sendMessage(Component.text("✦ Your alignment prevents you from channeling Grim Scythe!", NamedTextColor.RED));
            return;
        }

        // If HollowedOut is armed -> fires a sonic boom blast!
        if (Boolean.TRUE.equals(hollowedOutArmed.remove(player.getUniqueId()))) {
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();

            player.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.0f);
            player.getWorld().playSound(eye, Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.6f);

            Particle.DustOptions darkGreen = new Particle.DustOptions(Color.fromRGB(0, 77, 0), 2.0f);
            Particle.DustOptions grayGreen = new Particle.DustOptions(Color.fromRGB(85, 107, 47), 1.5f);

            Location curr = eye.clone();
            for (int i = 0; i < 20; i++) {
                curr.add(dir.clone().multiply(0.8));
                curr.getWorld().spawnParticle(Particle.DUST, curr, 3, 0.1, 0.1, 0.1, 0, darkGreen);
                curr.getWorld().spawnParticle(Particle.DUST, curr, 2, 0.1, 0.1, 0.1, 0, grayGreen);
                curr.getWorld().spawnParticle(Particle.SMOKE, curr, 1, 0.05, 0.05, 0.05, 0.01);
                if (i % 4 == 0) {
                    curr.getWorld().spawnParticle(Particle.SONIC_BOOM, curr, 1);
                }

                for (LivingEntity target : curr.getWorld().getNearbyLivingEntities(curr, 1.5, e -> !e.equals(player))) {
                    target.damage(8.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                    failedActionTarget.put(target.getUniqueId(), System.currentTimeMillis() + 15000L);
                }
            }

            player.sendMessage(Component.text("✦ HollowedOut unleashed a dark Sonic Boom blast!", NamedTextColor.DARK_GREEN));
            return;
        }

        // Soultaking throw: 40s cooldown
        String cdKey = "grim_soultaking";
        if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
            return;
        }
        plugin.getCooldownManager().setCooldown(player, cdKey, 40);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
        player.swingMainHand();

        Vector dir = player.getEyeLocation().getDirection().normalize().multiply(1.4);
        Location startLoc = player.getEyeLocation();

        Particle.DustOptions scytheDust = new Particle.DustOptions(Color.fromRGB(0, 77, 0), 1.8f);
        Particle.DustOptions scytheGray = new Particle.DustOptions(Color.fromRGB(85, 107, 47), 1.5f);

        new BukkitRunnable() {
            int step = 0;
            Location current = startLoc.clone();

            @Override
            public void run() {
                step++;
                if (step > 25) {
                    cancel();
                    return;
                }

                current.add(dir);
                // Scythe spinning visual (dark green and gray green)
                for (double angle = 0; angle < 360; angle += 60) {
                    double rad = Math.toRadians(angle + (step * 30));
                    Vector offset = new Vector(Math.cos(rad) * 0.7, Math.sin(rad) * 0.7, 0);
                    current.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset), 1, 0, 0, 0, 0, scytheDust);
                    current.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset.multiply(0.5)), 1, 0, 0, 0, 0, scytheGray);
                }
                current.getWorld().spawnParticle(Particle.SMOKE, current, 2, 0.1, 0.1, 0.1, 0.02);

                for (LivingEntity target : current.getWorld().getNearbyLivingEntities(current, 1.5, e -> !e.equals(player))) {
                    // Steal 2 hearts (4.0 HP), blind, teleport behind target
                    target.damage(4.0, player);
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));

                    Vector behindVec = target.getLocation().getDirection().normalize().multiply(-1.5);
                    Location behind = target.getLocation().add(behindVec);
                    behind.setDirection(target.getLocation().getDirection());
                    player.teleport(behind);

                    player.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_VEX_HURT, 1.2f, 0.5f);
                    target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);

                    player.sendMessage(Component.text("✦ Soultaking: Stole 2 hearts and phased behind " + target.getName() + "!", NamedTextColor.DARK_GREEN));
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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

        // Dark void particles around hands and feet
        Location pLoc = player.getLocation();
        pLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, pLoc.clone().add(0, 1.0, 0), 15, 0.4, 0.4, 0.4, 0.05);
        pLoc.getWorld().spawnParticle(Particle.SOUL, pLoc.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);

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

        // Dark red aura pulse from player
        Location pLoc = player.getLocation();
        Particle.DustOptions darkRed = new Particle.DustOptions(Color.fromRGB(120, 0, 20), 1.8f);
        for (int d = 0; d < 360; d += 20) {
            double rad = Math.toRadians(d);
            pLoc.getWorld().spawnParticle(Particle.DUST, pLoc.clone().add(Math.cos(rad) * 1.5, 0.2, Math.sin(rad) * 1.5), 1, 0, 0, 0, 0, darkRed);
        }

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

            // Void explosion at target
            Location hitLoc = target.getLocation().add(0, 1.0, 0);
            hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.6f);
            hitLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, hitLoc, 25, 0.5, 0.5, 0.5, 0.08);
            hitLoc.getWorld().spawnParticle(Particle.SOUL, hitLoc, 15, 0.4, 0.4, 0.4, 0.05);

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

            // Orbiting soul sand and dark red particles
            Location loc = attacker.getLocation().add(0, 1.0, 0);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 15, 0.5, 0.5, 0.5, 0.05);
            Particle.DustOptions darkRed = new Particle.DustOptions(Color.fromRGB(150, 10, 30), 1.5f);
            for (int d = 0; d < 360; d += 30) {
                double rad = Math.toRadians(d);
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(rad) * 1.0, 0, Math.sin(rad) * 1.0), 1, 0, 0, 0, 0, darkRed);
            }

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
            Location loc = victim.getLocation().add(0, 1.0, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.7f);
            loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 10, 0.4, 0.4, 0.4, 0.1);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 12, 0.5, 0.5, 0.5, 0.08);
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

        // Soultaking passive: triggers on kill with 10s cooldown, grants 5s Regen + Absorption
        plugin.getBossBarManager().showPassiveCooldown(player, this, "Soultaking", 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 0));

        // Soul harvesting visual
        Location loc = player.getLocation().add(0, 1.0, 0);
        loc.getWorld().spawnParticle(Particle.SOUL, loc, 25, 0.6, 0.8, 0.6, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_VEX_DEATH, 1.2f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.2f);
        player.sendMessage(Component.text("✦ Reaper harvested soul #" + kills + "! +1 Permanent Max Heart!", NamedTextColor.DARK_RED));
    }
}
