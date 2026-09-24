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
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#2F4F4F:#D3D3D3:#2F4F4F><bold>Voidbreaker</bold></gradient>"),
                Material.MACE,
                Alignment.NULLIFIED,
                "Fractured",
                "Bound");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Voidfeels", "Rifted", "Bound"), "Fractured", "Crumble"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            // Always Density 6 (not Breach) as requested
            try {
                meta.addEnchant(Enchantment.WIND_BURST, 3, true);
                meta.addEnchant(Enchantment.DENSITY, 6, true);
            } catch (Throwable ignored) {}
            meta.setCustomModelData(1006);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createItemWithEnchant(boolean useDensity) {
        return createItem();
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
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

        // Perform dash directly towards crosshair (full 3D direction, works in air!)
        Vector dash = player.getEyeLocation().getDirection().normalize().multiply(1.85);
        player.setVelocity(dash);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.0, 0), 30, 0.4, 0.4, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05, 0.5f);

        player.sendMessage(Component.text("✦ Bound Dash! Charges remaining: " + charges + "/3", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.voidbreaker.secondary_cooldown", 75);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Fractured Strike Armed", BossBar.Color.PURPLE, 15);

        fracturedArmed.put(player.getUniqueId(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.6f);
        player.sendMessage(Component.text("✦ Fractured armed! Your next strike inflicts the devastating Fallen debuff & stuns for 2s.", NamedTextColor.DARK_PURPLE));
        return true;
    }

    @Override
    public String getCustomActiveStatus(Player player, boolean secondary) {
        if (!secondary) {
            String boundKey = id + "_primary";
            if (plugin.getCooldownManager().isActive(player, boundKey)) {
                int charges = boundCharges.getOrDefault(player.getUniqueId(), 0);
                double rem = plugin.getCooldownManager().getActiveRemainingSeconds(player, boundKey);
                return charges + "/3 " + String.format(java.util.Locale.US, "%.1f", rem) + "ꜱ(ᴘᴇʀ ᴄʜᴀʀɢᴇ)";
            }
        }
        return null;
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
            plugin.getBossBarManager().showPassiveCooldown(player, this, "Rifted", (int) (cdDuration / 1000));
            Vector launch = player.getEyeLocation().getDirection().normalize().multiply(2.2);
            player.setVelocity(launch);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.4f);
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 25, 0.5, 0.5, 0.5, 0.05, 0.5f);
            player.sendMessage(Component.text("✦ Rifted Crosshair Launch!", NamedTextColor.DARK_PURPLE));
            return;
        }

        // Voidfeels: standard double jump (5s CD)
        long lastDJ = doubleJumpCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDJ < 5000L) return;

        doubleJumpCooldown.put(player.getUniqueId(), now);
        plugin.getBossBarManager().showPassiveCooldown(player, this, "Voidfeels", 5);
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
                attacker.sendMessage(Component.text("âœ¦ Bound: +1 Dash Charge from hit! (" + (c + 1) + "/3)", NamedTextColor.LIGHT_PURPLE));
            }
        }

        // Halve Rifted cooldown on mace slam
        long currentCD = riftedCDDuration.getOrDefault(attacker.getUniqueId(), 30000L);
        riftedCDDuration.put(attacker.getUniqueId(), Math.max(3750L, currentCD / 2));

        // Fractured: Applies Fallen debuff + 2s stun
        if (Boolean.TRUE.equals(fracturedArmed.remove(attacker.getUniqueId()))) {
            plugin.getFallenManager().applyFallen(target);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // 2s stun
            attacker.sendMessage(Component.text("âœ¦ Fractured strike landed! Target infected with Fallen!", NamedTextColor.DARK_PURPLE));
        }

        // Crumble: 1/3, 2/3, 3/3, 4th hit doubles damage with aftershock
        int hits = crumbleHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
        if (hits < 4) {
            crumbleHits.put(attacker.getUniqueId(), hits);
            attacker.sendMessage(Component.text("✦ Crumble: " + hits + "/3", NamedTextColor.LIGHT_PURPLE));
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f + (hits * 0.2f));

            // Dust particles at target's feet â€” more intense with each hit
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
            // 4th hit: Seismic shockwave detonates after 2 seconds (40 ticks)!
            crumbleHits.put(attacker.getUniqueId(), 0);
            Location shockwaveLoc = target.getLocation().clone();

            attacker.sendMessage(Component.text("✦ Crumble: 4th hit landed! Seismic shockwave erupting in 2s!", NamedTextColor.LIGHT_PURPLE));
            target.sendMessage(Component.text("⚠ Seismic shockwave building beneath your feet!", NamedTextColor.RED));
            shockwaveLoc.getWorld().playSound(shockwaveLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f);

            // 2-second charging particle animation
            new org.bukkit.scheduler.BukkitRunnable() {
                int t = 0;
                final double maceDamage = Math.max(14.0, damage * 1.5);

                @Override
                public void run() {
                    t += 2;
                    if (t < 40) {
                        // Pulsing warning ground particles
                        double pulseRadius = 0.5 + (t * 0.05);
                        for (int d = 0; d < 360; d += 30) {
                            double rad = Math.toRadians(d);
                            Location p = shockwaveLoc.clone().add(Math.cos(rad) * pulseRadius, 0.1, Math.sin(rad) * pulseRadius);
                            shockwaveLoc.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 80, 80), 1.4f));
                        }
                        if (t % 10 == 0) {
                            shockwaveLoc.getWorld().playSound(shockwaveLoc, Sound.BLOCK_HEAVY_CORE_STEP, 1.0f, 0.8f + (t * 0.02f));
                        }
                        return;
                    }

                    // Detonation after 2 seconds (40 ticks)!
                    shockwaveLoc.getWorld().playSound(shockwaveLoc, Sound.BLOCK_HEAVY_CORE_FALL, 2.0f, 0.5f);
                    shockwaveLoc.getWorld().playSound(shockwaveLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
                    shockwaveLoc.getWorld().playSound(shockwaveLoc, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);
                    shockwaveLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, shockwaveLoc.clone().add(0, 0.5, 0), 1);

                    Particle.DustOptions shockDustDark = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 2.2f);
                    Particle.DustOptions shockDustLight = new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 1.8f);

                    for (double r = 1.0; r <= 4.5; r += 0.8) {
                        for (int d = 0; d < 360; d += 15) {
                            double rad = Math.toRadians(d);
                            Location p = shockwaveLoc.clone().add(Math.cos(rad) * r, 0.15, Math.sin(rad) * r);
                            shockwaveLoc.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, (r > 2.2) ? shockDustLight : shockDustDark);
                        }
                    }
                    shockwaveLoc.getWorld().spawnParticle(Particle.BLOCK, shockwaveLoc.clone().add(0, 0.5, 0), 50, 1.8, 0.5, 1.8, 0.15, Material.GRAVEL.createBlockData());
                    shockwaveLoc.getWorld().spawnParticle(Particle.BLOCK, shockwaveLoc.clone().add(0, 0.5, 0), 30, 1.5, 0.4, 1.5, 0.1, Material.COBBLESTONE.createBlockData());

                    for (LivingEntity e : shockwaveLoc.getWorld().getNearbyLivingEntities(shockwaveLoc, 4.5)) {
                        if (e.equals(attacker)) continue;
                        e.damage(maceDamage, attacker);
                        Vector kb = e.getLocation().toVector().subtract(shockwaveLoc.toVector()).normalize().multiply(0.9).setY(0.45);
                        e.setVelocity(kb);
                    }

                    if (attacker.isOnline()) {
                        attacker.sendMessage(Component.text("✦ CRUMBLE SEISMIC SHOCKWAVE DETONATED!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                    }
                    cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Crumble: Resets after taking damage of any kind
        Integer hits = crumbleHits.remove(victim.getUniqueId());
        if (hits != null && hits > 0) {
            victim.sendMessage(Component.text("âœ¦ Crumble hit counter reset by incoming damage!", NamedTextColor.RED));
        }
    }
}
