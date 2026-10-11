package com.churchsmp.event;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Genesis Start Event (/churchadmin start):
 * 1. World Border shrinks to 25 blocks at spawn (0, 0).
 * 2. Everyone online must type /church ready (live action bar counter).
 * 3. Unstable white tree-vein soul particles descend onto players and slowly float to (0, 0).
 * 4. Blinding flash at (0, 0) -> expands into spinning star cycling through 7 Sins colors.
 * 5. 7 spinning Sin Souls manifest within 20 blocks of spawn for players to choose.
 * 6. Choosing a sin grants Invisibility and confirms in chat.
 * 7. When all choose: Star morphs into black/gray void orb + beacon beam for 4 seconds.
 * 8. Lightning strike -> World border restored -> All players launched to Y:200 with temporary Elytra until landing.
 */
public class StartEventManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public enum EventState {
        IDLE,
        READY_CHECK,
        SOUL_EXTRACTION,
        STAR_CYCLE,
        SIN_CHOICE,
        VOID_CLIMAX,
        LAUNCH_GLIDE
    }

    private EventState state = EventState.IDLE;
    private World eventWorld = null;
    private final Set<UUID> readyPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> attunedPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, ItemStack> cachedChestplates = new ConcurrentHashMap<>();
    private final Set<UUID> glidingPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<SinGemType, Location> sinSoulLocations = new ConcurrentHashMap<>();
    private final List<ItemDisplay> activeSoulDisplays = new ArrayList<>();
    private final List<TextDisplay> activeTextDisplays = new ArrayList<>();
    private final List<Interaction> activeInteractions = new ArrayList<>();

    private double originalBorderSize = 10000;
    private Location originalBorderCenter = null;
    private final Map<UUID, SinGemType> playerChosenSins = new ConcurrentHashMap<>();
    private BukkitTask mainLoopTask = null;
    private BukkitTask autoTimeoutTask = null;
    private BukkitTask readyAmbientTask = null;
    private BukkitTask glideTrailTask = null;
    private BukkitTask continuousSoundscapeTask = null;
    private BossBar ceremonyBossBar = null;

    public StartEventManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    public EventState getState() {
        return state;
    }

    public boolean isEventActive() {
        return state != EventState.IDLE;
    }

    /**
     * Signature retro-fantasy crystal sound designer:
     * High-pitched crystal rings layered with cascading experience orb chimes,
     * note-block chime synth tones, and a drawn-out sustained bell resonance with clean long decay.
     */
    public void playRetroCrystalChime(Location loc, float basePitch, float volume) {
        World world = loc.getWorld();
        if (world == null) return;

        // 1. Instant transient strike: Experience orb chime + Note block chime + Amethyst ring
        world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, Math.min(2.0f, basePitch * 1.0f));
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, volume * 0.9f, Math.min(2.0f, basePitch * 1.0f));
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, Math.min(2.0f, basePitch * 1.25f));

        // 2. Lingering crystal body with drawn-out sustain and clean long decay
        world.playSound(loc, Sound.BLOCK_BELL_RESONATE, volume * 0.85f, Math.min(2.0f, basePitch * 0.95f));
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume * 0.7f, Math.min(2.0f, basePitch * 0.85f));

        // 3. Cascading retro micro-delays (2 ticks and 4 ticks) for classic arcade/JRPG sparkling arpeggios
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isEventActive() && loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume * 0.9f, Math.min(2.0f, basePitch * 1.33f));
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, volume * 0.8f, Math.min(2.0f, basePitch * 1.2f));
            }
        }, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isEventActive() && loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume * 0.8f, Math.min(2.0f, basePitch * 1.66f));
                loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume * 0.85f, Math.min(2.0f, basePitch * 1.5f));
            }
        }, 4L);
    }

    /**
     * Continuous background soundscape engine providing drawn-out sustain,
     * slow tempo crystal pulses, and gentle retro synth ambiance throughout the entire event.
     */
    private void startContinuousSoundscapeTask(World world, Location center) {
        if (continuousSoundscapeTask != null) continuousSoundscapeTask.cancel();

        this.continuousSoundscapeTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!isEventActive()) {
                    cancel();
                    return;
                }
                ticks++;

                // 1. Slow-tempo deep cosmic hum with drawn-out sustain (every 40 ticks = 2.0s)
                if (ticks % 40 == 0) {
                    world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 0.75f);
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.65f, 0.85f);
                }

                // 2. Slow-tempo, high-pitched crystal ring and exp-orb chime ringing into silence (every 30 ticks = 1.5s)
                if (ticks % 30 == 0) {
                    world.playSound(center, Sound.BLOCK_BELL_RESONATE, 0.55f, 1.35f);
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.75f);
                    world.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
                    world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.25f);
                }

                // 3. Former souls rising from the underworld across the 25-block sanctuary
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double rx = center.getX() + (Math.random() - 0.5) * 44.0;
                        double rz = center.getZ() + (Math.random() - 0.5) * 44.0;
                        int groundY = world.getHighestBlockYAt((int) rx, (int) rz);
                        Location soulRiseLoc = new Location(world, rx, groundY + 0.1, rz);

                        world.spawnParticle(Particle.SOUL, soulRiseLoc, 1, 0.04, 0.08, 0.04, 0.015);
                        if (Math.random() < 0.35) {
                            world.spawnParticle(Particle.SCULK_SOUL, soulRiseLoc, 1, 0.03, 0.06, 0.03, 0.012);
                        }
                        if (Math.random() < 0.22) {
                            world.spawnParticle(Particle.SQUID_INK, soulRiseLoc, 1, 0.02, 0.05, 0.02, 0.01);
                        }
                    }
                }

                // 4. Celestial sky vortex (stars and cosmos spinning overhead)
                if (state == EventState.SOUL_EXTRACTION || state == EventState.STAR_CYCLE || state == EventState.VOID_CLIMAX) {
                    world.setTime(world.getTime() + 120);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Purges existing Ender Dragons in the event world.
     */
    private void purgeEnderDragons(World world) {
        if (world == null) return;
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            dragon.remove();
        }
    }

    /**
     * Suppresses any Ender Dragon spawning during the Genesis event.
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (isEventActive() && event.getEntity() instanceof EnderDragon) {
            event.setCancelled(true);
        }
    }

    /**
     * Initiates the Genesis Start Event via /churchadmin start.
     */
    public boolean startEvent(Player admin) {
        if (state != EventState.IDLE) {
            if (admin != null) {
                admin.sendMessage(miniMessage.deserialize("<red>The Start Event is already in progress!</red>"));
            }
            return false;
        }

        World world = (admin != null) ? admin.getWorld() : Bukkit.getWorlds().get(0);
        this.eventWorld = world;

        // 0. Purge any Ender Dragons present in the event world
        purgeEnderDragons(world);

        WorldBorder border = world.getWorldBorder();
        this.originalBorderSize = border.getSize();
        this.originalBorderCenter = border.getCenter();

        // 1. Shrink World Border to 25 blocks at spawn (0, 0)
        border.setCenter(0.5, 0.5);
        border.setSize(25.0);

        // Shift sky to midnight twilight and tempest
        world.setTime(18000);
        world.setStorm(true);
        world.setThundering(true);

        readyPlayers.clear();
        attunedPlayers.clear();
        playerChosenSins.clear();
        cachedChestplates.clear();
        glidingPlayers.clear();
        sinSoulLocations.clear();
        cleanupDisplays();

        this.state = EventState.READY_CHECK;

        // Apocalyptic Sky Darkening & Atmospheric World Fog
        this.ceremonyBossBar = BossBar.bossBar(
                miniMessage.deserialize("<gradient:#FF4500:#FFD700><bold>✦ THE GENESIS CEREMONY ✦</bold></gradient>"),
                1.0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS,
                Set.of(BossBar.Flag.DARKEN_SCREEN, BossBar.Flag.CREATE_WORLD_FOG)
        );

        // Announcement & Sounds
        Title title = Title.title(
                miniMessage.deserialize("<gradient:#FFD700:#FF4500><bold>✦ THE GENESIS CEREMONY ✦</bold></gradient>"),
                miniMessage.deserialize("<white>World Border set to 25 blocks! Type <yellow><bold>/church ready</bold></yellow>!</white>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        Location spawnLoc = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5);
        startContinuousSoundscapeTask(world, spawnLoc);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLoc);
            p.showBossBar(ceremonyBossBar);
            p.showTitle(title);
            playRetroCrystalChime(p.getLocation(), 1.0f, 1.4f);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.9f);
        }

        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<yellow><bold>✦ GENESIS CEREMONY COMMENCED! ✦</bold></yellow>"));
        Bukkit.broadcast(miniMessage.deserialize("<gray>The realm has converged to a 25-block Sanctuary at Center (0, 0).</gray>"));
        Bukkit.broadcast(miniMessage.deserialize("<white>Every player online must type <yellow><bold><click:run_command:'/church ready'>/church ready</click></bold></yellow> to align their soul!</white>"));
        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));

        startReadyCheckActionBarTask();
        return true;
    }

    /**
     * Marks a player as ready when they type /church ready.
     */
    public boolean markReady(Player player) {
        if (state != EventState.READY_CHECK) {
            player.sendMessage(miniMessage.deserialize("<red>Ready check is not currently active.</red>"));
            return false;
        }

        if (readyPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<yellow>You are already marked as ready!</yellow>"));
            return true;
        }

        readyPlayers.add(player.getUniqueId());
        playRetroCrystalChime(player.getLocation(), 1.3f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.1f, 1.4f);
        player.sendMessage(miniMessage.deserialize("<green>✦ Soul Aligned! Ethereal energy gathers beneath you. ✦</green>"));

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.playSound(other.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                other.playSound(other.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6f, 1.6f);
            }
        }

        updateReadyActionBar();

        // Check if all online players are ready
        int totalOnline = Bukkit.getOnlinePlayers().size();
        if (totalOnline > 0 && readyPlayers.size() >= totalOnline) {
            triggerSoulExtraction();
        }

        return true;
    }

    private void startReadyCheckActionBarTask() {
        if (readyAmbientTask != null) readyAmbientTask.cancel();

        readyAmbientTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (state != EventState.READY_CHECK) {
                    cancel();
                    return;
                }
                ticks++;

                World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);

                // 1. Ethereal Sanctuary Boundary ring (radius 12.5 blocks)
                if (ticks % 3 == 0) {
                    double bRadius = 12.5;
                    for (int d = 0; d < 360; d += 20) {
                        double rad = Math.toRadians(d + (ticks * 2));
                        double bx = 0.5 + Math.cos(rad) * bRadius;
                        double bz = 0.5 + Math.sin(rad) * bRadius;
                        double by = world.getHighestBlockYAt((int) bx, (int) bz) + 0.1;
                        Location bLoc = new Location(world, bx, by, bz);
                        world.spawnParticle(Particle.DUST, bLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(120, 190, 255), 1.1f));
                    }
                }

                // 2. Ready Trance spiritual aura for aligned players
                for (UUID uuid : readyPlayers) {
                    Player rp = Bukkit.getPlayer(uuid);
                    if (rp != null && rp.isOnline()) {
                        double a = Math.toRadians((ticks * 8) % 360);
                        Location pAura = rp.getLocation().add(Math.cos(a) * 0.75, 0.1, Math.sin(a) * 0.75);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, pAura, 1, 0, 0, 0, 0.005);
                        if (ticks % 4 == 0) {
                            world.spawnParticle(Particle.END_ROD, rp.getLocation().add(0, 1.0, 0), 1, 0.2, 0.4, 0.2, 0.01);
                        }
                    }
                }

                // 3. Suspense Heartbeat sound every 30 ticks (1.5 seconds)
                if (ticks % 30 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.7f);
                    }
                }

                // 4. Update action bar every second (20 ticks)
                if (ticks % 20 == 0) {
                    updateReadyActionBar();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateReadyActionBar() {
        int readyCount = readyPlayers.size();
        int total = Bukkit.getOnlinePlayers().size();

        int filled = (total > 0) ? Math.min(10, (readyCount * 10 / total)) : 0;
        int empty = 10 - filled;
        String bar = "█".repeat(filled) + "░".repeat(empty);

        Component ab = miniMessage.deserialize(
                "<gold>✦ [</gold><green>" + bar.substring(0, filled) + "</green><dark_gray>" + bar.substring(filled) + "</dark_gray><gold>] </gold>" +
                "<yellow><bold>" + readyCount + "/" + total + " Souls Aligned</bold></yellow> " +
                "<gray>• Type <click:run_command:'/church ready'><yellow><bold>/church ready</bold></yellow></click></gray>"
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(ab);
        }
    }

    /**
     * Phase 2: Tree-Vein Soul Extraction and guaranteed SLOW drift to (0, 0).
     */
    private void triggerSoulExtraction() {
        this.state = EventState.SOUL_EXTRACTION;
        if (readyAmbientTask != null) {
            readyAmbientTask.cancel();
            readyAmbientTask = null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 75, 0, false, false, false));
            playRetroCrystalChime(p.getLocation(), 0.9f, 1.3f);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.7f);
            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.5f);
            p.sendActionBar(miniMessage.deserialize("<gradient:#FFFFFF:#ADD8E6><bold>✦ THE HEAVENS FRACTURE • SOULS CONVERGING TO (0, 0)... ✦</bold></gradient>"));
        }

        World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
        Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.2, 0.5);

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        final Map<UUID, Location> startSoulLocs = new HashMap<>();
        final Map<UUID, Double> soulAngles = new HashMap<>();

        new BukkitRunnable() {
            int ticks = 0;
            final int veinTicks = 70; // 3.5 seconds of trembling tree veins descending from sky
            final int pulseTicks = 20; // 1.0 second of soul detachment pulse
            final int driftDuration = 160; // Exactly 8.0 seconds of guaranteed slow cinematic drift

            @Override
            public void run() {
                ticks++;

                // 1. Initial 3.5 seconds: Unstable tree-vein particle strings descending from sky onto players
                if (ticks <= veinTicks) {
                    for (Player p : participants) {
                        if (!p.isOnline()) continue;
                        Location head = p.getLocation().add(0, 1.8, 0);

                        for (double y = 0; y <= 16; y += 0.5) {
                            double swayX = Math.sin((ticks * 0.4) + y) * 0.32 + (Math.random() - 0.5) * 0.15;
                            double swayZ = Math.cos((ticks * 0.3) + y) * 0.32 + (Math.random() - 0.5) * 0.15;
                            Location veinPt = head.clone().add(swayX, y, swayZ);

                            world.spawnParticle(Particle.DUST, veinPt, 1, 0.04, 0.04, 0.04, 0,
                                    new Particle.DustOptions(Color.fromRGB(245, 248, 255), 1.25f));

                            // Branching fractal tendrils at higher elevation
                            if (y > 6 && Math.random() < 0.35) {
                                double branchX = swayX + Math.sin(y * 1.5) * 0.5;
                                double branchZ = swayZ + Math.cos(y * 1.5) * 0.5;
                                world.spawnParticle(Particle.DUST, head.clone().add(branchX, y, branchZ), 1, 0, 0, 0, 0,
                                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.0f));
                            }

                            if (y > 4 && Math.random() < 0.25) {
                                world.spawnParticle(Particle.END_ROD, veinPt.clone().add((Math.random() - 0.5) * 0.4, 0, (Math.random() - 0.5) * 0.4), 1, 0, 0, 0, 0.01);
                            }
                        }
                    }

                    if (ticks % 14 == 0) {
                        for (Player p : participants) {
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85f, 1.5f + (ticks * 0.008f));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f + (ticks * 0.008f));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
                            p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3f, 1.8f);
                        }
                    }
                    return;
                }

                // 2. Pulse of soul detachment (1.0 second)
                int extractionTick = ticks - veinTicks;
                if (extractionTick <= pulseTicks) {
                    if (extractionTick == 1) {
                        int idx = 0;
                        for (Player p : participants) {
                            if (p.isOnline()) {
                                startSoulLocs.put(p.getUniqueId(), p.getLocation().add(0, 2.0, 0));
                                double baseAngle = idx * (2 * Math.PI / Math.max(1, participants.size()));
                                soulAngles.put(p.getUniqueId(), baseAngle);
                                playRetroCrystalChime(p.getLocation(), 1.25f, 1.3f);
                                p.playSound(p.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1.0f, 0.7f);
                            }
                            idx++;
                        }
                    }

                    for (Player p : participants) {
                        Location sLoc = startSoulLocs.get(p.getUniqueId());
                        if (sLoc != null) {
                            world.spawnParticle(Particle.DUST, sLoc, 3, 0.15, 0.15, 0.15, 0,
                                    new Particle.DustOptions(Color.fromRGB(225, 240, 255), 1.6f));
                            world.spawnParticle(Particle.FIREWORK, sLoc, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }
                    return;
                }

                // 3. Guaranteed SLOW Cinematic Drift to Center (0, 0) for 160 ticks (8 seconds)
                int currentDrift = extractionTick - pulseTicks;
                double t = Math.min(1.0, currentDrift / (double) driftDuration);

                for (Player p : participants) {
                    Location start = startSoulLocs.get(p.getUniqueId());
                    if (start == null) continue;

                    // Interpolate base trajectory from start to center
                    double lx = start.getX() + (center.getX() - start.getX()) * t;
                    double ly = start.getY() + (center.getY() - start.getY()) * t;
                    double lz = start.getZ() + (center.getZ() - start.getZ()) * t;

                    // High arc lift (rises up to 3.5 blocks and arcs down into center)
                    double arcY = Math.sin(t * Math.PI) * 3.5;

                    // Spiral swirl converging into center
                    double baseAngle = soulAngles.getOrDefault(p.getUniqueId(), 0.0);
                    double swirlAngle = baseAngle + (t * 4.0 * Math.PI);
                    double spiralRadius = (1.0 - t) * 3.0 + 0.2;
                    double sx = Math.cos(swirlAngle) * spiralRadius;
                    double sz = Math.sin(swirlAngle) * spiralRadius;

                    Location soulPt = new Location(world, lx + sx, ly + arcY, lz + sz);
                    // Counter-rotating twin stardust stream for double-helix DNA effect
                    Location twinPt = new Location(world, lx - sx * 0.7, ly + arcY, lz - sz * 0.7);

                    world.spawnParticle(Particle.DUST, soulPt, 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(235, 245, 255), 1.5f));
                    world.spawnParticle(Particle.END_ROD, soulPt, 1, 0.01, 0.01, 0.01, 0.005);
                    world.spawnParticle(Particle.DUST, twinPt, 1, 0.04, 0.04, 0.04, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 215, 255), 1.1f));
                }

                // Center nexus ground hums and crackles as souls draw close
                if (t > 0.3) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add((Math.random() - 0.5) * 1.5, -1.8, (Math.random() - 0.5) * 1.5), 1, 0, 0.02, 0, 0.01);
                }

                if (currentDrift % 16 == 0) {
                    float pitch = 0.8f + (float) (t * 0.85);
                    playRetroCrystalChime(center, pitch, 1.25f);
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1f, pitch * 0.85f);
                }

                // Completed 8-second slow travel -> Singularity triggers blinding flash!
                if (currentDrift >= driftDuration) {
                    cancel();
                    triggerBlindingFlashAndStar(center);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Phase 3: Blinding Convergence & Star Cycling 7 Sins Colors.
     */
    private void triggerBlindingFlashAndStar(Location center) {
        this.state = EventState.STAR_CYCLE;
        World world = center.getWorld();

        // 1. Blinding Flash of Light & Detonation with clean long decay
        world.spawnParticle(Particle.FLASH, center, 14, 1.0, 1.0, 1.0, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
        world.spawnParticle(Particle.SONIC_BOOM, center, 1);
        playRetroCrystalChime(center, 1.5f, 2.0f);
        world.playSound(center, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.65f);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 2.0f);
        world.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 2.0f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);

        Title flashTitle = Title.title(
                miniMessage.deserialize("<white>█</white>"),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(300), Duration.ofMillis(500))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(flashTitle);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 45, 0, false, false, false));
        }

        // 2. Expand into spinning star cycling through 7 Sins colors
        final SinGemType[] sins = SinGemType.values(); // WRATH, GREED, GLUTTONY, LUST, ENVY, PRIDE, SLOTH
        final double ringRadius = 9.0;
        final double angleStep = (2 * Math.PI) / sins.length;

        new BukkitRunnable() {
            int ticks = 0;
            final int starDuration = 140; // 7.0 seconds of star spinning and screen cycling (20 ticks per Sin)
            int lastSinIndex = -1;

            @Override
            public void run() {
                ticks++;

                int sinIdx = (ticks / 20) % sins.length;
                SinGemType currentSin = sins[sinIdx];
                Color c = Color.fromRGB(
                        (currentSin.getColor().value() >> 16) & 0xFF,
                        (currentSin.getColor().value() >> 8) & 0xFF,
                        currentSin.getColor().value() & 0xFF
                );
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.5f);

                // Render spinning 5-pointed star at (0, 0)
                double rot = ticks * 0.10;
                renderSpinningStar(center, rot, 3.2, dust);

                // Screen Cycle & Soul Materialization
                if (sinIdx != lastSinIndex) {
                    lastSinIndex = sinIdx;

                    Title sinTitle = Title.title(
                            miniMessage.deserialize("<bold><color:" + currentSin.getColor().asHexString() + ">✦ " + currentSin.getDisplayName() + " ✦</color></bold>"),
                            miniMessage.deserialize("<gray>✦ Sin Essence Manifesting... ✦</gray>"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(750), Duration.ofMillis(150))
                    );

                    float sinPitch = switch (currentSin) {
                        case WRATH -> 0.85f;
                        case GREED -> 1.35f;
                        case GLUTTONY -> 1.05f;
                        case LUST -> 1.75f;
                        case ENVY -> 1.20f;
                        case PRIDE -> 1.95f;
                        case SLOTH -> 0.70f;
                    };

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(sinTitle);
                        playRetroCrystalChime(p.getLocation(), sinPitch, 1.25f);
                    }
                }

                // In the 9-block ring, faint colored souls begin to slowly appear
                for (int i = 0; i <= sinIdx; i++) {
                    SinGemType sin = sins[i];
                    double angle = i * angleStep;
                    double sx = center.getX() + Math.cos(angle) * ringRadius;
                    double sz = center.getZ() + Math.sin(angle) * ringRadius;
                    double sy = Math.max(world.getHighestBlockYAt((int) sx, (int) sz) + 1.2, center.getY());

                    Color sc = Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    );
                    Location soulLoc = new Location(world, sx, sy, sz);
                    world.spawnParticle(Particle.DUST, soulLoc, 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(sc, 1.2f));
                }

                if (ticks >= starDuration) {
                    cancel();
                    spawnSinSoulsRing(center);
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private void renderSpinningStar(Location center, double rotation, double radius, Particle.DustOptions dust) {
        World world = center.getWorld();
        int points = 5;
        double step = (2 * Math.PI) / points;

        for (int i = 0; i < points; i++) {
            double a1 = rotation + (i * step);
            double a2 = rotation + (((i + 2) % points) * step);

            Vector v1 = new Vector(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vector v2 = new Vector(Math.cos(a2) * radius, 0, Math.sin(a2) * radius);

            // Interpolate line between star points
            for (double f = 0; f <= 1.0; f += 0.25) {
                Vector p = v1.clone().multiply(1.0 - f).add(v2.clone().multiply(f));
                world.spawnParticle(Particle.DUST, center.clone().add(p), 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /**
     * Phase 4: 7 Spinning Sin Souls manifest within the 25-block Sanctuary.
     * Uses modern Interaction entities to guarantee 100% clickability and responsive selection.
     */
    private void spawnSinSoulsRing(Location center) {
        this.state = EventState.SIN_CHOICE;
        World world = center.getWorld();

        SinGemType[] sins = SinGemType.values();
        double ringRadius = 9.0; // 9 blocks radius (fits safely inside 25-block diameter sanctuary)
        double angleStep = (2 * Math.PI) / sins.length;

        sinSoulLocations.clear();
        cleanupDisplays();

        for (int i = 0; i < sins.length; i++) {
            SinGemType sin = sins[i];
            double angle = i * angleStep;
            double x = center.getX() + Math.cos(angle) * ringRadius;
            double z = center.getZ() + Math.sin(angle) * ringRadius;
            double y = Math.max(world.getHighestBlockYAt((int) x, (int) z) + 1.2, center.getY());

            Location soulLoc = new Location(world, x, y, z);
            sinSoulLocations.put(sin, soulLoc);

            // 1. Floating ItemDisplay (visual icon)
            ItemDisplay id = world.spawn(soulLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(sin.getIconMaterial()));
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.85f, 0.85f, 0.85f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                d.getPersistentDataContainer().set(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING, sin.name());
            });
            activeSoulDisplays.add(id);

            // 2. Floating TextDisplay holographic title
            TextDisplay td = world.spawn(soulLoc.clone().add(0, 1.2, 0), TextDisplay.class, t -> {
                t.text(miniMessage.deserialize("<bold><color:" + sin.getColor().asHexString() + ">" + sin.getDisplayName() + "</color></bold>\n<yellow><bold>[Right-Click to Choose]</bold></yellow>"));
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            });
            activeTextDisplays.add(td);

            // 3. Dedicated Interaction Hitbox Entity (Fixes display entities having no hitbox!)
            Interaction hitbox = world.spawn(soulLoc.clone().subtract(0, 0.4, 0), Interaction.class, it -> {
                it.setInteractionWidth(1.8f);
                it.setInteractionHeight(2.2f);
                it.setResponsive(true);
                it.getPersistentDataContainer().set(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING, sin.name());
            });
            activeInteractions.add(hitbox);
        }

        // Broadcast interactive chat selection roster
        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<yellow><bold>✦ THE 7 SIN SOULS HAVE AWAKENED! ✦</bold></yellow>"));
        Bukkit.broadcast(miniMessage.deserialize("<gray>Approach a spinning soul and right-click, or click below in chat:</gray>"));

        Component roster = Component.empty();
        for (SinGemType s : sins) {
            Component btn = Component.text("[" + s.name() + "] ", TextColor.color(s.getColor().value()), TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/church confirm " + s.name()))
                    .hoverEvent(HoverEvent.showText(Component.text("Attune to " + s.getDisplayName(), NamedTextColor.YELLOW)));
            roster = roster.append(btn);
        }
        Bukkit.broadcast(roster);
        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            playRetroCrystalChime(p.getLocation(), 1.5f, 1.3f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 1.0f);
        }

        // Active animation & proximity prompt task
        this.mainLoopTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (state != EventState.SIN_CHOICE) {
                    cancel();
                    return;
                }
                ticks++;

                for (Map.Entry<SinGemType, Location> entry : sinSoulLocations.entrySet()) {
                    SinGemType sin = entry.getKey();
                    Location loc = entry.getValue();

                    Color c = Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    );

                    // 1. Rotating ritual ground rune beneath each soul
                    for (int deg = 0; deg < 360; deg += 60) {
                        double rAngle = Math.toRadians(deg + (ticks * 4));
                        Location ringPt = loc.clone().add(Math.cos(rAngle) * 1.1, -0.9, Math.sin(rAngle) * 1.1);
                        world.spawnParticle(Particle.DUST, ringPt, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.2f));
                    }

                    // 2. Gentle floating bob + orbiting particle aura
                    double rad = Math.toRadians((ticks * 5) % 360);
                    Location pLoc = loc.clone().add(Math.cos(rad) * 0.7, 0.4 + Math.sin(ticks * 0.12) * 0.15, Math.sin(rad) * 0.7);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.4f));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.3, 0), 1, 0.04, 0.04, 0.04, 0.01);
                }

                // Proximity check for action bar inspection HUD
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (attunedPlayers.contains(p.getUniqueId())) continue;

                    SinGemType nearestSin = null;
                    double nearestDistSq = Double.MAX_VALUE;

                    for (Map.Entry<SinGemType, Location> entry : sinSoulLocations.entrySet()) {
                        double distSq = p.getLocation().distanceSquared(entry.getValue());
                        if (distSq <= 12.25 && distSq < nearestDistSq) { // Within 3.5 blocks
                            nearestDistSq = distSq;
                            nearestSin = entry.getKey();
                        }
                    }

                    if (nearestSin != null) {
                        p.sendActionBar(miniMessage.deserialize(
                                "<bold><color:" + nearestSin.getColor().asHexString() + ">✦ " + nearestSin.getDisplayName().toUpperCase() + " ✦</color></bold>  <gray>•</gray>  <white><bold>[Right-Click to Embrace]</bold></white>"
                        ));
                        p.setFreezeTicks(Math.max(p.getFreezeTicks(), 50));
                        if (ticks % 20 == 0) {
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75f, 1.8f);
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85f, 2.0f);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.65f, 1.5f);
                            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.5f, 1.6f);
                        }
                    }
                }

                // Keep central star spinning
                renderSpinningStar(center, ticks * 0.08, 3.2, new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // Fail-safe auto-timeout task (75 seconds max, prevents event from ever freezing permanently)
        this.autoTimeoutTask = new BukkitRunnable() {
            int secondsLeft = 75;

            @Override
            public void run() {
                if (state != EventState.SIN_CHOICE) {
                    cancel();
                    return;
                }
                secondsLeft -= 5;

                if (secondsLeft == 15) {
                    Bukkit.broadcast(miniMessage.deserialize("<red><bold>✦ 15 seconds remaining to choose your Sin Attunement! ✦</bold></red>"));
                } else if (secondsLeft <= 0) {
                    cancel();
                    autoAssignRemainingSins();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * Handles player right-clicking on an Interaction hitbox or Display entity.
     */
    @EventHandler
    public void onSoulInteract(PlayerInteractAtEntityEvent event) {
        if (state != EventState.SIN_CHOICE) return;

        String sinName = event.getRightClicked().getPersistentDataContainer().get(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING);
        if (sinName == null) return;

        event.setCancelled(true);
        handleSinSelectionPrompt(event.getPlayer(), sinName);
    }

    /**
     * Handles player punching (left-clicking) an Interaction entity.
     */
    @EventHandler
    public void onSoulDamage(EntityDamageByEntityEvent event) {
        if (state != EventState.SIN_CHOICE) return;
        if (!(event.getDamager() instanceof Player player)) return;

        String sinName = event.getEntity().getPersistentDataContainer().get(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING);
        if (sinName == null) return;

        event.setCancelled(true);
        handleSinSelectionPrompt(player, sinName);
    }

    private void handleSinSelectionPrompt(Player player, String sinName) {
        SinGemType sin;
        try {
            sin = SinGemType.valueOf(sinName);
        } catch (IllegalArgumentException ex) {
            return;
        }

        if (attunedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<red>You have already chosen your Sin Attunement!</red>"));
            return;
        }

        Component confirmButton = Component.text("[CLICK TO CONFIRM]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/church confirm " + sin.name()))
                .hoverEvent(HoverEvent.showText(Component.text("Attune your soul to " + sin.getDisplayName(), NamedTextColor.YELLOW)));

        player.sendMessage(miniMessage.deserialize("<gold>✦ Do you choose to attune to <bold>" + sin.getDisplayName() + "</bold>? </gold>").append(confirmButton));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
    }

    /**
     * Spawns a descending pillar of lightning strikes at the chosen Sin,
     * coupled with a slow-expanding ground shockwave with a color gradient fading to black.
     */
    private void triggerSinPillarAndShockwave(Location origin, SinGemType sin) {
        World world = origin.getWorld();
        if (world == null) return;

        Color sinColor = Color.fromRGB(
                (sin.getColor().value() >> 16) & 0xFF,
                (sin.getColor().value() >> 8) & 0xFF,
                sin.getColor().value() & 0xFF
        );

        // 1. Pillar of lightning bolts repeatedly striking down over 20 ticks
        new BukkitRunnable() {
            int strikes = 0;

            @Override
            public void run() {
                if (strikes >= 5) {
                    cancel();
                    return;
                }
                strikes++;

                world.strikeLightningEffect(origin);
                world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 0.85f + (strikes * 0.05f));
                world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.7f);

                // Vertical lightning pillar particles
                for (double y = 0; y <= 28; y += 1.0) {
                    Location pillarPt = origin.clone().add(
                            (Math.random() - 0.5) * 0.6,
                            y,
                            (Math.random() - 0.5) * 0.6
                    );
                    world.spawnParticle(Particle.END_ROD, pillarPt, 1, 0, 0, 0, 0.02);
                    world.spawnParticle(Particle.DUST, pillarPt, 2, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(sinColor, 1.5f));
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        // 2. Expanding ground shockwave with dark color gradient to black as it slowly fades
        new BukkitRunnable() {
            int waveTicks = 0;
            final int maxWaveTicks = 25; // 1.25s duration

            @Override
            public void run() {
                waveTicks++;
                if (waveTicks > maxWaveTicks) {
                    cancel();
                    return;
                }

                double progress = (double) waveTicks / maxWaveTicks; // 0.0 to 1.0
                double radius = 0.5 + Math.pow(progress, 0.8) * 9.5; // Expands outward from 0.5 to 10.0 blocks

                // Interpolate color from vibrant Sin Color -> deep dark shade -> pitch black (0, 0, 0)
                int r = (int) (sinColor.getRed() * Math.max(0.0, 1.0 - (progress * 1.15)));
                int g = (int) (sinColor.getGreen() * Math.max(0.0, 1.0 - (progress * 1.15)));
                int b = (int) (sinColor.getBlue() * Math.max(0.0, 1.0 - (progress * 1.15)));
                Color ringColor = Color.fromRGB(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));

                float particleSize = (float) Math.max(0.4f, 2.2f * (1.0 - (progress * 0.65)));
                Particle.DustOptions ringDust = new Particle.DustOptions(ringColor, particleSize);

                // Render shockwave ripple on terrain surface
                int points = 44;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double px = origin.getX() + Math.cos(angle) * radius;
                    double pz = origin.getZ() + Math.sin(angle) * radius;
                    int groundY = world.getHighestBlockYAt((int) px, (int) pz);
                    Location ringLoc = new Location(world, px, groundY + 0.12, pz);

                    world.spawnParticle(Particle.DUST, ringLoc, 1, 0, 0, 0, 0, ringDust);

                    // Dark rim shadow / smoke dissipation
                    if (progress > 0.35 && i % 4 == 0) {
                        world.spawnParticle(Particle.SQUID_INK, ringLoc, 1, 0.02, 0.03, 0.02, 0.01);
                    }
                    if (progress > 0.5 && i % 3 == 0) {
                        world.spawnParticle(Particle.SMOKE, ringLoc, 1, 0.02, 0.02, 0.02, 0.01);
                    }
                }

                if (waveTicks == 1) {
                    world.playSound(origin, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2f, 0.5f);
                    world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.7f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Confirms player's Sin choice.
     */
    public boolean confirmSinChoice(Player player, SinGemType sin) {
        if (state != EventState.SIN_CHOICE) {
            player.sendMessage(miniMessage.deserialize("<red>Sin attunement selection is not currently active.</red>"));
            return false;
        }

        if (attunedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<red>You have already chosen your Sin!</red>"));
            return false;
        }

        attunedPlayers.add(player.getUniqueId());
        playerChosenSins.put(player.getUniqueId(), sin);

        // Attune via SinGemManager
        plugin.getSinGemManager().forceAttune(player, sin);
        plugin.getSinGemManager().giveGemToPlayer(player, sin);

        // Grant Invisibility for 60 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0, false, false, true));

        // Screen pop & Title
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        Title sinTitle = Title.title(
                miniMessage.deserialize("<bold><color:" + sin.getColor().asHexString() + ">✦ " + sin.getDisplayName().toUpperCase() + " ✦</color></bold>"),
                miniMessage.deserialize("<gradient:#FFFFFF:#AAAAAA><italic>Soul Attuned to " + sin.getDisplayName() + "</italic></gradient>"),
                Title.Times.times(Duration.ofMillis(80), Duration.ofSeconds(2), Duration.ofMillis(500))
        );
        player.showTitle(sinTitle);
        player.sendActionBar(Component.empty());

        // Audio & Visual pulse
        playRetroCrystalChime(player.getLocation(), 1.4f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.4f);

        Color c = Color.fromRGB(
                (sin.getColor().value() >> 16) & 0xFF,
                (sin.getColor().value() >> 8) & 0xFF,
                sin.getColor().value() & 0xFF
        );
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.0, 0), 25, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(c, 1.8f));
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.0, 0), 8, 0.2, 0.2, 0.2, 0.02);

        // Pillar of lightning and expanding ground shockwave
        Location soulLoc = sinSoulLocations.get(sin);
        triggerSinPillarAndShockwave(soulLoc != null ? soulLoc : player.getLocation(), sin);

        Bukkit.broadcast(miniMessage.deserialize("<light_purple>✦ <bold>" + player.getName() + "</bold> has embraced the Sin of " + sin.getDisplayName() + "! (" + attunedPlayers.size() + "/" + Bukkit.getOnlinePlayers().size() + ")</light_purple>"));

        // Check if all online players have chosen their sin
        int totalOnline = Bukkit.getOnlinePlayers().size();
        if (attunedPlayers.size() >= totalOnline) {
            triggerVoidClimaxAndLaunch();
        }

        return true;
    }

    /**
     * Auto-assigns remaining un-attuned players to ensure the event never halts indefinitely.
     */
    private void autoAssignRemainingSins() {
        if (state != EventState.SIN_CHOICE) return;

        List<SinGemType> availableSins = new ArrayList<>(Arrays.asList(SinGemType.values()));
        Collections.shuffle(availableSins);

        int sinIdx = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!attunedPlayers.contains(p.getUniqueId())) {
                SinGemType assign = availableSins.get(sinIdx % availableSins.size());
                sinIdx++;
                confirmSinChoice(p, assign);
            }
        }
    }

    /**
     * Admin command force progression: /churchadmin start force.
     */
    public void forceProgress(CommandSender sender) {
        if (state == EventState.IDLE) {
            sender.sendMessage(miniMessage.deserialize("<red>No Start Event is currently running.</red>"));
            return;
        }

        sender.sendMessage(miniMessage.deserialize("<green>Forcing progression of Start Event...</green>"));

        if (state == EventState.READY_CHECK) {
            readyPlayers.clear();
            for (Player p : Bukkit.getOnlinePlayers()) readyPlayers.add(p.getUniqueId());
            triggerSoulExtraction();
        } else if (state == EventState.SOUL_EXTRACTION || state == EventState.STAR_CYCLE) {
            World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
            Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.0, 0.5);
            spawnSinSoulsRing(center);
        } else if (state == EventState.SIN_CHOICE) {
            autoAssignRemainingSins();
        } else if (state == EventState.VOID_CLIMAX) {
            World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
            Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.0, 0.5);
            executeGrandLaunch(center);
        }
    }

    /**
     * Phase 5: Void Climax, Lightning, & Sky Elytra Launch.
     */
    private void triggerVoidClimaxAndLaunch() {
        this.state = EventState.VOID_CLIMAX;
        if (mainLoopTask != null) mainLoopTask.cancel();
        if (autoTimeoutTask != null) autoTimeoutTask.cancel();
        cleanupDisplays();

        World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
        Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.0, 0.5);

        Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>✦ ALL SINS EMBRACED! THE ABYSS CONSUMES THE REALM! ✦</bold></dark_red>"));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // Exactly 4.0 seconds of black hole & widening beacon beam

            @Override
            public void run() {
                ticks++;

                double beamRadius = Math.min(3.5, 0.5 + (ticks * 0.05));

                // 1. Swirling Gray/Black Orb
                world.spawnParticle(Particle.DUST, center, 8, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(20, 20, 25), 1.8f));
                world.spawnParticle(Particle.DUST, center, 4, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 80, 85), 1.4f));
                world.spawnParticle(Particle.SMOKE, center, 3, 0.2, 0.2, 0.2, 0.02);

                // Gravitational suction particles (matter pulled into singularity)
                for (int i = 0; i < 3; i++) {
                    double pullAngle = Math.random() * 2 * Math.PI;
                    double pullDist = 2.5 + Math.random() * 4.5;
                    Location pullStart = center.clone().add(Math.cos(pullAngle) * pullDist, (Math.random() - 0.5) * 2.5, Math.sin(pullAngle) * pullDist);
                    Vector pullVec = center.toVector().subtract(pullStart.toVector()).normalize().multiply(0.35);
                    world.spawnParticle(Particle.PORTAL, pullStart, 0, pullVec.getX(), pullVec.getY(), pullVec.getZ(), 0.6);
                }

                // 2. Widening Beacon Beam shooting upwards with ascending spiral rings
                for (double y = 0; y <= 35; y += 1.2) {
                    for (int d = 0; d < 360; d += 90) {
                        double rad = Math.toRadians(d + (ticks * 8));
                        Location bPt = center.clone().add(Math.cos(rad) * beamRadius, y, Math.sin(rad) * beamRadius);
                        world.spawnParticle(Particle.END_ROD, bPt, 1, 0, 0, 0, 0.01);
                    }
                }

                double spiralY = (ticks * 1.2) % 35;
                double spiralAngle = ticks * 0.25;
                Location spiralPt = center.clone().add(Math.cos(spiralAngle) * (beamRadius + 0.2), spiralY, Math.sin(spiralAngle) * (beamRadius + 0.2));
                world.spawnParticle(Particle.DUST, spiralPt, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.5f));

                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.4f, 0.5f + (ticks * 0.01f));
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f + (ticks * 0.01f));
                }

                // Climax at exactly 4 seconds
                if (ticks >= maxTicks) {
                    cancel();
                    executeGrandLaunch(center);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeGrandLaunch(Location center) {
        this.state = EventState.LAUNCH_GLIDE;
        World world = center.getWorld();

        // 1. Black hole disappears & Lightning strikes
        world.strikeLightningEffect(center);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);

        // Reset sky to dawn of the new realm & lift boss fog
        world.setTime(0);
        world.setStorm(false);
        world.setThundering(false);

        if (ceremonyBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(ceremonyBossBar);
            }
            ceremonyBossBar = null;
        }

        // 2. Restore World Border
        WorldBorder border = world.getWorldBorder();
        if (originalBorderCenter != null) {
            border.setCenter(originalBorderCenter);
        }
        border.setSize(originalBorderSize > 25 ? originalBorderSize : 10000);

        Title launchTitle = Title.title(
                miniMessage.deserialize("<gradient:#FFD700:#FF4500><bold>✦ THE NEW ERA DAWNS ✦</bold></gradient>"),
                miniMessage.deserialize("<yellow>Glide to your starting sanctuary • The realm is born!</yellow>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))
        );

        // 3. Launch everyone into air to Y:200 with temporary Elytra & Hyperspace FOV stretch
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();

            // Cache original chestplate
            ItemStack cp = p.getInventory().getChestplate();
            if (cp != null && cp.getType() != Material.AIR) {
                cachedChestplates.put(p.getUniqueId(), cp.clone());
            }

            // Equip temporary Elytra
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            p.getInventory().setChestplate(elytra);
            glidingPlayers.add(p.getUniqueId());

            // Teleport smoothly to Y:200 and launch forward
            Location launchLoc = new Location(world, loc.getX(), 200, loc.getZ(), loc.getYaw(), loc.getPitch());
            p.teleport(launchLoc);
            p.setVelocity(loc.getDirection().normalize().multiply(1.5).setY(0.25));
            p.setGliding(true);

            // Hyperspace FOV warp stretch
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 12, false, false, false));

            p.showTitle(launchTitle);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.5f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 1.0f);
        }

        startGlideTrailTask();
        Bukkit.broadcast(miniMessage.deserialize("<gold>✦ The Genesis Ceremony has concluded! Realm borders expanded to " + (int) border.getSize() + " blocks! ✦</gold>"));
    }

    private void startGlideTrailTask() {
        if (glideTrailTask != null) glideTrailTask.cancel();

        this.glideTrailTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (glidingPlayers.isEmpty()) {
                    cancel();
                    return;
                }
                for (UUID uuid : new ArrayList<>(glidingPlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline() || !p.isGliding()) continue;

                    SinGemType sin = playerChosenSins.get(uuid);
                    Color c = (sin != null) ? Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    ) : Color.WHITE;

                    Location trailLoc = p.getLocation().subtract(p.getLocation().getDirection().multiply(0.7)).add(0, 0.2, 0);
                    p.getWorld().spawnParticle(Particle.DUST, trailLoc, 3, 0.08, 0.08, 0.08, 0, new Particle.DustOptions(c, 1.8f));
                    p.getWorld().spawnParticle(Particle.END_ROD, trailLoc, 1, 0.01, 0.01, 0.01, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Cleans up temporary Elytra and restores original chestplate upon landing.
     */
    @EventHandler
    public void onPlayerLand(PlayerMoveEvent event) {
        if (!glidingPlayers.contains(event.getPlayer().getUniqueId())) return;
        Player player = event.getPlayer();

        if (player.isOnGround() || player.isInWater() || player.getLocation().getBlock().isLiquid()) {
            glidingPlayers.remove(player.getUniqueId());

            // Restore chestplate
            ItemStack original = cachedChestplates.remove(player.getUniqueId());
            if (original != null) {
                player.getInventory().setChestplate(original);
            } else {
                player.getInventory().setChestplate(null);
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.4f, 1.2f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 10, 0.3, 0.1, 0.3, 0.02);
            player.sendMessage(miniMessage.deserialize("<green><bold>✦ Safely landed! Your gear has been restored. ✦</bold></green>"));

            if (glidingPlayers.isEmpty()) {
                // All players have safely landed
                if (glideTrailTask != null) {
                    glideTrailTask.cancel();
                    glideTrailTask = null;
                }
                this.state = EventState.IDLE;
            }
        }
    }

    /**
     * Prevent fall damage during the initial launch and glide.
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && glidingPlayers.contains(p.getUniqueId())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (ceremonyBossBar != null) {
            p.hideBossBar(ceremonyBossBar);
        }
        if (glidingPlayers.remove(p.getUniqueId())) {
            ItemStack original = cachedChestplates.remove(p.getUniqueId());
            if (original != null) {
                p.getInventory().setChestplate(original);
            } else {
                p.getInventory().setChestplate(null);
            }
            if (glidingPlayers.isEmpty() && glideTrailTask != null) {
                glideTrailTask.cancel();
                glideTrailTask = null;
            }
        }
    }

    @EventHandler
    public void onPlayerJoinDuringEvent(org.bukkit.event.player.PlayerJoinEvent event) {
        if (isEventActive()) {
            World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
            Location spawnLoc = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5);
            event.getPlayer().teleport(spawnLoc);
            if (ceremonyBossBar != null) {
                event.getPlayer().showBossBar(ceremonyBossBar);
            }
            event.getPlayer().sendMessage(miniMessage.deserialize("<gold>✦ A Genesis Ceremony is currently active at Spawn! Type <yellow><bold>/church ready</bold></yellow>! ✦</gold>"));
            updateReadyActionBar();
        }
    }

    public void cleanupDisplays() {
        if (ceremonyBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(ceremonyBossBar);
            }
            ceremonyBossBar = null;
        }
        if (continuousSoundscapeTask != null) {
            continuousSoundscapeTask.cancel();
            continuousSoundscapeTask = null;
        }
        if (glideTrailTask != null) {
            glideTrailTask.cancel();
            glideTrailTask = null;
        }
        if (readyAmbientTask != null) {
            readyAmbientTask.cancel();
            readyAmbientTask = null;
        }
        if (mainLoopTask != null) {
            mainLoopTask.cancel();
            mainLoopTask = null;
        }
        if (autoTimeoutTask != null) {
            autoTimeoutTask.cancel();
            autoTimeoutTask = null;
        }
        for (ItemDisplay id : activeSoulDisplays) {
            if (id != null && id.isValid()) id.remove();
        }
        activeSoulDisplays.clear();

        for (TextDisplay td : activeTextDisplays) {
            if (td != null && td.isValid()) td.remove();
        }
        activeTextDisplays.clear();

        for (Interaction it : activeInteractions) {
            if (it != null && it.isValid()) it.remove();
        }
        activeInteractions.clear();
    }
}
