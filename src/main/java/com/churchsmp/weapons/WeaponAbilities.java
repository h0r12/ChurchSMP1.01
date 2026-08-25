package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

/**
 * Actual gameplay effects triggered by each weapon ability.
 * execute() returns how many seconds the trigger slot that was just used
 * should go on cooldown for — most weapons just echo the configured
 * default, but VoidBreaker needs a different cooldown per named ability
 * even though both share the same "ability 1" trigger.
 */
public class WeaponAbilities {

    private final ChurchSMP plugin;
    private final AlignmentManager alignmentManager;

    private static final Set<Material> GOLDEN_FOODS = EnumSet.of(
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_CARROT);

    public WeaponAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentManager = plugin.getAlignmentManager();
    }

    /** Returns the cooldown (in seconds) to apply to the trigger slot just used. */
    public int execute(WeaponType type, int ability, Player player) {
        switch (type) {
            case BLADE_OF_ARCHANGEL:
                if (ability == 1) radiantBarrier(player); else holyNova(player);
                break;
            case SWORD_OF_DAVID:
                if (ability == 1) smiteBeam(player); else giantSlayer(player);
                break;
            case STAFF_OF_MOSES:
                if (ability == 1) partingWave(player); else seaPath(player);
                break;
            case SCYTHE_OF_CAIN:
                if (ability == 1) lifestealStrike(player); else markOfCain(player);
                break;
            case TRIDENT_OF_LEVIATHAN:
                if (ability == 1) whirlpoolPull(player); else leviathanRoar(player);
                break;
            case BLADE_OF_JUDAS:
                if (ability == 1) backstabEscape(player); else thirtyPiecesOfSilver(player);
                break;
            case VOIDBREAKER:
                if (ability == 2) {
                    spacedBound(player);
                    return 0; // Spaced Bound has no cooldown, by design
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.containsEnchantment(Enchantment.BREACH)) {
                    lightlessPhos(player);
                    return 120;
                } else {
                    spiralBoom(player);
                    return 15;
                }
        }
        return plugin.getWeaponManager().getConfiguredCooldown(ability);
    }

    // ---------------- GOOD ----------------

    private void radiantBarrier(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 0));
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 40, 0.6, 1, 0.6, 0.02);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        msg(player, "The Archangel shields you.");
    }

    private void holyNova(Player player) {
        double radius = 5;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player target) {
                AlignmentTier tier = alignmentManager.getTier(target);
                if (tier.isEvil()) {
                    target.damage(6, player);
                } else if (tier.isGood()) {
                    target.setHealth(Math.min(target.getHealth() + 4, target.getMaxHealth()));
                }
            } else if (e instanceof LivingEntity le && isUndead(le)) {
                le.damage(6, player);
            }
        }
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation(), 1);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 100, radius / 2, 1, radius / 2, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.2f);
        msg(player, "Holy Nova erupts around you!");
    }

    private void smiteBeam(Player player) {
        LivingEntity target = getTargetedEntity(player, 20);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }
        double bonus = (target instanceof Player p && alignmentManager.getTier(p).isEvil()) || isUndead(target) ? 6 : 3;
        target.damage(bonus, player);
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1.6f);
        msg(player, "Smite Beam strikes " + target.getName() + "!");
    }

    private void giantSlayer(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
        LivingEntity target = getTargetedEntity(player, 4);
        if (target != null) {
            target.damage(player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue() * 2, player);
        }
        msg(player, "Giant Slayer empowers your strike.");
    }

    private void partingWave(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        double radius = 3;
        for (Entity e : player.getNearbyEntities(radius, 2, radius)) {
            if (e instanceof LivingEntity le && !e.equals(player)) {
                le.setVelocity(dir.clone().multiply(1.4).setY(0.3));
            }
        }
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(dir.clone().multiply(2)), 30, 1, 0.3, 1, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 1f);
        msg(player, "The waters part before you.");
    }

    private void seaPath(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location origin = player.getLocation();
        for (int i = 1; i <= 6; i++) {
            Block b = origin.clone().add(dir.clone().multiply(i)).getBlock();
            if (b.getType().isAir()) {
                org.bukkit.block.data.BlockData original = b.getBlockData();
                b.setType(Material.WATER);
                new BukkitRunnable() {
                    @Override public void run() {
                        if (b.getType() == Material.WATER) {
                            b.setBlockData(original);
                        }
                    }
                }.runTaskLater(plugin, 100L); // reverts after 5s
            }
        }
        msg(player, "A path opens through the waters.");
    }

    // ---------------- EVIL ----------------

    private void lifestealStrike(Player player) {
        LivingEntity target = getTargetedEntity(player, 4);
        if (target == null) {
            msg(player, "No target in reach.");
            return;
        }
        double dmg = 5;
        target.damage(dmg, player);
        player.setHealth(Math.min(player.getHealth() + dmg * 0.5, player.getMaxHealth()));
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 10);
        msg(player, "You drain " + target.getName() + "'s life force.");
    }

    private void markOfCain(Player player) {
        LivingEntity target = getTargetedEntity(player, 15);
        if (target == null) {
            msg(player, "No target in sight.");
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        msg(player, "The Mark of Cain is placed upon " + target.getName() + ".");
    }

    private void whirlpoolPull(Player player) {
        double radius = 5;
        for (Entity e : player.getNearbyEntities(radius, 2, radius)) {
            if (e instanceof LivingEntity le && !e.equals(player)) {
                Vector pull = player.getLocation().toVector().subtract(le.getLocation().toVector()).normalize().multiply(0.6);
                le.setVelocity(pull);
            }
        }
        player.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, player.getLocation(), 60, radius / 2, 0.5, radius / 2, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 0.8f);
        msg(player, "The Leviathan drags your foes closer.");
    }

    private void leviathanRoar(Player player) {
        double radius = 6;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity le && !e.equals(player)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        }
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation(), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 0.7f);
        msg(player, "The Leviathan's roar echoes out!");
    }

    private void backstabEscape(Player player) {
        LivingEntity target = getTargetedEntity(player, 4);
        if (target != null) {
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            Vector targetFacing = target.getLocation().getDirection().normalize();
            boolean isBehind = toTarget.dot(targetFacing) > 0.3;
            target.damage(isBehind ? 8 : 4, player);
        }
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 40, 0.4, 0.6, 0.4, 0.02);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0));
        msg(player, "You vanish into smoke.");
    }

    private void thirtyPiecesOfSilver(Player player) {
        double cost = Math.min(4, player.getHealth() - 1);
        if (cost <= 0) {
            msg(player, "Too weak to pay the price.");
            return;
        }
        player.setHealth(player.getHealth() - cost);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 2));
        LivingEntity target = getTargetedEntity(player, 4);
        if (target != null) {
            target.damage(10, player);
        }
        msg(player, "You pay in blood for power.");
    }

    // ---------------- NULLIFIED (VoidBreaker) ----------------

    /**
     * Density-mode Ability 1. Draws a custom particle "lightning spiral" at
     * whatever the player is looking at (entity or block), pulsing damage
     * and a Blindness+Slowness III debuff to anything in a ~4x4 area there
     * over 5 seconds. This is fully custom particle work — no vanilla
     * LightningBolt entity is summoned.
     */
    private void spiralBoom(Player player) {
        Location epicenter = resolveCrosshairLocation(player, 20);
        player.playSound(epicenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
        player.playSound(epicenter, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1f);
        msg(player, "Spiral Boom crackles to life!");

        double damagePerPulse = plugin.getConfig().getDouble("voidbreaker.spiral-boom-damage-per-pulse", 3);
        double areaRadius = 2.0; // approximates the requested "4x4" area

        new BukkitRunnable() {
            int tick = 0; // counts in 2-tick steps, 50 steps = 100 ticks = 5s

            @Override
            public void run() {
                try {
                    if (tick >= 50 || epicenter.getWorld() == null
                            || !epicenter.getWorld().isChunkLoaded(epicenter.getBlockX() >> 4, epicenter.getBlockZ() >> 4)) {
                        cancel();
                        return;
                    }
                    double angle = tick * 0.7;
                    double radius = 1.3;
                    double height = ((tick % 20) / 20.0) * 2.5;
                    Location point = epicenter.clone().add(radius * Math.cos(angle), height, radius * Math.sin(angle));

                    // ELECTRIC_SPARK alone is tiny and easy to miss — layer a bright
                    // END_ROD trail on top so the spiral is obvious at a glance.
                    epicenter.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 6, 0.05, 0.05, 0.05, 0.02);
                    epicenter.getWorld().spawnParticle(Particle.END_ROD, point, 2, 0.02, 0.02, 0.02, 0.01);

                    if (tick % 5 == 0) {
                        epicenter.getWorld().spawnParticle(Particle.FLASH, epicenter.clone().add(0, 1, 0), 1);
                        for (Entity e : epicenter.getWorld().getNearbyEntities(epicenter, areaRadius, 3, areaRadius)) {
                            if (e instanceof LivingEntity le && !le.equals(player)) {
                                le.damage(damagePerPulse, player);
                                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
                                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2));
                            }
                        }
                    }
                    tick++;
                } catch (Exception ex) {
                    // A silently-dying repeating task looks exactly like "it did
                    // nothing" — log it loudly instead so it shows up in console.
                    plugin.getLogger().warning("Spiral Boom animation error: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Breach-mode Ability 1. Dark AoE burst centered on the caster dealing
     * heavy damage, and "nullifying" whatever every nearby player is
     * holding — durability damage to weapons/tools, consuming non-golden
     * food, or long-cooldown-locking a target's own VoidBreaker.
     */
    private void lightlessPhos(Player player) {
        double range = 5; // approximates "10x10"
        double damage = plugin.getConfig().getDouble("voidbreaker.lightless-phos-damage", 20);

        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
        for (int i = 0; i < 60; i++) {
            double x = (Math.random() - 0.5) * range * 2;
            double y = Math.random() * 3;
            double z = (Math.random() - 0.5) * range * 2;
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(x, y, z), 1, 0, 0, 0, 0);
        }
        msg(player, "Lightless Ph\u014ds consumes the light around you.");

        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (e.equals(player) || !(e instanceof LivingEntity le)) continue;
            le.damage(damage, player);

            if (e instanceof Player target) {
                nullifyHeldItem(target);
            }
        }
    }

    private void nullifyHeldItem(Player target) {
        ItemStack held = target.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;

        if (held.getType() == Material.MACE) {
            plugin.getWeaponManager().putOnCooldown(target, WeaponType.VOIDBREAKER, 1, 120);
            plugin.getWeaponManager().putOnCooldown(target, WeaponType.VOIDBREAKER, 2, 120);
            title(target, "Nullifying yours pride, after all");
            return;
        }
        if (isWeaponMaterial(held.getType())) {
            damageDurability(target, held, 0.20);
            title(target, "Nullifying yours wrath");
            return;
        }
        if (isDiggingMaterial(held.getType())) {
            damageDurability(target, held, 0.20);
            title(target, "Nullifying yours greed");
            return;
        }
        if (held.getType().isEdible() && !GOLDEN_FOODS.contains(held.getType())) {
            held.setAmount(Math.max(0, held.getAmount() - 1));
            target.getInventory().setItemInMainHand(held);
            title(target, "Nullifying yours gluttony");
        }
    }

    private void damageDurability(Player target, ItemStack item, double percentOfMax) {
        if (!(item.getItemMeta() instanceof Damageable dmg)) return;
        if (item.getItemMeta().isUnbreakable()) return;
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;
        int addDamage = Math.max(1, (int) (maxDurability * percentOfMax));
        dmg.setDamage(Math.min(maxDurability, dmg.getDamage() + addDamage));
        item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
        target.getInventory().setItemInMainHand(item);
    }

    private boolean isWeaponMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || material == Material.TRIDENT;
    }

    private boolean isDiggingMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE") || name.endsWith("_HOE");
    }

    /**
     * Ability 2. Toggles VoidBreaker between Density mode (default) and
     * Breach mode by swapping the real vanilla enchantments on the item —
     * this lets the game engine handle the actual mace smash-attack math,
     * rather than the plugin re-implementing it. Also gives a short
     * forward dash for a bit of mobility. No cooldown.
     */
    private void spacedBound(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean wasBreach = item.containsEnchantment(Enchantment.BREACH);

        if (wasBreach) {
            item.removeEnchantment(Enchantment.BREACH);
            item.addUnsafeEnchantment(Enchantment.DENSITY, 6);
            msg(player, "VoidBreaker shifts into Density mode.");
        } else {
            item.removeEnchantment(Enchantment.DENSITY);
            item.addUnsafeEnchantment(Enchantment.BREACH, 6);
            msg(player, "VoidBreaker shifts into Breach mode.");
        }
        player.getInventory().setItemInMainHand(item);

        Vector dash = player.getLocation().getDirection().normalize().multiply(1.4);
        dash.setY(0.35);
        player.setVelocity(dash);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);
    }

    // ---------------- helpers ----------------

    private boolean isUndead(LivingEntity le) {
        return switch (le.getType()) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY, WITHER_SKELETON,
                    PHANTOM, ZOMBIFIED_PIGLIN, WITHER -> true;
            default -> false;
        };
    }

    private LivingEntity getTargetedEntity(Player player, double range) {
        Entity target = player.getTargetEntity((int) range);
        return target instanceof LivingEntity le ? le : null;
    }

    /** Entity under the crosshair if there is one, else the targeted block's location, else a point ahead of the player. */
    private Location resolveCrosshairLocation(Player player, double range) {
        LivingEntity entity = getTargetedEntity(player, range);
        if (entity != null) return entity.getLocation();
        Block block = player.getTargetBlockExact((int) range);
        if (block != null) return block.getLocation().add(0.5, 1, 0.5);
        return player.getLocation().add(player.getLocation().getDirection().multiply(5));
    }

    private void msg(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }

    private void title(Player player, String text) {
        player.showTitle(Title.title(Component.empty(),
                Component.text(text, NamedTextColor.DARK_PURPLE)));
    }
}
