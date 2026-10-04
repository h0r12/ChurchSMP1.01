package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoidBreaker extends LegendaryWeapon {

    // --- Crumble ---
    private final Map<UUID, Integer> crumbleHits = new ConcurrentHashMap<>();
    private final Set<UUID> shockwaveArmed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> shockwaveTarget = new ConcurrentHashMap<>();
    /** Prevents recursive onHit when shockwave/WoS deals programmatic damage */
    private final Set<UUID> abilityDamaging = ConcurrentHashMap.newKeySet();

    // --- Bound (Dash) ---
    private final Map<UUID, Integer> boundCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDashTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> boundMarkedTarget = new ConcurrentHashMap<>();

    // --- Voidfeels ---
    private final Map<UUID, Long> doubleJumpCooldown = new ConcurrentHashMap<>();

    // --- Weight of Sin ---
    /** caster UUID -> target UUID */
    private final Map<UUID, UUID> weightOfSinTargets = new ConcurrentHashMap<>();
    /** target UUIDs flagged for immediate crush (mace slam acceleration) */
    private final Set<UUID> weightOfSinForceResolve = ConcurrentHashMap.newKeySet();

    public VoidBreaker(ChurchSMP plugin) {
        super(plugin,
                "voidbreaker",
                new String[]{"void_breaker", "abyssal_shatter"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#2F4F4F:#D3D3D3:#2F4F4F><bold>Voidbreaker</bold></gradient>"),
                Material.MACE,
                Alignment.NULLIFIED,
                "Bound",
                "Weight of Sin");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Voidfeels", "Crumble"), "Bound", "Weight of Sin"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            try {
                meta.addEnchant(Enchantment.WIND_BURST, 2, true);
                meta.addEnchant(Enchantment.DENSITY, 3, true);
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

    // =========================================================================
    // PRIMARY: Bound (F Key) — 3-charge directional dash, works mid-air
    // =========================================================================
    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        long now = System.currentTimeMillis();

        // First activation starts the 10s active window with 3 charges
        if (!plugin.getCooldownManager().isActive(player, key)) {
            if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;
            plugin.getCooldownManager().setActiveDuration(player, key, 10);
            plugin.getBossBarManager().showActiveCountdown(player, "Bound Active (Dash)", BossBar.Color.PURPLE, 10);
            boundCharges.put(player.getUniqueId(), 3);
        }

        int charges = boundCharges.getOrDefault(player.getUniqueId(), 3);
        if (charges <= 0) {
            player.sendMessage(Component.text("✦ Bound: Out of dash charges! Land a mace slam to recharge.", NamedTextColor.RED));
            return false;
        }

        // 1s cooldown between individual dashes
        long lastDash = lastDashTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDash < 1000L) return false;

        lastDashTime.put(player.getUniqueId(), now);
        charges--;
        boundCharges.put(player.getUniqueId(), charges);

        // Full 3D dash towards crosshair — works in air
        Vector dash = player.getEyeLocation().getDirection().normalize().multiply(1.85);
        // Preserve some upward momentum for better mid-air feel
        Vector current = player.getVelocity();
        if (current.getY() > 0) {
            dash.setY(Math.max(dash.getY(), current.getY() * 0.5 + 0.15));
        }
        player.setVelocity(dash);

        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.0, 0), 30, 0.4, 0.4, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

        // Re-enable flight after dash so double jump works mid-air
        reEnableFlightDelayed(player);

        // Raycast along dash line to mark a target
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 1.0; d <= 6.0; d += 0.5) {
            Location check = eye.clone().add(dir.clone().multiply(d));
            for (LivingEntity nearby : check.getWorld().getNearbyLivingEntities(check, 1.8)) {
                if (!nearby.equals(player)) {
                    boundMarkedTarget.put(player.getUniqueId(), nearby.getUniqueId());
                    nearby.getWorld().spawnParticle(Particle.SONIC_BOOM, nearby.getLocation().add(0, 1.0, 0), 1);
                    player.sendMessage(Component.text("✦ Bound marked " + nearby.getName() + "!", NamedTextColor.LIGHT_PURPLE));
                    break;
                }
            }
            if (boundMarkedTarget.containsKey(player.getUniqueId())) break;
        }

        player.sendMessage(Component.text("✦ Bound Dash! Charges: " + charges + "/3", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    // =========================================================================
    // SECONDARY: Weight of Sin (Shift+F) — Descending particle hammer
    // =========================================================================
    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Raycast to find a target within 15 blocks
        LivingEntity target = null;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 1.0; d <= 15.0; d += 0.5) {
            Location check = eye.clone().add(dir.clone().multiply(d));
            for (LivingEntity nearby : check.getWorld().getNearbyLivingEntities(check, 1.5)) {
                if (!nearby.equals(player)) {
                    target = nearby;
                    break;
                }
            }
            if (target != null) break;
        }

        if (target == null) {
            player.sendMessage(Component.text("✦ Weight of Sin: No target in sight!", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("weapons.voidbreaker.secondary_cooldown", 60);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        final LivingEntity finalTarget = target;
        final UUID casterUuid = player.getUniqueId();
        final UUID targetUuid = target.getUniqueId();

        weightOfSinTargets.put(casterUuid, targetUuid);

        // Activation sounds and messages
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.5f);
        player.sendMessage(Component.text("✦ Weight of Sin descending upon " + target.getName() + "!", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));

        if (target instanceof Player tp) {
            tp.sendMessage(Component.text("⚠ THE WEIGHT OF SIN IS DESCENDING UPON YOU!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }

        plugin.getBossBarManager().showActiveCountdown(player, "Weight of Sin", BossBar.Color.PURPLE, 3);

        // Descending hammer task — 60 ticks (3 seconds), runs every 2 ticks
        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = 60;
            final double startHeight = 10.0;

            @Override
            public void run() {
                Player caster = Bukkit.getPlayer(casterUuid);
                if (caster == null || !caster.isOnline()) {
                    cleanup();
                    cancel();
                    return;
                }

                if (!finalTarget.isValid() || finalTarget.isDead()) {
                    caster.sendMessage(Component.text("✦ Weight of Sin: Target perished before the crush!", NamedTextColor.GRAY));
                    cleanup();
                    cancel();
                    return;
                }

                // Check force-resolve flag (set by mace slam in onHit)
                if (weightOfSinForceResolve.remove(targetUuid)) {
                    resolveWeightOfSin(caster, finalTarget, finalTarget.getLocation());
                    cleanup();
                    cancel();
                    return;
                }

                Location targetLoc = finalTarget.getLocation();
                World world = targetLoc.getWorld();
                double progress = (double) ticks / totalTicks; // 0.0 → 1.0
                double hammerY = targetLoc.getY() + startHeight * (1.0 - progress);

                // === PARTICLE AXE (Spinning) ===
                Location axeCenter = new Location(world, targetLoc.getX(), hammerY, targetLoc.getZ());
                Particle.DustOptions darkDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(40, 40, 40), 2.5f);
                Particle.DustOptions purpleDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(75, 0, 130), 1.8f);

                double rotationAngle = ticks * 0.4; // Spins over time

                // Handle — vertical column
                for (double dy = -1.0; dy <= 2.0; dy += 0.4) {
                    world.spawnParticle(Particle.DUST, axeCenter.clone().add(0, dy, 0), 1, 0, 0, 0, 0, purpleDust);
                }

                // Double axe heads (rotating)
                for (double r = 0.4; r <= 1.8; r += 0.3) {
                    double bladeY = Math.sin(r * 2) * 0.5; // Curve of the blade

                    // Head 1
                    double h1x = Math.cos(rotationAngle) * r;
                    double h1z = Math.sin(rotationAngle) * r;
                    world.spawnParticle(Particle.DUST, axeCenter.clone().add(h1x, bladeY, h1z), 1, 0, 0, 0, 0, darkDust);
                    world.spawnParticle(Particle.DUST, axeCenter.clone().add(h1x, -bladeY, h1z), 1, 0, 0, 0, 0, darkDust);

                    // Head 2 (opposite side)
                    double h2x = Math.cos(rotationAngle + Math.PI) * r;
                    double h2z = Math.sin(rotationAngle + Math.PI) * r;
                    world.spawnParticle(Particle.DUST, axeCenter.clone().add(h2x, bladeY, h2z), 1, 0, 0, 0, 0, darkDust);
                    world.spawnParticle(Particle.DUST, axeCenter.clone().add(h2x, -bladeY, h2z), 1, 0, 0, 0, 0, darkDust);
                }

                // Soul flame trail at the tips of the blades
                double tipX = Math.cos(rotationAngle) * 2.0;
                double tipZ = Math.sin(rotationAngle) * 2.0;
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, axeCenter.clone().add(tipX, 0, tipZ), 1, 0, 0, 0, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, axeCenter.clone().add(-tipX, 0, -tipZ), 1, 0, 0, 0, 0.02);

                // === GROUND PRESSURE EFFECTS ===
                Particle.DustOptions greyDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 100, 100), 2.0f);
                // Pulsing ring on the ground beneath target
                double ringRadius = 1.5 + progress * 2.5;
                int ringPoints = 20 + (int) (progress * 12);
                for (int i = 0; i < ringPoints; i++) {
                    double angle = (2 * Math.PI / ringPoints) * i + (ticks * 0.1);
                    double rx = Math.cos(angle) * ringRadius;
                    double rz = Math.sin(angle) * ringRadius;
                    world.spawnParticle(Particle.DUST, targetLoc.clone().add(rx, 0.1, rz), 1, 0, 0, 0, 0, greyDust);
                }

                // Downward pressure-wave columns (every 5 ticks)
                if (ticks % 5 == 0) {
                    Particle.DustOptions pressureDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 0, 80), 1.2f);
                    for (int i = 0; i < 8; i++) {
                        double angle = (2 * Math.PI / 8) * i;
                        double px = Math.cos(angle) * (0.5 + progress);
                        double pz = Math.sin(angle) * (0.5 + progress);
                        for (double py = hammerY; py > targetLoc.getY(); py -= 1.5) {
                            world.spawnParticle(Particle.DUST,
                                    new Location(world, targetLoc.getX() + px, py, targetLoc.getZ() + pz),
                                    1, 0, 0, 0, 0, pressureDust);
                        }
                    }
                }

                // Ground crack particles (increasing intensity)
                if (ticks % 10 == 0) {
                    int crackCount = 3 + (int) (progress * 12);
                    world.spawnParticle(Particle.BLOCK, targetLoc.clone().add(0, 0.1, 0),
                            crackCount, 1.0 + progress, 0.1, 1.0 + progress, 0.05, Material.GRAVEL.createBlockData());
                }

                // Ominous ticking sound
                if (ticks % 10 == 0) {
                    world.playSound(targetLoc, Sound.BLOCK_HEAVY_CORE_STEP, 1.0f, 0.5f + (float) (progress * 0.8));
                }

                // === PROGRESSIVE DEBUFFS ===
                if (ticks < 20) {
                    finalTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, false, false, true));
                } else if (ticks < 40) {
                    finalTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1, false, false, true));
                } else {
                    finalTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 2, false, false, true));
                }

                // Progressively suppress jumping
                if (finalTarget instanceof Player tp) {
                    Vector vel = tp.getVelocity();
                    double maxJump = 0.42 * (1.0 - progress * 0.8);
                    if (vel.getY() > maxJump && vel.getY() > 0) {
                        vel.setY(maxJump);
                        tp.setVelocity(vel);
                    }
                }

                ticks += 2;

                // === FULL DESCENT REACHED: CRUSH ===
                if (ticks >= totalTicks) {
                    resolveWeightOfSin(caster, finalTarget, finalTarget.getLocation());
                    cleanup();
                    cancel();
                }
            }

            private void cleanup() {
                weightOfSinTargets.remove(casterUuid);
                weightOfSinForceResolve.remove(targetUuid);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        return true;
    }

    /**
     * Resolves the Weight of Sin crush: massive damage + Fallen + stun + explosion visuals.
     */
    private void resolveWeightOfSin(Player caster, LivingEntity target, Location loc) {
        World world = loc.getWorld();

        // Guard: prevent recursive onHit from the crush damage
        abilityDamaging.add(caster.getUniqueId());

        // Crush damage
        target.damage(22.0, caster);

        // Apply Fallen debuff
        plugin.getFallenManager().applyFallen(target);

        // Apply 2s True Stun
        plugin.getStunManager().applyTrueStun(target, 40, "WEIGHT OF SIN");

        // === CRUSH VISUALS ===
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 2.0f, 0.4f);
        world.playSound(loc, Sound.BLOCK_HEAVY_CORE_FALL, 2.0f, 0.5f);
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.0, 0), 1);

        // Animated expanding shockwave rings
        Particle.DustOptions crushDark = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 3.0f);
        Particle.DustOptions crushPurple = new Particle.DustOptions(org.bukkit.Color.fromRGB(75, 0, 130), 2.2f);

        new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame >= 4) {
                    cancel();
                    return;
                }
                double radius = 2.0 + frame * 2.5;
                for (int d = 0; d < 360; d += 10) {
                    double rad = Math.toRadians(d);
                    Location p = loc.clone().add(Math.cos(rad) * radius, 0.15, Math.sin(rad) * radius);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, frame < 2 ? crushDark : crushPurple);
                }
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 3L);

        // Block debris
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0), 60, 2.0, 0.5, 2.0, 0.2, Material.GRAVEL.createBlockData());
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0), 40, 1.5, 0.4, 1.5, 0.15, Material.COBBLESTONE.createBlockData());
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.3, 0), 25, 1.2, 0.3, 1.2, 0.1, Material.DEEPSLATE.createBlockData());

        // Rising dust columns around impact
        for (int i = 0; i < 6; i++) {
            double angle = (2 * Math.PI / 6) * i;
            Location col = loc.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            for (double y = 0; y < 3.0; y += 0.4) {
                world.spawnParticle(Particle.DUST, col.clone().add(0, y, 0), 1, 0.1, 0, 0.1, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 80, 80), 1.5f));
            }
        }

        // Knockback nearby entities (not the stunned target, not the caster)
        for (LivingEntity e : world.getNearbyLivingEntities(loc, 5.0)) {
            if (e.equals(caster) || e.equals(target)) continue;
            Vector kb = e.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.3).setY(0.6);
            e.setVelocity(kb);
            e.damage(22.0, caster);
        }

        // Messages
        caster.sendMessage(Component.text("✦ WEIGHT OF SIN CRUSHED " + target.getName() + "! FALLEN INFLICTED!",
                NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
        if (target instanceof Player tp) {
            tp.sendMessage(Component.text("⚠ The Weight of Sin has crushed you!",
                    NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }

        // Clear guard flag after damage events have propagated
        Bukkit.getScheduler().runTaskLater(plugin, () -> abilityDamaging.remove(caster.getUniqueId()), 2L);
    }

    // =========================================================================
    // DOUBLE JUMP: Voidfeels (Space) — works mid-air
    // =========================================================================
    public void handleDoubleJump(Player player) {
        long now = System.currentTimeMillis();

        // Voidfeels: standard double jump (5s CD)
        long lastDJ = doubleJumpCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastDJ < 5000L) return;

        doubleJumpCooldown.put(player.getUniqueId(), now);
        plugin.getBossBarManager().showPassiveCooldown(player, this, "Voidfeels", 5);
        player.setVelocity(new Vector(player.getVelocity().getX(), 0.9, player.getVelocity().getZ()));
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.2f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);

        reEnableFlightDelayed(player);
    }

    /**
     * Re-enables allowFlight after a short delay so double jump can trigger again mid-air.
     * The cooldown system handles rate-limiting; this just ensures the toggle-flight event fires.
     */
    private void reEnableFlightDelayed(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                LegendaryWeapon w = plugin.getWeaponManager().getWeapon(player.getInventory().getItemInMainHand());
                if (w instanceof VoidBreaker) {
                    player.setAllowFlight(true);
                }
            }
        }, 5L);
    }

    // =========================================================================
    // SHOCKWAVE: Armed at crumble 5/5, detonated via RMB
    // =========================================================================
    /** Called by InputListener to check if RMB should trigger shockwave */
    public boolean hasShockwaveArmed(Player player) {
        return shockwaveArmed.contains(player.getUniqueId());
    }

    /** Called by InputListener on RMB when shockwave is armed */
    public void detonateShockwave(Player player) {
        if (!shockwaveArmed.remove(player.getUniqueId())) return;
        crumbleHits.put(player.getUniqueId(), 0);

        // Detonation location: the enemy who took the 5th hit
        UUID markedId = shockwaveTarget.remove(player.getUniqueId());
        org.bukkit.entity.Entity markedEnt = (markedId != null) ? Bukkit.getEntity(markedId) : null;
        Location detonateLoc = (markedEnt instanceof LivingEntity le && le.isValid() && !le.isDead())
                ? le.getLocation() : player.getLocation();

        World world = detonateLoc.getWorld();

        // Guard: prevent recursive onHit
        abilityDamaging.add(player.getUniqueId());

        // === SOUNDS ===
        world.playSound(detonateLoc, Sound.BLOCK_HEAVY_CORE_FALL, 2.0f, 0.5f);
        world.playSound(detonateLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
        world.playSound(detonateLoc, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);
        world.spawnParticle(Particle.SONIC_BOOM, detonateLoc.clone().add(0, 0.5, 0), 1);

        // === EXPANDING SHOCKWAVE RINGS ===
        Particle.DustOptions shockDustDark = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 30, 30), 2.2f);
        Particle.DustOptions shockDustLight = new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 1.8f);

        for (double r = 1.0; r <= 4.5; r += 0.8) {
            for (int d = 0; d < 360; d += 15) {
                double rad = Math.toRadians(d);
                Location p = detonateLoc.clone().add(Math.cos(rad) * r, 0.15, Math.sin(rad) * r);
                world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, (r > 2.2) ? shockDustLight : shockDustDark);
            }
        }

        // Block debris
        world.spawnParticle(Particle.BLOCK, detonateLoc.clone().add(0, 0.5, 0), 50, 1.8, 0.5, 1.8, 0.15, Material.GRAVEL.createBlockData());
        world.spawnParticle(Particle.BLOCK, detonateLoc.clone().add(0, 0.5, 0), 30, 1.5, 0.4, 1.5, 0.1, Material.COBBLESTONE.createBlockData());

        // === AOE DAMAGE ===
        double shockwaveDmg = 18.0;
        for (LivingEntity e : world.getNearbyLivingEntities(detonateLoc, 4.5)) {
            if (e.equals(player)) continue;
            e.damage(shockwaveDmg, player);
            Vector kb = e.getLocation().toVector().subtract(detonateLoc.toVector()).normalize().multiply(1.1).setY(0.5);
            e.setVelocity(kb);
        }

        // === BOOST PLAYER UPWARD for mace slam follow-up ===
        player.setVelocity(player.getVelocity().add(new Vector(0, 1.8, 0)));
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.8f);
        reEnableFlightDelayed(player);

        player.sendMessage(Component.text("✦ SEISMIC SHOCKWAVE DETONATED!",
                NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));

        // Clear guard flag after damage events propagate
        Bukkit.getScheduler().runTaskLater(plugin, () -> abilityDamaging.remove(player.getUniqueId()), 2L);
    }

    // =========================================================================
    // ON HIT — Crumble counter, bound recharge, rifted CD halve, WoS accel
    // =========================================================================
    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Guard: skip entirely if this damage came from our own ability (shockwave/WoS)
        if (abilityDamaging.contains(attacker.getUniqueId())) return;

        boolean isMaceSlam = attacker.getFallDistance() > 0.5f || !attacker.isOnGround();

        if (isMaceSlam) {
            // Recharge ALL dash charges (3/3) on mace slam hit
            boundCharges.put(attacker.getUniqueId(), 3);
            attacker.sendMessage(Component.text("✦ Bound: All 3 Dash Charges recharged! (3/3)", NamedTextColor.LIGHT_PURPLE));
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.6f);

            // --- Crumble: count up to 5 hits ---
            if (!shockwaveArmed.contains(attacker.getUniqueId())) {
                int hits = crumbleHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
                crumbleHits.put(attacker.getUniqueId(), hits);

                // Visual effects per hit (escalating rings)
                Location feet = target.getLocation();
                int particleCount = 8 + (hits * 4);
                double spread = 0.3 + (hits * 0.12);
                Particle.DustOptions dustColor = new Particle.DustOptions(
                        org.bukkit.Color.fromRGB(140, 140, 140), 1.0f + (hits * 0.25f));
                feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(0, 0.1, 0),
                        particleCount, spread, 0.05, spread, 0, dustColor);
                feet.getWorld().spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.1, 0),
                        particleCount / 2, spread, 0.1, spread, 0.1, Material.GRAVEL.createBlockData());

                for (int i = 0; i < hits * 5; i++) {
                    double angle = (2 * Math.PI / (hits * 5)) * i;
                    double rx = Math.cos(angle) * (0.5 + hits * 0.2);
                    double rz = Math.sin(angle) * (0.5 + hits * 0.2);
                    target.getLocation().getWorld().spawnParticle(Particle.DUST,
                            target.getLocation().add(rx, 0.05, rz), 1, 0, 0, 0, 0, dustColor);
                }

                attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f + (hits * 0.15f));

                if (hits < 5) {
                    attacker.sendMessage(Component.text("✦ Crumble: " + hits + "/5", NamedTextColor.LIGHT_PURPLE));
                } else {
                    // === 5th HIT: ARM SHOCKWAVE ===
                    shockwaveArmed.add(attacker.getUniqueId());
                    shockwaveTarget.put(attacker.getUniqueId(), target.getUniqueId());
                    attacker.sendMessage(Component.text("✦ CRUMBLE 5/5 — SEISMIC SHOCKWAVE ARMED! Right-Click to DETONATE!",
                            NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.5f, 0.6f);

                    // Bossbar reminder with 15s timeout
                    plugin.getBossBarManager().showActiveCountdown(attacker, "SHOCKWAVE ARMED — RMB", BossBar.Color.PURPLE, 15);

                    // Auto-disarm after 15 seconds if not used
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (shockwaveArmed.remove(attacker.getUniqueId())) {
                            crumbleHits.put(attacker.getUniqueId(), 0);
                            if (attacker.isOnline()) {
                                attacker.sendMessage(Component.text("✦ Seismic Shockwave expired!", NamedTextColor.RED));
                            }
                        }
                    }, 300L); // 15 seconds = 300 ticks
                }
            }

            // --- Weight of Sin: if attacker mace slams the WoS target, accelerate hammer ---
            UUID wosTargetId = weightOfSinTargets.get(attacker.getUniqueId());
            if (wosTargetId != null && wosTargetId.equals(target.getUniqueId())) {
                weightOfSinForceResolve.add(target.getUniqueId());
                attacker.sendMessage(Component.text("✦ Mace slam accelerated Weight of Sin! INSTANT CRUSH!",
                        NamedTextColor.DARK_PURPLE));
            }
        }
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Crumble: resets on incoming damage
        Integer hits = crumbleHits.remove(victim.getUniqueId());
        if (hits != null && hits > 0) {
            victim.sendMessage(Component.text("✦ Crumble hit counter reset by incoming damage!", NamedTextColor.RED));
        }
        // Also disarm shockwave if it was armed
        if (shockwaveArmed.remove(victim.getUniqueId())) {
            victim.sendMessage(Component.text("✦ Seismic Shockwave disarmed by incoming damage!", NamedTextColor.RED));
        }
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
}
