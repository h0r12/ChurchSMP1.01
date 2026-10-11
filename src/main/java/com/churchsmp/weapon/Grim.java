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
import org.bukkit.World;
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
                                target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 5, 0.2, 0.25, 0.2, 0.02);

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
        pLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, pLoc.clone().add(0, 1.0, 0), 5, 0.25, 0.25, 0.25, 0.02);
        pLoc.getWorld().spawnParticle(Particle.SOUL, pLoc.clone().add(0, 0.5, 0), 4, 0.2, 0.2, 0.2, 0.01);

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
                            t.getWorld().spawnParticle(Particle.LARGE_SMOKE, t.getLocation().add(0, 1.0, 0), 6, 0.25, 0.25, 0.25, 0.02);
                            t.getWorld().spawnParticle(Particle.SOUL, t.getLocation().add(0, 1.0, 0), 5, 0.2, 0.2, 0.2, 0.02);
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

    private void spawnGravityVortex(Player player, Location hitLoc) {
        // Snap to ground directly beneath hit location (preserving caves/indoor ceilings)
        Location groundLoc = hitLoc.clone();
        for (int dy = 0; dy <= 4; dy++) {
            Location check = groundLoc.clone().subtract(0, dy, 0);
            if (check.getBlock().getType().isSolid()) {
                groundLoc = check.add(0, 1.0, 0);
                break;
            }
        }
        final Location spawnLoc = groundLoc;

        player.getWorld().playSound(spawnLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        player.getWorld().playSound(spawnLoc, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.6f);

        Set<LivingEntity> trappedEntities = Collections.synchronizedSet(new HashSet<>());

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // 4 seconds duration
            final Particle.DustOptions blackHoleDust = new Particle.DustOptions(Color.fromRGB(15, 15, 15), 1.6f);
            final Particle.DustOptions ringDust = new Particle.DustOptions(Color.fromRGB(180, 0, 50), 1.3f);
            final Particle.DustOptions purpleAura = new Particle.DustOptions(Color.fromRGB(160, 0, 240), 1.3f);

            @Override
            public void run() {
                ticks += 2;

                // Central Black Hole Orb visual - Swirling Void Core
                spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc, 6, 0.3, 0.3, 0.3, 0, blackHoleDust);
                spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc, 3, 0.25, 0.25, 0.25, 0, purpleAura);
                spawnLoc.getWorld().spawnParticle(Particle.SMOKE, spawnLoc, 2, 0.2, 0.2, 0.2, 0.01);
                spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc, 2, 0.2, 0.2, 0.2, 0.02);
                spawnLoc.getWorld().spawnParticle(Particle.WITCH, spawnLoc, 2, 0.2, 0.2, 0.2, 0.02);

                // 7x7 Ring on the ground (radius 3.5 = diameter 7)
                double radius = 3.5;
                for (int d = 0; d < 360; d += 24) {
                    double rad = Math.toRadians(d + ticks * 2); // Fast rotating ring
                    Location ringPoint = spawnLoc.clone().add(Math.cos(rad) * radius, 0.15, Math.sin(rad) * radius);
                    spawnLoc.getWorld().spawnParticle(Particle.DUST, ringPoint, 1, 0, 0, 0, 0, ringDust);
                    if (d % 48 == 0) {
                        Location inner = spawnLoc.clone().add(Math.cos(rad) * (radius * Math.random()), Math.random() * 0.3, Math.sin(rad) * (radius * Math.random()));
                        spawnLoc.getWorld().spawnParticle(Particle.DUST, inner, 1, 0, 0, 0, 0, blackHoleDust);
                        spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, inner, 1, 0, 0, 0, 0.01);
                    }
                }

                // Ambient hum
                if (ticks % 20 == 0) {
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 1.2f, 0.7f);
                }

                // Gravitational pull & 7x7 damage/slowness
                for (LivingEntity e : spawnLoc.getWorld().getNearbyLivingEntities(spawnLoc, 7.0)) {
                    if (e.equals(player)) continue;

                    // Pull toward vortex center
                    Vector pull = spawnLoc.toVector().subtract(e.getLocation().toVector());
                    double dist = pull.length();
                    if (dist > 0.8) {
                        Vector vel = pull.normalize().multiply(0.42);
                        e.setVelocity(e.getVelocity().add(vel));
                    }

                    // Inside 3-block ring
                    if (dist <= 3.2) {
                        trappedEntities.add(e);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false));

                        // -1 HP (0.5 heart = 1.0 damage) every second (20 ticks)
                        if (ticks % 20 == 0) {
                            e.damage(1.0, player);
                            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 1.5f);
                            e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, e.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }

                // Vortex climax: fire 3x homing dark shards per trapped player (minimum 3 shards)!
                if (ticks >= maxTicks) {
                    cancel();

                    List<LivingEntity> validTargets = new ArrayList<>();
                    for (LivingEntity e : trappedEntities) {
                        if (e != null && e.isValid() && !e.isDead() && e.getWorld().equals(spawnLoc.getWorld()) && e.getLocation().distance(spawnLoc) <= 14.0) {
                            validTargets.add(e);
                        }
                    }

                    int count = validTargets.size();
                    int totalProjectiles = (count > 0) ? (count * 3) : 3;

                    if (count > 0) {
                        player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#8B0000><bold>✦ GRAVITY VORTEX UNLEASHES " + totalProjectiles + " DARK VOID SHARDS! (" + count + " trapped) ✦</bold></gradient>"));
                    } else {
                        player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#8B0000><bold>✦ GRAVITY VORTEX ERUPTS 3 DARK VOID SHARDS! ✦</bold></gradient>"));
                    }

                    // Climax blast sounds & shockwave ring
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.5f);
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 1.1f, 1.8f);

                    Particle.DustOptions magentaDust = new Particle.DustOptions(Color.fromRGB(220, 0, 255), 1.4f);
                    for (int d = 0; d < 360; d += 30) {
                        double rad = Math.toRadians(d);
                        spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc.clone().add(Math.cos(rad) * 3.0, 0.3, Math.sin(rad) * 3.0), 1, 0, 0, 0, 0, magentaDust);
                        spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc.clone().add(Math.cos(rad) * 2.5, 0.3, Math.sin(rad) * 2.5), 1, 0, 0, 0, 0.02);
                    }

                    // Fire shards at targets or forward
                    fireVortexProjectiles(player, spawnLoc, validTargets, totalProjectiles);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void fireVortexProjectiles(Player player, Location origin, List<LivingEntity> targets, int totalProjectiles) {
        new BukkitRunnable() {
            int fired = 0;

            @Override
            public void run() {
                if (fired >= totalProjectiles) {
                    cancel();
                    return;
                }

                LivingEntity target = (!targets.isEmpty()) ? targets.get(fired % targets.size()) : null;
                Vector fallbackDir = null;
                if (target == null) {
                    // Spread in a fan cone in player's forward direction
                    double spreadAngle = (fired - 1) * 25.0; // -25°, 0°, +25°
                    Vector baseDir = player.getLocation().getDirection().setY(0.15).normalize();
                    double rad = Math.toRadians(spreadAngle);
                    double cos = Math.cos(rad);
                    double sin = Math.sin(rad);
                    fallbackDir = new Vector(baseDir.getX() * cos - baseDir.getZ() * sin, baseDir.getY(), baseDir.getX() * sin + baseDir.getZ() * cos).normalize();
                }

                fired++;
                launchSingleDarkProjectile(player, origin.clone().add(0, 0.8, 0), target, fallbackDir);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void launchSingleDarkProjectile(Player player, Location start, LivingEntity target, Vector optionalDir) {
        Location launchLoc = start.clone();
        launchLoc.getWorld().playSound(launchLoc, Sound.ENTITY_WITHER_SHOOT, 1.4f, 1.8f);
        launchLoc.getWorld().playSound(launchLoc, Sound.ITEM_TRIDENT_THROW, 1.2f, 1.6f);
        launchLoc.getWorld().spawnParticle(Particle.FLASH, launchLoc, 1);

        new BukkitRunnable() {
            Location curr = launchLoc.clone();
            Vector lastDir = (optionalDir != null) ? optionalDir.clone().normalize().multiply(1.35) : new Vector(0, 0.5, 0);
            int steps = 0;

            final Particle.DustOptions corePurple = new Particle.DustOptions(Color.fromRGB(200, 0, 255), 2.4f);
            final Particle.DustOptions coreCrimson = new Particle.DustOptions(Color.fromRGB(255, 30, 60), 2.0f);
            final Particle.DustOptions wingDust = new Particle.DustOptions(Color.fromRGB(130, 0, 220), 1.6f);

            @Override
            public void run() {
                steps++;
                if (steps > 45) {
                    detonateShard(player, curr, null);
                    cancel();
                    return;
                }

                Vector dir;
                if (target != null && target.isValid() && !target.isDead()) {
                    dir = target.getEyeLocation().toVector().subtract(curr.toVector()).normalize().multiply(1.35);
                    lastDir = dir.clone();
                } else {
                    dir = lastDir;
                }

                curr.add(dir);

                // ── 1. Razor Shard / Scythe Blade Head Shape ──
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
                if (perp.lengthSquared() > 0.001) {
                    perp.normalize().multiply(0.28);
                } else {
                    perp = new Vector(0.28, 0, 0);
                }

                // Glowing core head
                curr.getWorld().spawnParticle(Particle.DUST, curr, 3, 0.06, 0.06, 0.06, 0, corePurple);
                curr.getWorld().spawnParticle(Particle.DUST, curr, 2, 0.04, 0.04, 0.04, 0, coreCrimson);
                curr.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, curr, 1, 0.02, 0.02, 0.02, 0.01);
                curr.getWorld().spawnParticle(Particle.WITCH, curr, 1, 0.05, 0.05, 0.05, 0.02);

                // Razor wings / scythe crescent
                Location leftWing = curr.clone().add(perp).subtract(dir.clone().multiply(0.2));
                Location rightWing = curr.clone().subtract(perp).subtract(dir.clone().multiply(0.2));
                curr.getWorld().spawnParticle(Particle.DUST, leftWing, 1, 0, 0, 0, 0, wingDust);
                curr.getWorld().spawnParticle(Particle.DUST, rightWing, 1, 0, 0, 0, 0, wingDust);

                // ── 2. Vivid Ethereal Trail ──
                curr.getWorld().spawnParticle(Particle.CRIT, curr, 2, 0.1, 0.1, 0.1, 0.05);
                curr.getWorld().spawnParticle(Particle.DRAGON_BREATH, curr, 1, 0.05, 0.05, 0.05, 0.01);
                curr.getWorld().spawnParticle(Particle.LARGE_SMOKE, curr, 1, 0.04, 0.04, 0.04, 0.01);
                if (steps % 2 == 0) {
                    curr.getWorld().spawnParticle(Particle.SOUL, curr, 1, 0.05, 0.05, 0.05, 0.01);
                }

                // Check direct entity hit
                if (target != null && target.isValid() && curr.distanceSquared(target.getEyeLocation()) <= 3.0) {
                    detonateShard(player, curr, target);
                    cancel();
                    return;
                }

                // Check solid block hit
                if (curr.getBlock().getType().isSolid()) {
                    detonateShard(player, curr, null);
                    cancel();
                    return;
                }

                // If non-targeted, check collision with any nearby enemy
                if (target == null) {
                    for (LivingEntity nearby : curr.getWorld().getNearbyLivingEntities(curr, 1.5)) {
                        if (!nearby.equals(player)) {
                            detonateShard(player, curr, nearby);
                            cancel();
                            return;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonateShard(Player player, Location hitLoc, LivingEntity target) {
        World w = hitLoc.getWorld();
        w.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.5f);
        w.playSound(hitLoc, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 1.4f);
        w.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.7f);

        Particle.DustOptions boomPurple = new Particle.DustOptions(Color.fromRGB(220, 0, 255), 1.4f);
        Particle.DustOptions boomRed = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.2f);

        w.spawnParticle(Particle.FLASH, hitLoc, 1, Color.PURPLE);
        w.spawnParticle(Particle.EXPLOSION, hitLoc, 1);
        w.spawnParticle(Particle.SWEEP_ATTACK, hitLoc, 1);
        w.spawnParticle(Particle.DUST, hitLoc, 8, 0.25, 0.25, 0.25, 0, boomPurple);
        w.spawnParticle(Particle.DUST, hitLoc, 6, 0.2, 0.2, 0.2, 0, boomRed);
        w.spawnParticle(Particle.SOUL, hitLoc, 4, 0.2, 0.25, 0.2, 0.02);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, hitLoc, 3, 0.2, 0.2, 0.2, 0.02);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 2, 0.15, 0.15, 0.15, 0.02);

        if (target != null && target.isValid()) {
            target.damage(4.0, player); // 2 hearts per shard
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, false, false));
        } else {
            // Splash damage in 2.0 block radius
            for (LivingEntity nearby : w.getNearbyLivingEntities(hitLoc, 2.0)) {
                if (!nearby.equals(player)) {
                    nearby.damage(4.0, player);
                }
            }
        }
    }

    /**
     * Air Variant:
     * Dark Chains erupt from the heavens and ground, violently anchoring and slamming the player down!
     * Spiraling chain vortex around the player during descent.
     * Ground impact shatters chains in a massive radius, blows back enemies, deals 6 HP (3 hearts),
     * and inflicts True Stun for 2s.
     * Marks hit targets ready for Fallen: pressing Secondary again within 4s inflicts Voidbreaker's Fallen!
     * Cooldown Reset: If at least 1 hit player is under 4 hearts (<= 8.0 HP), resets cooldown immediately!
     */
    private boolean executeAirVariant(Player player, String key) {
        int cd = plugin.getConfig().getInt("weapons.grim.secondary_cooldown", 35);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        isDivingAirVariant.add(player.getUniqueId());

        // Initial heavy chain deployment sounds
        Location startApex = player.getLocation().clone();
        player.getWorld().playSound(startApex, Sound.BLOCK_CHAIN_FALL, 2.0f, 0.5f);
        player.getWorld().playSound(startApex, Sound.BLOCK_CHAIN_PLACE, 1.8f, 0.7f);
        player.getWorld().playSound(startApex, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 1.2f);
        player.getWorld().playSound(startApex, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 0.6f);

        player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>✦ Dark Particle [Air Variant]: SOUL CHAIN SLAM! ✦</bold></gradient>"));

        // Forceful downward velocity
        Vector dive = new Vector(0, -2.4, 0);
        player.setVelocity(dive);

        final ItemStack chainItem = new ItemStack(Material.CHAIN);
        final Particle.DustOptions darkMetalDust = new Particle.DustOptions(Color.fromRGB(60, 60, 70), 1.8f);
        final Particle.DustOptions soulPurpleDust = new Particle.DustOptions(Color.fromRGB(140, 0, 220), 1.8f);
        final Particle.DustOptions crimsonDust = new Particle.DustOptions(Color.fromRGB(220, 20, 40), 1.6f);

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

                // Keep pulling downward violently
                if (player.getVelocity().getY() > -1.5) {
                    player.setVelocity(new Vector(0, -2.4, 0));
                }

                Location pLoc = player.getLocation();
                World world = player.getWorld();

                // ── 1. Vertical Sky Chains (Traced from Start Apex down to Player) ──
                double distFromApex = startApex.getY() - pLoc.getY();
                if (distFromApex > 0.5) {
                    for (double y = pLoc.getY(); y <= startApex.getY(); y += 0.8) {
                        Location linkLoc = new Location(world, pLoc.getX(), y, pLoc.getZ());
                        world.spawnParticle(Particle.ITEM, linkLoc, 1, 0, 0, 0, 0, chainItem);
                        world.spawnParticle(Particle.DUST, linkLoc, 1, 0, 0, 0, 0, darkMetalDust);
                    }
                }

                // ── 2. Diagonal Anchor Chains Shooting from Waist to Ground Below ──
                for (int d = 45; d < 360; d += 90) {
                    double rad = Math.toRadians(d);
                    Vector offset = new Vector(Math.cos(rad) * 2.2, -1.8, Math.sin(rad) * 2.2);
                    for (double f = 0.2; f <= 1.0; f += 0.3) {
                        Location point = pLoc.clone().add(0, 0.8, 0).add(offset.clone().multiply(f));
                        world.spawnParticle(Particle.ITEM, point, 1, 0, 0, 0, 0, chainItem);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0.02);
                    }
                }

                // ── 3. Spiraling Chain Helix wrapping the descending Reaper ──
                double rot = ticks * 0.85;
                for (int i = 0; i < 2; i++) {
                    double angle = rot + (i * Math.PI);
                    Location helixPoint = pLoc.clone().add(Math.cos(angle) * 1.1, 0.8 + Math.sin(rot) * 0.4, Math.sin(angle) * 1.1);
                    world.spawnParticle(Particle.ITEM, helixPoint, 1, 0, 0, 0, 0, chainItem);
                    world.spawnParticle(Particle.DUST, helixPoint, 2, 0.05, 0.05, 0.05, 0, soulPurpleDust);
                    world.spawnParticle(Particle.DUST, helixPoint, 1, 0.05, 0.05, 0.05, 0, crimsonDust);
                }

                // ── 4. Rattling Chain Sound ──
                if (ticks % 2 == 0) {
                    world.playSound(pLoc, Sound.BLOCK_CHAIN_FALL, 1.4f, 0.8f + (ticks * 0.02f));
                    world.playSound(pLoc, Sound.BLOCK_CHAIN_STEP, 1.2f, 0.6f);
                }

                // Check ground impact
                if (player.isOnGround() || (ticks > 3 && player.getVelocity().getY() >= -0.2)) {
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
        World world = impact.getWorld();

        // Audio: Shattering chains, massive explosion, anvil and warden sonic booms
        world.playSound(impact, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.6f);
        world.playSound(impact, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.4f);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        world.playSound(impact, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);
        world.playSound(impact, Sound.BLOCK_HEAVY_CORE_FALL, 1.8f, 0.5f);

        final ItemStack chainItem = new ItemStack(Material.CHAIN);
        final Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(20, 20, 25), 1.8f);
        final Particle.DustOptions crimsonDust = new Particle.DustOptions(Color.fromRGB(220, 20, 40), 1.5f);
        final Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(160, 0, 255), 1.5f);

        // 1. Blasting Chain Fragments & Shards
        world.spawnParticle(Particle.ITEM, impact.clone().add(0, 0.5, 0), 6, 0.25, 0.2, 0.25, 0.05, chainItem);
        world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.5, 0), 5, 0.2, 0.15, 0.2, 0.03, Material.OBSIDIAN.createBlockData());
        world.spawnParticle(Particle.DUST, impact.clone().add(0, 0.4, 0), 4, 0.2, 0.15, 0.2, 0, darkDust);
        world.spawnParticle(Particle.SONIC_BOOM, impact.clone().add(0, 0.2, 0), 1);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 1);
        world.spawnParticle(Particle.FLASH, impact, 1);

        // 2. Rising Chain Spikes around the impact perimeter
        for (int d = 0; d < 360; d += 60) {
            double rad = Math.toRadians(d);
            Location spikeBase = impact.clone().add(Math.cos(rad) * 3.2, 0.1, Math.sin(rad) * 3.2);
            for (double h = 0; h <= 2.8; h += 0.9) {
                world.spawnParticle(Particle.ITEM, spikeBase.clone().add(0, h, 0), 1, 0.03, 0.03, 0.03, 0.01, chainItem);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, spikeBase.clone().add(0, h, 0), 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        // 3. Expanding Concentric Shockwave Rings
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step > 4) {
                    cancel();
                    return;
                }
                double r = step * 1.5; // Radius 1.5 -> 3.0 -> 4.5 -> 6.0
                for (int d = 0; d < 360; d += 24) {
                    double rad = Math.toRadians(d);
                    Location ringP = impact.clone().add(Math.cos(rad) * r, 0.15, Math.sin(rad) * r);
                    world.spawnParticle(Particle.DUST, ringP, 1, 0, 0, 0, 0, (step % 2 == 0) ? crimsonDust : purpleDust);
                    if (d % 48 == 0) {
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, ringP, 1, 0, 0, 0, 0.02);
                        world.spawnParticle(Particle.WITCH, ringP, 1, 0, 0, 0, 0.02);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        List<LivingEntity> hitTargets = new ArrayList<>();
        boolean lowHealthHit = false;

        for (LivingEntity e : impact.getWorld().getNearbyLivingEntities(impact, 5.5)) {
            if (e.equals(player)) continue;

            // Damage: 6.0 HP (3 hearts)
            e.damage(6.0, player);

            // Knockback
            Vector kb = e.getLocation().toVector().subtract(impact.toVector()).normalize().multiply(1.3).setY(0.45);
            e.setVelocity(kb);

            // Visual Chains binding the target
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.8f);
            for (double h = 0.2; h <= 1.8; h += 0.6) {
                e.getWorld().spawnParticle(Particle.ITEM, e.getLocation().add(0, h, 0), 1, 0.15, 0.05, 0.15, 0.01, chainItem);
            }

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
            player.sendMessage(miniMessage.deserialize("<gold>✦ [GRIM EXECUTION] <dark_red><bold>Prey under 4 hearts crushed by chains! Dark Particle Cooldown RESET!</bold></dark_red> ✦</gold>"));
        }

        // Mark for "Press again" Fallen infliction
        if (!hitTargets.isEmpty()) {
            airVariantMarked.put(player.getUniqueId(), hitTargets);
            airVariantMarkExpire.put(player.getUniqueId(), System.currentTimeMillis() + 4000L); // 4 seconds

            player.sendActionBar(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>✦ PRESS SECONDARY AGAIN TO INFLICT FALLEN! ✦</bold></gradient>"));
            player.sendMessage(miniMessage.deserialize("<light_purple>✦ Chained & stunned " + hitTargets.size() + " enemies! Press Secondary again within 4s to inflict Voidbreaker's Fallen!</light_purple>"));

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
            hitLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, hitLoc, 3, 0.2, 0.2, 0.2, 0.01);
            hitLoc.getWorld().spawnParticle(Particle.SOUL, hitLoc, 3, 0.2, 0.2, 0.2, 0.02);

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
        loc.getWorld().spawnParticle(Particle.SOUL, loc, 4, 0.2, 0.25, 0.2, 0.02);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.6, 0), 0, 0, 1, 0, 0.04);
        player.playSound(player.getLocation(), Sound.ENTITY_VEX_DEATH, 1.2f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.2f);
        player.sendMessage(Component.text("✦ Reaper harvested soul #" + kills + "! +1 Permanent Max Heart!", NamedTextColor.DARK_RED));
    }
}
