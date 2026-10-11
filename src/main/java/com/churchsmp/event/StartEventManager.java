package com.churchsmp.event;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
    private final Set<UUID> readyPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> attunedPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, ItemStack> cachedChestplates = new ConcurrentHashMap<>();
    private final Set<UUID> glidingPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<SinGemType, Location> sinSoulLocations = new ConcurrentHashMap<>();
    private final List<ItemDisplay> activeSoulDisplays = new ArrayList<>();
    private final List<TextDisplay> activeTextDisplays = new ArrayList<>();

    private double originalBorderSize = 10000;
    private Location originalBorderCenter = null;
    private BukkitTask mainLoopTask = null;

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
     * Initiates the Genesis Start Event via /churchadmin start.
     */
    public boolean startEvent(Player admin) {
        if (state != EventState.IDLE) {
            if (admin != null) {
                admin.sendMessage(miniMessage.deserialize("<red>The Start Event is already in progress!</red>"));
            }
            return false;
        }

        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        this.originalBorderSize = border.getSize();
        this.originalBorderCenter = border.getCenter();

        // 1. Shrink World Border to 25 blocks at spawn (0, 0)
        border.setCenter(0.5, 0.5);
        border.setSize(25.0);

        readyPlayers.clear();
        attunedPlayers.clear();
        cachedChestplates.clear();
        glidingPlayers.clear();
        sinSoulLocations.clear();
        cleanupDisplays();

        this.state = EventState.READY_CHECK;

        // Announcement & Sounds
        Title title = Title.title(
                miniMessage.deserialize("<gradient:#FFD700:#FF4500><bold>✦ THE GENESIS CEREMONY ✦</bold></gradient>"),
                miniMessage.deserialize("<white>World Border set to 25 blocks! Type <yellow><bold>/church ready</bold></yellow>!</white>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        Location spawnLoc = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLoc);
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.4f, 1.0f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.8f);
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
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        player.sendMessage(miniMessage.deserialize("<green>✦ Soul Aligned! You are marked as READY. ✦</green>"));

        updateReadyActionBar();

        // Check if all online players are ready
        int totalOnline = Bukkit.getOnlinePlayers().size();
        if (totalOnline > 0 && readyPlayers.size() >= totalOnline) {
            triggerSoulExtraction();
        }

        return true;
    }

    private void startReadyCheckActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != EventState.READY_CHECK) {
                    cancel();
                    return;
                }
                updateReadyActionBar();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateReadyActionBar() {
        int readyCount = readyPlayers.size();
        int total = Bukkit.getOnlinePlayers().size();

        StringBuilder names = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean r = readyPlayers.contains(p.getUniqueId());
            if (names.length() > 0) names.append(", ");
            if (r) {
                names.append("<green>").append(p.getName()).append("</green>");
            } else {
                names.append("<red>").append(p.getName()).append("</red>");
            }
        }

        Component ab = miniMessage.deserialize(
                "<gold>✦ Ready: </gold><yellow><bold>" + readyCount + "/" + total + "</bold></yellow> " +
                "<dark_gray>[</dark_gray>" + names + "<dark_gray>]</dark_gray> <gray>• Type /church ready</gray>"
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(ab);
        }
    }

    /**
     * Phase 2: Tree-Vein Soul Extraction to (0, 0).
     */
    private void triggerSoulExtraction() {
        this.state = EventState.SOUL_EXTRACTION;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.5f);
            p.sendActionBar(miniMessage.deserialize("<gradient:#FFFFFF:#ADD8E6><bold>✦ SOULS CONVERGING TO CENTER (0, 0)... ✦</bold></gradient>"));
        }

        World world = Bukkit.getWorlds().get(0);
        Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.5, 0.5);

        // Map of player positions to animate tree-vein soul particles
        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());

        new BukkitRunnable() {
            int ticks = 0;
            final int tremblingTicks = 60; // 3 seconds of trembling tree veins
            final Map<UUID, Location> currentSoulLocs = new HashMap<>();

            @Override
            public void run() {
                ticks++;

                // 1. Initial 3 seconds: Tree-vein descending particle string with unstable jitter
                if (ticks <= tremblingTicks) {
                    for (Player p : participants) {
                        if (!p.isOnline()) continue;
                        Location head = p.getLocation().add(0, 1.8, 0);

                        // Branching downward from y+12
                        for (double y = 0; y <= 10; y += 0.5) {
                            double swayX = Math.sin((ticks * 0.4) + y) * 0.25 + (Math.random() - 0.5) * 0.12;
                            double swayZ = Math.cos((ticks * 0.3) + y) * 0.25 + (Math.random() - 0.5) * 0.12;
                            Location veinPt = head.clone().add(swayX, y, swayZ);

                            world.spawnParticle(Particle.DUST, veinPt, 1, 0.05, 0.05, 0.05, 0,
                                    new Particle.DustOptions(Color.fromRGB(245, 248, 255), 1.2f));
                            if (y > 4 && Math.random() < 0.25) {
                                // Branch off
                                world.spawnParticle(Particle.END_ROD, veinPt.clone().add((Math.random() - 0.5) * 0.4, 0, (Math.random() - 0.5) * 0.4), 1, 0, 0, 0, 0.01);
                            }
                        }
                    }

                    if (ticks % 10 == 0) {
                        for (Player p : participants) {
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + (ticks * 0.01f));
                        }
                    }
                    return;
                }

                // Initialize soul positions at start of drift
                if (ticks == tremblingTicks + 1) {
                    for (Player p : participants) {
                        if (p.isOnline()) {
                            currentSoulLocs.put(p.getUniqueId(), p.getLocation().add(0, 1.8, 0));
                            p.playSound(p.getLocation(), Sound.ENTITY_VEX_AMBIENT, 1.2f, 0.6f);
                        }
                    }
                }

                // 2. Souls slowly drift toward (0, 0)
                boolean allArrived = true;
                for (Player p : participants) {
                    Location cur = currentSoulLocs.get(p.getUniqueId());
                    if (cur == null) continue;

                    Vector dir = center.toVector().subtract(cur.toVector());
                    double dist = dir.length();

                    if (dist > 1.2) {
                        allArrived = false;
                        dir.normalize().multiply(0.28); // Slow graceful drift
                        cur.add(dir);

                        world.spawnParticle(Particle.DUST, cur, 2, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(230, 240, 255), 1.4f));
                        world.spawnParticle(Particle.END_ROD, cur, 1, 0.02, 0.02, 0.02, 0.01);
                    }
                }

                // Arrived at (0, 0) -> Blinding flash!
                if (allArrived || ticks > 240) {
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

        // 1. Blinding Flash of Light
        world.spawnParticle(Particle.FLASH, center, 4, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.SONIC_BOOM, center, 1);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.9f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.1f);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        }

        // 2. Expand into spinning star cycling through 7 Sins colors
        final SinGemType[] sins = SinGemType.values(); // WRATH, GREED, GLUTTONY, LUST, ENVY, PRIDE, SLOTH

        new BukkitRunnable() {
            int ticks = 0;
            final int starDuration = 100; // 5 seconds of star spinning before souls appear

            @Override
            public void run() {
                ticks++;

                int sinIdx = (ticks / 10) % sins.length;
                SinGemType currentSin = sins[sinIdx];
                Color c = Color.fromRGB(
                        (currentSin.getColor().value() >> 16) & 0xFF,
                        (currentSin.getColor().value() >> 8) & 0xFF,
                        currentSin.getColor().value() & 0xFF
                );
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.5f);

                // Render spinning 5-pointed star
                double rot = ticks * 0.12;
                renderSpinningStar(center, rot, 2.8, dust);

                // Subtle audio hum
                if (ticks % 8 == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.8f, 1.0f + (sinIdx * 0.08f));
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
     * Phase 4: 7 Spinning Sin Souls manifest within 20 blocks of spawn for players to choose.
     */
    private void spawnSinSoulsRing(Location center) {
        this.state = EventState.SIN_CHOICE;
        World world = center.getWorld();

        SinGemType[] sins = SinGemType.values();
        double ringRadius = 10.0; // 10 blocks radius (fits safely inside 25-block border)
        double angleStep = (2 * Math.PI) / sins.length;

        sinSoulLocations.clear();
        cleanupDisplays();

        for (int i = 0; i < sins.length; i++) {
            SinGemType sin = sins[i];
            double angle = i * angleStep;
            double x = center.getX() + Math.cos(angle) * ringRadius;
            double z = center.getZ() + Math.sin(angle) * ringRadius;
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1.2;

            Location soulLoc = new Location(world, x, y, z);
            sinSoulLocations.put(sin, soulLoc);

            // Spawn floating ItemDisplay
            ItemDisplay id = world.spawn(soulLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(sin.getIconMaterial()));
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.8f, 0.8f, 0.8f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                d.getPersistentDataContainer().set(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING, sin.name());
            });
            activeSoulDisplays.add(id);

            // Floating TextDisplay title
            TextDisplay td = world.spawn(soulLoc.clone().add(0, 1.1, 0), TextDisplay.class, t -> {
                t.text(miniMessage.deserialize("<bold>" + sin.getDisplayName() + "</bold>"));
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            });
            activeTextDisplays.add(td);
        }

        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<yellow><bold>✦ THE 7 SIN SOULS HAVE MANIFESTED! ✦</bold></yellow>"));
        Bukkit.broadcast(miniMessage.deserialize("<gray>Approach a spinning soul and right-click to choose your attunement!</gray>"));
        Bukkit.broadcast(miniMessage.deserialize("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 1.0f);
        }

        // Active animation task for spinning souls
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

                    // Gentle floating bob + orbiting particle aura
                    double rad = Math.toRadians((ticks * 4) % 360);
                    Location pLoc = loc.clone().add(Math.cos(rad) * 0.6, 0.4 + Math.sin(ticks * 0.1) * 0.15, Math.sin(rad) * 0.6);
                    world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.3f));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.3, 0), 1, 0.05, 0.05, 0.05, 0.01);
                }

                // Keep spinning the central star
                renderSpinningStar(center, ticks * 0.08, 3.0, new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Handles player interaction with a spinning Sin Soul.
     */
    @EventHandler
    public void onSoulInteract(PlayerInteractAtEntityEvent event) {
        if (state != EventState.SIN_CHOICE) return;
        if (!(event.getRightClicked() instanceof ItemDisplay id)) return;

        String sinName = id.getPersistentDataContainer().get(new NamespacedKey(plugin, "sin_soul"), PersistentDataType.STRING);
        if (sinName == null) return;

        Player player = event.getPlayer();
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

        // Send confirmation prompt in chat with clickable button
        Component confirmButton = Component.text("[CLICK TO CONFIRM]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/church confirm " + sin.name()))
                .hoverEvent(HoverEvent.showText(Component.text("Attune your soul to " + sin.getDisplayName(), NamedTextColor.YELLOW)));

        player.sendMessage(miniMessage.deserialize("<gold>✦ Do you choose to attune to <bold>" + sin.getDisplayName() + "</bold>? </gold>").append(confirmButton));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
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

        // Attune via SinGemManager
        plugin.getSinGemManager().forceAttune(player, sin);
        plugin.getSinGemManager().giveGemToPlayer(player, sin);

        // Grant Invisibility
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0, false, false, true));

        // Audio & Visual pulse
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        Color c = Color.fromRGB(
                (sin.getColor().value() >> 16) & 0xFF,
                (sin.getColor().value() >> 8) & 0xFF,
                sin.getColor().value() & 0xFF
        );
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(c, 1.6f));

        Bukkit.broadcast(miniMessage.deserialize("<light_purple>✦ <bold>" + player.getName() + "</bold> has embraced the Sin of " + sin.getDisplayName() + "! (" + attunedPlayers.size() + "/" + Bukkit.getOnlinePlayers().size() + ")</light_purple>"));

        // Check if all online players have chosen their sin
        int totalOnline = Bukkit.getOnlinePlayers().size();
        if (attunedPlayers.size() >= totalOnline) {
            triggerVoidClimaxAndLaunch();
        }

        return true;
    }

    /**
     * Phase 5: Void Climax, Lightning, & Sky Elytra Launch.
     */
    private void triggerVoidClimaxAndLaunch() {
        this.state = EventState.VOID_CLIMAX;
        if (mainLoopTask != null) mainLoopTask.cancel();
        cleanupDisplays();

        World world = Bukkit.getWorlds().get(0);
        Location center = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 2.0, 0.5);

        Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>✦ ALL SINS EMBRACED! THE ABYSS CONSUMES THE REALM! ✦</bold></dark_red>"));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // 4 seconds of black hole & widening beacon beam

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

                // 2. Widening Beacon Beam shooting upwards
                for (double y = 0; y <= 35; y += 1.2) {
                    for (int d = 0; d < 360; d += 90) {
                        double rad = Math.toRadians(d + (ticks * 8));
                        Location bPt = center.clone().add(Math.cos(rad) * beamRadius, y, Math.sin(rad) * beamRadius);
                        world.spawnParticle(Particle.END_ROD, bPt, 1, 0, 0, 0, 0.01);
                    }
                }

                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.4f, 0.5f + (ticks * 0.01f));
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f + (ticks * 0.01f));
                }

                // Climax at 4 seconds
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

        // 2. Restore World Border
        WorldBorder border = world.getWorldBorder();
        if (originalBorderCenter != null) {
            border.setCenter(originalBorderCenter);
        }
        border.setSize(originalBorderSize > 25 ? originalBorderSize : 10000);

        Title launchTitle = Title.title(
                miniMessage.deserialize("<gradient:#FFD700:#FFA500><bold>✦ DESCEND UPON THE REALM ✦</bold></gradient>"),
                miniMessage.deserialize("<yellow>Glide to your starting sanctuary!</yellow>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        // 3. Launch everyone into air to Y:200 with temporary Elytra
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
            p.setVelocity(loc.getDirection().normalize().multiply(1.5).setY(0.2));
            p.setGliding(true);

            p.showTitle(launchTitle);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.5f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 1.0f);
        }

        Bukkit.broadcast(miniMessage.deserialize("<gold>✦ The Genesis Ceremony has concluded! Realm borders expanded to " + (int) border.getSize() + " blocks! ✦</gold>"));
    }

    /**
     * Cleans up temporary Elytra and restores original chestplate upon landing.
     */
    @EventHandler
    public void onPlayerLand(PlayerMoveEvent event) {
        if (!glidingPlayers.contains(event.getPlayer().getUniqueId())) return;
        Player player = event.getPlayer();

        if (player.isOnGround()) {
            glidingPlayers.remove(player.getUniqueId());

            // Restore chestplate
            ItemStack original = cachedChestplates.remove(player.getUniqueId());
            if (original != null) {
                player.getInventory().setChestplate(original);
            } else {
                player.getInventory().setChestplate(null);
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.0f);
            player.sendMessage(miniMessage.deserialize("<green>✦ Safely landed! Your gear has been restored. ✦</green>"));

            if (glidingPlayers.isEmpty()) {
                // All players have safely landed
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
        if (glidingPlayers.remove(p.getUniqueId())) {
            ItemStack original = cachedChestplates.remove(p.getUniqueId());
            if (original != null) {
                p.getInventory().setChestplate(original);
            } else {
                p.getInventory().setChestplate(null);
            }
        }
    }

    @EventHandler
    public void onPlayerJoinDuringEvent(org.bukkit.event.player.PlayerJoinEvent event) {
        if (isEventActive()) {
            World world = Bukkit.getWorlds().get(0);
            Location spawnLoc = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5);
            event.getPlayer().teleport(spawnLoc);
            event.getPlayer().sendMessage(miniMessage.deserialize("<gold>✦ A Genesis Ceremony is currently active at Spawn! Type <yellow><bold>/church ready</bold></yellow>! ✦</gold>"));
            updateReadyActionBar();
        }
    }

    public void cleanupDisplays() {
        for (ItemDisplay id : activeSoulDisplays) {
            if (id != null && id.isValid()) id.remove();
        }
        activeSoulDisplays.clear();

        for (TextDisplay td : activeTextDisplays) {
            if (td != null && td.isValid()) td.remove();
        }
        activeTextDisplays.clear();
    }
}
