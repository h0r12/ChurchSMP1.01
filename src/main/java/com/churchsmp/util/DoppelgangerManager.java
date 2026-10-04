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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
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

    public enum Axis {
        NORTH, SOUTH, EAST, WEST
    }

    // ─────────────────────────────────── inner types ───────────────────────────

    public static class Doppelganger {
        public final int fakeEntityId;
        public final UUID fakeUUID;
        public final WrappedGameProfile profile;
        public Location currentLocation;
        public Axis axis;
        public double baseDist;
        public ArmorStand hitbox;
        public UUID ownerUUID;

        Doppelganger(int id, UUID uuid, WrappedGameProfile p, Location loc, Axis axis, double dist, UUID owner) {
            this.fakeEntityId = id;
            this.fakeUUID = uuid;
            this.profile = p;
            this.currentLocation = loc.clone();
            this.axis = axis;
            this.baseDist = dist;
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
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            plugin.getLogger().info("[Doppelganger] ProtocolLib detected — true doppelgangers enabled.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[Doppelganger] ProtocolLib not found — doppelgangers disabled.");
        }
    }

    public boolean isEnabled() { return protocolManager != null; }
    public NamespacedKey getDoppelgangerKey() { return doppelgangerKey; }

    // ─────────────────────────────────── public API ──────────────────────────

    /** Spawn initial 4 clones in 4 cardinal mirror axes. */
    public DoppelgangerSession spawnInitial(Player owner) {
        cleanup(owner.getUniqueId());

        DoppelgangerSession session = new DoppelgangerSession(owner.getUniqueId(), owner.getLocation());
        sessions.put(owner.getUniqueId(), session);

        spawnOne(owner, Axis.NORTH, 3.5, session);
        spawnOne(owner, Axis.EAST, 3.5, session);
        spawnOne(owner, Axis.SOUTH, 3.5, session);
        spawnOne(owner, Axis.WEST, 3.5, session);

        startMirrorTask(owner, session);
        return session;
    }

    /** Spawn a single clone along an axis at base distance. Returns null if at cap. */
    public Doppelganger spawnOne(Player owner, Axis axis, double dist, DoppelgangerSession session) {
        if (session.clones.size() >= DoppelgangerSession.MAX) return null;

        Location loc = session.origin.clone();
        switch (axis) {
            case NORTH -> loc.add(0, 0, -dist);
            case SOUTH -> loc.add(0, 0, dist);
            case EAST  -> loc.add(dist, 0, 0);
            case WEST  -> loc.add(-dist, 0, 0);
        }

        int fakeId = ENTITY_ID_COUNTER.getAndIncrement();
        UUID fakeUuid = UUID.randomUUID();

        // Build profile with owner's skin
        WrappedGameProfile ownerProfile = WrappedGameProfile.fromPlayer(owner);
        WrappedGameProfile fakeProfile = new WrappedGameProfile(fakeUuid, "");
        fakeProfile.getProperties().putAll(ownerProfile.getProperties());

        Doppelganger dg = new Doppelganger(fakeId, fakeUuid, fakeProfile, loc, axis, dist, owner.getUniqueId());

        // Invisible ArmorStand as hit-box
        ArmorStand as = owner.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setInvulnerable(false);
            a.setSmall(false);
            a.setMarker(false);
            a.setCollidable(false);
            a.setCustomNameVisible(false);
            a.setSilent(true);
            a.getPersistentDataContainer().set(doppelgangerKey, PersistentDataType.STRING,
                    owner.getUniqueId().toString());
            var attr = a.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(1.0);
        });
        dg.hitbox = as;
        hitboxMap.put(as.getEntityId(), dg);
        session.clones.add(dg);

        // Send visual packets to all viewers
        for (Player v : Bukkit.getOnlinePlayers()) {
            sendSpawn(v, dg, owner);
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
        shatterLoc.getWorld().spawnParticle(Particle.DUST, shatterLoc.clone().add(0, 1, 0), 30,
                0.4, 0.5, 0.4, 0, new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.5f));
        shatterLoc.getWorld().spawnParticle(Particle.SOUL, shatterLoc.clone().add(0, 1.2, 0), 12,
                0.3, 0.4, 0.3, 0.06);

        // Multiply: spawn 3 more if under 27 cap (3^3), strictly along the 4 mirror axes
        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null && owner.isOnline() && session.clones.size() < DoppelgangerSession.MAX) {
            int toSpawn = Math.min(3, DoppelgangerSession.MAX - session.clones.size());
            Axis[] axes = Axis.values();
            for (int i = 0; i < toSpawn; i++) {
                Axis axis = axes[RANDOM.nextInt(axes.length)];
                double dist = 2.5 + (RANDOM.nextInt(5) + 1) * 1.6;
                spawnOne(owner, axis, dist, session);
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

                for (Doppelganger dg : new ArrayList<>(session.clones)) {
                    Location newLoc = session.origin.clone();
                    Vector reflectedDir;

                    switch (dg.axis) {
                        case NORTH -> {
                            // Mirror across North plane (reflects Z)
                            newLoc.add(relX, 0, -dg.baseDist - relZ);
                            reflectedDir = new Vector(dir.getX(), dir.getY(), -dir.getZ());
                        }
                        case SOUTH -> {
                            // Mirror across South plane (reflects Z)
                            newLoc.add(relX, 0, dg.baseDist - relZ);
                            reflectedDir = new Vector(dir.getX(), dir.getY(), -dir.getZ());
                        }
                        case EAST -> {
                            // Mirror across East plane (reflects X)
                            newLoc.add(dg.baseDist - relX, 0, relZ);
                            reflectedDir = new Vector(-dir.getX(), dir.getY(), dir.getZ());
                        }
                        case WEST -> {
                            // Mirror across West plane (reflects X)
                            newLoc.add(-dg.baseDist - relX, 0, relZ);
                            reflectedDir = new Vector(-dir.getX(), dir.getY(), dir.getZ());
                        }
                        default -> {
                            newLoc.add(relX, 0, relZ);
                            reflectedDir = dir;
                        }
                    }

                    newLoc.setY(ownerLoc.getY());
                    if (reflectedDir.lengthSquared() > 0.001) {
                        newLoc.setDirection(reflectedDir);
                    }
                    dg.currentLocation = newLoc;

                    // Move invisible hitbox
                    if (dg.hitbox != null && dg.hitbox.isValid()) dg.hitbox.teleport(newLoc);

                    // Move fake player visual for nearby viewers
                    for (Player v : Bukkit.getOnlinePlayers()) {
                        if (v.getWorld().equals(ownerLoc.getWorld())
                                && v.getLocation().distanceSquared(ownerLoc) < 4096) {
                            sendTeleport(v, dg);
                        }
                    }
                }
            }
        };
        session.mirrorTask.runTaskTimer(plugin, 0L, 1L);
    }

    // ─────────────────────────────── packet helpers ──────────────────────────

    private void sendSpawn(Player viewer, Doppelganger dg, Player owner) {
        try {
            // 1. PLAYER_INFO_UPDATE — add fake profile (with skin) to tab
            PacketContainer info = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_UPDATE);
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

            // 2. SPAWN_ENTITY (player type)
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

            // 3. ENTITY_METADATA — show all skin layers
            PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, dg.fakeEntityId);
            WrappedDataWatcher w = new WrappedDataWatcher();
            WrappedDataWatcher.Serializer byteSer = WrappedDataWatcher.Registry.get(Byte.class);
            w.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(17, byteSer), (byte) 0x7F);
            meta.getWatchableCollectionModifier().write(0, w.getWatchableObjects());
            protocolManager.sendServerPacket(viewer, meta);

            // 4. ENTITY_EQUIPMENT — mirror owner's gear
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

            // 5. Head rotation
            PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, dg.fakeEntityId);
            head.getBytes().write(0, angleByte(dg.currentLocation.getYaw()));
            protocolManager.sendServerPacket(viewer, head);

            // 6. Remove from tab after 2 ticks (skin loads, name disappears)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    PacketContainer rm = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                    rm.getUUIDLists().write(0, List.of(dg.fakeUUID));
                    protocolManager.sendServerPacket(viewer, rm);
                } catch (Throwable ignored) {}
            }, 2L);

        } catch (Throwable t) {
            plugin.getLogger().warning("[Doppelganger] Spawn packet error: " + t.getMessage());
        }
    }

    private void sendTeleport(Player viewer, Doppelganger dg) {
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
