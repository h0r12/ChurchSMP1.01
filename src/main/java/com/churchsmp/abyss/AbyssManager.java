package com.churchsmp.abyss;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.CinematicVFXUtil;
import com.churchsmp.util.VFXUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Abyss Realm, the Bedrock Dual-Orbit Altar, the Sacrificial Gem Ritual,
 * and the Grand Void Climax for forging the ultimate Mythic SIN Gem.
 */
public class AbyssManager implements Listener {

    public static final NamespacedKey WAND_KEY = new NamespacedKey("churchsmp", "abyss_wand");

    public enum SetupTarget {
        MAIN("Bedrock Main Altar", "main"),
        ENTRANCE("Abyss Entrance", "entrance"),
        WRATH("Wrath Pedestal", "wrath"),
        ENVY("Envy Pedestal", "envy"),
        PRIDE("Pride Pedestal", "pride"),
        GLUTTONY("Gluttony Pedestal", "gluttony"),
        GREED("Greed Pedestal", "greed"),
        LUST("Lust Pedestal", "lust"),
        SLOTH("Sloth Pedestal", "sloth");

        private final String displayName;
        private final String configKey;

        SetupTarget(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }

        public String getDisplayName() { return displayName; }
        public String getConfigKey() { return configKey; }
    }

    private final ChurchSMP plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final File dataFile;
    private FileConfiguration config;

    private Location mainAltarLocation = null;
    private Location entranceLocation = null;
    private final Map<SinGemType, Location> sinSlotLocations = new ConcurrentHashMap<>();
    private final Map<SinGemType, ItemDisplay> activeSlotDisplays = new ConcurrentHashMap<>();
    private final Map<SinGemType, ItemDisplay> dualOrbitDisplays = new ConcurrentHashMap<>();
    private final Set<SinGemType> consumedSins = ConcurrentHashMap.newKeySet();
    private final Set<SinGemType> activeSacrifices = ConcurrentHashMap.newKeySet();
    private final Set<SinGemType> fallingSins = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> wandSteps = new ConcurrentHashMap<>();

    private BukkitTask dualOrbitTask = null;
    private BukkitTask skyboxTask = null;
    private boolean climaxInProgress = false;

    public AbyssManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "abyss.yml");
        loadData();
        startSkyboxCycle();
    }

    public void loadData() {
        if (!dataFile.exists()) {
            plugin.saveResource("abyss.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(dataFile);

        if (config.contains("main")) {
            mainAltarLocation = config.getLocation("main");
        }
        if (config.contains("entrance")) {
            entranceLocation = config.getLocation("entrance");
        }

        sinSlotLocations.clear();
        for (SinGemType sin : SinGemType.BASE_SINS) {
            String path = "slots." + sin.name().toLowerCase(Locale.ROOT);
            if (config.contains(path)) {
                sinSlotLocations.put(sin, config.getLocation(path));
            }
        }

        if (mainAltarLocation != null) {
            startMainAltar(mainAltarLocation);
        }
    }

    public void saveData() {
        final Location mainCopy = (mainAltarLocation != null) ? mainAltarLocation.clone() : null;
        final Location entranceCopy = (entranceLocation != null) ? entranceLocation.clone() : null;
        final Map<SinGemType, Location> slotsCopy = new HashMap<>(sinSlotLocations);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (mainCopy != null) {
                    config.set("main", mainCopy);
                }
                if (entranceCopy != null) {
                    config.set("entrance", entranceCopy);
                }
                for (Map.Entry<SinGemType, Location> entry : slotsCopy.entrySet()) {
                    config.set("slots." + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
                }
                config.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save abyss.yml: " + e.getMessage());
            }
        });
    }

    public Location getMainAltarLocation() {
        return mainAltarLocation;
    }

    public void setEntranceLocation(Location loc) {
        this.entranceLocation = loc;
        saveData();
    }

    public void setSlotLocation(SinGemType sin, Location loc) {
        sinSlotLocations.put(sin, loc);
        saveData();
    }

    public Location getSlotLocation(SinGemType sin) {
        return sinSlotLocations.get(sin);
    }

    /**
     * Teleports player to the Abyss.
     */
    public boolean teleportToAbyss(Player player) {
        Location target = (entranceLocation != null) ? entranceLocation : mainAltarLocation;
        if (target == null) {
            return false;
        }
        player.teleport(target.clone().add(0, 0.5, 0));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.6f);
        player.playSound(player.getLocation(), Sound.AMBIENT_NETHER_WASTES_MOOD, 1.5f, 0.5f);
        CinematicVFXUtil.flashScreen(player, "#1a0505", 40, 200, 600);
        player.sendMessage(mm.deserialize("<dark_red><bold>✦ THE ABYSS ✦</bold></dark_red> <gray>You have entered the cosmic unknown.</gray>"));
        return true;
    }

    /**
     * Sets the Main Altar location, spawns the Bedrock block, and starts the Dual Orbit system.
     */
    public void startMainAltar(Location center) {
        this.mainAltarLocation = center.clone();
        saveData();

        cleanupAltar();

        World world = center.getWorld();
        if (world == null) return;

        // Place Bedrock Altar Block
        center.getBlock().setType(Material.BEDROCK);

        // Spawn 7 Base Sins in Dual Orbit around the Bedrock Altar
        final SinGemType[] sins = SinGemType.BASE_SINS;
        final double orbitRadius = 3.2;

        for (int i = 0; i < sins.length; i++) {
            SinGemType sin = sins[i];
            double angle = (2 * Math.PI / sins.length) * i;
            Location spawnLoc = center.clone().add(Math.cos(angle) * orbitRadius, 1.2, Math.sin(angle) * orbitRadius);

            ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
                d.setItemStack(plugin.getSinGemManager().createGemItem(sin));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(1.35f, 1.35f, 1.35f),
                        new AxisAngle4f(0f, 0f, 1f, 0f)
                ));
            });
            dualOrbitDisplays.put(sin, display);
        }

        // Dual Orbit Animation Task
        dualOrbitTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (mainAltarLocation == null || mainAltarLocation.getWorld() == null) return;

                double revAngle = ticks * 0.035;
                float selfRotAngle = (float) (ticks * 0.12);

                for (int i = 0; i < sins.length; i++) {
                    SinGemType sin = sins[i];
                    ItemDisplay display = dualOrbitDisplays.get(sin);
                    if (display == null || !display.isValid()) continue;

                    // Skip displaying in orbit if it has already been consumed and slotted, or falling cartoon style
                    if (consumedSins.contains(sin) || fallingSins.contains(sin)) {
                        continue;
                    } else {
                        display.setViewRange(1.0f);
                    }

                    double currentAngle = revAngle + (2 * Math.PI / sins.length) * i;
                    double yOffset = 1.3 + Math.sin(ticks * 0.08 + i) * 0.16;

                    Location targetLoc = mainAltarLocation.clone().add(
                            Math.cos(currentAngle) * orbitRadius,
                            yOffset,
                            Math.sin(currentAngle) * orbitRadius
                    );

                    display.teleport(targetLoc);
                    display.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(selfRotAngle, 0f, 1f, 0f),
                            new Vector3f(1.35f, 1.35f, 1.35f),
                            new AxisAngle4f(0.2f, 1f, 0f, 0f)
                    ));
                    display.setInterpolationDuration(1);
                    display.setInterpolationDelay(0);

                    // Occasional subtle sin sparkle
                    if (ticks % 6 == 0) {
                        Color sinColor = Color.fromRGB(sin.getColor().value());
                        targetLoc.getWorld().spawnParticle(Particle.DUST, targetLoc.clone().add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0,
                                new Particle.DustOptions(sinColor, 1.2f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Continuous cosmic skybox: Dark red and pitch-black geometric objects cycle endlessly.
     */
    private void startSkyboxCycle() {
        if (skyboxTask != null) skyboxTask.cancel();

        skyboxTask = new BukkitRunnable() {
            int skyTicks = 0;

            @Override
            public void run() {
                skyTicks++;
                if (mainAltarLocation == null || mainAltarLocation.getWorld() == null) return;
                World world = mainAltarLocation.getWorld();

                // Only render if there are players in the abyss world
                if (world.getPlayers().isEmpty()) return;

                double centerRot = skyTicks * 0.015;
                double skyRadius = 28.0;

                for (int i = 0; i < 4; i++) {
                    double angle = centerRot + (i * (Math.PI / 2));
                    double sx = mainAltarLocation.getX() + Math.cos(angle) * skyRadius;
                    double sz = mainAltarLocation.getZ() + Math.sin(angle) * skyRadius;
                    double sy = mainAltarLocation.getY() + 14.0 + Math.sin(skyTicks * 0.03 + i) * 3.0;

                    Location skyPoint = new Location(world, sx, sy, sz);

                    // Dark red and black orbital geometry
                    world.spawnParticle(Particle.DUST, skyPoint, 3, 0.6, 0.6, 0.6, 0,
                            new Particle.DustOptions(Color.fromRGB(45, 0, 5), 2.8f));
                    world.spawnParticle(Particle.DUST, skyPoint, 2, 0.4, 0.4, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(5, 5, 8), 2.5f));
                    if (skyTicks % 10 == 0) {
                        world.spawnParticle(Particle.PORTAL, skyPoint, 4, 0.5, 0.5, 0.5, 0.05);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Executes the cartoon-style fall for the specified orbiting sin at the altar.
     * When the player sacrifices a gem, the corresponding orbiting gem pauses, shakes in mid-air,
     * and plunges straight down cartoon-style with gravity acceleration into a smoke poof!
     */
    private void executeCartoonFall(ItemDisplay display, SinGemType sin) {
        World world = display.getWorld();
        Location initialLoc = display.getLocation().clone();
        Color sinColor = Color.fromRGB(sin.getColor().value());

        new BukkitRunnable() {
            int ticks = 0;
            double fallVelocity = 0.06;
            Location current = initialLoc.clone();

            @Override
            public void run() {
                ticks++;
                if (!display.isValid()) {
                    cancel();
                    return;
                }

                // Phase 1 (Ticks 1-8): Cartoon freeze & wobble in mid-air
                if (ticks <= 8) {
                    double wobbleX = (Math.random() - 0.5) * 0.16;
                    double wobbleZ = (Math.random() - 0.5) * 0.16;
                    display.teleport(initialLoc.clone().add(wobbleX, 0, wobbleZ));
                    world.playSound(initialLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 1.8f + (ticks * 0.04f));
                    world.spawnParticle(Particle.DUST, initialLoc, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(sinColor, 1.0f));
                    return;
                }

                // Phase 2 (Tick 9): Cartoon drop sound
                if (ticks == 9) {
                    world.playSound(current, Sound.ENTITY_CHICKEN_EGG, 1.4f, 0.6f);
                    world.playSound(current, Sound.BLOCK_WOOL_FALL, 1.2f, 0.5f);
                }

                // Phase 3 (Ticks 9-22): Sudden accelerating plunge straight down!
                fallVelocity += 0.12;
                current.subtract(0, fallVelocity, 0);
                display.teleport(current);
                world.spawnParticle(Particle.DUST, current, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(sinColor, 1.4f));

                if (ticks >= 22) {
                    // Cartoon poof of smoke at the bottom
                    world.spawnParticle(Particle.POOF, current, 10, 0.2, 0.2, 0.2, 0.05);
                    world.playSound(current, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.8f);
                    display.setViewRange(0);
                    display.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Intercepts item drops in the Abyss to trigger the Sacrificial Gem Ritual.
     * Uses a flying ItemDisplay with only particle trails (zero dropped item physics).
     * Supports multiple simultaneous or sequential gem sacrifices!
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropSinGem(PlayerDropItemEvent event) {
        if (mainAltarLocation == null || climaxInProgress) return;
        Player player = event.getPlayer();
        if (!player.getWorld().equals(mainAltarLocation.getWorld())) return;

        // Check if within 40 blocks of Main Altar
        if (player.getLocation().distanceSquared(mainAltarLocation) > 1600) return;

        Item droppedItem = event.getItemDrop();
        ItemStack stack = droppedItem.getItemStack();
        SinGemType sin = plugin.getSinGemManager().getGemType(stack);

        if (sin == null || sin == SinGemType.SIN) return;

        // Check if already consumed or currently being sacrificed
        if (consumedSins.contains(sin) || activeSacrifices.contains(sin)) {
            player.sendMessage(mm.deserialize("<red>The Sin of " + sin.getDisplayName() + " has already been sacrificed or is in ritual!</red>"));
            return;
        }

        // REMOVE physical dropped item immediately - no vanilla dropped item exists on ground!
        droppedItem.remove();

        // Mark active sacrifice
        activeSacrifices.add(sin);

        // Detach corresponding orbiting sin display at altar and make it plunge cartoon style!
        fallingSins.add(sin);
        ItemDisplay orbitingDisplay = dualOrbitDisplays.get(sin);
        if (orbitingDisplay != null && orbitingDisplay.isValid()) {
            executeCartoonFall(orbitingDisplay, sin);
        }

        // Spawn sacrificial ItemDisplay at player eye location with only particle trail
        Location startLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.4));
        ItemDisplay flyingDisplay = startLoc.getWorld().spawn(startLoc, ItemDisplay.class, d -> {
            d.setItemStack(plugin.getSinGemManager().createGemItem(sin));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.4f, 1.4f, 1.4f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
        });

        executeSinSacrifice(player, flyingDisplay, startLoc, sin);
    }

    /**
     * Multi-stage Sacrificial Gem Ritual using flying ItemDisplay with particle trails:
     * 1. Red occult satanic multi-axis spinning circle around flying ItemDisplay
     * 2. Smooth flight to Main Altar with speed difference
     * 3. Dispersal of aura, hovering, and violent shaking
     * 4. Rapid blood chains striking and shattering the gem into particles
     * 5. 3-second dramatic pause (heartbeat sound)
     * 6. Manifestation into designated slot
     * 7. Void Climax trigger if all 7 consumed
     */
    private void executeSinSacrifice(Player player, ItemDisplay flyingDisplay, Location startLoc, SinGemType sin) {
        World world = flyingDisplay.getWorld();
        Location altarTarget = mainAltarLocation.clone().add(0, 1.8, 0);

        player.playSound(startLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.7f);
        player.playSound(startLoc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.5f);

        new BukkitRunnable() {
            int step = 0;
            final int travelTicks = 45; // ~2.25 seconds to travel

            @Override
            public void run() {
                step++;
                Location currentGemLoc = flyingDisplay.getLocation();
                Color sinColor = Color.fromRGB(sin.getColor().value());

                // 1. Red Occult Satanic Multi-Ring Spin (each ring on independent axis)
                double outerAngle = step * 0.16;
                double innerAngle = -step * 0.24;
                for (int a = 0; a < 360; a += 36) {
                    double r1 = Math.toRadians(a + outerAngle);
                    Location p1 = currentGemLoc.clone().add(Math.cos(r1) * 1.5, Math.sin(step * 0.1) * 0.1, Math.sin(r1) * 1.5);
                    world.spawnParticle(Particle.DUST, p1, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(220, 10, 20), 1.6f));

                    double r2 = Math.toRadians(a + innerAngle);
                    Location p2 = currentGemLoc.clone().add(Math.cos(r2) * 0.9, Math.cos(r2) * 0.4, Math.sin(r2) * 0.9);
                    world.spawnParticle(Particle.DUST, p2, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(110, 0, 10), 1.8f));
                }
                world.spawnParticle(Particle.CRIMSON_SPORE, currentGemLoc, 4, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, currentGemLoc, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(sinColor, 1.6f));

                // 2. Smoothstep non-linear flight to Altar with speed difference
                if (step <= travelTicks) {
                    double t = (double) step / travelTicks;
                    double smoothT = t * t * (3 - 2 * t); // Smoothstep curve
                    double nx = startLoc.getX() + (altarTarget.getX() - startLoc.getX()) * smoothT;
                    double ny = startLoc.getY() + (altarTarget.getY() - startLoc.getY()) * smoothT + Math.sin(t * Math.PI) * 1.2;
                    double nz = startLoc.getZ() + (altarTarget.getZ() - startLoc.getZ()) * smoothT;
                    flyingDisplay.teleport(new Location(world, nx, ny, nz));
                }

                if (step == travelTicks + 1) {
                    // Arrived at Altar: Particles vanish, gem hovers motionless
                    flyingDisplay.teleport(altarTarget);
                }

                // 3. Shaking at Altar (Ticks 46 to 75)
                if (step > travelTicks && step <= travelTicks + 30) {
                    double jitterX = (Math.random() - 0.5) * 0.08;
                    double jitterZ = (Math.random() - 0.5) * 0.08;
                    flyingDisplay.teleport(altarTarget.clone().add(jitterX, 0, jitterZ));

                    if (step % 5 == 0) {
                        world.playSound(altarTarget, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.8f);
                    }
                }

                // 4. Blood Chains strike down & shatter gem
                if (step == travelTicks + 31) {
                    flyingDisplay.remove(); // Remove ItemDisplay

                    // High velocity blood chains plunging from Y+10
                    Location chainOrigin = altarTarget.clone().add(0, 10.0, 0);
                    for (double y = altarTarget.getY(); y <= chainOrigin.getY(); y += 0.25) {
                        Location cPt = new Location(world, altarTarget.getX(), y, altarTarget.getZ());
                        world.spawnParticle(Particle.DUST, cPt, 2, 0.08, 0, 0.08, 0,
                                new Particle.DustOptions(Color.fromRGB(180, 0, 15), 2.2f));
                    }

                    // Gem shatters into particles!
                    world.spawnParticle(Particle.DUST, altarTarget, 40, 0.6, 0.6, 0.6, 0,
                            new Particle.DustOptions(sinColor, 2.4f));
                    world.spawnParticle(Particle.BLOCK, altarTarget, 30, 0.5, 0.5, 0.5, 0.1,
                            sin.getIconMaterial().createBlockData());
                    world.spawnParticle(Particle.FLASH, altarTarget, 3, 0.2, 0.2, 0.2, 0, Color.WHITE);

                    world.playSound(altarTarget, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
                    world.playSound(altarTarget, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
                    world.playSound(altarTarget, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.5f, 0.7f);
                    CinematicVFXUtil.triggerScreenShake(player, 2, 20, plugin);
                }

                // 5. 3-Second Dramatic Pause (60 ticks): Total stillness & Heartbeat audio
                if (step >= travelTicks + 32 && step < travelTicks + 92) {
                    int pauseTick = step - (travelTicks + 32);
                    if (pauseTick % 20 == 0) {
                        world.playSound(altarTarget, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.8f);
                    }
                }

                // 6. Slot Placement after 3-second pause!
                if (step == travelTicks + 92) {
                    cancel();
                    manifestGemInSlot(sin);
                    consumedSins.add(sin);
                    activeSacrifices.remove(sin);

                    Bukkit.broadcast(mm.deserialize("<dark_red>✦</dark_red> <gold><bold>" + player.getName() +
                            "</bold></gold> <gray>has sacrificed the</gray> " + sin.getFormattedName() +
                            " <gray>into the Abyss Altar! (" + consumedSins.size() + "/7)</gray>"));

                    // Check if all 7 Sins have been consumed!
                    if (consumedSins.size() >= 7) {
                        triggerGrandVoidClimax(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Gives the admin an interactive Setup Wand:
     * Left-Click a block to place current sin/altar point.
     * Swap to Offhand (F key) to undo/redo 1 step.
     */
    public void giveSetupWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize("<gold><bold>✦ Abyss Setup Wand ✦</bold></gold>"));
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            updateWandLore(meta, 0);
            wand.setItemMeta(meta);
        }
        wandSteps.put(player.getUniqueId(), 0);
        player.getInventory().addItem(wand);
        player.sendMessage(mm.deserialize("<gold><bold>✦ Abyss Setup Wand Received!</bold></gold> <yellow>Left-Click a block</yellow> to set <bold>" + SetupTarget.MAIN.getDisplayName() + "</bold> (1/9). <aqua>Press F (Off-hand)</aqua> to step back / redo."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.5f);
    }

    private void updateWandLore(ItemMeta meta, int step) {
        SetupTarget target = (step < SetupTarget.values().length) ? SetupTarget.values()[step] : SetupTarget.MAIN;
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Interactive setup tool for Abyss Altar & Pedestals</gray>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<yellow><bold>Left-Click Block:</bold></yellow> <white>Set <bold>" + target.getDisplayName() + "</bold></white>"));
        lore.add(mm.deserialize("<aqua><bold>Swap Off-Hand (F):</bold></aqua> <white>Undo / Go back 1 step</white>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Target: </gray><gold><bold>" + (step + 1) + "/9</bold></gold> <dark_gray>(" + target.getDisplayName() + ")</dark_gray>"));
        meta.lore(lore);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        int step = wandSteps.getOrDefault(player.getUniqueId(), 0);
        if (step >= SetupTarget.values().length) {
            step = 0;
        }

        SetupTarget target = SetupTarget.values()[step];
        Location blockLoc = event.getClickedBlock().getLocation();
        World world = blockLoc.getWorld();

        if (target == SetupTarget.MAIN) {
            startMainAltar(blockLoc);
            player.sendMessage(mm.deserialize("<green>✔ [1/9] <gold><bold>Main Altar Bedrock & Dual Orbit</bold></gold> set at " + blockLoc.getBlockX() + ", " + blockLoc.getBlockY() + ", " + blockLoc.getBlockZ() + "!</green>"));
        } else if (target == SetupTarget.ENTRANCE) {
            setEntranceLocation(blockLoc.clone().add(0.5, 1.0, 0.5));
            player.sendMessage(mm.deserialize("<green>✔ [2/9] <gold><bold>Abyss Entrance</bold></gold> set at " + blockLoc.getBlockX() + ", " + (blockLoc.getBlockY() + 1) + ", " + blockLoc.getBlockZ() + "!</green>"));
        } else {
            SinGemType sin = SinGemType.valueOf(target.getConfigKey().toUpperCase(Locale.ROOT));
            Location slotLoc = blockLoc.clone().add(0.5, 1.0, 0.5);
            setSlotLocation(sin, slotLoc);
            player.sendMessage(mm.deserialize("<green>✔ [" + (step + 1) + "/9] <bold>" + sin.getFormattedName() + " Pedestal</bold> set at " + blockLoc.getBlockX() + ", " + (blockLoc.getBlockY() + 1) + ", " + blockLoc.getBlockZ() + "!</green>"));
        }

        player.playSound(blockLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 2.0f);
        player.playSound(blockLoc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.2f, 1.8f);
        if (world != null) {
            world.spawnParticle(Particle.DUST, blockLoc.clone().add(0.5, 1.2, 0.5), 14, 0.25, 0.1, 0.25, 0, new Particle.DustOptions(Color.LIME, 1.6f));
        }

        step++;
        if (step < SetupTarget.values().length) {
            wandSteps.put(player.getUniqueId(), step);
            SetupTarget next = SetupTarget.values()[step];
            ItemMeta meta = item.getItemMeta();
            updateWandLore(meta, step);
            item.setItemMeta(meta);
            player.sendMessage(mm.deserialize("<yellow>✦ Next target: <gold><bold>" + next.getDisplayName() + "</bold></gold> (" + (step + 1) + "/9)</yellow>"));
        } else {
            wandSteps.remove(player.getUniqueId());
            player.sendMessage(mm.deserialize("<green><bold>✦ ALL 9 ABYSS LOCATIONS ARE CONFIGURED AND READY! ✦</bold></green>"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWandSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getMainHandItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        int step = wandSteps.getOrDefault(player.getUniqueId(), 0);
        if (step > 0) {
            step--;
            wandSteps.put(player.getUniqueId(), step);
            SetupTarget target = SetupTarget.values()[step];
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.75f);
            player.sendMessage(mm.deserialize("<yellow>↩ Redid one step back! Now setting: <gold><bold>" + target.getDisplayName() + "</bold></gold> (" + (step + 1) + "/9)</yellow>"));
            ItemMeta meta = item.getItemMeta();
            updateWandLore(meta, step);
            item.setItemMeta(meta);
        } else {
            player.sendMessage(mm.deserialize("<red>Already at the first step (" + SetupTarget.MAIN.getDisplayName() + ")!</red>"));
        }
    }

    /**
     * Manifests the sacrificed Sin Gem into its configured slot with linking beam to Bedrock.
     */
    private void manifestGemInSlot(SinGemType sin) {
        Location slotLoc = sinSlotLocations.get(sin);
        World world = (mainAltarLocation != null) ? mainAltarLocation.getWorld() : null;
        if (world == null) return;

        if (slotLoc == null) {
            // Default radial slot placement if not configured by admin
            int idx = Arrays.asList(SinGemType.BASE_SINS).indexOf(sin);
            double angle = (2 * Math.PI / 7) * idx;
            slotLoc = mainAltarLocation.clone().add(Math.cos(angle) * 5.5, 0.5, Math.sin(angle) * 5.5);
        }

        world.playSound(slotLoc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.5f, 1.2f);
        world.playSound(slotLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.6f);
        world.spawnParticle(Particle.FLASH, slotLoc, 2, 0.3, 0.3, 0.3, 0, Color.WHITE);

        Location finalLoc = slotLoc.clone().add(0, 1.0, 0);
        ItemDisplay slotDisplay = world.spawn(finalLoc, ItemDisplay.class, d -> {
            d.setItemStack(plugin.getSinGemManager().createGemItem(sin));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.5f, 1.5f, 1.5f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
        });
        activeSlotDisplays.put(sin, slotDisplay);

        // Linking beam to Main Bedrock
        if (mainAltarLocation != null) {
            Vector beamVec = mainAltarLocation.clone().add(0, 1.0, 0).toVector().subtract(finalLoc.toVector());
            double length = beamVec.length();
            Vector step = beamVec.normalize().multiply(0.3);
            Location pt = finalLoc.clone();
            Color sinColor = Color.fromRGB(sin.getColor().value());

            for (double d = 0; d < length; d += 0.3) {
                world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, new Particle.DustOptions(sinColor, 1.3f));
                pt.add(step);
            }
        }
    }

    /**
     * THE GRAND VOID CLIMAX:
     * 1. Dark chains come down + SIN celestial entity descends with accelerating wind sound.
     * 2. 7 Sins slowly die by dark lightning bolts.
     * 3. Reversed shockwave inward + full-screen pitch-black flash + full screen shake + SIN scaling down.
     * 4. Dark bolts spread everywhere around SIN.
     * 5. The Piercing Chain (Image 2): Slowly -> FAST -> Really fast, piercing through SIN & player!
     */
    public void triggerGrandVoidClimax(Player triggeringPlayer) {
        climaxInProgress = true;
        World world = mainAltarLocation.getWorld();
        if (world == null) return;

        Bukkit.broadcast(mm.deserialize("<dark_red><bold>☠ ALL 7 SINS CONSUMED! THE ABYSS AWAKENS THE SUPREME SIN! ☠</bold></dark_red>"));

        Location center = mainAltarLocation.clone().add(0, 3.5, 0);

        // Spawn descending SIN display
        ItemStack sinItem = plugin.getSinGemManager().createGemItem(SinGemType.SIN);
        ItemDisplay sinDisplay = world.spawn(center.clone().add(0, 15.0, 0), ItemDisplay.class, d -> {
            d.setItemStack(sinItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(3.5f, 3.5f, 3.5f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
        });

        new BukkitRunnable() {
            int ticks = 0;
            final SinGemType[] sins = SinGemType.BASE_SINS;
            int currentSinKillIndex = 0;

            @Override
            public void run() {
                ticks++;

                // STEP 1 (Ticks 0-60): Dark Chains & SIN Descent + Accelerating Wind Sound
                if (ticks <= 60) {
                    double progress = (double) ticks / 60.0;
                    double currentY = center.getY() + 15.0 * (1.0 - progress);
                    sinDisplay.teleport(new Location(world, center.getX(), currentY, center.getZ()));

                    float rotY = (float) (ticks * 0.1);
                    sinDisplay.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(rotY, 0f, 1f, 0f),
                            new Vector3f(3.5f, 3.5f, 3.5f),
                            new AxisAngle4f(0.2f, 1f, 0f, 0f)
                    ));

                    // 4 Dark chains descending from sky
                    for (int c = 0; c < 4; c++) {
                        double ang = c * (Math.PI / 2);
                        Location cBase = center.clone().add(Math.cos(ang) * 4.0, 0, Math.sin(ang) * 4.0);
                        for (double y = cBase.getY(); y <= cBase.getY() + (25.0 * progress); y += 0.4) {
                            Location cPt = new Location(world, cBase.getX(), y, cBase.getZ());
                            world.spawnParticle(Particle.DUST, cPt, 1, 0.05, 0, 0.05, 0,
                                    new Particle.DustOptions(Color.fromRGB(15, 5, 20), 2.2f));
                        }
                    }

                    // Expanding Firework Explosion Shockwave circle
                    double waveRadius = 1.0 + (progress * 15.0);
                    for (int a = 0; a < 360; a += 15) {
                        double ra = Math.toRadians(a + ticks * 6);
                        Location wavePt = center.clone().add(Math.cos(ra) * waveRadius, 0.2, Math.sin(ra) * waveRadius);
                        world.spawnParticle(Particle.FIREWORK, wavePt, 2, 0.04, 0.04, 0.04, 0.02);
                        if (a % 30 == 0) {
                            world.spawnParticle(Particle.EXPLOSION, wavePt, 1, 0, 0, 0, 0);
                        }
                    }

                    // Accelerating Firework Explosion audio
                    if (ticks % 8 == 0) {
                        float pitch = (float) (0.6 + (progress * 0.8));
                        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.8f, pitch);
                        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1.2f);
                    }
                }

                // STEP 2 (Ticks 61-200): 7 Sins Slow Death by Dark Lightning Bolts (Every 20 ticks)
                if (ticks > 60 && ticks <= 200) {
                    if ((ticks - 60) % 20 == 0 && currentSinKillIndex < sins.length) {
                        SinGemType targetSin = sins[currentSinKillIndex++];
                        Location targetLoc = sinSlotLocations.get(targetSin);
                        if (targetLoc == null) {
                            int idx = Arrays.asList(sins).indexOf(targetSin);
                            double angle = (2 * Math.PI / 7) * idx;
                            targetLoc = mainAltarLocation.clone().add(Math.cos(angle) * 5.5, 0.5, Math.sin(angle) * 5.5);
                        }

                        // Massive Dark Lightning Bolt crashes down from sky
                        for (double y = targetLoc.getY(); y <= targetLoc.getY() + 30; y += 0.3) {
                            Location boltPt = new Location(world, targetLoc.getX(), y, targetLoc.getZ());
                            world.spawnParticle(Particle.DUST, boltPt, 2, 0.1, 0, 0.1, 0,
                                    new Particle.DustOptions(Color.fromRGB(5, 5, 8), 2.5f));
                        }
                        world.spawnParticle(Particle.FLASH, targetLoc, 6, 0.5, 0.5, 0.5, 0, Color.WHITE);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc, 2);

                        world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.6f);
                        world.playSound(targetLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);

                        // Remove slot display and shatter
                        ItemDisplay sd = activeSlotDisplays.remove(targetSin);
                        if (sd != null && sd.isValid()) sd.remove();
                    }
                }

                // STEP 3 (Ticks 201-230): Reversed Shockwave Inward + Pitch-Black Flash + Maximum Shake + Scale Down
                if (ticks == 205) {
                    // Reversed shockwave sucking into SIN
                    for (int i = 0; i < 30; i++) {
                        double ang = Math.random() * 2 * Math.PI;
                        double dist = 8.0 + Math.random() * 14.0;
                        Location outerPt = center.clone().add(Math.cos(ang) * dist, (Math.random() - 0.5) * 4.0, Math.sin(ang) * dist);
                        Vector inVec = center.toVector().subtract(outerPt.toVector()).normalize().multiply(1.2);
                        world.spawnParticle(Particle.PORTAL, outerPt, 0, inVec.getX(), inVec.getY(), inVec.getZ(), 1.0);
                    }

                    // Full-screen Pitch-Black Flash + Full Screen Shake
                    for (Player p : world.getPlayers()) {
                        CinematicVFXUtil.flashScreen(p, "#000000", 30, 300, 600);
                        CinematicVFXUtil.triggerScreenShake(p, 5, 45, plugin);
                    }

                    world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 0.5f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.4f);

                    // SIN scales down inward (implosion)
                    sinDisplay.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f),
                            new Vector3f(1.0f, 1.0f, 1.0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f)
                    ));
                    sinDisplay.setInterpolationDuration(5);
                    sinDisplay.setInterpolationDelay(0);
                }

                // STEP 4 (Ticks 231-250): Dark Bolts Spread Everywhere around SIN
                if (ticks == 235) {
                    VFXUtil.drawRadialLightningBurst(center, 32, 10.0, Color.fromRGB(20, 5, 30), 2.2f);
                    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.7f);
                }

                // STEP 5 (Ticks 251-310): THE PIERCING CHAIN (Slowly -> FAST -> Really Fast)
                if (ticks >= 251 && ticks <= 310) {
                    int chainTick = ticks - 250;
                    Location playerLoc = triggeringPlayer.isOnline() ? triggeringPlayer.getLocation() : center;
                    Location chainTop = center.clone().add(0, 16.0, 0);

                    double chainY;
                    if (chainTick <= 30) {
                        // Phase 1: REALLY SLOW (creep downward 0.05 blocks/tick)
                        chainY = chainTop.getY() - (chainTick * 0.08);
                        if (chainTick % 8 == 0) {
                            world.playSound(center, Sound.BLOCK_CHAIN_STEP, 1.4f, 0.5f);
                        }
                    } else if (chainTick <= 45) {
                        // Phase 2: FAST (surge downward 0.45 blocks/tick)
                        double base = chainTop.getY() - (30 * 0.08);
                        chainY = base - ((chainTick - 30) * 0.45);
                        if (chainTick % 4 == 0) {
                            world.playSound(center, Sound.BLOCK_CHAIN_PLACE, 1.6f, 1.2f);
                        }
                    } else {
                        // Phase 3: REALLY FAST (lightning plunge 3.5 blocks/tick piercing through SIN & player!)
                        double base = chainTop.getY() - (30 * 0.08) - (15 * 0.45);
                        chainY = base - ((chainTick - 45) * 3.5);
                    }

                    // Render piercing chain beam
                    for (double y = Math.max(playerLoc.getY(), chainY); y <= chainTop.getY(); y += 0.3) {
                        Location cPt = new Location(world, center.getX(), y, center.getZ());
                        world.spawnParticle(Particle.DUST, cPt, 2, 0.06, 0, 0.06, 0,
                                new Particle.DustOptions(Color.fromRGB(200, 20, 30), 2.4f));
                        world.spawnParticle(Particle.DUST, cPt, 1, 0.04, 0, 0.04, 0,
                                new Particle.DustOptions(Color.fromRGB(10, 5, 10), 2.0f));
                    }

                    // Impact at Tick 50 (chainTick == 50)
                    if (chainTick == 50) {
                        cancel();

                        // Shatter impact
                        world.spawnParticle(Particle.FLASH, center, 10, 1.0, 1.0, 1.0, 0, Color.WHITE);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 4);
                        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.5f);
                        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.5f, 0.6f);
                        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);

                        for (Player p : world.getPlayers()) {
                            CinematicVFXUtil.flashScreen(p, "#ff0000", 20, 250, 700);
                            CinematicVFXUtil.triggerScreenShake(p, 5, 35, plugin);
                        }

                        // Remove SIN display
                        if (sinDisplay.isValid()) sinDisplay.remove();

                        // Deliver crafted SIN Relic Gem to the player!
                        plugin.getSinGemManager().giveGemToPlayer(triggeringPlayer, SinGemType.SIN);

                        Bukkit.broadcast(mm.deserialize("<dark_red><bold>✦ THE MYTHIC SIN GEM HAS BEEN FORGED IN THE ABYSS! ✦</bold></dark_red>"));
                        triggeringPlayer.sendMessage(mm.deserialize("<gold><bold>☠ YOU HAVE HARNESSED THE WEIGHT OF ALL SINS! ☠</bold></gold>"));

                        consumedSins.clear();
                        climaxInProgress = false;

                        // Re-initialize dual orbit altar for future sacrifices
                        startMainAltar(mainAltarLocation);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cleanupAltar() {
        if (dualOrbitTask != null) {
            dualOrbitTask.cancel();
            dualOrbitTask = null;
        }
        for (ItemDisplay id : dualOrbitDisplays.values()) {
            if (id != null && id.isValid()) id.remove();
        }
        dualOrbitDisplays.clear();

        for (ItemDisplay sd : activeSlotDisplays.values()) {
            if (sd != null && sd.isValid()) sd.remove();
        }
        activeSlotDisplays.clear();
        consumedSins.clear();
        activeSacrifices.clear();
        fallingSins.clear();
        climaxInProgress = false;
    }

    public void shutdown() {
        if (skyboxTask != null) {
            skyboxTask.cancel();
            skyboxTask = null;
        }
        cleanupAltar();
    }
}
