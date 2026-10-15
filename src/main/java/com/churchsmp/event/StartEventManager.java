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
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final Map<SinGemType, ItemDisplay> sinItemDisplays = new ConcurrentHashMap<>();
    private final Map<SinGemType, TextDisplay> sinTextDisplays = new ConcurrentHashMap<>();
    private final Map<SinGemType, Interaction> sinInteractions = new ConcurrentHashMap<>();

    private double originalBorderSize = 10000;
    private Location originalBorderCenter = null;
    private final Map<UUID, SinGemType> playerChosenSins = new ConcurrentHashMap<>();
    private final Set<SinGemType> claimedSins = ConcurrentHashMap.newKeySet();
    private final Set<UUID> fallImmunityPlayers = ConcurrentHashMap.newKeySet();
    private Location customCenter = null;
    private BukkitTask mainLoopTask = null;
    private BukkitTask autoTimeoutTask = null;
    private BukkitTask readyAmbientTask = null;
    private BukkitTask glideTrailTask = null;
    private BukkitTask continuousSoundscapeTask = null;
    private BossBar ceremonyBossBar = null;

    // --- BLACK SUN CONSTANTS (Base Item: HEART_OF_THE_SEA) ---
    public static final int CMD_BLACK_SUN_BASE = 7701;
    public static final int CMD_BLACK_SUN_WRATH = 7702;
    public static final int CMD_BLACK_SUN_GREED = 7703;
    public static final int CMD_BLACK_SUN_GLUTTONY = 7704;
    public static final int CMD_BLACK_SUN_LUST = 7705;
    public static final int CMD_BLACK_SUN_ENVY = 7706;
    public static final int CMD_BLACK_SUN_PRIDE = 7707;
    public static final int CMD_BLACK_SUN_SLOTH = 7708;
    public static final int CMD_BLACK_SUN_SIN = 7709;
    public static final int CMD_BLACK_SUN_HOLE = 7710;
    public static final int CMD_BLACK_SUN_WHITE = 7711;

    private ItemDisplay blackSunDisplay = null;
    private final Map<UUID, ItemDisplay> testBlackSunDisplays = new ConcurrentHashMap<>();

    public static int getSinBlackSunCMD(SinGemType sin) {
        if (sin == null) return CMD_BLACK_SUN_BASE;
        return switch (sin) {
            case WRATH -> CMD_BLACK_SUN_WRATH;
            case GREED -> CMD_BLACK_SUN_GREED;
            case GLUTTONY -> CMD_BLACK_SUN_GLUTTONY;
            case LUST -> CMD_BLACK_SUN_LUST;
            case ENVY -> CMD_BLACK_SUN_ENVY;
            case PRIDE -> CMD_BLACK_SUN_PRIDE;
            case SLOTH -> CMD_BLACK_SUN_SLOTH;
            case SIN -> CMD_BLACK_SUN_SIN;
            case ZEN -> CMD_BLACK_SUN_WHITE;
        };
    }

    public static ItemStack createBlackSunItem(int customModelData) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemDisplay spawnBlackSunDisplay(Location center, int customModelData, float initialScale) {
        World world = center.getWorld();
        if (world == null) return null;
        return world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(createBlackSunItem(customModelData));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(initialScale, initialScale, initialScale),
                    new AxisAngle4f(0f, 1f, 0f, 0f)
            ));
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
        });
    }

    public void updateBlackSunTransform(ItemDisplay display, float rotY, float tiltAngle, float scale) {
        if (display == null || !display.isValid()) return;
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(rotY, 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(tiltAngle, 1f, 0f, 0.4f)
        ));
        display.setInterpolationDuration(2);
        display.setInterpolationDelay(0);
    }

    public void removeBlackSun() {
        if (blackSunDisplay != null) {
            if (blackSunDisplay.isValid()) blackSunDisplay.remove();
            blackSunDisplay = null;
        }
    }

    public StartEventManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    public EventState getState() {
        return state;
    }

    public boolean isEventActive() {
        return state != EventState.IDLE;
    }

    public void setCustomCenter(Location loc) {
        this.customCenter = loc;
        if (loc != null) {
            plugin.getConfig().set("start_event.blacksun_world", loc.getWorld().getName());
            plugin.getConfig().set("start_event.blacksun_x", loc.getX());
            plugin.getConfig().set("start_event.blacksun_y", loc.getY());
            plugin.getConfig().set("start_event.blacksun_z", loc.getZ());
            plugin.saveConfig();
        }
    }

    public Location getCustomCenter() {
        if (customCenter == null) {
            String wName = plugin.getConfig().getString("start_event.blacksun_world");
            if (wName != null && Bukkit.getWorld(wName) != null) {
                World w = Bukkit.getWorld(wName);
                double x = plugin.getConfig().getDouble("start_event.blacksun_x");
                double y = plugin.getConfig().getDouble("start_event.blacksun_y");
                double z = plugin.getConfig().getDouble("start_event.blacksun_z");
                this.customCenter = new Location(w, x, y, z);
            }
        }
        return customCenter;
    }

    public Location getEventCenter(World world) {
        Location custom = getCustomCenter();
        if (custom != null && custom.getWorld().equals(world)) {
            return custom.clone();
        }
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.2, 0.5);
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
     * Renders a colossal rotating Tri-Ring Gyro-Glyph Matrix on the sanctuary ground:
     * 1. Core Reactor Ring (r=2.6) spinning clockwise with 4 energy nodes.
     * 2. Middle Rune Gear (r=6.0) spinning counter-clockwise with 6 runic nodes & rotating star chords.
     * 3. Outer Accelerator Ring (r=11.2) spinning clockwise with 8 perimeter nodes.
     * 4. 4 Sweeping Cardinal Pylons scanning the sanctuary with vertical starlight.
     * 5. 4 Rotating Radial Energy Spokes linking the concentric rings into a holographic matrix.
     */
    private void renderSanctuaryMagicCircle(Location center, int ticks) {
        World world = center.getWorld();
        if (world == null) return;

        double coreRadius = 2.6;
        double midRadius = 6.0;
        double outerRadius = 11.2;

        double coreAngleOffset = ticks * 0.08;
        double midAngleOffset = -ticks * 0.04;
        double outerAngleOffset = ticks * 0.025;

        Particle.DustOptions coreDust = new Particle.DustOptions(Color.fromRGB(220, 245, 255), 1.4f);
        Particle.DustOptions midDust = new Particle.DustOptions(Color.fromRGB(160, 190, 255), 1.3f);
        Particle.DustOptions outerDust = new Particle.DustOptions(Color.fromRGB(240, 210, 255), 1.5f);
        Particle.DustOptions spokeDust = new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.0f);

        // 1. Core Reactor Ring (Fast Clockwise)
        int corePoints = 20;
        for (int i = 0; i < corePoints; i++) {
            double angle = (2 * Math.PI * i) / corePoints + coreAngleOffset;
            double px = center.getX() + Math.cos(angle) * coreRadius;
            double pz = center.getZ() + Math.sin(angle) * coreRadius;
            int gy = world.getHighestBlockYAt((int) px, (int) pz);
            world.spawnParticle(Particle.DUST, px, gy + 0.12, pz, 1, 0, 0, 0, 0, coreDust);
        }
        for (int n = 0; n < 4; n++) {
            double nAngle = (Math.PI / 2 * n) + coreAngleOffset;
            double nx = center.getX() + Math.cos(nAngle) * coreRadius;
            double nz = center.getZ() + Math.sin(nAngle) * coreRadius;
            int gy = world.getHighestBlockYAt((int) nx, (int) nz);
            world.spawnParticle(Particle.END_ROD, nx, gy + 0.2, nz, 1, 0, 0.01, 0, 0.01);
        }

        // 2. Middle Rune Gear (Counter-Clockwise) with 6 Nodes & Rotating Hexagram Star
        int midPoints = 32;
        for (int i = 0; i < midPoints; i++) {
            double angle = (2 * Math.PI * i) / midPoints + midAngleOffset;
            double px = center.getX() + Math.cos(angle) * midRadius;
            double pz = center.getZ() + Math.sin(angle) * midRadius;
            int gy = world.getHighestBlockYAt((int) px, (int) pz);
            world.spawnParticle(Particle.DUST, px, gy + 0.12, pz, 1, 0, 0, 0, 0, midDust);
        }
        Location[] hexNodes = new Location[6];
        for (int n = 0; n < 6; n++) {
            double nAngle = (Math.PI / 3 * n) + midAngleOffset;
            double nx = center.getX() + Math.cos(nAngle) * midRadius;
            double nz = center.getZ() + Math.sin(nAngle) * midRadius;
            int gy = world.getHighestBlockYAt((int) nx, (int) nz);
            hexNodes[n] = new Location(world, nx, gy + 0.12, nz);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, nx, gy + 0.15, nz, 1, 0, 0.02, 0, 0.005);
            world.spawnParticle(Particle.END_ROD, nx, gy + 0.25, nz, 1, 0, 0.01, 0, 0.01);
        }
        // Rotating Hexagram star chords connecting nodes (0-2-4 and 1-3-5)
        if (ticks % 4 == 0) {
            for (int t = 0; t < 3; t++) {
                renderGroundChord(world, hexNodes[t * 2], hexNodes[(t * 2 + 2) % 6], spokeDust);
                renderGroundChord(world, hexNodes[t * 2 + 1], hexNodes[(t * 2 + 3) % 6], spokeDust);
            }
        }

        // 3. Outer Accelerator Ring (Clockwise) with 8 Perimeter Energy Nodes
        int outerPoints = 44;
        for (int i = 0; i < outerPoints; i++) {
            double angle = (2 * Math.PI * i) / outerPoints + outerAngleOffset;
            double px = center.getX() + Math.cos(angle) * outerRadius;
            double pz = center.getZ() + Math.sin(angle) * outerRadius;
            int gy = world.getHighestBlockYAt((int) px, (int) pz);
            world.spawnParticle(Particle.DUST, px, gy + 0.12, pz, 1, 0, 0, 0, 0, outerDust);
        }
        for (int n = 0; n < 8; n++) {
            double nAngle = (Math.PI / 4 * n) + outerAngleOffset;
            double nx = center.getX() + Math.cos(nAngle) * outerRadius;
            double nz = center.getZ() + Math.sin(nAngle) * outerRadius;
            int gy = world.getHighestBlockYAt((int) nx, (int) nz);
            if (ticks % 4 == 0) {
                world.spawnParticle(Particle.END_ROD, nx, gy + 0.25, nz, 1, 0, 0.01, 0, 0.01);
            }
        }

        // 4. 4 Sweeping Cardinal Pylons (scanning the perimeter like radar containment)
        for (int c = 0; c < 4; c++) {
            double cAngle = (Math.PI / 2 * c) + outerAngleOffset;
            double cx = center.getX() + Math.cos(cAngle) * outerRadius;
            double cz = center.getZ() + Math.sin(cAngle) * outerRadius;
            int gy = world.getHighestBlockYAt((int) cx, (int) cz);
            for (double by = 0; by <= 4.0; by += 1.0) {
                world.spawnParticle(Particle.END_ROD, cx, gy + by, cz, 1, 0, 0, 0, 0.005);
            }
        }

        // 5. 4 Rotating Radial Energy Spokes linking Core to Outer Ring
        double spokeAngleOffset = ticks * 0.035;
        for (int s = 0; s < 4; s++) {
            double sa = (Math.PI / 2 * s) + spokeAngleOffset;
            for (double r = coreRadius + 0.5; r < outerRadius - 0.5; r += 1.5) {
                double sx = center.getX() + Math.cos(sa) * r;
                double sz = center.getZ() + Math.sin(sa) * r;
                int gy = world.getHighestBlockYAt((int) sx, (int) sz);
                world.spawnParticle(Particle.DUST, sx, gy + 0.12, sz, 1, 0, 0, 0, 0, spokeDust);
            }
        }
    }

    /**
     * Helper to render a dust chord between two ground locations.
     */
    private void renderGroundChord(World world, Location p1, Location p2, Particle.DustOptions dust) {
        if (p1 == null || p2 == null) return;
        Vector v = p2.toVector().subtract(p1.toVector());
        double len = v.length();
        v.normalize();
        for (double d = 0.8; d < len; d += 1.2) {
            double cx = p1.getX() + v.getX() * d;
            double cz = p1.getZ() + v.getZ() * d;
            int gy = world.getHighestBlockYAt((int) cx, (int) cz);
            world.spawnParticle(Particle.DUST, cx, gy + 0.12, cz, 1, 0, 0, 0, 0, dust);
        }
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

        Location center = getEventCenter(world);
        // 1. Shrink World Border to 25 blocks at Black Sun center
        border.setCenter(center.getX(), center.getZ());
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
        claimedSins.clear();
        fallImmunityPlayers.clear();
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

        Location spawnLoc = center.clone();
        int groundY = world.getHighestBlockYAt((int) spawnLoc.getX(), (int) spawnLoc.getZ());
        spawnLoc.setY(Math.max(groundY + 1.0, center.getY()));
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

                // Ready spiritual aura for aligned players (clean vertical energy, zero circular rings)
                for (UUID uuid : readyPlayers) {
                    Player rp = Bukkit.getPlayer(uuid);
                    if (rp != null && rp.isOnline()) {
                        if (ticks % 3 == 0) {
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, rp.getLocation().add(0, 0.1, 0), 1, 0.15, 0.05, 0.15, 0.01);
                        }
                        if (ticks % 6 == 0) {
                            world.spawnParticle(Particle.END_ROD, rp.getLocation().add(0, 1.2, 0), 1, 0.15, 0.3, 0.15, 0.01);
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
        Location center = getEventCenter(world);

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        final Map<UUID, Location> startSoulLocs = new HashMap<>();
        final Map<UUID, Double> soulAngles = new HashMap<>();

        final List<ItemDisplay> extractedSoulCores = new ArrayList<>();

        new BukkitRunnable() {
            int ticks = 0;
            final int veinTicks = 70; // 3.5 seconds of celestial sky rifts & trembling lightning tendrils
            final int pulseTicks = 20; // 1.0 second of soul detachment pulse
            final int driftDuration = 160; // 8.0 seconds of slow cinematic drift

            @Override
            public void run() {
                ticks++;

                // 1. Initial 3.5 seconds: Sky Rifts & Heavy Lightning Tendrils descending onto players
                if (ticks <= veinTicks) {
                    for (Player p : participants) {
                        if (!p.isOnline()) continue;
                        Location head = p.getLocation().add(0, 1.8, 0);

                        // Sky Rift at Y+14 above player
                        Location rift = head.clone().add(0, 12, 0);
                        world.spawnParticle(Particle.PORTAL, rift, 2, 0.4, 0.4, 0.4, 0.5);
                        world.spawnParticle(Particle.SCULK_SOUL, rift, 1, 0.2, 0.1, 0.2, 0.02);
                        if (ticks % 3 == 0) {
                            world.spawnParticle(Particle.ELECTRIC_SPARK, rift, 2, 0.3, 0.3, 0.3, 0.04);
                        }

                        // Clean Mathematical Particle Bolt descending to player's head:
                        // 1. Pure straight mathematical vertical core line
                        // 2. Double counter-rotating mathematical helix around core
                        // 3. Mathematical expanding cone / funnel on top (y >= 9)
                        for (double y = 0; y <= 14; y += 0.25) {
                            // Core straight line
                            Location corePt = new Location(world, head.getX(), head.getY() + y, head.getZ());
                            world.spawnParticle(Particle.DUST, corePt, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f));

                            // Mathematical Double Helix
                            double r = 0.35;
                            double a1 = (ticks * 0.25) + (y * 0.85);
                            double a2 = -(ticks * 0.25) + (y * 0.85) + Math.PI;
                            Location h1 = new Location(world, head.getX() + Math.cos(a1) * r, head.getY() + y, head.getZ() + Math.sin(a1) * r);
                            Location h2 = new Location(world, head.getX() + Math.cos(a2) * r, head.getY() + y, head.getZ() + Math.sin(a2) * r);
                            world.spawnParticle(Particle.DUST, h1, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(210, 240, 255), 1.3f));
                            world.spawnParticle(Particle.DUST, h2, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(180, 210, 255), 1.3f));

                            // Mathematical expanding cone on top (y >= 9)
                            if (y >= 9.0) {
                                double coneRadius = (y - 9.0) * 0.38;
                                for (int k = 0; k < 4; k++) {
                                    double ca = (k * Math.PI / 2.0) + (ticks * 0.08);
                                    Location conePt = new Location(world, head.getX() + Math.cos(ca) * coneRadius, head.getY() + y, head.getZ() + Math.sin(ca) * coneRadius);
                                    world.spawnParticle(Particle.END_ROD, conePt, 1, 0, 0, 0, 0.005);
                                }
                            }
                        }
                    }

                    if (ticks % 14 == 0) {
                        for (Player p : participants) {
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.95f, 1.5f + (ticks * 0.008f));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f + (ticks * 0.008f));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
                            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.6f);
                        }
                    }
                    return;
                }

                // 2. Pulse of soul detachment: physical glowing Soul Core rises from chest
                int extractionTick = ticks - veinTicks;
                if (extractionTick <= pulseTicks) {
                    if (extractionTick == 1) {
                        int idx = 0;
                        for (Player p : participants) {
                            if (p.isOnline()) {
                                Location chest = p.getLocation().add(0, 1.2, 0);
                                startSoulLocs.put(p.getUniqueId(), chest);
                                double baseAngle = idx * (2 * Math.PI / Math.max(1, participants.size()));
                                soulAngles.put(p.getUniqueId(), baseAngle);

                                // Spawn floating glowing Soul Core entity
                                ItemDisplay core = world.spawn(chest, ItemDisplay.class, d -> {
                                    d.setItemStack(new ItemStack(Material.NETHER_STAR));
                                    d.setTransformation(new Transformation(
                                            new Vector3f(0, 0, 0),
                                            new AxisAngle4f(0, 0, 1, 0),
                                            new Vector3f(1.4f, 1.4f, 1.4f),
                                            new AxisAngle4f(0, 0, 1, 0)
                                    ));
                                    d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                                });
                                extractedSoulCores.add(core);
                                activeSoulDisplays.add(core);

                                playRetroCrystalChime(p.getLocation(), 1.25f, 1.3f);
                                p.playSound(p.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1.0f, 0.7f);
                                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
                            }
                            idx++;
                        }
                    }

                    for (Player p : participants) {
                        Location sLoc = startSoulLocs.get(p.getUniqueId());
                        if (sLoc != null) {
                            world.spawnParticle(Particle.DUST, sLoc, 4, 0.2, 0.2, 0.2, 0,
                                    new Particle.DustOptions(Color.fromRGB(235, 245, 255), 1.8f));
                            world.spawnParticle(Particle.FIREWORK, sLoc, 2, 0.05, 0.05, 0.05, 0.02);
                        }
                    }
                    return;
                }

                // 3. Guaranteed SLOW Cinematic Drift to Center (0, 0) with Zero-G Shields
                int currentDrift = extractionTick - pulseTicks;
                double t = Math.min(1.0, currentDrift / (double) driftDuration);

                int coreIdx = 0;
                for (Player p : participants) {
                    Location start = startSoulLocs.get(p.getUniqueId());
                    if (start == null) continue;

                    // Interpolate base trajectory from start to center
                    double lx = start.getX() + (center.getX() - start.getX()) * t;
                    double ly = start.getY() + (center.getY() - start.getY()) * t;
                    double lz = start.getZ() + (center.getZ() - start.getZ()) * t;

                    // High arc lift (rises up to 4.0 blocks and arcs into center)
                    double arcY = Math.sin(t * Math.PI) * 4.0;

                    // Spiral swirl converging into center
                    double baseAngle = soulAngles.getOrDefault(p.getUniqueId(), 0.0);
                    double swirlAngle = baseAngle + (t * 4.0 * Math.PI);
                    double spiralRadius = (1.0 - t) * 3.5 + 0.3;
                    double sx = Math.cos(swirlAngle) * spiralRadius;
                    double sz = Math.sin(swirlAngle) * spiralRadius;

                    Location soulPt = new Location(world, lx + sx, ly + arcY, lz + sz);
                    Location twinPt = new Location(world, lx - sx * 0.7, ly + arcY, lz - sz * 0.7);

                    // Move and axially spin physical Soul Core entity
                    if (coreIdx < extractedSoulCores.size()) {
                        ItemDisplay core = extractedSoulCores.get(coreIdx);
                        if (core != null && core.isValid()) {
                            core.teleport(soulPt);
                            core.setTransformation(new Transformation(
                                    new Vector3f(0, 0, 0),
                                    new AxisAngle4f((float) (currentDrift * 0.15 + coreIdx), 0, 1, 0),
                                    new Vector3f(1.1f, 1.1f, 1.1f),
                                    new AxisAngle4f(0, 0, 1, 0)
                            ));
                        }
                    }
                    coreIdx++;

                    // Rich celestial particle streams
                    world.spawnParticle(Particle.DUST, soulPt, 3, 0.08, 0.08, 0.08, 0,
                            new Particle.DustOptions(Color.fromRGB(240, 250, 255), 1.8f));
                    world.spawnParticle(Particle.END_ROD, soulPt, 1, 0.02, 0.02, 0.02, 0.01);
                    world.spawnParticle(Particle.DUST, twinPt, 2, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.4f));

                    // Sci-Fi Zero-G Spherical Tri-Gyro Shield orbiting player
                    if (p.isOnline()) {
                        Location pLoc = p.getLocation().add(0, 1.0, 0);
                        double r1 = Math.toRadians(ticks * 8);
                        double r2 = Math.toRadians(-ticks * 6);
                        double r3 = Math.toRadians(ticks * 5);
                        double sRad = 1.55;

                        for (int deg = 0; deg < 360; deg += 45) {
                            double a = Math.toRadians(deg);
                            // Equatorial Gyro Ring (XZ)
                            Location ring1 = pLoc.clone().add(Math.cos(a + r1) * sRad, 0, Math.sin(a + r1) * sRad);
                            world.spawnParticle(Particle.DUST, ring1, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.3f));

                            // Vertical Pitch Gyro Ring (XY tilted)
                            Location ring2 = pLoc.clone().add(Math.cos(a + r2) * sRad, Math.sin(a + r2) * sRad, 0);
                            world.spawnParticle(Particle.DUST, ring2, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 230, 255), 1.1f));

                            // Tilted Roll Gyro Ring (YZ tilted)
                            Location ring3 = pLoc.clone().add(0, Math.cos(a + r3) * sRad, Math.sin(a + r3) * sRad);
                            world.spawnParticle(Particle.DUST, ring3, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(180, 210, 255), 1.1f));
                        }

                        // Orbiting kinetic shield nodes
                        Location node1 = pLoc.clone().add(Math.cos(r1) * sRad, Math.sin(r1) * 0.4, Math.sin(r1) * sRad);
                        Location node2 = pLoc.clone().add(-Math.cos(r1) * sRad, -Math.sin(r1) * 0.4, -Math.sin(r1) * sRad);
                        world.spawnParticle(Particle.END_ROD, node1, 1, 0, 0, 0, 0.01);
                        world.spawnParticle(Particle.END_ROD, node2, 1, 0, 0, 0, 0.01);
                    }
                }

                // Center nexus ground hums and crackles as souls draw close
                if (t > 0.25) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add((Math.random() - 0.5) * 2.0, -1.8, (Math.random() - 0.5) * 2.0), 2, 0, 0.04, 0, 0.02);
                    world.spawnParticle(Particle.PORTAL, center, 3, 0.5, 0.5, 0.5, 0.4);
                }

                if (currentDrift % 16 == 0) {
                    float pitch = 0.8f + (float) (t * 0.85);
                    playRetroCrystalChime(center, pitch, 1.35f);
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, pitch * 0.85f);
                }

                // Completed 8-second slow travel -> Singularity triggers blinding flash!
                if (currentDrift >= driftDuration) {
                    cancel();
                    for (ItemDisplay core : extractedSoulCores) {
                        if (core != null && core.isValid()) core.remove();
                    }
                    extractedSoulCores.clear();
                    triggerBlindingFlashAndStar(center);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Phase 3: Blinding Convergence & Black Sun Manifestation:
     * - Screen shake at lowest level (Hardness 1)
     * - Screen vintage of the Sin color before it appears
     * - Single straight unreversed particle bolt ascending from ground to apex, expanding on top
     * - Flash-fade white when it manifests
     * - Rotates Celestial Black Sun cycling through all 7 Sin colors (zero cross circles)
     */
    private void triggerBlindingFlashAndStar(Location center) {
        this.state = EventState.STAR_CYCLE;
        World world = center.getWorld();
        if (world == null) return;

        // 1. Screen shake at lowest level (Hardness 1)
        com.churchsmp.util.CinematicVFXUtil.triggerScreenShakeAll(1, 35, plugin);

        // 2. Screen vintage of the sin color before it appears (Wrath #ff1a1a)
        for (Player p : Bukkit.getOnlinePlayers()) {
            com.churchsmp.util.CinematicVFXUtil.flashScreen(p, "#ff1a1a", 30, 220, 600);
        }

        // 3. Particle bolt: single straight line that expands on top (unreversed)
        int groundY = world.getHighestBlockYAt((int) center.getX(), (int) center.getZ());
        double startY = Math.min(groundY, center.getY() - 10);
        double endY = center.getY();

        new BukkitRunnable() {
            int boltTicks = 0;
            final int maxBoltTicks = 25; // 1.25s ascending

            @Override
            public void run() {
                boltTicks++;
                double progress = Math.min(1.0, (double) boltTicks / maxBoltTicks);
                double currentY = startY + (endY - startY) * progress;

                // Single straight vertical line ascending to apex
                for (double y = startY; y <= currentY; y += 0.12) {
                    Location pt = new Location(world, center.getX(), y, center.getZ());
                    world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 30, 40), 1.6f));
                    if (Math.random() < 0.2) {
                        world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.005);
                    }
                }

                if (boltTicks >= maxBoltTicks) {
                    cancel();

                    // Expand on top! (Coronal canopy / starburst)
                    com.churchsmp.util.VFXUtil.drawRadialLightningBurst(center, 12, 4.5, Color.fromRGB(255, 40, 50), 1.6f);

                    // Flash-fade white when it does!
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        com.churchsmp.util.CinematicVFXUtil.flashScreen(p, "#ffffff", 20, 250, 700);
                    }

                    world.spawnParticle(Particle.FLASH, center, 14, 1.0, 1.0, 1.0, 0, Color.WHITE);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                    world.playSound(center, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.65f);
                    playRetroCrystalChime(center, 1.5f, 2.0f);

                    startBlackSunColorCycle(center);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startBlackSunColorCycle(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        final SinGemType[] sins = SinGemType.BASE_SINS;
        final double ringRadius = 9.0;
        final double angleStep = (2 * Math.PI) / sins.length;

        removeBlackSun();
        blackSunDisplay = spawnBlackSunDisplay(center, CMD_BLACK_SUN_WRATH, 4.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final int starDuration = 140; // 7.0 seconds of cycling
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

                // Render 3D Celestial Black Sun ItemDisplay with smooth continuous rotation
                if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                    float rot = (float) (ticks * 0.06);
                    updateBlackSunTransform(blackSunDisplay, rot, 0.25f, 4.5f);
                }

                // Render subtle ambient celestial aura particles (Zero cross-circles!)
                double rot = ticks * 0.08;
                com.churchsmp.util.VFXUtil.drawCelestialBlackSun(center, 4.2, rot, c, Color.fromRGB(255, 240, 160));

                if (sinIdx != lastSinIndex) {
                    lastSinIndex = sinIdx;

                    if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                        blackSunDisplay.setItemStack(createBlackSunItem(getSinBlackSunCMD(currentSin)));
                    }

                    Title sinTitle = Title.title(
                            miniMessage.deserialize("<bold><color:" + currentSin.getColor().asHexString() + ">" + currentSin.getDisplayName() + "</color></bold>"),
                            miniMessage.deserialize("<gray>Sin Essence Manifesting...</gray>"),
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
                        case SIN -> 0.50f;
                        case ZEN -> 1.60f;
                    };

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(sinTitle);
                        playRetroCrystalChime(p.getLocation(), sinPitch, 1.25f);
                        com.churchsmp.util.CinematicVFXUtil.triggerSinScreenFX(p, currentSin, plugin);
                    }

                    triggerSinColorWave(center, currentSin);
                }

                // In the 9-block ring, faint colored souls begin to slowly revolve in orbit (zero cross-circles)
                double soulOrbit = rot * 0.4;
                for (int i = 0; i <= sinIdx; i++) {
                    SinGemType sin = sins[i];
                    double angle = (i * angleStep) + soulOrbit;
                    double sx = center.getX() + Math.cos(angle) * ringRadius;
                    double sz = center.getZ() + Math.sin(angle) * ringRadius;
                    double sy = Math.max(world.getHighestBlockYAt((int) sx, (int) sz) + 1.2, center.getY()) + Math.sin((ticks * 0.1) + i) * 0.2;

                    Color sc = Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    );
                    Location soulLoc = new Location(world, sx, sy, sz);
                    world.spawnParticle(Particle.DUST, soulLoc, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(sc, 1.4f));
                    if (ticks % 3 == 0) {
                        world.spawnParticle(Particle.END_ROD, soulLoc, 1, 0, 0, 0, 0.005);
                    }
                }

                if (ticks >= starDuration) {
                    cancel();
                    spawnSinSoulsRing(center);
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    /**
     * Renders a colossal 3D Gyroscopic Hyper-Star Anomaly with 4 multi-axis precessing gimbal rings,
     * a blazing spinning quantum vortex core, 4 high-speed orbiting electron satellites, and 3D star chords.
     */
    private void render3DGyroscopicHyperStar(Location center, double rot, double radius, Particle.DustOptions dust, Color c) {
        World world = center.getWorld();
        if (world == null) return;

        // 1. Quantum Singularity Core (Spinning Micro-Vortex at 3.5x speed)
        double coreRot = rot * 3.5;
        for (int i = 0; i < 4; i++) {
            double ca = coreRot + (Math.PI / 2 * i);
            Location cPt = center.clone().add(Math.cos(ca) * 0.5, Math.sin(ca * 2) * 0.25, Math.sin(ca) * 0.5);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, cPt, 1, 0, 0.01, 0, 0.005);
        }
        world.spawnParticle(Particle.DUST, center, 4, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(Color.WHITE, 1.8f));
        world.spawnParticle(Particle.END_ROD, center, 2, 0.1, 0.1, 0.1, 0.02);

        int ringPoints = 32;

        // 2. Ring 1 (Horizontal Equatorial Ring - XZ Plane, Clockwise rot)
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2 * Math.PI * i) / ringPoints + rot;
            Location pt = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }

        // 3. Ring 2 (Precessing Pitch Gimbal Ring, Tilted 45 deg, Counter-rot)
        double rot2 = -rot * 1.35;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2 * Math.PI * i) / ringPoints + rot2;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            double tx = cos * 0.7071;
            double ty = sin;
            double tz = cos * 0.7071;
            Location pt = center.clone().add(tx, ty, tz);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }

        // 4. Ring 3 (Precessing Roll Gimbal Ring, Tilted -45 deg, Counter-rot)
        double rot3 = rot * 1.15;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2 * Math.PI * i) / ringPoints + rot3;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            double tx = -cos * 0.7071;
            double ty = sin;
            double tz = cos * 0.7071;
            Location pt = center.clone().add(tx, ty, tz);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }

        // 5. Ring 4 (Vertical Polar Meridian Ring - YZ Plane, Fast rot)
        double rot4 = rot * 1.6;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2 * Math.PI * i) / ringPoints + rot4;
            Location pt = center.clone().add(0, Math.sin(angle) * radius, Math.cos(angle) * radius);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }

        // 6. 4 High-Speed Orbiting Electron Satellites (looping through gimbal rings at 2.5x speed)
        double satRot = rot * 2.5;
        for (int s = 0; s < 4; s++) {
            double sa = satRot + (Math.PI / 2 * s);
            double sx = Math.cos(sa) * (radius * 1.05);
            double sy = Math.sin(sa * 2) * (radius * 0.6);
            double sz = Math.sin(sa) * (radius * 1.05);
            Location satLoc = center.clone().add(sx, sy, sz);
            world.spawnParticle(Particle.END_ROD, satLoc, 1, 0, 0, 0, 0.005);
            if (s % 2 == 0) {
                world.spawnParticle(Particle.FIREWORK, satLoc, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        // 7. 3D Sacred Star Chords connecting the gyroscopic vertices
        int starPoints = 8;
        double step = (2 * Math.PI) / starPoints;
        for (int i = 0; i < starPoints; i++) {
            double a = rot + (i * step);
            Vector tip = new Vector(Math.cos(a) * (radius * 1.15), Math.sin(a * 2) * 1.2, Math.sin(a) * (radius * 1.15));
            Location starNode = center.clone().add(tip);
            world.spawnParticle(Particle.END_ROD, starNode, 1, 0, 0, 0, 0.005);
        }
    }

    /**
     * Detonates an expanding spherical color wave washing across the 25-block sanctuary.
     */
    private void triggerSinColorWave(Location center, SinGemType sin) {
        World world = center.getWorld();
        if (world == null) return;

        Color sc = Color.fromRGB(
                (sin.getColor().value() >> 16) & 0xFF,
                (sin.getColor().value() >> 8) & 0xFF,
                sin.getColor().value() & 0xFF
        );

        new BukkitRunnable() {
            int waveTick = 0;
            final int maxWave = 12;

            @Override
            public void run() {
                waveTick++;
                if (waveTick > maxWave) {
                    cancel();
                    return;
                }

                double progress = (double) waveTick / maxWave;
                double waveRadius = 1.0 + progress * 13.0; // Expands to 14.0 blocks
                Particle.DustOptions waveDust = new Particle.DustOptions(sc, (float) (2.0 * (1.0 - progress * 0.5)));

                int pts = 36;
                for (int i = 0; i < pts; i++) {
                    double angle = (2 * Math.PI * i) / pts;
                    double wx = center.getX() + Math.cos(angle) * waveRadius;
                    double wz = center.getZ() + Math.sin(angle) * waveRadius;
                    int gy = world.getHighestBlockYAt((int) wx, (int) wz);
                    Location wLoc = new Location(world, wx, gy + 0.15, wz);
                    world.spawnParticle(Particle.DUST, wLoc, 1, 0, 0, 0, 0, waveDust);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Phase 4: 7 Spinning Sin Souls manifest within the 25-block Sanctuary.
     * Uses modern Interaction entities to guarantee 100% clickability and responsive selection.
     */
    private void spawnSinSoulsRing(Location center) {
        this.state = EventState.SIN_CHOICE;
        World world = center.getWorld();

        SinGemType[] sins = SinGemType.BASE_SINS;
        double ringRadius = 9.0; // 9 blocks radius (fits safely inside 25-block diameter sanctuary)
        double angleStep = (2 * Math.PI) / sins.length;

        sinSoulLocations.clear();
        cleanupSoulDisplays();
        if (blackSunDisplay == null || !blackSunDisplay.isValid()) {
            blackSunDisplay = spawnBlackSunDisplay(center, CMD_BLACK_SUN_BASE, 4.5f);
        }

        for (int i = 0; i < sins.length; i++) {
            SinGemType sin = sins[i];
            double angle = i * angleStep;
            double x = center.getX() + Math.cos(angle) * ringRadius;
            double z = center.getZ() + Math.sin(angle) * ringRadius;
            double y = Math.max(world.getHighestBlockYAt((int) x, (int) z) + 1.2, center.getY());

            double dx = x - center.getX();
            double dz = z - center.getZ();
            float yawAway = (float) Math.toDegrees(Math.atan2(-dx, dz));

            Location soulLoc = new Location(world, x, y, z, yawAway, 0f);
            sinSoulLocations.put(sin, soulLoc);

            // 1. Floating ItemDisplay (visual icon with full brightness)
            ItemDisplay id = world.spawn(soulLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(sin.getIconMaterial()));
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 1, 0, 0),
                        new Vector3f(1.5f, 1.5f, 1.5f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                d.getPersistentDataContainer().set(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING, sin.name());
            });
            activeSoulDisplays.add(id);
            sinItemDisplays.put(sin, id);

            // 2. Floating TextDisplay holographic title
            TextDisplay td = world.spawn(soulLoc.clone().add(0, 1.4, 0), TextDisplay.class, t -> {
                t.text(miniMessage.deserialize("<bold><color:" + sin.getColor().asHexString() + ">✦ " + sin.getDisplayName() + " ✦</color></bold>\n<yellow><bold>[Right-Click to Choose]</bold></yellow>"));
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            });
            activeTextDisplays.add(td);
            sinTextDisplays.put(sin, td);

            // 3. Dedicated Interaction Hitbox Entity (Fixes display entities having no hitbox!)
            Interaction hitbox = world.spawn(soulLoc.clone().subtract(0, 0.4, 0), Interaction.class, it -> {
                it.setInteractionWidth(2.2f);
                it.setInteractionHeight(2.6f);
                it.setResponsive(true);
                it.getPersistentDataContainer().set(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING, sin.name());
            });
            activeInteractions.add(hitbox);
            sinInteractions.put(sin, hitbox);
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

        // Active planetary orbital animation & proximity prompt task
        this.mainLoopTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (state != EventState.SIN_CHOICE) {
                    cancel();
                    return;
                }
                ticks++;

                // 1. Planetary Orbital Revolution & Slow Outward Drift:
                // Gems slowly flip away from the black sun as radius expands slowly
                double driftRadius = ringRadius + (ticks * 0.005);
                double orbitAngle = ticks * 0.02; // Smooth continuous orbital revolution
                for (int i = 0; i < sins.length; i++) {
                    SinGemType sin = sins[i];
                    if (claimedSins.contains(sin)) continue; // Disappeared when chosen!

                    double angle = (i * angleStep) + orbitAngle;
                    double x = center.getX() + Math.cos(angle) * driftRadius;
                    double z = center.getZ() + Math.sin(angle) * driftRadius;
                    double groundY = world.getHighestBlockYAt((int) x, (int) z);
                    double baseY = Math.max(groundY + 1.2, center.getY());
                    double hoverY = baseY + Math.sin((ticks * 0.12) + (i * 0.9)) * 0.28;

                    double dx = x - center.getX();
                    double dz = z - center.getZ();
                    float yawAway = (float) Math.toDegrees(Math.atan2(-dx, dz));

                    Location newSoulLoc = new Location(world, x, hoverY, z, yawAway, 0f);
                    sinSoulLocations.put(sin, newSoulLoc);

                    // Move and slowly flip ItemDisplay in 3D facing away from Black Sun
                    ItemDisplay id = sinItemDisplays.get(sin);
                    if (id != null && id.isValid()) {
                        id.teleport(newSoulLoc);
                        float flipAngle = (float) (ticks * 0.06); // slow tumble/flip on pitch
                        id.setTransformation(new Transformation(
                                new Vector3f(0, 0, 0),
                                new AxisAngle4f(flipAngle, 1, 0, 0),
                                new Vector3f(1.5f, 1.5f, 1.5f),
                                new AxisAngle4f(0, 0, 1, 0)
                        ));
                    }

                    // Move TextDisplay holographic title
                    TextDisplay td = sinTextDisplays.get(sin);
                    if (td != null && td.isValid()) {
                        td.teleport(newSoulLoc.clone().add(0, 1.4, 0));
                    }

                    // Move Interaction hitbox
                    Interaction it = sinInteractions.get(sin);
                    if (it != null && it.isValid()) {
                        it.teleport(newSoulLoc.clone().subtract(0, 0.4, 0));
                    }
                }

                // 2. Render revolving Elemental Vortex Pillars & Sweeping Sacred Light Beams
                for (Map.Entry<SinGemType, Location> entry : sinSoulLocations.entrySet()) {
                    SinGemType sin = entry.getKey();
                    if (claimedSins.contains(sin)) continue;
                    Location loc = entry.getValue();

                    Color c = Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    );

                    // Towering Counter-Rotating Double-Helix Elemental Pillar
                    renderSinElementalPillar(world, loc, sin, ticks, c);

                    // Sweeping Sacred Light Beam linking rotating Sin Soul to central 3D Star
                    if (ticks % 2 == 0) {
                        renderLightBeam(world, loc, center, c);
                    }
                }

                // 3. Proximity check for action bar inspection HUD
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (attunedPlayers.contains(p.getUniqueId())) continue;

                    SinGemType nearestSin = null;
                    double nearestDistSq = Double.MAX_VALUE;

                    for (Map.Entry<SinGemType, Location> entry : sinSoulLocations.entrySet()) {
                        if (claimedSins.contains(entry.getKey())) continue;
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

                // 4. Keep central Celestial Black Sun ItemDisplay rotating smoothly (Zero cross-circles!)
                if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                    float rot = (float) (ticks * 0.06);
                    updateBlackSunTransform(blackSunDisplay, rot, 0.25f, 4.5f);
                }
                com.churchsmp.util.VFXUtil.drawCelestialBlackSun(center, 4.2, ticks * 0.08, Color.fromRGB(240, 40, 50), Color.fromRGB(255, 230, 140));
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
     * Renders a towering elemental aura pillar and vortex unique to each of the 7 Sins.
     */
    private void renderSinElementalPillar(World world, Location loc, SinGemType sin, int ticks, Color c) {
        Location base = loc.clone().subtract(0, 0.8, 0);

        // 1. Dual Counter-Rotating Ground Base Gears
        for (int d = 0; d < 360; d += 60) {
            // Inner gear (Clockwise)
            double a1 = Math.toRadians(d + ticks * 6);
            Location g1 = base.clone().add(Math.cos(a1) * 1.0, 0.05, Math.sin(a1) * 1.0);
            world.spawnParticle(Particle.DUST, g1, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.2f));

            // Outer gear (Counter-Clockwise)
            double a2 = Math.toRadians(d - ticks * 4);
            Location g2 = base.clone().add(Math.cos(a2) * 1.6, 0.05, Math.sin(a2) * 1.6);
            world.spawnParticle(Particle.DUST, g2, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1.0f));
        }

        // 2. Counter-Rotating Double-Helix Energy Conduit
        for (double y = 0; y <= 5.5; y += 0.6) {
            double vRad = 0.6 + (y * 0.12);

            // Strand A: Clockwise ascending elemental flow
            double angleA = Math.toRadians((ticks * 8) + (y * 48));
            Location ptA = base.clone().add(Math.cos(angleA) * vRad, y, Math.sin(angleA) * vRad);

            // Themed elemental particle signature on Strand A
            switch (sin) {
                case WRATH -> {
                    world.spawnParticle(Particle.FLAME, ptA, 1, 0.02, 0.05, 0.02, 0.01);
                    if (Math.random() < 0.25) world.spawnParticle(Particle.LAVA, ptA, 1, 0, 0, 0, 0);
                }
                case PRIDE -> {
                    world.spawnParticle(Particle.FIREWORK, ptA, 1, 0.02, 0.04, 0.02, 0.02);
                    if (Math.random() < 0.3) world.spawnParticle(Particle.END_ROD, ptA, 1, 0, 0, 0, 0.01);
                }
                case LUST -> {
                    world.spawnParticle(Particle.CHERRY_LEAVES, ptA, 1, 0.05, 0.05, 0.05, 0.01);
                    if (Math.random() < 0.3) world.spawnParticle(Particle.PORTAL, ptA, 1, 0, 0, 0, 0.5);
                }
                case ENVY -> {
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, ptA, 1, 0.03, 0.05, 0.03, 0.02);
                    if (Math.random() < 0.25) world.spawnParticle(Particle.HAPPY_VILLAGER, ptA, 1, 0.05, 0.05, 0.05, 0.01);
                }
                case GLUTTONY -> {
                    world.spawnParticle(Particle.SCULK_SOUL, ptA, 1, 0.03, 0.05, 0.03, 0.01);
                    if (Math.random() < 0.25) world.spawnParticle(Particle.SQUID_INK, ptA, 1, 0, 0.02, 0, 0.01);
                }
                case GREED -> {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, ptA, 1, 0.04, 0.04, 0.04, 0.02);
                    if (Math.random() < 0.3) world.spawnParticle(Particle.DUST, ptA, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f));
                }
                case SLOTH -> {
                    world.spawnParticle(Particle.SNOWFLAKE, ptA, 1, 0.03, 0.04, 0.03, 0.01);
                    if (Math.random() < 0.25) world.spawnParticle(Particle.CLOUD, ptA, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            world.spawnParticle(Particle.DUST, ptA, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.3f));

            // Strand B: Counter-Clockwise containment field
            double angleB = Math.toRadians((-ticks * 8) - (y * 48) + 180);
            Location ptB = base.clone().add(Math.cos(angleB) * vRad, y, Math.sin(angleB) * vRad);
            world.spawnParticle(Particle.DUST, ptB, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1.0f));
            if (Math.random() < 0.3) {
                world.spawnParticle(Particle.END_ROD, ptB, 1, 0, 0, 0, 0.005);
            }

            // Transverse energy spark connecting strands
            if (Math.random() < 0.18) {
                Location mid = ptA.clone().add(ptB).multiply(0.5);
                world.spawnParticle(Particle.ELECTRIC_SPARK, mid, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    /**
     * Renders a glowing starlight beam connecting a Sin Soul monument to the central star core.
     */
    private void renderLightBeam(World world, Location from, Location to, Color c) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        dir.normalize();

        Particle.DustOptions beamDust = new Particle.DustOptions(c, 1.1f);
        for (double d = 0.8; d < dist - 0.5; d += 1.2) {
            Location p = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, beamDust);
            if (Math.random() < 0.15) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0.005);
            }
        }
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

        if (claimedSins.contains(sin)) {
            player.sendMessage(miniMessage.deserialize("<red>✦ " + sin.getDisplayName() + " has already been claimed by another soul! ✦</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
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

        // 1. Pillar of lightning tempest repeatedly striking down over 24 ticks
        new BukkitRunnable() {
            int strikes = 0;

            @Override
            public void run() {
                if (strikes >= 6) {
                    cancel();
                    return;
                }
                strikes++;

                world.strikeLightningEffect(origin);
                Location satLoc = origin.clone().add((Math.random() - 0.5) * 3.0, 0, (Math.random() - 0.5) * 3.0);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, satLoc, 1);
                world.spawnParticle(Particle.FLASH, origin, 2, 0.5, 0.5, 0.5, 0, Color.WHITE);

                world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 0.85f + (strikes * 0.04f));
                world.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.65f);
                world.playSound(origin, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.9f);

                // Vertical lightning pillar particles
                for (double y = 0; y <= 32; y += 0.8) {
                    Location pillarPt = origin.clone().add(
                            (Math.random() - 0.5) * 0.8,
                            y,
                            (Math.random() - 0.5) * 0.8
                    );
                    world.spawnParticle(Particle.END_ROD, pillarPt, 1, 0, 0, 0, 0.02);
                    world.spawnParticle(Particle.DUST, pillarPt, 2, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(sinColor, 1.8f));
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);

        // 2. Terrain-shattering ground shockwave with flying block debris and dark gradient to black
        new BukkitRunnable() {
            int waveTicks = 0;
            final int maxWaveTicks = 28; // 1.4s duration

            @Override
            public void run() {
                waveTicks++;
                if (waveTicks > maxWaveTicks) {
                    cancel();
                    return;
                }

                double progress = (double) waveTicks / maxWaveTicks; // 0.0 to 1.0
                double radius = 0.5 + Math.pow(progress, 0.75) * 11.5; // Expands outward from 0.5 to 12.0 blocks

                // Interpolate color from vibrant Sin Color -> deep dark shade -> pitch black (0, 0, 0)
                int r = (int) (sinColor.getRed() * Math.max(0.0, 1.0 - (progress * 1.15)));
                int g = (int) (sinColor.getGreen() * Math.max(0.0, 1.0 - (progress * 1.15)));
                int b = (int) (sinColor.getBlue() * Math.max(0.0, 1.0 - (progress * 1.15)));
                Color ringColor = Color.fromRGB(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));

                float particleSize = (float) Math.max(0.4f, 2.4f * (1.0 - (progress * 0.6)));
                Particle.DustOptions ringDust = new Particle.DustOptions(ringColor, particleSize);
                Particle.DustOptions innerNeonDust = new Particle.DustOptions(sinColor, 1.6f);

                // Render shockwave ripple on terrain surface
                int points = 56;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double px = origin.getX() + Math.cos(angle) * radius;
                    double pz = origin.getZ() + Math.sin(angle) * radius;
                    int groundY = world.getHighestBlockYAt((int) px, (int) pz);
                    Location ringLoc = new Location(world, px, groundY + 0.12, pz);

                    // Outer expanding shockwave wave
                    world.spawnParticle(Particle.DUST, ringLoc, 1, 0, 0, 0, 0, ringDust);

                    // Concentric inner neon ring
                    if (radius > 1.2 && i % 2 == 0) {
                        double inX = origin.getX() + Math.cos(angle) * (radius - 0.7);
                        double inZ = origin.getZ() + Math.sin(angle) * (radius - 0.7);
                        int inGy = world.getHighestBlockYAt((int) inX, (int) inZ);
                        world.spawnParticle(Particle.DUST, inX, inGy + 0.12, inZ, 1, 0, 0, 0, 0, innerNeonDust);
                    }

                    // Explosive terrain debris chunks flying up
                    if (i % 6 == 0) {
                        world.spawnParticle(Particle.BLOCK, ringLoc.clone().add(0, 0.35, 0), 2, 0.1, 0.35, 0.1, 0.05,
                                Material.DEEPSLATE.createBlockData());
                        world.spawnParticle(Particle.BLOCK, ringLoc.clone().add(0, 0.25, 0), 2, 0.1, 0.25, 0.1, 0.04,
                                Material.STONE.createBlockData());
                    }

                    // Dark rim shadow / smoke dissipation
                    if (progress > 0.3 && i % 4 == 0) {
                        world.spawnParticle(Particle.SQUID_INK, ringLoc, 1, 0.03, 0.04, 0.03, 0.01);
                    }
                    if (progress > 0.45 && i % 3 == 0) {
                        world.spawnParticle(Particle.LARGE_SMOKE, ringLoc, 1, 0.02, 0.04, 0.02, 0.02);
                    }
                }

                if (waveTicks == 1) {
                    world.spawnParticle(Particle.SONIC_BOOM, origin.clone().add(0, 0.5, 0), 1);
                    world.playSound(origin, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.4f, 0.5f);
                    world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.7f);
                    world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
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

        if (claimedSins.contains(sin)) {
            player.sendMessage(miniMessage.deserialize("<red>✦ " + sin.getDisplayName() + " has already been claimed by another soul! ✦</red>"));
            return false;
        }

        claimedSins.add(sin);
        attunedPlayers.add(player.getUniqueId());
        playerChosenSins.put(player.getUniqueId(), sin);

        // Despawn this Sin's displays immediately so no one else can take it
        ItemDisplay id = sinItemDisplays.remove(sin);
        if (id != null && id.isValid()) {
            Location loc = id.getLocation();
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.2, 0.2, 0.01);
            Color c = Color.fromRGB(
                    (sin.getColor().value() >> 16) & 0xFF,
                    (sin.getColor().value() >> 8) & 0xFF,
                    sin.getColor().value() & 0xFF
            );
            loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(c, 1.8f));
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_EYE_DEATH, 1.5f, 0.7f);
            id.remove();
        }
        TextDisplay td = sinTextDisplays.remove(sin);
        if (td != null && td.isValid()) td.remove();
        Interaction it = sinInteractions.remove(sin);
        if (it != null && it.isValid()) it.remove();
        Location soulLoc = sinSoulLocations.remove(sin);

        // Attune via SinGemManager
        plugin.getSinGemManager().forceAttune(player, sin);
        plugin.getSinGemManager().giveGemToPlayer(player, sin);

        // Grant Invisibility for 60 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0, false, false, true));

        // Screen pop & Title
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        com.churchsmp.util.CinematicVFXUtil.triggerSinScreenFX(player, sin, plugin);
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
        triggerSinPillarAndShockwave(soulLoc != null ? soulLoc : player.getLocation(), sin);

        Bukkit.broadcast(miniMessage.deserialize("<light_purple>✦ <bold>" + player.getName() + "</bold> has embraced the Sin of " + sin.getDisplayName() + "! (" + claimedSins.size() + "/" + Math.min(SinGemType.BASE_SINS.length, Bukkit.getOnlinePlayers().size()) + ")</light_purple>"));

        // Check if all sins are chosen or all online players are attuned
        int totalOnline = Bukkit.getOnlinePlayers().size();
        if (claimedSins.size() >= SinGemType.BASE_SINS.length || attunedPlayers.size() >= totalOnline) {
            World world = player.getWorld();
            Location center = getEventCenter(world);
            triggerVoidClimaxAndLaunch(center);
        }

        return true;
    }

    /**
     * Auto-assigns remaining un-attuned players to ensure the event never halts indefinitely.
     */
    private void autoAssignRemainingSins() {
        if (state != EventState.SIN_CHOICE) return;

        List<SinGemType> availableSins = new ArrayList<>();
        for (SinGemType s : SinGemType.BASE_SINS) {
            if (!claimedSins.contains(s)) {
                availableSins.add(s);
            }
        }
        Collections.shuffle(availableSins);

        int sinIdx = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!attunedPlayers.contains(p.getUniqueId()) && sinIdx < availableSins.size()) {
                SinGemType assign = availableSins.get(sinIdx);
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

        World world = (eventWorld != null) ? eventWorld : Bukkit.getWorlds().get(0);
        Location center = getEventCenter(world);

        if (state == EventState.READY_CHECK) {
            readyPlayers.clear();
            for (Player p : Bukkit.getOnlinePlayers()) readyPlayers.add(p.getUniqueId());
            triggerSoulExtraction();
        } else if (state == EventState.SOUL_EXTRACTION || state == EventState.STAR_CYCLE) {
            spawnSinSoulsRing(center);
        } else if (state == EventState.SIN_CHOICE) {
            autoAssignRemainingSins();
        } else if (state == EventState.VOID_CLIMAX) {
            executeGrandLaunch(center);
        }
    }

    /**
     * Phase 5: Void Climax Sequence.
     * Sequence:
     * 1. Sun replaced by Black Hole singularity (Ticks 0-50).
     * 2. Blinding White State (Ticks 51-80) -> PUSH EVERYONE OUT at Tick 65!
     * 3. Reversal: PULL BACK IN (Ticks 81-95) with inward gravitational vortex.
     * 4. BECOME BLACK with vignette of black (Ticks 96-120).
     * 5. At Tick 120: Launch everyone to Y: 300 with untakeoffable Elytra and fall damage immunity!
     */
    private void triggerVoidClimaxAndLaunch(Location center) {
        this.state = EventState.VOID_CLIMAX;
        if (mainLoopTask != null) mainLoopTask.cancel();
        if (autoTimeoutTask != null) autoTimeoutTask.cancel();
        cleanupSoulDisplays();

        World world = center.getWorld();
        if (world == null) return;

        if (blackSunDisplay == null || !blackSunDisplay.isValid()) {
            blackSunDisplay = spawnBlackSunDisplay(center, CMD_BLACK_SUN_HOLE, 4.0f);
        }

        Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>✦ ALL SINS EMBRACED! THE COSMOS COLLAPSES INTO THE ABYSS! ✦</bold></dark_red>"));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 120; // 6.0 seconds total climax

            @Override
            public void run() {
                ticks++;

                if (ticks <= 50) {
                    // STAGE 1 (Ticks 0-50): Black Sun shrinks into collapsing Black Hole singularity
                    double shrinkProgress = (double) ticks / 50.0;
                    double holeRadius = Math.max(0.6, 4.0 * (1.0 - shrinkProgress));
                    float holeScale = (float) Math.max(0.7, 4.5 * (1.0 - shrinkProgress));
                    float rot = (float) (ticks * 0.16);

                    if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                        if (ticks == 1) {
                            blackSunDisplay.setItemStack(createBlackSunItem(CMD_BLACK_SUN_HOLE));
                        }
                        updateBlackSunTransform(blackSunDisplay, rot, 0.45f, holeScale);
                    }

                    world.spawnParticle(Particle.DUST, center, 14, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(Color.fromRGB(5, 5, 8), 2.4f));
                    world.spawnParticle(Particle.SQUID_INK, center, 8, 0.2, 0.2, 0.2, 0.02);
                    world.spawnParticle(Particle.SMOKE, center, 5, 0.3, 0.3, 0.3, 0.03);

                    // Shrinking accretion swirl
                    for (int a = 0; a < 360; a += 45) {
                        double ang = Math.toRadians(a + ticks * 12);
                        Location p = center.clone().add(Math.cos(ang) * holeRadius, Math.sin(ticks * 0.2 + a) * 0.2, Math.sin(ang) * holeRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(40, 20, 60), 1.6f));
                    }

                    if (ticks % 10 == 0) {
                        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.4f, 0.5f + (ticks * 0.01f));
                        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.7f + (ticks * 0.01f));
                    }
                } else if (ticks <= 80) {
                    // STAGE 2 (Ticks 51-80): White State Expansion & PUSH OUT at Tick 65
                    double whiteProgress = (double) (ticks - 50) / 30.0;
                    double whiteRadius = 1.0 + (whiteProgress * 7.0);
                    float whiteScale = (float) (1.0 + (whiteProgress * 6.0));
                    float rot = (float) (ticks * 0.1);

                    if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                        if (ticks == 51) {
                            blackSunDisplay.setItemStack(createBlackSunItem(CMD_BLACK_SUN_WHITE));
                        }
                        updateBlackSunTransform(blackSunDisplay, rot, 0.15f, whiteScale);
                    }

                    world.spawnParticle(Particle.FLASH, center, 6, 0.5, 0.5, 0.5, 0, Color.WHITE);
                    world.spawnParticle(Particle.DUST, center, 20, 0.4, 0.4, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 255), 2.5f));
                    world.spawnParticle(Particle.END_ROD, center, 8, 0.5, 0.5, 0.5, 0.03);

                    // Expanding white aura sphere
                    for (int a = 0; a < 360; a += 30) {
                        double ang = Math.toRadians(a + ticks * 16);
                        Location pt = center.clone().add(Math.cos(ang) * whiteRadius, 0, Math.sin(ang) * whiteRadius);
                        world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1.8f));
                        world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.005);
                    }

                    // AT TICKS 65-74: CONTINUOUS POWERFUL PUSH EVERYONE OUT ~30 BLOCKS!
                    if (ticks >= 65 && ticks <= 74) {
                        if (ticks == 65) {
                            world.spawnParticle(Particle.FLASH, center, 4, 1.0, 1.0, 1.0, 0, Color.WHITE);
                            world.spawnParticle(Particle.FIREWORK, center, 50, 2.0, 1.0, 2.0, 0.25);
                            world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.6f);
                            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.5f);
                            world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
                        }

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            Vector dirAway = p.getLocation().toVector().subtract(center.toVector());
                            if (dirAway.lengthSquared() < 0.01) {
                                dirAway = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                            }
                            dirAway.setY(0).normalize();
                            // Powerful push velocity to propel ~30 blocks horizontally
                            p.setVelocity(dirAway.multiply(3.85).setY(0.26));
                            if (ticks == 65) {
                                com.churchsmp.util.CinematicVFXUtil.triggerScreenShake(p, 4, 30, plugin);
                                com.churchsmp.util.CinematicVFXUtil.flashScreen(p, "#ffffff", 10, 180, 400);
                            }
                        }
                    }
                } else if (ticks <= 95) {
                    // STAGE 3 (Ticks 81-95): PULL BACK IN (Inward Gravitational Vortex ~30 blocks)
                    double pullProgress = (double) (ticks - 80) / 15.0;
                    float pullScale = (float) (7.0 - (pullProgress * 3.5));
                    float rot = (float) (ticks * 0.22);

                    if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                        updateBlackSunTransform(blackSunDisplay, rot, 0.35f, pullScale);
                    }

                    for (int i = 0; i < 8; i++) {
                        double pullAngle = Math.random() * 2 * Math.PI;
                        double pullDist = 3.0 + Math.random() * 9.0;
                        Location pullStart = center.clone().add(Math.cos(pullAngle) * pullDist, (Math.random() - 0.5) * 3.0, Math.sin(pullAngle) * pullDist);
                        Vector pullVec = center.toVector().subtract(pullStart.toVector()).normalize().multiply(0.65);
                        world.spawnParticle(Particle.PORTAL, pullStart, 0, pullVec.getX(), pullVec.getY(), pullVec.getZ(), 0.9);
                        world.spawnParticle(Particle.DUST, pullStart, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(80, 50, 120), 1.4f));
                    }

                    // Continuous powerful inward vacuum force to pull ~30 blocks back to center!
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Vector toCenter = center.toVector().subtract(p.getLocation().toVector());
                        double distSq = toCenter.lengthSquared();
                        if (distSq > 2.0) {
                            toCenter.setY(0).normalize();
                            p.setVelocity(toCenter.multiply(2.85).setY(0.22));
                        }
                    }

                    if (ticks % 5 == 0) {
                        world.playSound(center, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.6f);
                        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.7f);
                    }
                } else {
                    // STAGE 4 (Ticks 96-120): BECOME BLACK WITH VIGNETTE OF BLACK
                    float rot = (float) (ticks * 0.05);
                    if (blackSunDisplay != null && blackSunDisplay.isValid()) {
                        if (ticks == 96) {
                            blackSunDisplay.setItemStack(createBlackSunItem(CMD_BLACK_SUN_BASE));
                        }
                        updateBlackSunTransform(blackSunDisplay, rot, 0.2f, 3.5f);
                    }

                    world.spawnParticle(Particle.DUST, center, 22, 0.4, 0.4, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 0, 0), 3.0f));
                    world.spawnParticle(Particle.SQUID_INK, center, 14, 0.3, 0.3, 0.3, 0.02);

                    if (ticks == 96) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            com.churchsmp.util.CinematicVFXUtil.triggerDarknessVignette(p, 30);
                            com.churchsmp.util.CinematicVFXUtil.flashScreen(p, "#050505", 35, 240, 400);
                            com.churchsmp.util.CinematicVFXUtil.triggerScreenShake(p, 1, 25, plugin);
                            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.4f);
                            p.playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.4f, 0.5f);
                        }
                    }
                }

                // Climax at exactly 120 ticks (6.0s): Grand Launch to Y: 300
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
        if (world == null) return;

        removeBlackSun();

        // 1. Black hole collapses & Titanic lightning detonation
        world.strikeLightningEffect(center);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 4);
        world.spawnParticle(Particle.FLASH, center, 15, 1.0, 1.0, 1.0, 0, Color.WHITE);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.9f);

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
                miniMessage.deserialize("<white><bold>Arc - 1 | Illuminate what <gold>He</gold> left us.</bold></white>"),
                miniMessage.deserialize("<gray><italic>" + TextUtil.toSmallCaps("Glide to grace with wings.") + "</italic></gray>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(1000))
        );

        // 3. Launch everyone into air to Y:300 with untakeoffable Elytra & fall damage immunity
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();

            // Cache original chestplate
            ItemStack cp = p.getInventory().getChestplate();
            if (cp != null && cp.getType() != Material.AIR) {
                cachedChestplates.put(p.getUniqueId(), cp.clone());
            }

            // Equip temporary untakeoffable Elytra
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            org.bukkit.inventory.meta.ItemMeta meta = elytra.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                meta.displayName(miniMessage.deserialize("<gold><bold>Arc I Genesis Wings</bold></gold>"));
                meta.lore(List.of(
                        miniMessage.deserialize("<gray>" + TextUtil.toSmallCaps("Glide to grace with wings.") + "</gray>"),
                        miniMessage.deserialize("<dark_purple>Bound to your soul for the Genesis flight.</dark_purple>")
                ));
                elytra.setItemMeta(meta);
            }
            p.getInventory().setChestplate(elytra);
            glidingPlayers.add(p.getUniqueId());
            fallImmunityPlayers.add(p.getUniqueId());

            // Teleport smoothly to Y:300 and launch forward
            Location launchLoc = new Location(world, loc.getX(), 300.0, loc.getZ(), loc.getYaw(), loc.getPitch());
            p.teleport(launchLoc);
            p.setVelocity(loc.getDirection().normalize().multiply(1.8).setY(0.2));
            p.setGliding(true);

            // Hyperspace FOV warp stretch
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 10, false, false, false));

            // Supersonic Mach Shock Rings behind player
            Location trail = launchLoc.clone().subtract(loc.getDirection().multiply(1.5));
            world.spawnParticle(Particle.SONIC_BOOM, trail, 1);
            world.spawnParticle(Particle.FIREWORK, trail, 15, 0.5, 0.5, 0.5, 0.08);
            world.spawnParticle(Particle.FLASH, trail, 2, 0.2, 0.2, 0.2, 0, Color.WHITE);

            p.showTitle(launchTitle);
            com.churchsmp.util.CinematicVFXUtil.flashVintage(p, com.churchsmp.util.CinematicVFXUtil.VintageStyle.SOLAR_ECLIPSE, plugin);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.5f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 1.0f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
        }

        startGlideTrailTask();
        Bukkit.broadcast(miniMessage.deserialize("<gold>✦ The Genesis Ceremony has concluded! Realm borders expanded to " + (int) border.getSize() + " blocks! ✦</gold>"));
    }

    private void startGlideTrailTask() {
        if (glideTrailTask != null) glideTrailTask.cancel();

        this.glideTrailTask = new BukkitRunnable() {
            int trailTicks = 0;

            @Override
            public void run() {
                if (glidingPlayers.isEmpty()) {
                    cancel();
                    return;
                }
                trailTicks++;

                for (UUID uuid : new ArrayList<>(glidingPlayers)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline() || !p.isGliding()) continue;

                    SinGemType sin = playerChosenSins.get(uuid);
                    Color c = (sin != null) ? Color.fromRGB(
                            (sin.getColor().value() >> 16) & 0xFF,
                            (sin.getColor().value() >> 8) & 0xFF,
                            sin.getColor().value() & 0xFF
                    ) : Color.WHITE;

                    Vector dir = p.getLocation().getDirection().normalize();
                    Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                    if (right.lengthSquared() < 0.01) {
                        right = new Vector(1, 0, 0);
                    }
                    Vector up = right.clone().crossProduct(dir).normalize();

                    Location centerTrail = p.getLocation().subtract(dir.clone().multiply(0.8)).add(0, 0.3, 0);

                    // Left Wingtip Corkscrew Vortex (Clockwise)
                    Location leftWing = centerTrail.clone().subtract(right.clone().multiply(0.85));
                    double leftAngle = Math.toRadians((trailTicks * 28) % 360);
                    Location leftSpiral = leftWing.clone().add(
                            right.clone().multiply(Math.cos(leftAngle) * 0.25).add(up.clone().multiply(Math.sin(leftAngle) * 0.25))
                    );
                    p.getWorld().spawnParticle(Particle.DUST, leftSpiral, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.6f));

                    // Right Wingtip Corkscrew Vortex (Counter-Clockwise)
                    Location rightWing = centerTrail.clone().add(right.clone().multiply(0.85));
                    double rightAngle = Math.toRadians((-trailTicks * 28) % 360);
                    Location rightSpiral = rightWing.clone().add(
                            right.clone().multiply(Math.cos(rightAngle) * 0.25).add(up.clone().multiply(Math.sin(rightAngle) * 0.25))
                    );
                    p.getWorld().spawnParticle(Particle.DUST, rightSpiral, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.6f));

                    // Center Supersonic Ionization Trail
                    p.getWorld().spawnParticle(Particle.END_ROD, centerTrail, 1, 0.01, 0.01, 0.01, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Prevents players from removing the temporary Genesis Elytra from their chest slot.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!glidingPlayers.contains(player.getUniqueId())) return;

        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR || event.getRawSlot() == 6) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize("<red>Your wings are bound to your soul until safe landing!</red>"));
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if ((current != null && current.getType() == Material.ELYTRA) || (cursor != null && cursor.getType() == Material.ELYTRA)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents players from dropping the Genesis Elytra while gliding.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!glidingPlayers.contains(event.getPlayer().getUniqueId())) return;
        if (event.getItemDrop().getItemStack().getType() == Material.ELYTRA) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize("<red>You cannot discard your wings while gliding!</red>"));
        }
    }

    /**
     * Cleans up temporary Elytra and restores original chestplate upon landing.
     * Retains fall damage immunity for a 10s grace period.
     */
    @EventHandler
    public void onPlayerLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!glidingPlayers.contains(uuid)) return;

        if (player.isOnGround() || player.isInWater() || player.getLocation().getBlock().isLiquid()) {
            glidingPlayers.remove(uuid);

            // Restore chestplate
            ItemStack original = cachedChestplates.remove(uuid);
            if (original != null) {
                player.getInventory().setChestplate(original);
            } else {
                player.getInventory().setChestplate(null);
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.4f, 1.2f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 10, 0.3, 0.1, 0.3, 0.02);
            player.sendMessage(miniMessage.deserialize("<green><bold>✦ Safely landed! Your gear has been restored. ✦</bold></green>"));

            // 10s grace period for fall damage immunity
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fallImmunityPlayers.remove(uuid);
            }, 200L);

            if (glidingPlayers.isEmpty()) {
                if (glideTrailTask != null) {
                    glideTrailTask.cancel();
                    glideTrailTask = null;
                }
                this.state = EventState.IDLE;
            }
        }
    }

    /**
     * Fall and kinetic wall damage immunity while gliding + safe landing grace period.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            UUID uuid = p.getUniqueId();
            if (glidingPlayers.contains(uuid) || fallImmunityPlayers.contains(uuid)) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                        event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
                    event.setCancelled(true);
                }
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
            Location center = getEventCenter(world);
            Location spawnLoc = center.clone();
            int groundY = world.getHighestBlockYAt((int) spawnLoc.getX(), (int) spawnLoc.getZ());
            spawnLoc.setY(Math.max(groundY + 1.0, center.getY()));
            event.getPlayer().teleport(spawnLoc);
            if (ceremonyBossBar != null) {
                event.getPlayer().showBossBar(ceremonyBossBar);
            }
            event.getPlayer().sendMessage(miniMessage.deserialize("<gold>✦ A Genesis Ceremony is currently active at Spawn! Type <yellow><bold>/church ready</bold></yellow>! ✦</gold>"));
            updateReadyActionBar();
        }
    }

    public void cleanupSoulDisplays() {
        for (ItemDisplay id : activeSoulDisplays) {
            if (id != null && id.isValid()) id.remove();
        }
        activeSoulDisplays.clear();
        for (ItemDisplay id : sinItemDisplays.values()) {
            if (id != null && id.isValid()) id.remove();
        }
        sinItemDisplays.clear();

        for (TextDisplay td : activeTextDisplays) {
            if (td != null && td.isValid()) td.remove();
        }
        activeTextDisplays.clear();
        for (TextDisplay td : sinTextDisplays.values()) {
            if (td != null && td.isValid()) td.remove();
        }
        sinTextDisplays.clear();

        for (Interaction it : activeInteractions) {
            if (it != null && it.isValid()) it.remove();
        }
        activeInteractions.clear();
        for (Interaction it : sinInteractions.values()) {
            if (it != null && it.isValid()) it.remove();
        }
        sinInteractions.clear();
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
        cleanupSoulDisplays();
        removeBlackSun();
    }

    private final Map<UUID, BukkitTask> testBlackSunTasks = new ConcurrentHashMap<>();

    public void startTestBlackSun(Player player) {
        stopTestBlackSun(player);
        Location center = player.getLocation().add(0, 5.0, 0);
        final SinGemType[] sins = SinGemType.BASE_SINS;
        World world = player.getWorld();

        // 1. Screen shake at lowest level (Hardness 1)
        com.churchsmp.util.CinematicVFXUtil.triggerScreenShake(player, 1, 35, plugin);

        // 2. Screen vintage of the sin color before it appears (Wrath #ff1a1a)
        com.churchsmp.util.CinematicVFXUtil.flashScreen(player, "#ff1a1a", 30, 220, 600);

        // 3. Particle bolt: single straight line that expands on top (unreversed)
        double startY = player.getLocation().getY();
        double endY = center.getY();

        new BukkitRunnable() {
            int boltTicks = 0;
            final int maxBoltTicks = 20;

            @Override
            public void run() {
                boltTicks++;
                double progress = Math.min(1.0, (double) boltTicks / maxBoltTicks);
                double currentY = startY + (endY - startY) * progress;

                for (double y = startY; y <= currentY; y += 0.15) {
                    Location pt = new Location(world, center.getX(), y, center.getZ());
                    world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 30, 40), 1.6f));
                    if (Math.random() < 0.2) {
                        world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.005);
                    }
                }

                if (boltTicks >= maxBoltTicks) {
                    cancel();

                    // Expand on top! (Radial burst)
                    com.churchsmp.util.VFXUtil.drawRadialLightningBurst(center, 12, 4.5, Color.fromRGB(255, 40, 50), 1.6f);

                    // Flash-fade white when it does!
                    com.churchsmp.util.CinematicVFXUtil.flashScreen(player, "#ffffff", 20, 250, 700);
                    world.spawnParticle(Particle.FLASH, center, 14, 1.0, 1.0, 1.0, 0, Color.WHITE);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
                    player.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                    player.playSound(center, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.65f);
                    playRetroCrystalChime(center, 1.5f, 2.0f);

                    // Start rotating and cycling through sin colors with 3D ItemDisplay (Zero cross-circles!)
                    ItemDisplay testDisplay = spawnBlackSunDisplay(center, CMD_BLACK_SUN_WRATH, 4.5f);
                    if (testDisplay != null) {
                        testBlackSunDisplays.put(player.getUniqueId(), testDisplay);
                    }

                    BukkitTask task = new BukkitRunnable() {
                        int ticks = 0;
                        int lastSinIdx = -1;
                        @Override
                        public void run() {
                            if (!player.isOnline()) {
                                cancel();
                                stopTestBlackSun(player);
                                return;
                            }
                            ticks++;
                            double rot = ticks * 0.08;
                            int sinIdx = (ticks / 25) % sins.length;
                            SinGemType cur = sins[sinIdx];
                            Color c = Color.fromRGB(
                                    (cur.getColor().value() >> 16) & 0xFF,
                                    (cur.getColor().value() >> 8) & 0xFF,
                                    cur.getColor().value() & 0xFF
                            );

                            ItemDisplay td = testBlackSunDisplays.get(player.getUniqueId());
                            if (td != null && td.isValid()) {
                                float modelRot = (float) (ticks * 0.06);
                                updateBlackSunTransform(td, modelRot, 0.25f, 4.5f);
                                if (sinIdx != lastSinIdx) {
                                    lastSinIdx = sinIdx;
                                    td.setItemStack(createBlackSunItem(getSinBlackSunCMD(cur)));
                                }
                            }

                            // Constant unreversed math particle bolt strikes down from celestial sun upon the player!
                            if (ticks % 8 == 0) {
                                Location pLoc = player.getLocation();
                                double topY = center.getY();
                                double bottomY = pLoc.getY() + 1.8;
                                double height = topY - bottomY;

                                for (double y = 0; y <= height; y += 0.25) {
                                    double currentY = bottomY + y;
                                    // Central straight line
                                    Location boltPt = new Location(world, pLoc.getX(), currentY, pLoc.getZ());
                                    world.spawnParticle(Particle.DUST, boltPt, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.9f));

                                    // Mathematical counter-rotating double helix
                                    double hr = 0.35;
                                    double ha1 = (ticks * 0.3) + (y * 0.9);
                                    double ha2 = -(ticks * 0.3) + (y * 0.9) + Math.PI;
                                    Location h1 = new Location(world, pLoc.getX() + Math.cos(ha1) * hr, currentY, pLoc.getZ() + Math.sin(ha1) * hr);
                                    Location h2 = new Location(world, pLoc.getX() + Math.cos(ha2) * hr, currentY, pLoc.getZ() + Math.sin(ha2) * hr);
                                    world.spawnParticle(Particle.DUST, h1, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1.2f));
                                    world.spawnParticle(Particle.DUST, h2, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1.2f));

                                    // Mathematical expanding cone on top (highest 4 blocks)
                                    if (y >= height - 4.0) {
                                        double coneRadius = (y - (height - 4.0)) * 0.45;
                                        for (int k = 0; k < 4; k++) {
                                            double ca = (k * Math.PI / 2.0) + (ticks * 0.1);
                                            Location conePt = new Location(world, pLoc.getX() + Math.cos(ca) * coneRadius, currentY, pLoc.getZ() + Math.sin(ca) * coneRadius);
                                            world.spawnParticle(Particle.END_ROD, conePt, 1, 0, 0, 0, 0.005);
                                        }
                                    }
                                }
                                world.playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
                                world.playSound(pLoc, Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.6f);
                            }

                            com.churchsmp.util.VFXUtil.drawCelestialBlackSun(center, 4.5, rot, c, Color.fromRGB(255, 240, 160));
                        }
                    }.runTaskTimer(plugin, 0L, 2L);

                    testBlackSunTasks.put(player.getUniqueId(), task);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stopTestBlackSun(Player player) {
        BukkitTask task = testBlackSunTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        ItemDisplay td = testBlackSunDisplays.remove(player.getUniqueId());
        if (td != null && td.isValid()) td.remove();
    }
}
