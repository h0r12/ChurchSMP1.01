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
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
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

public class LuminescenceSpear extends LegendaryWeapon {

    private final Map<UUID, Integer> blinkCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boltCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lightStealCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> crescentEclipseQueued = new ConcurrentHashMap<>();

    public LuminescenceSpear(ChurchSMP plugin) {
        super(plugin,
                "luminescence_spear",
                new String[]{"sword_of_david"},
                Component.text("Luminescence Spear", TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD),
                Material.TRIDENT,
                Alignment.GOOD,
                "Blink",
                "CrescentEclipse");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));
            lore.add(Component.text("As so the shadow fall behinds.", TextColor.color(0xFFE4B5)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Bolt: ", NamedTextColor.YELLOW).append(Component.text("Falling creates an explosion scaling with velocity (like mace). (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" • LightStealing: ", NamedTextColor.YELLOW).append(Component.text("Throwing inflicts Darkness for 10s. (60s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" • BurningBones: ", NamedTextColor.YELLOW).append(Component.text("Sword attack speed (1.6) with trident impact power.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Blink: ", NamedTextColor.AQUA).append(Component.text("Dash 6 blocks (3/3 charges); foes caught take 1 heart & cannot throw projectiles for 8s.", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] CrescentEclipse: ", NamedTextColor.AQUA).append(Component.text("Next throw marks target for stun + 4-heart orbital beam after 2s; ground shockwave stuns for 2s with Darkness for 4s. (130s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.GOLD));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // BurningBones: attack speed of sword (1.6)
            NamespacedKey speedKey = new NamespacedKey(plugin, "spear_speed");
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

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
            blinkCharges.put(player.getUniqueId(), 3);
            currentCharges = 3;
        }

        // Dash 6 blocks forward
        Vector dir = player.getLocation().getDirection().normalize();
        Location start = player.getLocation();
        Location end = start.clone().add(dir.clone().multiply(6.0));

        player.teleport(end);
        player.playSound(end, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.4f);

        // Electricity trail between start and end
        for (double d = 0; d <= 6.0; d += 0.5) {
            Location p = start.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 5, 0.2, 0.2, 0.2, 0.05);

            for (LivingEntity victim : p.getWorld().getNearbyLivingEntities(p, 1.5)) {
                if (victim.equals(player)) continue;
                victim.damage(2.0, player); // 1 heart
                // Cannot throw projectiles for 8s (Slowness/Weakness)
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 160, 2));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 1));
            }
        }

        currentCharges--;
        blinkCharges.put(player.getUniqueId(), currentCharges);

        if (currentCharges <= 0) {
            int cd = plugin.getConfig().getInt("weapons.luminescence_spear.primary_cooldown", 12);
            plugin.getCooldownManager().setCooldown(player, key, cd);
        } else {
            player.sendMessage(Component.text("✦ Blink Charges: " + currentCharges + "/3", NamedTextColor.YELLOW));
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.luminescence_spear.secondary_cooldown", 130);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "CrescentEclipse Armed", BossBar.Color.YELLOW, 10);

        crescentEclipseQueued.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        player.sendMessage(Component.text("✦ CrescentEclipse armed! Your next throw will mark the target for an orbital celestial beam.", NamedTextColor.GOLD));
        return true;
    }

    public void onTridentThrowHit(Player thrower, LivingEntity target) {
        long now = System.currentTimeMillis();

        // LightStealing: Throwing inflicts entity with Darkness for 10s (60s CD)
        long lastSteal = lightStealCooldown.getOrDefault(thrower.getUniqueId(), 0L);
        if (now - lastSteal > 60000L) {
            lightStealCooldown.put(thrower.getUniqueId(), now);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            thrower.sendMessage(Component.text("✦ LightStealing inflicted Darkness on target!", NamedTextColor.GRAY));
        }

        // CrescentEclipse trigger on throw
        if (Boolean.TRUE.equals(crescentEclipseQueued.remove(thrower.getUniqueId()))) {
            triggerCrescentEclipseBeam(thrower, target);
        }
    }

    private void triggerCrescentEclipseBeam(Player thrower, LivingEntity target) {
        target.sendMessage(Component.text("⚔ You are locked by CrescentEclipse! Stunned!", NamedTextColor.GOLD));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // Stun

        // 2s delay -> 4 hearts beam
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid()) return;

                Location loc = target.getLocation();
                loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.0f);

                for (int y = 0; y < 25; y++) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, y, 0), 5, 0.2, 0.2, 0.2, 0.02);
                }
                loc.getWorld().spawnParticle(Particle.FLASH, loc, 3);

                // 4 hearts damage (8 HP)
                target.damage(8.0, thrower);

                // Expanding ground shockwave
                for (int deg = 0; deg < 360; deg += 15) {
                    double rad = Math.toRadians(deg);
                    loc.getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(Math.cos(rad) * 4.0, 0.2, Math.sin(rad) * 4.0), 2);
                }

                // Stun 2s with Darkness for 4s
                for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 5.0)) {
                    if (e.equals(thrower)) continue;
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Bolt: Falling creates explosion based on fall distance / velocity (60s CD)
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            long now = System.currentTimeMillis();
            long lastBolt = boltCooldown.getOrDefault(victim.getUniqueId(), 0L);

            if (now - lastBolt > 60000L) {
                boltCooldown.put(victim.getUniqueId(), now);
                float fallDist = victim.getFallDistance();

                if (fallDist > 3.0f) {
                    Location loc = victim.getLocation();
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);

                    double dmg = Math.min(20.0, fallDist * 1.5);
                    for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 5.0)) {
                        if (e.equals(victim)) continue;
                        e.damage(dmg, victim);
                    }
                    victim.sendMessage(Component.text("✦ Bolt slam discharged from your fall! (" + String.format(java.util.Locale.US, "%.1f", dmg) + " damage)", NamedTextColor.YELLOW));
                }
            }
        }
    }
}
