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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Grim extends LegendaryWeapon {

    private final NamespacedKey killCountKey;
    private final Map<UUID, Boolean> hollowedOutArmed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> failedActionTarget = new ConcurrentHashMap<>();
    private final Map<UUID, List<LivingEntity>> airVariantMarked = new ConcurrentHashMap<>();
    private final Map<UUID, Long> airVariantMarkExpire = new ConcurrentHashMap<>();
    private final Set<UUID> isDivingAirVariant = ConcurrentHashMap.newKeySet();
    private final net.kyori.adventure.text.minimessage.MiniMessage miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

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
            
            int sharpnessLevel = Math.min(10, 5 + startingKills);
            meta.addEnchant(Enchantment.SHARPNESS, sharpnessLevel, true);
            
            meta.setCustomModelData(1007);
            item.setItemMeta(meta);
        }
        return item;
    }

    private final Map<UUID, Boolean> isChargingThrow = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> isChargingSonic = new ConcurrentHashMap<>();

    public void throwScythe(Player player) {
        // Alignment check
        if (!plugin.getAlignmentManager().canWield(player, requiredAlignment)) {
            player.sendMessage(Component.text("✦ Your alignment prevents you from channeling Grim Scythe!", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();

        // 1. If HollowedOut is armed -> Charges and fires a dark Sonic Boom!
        if (Boolean.TRUE.equals(hollowedOutArmed.get(uuid))) {
            if (Boolean.TRUE.equals(isChargingSonic.get(uuid))) return;

            isChargingSonic.put(uuid, true);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);

            // Charge for 40 ticks (2.0 seconds)
            new BukkitRunnable() {
                int ticks = 0;
                final int totalChargeTicks = 40;

                @Override
                public void run() {
                    ticks++;
                    if (!player.isOnline() || !isHoldingGrim(player) || !Boolean.TRUE.equals(hollowedOutArmed.get(uuid))) {
                        isChargingSonic.remove(uuid);
                        cancel();
                        return;
                    }

                    int pct = (ticks * 100) / totalChargeTicks;
                    player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<gradient:#4B0082:#9400D3><bold>✦ CHARGING SONIC BOOM [" + pct + "%] ✦</bold></gradient>"));

                    Location eye = player.getEyeLocation();
                    eye.getWorld().spawnParticle(Particle.SOUL, eye.clone().add(eye.getDirection().multiply(0.8)), 2, 0.1, 0.1, 0.1, 0.02);

                    if (ticks % 8 == 0) {
                        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.8f + (ticks * 0.02f));
                    }

                    if (ticks >= totalChargeTicks) {
                        isChargingSonic.remove(uuid);
                        hollowedOutArmed.remove(uuid);

                        // Fire Sonic Boom!
                        Vector dir = eye.getDirection().normalize();
                        player.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.0f);
                        player.getWorld().playSound(eye, Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.6f);

                        Particle.DustOptions darkGreen = new Particle.DustOptions(Color.fromRGB(0, 77, 0), 2.0f);
                        Particle.DustOptions grayGreen = new Particle.DustOptions(Color.fromRGB(85, 107, 47), 1.5f);

                        Location curr = eye.clone();
                        int stunDurationTicks = totalChargeTicks / 2; // Halved charge time = 20 ticks (1s)

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
                                // Stun target for halved the charge time (20 ticks = 1 second)
                                plugin.getStunManager().applyTrueStun(target, stunDurationTicks, "HOLLOWED STUN");
                                failedActionTarget.put(target.getUniqueId(), System.currentTimeMillis() + 15000L);
                            }
                        }

                        player.sendMessage(Component.text("✦ HollowedOut unleashed a dark Sonic Boom! Targets stunned for 1s!", NamedTextColor.DARK_GREEN));
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
            return;
        }

        // 2. Soultaking throw: check cooldown
        String cdKey = "grim_soultaking";
        if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
            double remaining = plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey);
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>✦ Scythe Throw on cooldown: " + String.format(java.util.Locale.US, "%.1f", remaining) + "s ✦</red>"));
            return;
        }

        if (Boolean.TRUE.equals(isChargingThrow.get(uuid))) return;

        isChargingThrow.put(uuid, true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, false));

        // Charge for 30 ticks (1.5 seconds)
        new BukkitRunnable() {
            int ticks = 0;
            final int totalChargeTicks = 30;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || !isHoldingGrim(player)) {
                    isChargingThrow.remove(uuid);
                    cancel();
                    return;
                }

                int pct = (ticks * 100) / totalChargeTicks;
                player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<gradient:#006400:#2E8B57><bold>✦ CHARGING SCYTHE THROW [" + pct + "%] ✦</bold></gradient>"));

                Location pLoc = player.getLocation().add(0, 0.5, 0);
                pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 3, 0.4, 0.1, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 77, 0), 1.3f));

                if (ticks % 6 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.0f + (ticks * 0.02f));
                }

                if (ticks >= totalChargeTicks) {
                    isChargingThrow.remove(uuid);

                    // Set cooldown for throw (40s)
                    plugin.getCooldownManager().setCooldown(player, cdKey, 40);
                    plugin.getBossBarManager().showPassiveCooldown(player, Grim.this, "Soultaking Throw", 40);

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
                            for (double angle = 0; angle < 360; angle += 60) {
                                double rad = Math.toRadians(angle + (step * 30));
                                Vector offset = new Vector(Math.cos(rad) * 0.7, Math.sin(rad) * 0.7, 0);
                                current.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset), 1, 0, 0, 0, 0, scytheDust);
                                current.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset.multiply(0.5)), 1, 0, 0, 0, 0, scytheGray);
                            }
                            current.getWorld().spawnParticle(Particle.SMOKE, current, 2, 0.1, 0.1, 0.1, 0.02);

                            for (LivingEntity target : current.getWorld().getNearbyLivingEntities(current, 1.5, e -> !e.equals(player))) {
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

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
        UUID uuid = player.getUniqueId();

        // 1. Air Variant "Press Again" Followup: Inflict Voidbreaker's Fallen status effect
        if (airVariantMarked.containsKey(uuid)) {
            Long expire = airVariantMarkExpire.get(uuid);
            if (expire != null && System.currentTimeMillis() <= expire) {
                List<LivingEntity> targets = airVariantMarked.remove(uuid);
                airVariantMarkExpire.remove(uuid);

                if (targets != null && !targets.isEmpty()) {
                    int count = 0;
                    for (LivingEntity t : targets) {
                        if (t != null && t.isValid() && !t.isDead()) {
                            plugin.getFallenManager().applyFallen(t);
                            t.getWorld().playSound(t.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.4f, 0.6f);
                            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
                            t.getWorld().spawnParticle(Particle.LARGE_SMOKE, t.getLocation().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4, 0.05);
                            t.getWorld().spawnParticle(Particle.SOUL, t.getLocation().add(0, 1.0, 0), 15, 0.3, 0.4, 0.3, 0.03);
                            count++;
                        }
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
                    player.sendMessage(miniMessage.deserialize("<dark_purple>✦ [AIR VARIANT] Inflicted Voidbreaker's Fallen status effect on " + count + " stunned enemies!</dark_purple>"));
                    return true;
                }
            } else {
                airVariantMarked.remove(uuid);
                airVariantMarkExpire.remove(uuid);
            }
        }

        // 2. Normal Secondary Activation (Cooldown check)
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Choose variant based on player state: Air vs Ground
        if (!player.isOnGround()) {
            return executeAirVariant(player, key);
        } else {
            return executeGroundVariant(player, key);
        }
    }

    /**
     * Ground Variant:
     * Summons a dark Gravity Vortex at target location.
     * Pulls enemies toward the center.
     * Inner 3-block field: Slowness III and -1 HP (0.5 heart) per second.
     * Climax: If N players/entities are trapped, fires 3 * N homing dark projectiles!
     */
    private boolean executeGroundVariant(Player player, String key) {
        int cd = plugin.getConfig().getInt("weapons.grim.secondary_cooldown", 35);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Dark Particle: Gravity Vortex", BossBar.Color.PURPLE, 5);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.2f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.6f);
        player.sendMessage(miniMessage.deserialize("<dark_purple>✦ Dark Particle [Ground Variant]: Threw Gravity Vortex! ✦</dark_purple>"));

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize().multiply(1.2);

        // Throwing a dark particle projectile
        new BukkitRunnable() {
            int ticks = 0;
            Location current = start.clone();

            @Override
            public void run() {
                ticks++;
                if (ticks > 40) { // Max range
                    spawnGravityVortex(player, current);
                    cancel();
                    return;
                }

                current.add(dir);
                current.getWorld().spawnParticle(Particle.LARGE_SMOKE, current, 3, 0.1, 0.1, 0.1, 0.01);
                current.getWorld().spawnParticle(Particle.SOUL, current, 2, 0.1, 0.1, 0.1, 0.02);
                current.getWorld().spawnParticle(Particle.DUST, current, 4, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.5f));

                // Check block hit
                if (current.getBlock().getType().isSolid()) {
                    spawnGravityVortex(player, current);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private void spawnGravityVortex(Player player, Location spawnLoc) {
        spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1.0);

        player.getWorld().playSound(spawnLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        player.getWorld().playSound(spawnLoc, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.6f);

        Set<LivingEntity> trappedEntities = Collections.synchronizedSet(new HashSet<>());

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // 4 seconds duration
            final Particle.DustOptions blackHoleDust = new Particle.DustOptions(Color.fromRGB(15, 15, 15), 3.5f);
            final Particle.DustOptions ringDust = new Particle.DustOptions(Color.fromRGB(80, 0, 30), 1.8f);

            @Override
            public void run() {
                ticks += 2;

                // Central Black Hole Orb visual - Bigger
                spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc, 30, 0.8, 0.8, 0.8, 0, blackHoleDust);
                spawnLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, spawnLoc, 12, 0.6, 0.6, 0.6, 0.02);
                spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 8, 0.5, 0.5, 0.5, 0.02);

                // 7x7 Ring on the ground (radius 3.5 = diameter 7)
                double radius = 3.5;
                for (int d = 0; d < 360; d += 12) {
                    double rad = Math.toRadians(d + ticks); // Rotating ring
                    Location ringPoint = spawnLoc.clone().add(Math.cos(rad) * radius, 0.15, Math.sin(rad) * radius);
                    spawnLoc.getWorld().spawnParticle(Particle.DUST, ringPoint, 1, 0, 0, 0, 0, ringDust);
                    // Inner dense vortex particles
                    if (d % 36 == 0) {
                        Location inner = spawnLoc.clone().add(Math.cos(rad) * (radius * Math.random()), Math.random() * 0.5, Math.sin(rad) * (radius * Math.random()));
                        spawnLoc.getWorld().spawnParticle(Particle.DUST, inner, 1, 0, 0, 0, 0, blackHoleDust);
                    }
                }

                // Ambient hum
                if (ticks % 20 == 0) {
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 1.0f, 0.7f);
                }

                // Gravitational pull & 7x7 damage/slowness
                for (LivingEntity e : spawnLoc.getWorld().getNearbyLivingEntities(spawnLoc, 7.0)) {
                    if (e.equals(player)) continue;

                    // Pull toward vortex center
                    Vector pull = spawnLoc.toVector().subtract(e.getLocation().toVector());
                    double dist = pull.length();
                    if (dist > 0.8) {
                        Vector vel = pull.normalize().multiply(0.38);
                        e.setVelocity(e.getVelocity().add(vel));
                    }

                    // Inside 3-block ring
                    if (dist <= 3.2) {
                        trappedEntities.add(e);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false));

                        // -1 HP (0.5 heart = 1.0 damage) every second (20 ticks)
                        if (ticks % 20 == 0) {
                            e.damage(1.0, player);
                            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.6f, 1.5f);
                            e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, e.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }

                // Vortex climax: fire 3x projectiles per trapped player!
                if (ticks >= maxTicks) {
                    cancel();

                    List<LivingEntity> validTargets = new ArrayList<>();
                    for (LivingEntity e : trappedEntities) {
                        if (e != null && e.isValid() && !e.isDead() && e.getWorld().equals(spawnLoc.getWorld()) && e.getLocation().distance(spawnLoc) <= 12.0) {
                            validTargets.add(e);
                        }
                    }

                    int count = validTargets.size();
                    if (count > 0) {
                        int totalProjectiles = count * 3;
                        player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#8B0000><bold>✦ GRAVITY VORTEX UNLEASHES " + totalProjectiles + " DARK PROJECTILES! (" + count + " trapped) ✦</bold></gradient>"));
                        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.6f);

                        // Fire projectiles at targets
                        fireVortexProjectiles(player, spawnLoc, validTargets, totalProjectiles);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        return true;
    }

    private void fireVortexProjectiles(Player player, Location origin, List<LivingEntity> targets, int totalProjectiles) {
        new BukkitRunnable() {
            int fired = 0;

            @Override
            public void run() {
                if (fired >= totalProjectiles || targets.isEmpty()) {
                    cancel();
                    return;
                }

                LivingEntity target = targets.get(fired % targets.size());
                fired++;

                launchSingleDarkProjectile(player, origin.clone().add(0, 0.5, 0), target);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void launchSingleDarkProjectile(Player player, Location start, LivingEntity target) {
        new BukkitRunnable() {
            Location curr = start.clone();
            int steps = 0;
            final Particle.DustOptions projDust = new Particle.DustOptions(Color.fromRGB(20, 0, 40), 1.8f);

            @Override
            public void run() {
                steps++;
                if (!target.isValid() || target.isDead() || steps > 40) {
                    cancel();
                    return;
                }

                Vector dir = target.getEyeLocation().toVector().subtract(curr.toVector()).normalize().multiply(1.2);
                curr.add(dir);

                // Projectile visual trail
                curr.getWorld().spawnParticle(Particle.DUST, curr, 3, 0.1, 0.1, 0.1, 0, projDust);
                curr.getWorld().spawnParticle(Particle.SOUL, curr, 1, 0.05, 0.05, 0.05, 0.01);
                curr.getWorld().spawnParticle(Particle.SMOKE, curr, 2, 0.05, 0.05, 0.05, 0.01);

                if (curr.distanceSquared(target.getEyeLocation()) <= 2.25) {
                    cancel();
                    target.damage(3.0, player);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 1.4f);
                    target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.4, 0.3, 0.05);
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0), 4, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Air Variant:
     * Chains/dives downward with high velocity.
     * Ground impact creates concentric shockwaves, blows back enemies, deals 6 HP (3 hearts),
     * and inflicts True Stun for 2s.
     * Marks hit targets ready for Fallen: pressing Secondary again within 4s inflicts Voidbreaker's Fallen!
     * Cooldown Reset: If at least 1 hit player is under 4 hearts (<= 8.0 HP), resets cooldown immediately!
     */
    private boolean executeAirVariant(Player player, String key) {
        int cd = plugin.getConfig().getInt("weapons.grim.secondary_cooldown", 35);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        isDivingAirVariant.add(player.getUniqueId());

        // Dive downward with high velocity (removed forward momentum, just straight down)
        Vector dive = new Vector(0, -1.8, 0);
        player.setVelocity(dive);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 0.6f);
        player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>✦ Dark Particle [Air Variant]: AIR SLAM! ✦</bold></gradient>"));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                if (!player.isOnline() || ticks > 40) {
                    isDivingAirVariant.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Dark trail
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation(), 4, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.8f));

                // Check ground impact
                if (player.isOnGround() || (ticks > 5 && player.getVelocity().getY() >= -0.1)) {
                    isDivingAirVariant.remove(player.getUniqueId());
                    cancel();
                    triggerAirSlamImpact(player, key);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private void triggerAirSlamImpact(Player player, String key) {
        Location impact = player.getLocation();

        // Audio
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
        impact.getWorld().playSound(impact, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
        impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);

        // Concentric shockwave rings (expanding outward from 1.0 to 5.0 blocks)
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(25, 25, 25), 2.2f);
        Particle.DustOptions crimsonDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.8f);

        for (double r = 1.0; r <= 5.0; r += 0.8) {
            for (int d = 0; d < 360; d += 15) {
                double rad = Math.toRadians(d);
                Location p = impact.clone().add(Math.cos(rad) * r, 0.15, Math.sin(rad) * r);
                impact.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, (r > 3.0) ? crimsonDust : darkDust);
            }
        }
        impact.getWorld().spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.5, 0), 40, 1.5, 0.4, 1.5, 0.1, Material.OBSIDIAN.createBlockData());

        List<LivingEntity> hitTargets = new ArrayList<>();
        boolean lowHealthHit = false;

        for (LivingEntity e : impact.getWorld().getNearbyLivingEntities(impact, 5.0)) {
            if (e.equals(player)) continue;

            // Damage
            e.damage(6.0, player); // 3 hearts

            // Knockback
            Vector kb = e.getLocation().toVector().subtract(impact.toVector()).normalize().multiply(1.2).setY(0.45);
            e.setVelocity(kb);

            // True Stun for 40 ticks (2 seconds)
            plugin.getStunManager().applyTrueStun(e, 40, "Dark Particle Air Slam");

            // Tag as ready for Fallen
            hitTargets.add(e);

            // Execution reset condition: hit at least 1 person under 4 hearts (<= 8.0 HP)
            if (e.getHealth() <= 8.0) {
                lowHealthHit = true;
            }
        }

        // Execution Cooldown Reset!
        if (lowHealthHit) {
            plugin.getCooldownManager().resetCooldown(player, key);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.4f);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.8f);
            player.sendMessage(miniMessage.deserialize("<gold>✦ [GRIM EXECUTION] <dark_red><bold>Hit low-health prey (< 4 hearts)! Dark Particle Cooldown RESET!</bold></dark_red> ✦</gold>"));
        }

        // Mark for "Press again" Fallen infliction
        if (!hitTargets.isEmpty()) {
            airVariantMarked.put(player.getUniqueId(), hitTargets);
            airVariantMarkExpire.put(player.getUniqueId(), System.currentTimeMillis() + 4000L); // 4 seconds

            player.sendActionBar(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>✦ PRESS SECONDARY AGAIN TO INFLICT FALLEN! ✦</bold></gradient>"));
            player.sendMessage(miniMessage.deserialize("<light_purple>✦ Stunned " + hitTargets.size() + " enemies! Press Secondary again within 4s to inflict Voidbreaker's Fallen!</light_purple>"));

            // Schedule mark expiry cleanup after 4 seconds
            new BukkitRunnable() {
                @Override
                public void run() {
                    airVariantMarked.remove(player.getUniqueId());
                    airVariantMarkExpire.remove(player.getUniqueId());
                }
            }.runTaskLater(plugin, 80L);
        }
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
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Cancel fall damage if diving during Air Variant
        if (isDivingAirVariant.contains(victim.getUniqueId()) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
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
        // Reaper: Sharpness increases up to 10
        int sharpnessLevel = Math.min(10, 5 + kills);
        meta.addEnchant(Enchantment.SHARPNESS, sharpnessLevel, true);
        weapon.setItemMeta(meta);

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
