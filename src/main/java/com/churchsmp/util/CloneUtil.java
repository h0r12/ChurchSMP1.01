package com.churchsmp.util;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Unified utility for spawning hyper-realistic humanoid clones across all ChurchSMP abilities.
 * Clones mirror the player's skin, armor, held weapons, combat animations, and AI.
 */
public class CloneUtil {

    private static final Random RANDOM = new Random();

    public static class CloneConfig {
        public Player owner;
        public Location location;
        public Component displayName;
        public NamespacedKey tagKey;
        public String tagValue;
        public int durationTicks = 400; // default 20s
        public double movementSpeed = 0.32; // sprint speed
        public double followRange = 24.0;
        public Vector formationOffset;
        public boolean followOwner = true;
        public boolean syncSneak = true;
        public double attackRange = 3.2;
        public double attackDamage = 1.0;
        public boolean jumpCrit = true;
        public boolean showNameTag = true;
        public boolean hasArms = true;
        public BiConsumer<Zombie, LivingEntity> onAttack;
        public Consumer<Zombie> onTick;
        public Consumer<Zombie> onDespawn;
        public ItemStack mainHandOverride;
        public ItemStack offHandOverride;
        public ItemStack helmetOverride;
        public ItemStack chestplateOverride;
        public ItemStack leggingsOverride;
        public ItemStack bootsOverride;
    }

    /**
     * Spawns a realistic humanoid clone.
     */
    public static Zombie spawnRealisticClone(ChurchSMP plugin, CloneConfig config) {
        if (config.location == null || config.location.getWorld() == null || config.owner == null) return null;

        Zombie clone = config.location.getWorld().spawn(config.location, Zombie.class, z -> {
            z.setAdult();
            z.setSilent(true);
            z.setCanPickupItems(false);

            if (config.displayName != null && config.showNameTag) {
                z.customName(config.displayName);
                z.setCustomNameVisible(true);
            } else if (config.showNameTag) {
                z.customName(config.owner.name());
                z.setCustomNameVisible(true);
            } else {
                z.customName(null);
                z.setCustomNameVisible(false);
            }

            if (config.tagKey != null && config.tagValue != null) {
                z.getPersistentDataContainer().set(config.tagKey, PersistentDataType.STRING, config.tagValue);
            }

            // 1. Hide green zombie flesh completely with ambient invisibility + fire resistance
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 12000, 0, false, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 12000, 0, false, false, false));

            // 2. Equip 1:1 player skull with owner's skin and matching armor
            if (config.helmetOverride != null) {
                z.getEquipment().setHelmet(config.helmetOverride.clone());
            } else {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta skull) {
                    skull.setOwningPlayer(config.owner);
                    head.setItemMeta(skull);
                }
                z.getEquipment().setHelmet(head);
            }

            if (config.hasArms) {
                if (config.chestplateOverride != null) {
                    z.getEquipment().setChestplate(config.chestplateOverride.clone());
                } else {
                    ItemStack cp = config.owner.getInventory().getChestplate();
                    if (cp != null && cp.getType() != Material.AIR) {
                        z.getEquipment().setChestplate(cp.clone());
                    }
                }
            } else {
                z.getEquipment().setChestplate(null);
            }

            if (config.leggingsOverride != null) {
                z.getEquipment().setLeggings(config.leggingsOverride.clone());
            } else {
                ItemStack leg = config.owner.getInventory().getLeggings();
                if (leg != null && leg.getType() != Material.AIR) {
                    z.getEquipment().setLeggings(leg.clone());
                }
            }

            if (config.bootsOverride != null) {
                z.getEquipment().setBoots(config.bootsOverride.clone());
            } else {
                ItemStack boot = config.owner.getInventory().getBoots();
                if (boot != null && boot.getType() != Material.AIR) {
                    z.getEquipment().setBoots(boot.clone());
                }
            }

            // Equip mainhand & offhand (only if hasArms is true)
            if (config.hasArms) {
                if (config.mainHandOverride != null) {
                    z.getEquipment().setItemInMainHand(config.mainHandOverride.clone());
                } else {
                    ItemStack mainHand = config.owner.getInventory().getItemInMainHand();
                    if (mainHand != null && mainHand.getType() != Material.AIR) {
                        z.getEquipment().setItemInMainHand(mainHand.clone());
                    }
                }

                if (config.offHandOverride != null) {
                    z.getEquipment().setItemInOffHand(config.offHandOverride.clone());
                } else {
                    ItemStack offHand = config.owner.getInventory().getItemInOffHand();
                    if (offHand != null && offHand.getType() != Material.AIR) {
                        z.getEquipment().setItemInOffHand(offHand.clone());
                    }
                }
            } else {
                z.getEquipment().setItemInMainHand(null);
                z.getEquipment().setItemInOffHand(null);
            }

            // Zero drop chance
            z.getEquipment().setHelmetDropChance(0.0f);
            z.getEquipment().setChestplateDropChance(0.0f);
            z.getEquipment().setLeggingsDropChance(0.0f);
            z.getEquipment().setBootsDropChance(0.0f);
            z.getEquipment().setItemInMainHandDropChance(0.0f);
            z.getEquipment().setItemInOffHandDropChance(0.0f);

            // 3. Living AI with sprint speed
            z.setAI(true);
            if (z.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(config.movementSpeed);
            }
            if (z.getAttribute(Attribute.FOLLOW_RANGE) != null) {
                z.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(config.followRange);
            }
        });

        // 4. Realistic behavior loop (sneak sync, dynamic tracking, weapon swinging, formation movement)
        new BukkitRunnable() {
            int lifeTicks = 0;

            @Override
            public void run() {
                lifeTicks += 4;
                if (!clone.isValid() || clone.isDead() || lifeTicks > config.durationTicks || !config.owner.isOnline()) {
                    if (config.onDespawn != null) {
                        config.onDespawn.accept(clone);
                    }
                    if (clone.isValid()) {
                        clone.remove();
                    }
                    cancel();
                    return;
                }

                if (config.syncSneak) {
                    clone.setSneaking(config.owner.isSneaking());
                }

                // Find nearest hostile or target
                LivingEntity enemyTarget = null;
                double nearestDist = config.followRange;
                for (LivingEntity nearby : clone.getWorld().getNearbyLivingEntities(clone.getLocation(), config.followRange)) {
                    if (nearby.equals(config.owner) || nearby.equals(clone)) continue;
                    if (config.tagKey != null && nearby.getPersistentDataContainer().has(config.tagKey, PersistentDataType.STRING)) continue;
                    if (nearby instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR)) continue;
                    double dist = nearby.getLocation().distance(clone.getLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        enemyTarget = nearby;
                    }
                }

                if (enemyTarget != null) {
                    clone.setTarget(enemyTarget);

                    if (nearestDist <= config.attackRange) {
                        clone.swingMainHand();
                        if (config.onAttack != null) {
                            config.onAttack.accept(clone, enemyTarget);
                        } else if (config.attackDamage > 0) {
                            enemyTarget.damage(config.attackDamage, config.owner);
                            clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                            clone.getWorld().spawnParticle(Particle.SWEEP_ATTACK, enemyTarget.getLocation().add(0, 1.0, 0), 1);
                        }

                        if (config.jumpCrit && RANDOM.nextInt(3) == 0 && clone.isOnGround()) {
                            clone.setVelocity(new Vector(0, 0.35, 0));
                        }
                    }
                } else if (config.followOwner) {
                    clone.setTarget(null);
                    Location followLoc = (config.formationOffset != null)
                            ? config.owner.getLocation().add(config.formationOffset)
                            : config.owner.getLocation();
                    try {
                        clone.getPathfinder().moveTo(followLoc);
                    } catch (Throwable ignored) {
                        Vector toOwner = followLoc.toVector().subtract(clone.getLocation().toVector());
                        if (toOwner.lengthSquared() > 1.5) {
                            clone.setVelocity(toOwner.normalize().multiply(0.25).setY(clone.getVelocity().getY()));
                        }
                    }
                }

                if (config.onTick != null) {
                    config.onTick.accept(clone);
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);

        return clone;
    }
}
