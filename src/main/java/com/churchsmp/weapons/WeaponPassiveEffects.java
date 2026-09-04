package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every weapon has its own small ambient particle "signature" that plays
 * continuously while it's held in the main hand — this is what actually
 * delivers looped/spiral motion, since a flat item texture can't animate
 * in 3D space on its own. Also owns a couple of stateful per-weapon
 * passives (Sorrowess's crouch-water and permanent health bonus, Blade of
 * Judas's random buff) that need more than a single particle call.
 */
public class WeaponPassiveEffects implements Listener {

    private static final org.bukkit.NamespacedKey SORROWESS_HEALTH_KEY =
            new org.bukkit.NamespacedKey("churchsmp", "sorrowess_health_bonus");

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private int globalTick = 0;

    /** Tracks the single block each player has temporarily turned to water via Sorrowess's crouch passive. */
    private final Map<UUID, BlockSnapshot> waterConversions = new HashMap<>();

    private record BlockSnapshot(Block block, BlockData original) {}

    public WeaponPassiveEffects(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                globalTick++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    WeaponType type = weaponManager.getWeaponType(held);
                    if (type != null) {
                        play(type, player);
                    }
                    if (type != WeaponType.SORROWESS) {
                        revertWaterIfAny(player);
                    }
                    if (globalTick % 25 == 0) {
                        checkSorrowessHealthBonus(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 4L); // every 4 ticks (0.2s), starting 1s after enable
    }

    private void play(WeaponType type, Player player) {
        switch (type) {
            case BLADE_OF_ARCHANGEL -> archangelTrail(player);
            case SWORD_OF_DAVID -> davidPulseRing(player);
            case STAFF_OF_MOSES -> {
                mayimAura(player);
                mayimWaterMight(player);
                mayimIcyPath(player);
            }
            case SCYTHE_OF_CAIN -> cainSwirl(player);
            case SORROWESS -> {
                sorrowessRainCloud(player);
                sorrowessCrouchWater(player);
            }
            case BLADE_OF_JUDAS -> judasFlicker(player);
            case VOIDBREAKER -> voidbreakerSpiral(player);
        }
    }

    private void archangelTrail(Player player) {
        player.getWorld().spawnParticle(Particle.END_ROD,
                player.getLocation().add(0, 1.3, 0), 2, 0.3, 0.2, 0.3, 0.01);
    }

    /** Expands outward from the player and resets every 2 seconds (40 ticks). */
    private void davidPulseRing(Player player) {
        int phase = globalTick % 40; // 0..39, one full expand cycle every 2s
        double radius = (phase / 40.0) * 2.2;
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            player.getWorld().spawnParticle(Particle.WITCH,
                    player.getLocation().add(x, 0.1, z), 0, 0, 0, 0, 0);
        }
    }

    /** Bubbles orbiting the feet, plus a light water-splash drift around the whole body. */
    private void mayimAura(Player player) {
        double angle = globalTick * 0.35;
        double radius = 0.6;
        Vector offset = new Vector(radius * Math.cos(angle), 0.1, radius * Math.sin(angle));
        player.getWorld().spawnParticle(Particle.BUBBLE_POP, player.getLocation().add(offset), 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.SPLASH,
                player.getLocation().add(0, 1, 0), 2, 0.4, 0.5, 0.4, 0.01);
    }

    /** Passive 1, Water Mighty: Strength I on dry land, Strength III while in water. */
    private void mayimWaterMight(Player player) {
        int amplifier = player.isInWater() ? 2 : 0;
        PotionEffect current = player.getPotionEffect(PotionEffectType.STRENGTH);
        if (current == null || current.getAmplifier() != amplifier || current.getDuration() < 30) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, amplifier, false, false, false));
        }
    }

    /** Tracks each player's carried horizontal momentum for the Icy Path passive. */
    private final Map<UUID, Vector> icyMomentum = new HashMap<>();

    /** Passive 3, Icy Path: holding the sword makes you slide like you're on blue ice. */
    private void mayimIcyPath(Player player) {
        if (!player.isOnGround()) return;
        Vector velocity = player.getVelocity();
        Vector horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
        Vector carried = icyMomentum.getOrDefault(player.getUniqueId(), new Vector(0, 0, 0));

        Vector blended = horizontal.length() > 0.02
                ? horizontal.clone().add(carried.clone().multiply(0.55))
                : carried.clone().multiply(0.9); // still sliding even after you let go of the stick

        if (blended.length() > 0.5) blended.normalize().multiply(0.5); // don't let it run away

        icyMomentum.put(player.getUniqueId(), blended);
        if (blended.length() > 0.03) {
            player.setVelocity(new Vector(blended.getX(), velocity.getY(), blended.getZ()));
        }
    }

    private void cainSwirl(Player player) {
        double angle = -globalTick * 0.5; // opposite rotation direction, feels more sinister
        double radius = 0.7;
        Vector offset = new Vector(radius * Math.cos(angle), 0.9, radius * Math.sin(angle));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(140, 10, 10), 1f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(offset), 1, 0, 0, 0, 0, dust);
    }

    /** A little rain cloud of CLOUD particles hovering above, dripping lava-drop particles down like rain. */
    private void sorrowessRainCloud(Player player) {
        Location cloudCenter = player.getLocation().add(0, 2.6, 0);
        for (int i = 0; i < 3; i++) {
            double x = (Math.random() - 0.5) * 1.4;
            double z = (Math.random() - 0.5) * 1.4;
            player.getWorld().spawnParticle(Particle.CLOUD, cloudCenter.clone().add(x, 0, z), 1, 0, 0, 0, 0);
        }
        if (globalTick % 2 == 0) {
            double x = (Math.random() - 0.5) * 1.2;
            double z = (Math.random() - 0.5) * 1.2;
            Location dripStart = cloudCenter.clone().add(x, -0.3, z);
            player.getWorld().spawnParticle(Particle.DRIPPING_LAVA, dripStart, 1, 0, 0.1, 0, 0);
        }
    }

    /**
     * Crouch passive: the block at your feet becomes water while sneaking,
     * and reverts the moment you stop sneaking or step onto a new block.
     * Note: this is a real (if brief) water source block, so it can spread
     * slightly to neighbours before the next check reverts it — the API
     * doesn't expose a way to place genuinely non-flowing water.
     */
    private void sorrowessCrouchWater(Player player) {
        if (!player.isSneaking()) {
            revertWaterIfAny(player);
            return;
        }
        Block feetBlock = player.getLocation().getBlock();
        BlockSnapshot existing = waterConversions.get(player.getUniqueId());
        if (existing != null && existing.block().equals(feetBlock)) {
            return; // already converted this exact block, nothing to do
        }
        if (existing != null) {
            existing.block().setBlockData(existing.original());
        }
        if (feetBlock.getType().isAir()) {
            waterConversions.put(player.getUniqueId(), new BlockSnapshot(feetBlock, feetBlock.getBlockData()));
            feetBlock.setType(org.bukkit.Material.WATER);
        } else {
            waterConversions.remove(player.getUniqueId());
        }
    }

    private void revertWaterIfAny(Player player) {
        BlockSnapshot snapshot = waterConversions.remove(player.getUniqueId());
        if (snapshot != null && snapshot.block().getType() == org.bukkit.Material.WATER) {
            snapshot.block().setBlockData(snapshot.original());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        revertWaterIfAny(event.getPlayer());
    }

    /** One-time (idempotent) +2 hearts the first time Sorrowess is found anywhere in the player's inventory. */
    private void checkSorrowessHealthBonus(Player player) {
        boolean hasSorrowess = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (weaponManager.getWeaponType(item) == WeaponType.SORROWESS) {
                hasSorrowess = true;
                break;
            }
        }
        if (!hasSorrowess) return;

        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null || attribute.getModifier(SORROWESS_HEALTH_KEY) != null) return;

        attribute.addModifier(new AttributeModifier(SORROWESS_HEALTH_KEY, 4, AttributeModifier.Operation.ADD_NUMBER));
        player.setHealth(Math.min(player.getHealth() + 4, attribute.getValue()));
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Sorrowess grants you 2 extra hearts.", net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA));
    }

    private void judasFlicker(Player player) {
        if (globalTick % 5 == 0) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(160, 20, 20), 0.8f);
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add((Math.random() - 0.5) * 0.6, 1 + Math.random() * 0.4, (Math.random() - 0.5) * 0.6),
                    1, 0, 0, 0, 0, dust);
        }

        // Randomly (roughly once every ~20s on average) grants a mixed blessing:
        // a burst of speed paired with hunger, betrayer-flavored.
        if (!player.hasPotionEffect(PotionEffectType.SPEED) && Math.random() < 0.0008) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Judas's gift: swift, but starving.", net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
        }
    }

    /** Passive 2, Rust: a chance on each hit to corrode a random piece of the target's armor and mend one of yours. */
    @EventHandler
    public void onMayimAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (weaponManager.getWeaponType(player.getInventory().getItemInMainHand()) != WeaponType.STAFF_OF_MOSES) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        if (Math.random() >= 0.2) return; // "there's some chance"

        ItemStack[] targetArmor = target.getEquipment() != null ? target.getEquipment().getArmorContents() : null;
        if (targetArmor == null) return;
        java.util.List<ItemStack> damageable = new java.util.ArrayList<>();
        for (ItemStack piece : targetArmor) {
            if (piece != null && piece.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg
                    && dmg.getDamage() < piece.getType().getMaxDurability() - 1) {
                damageable.add(piece);
            }
        }
        if (!damageable.isEmpty()) {
            ItemStack piece = damageable.get((int) (Math.random() * damageable.size()));
            damageDurability(piece, 25);
        }

        ItemStack[] ownArmor = player.getInventory().getArmorContents();
        java.util.List<ItemStack> repairable = new java.util.ArrayList<>();
        for (ItemStack piece : ownArmor) {
            if (piece != null && piece.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) {
                repairable.add(piece);
            }
        }
        if (!repairable.isEmpty()) {
            ItemStack piece = repairable.get((int) (Math.random() * repairable.size()));
            damageDurability(piece, -25);
        }

        Particle.DustOptions rust = new Particle.DustOptions(Color.fromRGB(140, 80, 30), 1f);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0, rust);
    }

    private void damageDurability(ItemStack item, int amount) {
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta)) return;
        int newDamage = Math.max(0, Math.min(item.getType().getMaxDurability() - 1, meta.getDamage() + amount));
        meta.setDamage(newDamage);
        item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) meta);
    }
}
