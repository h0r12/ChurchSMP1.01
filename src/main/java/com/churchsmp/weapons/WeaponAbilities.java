package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Actual gameplay effects triggered by each weapon ability.
 * Kept intentionally straightforward (potion effects / particles / simple
 * physics) so each method is a clear extension point for further tuning.
 */
public class WeaponAbilities {

    private final ChurchSMP plugin;
    private final AlignmentManager alignmentManager;

    public WeaponAbilities(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentManager = plugin.getAlignmentManager();
    }

    public void execute(WeaponType type, int ability, Player player) {
        switch (type) {
            case BLADE_OF_ARCHANGEL -> {
                if (ability == 1) radiantBarrier(player); else holyNova(player);
            }
            case SWORD_OF_DAVID -> {
                if (ability == 1) smiteBeam(player); else giantSlayer(player);
            }
            case STAFF_OF_MOSES -> {
                if (ability == 1) partingWave(player); else seaPath(player);
            }
            case SCYTHE_OF_CAIN -> {
                if (ability == 1) lifestealStrike(player); else markOfCain(player);
            }
            case TRIDENT_OF_LEVIATHAN -> {
                if (ability == 1) whirlpoolPull(player); else leviathanRoar(player);
            }
            case BLADE_OF_JUDAS -> {
                if (ability == 1) backstabEscape(player); else thirtyPiecesOfSilver(player);
            }
            case STAFF_OF_SOLOMON -> {
                if (ability == 1) judgment(player); else wisdomsVerdict(player);
            }
        }
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
                b.setType(org.bukkit.Material.WATER);
                new BukkitRunnable() {
                    @Override public void run() {
                        if (b.getType() == org.bukkit.Material.WATER) {
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

    // ---------------- NULLIFIED ----------------

    private void judgment(Player player) {
        double radius = 15;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player target) {
                AlignmentTier tier = alignmentManager.getTier(target);
                Particle.DustOptions dust = new Particle.DustOptions(
                        tier.isGood() ? org.bukkit.Color.YELLOW
                                : tier.isEvil() ? org.bukkit.Color.RED
                                : org.bukkit.Color.WHITE, 1.2f);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 2.2, 0), 20, 0.3, 0.1, 0.3, dust);
            }
        }
        msg(player, "You perceive the alignment of those nearby.");
    }

    private void wisdomsVerdict(Player player) {
        double radius = 8;
        Collection<Entity> nearby = player.getNearbyEntities(radius, radius, radius);
        long durationTicks = 100L; // 5s
        for (Entity e : nearby) {
            if (e instanceof Player target) {
                plugin.getNullifiedZoneManager().nullify(target, durationTicks);
            }
        }
        plugin.getNullifiedZoneManager().nullify(player, durationTicks);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation(), 80, radius / 2, 1, radius / 2, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        msg(player, "Wisdom's Verdict silences all abilities nearby.");
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

    private void msg(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }
}
