package com.churchsmp.util;

import com.churchsmp.ChurchSMP;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages ProtocolLib-based doppelganger NPCs that look exactly like real players.
 * Each doppelganger = fake player visual (packets only) + invisible ArmorStand (hitbox).
 *
 * Clones start at 4, multiply by 3 on hit, capped at 27 (3^3).
 * All clones mirror the owner's movement in real-time.
 */
public class DoppelgangerManager {

    private final ChurchSMP plugin;
    private ProtocolManager protocolManager;

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE / 2);
    private static final Random RANDOM = new Random();

    private final Map<UUID, DoppelgangerSession> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, Doppelganger> hitboxMap = new ConcurrentHashMap<>(); // ArmorStand entity ID → clone

    private final NamespacedKey doppelgangerKey;


    // ─────────────────────────────────── inner types ───────────────────────────

    public static class Doppelganger {
        public final int fakeEntityId;
        public final UUID fakeUUID;
        public final WrappedGameProfile profile;
        public Location currentLocation;
        public double mirrorAngle; // Angle of the reflection plane
        public double baseDist;    // Distance from domain origin
        public double phase;       // Individual sway phase
        public double swaySpeed;   // Feint / strafe frequency
        public ArmorStand hitbox;
        public UUID ownerUUID;

        Doppelganger(int id, UUID uuid, WrappedGameProfile p, Location loc, double angle, double dist, UUID owner) {
            this.fakeEntityId = id;
            this.fakeUUID = uuid;
            this.profile = p;
            this.currentLocation = loc.clone();
            this.mirrorAngle = angle;
            this.baseDist = dist;
            this.phase = RANDOM.nextDouble() * Math.PI * 2;
            this.swaySpeed = 0.08 + RANDOM.nextDouble() * 0.08;
            this.ownerUUID = owner;
        }
    }

    public static class DoppelgangerSession {
        public final UUID ownerUUID;
        public final Location origin;
        public final List<Doppelganger> clones = new ArrayList<>();
        public static final int MAX = 27; // 3^3
        public BukkitRunnable mirrorTask;

        DoppelgangerSession(UUID owner, Location origin) {
            this.ownerUUID = owner;
            this.origin = origin.clone();
        }
    }

    // ─────────────────────────────────── constructor ──────────────────────────

    public DoppelgangerManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.doppelgangerKey = new NamespacedKey(plugin, "doppelganger_hitbox");
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                this.protocolManager = ProtocolLibrary.getProtocolManager();
                plugin.getLogger().info("[Doppelganger] ProtocolLib detected — true doppelgangers enabled.");
            } else {
                plugin.getLogger().info("[Doppelganger] ProtocolLib not present — physical optical puppets enabled.");
            }
        } catch (Throwable t) {
            plugin.getLogger().info("[Doppelganger] Physical optical puppets enabled.");
        }
    }

    public boolean isEnabled() { return true; }
    public NamespacedKey getDoppelgangerKey() { return doppelgangerKey; }

    // ─────────────────────────────────── public API ──────────────────────────

    /** Spawn initial 4 clones scattered in 4 quadrants with natural angular jitter. */
    public DoppelgangerSession spawnInitial(Player owner) {
        cleanup(owner.getUniqueId());

        DoppelgangerSession session = new DoppelgangerSession(owner.getUniqueId(), owner.getLocation());
        sessions.put(owner.getUniqueId(), session);

        // 4 quadrants with chaotic angular jitter so they scatter naturally
        double[] baseAngles = {
            Math.toRadians(45  + (RANDOM.nextDouble() - 0.5) * 40),
            Math.toRadians(135 + (RANDOM.nextDouble() - 0.5) * 40),
            Math.toRadians(225 + (RANDOM.nextDouble() - 0.5) * 40),
            Math.toRadians(315 + (RANDOM.nextDouble() - 0.5) * 40)
        };

        for (double ang : baseAngles) {
            double dist = 3.2 + RANDOM.nextDouble() * 2.5; // Staggered distances
            spawnOne(owner, ang, dist, session);
        }

        startMirrorTask(owner, session);
        return session;
    }

    /** Spawn a single clone at a scattered angle and distance. Returns null if at cap. */
    public Doppelganger spawnOne(Player owner, double angle, double dist, DoppelgangerSession session) {
        if (session.clones.size() >= DoppelgangerSession.MAX) return null;

        Location loc = session.origin.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

        int fakeId = ENTITY_ID_COUNTER.getAndIncrement();
        UUID fakeUuid = UUID.randomUUID();

        // Build profile with owner's skin (if ProtocolLib available)
        WrappedGameProfile fakeProfile = null;
        try {
            if (protocolManager != null) {
                WrappedGameProfile ownerProfile = WrappedGameProfile.fromPlayer(owner);
                fakeProfile = new WrappedGameProfile(fakeUuid, owner.getName());
                fakeProfile.getProperties().putAll(ownerProfile.getProperties());
            }
        } catch (Throwable ignored) {}

        Doppelganger dg = new Doppelganger(fakeId, fakeUuid, fakeProfile, loc, angle, dist, owner.getUniqueId());

        // Visible puppet ArmorStand with owner's exact player skin head, gear, and weapon
        ArmorStand as = owner.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setVisible(false); // Frame hidden, equipped items & head are 100% visible!
            a.setArms(true);
            a.setBasePlate(false);
            a.setGravity(false);
            a.setInvulnerable(false);
            a.setSmall(false);
            a.setMarker(false);
            a.setCollidable(false);
            a.setCustomNameVisible(false);
            a.setSilent(true);
            a.getPersistentDataContainer().set(doppelgangerKey, PersistentDataType.STRING,
                    owner.getUniqueId().toString());
            var attr = a.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(1.0);

            // Equips player's skin head, armor, and weapon
            equipCloneStand(a, owner);
        });
        dg.hitbox = as;
        hitboxMap.put(as.getEntityId(), dg);
        session.clones.add(dg);

        // Spawn visual burst
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1.0, 0), 8, 0.25, 0.3, 0.25, 0.03);

        // Send visual packets to all viewers (if ProtocolLib is active)
        if (protocolManager != null) {
            for (Player v : Bukkit.getOnlinePlayers()) {
                sendSpawn(v, dg, owner);
            }
        }
        return dg;
    }

    /** Called when a doppelganger hitbox is attacked. Returns owner UUID (or null). */
    public UUID onHit(ArmorStand hitbox, LivingEntity attacker) {
        Doppelganger dg = hitboxMap.get(hitbox.getEntityId());
        if (dg == null) return null;

        UUID ownerUUID = dg.ownerUUID;
        DoppelgangerSession session = sessions.get(ownerUUID);
        if (session == null) return ownerUUID;

        Location shatterLoc = dg.currentLocation.clone();

        // Destroy visual
        for (Player v : Bukkit.getOnlinePlayers()) sendDestroy(v, dg);
        hitboxMap.remove(hitbox.getEntityId());
        if (dg.hitbox != null && dg.hitbox.isValid()) dg.hitbox.remove();
        session.clones.remove(dg);

        // Shatter FX
        shatterLoc.getWorld().playSound(shatterLoc, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
        shatterLoc.getWorld().playSound(shatterLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 1.2f);
        shatterLoc.getWorld().spawnParticle(Particle.FLASH, shatterLoc.clone().add(0, 1, 0), 1);
        shatterLoc.getWorld().spawnParticle(Particle.DUST, shatterLoc.clone().add(0, 1, 0), 12,
                0.25, 0.3, 0.25, 0, new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.3f));
        shatterLoc.getWorld().spawnParticle(Particle.SOUL, shatterLoc.clone().add(0, 1.2, 0), 5,
                0.2, 0.25, 0.2, 0.03);

        // DISORIENTATION SHUFFLE: Surviving clones scatter & shift positions slightly!
        for (Doppelganger surviving : session.clones) {
            surviving.mirrorAngle += (RANDOM.nextDouble() - 0.5) * 0.55; // Scatter angular shift
            surviving.baseDist = Math.max(2.5, Math.min(8.5, surviving.baseDist + (RANDOM.nextDouble() - 0.5) * 1.5));
            surviving.phase += RANDOM.nextDouble() * 2.0;
            // Quick purple puff at surviving clone location
            surviving.currentLocation.getWorld().spawnParticle(Particle.PORTAL,
                    surviving.currentLocation.clone().add(0, 1, 0), 5, 0.2, 0.3, 0.2, 0.05);
        }

        // Multiply: spawn 3 more scattered around the arena if under 27 cap (3^3)
        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null && owner.isOnline() && session.clones.size() < DoppelgangerSession.MAX) {
            int toSpawn = Math.min(3, DoppelgangerSession.MAX - session.clones.size());
            for (int i = 0; i < toSpawn; i++) {
                double scatterAngle = RANDOM.nextDouble() * Math.PI * 2; // Any angle 360°
                double scatterDist = 2.5 + RANDOM.nextDouble() * 5.8;   // Clustered across the domain
                spawnOne(owner, scatterAngle, scatterDist, session);
            }
        }
        return ownerUUID;
    }

    public boolean isDoppelganger(Entity e) {
        return e instanceof ArmorStand as
                && as.getPersistentDataContainer().has(doppelgangerKey, PersistentDataType.STRING);
    }

    public boolean hasActive(UUID ownerUUID) {
        DoppelgangerSession s = sessions.get(ownerUUID);
        return s != null && !s.clones.isEmpty();
    }

    public int getCount(UUID ownerUUID) {
        DoppelgangerSession s = sessions.get(ownerUUID);
        return s != null ? s.clones.size() : 0;
    }

    public void cleanup(UUID ownerUUID) {
        DoppelgangerSession session = sessions.remove(ownerUUID);
        if (session == null) return;
        if (session.mirrorTask != null) session.mirrorTask.cancel();
        for (Doppelganger dg : session.clones) {
            for (Player v : Bukkit.getOnlinePlayers()) sendDestroy(v, dg);
            hitboxMap.remove(dg.hitbox != null ? dg.hitbox.getEntityId() : -1);
            if (dg.hitbox != null && dg.hitbox.isValid()) dg.hitbox.remove();
        }
        session.clones.clear();
    }

    // ─────────────────────────────────── mirror task ─────────────────────────

    private void startMirrorTask(Player owner, DoppelgangerSession session) {
        session.mirrorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!owner.isOnline() || !sessions.containsKey(owner.getUniqueId())
                        || session.clones.isEmpty()) {
                    cleanup(owner.getUniqueId());
                    cancel();
                    return;
                }

                Location ownerLoc = owner.getLocation();
                double relX = ownerLoc.getX() - session.origin.getX();
                double relZ = ownerLoc.getZ() - session.origin.getZ();
                Vector dir = ownerLoc.getDirection();
                double dirX = dir.getX();
                double dirZ = dir.getZ();
                long tick = Bukkit.getCurrentTick();

                for (Doppelganger dg : new ArrayList<>(session.clones)) {
                    // Normal unit vector for this clone's mirror facet
                    double cosA = Math.cos(dg.mirrorAngle);
                    double sinA = Math.sin(dg.mirrorAngle);

                    // Decompose displacement into parallel (normal) and perpendicular (tangent)
                    double rParallel = relX * cosA + relZ * sinA;
                    double rTangent  = -relX * sinA + relZ * cosA;

                    // Subtle micro-sway / feinting to mimic human PvP strafing
                    double sway = Math.sin(tick * dg.swaySpeed + dg.phase) * 0.45;
                    rTangent += sway;

                    // Optical reflection: normal reflects (D - rParallel), tangent persists
                    double refNorm = dg.baseDist - rParallel;
                    double refX = refNorm * cosA - rTangent * sinA;
                    double refZ = refNorm * sinA + rTangent * cosA;

                    Location newLoc = session.origin.clone().add(refX, 0, refZ);
                    newLoc.setY(ownerLoc.getY());

                    // Reflected facing direction: d' = d - 2(d . n)n
                    double dot = dirX * cosA + dirZ * sinA;
                    double refDirX = dirX - 2.0 * dot * cosA;
                    double refDirZ = dirZ - 2.0 * dot * sinA;
                    Vector reflectedDir = new Vector(refDirX, dir.getY(), refDirZ);

                    if (reflectedDir.lengthSquared() > 0.001) {
                        newLoc.setDirection(reflectedDir);
                    }
                    dg.currentLocation = newLoc;

                    // Move and animate visible puppet
                    if (dg.hitbox != null && dg.hitbox.isValid()) {
                        dg.hitbox.teleport(newLoc);

                        // Live walking limb swing animation
                        double speed = owner.getVelocity().lengthSquared();
                        if (speed > 0.003) {
                            double swing = Math.sin(tick * 0.45 + dg.phase) * 0.55;
                            dg.hitbox.setRightLegPose(new EulerAngle(-swing, 0, 0));
                            dg.hitbox.setLeftLegPose(new EulerAngle(swing, 0, 0));
                            dg.hitbox.setRightArmPose(new EulerAngle(swing * 0.7 - 0.2, 0, Math.toRadians(8)));
                            dg.hitbox.setLeftArmPose(new EulerAngle(-swing * 0.7, 0, Math.toRadians(-8)));
                        } else {
                            dg.hitbox.setRightLegPose(new EulerAngle(0, 0, 0));
                            dg.hitbox.setLeftLegPose(new EulerAngle(0, 0, 0));
                            dg.hitbox.setRightArmPose(new EulerAngle(Math.toRadians(12), 0, Math.toRadians(6)));
                            dg.hitbox.setLeftArmPose(new EulerAngle(Math.toRadians(12), 0, Math.toRadians(-6)));
                        }

                        // Subtle mirror illusion particles
                        if (tick % 6 == 0) {
                            newLoc.getWorld().spawnParticle(Particle.PORTAL, newLoc.clone().add(0, 0.9, 0), 2, 0.2, 0.3, 0.2, 0.02);
                        }
                    }

                    // Move fake player visual for nearby viewers (if ProtocolLib is active)
                    if (protocolManager != null) {
                        for (Player v : Bukkit.getOnlinePlayers()) {
                            if (v.getWorld().equals(ownerLoc.getWorld())
                                    && v.getLocation().distanceSquared(ownerLoc) < 4096) {
                                sendTeleport(v, dg);
                            }
                        }
                    }
                }
            }
        };
        session.mirrorTask.runTaskTimer(plugin, 0L, 1L);
    }

    // ─────────────────────────────── equipment helper ────────────────────────

    private void equipCloneStand(ArmorStand stand, Player owner) {
        // 1. Head / Helmet
        ItemStack helm = owner.getInventory().getHelmet();
        if (helm != null && helm.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.HEAD, helm.clone());
        } else {
            stand.setItem(EquipmentSlot.HEAD, createPlayerSkinHead(owner));
        }

        // 2. Chestplate
        ItemStack cp = owner.getInventory().getChestplate();
        if (cp != null && cp.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.CHEST, cp.clone());
        } else {
            ItemStack tunic = new ItemStack(Material.LEATHER_CHESTPLATE);
            if (tunic.getItemMeta() instanceof LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(45, 52, 71));
                tunic.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.CHEST, tunic);
        }

        // 3. Leggings
        ItemStack leg = owner.getInventory().getLeggings();
        if (leg != null && leg.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.LEGS, leg.clone());
        } else {
            ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
            if (pants.getItemMeta() instanceof LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(30, 35, 48));
                pants.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.LEGS, pants);
        }

        // 4. Boots
        ItemStack boots = owner.getInventory().getBoots();
        if (boots != null && boots.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.FEET, boots.clone());
        } else {
            ItemStack bootItem = new ItemStack(Material.LEATHER_BOOTS);
            if (bootItem.getItemMeta() instanceof LeatherArmorMeta lam) {
                lam.setColor(Color.fromRGB(20, 24, 33));
                bootItem.setItemMeta(lam);
            }
            stand.setItem(EquipmentSlot.FEET, bootItem);
        }

        // 5. Main Hand & Off Hand
        ItemStack mainHand = owner.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.HAND, mainHand.clone());
        }
        ItemStack offHand = owner.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            stand.setItem(EquipmentSlot.OFF_HAND, offHand.clone());
        }

        // Natural posture
        stand.setRightArmPose(new EulerAngle(Math.toRadians(12), 0, Math.toRadians(6)));
        stand.setLeftArmPose(new EulerAngle(Math.toRadians(12), 0, Math.toRadians(-6)));
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

    // ─────────────────────────────── packet helpers ──────────────────────────

    private void sendSpawn(Player viewer, Doppelganger dg, Player owner) {
        if (protocolManager == null) return;
        try {
            // Modern ProtocolLib 5.3+ packet support (fail-safe)
            PacketType infoType = null;
            try {
                infoType = PacketType.Play.Server.getInstance().values().stream()
                        .filter(pt -> pt.name().equals("PLAYER_INFO_UPDATE"))
                        .findFirst().orElse(null);
            } catch (Throwable ignored) {}

            if (infoType != null) {
                PacketContainer info = protocolManager.createPacket(infoType);
                info.getPlayerInfoActions().write(0,
                        EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                                   EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
                PlayerInfoData pid = new PlayerInfoData(
                        dg.fakeUUID, 0, false,
                        EnumWrappers.NativeGameMode.SURVIVAL,
                        dg.profile, null
                );
                info.getPlayerInfoDataLists().write(0, List.of(pid));
                protocolManager.sendServerPacket(viewer, info);
            }

            // SPAWN_ENTITY (player type)
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, dg.fakeEntityId);
            spawn.getUUIDs().write(0, dg.fakeUUID);
            spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
            spawn.getDoubles()
                    .write(0, dg.currentLocation.getX())
                    .write(1, dg.currentLocation.getY())
                    .write(2, dg.currentLocation.getZ());
            spawn.getBytes()
                    .write(0, angleByte(dg.currentLocation.getPitch()))
                    .write(1, angleByte(dg.currentLocation.getYaw()));
            protocolManager.sendServerPacket(viewer, spawn);

            // ENTITY_EQUIPMENT — mirror owner's gear
            try {
                PacketContainer equip = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                equip.getIntegers().write(0, dg.fakeEntityId);
                List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack>> gear = new ArrayList<>();
                gear.add(pair(EnumWrappers.ItemSlot.MAINHAND, owner.getInventory().getItemInMainHand()));
                gear.add(pair(EnumWrappers.ItemSlot.HEAD,     owner.getInventory().getHelmet()));
                gear.add(pair(EnumWrappers.ItemSlot.CHEST,    owner.getInventory().getChestplate()));
                gear.add(pair(EnumWrappers.ItemSlot.LEGS,     owner.getInventory().getLeggings()));
                gear.add(pair(EnumWrappers.ItemSlot.FEET,     owner.getInventory().getBoots()));
                equip.getSlotStackPairLists().write(0, gear);
                protocolManager.sendServerPacket(viewer, equip);
            } catch (Throwable ignored) {}

            // Head rotation
            try {
                PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                head.getIntegers().write(0, dg.fakeEntityId);
                head.getBytes().write(0, angleByte(dg.currentLocation.getYaw()));
                protocolManager.sendServerPacket(viewer, head);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // Physical puppet is already active and visible to all
        }
    }

    private void sendTeleport(Player viewer, Doppelganger dg) {
        if (protocolManager == null) return;
        try {
            PacketContainer tp = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, dg.fakeEntityId);
            tp.getDoubles()
                    .write(0, dg.currentLocation.getX())
                    .write(1, dg.currentLocation.getY())
                    .write(2, dg.currentLocation.getZ());
            tp.getBytes()
                    .write(0, angleByte(dg.currentLocation.getYaw()))
                    .write(1, angleByte(dg.currentLocation.getPitch()));
            tp.getBooleans().write(0, false);
            protocolManager.sendServerPacket(viewer, tp);

            PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, dg.fakeEntityId);
            head.getBytes().write(0, angleByte(dg.currentLocation.getYaw()));
            protocolManager.sendServerPacket(viewer, head);
        } catch (Throwable ignored) {}
    }

    private void sendDestroy(Player viewer, Doppelganger dg) {
        if (protocolManager == null) return;
        try {
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(dg.fakeEntityId));
            protocolManager.sendServerPacket(viewer, destroy);

            PacketContainer rm = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            rm.getUUIDLists().write(0, List.of(dg.fakeUUID));
            protocolManager.sendServerPacket(viewer, rm);
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────── util ────────────────────────────────

    private static byte angleByte(float angle) {
        return (byte) (angle * 256.0F / 360.0F);
    }

    private static com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack>
    pair(EnumWrappers.ItemSlot slot, org.bukkit.inventory.ItemStack item) {
        return new com.comphenix.protocol.wrappers.Pair<>(slot, item);
    }
}
