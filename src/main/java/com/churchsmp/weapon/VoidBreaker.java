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
    private final Map<UUID, UUID> boundMarkedTarget = new ConcurrentHashMap<>();

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

            // Base attack damage modifier (+11.0 = 12.0 total base attack damage)
            NamespacedKey dmgKey = new NamespacedKey(plugin, "voidbreaker_damage");
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(dmgKey, 11.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

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

        // Mark target along dash line with F
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 1.0; d <= 6.0; d += 0.5) {
            Location check = eye.clone().add(dir.clone().multiply(d));
            for (LivingEntity nearby : check.getWorld().getNearbyLivingEntities(check, 1.8)) {
                if (!nearby.equals(player)) {
                    boundMarkedTarget.put(player.getUniqueId(), nearby.getUniqueId());
                    nearby.getWorld().spawnParticle(Particle.SONIC_BOOM, nearby.getLocation().add(0, 1.0, 0), 1);
                    player.sendMessage(Component.text("✦ Bound (F) marked " + nearby.getName() + "! Crumble seismic shockwave locked!", NamedTextColor.LIGHT_PURPLE));
                    break;
                }
            }
            if (boundMarkedTarget.containsKey(player.getUniqueId())) break;
        }

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
        boolean isMaceSlam = attacker.getFallDistance() > 0.5f || !attacker.isOnGround();

        // Bound & Crumble only active when mace slam is hit
        if (isMaceSlam) {
            // Recharge ALL dash charges (3/3) on mace slam hit
            boundCharges.put(attacker.getUniqueId(), 3);
            attacker.sendMessage(Component.text("✦ Bound: All 3 Dash Charges recharged from Mace Slam! (3/3)", NamedTextColor.LIGHT_PURPLE));
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.6f);

            // Halve Rifted cooldown on mace slam
            long currentCD = riftedCDDuration.getOrDefault(attacker.getUniqueId(), 30000L);
            riftedCDDuration.put(attacker.getUniqueId(), Math.max(3750L, currentCD / 2));

            // Crumble: 1/3, 2/3, 3/3, 4th hit doubles damage with aftershock
            int hits = crumbleHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
            if (hits < 4) {
                crumbleHits.put(attacker.getUniqueId(), hits);
                attacker.sendMessage(Component.text("✦ Crumble: " + hits + "/3", NamedTextColor.LIGHT_PURPLE));
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f + (hits * 0.2f));

                Location feet = target.getLocation();
                int particleCount = 8 + (hits * 6);
                double spread = 0.3 + (hits * 0.15);
                Particle.DustOptions dustColor = new Particle.DustOptions(
                        org.bukkit.Color.fromRGB(140, 140, 140), 1.0f + (hits * 0.3f));
                feet.getWorld().spawnParticle(Particle.DUST, feet.add(0, 0.1, 0), particleCount, spread, 0.05, spread, 0, dustColor);
                feet.getWorld().spawnParticle(Particle.BLOCK, feet, particleCount / 2,
                        spread, 0.1, spread, 0.1, Material.GRAVEL.createBlockData());

                for (int i = 0; i < hits * 6; i++) {
                    double angle = (2 * Math.PI / (hits * 6)) * i;
                    double rx = Math.cos(angle) * (0.6 + hits * 0.2);
                    double rz = Math.sin(angle) * (0.6 + hits * 0.2);
                    feet.getWorld().spawnParticle(Particle.DUST,
                            target.getLocation().add(rx, 0.05, rz), 1, 0, 0, 0, 0, dustColor);
                }
            } else {
                // 4th hit: Seismic shockwave detonates directly at target after 2 seconds (40 ticks)!
                crumbleHits.put(attacker.getUniqueId(), 0);
                UUID markedId = boundMarkedTarget.remove(attacker.getUniqueId());
                org.bukkit.entity.Entity markedEnt = (markedId != null) ? org.bukkit.Bukkit.getEntity(markedId) : null;
                final LivingEntity finalTarget = (markedEnt instanceof LivingEntity le && le.isValid() && !le.isDead()) ? le : target;
                final Location initialLoc = finalTarget.getLocation().clone();

                attacker.sendMessage(Component.text("✦ Crumble: 4th slam landed! Seismic shockwave locking directly onto target in 2s!", NamedTextColor.LIGHT_PURPLE));
                finalTarget.sendMessage(Component.text("⚠ Seismic shockwave locked directly onto you!", NamedTextColor.RED));
                initialLoc.getWorld().playSound(initialLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f);

                new org.bukkit.scheduler.BukkitRunnable() {
                    int t = 0;
                    final double maceDamage = Math.max(18.0, damage * 1.8);

                    @Override
                    public void run() {
                        t += 2;
                        Location currentLoc = (finalTarget.isValid() && !finalTarget.isDead()) ? finalTarget.getLocation() : initialLoc;

                        if (t < 40) {
                            double pulseRadius = 0.5 + (t * 0.05);
                            for (int d = 0; d < 360; d += 30) {
                                double rad = Math.toRadians(d);
                                Location p = currentLoc.clone().add(Math.cos(rad) * pulseRadius, 0.1, Math.sin(rad) * pulseRadius);
                                currentLoc.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                                        new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 80, 80), 1.4f));
                            }
                            if (t % 10 == 0) {
                                currentLoc.getWorld().playSound(currentLoc, Sound.BLOCK_HEAVY_CORE_STEP, 1.0f, 0.8f + (t * 0.02f));
                            }
                            return;
                        }

                        // Detonation directly at target!
                        Location detonateLoc = (finalTarget.isValid() && !finalTarget.isDead()) ? finalTarget.getLocation() : initialLoc;
                        detonateLoc.getWorld().playSound(detonateLoc, Sound.BLOCK_HEAVY_CORE_FALL, 2.0f, 0.5f);
                        detonateLoc.getWorld().playSound(detonateLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
                        detonateLoc.getWorld().playSound(detonateLoc, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);
                        detonateLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, detonateLoc.clone().add(0, 0.5, 0), 1);

                        Particle.DustOptions shockDustDark = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 2.2f);
                        Particle.DustOptions shockDustLight = new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 1.8f);

                        for (double r = 1.0; r <= 4.5; r += 0.8) {
                            for (int d = 0; d < 360; d += 15) {
                                double rad = Math.toRadians(d);
                                Location p = detonateLoc.clone().add(Math.cos(rad) * r, 0.15, Math.sin(rad) * r);
                                detonateLoc.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, (r > 2.2) ? shockDustLight : shockDustDark);
                            }
                        }
                        detonateLoc.getWorld().spawnParticle(Particle.BLOCK, detonateLoc.clone().add(0, 0.5, 0), 50, 1.8, 0.5, 1.8, 0.15, Material.GRAVEL.createBlockData());
                        detonateLoc.getWorld().spawnParticle(Particle.BLOCK, detonateLoc.clone().add(0, 0.5, 0), 30, 1.5, 0.4, 1.5, 0.1, Material.COBBLESTONE.createBlockData());

                        for (LivingEntity e : detonateLoc.getWorld().getNearbyLivingEntities(detonateLoc, 4.5)) {
                            if (e.equals(attacker)) continue;
                            e.damage(maceDamage, attacker);
                            Vector kb = e.getLocation().toVector().subtract(detonateLoc.toVector()).normalize().multiply(1.1).setY(0.5);
                            e.setVelocity(kb);
                        }

                        if (attacker.isOnline()) {
                            attacker.sendMessage(Component.text("✦ CRUMBLE SEISMIC SHOCKWAVE DETONATED DIRECTLY AT TARGET!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                        }
                        cancel();
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
        }

        // Fractured: Applies Fallen debuff + 2s stun
        if (Boolean.TRUE.equals(fracturedArmed.remove(attacker.getUniqueId()))) {
            plugin.getFallenManager().applyFallen(target);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // 2s stun
            attacker.sendMessage(Component.text("✦ Fractured strike landed! Target infected with Fallen!", NamedTextColor.DARK_PURPLE));
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
