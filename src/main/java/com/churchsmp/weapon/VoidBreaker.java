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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoidBreaker extends LegendaryWeapon {

    private final Map<UUID, Integer> crumbleHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> doubleJumpCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> riftedCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> riftedCDDuration = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> boundCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDashTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> fracturedArmed = new ConcurrentHashMap<>();

    public VoidBreaker(ChurchSMP plugin) {
        super(plugin,
                "voidbreaker",
                new String[]{"void_breaker", "abyssal_shatter"},
                Component.text("Voidbreaker", TextColor.color(0x9400D3)).decorate(TextDecoration.BOLD),
                Material.MACE,
                Alignment.NULLIFIED,
                "Fractured",
                "Bound");
    }

    @Override
    public ItemStack createItem() {
        return createItemWithEnchant(true); // Default to Density 6
    }

    public ItemStack createItemWithEnchant(boolean useDensity) {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("The Abandoned Unknowing.", TextColor.color(0xDA70D6)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Voidfeels: ", NamedTextColor.DARK_AQUA).append(Component.text("Double Jump in mid-air. (5s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Crumble: ", NamedTextColor.DARK_AQUA).append(Component.text("Slam counter (1/3, 2/3, 3/3); 4th hit doubles damage with aftershock; missed shock rebounds for half. Resets on hit.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Rifted: ", NamedTextColor.DARK_AQUA).append(Component.text("Sneaking Double Jump launches at crosshair. (30s CD, halved each slam)", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Fractured: ", NamedTextColor.LIGHT_PURPLE).append(Component.text("Next hit embeds Fallen debuff + 2s stun. (75s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Bound: ", NamedTextColor.LIGHT_PURPLE).append(Component.text("Dash (3/3 charges, 1s cooldown); landing mace hit grants +1 charge. Active for 10s.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);

            // 1.21 Mace Enchantments: Wind Burst 3, Density 6 or Breach 6
            try {
                meta.addEnchant(Enchantment.WIND_BURST, 3, true);
                if (useDensity) {
                    meta.addEnchant(Enchantment.DENSITY, 6, true);
                } else {
                    meta.addEnchant(Enchantment.BREACH, 6, true);
                }
            } catch (Throwable ignored) {}

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

        int cd = plugin.getConfig().getInt("weapons.voidbreaker.primary_cooldown", 75);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Fractured Strike Armed", BossBar.Color.PURPLE, 15);

        fracturedArmed.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.6f);
        player.sendMessage(Component.text("✦ Fractured armed! Your next strike inflicts the devastating Fallen debuff & stuns for 2s.", NamedTextColor.DARK_PURPLE));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        long now = System.currentTimeMillis();

        // Check active duration
        if (!plugin.getCooldownManager().isActive(player, key)) {
            if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

            // Start Bound ability (10s active duration)
            plugin.getCooldownManager().setActiveDuration(player, key, 10);
            plugin.getBossBarManager().showActiveCountdown(player, "Bound Active (Dash)", BossBar.Color.PURPLE, 10);
            boundCharges.put(player.getUniqueId(), 3);
        }

        int charges = boundCharges.getOrDefault(player.getUniqueId(), 3);
        if (charges <= 0) {
            player.sendMessage(Component.text("✦ Bound: Out of dash charges! Land a mace hit to gain +1 charge.", NamedTextColor.RED));
            return false;
        }

        // 1s cooldown between dashes
        long lastDash = lastDashTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDash < 1000L) {
            return false;
        }

        lastDashTime.put(player.getUniqueId(), now);
        charges--;
        boundCharges.put(player.getUniqueId(), charges);

        // Perform dash
        Vector dash = player.getLocation().getDirection().normalize().multiply(1.7).setY(0.2);
        player.setVelocity(dash);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.0, 0), 25, 0.4, 0.4, 0.4, 0.1);

        player.sendMessage(Component.text("✦ Bound Dash! Charges remaining: " + charges + "/3", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    public void handleDoubleJump(Player player) {
        long now = System.currentTimeMillis();

        if (player.isSneaking()) {
            // Rifted: launches at crosshair (30s CD, halved each mace slam)
            long lastRifted = riftedCooldown.getOrDefault(player.getUniqueId(), 0L);
            long cdDuration = riftedCDDuration.getOrDefault(player.getUniqueId(), 30000L);

            if (now - lastRifted < cdDuration) {
                double remaining = Math.round((cdDuration - (now - lastRifted)) / 100.0) / 10.0;
                player.sendMessage(Component.text("Rifted launch on cooldown: " + remaining + "s", NamedTextColor.RED));
                return;
            }

            riftedCooldown.put(player.getUniqueId(), now);
            Vector launch = player.getEyeLocation().getDirection().normalize().multiply(2.2);
            player.setVelocity(launch);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.4f);
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
            player.sendMessage(Component.text("✦ Rifted Crosshair Launch!", NamedTextColor.DARK_PURPLE));
            return;
        }

        // Voidfeels: standard double jump (5s CD)
        long lastDJ = doubleJumpCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDJ < 5000L) return;

        doubleJumpCooldown.put(player.getUniqueId(), now);
        player.setVelocity(new Vector(player.getVelocity().getX(), 0.9, player.getVelocity().getZ()));
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.2f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Bound: +1 charge if hit landed while active
        String boundKey = id + "_secondary";
        if (plugin.getCooldownManager().isActive(attacker, boundKey)) {
            int c = boundCharges.getOrDefault(attacker.getUniqueId(), 0);
            if (c < 3) {
                boundCharges.put(attacker.getUniqueId(), c + 1);
                attacker.sendMessage(Component.text("✦ Bound: +1 Dash Charge from hit! (" + (c + 1) + "/3)", NamedTextColor.LIGHT_PURPLE));
            }
        }

        // Halve Rifted cooldown on mace slam
        long currentCD = riftedCDDuration.getOrDefault(attacker.getUniqueId(), 30000L);
        riftedCDDuration.put(attacker.getUniqueId(), Math.max(3750L, currentCD / 2));

        // Fractured: Applies Fallen debuff + 2s stun
        if (Boolean.TRUE.equals(fracturedArmed.remove(attacker.getUniqueId()))) {
            plugin.getFallenManager().applyFallen(target);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // 2s stun
            attacker.sendMessage(Component.text("✦ Fractured strike landed! Target infected with Fallen!", NamedTextColor.DARK_PURPLE));
        }

        // Crumble: 1/3, 2/3, 3/3, 4th hit doubles damage with aftershock
        int hits = crumbleHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
        if (hits < 4) {
            crumbleHits.put(attacker.getUniqueId(), hits);
            attacker.sendMessage(Component.text("✦ Crumble: " + hits + "/3", NamedTextColor.LIGHT_PURPLE));
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f + (hits * 0.2f));

            // Dust particles at target's feet — more intense with each hit
            Location feet = target.getLocation();
            int particleCount = 8 + (hits * 6); // 14, 20, 26 particles
            double spread = 0.3 + (hits * 0.15); // expanding ring
            Particle.DustOptions dustColor = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(140, 140, 140), 1.0f + (hits * 0.3f)); // grey, growing size
            feet.getWorld().spawnParticle(Particle.DUST, feet.add(0, 0.1, 0), particleCount, spread, 0.05, spread, 0, dustColor);
            feet.getWorld().spawnParticle(Particle.BLOCK, feet, particleCount / 2,
                    spread, 0.1, spread, 0.1, Material.GRAVEL.createBlockData());

            // Ring effect at feet for visual clarity
            for (int i = 0; i < hits * 6; i++) {
                double angle = (2 * Math.PI / (hits * 6)) * i;
                double rx = Math.cos(angle) * (0.6 + hits * 0.2);
                double rz = Math.sin(angle) * (0.6 + hits * 0.2);
                feet.getWorld().spawnParticle(Particle.DUST,
                        target.getLocation().add(rx, 0.05, rz), 1, 0, 0, 0, 0, dustColor);
            }
        } else {
            // 4th hit: Double damage with aftershock explosion
            crumbleHits.put(attacker.getUniqueId(), 0);

            // Big dust burst at target's feet FIRST before the explosion
            Location feet = target.getLocation();
            Particle.DustOptions bigDust = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(90, 90, 90), 2.5f);
            feet.getWorld().spawnParticle(Particle.DUST, feet.add(0, 0.1, 0), 60, 1.2, 0.1, 1.2, 0, bigDust);
            feet.getWorld().spawnParticle(Particle.BLOCK, feet, 40,
                    1.5, 0.2, 1.5, 0.2, Material.GRAVEL.createBlockData());

            // Expanding dust ring at feet
            for (int i = 0; i < 24; i++) {
                double angle = (2 * Math.PI / 24) * i;
                double rx = Math.cos(angle) * 1.5;
                double rz = Math.sin(angle) * 1.5;
                feet.getWorld().spawnParticle(Particle.DUST,
                        target.getLocation().add(rx, 0.1, rz), 2, 0, 0, 0, 0, bigDust);
            }

            // Then the actual explosion + damage
            target.damage(damage, attacker); // Double hit

            Location loc = target.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);

            for (LivingEntity e : loc.getWorld().getNearbyLivingEntities(loc, 4.0)) {
                if (e.equals(attacker)) continue;
                e.damage(8.0, attacker);
            }
            attacker.sendMessage(Component.text("✦ CRUMBLE 4th HIT AFTERSHOCK!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Crumble: Resets after taking damage of any kind
        Integer hits = crumbleHits.remove(victim.getUniqueId());
        if (hits != null && hits > 0) {
            victim.sendMessage(Component.text("✦ Crumble hit counter reset by incoming damage!", NamedTextColor.RED));
        }
    }
}
