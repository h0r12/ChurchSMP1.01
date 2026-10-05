package com.churchsmp.util;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Unified utility for spawning hyper-realistic humanoid clones across all ChurchSMP abilities.
 * Supports multiple human-like Doppelgänger visual models:
 * - LIVING_RIG: Invisible mob host with a natural-posture ArmorStand puppet (arms naturally down, no zombie stretch).
 * - KINETIC: Standalone procedural walking rig with live limb swing physics (legs & arms swing naturally).
 * - SPECTRAL: Translucent weeping soul phantom with ethereal particles and mirror shatter.
 */
public class CloneUtil {

    private static final Random RANDOM = new Random();

    public enum CloneModelType {
        LIVING_RIG,
        KINETIC,
        SPECTRAL,
        CLASSIC_ZOMBIE
    }

    // Registry of active test clones per player
    private static final Map<UUID, List<Entity>> testClones = new ConcurrentHashMap<>();

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
        public CloneModelType modelType = CloneModelType.LIVING_RIG;
        public BiConsumer<LivingEntity, LivingEntity> onAttack;
        public Consumer<LivingEntity> onTick;
        public Consumer<LivingEntity> onDespawn;
        public boolean forceSkinHead = false;
        public ItemStack mainHandOverride;
        public ItemStack offHandOverride;
        public ItemStack helmetOverride;
        public ItemStack chestplateOverride;
        public ItemStack leggingsOverride;
        public ItemStack bootsOverride;
    }

    /**
     * Clears all test clones for a given player.
     */
    public static void clearTestClones(Player player) {
        List<Entity> list = testClones.remove(player.getUniqueId());
        if (list != null) {
            for (Entity e : list) {
                if (e != null && e.isValid()) {
                    e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 1.0, 0), 10, 0.2, 0.4, 0.2, 0.05);
                    e.remove();
                }
            }
        }
    }

    /**
     * Registers an entity as a test clone.
     */
    public static void registerTestClone(Player player, Entity entity) {
        testClones.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(entity);
    }

    /**
     * Spawns a realistic humanoid clone based on the specified model type.
     */
    public static LivingEntity spawnRealisticClone(ChurchSMP plugin, CloneConfig config) {
        if (config.location == null || config.location.getWorld() == null || config.owner == null) return null;

        return switch (config.modelType) {
            case LIVING_RIG -> spawnLivingRigClone(plugin, config);
            case KINETIC -> spawnKineticClone(plugin, config);
            case SPECTRAL -> spawnSpectralClone(plugin, config);
            case CLASSIC_ZOMBIE -> spawnClassicZombieClone(plugin, config);
        };
    }

    /**
     * MODEL A: LIVING RIG
     * Invisible Host Zombie (provides smart pathfinding, jumping, sprinting)
     * carrying an invisible ArmorStand puppet with arms down at sides and exact player gear.
     * ZERO zombie outstretched arms!
     */
    private static LivingEntity spawnLivingRigClone(ChurchSMP plugin, CloneConfig config) {
        Zombie host = config.location.getWorld().spawn(config.location, Zombie.class, z -> {
            z.setAdult();
            z.setSilent(true);
            z.setCanPickupItems(false);
            z.setInvisible(true);
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 72000, 0, false, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 72000, 0, false, false, false));

            if (config.tagKey != null && config.tagValue != null) {
                z.getPersistentDataContainer().set(config.tagKey, PersistentDataType.STRING, config.tagValue);
            }

            z.setAI(true);
            if (z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(config.movementSpeed);
            }
            if (z.getAttribute(Attribute.GENERIC_FOLLOW_RANGE) != null) {
                z.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(config.followRange);
            }
        });

        // Spawn custom human ArmorStand puppet attached to host
        ArmorStand puppet = config.location.getWorld().spawn(config.location, ArmorStand.class, stand -> {
            stand.setInvisible(true); // Hides the wooden frame; armor and head remain fully visible
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setSmall(false);

            if (config.showNameTag && config.displayName != null) {
                stand.customName(config.displayName);
                stand.setCustomNameVisible(true);
            } else if (config.showNameTag) {
                stand.customName(config.owner.name());
                stand.setCustomNameVisible(true);
            } else {
                stand.setCustomNameVisible(false);
            }

            // Natural human idle arm posture (arms resting naturally at sides)
            stand.setRightArmPose(new EulerAngle(Math.toRadians(8), 0, Math.toRadians(4)));
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(8), 0, Math.toRadians(-4)));

            equipStand(stand, config);
        });

        // Tracking & Animation Loop
        new BukkitRunnable() {
            int ticks = 0;
            int attackAnimTicks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (!host.isValid() || host.isDead() || ticks > config.durationTicks || !config.owner.isOnline()) {
                    if (config.onDespawn != null) config.onDespawn.accept(host);
                    if (puppet.isValid()) puppet.remove();
                    if (host.isValid()) host.remove();
                    cancel();
                    return;
                }

                // Sync puppet position & rotation to host
                Location hostLoc = host.getLocation();
                puppet.teleport(hostLoc);

                // Walking limb movement
                boolean isMoving = host.getVelocity().lengthSquared() > 0.005;
                if (attackAnimTicks <= 0) {
                    if (isMoving) {
                        double swing = Math.sin(ticks * 0.45) * 0.45;
                        puppet.setRightArmPose(new EulerAngle(swing, 0, Math.toRadians(4)));
                        puppet.setLeftArmPose(new EulerAngle(-swing, 0, Math.toRadians(-4)));
                        puppet.setRightLegPose(new EulerAngle(-swing * 0.8, 0, 0));
                        puppet.setLeftLegPose(new EulerAngle(swing * 0.8, 0, 0));
                    } else {
                        puppet.setRightArmPose(new EulerAngle(Math.toRadians(8), 0, Math.toRadians(4)));
                        puppet.setLeftArmPose(new EulerAngle(Math.toRadians(8), 0, Math.toRadians(-4)));
                        puppet.setRightLegPose(new EulerAngle(0, 0, 0));
                        puppet.setLeftLegPose(new EulerAngle(0, 0, 0));
                    }
                } else {
                    attackAnimTicks--;
                    // Downward weapon slash animation
                    puppet.setRightArmPose(new EulerAngle(Math.toRadians(-70 + (4 - attackAnimTicks) * 35), Math.toRadians(-20), 0));
                }

                // AI combat handling
                LivingEntity enemy = findNearestEnemy(host, config);
                if (enemy != null) {
                    host.setTarget(enemy);
                    double dist = enemy.getLocation().distance(hostLoc);
                    if (dist <= config.attackRange && attackAnimTicks <= 0) {
                        attackAnimTicks = 4;
                        puppet.getWorld().playSound(hostLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                        puppet.getWorld().spawnParticle(Particle.SWEEP_ATTACK, enemy.getLocation().add(0, 1.0, 0), 1);
                        if (config.onAttack != null) {
                            config.onAttack.accept(host, enemy);
                        } else if (config.attackDamage > 0) {
                            enemy.damage(config.attackDamage, config.owner);
                        }
                    }
                } else if (config.followOwner) {
                    host.setTarget(null);
                    Location targetLoc = (config.formationOffset != null)
                            ? config.owner.getLocation().add(config.formationOffset)
                            : config.owner.getLocation();
                    try {
                        host.getPathfinder().moveTo(targetLoc);
                    } catch (Throwable ignored) {
                        Vector diff = targetLoc.toVector().subtract(hostLoc.toVector());
                        if (diff.lengthSquared() > 1.5) {
                            host.setVelocity(diff.normalize().multiply(0.24).setY(host.getVelocity().getY()));
                        }
                    }
                }

                if (config.onTick != null) {
                    config.onTick.accept(host);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);

        return host;
    }

    /**
     * MODEL B: KINETIC PUPPET
     * Standalone animated ArmorStand with smooth procedural walking stride,
     * head tracking, and clean sword swings.
     */
    private static LivingEntity spawnKineticClone(ChurchSMP plugin, CloneConfig config) {
        ArmorStand stand = config.location.getWorld().spawn(config.location, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(true);
            s.setSmall(false);

            if (config.showNameTag && config.displayName != null) {
                s.customName(config.displayName);
                s.setCustomNameVisible(true);
            } else if (config.showNameTag) {
                s.customName(config.owner.name());
                s.setCustomNameVisible(true);
            }

            if (config.tagKey != null && config.tagValue != null) {
                s.getPersistentDataContainer().set(config.tagKey, PersistentDataType.STRING, config.tagValue);
            }

            equipStand(s, config);
        });

        new BukkitRunnable() {
            int ticks = 0;
            int attackTicks = 0;

            @Override
            public void run() {
                ticks++;
                if (!stand.isValid() || ticks > config.durationTicks || !config.owner.isOnline()) {
                    if (config.onDespawn != null) config.onDespawn.accept(stand);
                    if (stand.isValid()) stand.remove();
                    cancel();
                    return;
                }

                Location current = stand.getLocation();
                LivingEntity enemy = findNearestEnemy(stand, config);
                Location dest = (enemy != null)
                        ? enemy.getLocation()
                        : (config.followOwner
                                ? (config.formationOffset != null ? config.owner.getLocation().add(config.formationOffset) : config.owner.getLocation())
                                : (config.formationOffset != null ? current.clone().add(config.formationOffset) : null));

                if (dest != null && dest.getWorld().equals(current.getWorld())) {
                    Vector diff = dest.toVector().subtract(current.toVector());
                    double dist = diff.length();

                    // Look toward destination
                    float yaw = (float) Math.toDegrees(Math.atan2(-diff.getX(), diff.getZ()));
                    current.setYaw(yaw);

                    if (dist > 1.8) {
                        Vector step = diff.normalize().multiply(0.22);
                        step.setY(stand.getVelocity().getY());
                        stand.setVelocity(step);

                        // Dynamic walk cycle animation (procedural sine oscillation)
                        double swing = Math.sin(ticks * 0.4) * 0.55;
                        stand.setRightLegPose(new EulerAngle(-swing, 0, 0));
                        stand.setLeftLegPose(new EulerAngle(swing, 0, 0));
                        if (attackTicks <= 0) {
                            stand.setRightArmPose(new EulerAngle(swing * 0.8, 0, Math.toRadians(6)));
                            stand.setLeftArmPose(new EulerAngle(-swing * 0.8, 0, Math.toRadians(-6)));
                        }
                    } else {
                        // Standing still
                        stand.setRightLegPose(new EulerAngle(0, 0, 0));
                        stand.setLeftLegPose(new EulerAngle(0, 0, 0));
                    }

                    // Always check attack if in range, even while moving
                    if (enemy != null && dist <= config.attackRange && attackTicks <= 0) {
                        attackTicks = 6;
                        stand.getWorld().playSound(current, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                        stand.getWorld().spawnParticle(Particle.SWEEP_ATTACK, enemy.getLocation().add(0, 1.0, 0), 1);
                        if (config.onAttack != null) {
                            config.onAttack.accept(stand, enemy);
                        } else if (config.attackDamage > 0) {
                            enemy.damage(config.attackDamage, config.owner);
                        }
                    }
                }

                if (attackTicks > 0) {
                    attackTicks--;
                    stand.setRightArmPose(new EulerAngle(Math.toRadians(-60 + (6 - attackTicks) * 20), Math.toRadians(-15), 0));
                }

                if (config.onTick != null) {
                    config.onTick.accept(stand);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return stand;
    }

    /**
     * MODEL C: SPECTRAL MIRAGE
     * Translucent soul double draped in purple soul wisps and tears.
     * Glides with eerie precision; shatters like glass when struck.
     */
    private static LivingEntity spawnSpectralClone(ChurchSMP plugin, CloneConfig config) {
        ArmorStand spectral = config.location.getWorld().spawn(config.location, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(false); // Glides smoothly
            s.setSmall(false);

            if (config.showNameTag && config.displayName != null) {
                s.customName(config.displayName);
                s.setCustomNameVisible(true);
            } else if (config.showNameTag) {
                s.customName(config.owner.name());
                s.setCustomNameVisible(true);
            }

            if (config.tagKey != null && config.tagValue != null) {
                s.getPersistentDataContainer().set(config.tagKey, PersistentDataType.STRING, config.tagValue);
            }

            equipStand(s, config);
            s.setRightArmPose(new EulerAngle(Math.toRadians(15), 0, Math.toRadians(10)));
            s.setLeftArmPose(new EulerAngle(Math.toRadians(15), 0, Math.toRadians(-10)));
        });

        new BukkitRunnable() {
            int ticks = 0;
            Particle.DustOptions purpleMist = new Particle.DustOptions(Color.fromRGB(138, 43, 226), 1.2f);

            @Override
            public void run() {
                ticks++;
                if (!spectral.isValid() || ticks > config.durationTicks || !config.owner.isOnline()) {
                    if (config.onDespawn != null) config.onDespawn.accept(spectral);
                    if (spectral.isValid()) {
                        spectral.getWorld().spawnParticle(Particle.FLASH, spectral.getLocation().add(0, 1, 0), 1, Color.WHITE);
                        spectral.remove();
                    }
                    cancel();
                    return;
                }

                Location loc = spectral.getLocation();
                // Ethereal particle aura
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.9, 0), 2, 0.25, 0.4, 0.25, 0, purpleMist);
                if (ticks % 3 == 0) {
                    loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 0.2, 0), 1, 0.1, 0.1, 0.1, 0.02);
                }

                LivingEntity enemy = findNearestEnemy(spectral, config);
                Location targetLoc = (enemy != null) ? enemy.getLocation() : (config.followOwner ? config.owner.getLocation().add(0, 0.2, 0) : null);

                if (targetLoc != null) {
                    Vector dir = targetLoc.toVector().subtract(loc.toVector());
                    if (dir.length() > 1.5) {
                        loc.add(dir.normalize().multiply(0.24));
                        loc.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
                        spectral.teleport(loc);
                    } else if (enemy != null && ticks % 15 == 0) {
                        spectral.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.8f);
                        if (config.onAttack != null) {
                            config.onAttack.accept(spectral, enemy);
                        } else if (config.attackDamage > 0) {
                            enemy.damage(config.attackDamage, config.owner);
                        }
                    }
                }

                if (config.onTick != null) {
                    config.onTick.accept(spectral);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return spectral;
    }

    /**
     * MODEL D: CLASSIC ZOMBIE (Original fallback)
     */
    private static LivingEntity spawnClassicZombieClone(ChurchSMP plugin, CloneConfig config) {
        Zombie z = config.location.getWorld().spawn(config.location, Zombie.class, clone -> {
            clone.setAdult();
            clone.setSilent(true);
            clone.setCanPickupItems(false);
            clone.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 12000, 0, false, false, false));
            clone.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 12000, 0, false, false, false));

            if (config.showNameTag && config.displayName != null) {
                clone.customName(config.displayName);
                clone.setCustomNameVisible(true);
            } else if (config.showNameTag) {
                clone.customName(config.owner.name());
                clone.setCustomNameVisible(true);
            }

            if (config.tagKey != null && config.tagValue != null) {
                clone.getPersistentDataContainer().set(config.tagKey, PersistentDataType.STRING, config.tagValue);
            }

            equipLivingEntity(clone, config);
        });
        return z;
    }

    public static ItemStack createPlayerSkinHead(Player owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            try {
                skull.setOwningPlayer(owner);
            } catch (Throwable ignored) {}
            try {
                com.destroystokyo.paper.profile.PlayerProfile profile = owner.getPlayerProfile();
                if (profile.hasTextures()) {
                    skull.setPlayerProfile(profile);
                }
            } catch (Throwable ignored) {}
            head.setItemMeta(skull);
        }
        return head;
    }

    private static void equipStand(ArmorStand stand, CloneConfig config) {
        // 1. Helmet or Player Skin Head
        ItemStack playerHelm = config.owner.getInventory().getHelmet();
        if (!config.forceSkinHead && config.helmetOverride != null) {
            stand.setItem(EquipmentSlot.HEAD, config.helmetOverride.clone());
        } else if (!config.forceSkinHead && playerHelm != null && playerHelm.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.HEAD, playerHelm.clone());
        } else {
            stand.setItem(EquipmentSlot.HEAD, createPlayerSkinHead(config.owner));
        }

        // 2. Chestplate / Body
        ItemStack cp = (config.chestplateOverride != null) ? config.chestplateOverride : config.owner.getInventory().getChestplate();
        if (cp != null && cp.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.CHEST, cp.clone());
        } else {
            // Visible clothing tunic fallback so body is not invisible empty air
            ItemStack tunic = new ItemStack(Material.LEATHER_CHESTPLATE);
            if (tunic.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(45, 52, 71));
                tunic.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.CHEST, tunic);
        }

        // 3. Leggings
        ItemStack leg = (config.leggingsOverride != null) ? config.leggingsOverride : config.owner.getInventory().getLeggings();
        if (leg != null && leg.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.LEGS, leg.clone());
        } else {
            ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
            if (pants.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(30, 35, 45));
                pants.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.LEGS, pants);
        }

        // 4. Boots
        ItemStack boot = (config.bootsOverride != null) ? config.bootsOverride : config.owner.getInventory().getBoots();
        if (boot != null && boot.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.FEET, boot.clone());
        } else {
            ItemStack leatherBoots = new ItemStack(Material.LEATHER_BOOTS);
            if (leatherBoots.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(20, 20, 25));
                leatherBoots.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.FEET, leatherBoots);
        }

        // 5. Main Hand & Off Hand
        if (config.hasArms) {
            ItemStack mh = (config.mainHandOverride != null) ? config.mainHandOverride : config.owner.getInventory().getItemInMainHand();
            if (mh != null && mh.getType() != Material.AIR) stand.setItem(EquipmentSlot.HAND, mh.clone());

            ItemStack oh = (config.offHandOverride != null) ? config.offHandOverride : config.owner.getInventory().getItemInOffHand();
            if (oh != null && oh.getType() != Material.AIR) stand.setItem(EquipmentSlot.OFF_HAND, oh.clone());
        }
    }

    private static void equipLivingEntity(LivingEntity entity, CloneConfig config) {
        ItemStack playerHelm = config.owner.getInventory().getHelmet();
        if (!config.forceSkinHead && config.helmetOverride != null) {
            entity.getEquipment().setHelmet(config.helmetOverride.clone());
        } else if (!config.forceSkinHead && playerHelm != null && playerHelm.getType() != Material.AIR) {
            entity.getEquipment().setHelmet(playerHelm.clone());
        } else {
            entity.getEquipment().setHelmet(createPlayerSkinHead(config.owner));
        }

        ItemStack cp = (config.chestplateOverride != null) ? config.chestplateOverride : config.owner.getInventory().getChestplate();
        if (cp != null && cp.getType() != Material.AIR) {
            entity.getEquipment().setChestplate(cp.clone());
        } else {
            ItemStack tunic = new ItemStack(Material.LEATHER_CHESTPLATE);
            if (tunic.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(45, 52, 71));
                tunic.setItemMeta(lam);
            }
            entity.getEquipment().setChestplate(tunic);
        }

        ItemStack leg = (config.leggingsOverride != null) ? config.leggingsOverride : config.owner.getInventory().getLeggings();
        if (leg != null && leg.getType() != Material.AIR) {
            entity.getEquipment().setLeggings(leg.clone());
        } else {
            ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
            if (pants.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(30, 35, 45));
                pants.setItemMeta(lam);
            }
            entity.getEquipment().setLeggings(pants);
        }

        ItemStack boot = (config.bootsOverride != null) ? config.bootsOverride : config.owner.getInventory().getBoots();
        if (boot != null && boot.getType() != Material.AIR) {
            entity.getEquipment().setBoots(boot.clone());
        } else {
            ItemStack leatherBoots = new ItemStack(Material.LEATHER_BOOTS);
            if (leatherBoots.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(20, 20, 25));
                leatherBoots.setItemMeta(lam);
            }
            entity.getEquipment().setBoots(leatherBoots);
        }

        ItemStack mh = (config.mainHandOverride != null) ? config.mainHandOverride : config.owner.getInventory().getItemInMainHand();
        if (mh != null && mh.getType() != Material.AIR) entity.getEquipment().setItemInMainHand(mh.clone());

        ItemStack oh = (config.offHandOverride != null) ? config.offHandOverride : config.owner.getInventory().getItemInOffHand();
        if (oh != null && oh.getType() != Material.AIR) entity.getEquipment().setItemInOffHand(oh.clone());
    }

    private static LivingEntity findNearestEnemy(Entity origin, CloneConfig config) {
        LivingEntity enemy = null;
        double nearest = config.followRange;
        for (LivingEntity nearby : origin.getWorld().getNearbyLivingEntities(origin.getLocation(), config.followRange)) {
            if (nearby.equals(config.owner) || nearby.equals(origin)) continue;
            if (config.tagKey != null && nearby.getPersistentDataContainer().has(config.tagKey, PersistentDataType.STRING)) continue;
            if (nearby instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR)) continue;
            double d = nearby.getLocation().distance(origin.getLocation());
            if (d < nearest) {
                nearest = d;
                enemy = nearby;
            }
        }
        return enemy;
    }
}
