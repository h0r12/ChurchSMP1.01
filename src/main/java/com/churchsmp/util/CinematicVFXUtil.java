package com.churchsmp.util;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles immersive screen visual effects (Paper 1.21 native, zero mods/resource packs):
 * 1. Native WorldBorder Blood-Red Perimeter Vignette
 * 2. Native Frost/Cyan Freeze Screen Vignette
 * 3. Deep Void Darkness Vignette
 * 4. Vintage Chromatic Full-Screen Flash & Fade (exact ms timing, smooth alpha decay)
 * 5. Vintage Film Profiles (Sepia, Noir, Blood Haze, Solar Eclipse, Glacial Frost)
 * 6. Multi-stage Sin-Themed Screen FX (Wrath, Greed, Sloth, Pride, etc.)
 * 7. Fast Vintage Strobe Flashes
 * 8. Cinematic Title Letterboxing
 */
public final class CinematicVFXUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<UUID, WorldBorder> activeCustomBorders = new ConcurrentHashMap<>();
    private static final Map<UUID, TextDisplay> activeFlashDisplays = new ConcurrentHashMap<>();

    private CinematicVFXUtil() {}

    /**
     * Vintage Cinematic Film Profiles.
     */
    public enum VintageStyle {
        SEPIA_FILM("Vintage Sepia Film Burn", "#d4a373", 60, 220, 750, Sound.BLOCK_BEACON_ACTIVATE, 0.9f),
        NOIR_SHADOW("Vintage Monochrome Noir", "#1e1e24", 80, 250, 800, Sound.BLOCK_BELL_RESONATE, 0.6f),
        BLOOD_HAZE("Crimson Vintage Horror", "#cc0000", 40, 200, 700, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.75f),
        GLACIAL_FROST("Glacial Cold Vintage", "#48cae4", 70, 240, 850, Sound.BLOCK_GLASS_BREAK, 1.4f),
        SOLAR_ECLIPSE("Celestial Solar Flare", "#ffb703", 30, 280, 950, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f),
        VOID_ABYSS("Deep Void Blackout", "#080808", 50, 300, 1000, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.65f),
        NEON_CYBER("Vintage Synthwave", "#f72585", 40, 190, 600, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f);

        private final String displayName;
        private final String hexColor;
        private final int fadeInMs;
        private final int stayMs;
        private final int fadeOutMs;
        private final Sound sound;
        private final float pitch;

        VintageStyle(String displayName, String hexColor, int fadeInMs, int stayMs, int fadeOutMs, Sound sound, float pitch) {
            this.displayName = displayName;
            this.hexColor = hexColor;
            this.fadeInMs = fadeInMs;
            this.stayMs = stayMs;
            this.fadeOutMs = fadeOutMs;
            this.sound = sound;
            this.pitch = pitch;
        }

        public String getDisplayName() { return displayName; }
        public String getHexColor() { return hexColor; }
        public int getFadeInMs() { return fadeInMs; }
        public int getStayMs() { return stayMs; }
        public int getFadeOutMs() { return fadeOutMs; }
        public Sound getSound() { return sound; }
        public float getPitch() { return pitch; }
    }

    /**
     * Parses a hex string into an org.bukkit.Color safely.
     */
    public static Color parseHexColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) return Color.WHITE;
        String h = hexColor.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() != 6) return Color.WHITE;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    /**
     * Full-Screen Colored Flash & Fade (Smooth Alpha Decay + Bloom Particles).
     * Eliminates "just big color there" by smoothly fading opacity from peak down to 0,
     * allowing the player's vision to gracefully re-emerge without solid block artifacts.
     */
    public static void flashScreen(Player player, String hexColor, int fadeInMs, int stayMs, int fadeOutMs) {
        if (player == null || !player.isOnline()) return;

        Color baseColor = parseHexColor(hexColor);
        String hex = hexColor.toLowerCase(Locale.ROOT);
        boolean isWhite = hex.contains("ffffff") || hex.equals("#fff") || (baseColor.getRed() > 240 && baseColor.getGreen() > 240 && baseColor.getBlue() > 240);
        boolean isBlack = hex.contains("000000") || hex.contains("080808") || hex.contains("1a0505") || hex.contains("1e1e24") || (baseColor.getRed() < 25 && baseColor.getGreen() < 25 && baseColor.getBlue() < 25);

        Location eyeLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.22));

        // 1. Client-Side Bloom Shader (Always provide org.bukkit.Color to satisfy 1.21 Particle.FLASH requirement)
        try {
            player.spawnParticle(Particle.FLASH, eyeLoc, 2, 0.05, 0.05, 0.05, 0, isWhite ? Color.WHITE : baseColor);
            if (!isBlack) {
                player.spawnParticle(Particle.DUST, eyeLoc, 8, 0.2, 0.2, 0.2, 0.02, new Particle.DustOptions(baseColor, 2.0f));
            }
        } catch (Throwable ignored) {}

        int totalTicks = Math.max(6, (fadeInMs + stayMs + fadeOutMs) / 50);
        int holdTicks = Math.max(1, (fadeInMs + stayMs) / 50);
        int fadeTicks = Math.max(1, totalTicks - holdTicks);

        // 2. Darkness & Blindness for pitch-black void flashes
        if (isBlack) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, totalTicks + 4, 1, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, totalTicks + 4, 1, false, false, false));
        }

        // 3. Clear any existing active flash display for this player to prevent visual stacking
        TextDisplay oldDisplay = activeFlashDisplays.remove(player.getUniqueId());
        if (oldDisplay != null && oldDisplay.isValid()) {
            oldDisplay.remove();
        }

        final World world = player.getWorld();
        final int initialAlpha = isBlack ? 255 : (isWhite ? 245 : 230);
        final int r = baseColor.getRed();
        final int g = baseColor.getGreen();
        final int b = baseColor.getBlue();

        try {
            Location spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.12));
            TextDisplay textDisplay = world.spawn(spawnLoc, TextDisplay.class, d -> {
                d.setText("                    \n".repeat(16));
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setSeeThrough(true);
                d.setDefaultBackground(false);
                d.setTextOpacity((byte) 0);
                d.setBackgroundColor(Color.fromARGB(initialAlpha, r, g, b));
                d.setTransformation(new Transformation(
                        new Vector3f(-7.5f, -5.0f, -0.01f),
                        new AxisAngle4f(),
                        new Vector3f(15.0f, 10.0f, 0.01f),
                        new AxisAngle4f()
                ));
            });

            activeFlashDisplays.put(player.getUniqueId(), textDisplay);

            new BukkitRunnable() {
                int lived = 0;

                @Override
                public void run() {
                    lived++;
                    if (!player.isOnline() || lived > totalTicks || !textDisplay.isValid()) {
                        textDisplay.remove();
                        activeFlashDisplays.remove(player.getUniqueId(), textDisplay);
                        cancel();
                        return;
                    }

                    // Keep locked directly on the camera plane
                    Location cur = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.12));
                    textDisplay.teleport(cur);

                    // Dynamic alpha decay: hold during holdTicks, then smoothly fade out to transparent
                    if (lived > holdTicks) {
                        double progress = (double) (lived - holdTicks) / fadeTicks;
                        double decay = Math.pow(Math.max(0.0, 1.0 - progress), 2.2);
                        int currentAlpha = Math.max(0, (int) (initialAlpha * decay));

                        if (currentAlpha <= 4) {
                            textDisplay.remove();
                            activeFlashDisplays.remove(player.getUniqueId(), textDisplay);
                            cancel();
                            return;
                        }

                        textDisplay.setBackgroundColor(Color.fromARGB(currentAlpha, r, g, b));
                    }
                }
            }.runTaskTimer(ChurchSMP.getInstance(), 1L, 1L);
        } catch (Exception e) {
            ChurchSMP.getInstance().getLogger().fine("Flash textdisplay creation: " + e.getMessage());
        }
    }

    /**
     * Triggers a themed Vintage Film Wash with custom palette, soundscape, and screen response.
     */
    public static void flashVintage(Player player, VintageStyle style, ChurchSMP plugin) {
        if (player == null || !player.isOnline()) return;

        flashScreen(player, style.getHexColor(), style.getFadeInMs(), style.getStayMs(), style.getFadeOutMs());
        if (style.getSound() != null) {
            player.playSound(player.getLocation(), style.getSound(), 1.2f, style.getPitch());
        }

        switch (style) {
            case BLOOD_HAZE -> triggerRedVignette(player, 35, plugin);
            case GLACIAL_FROST -> triggerFrostVignette(player, 60);
            case VOID_ABYSS -> triggerDarknessVignette(player, 50);
            case NOIR_SHADOW -> triggerDarknessVignette(player, 30);
            case SOLAR_ECLIPSE -> {
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
                triggerRedVignette(player, 20, plugin);
            }
            case SEPIA_FILM -> {
                player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1.0f, 0.8f);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            }
            case NEON_CYBER -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.8f);
            }
        }
    }

    /**
     * Rapid vintage multi-frame strobe before dissipating.
     */
    public static void flashStrobe(Player player, String hexColor, int pulseCount, ChurchSMP plugin) {
        if (player == null || !player.isOnline()) return;

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!player.isOnline() || count >= pulseCount) {
                    cancel();
                    return;
                }
                count++;
                flashScreen(player, hexColor, 20, 35, 70);
                player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 0.7f, 1.6f);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Displays a clean cinematic letterboxed title card with custom MiniMessage formatting.
     */
    public static void triggerLetterbox(Player player, String titleText, String subtitleText, int fadeInMs, int stayMs, int fadeOutMs) {
        if (player == null || !player.isOnline()) return;

        Component mainTitle = MM.deserialize(titleText);
        Component subTitle = MM.deserialize(subtitleText);
        Title title = Title.title(
                mainTitle,
                subTitle,
                Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs), Duration.ofMillis(fadeOutMs))
        );
        player.showTitle(title);
    }

    public static void triggerLetterbox(Player player, String titleText, String subtitleText, int stayTicks) {
        triggerLetterbox(player, titleText, subtitleText, 300, stayTicks * 50, 600);
    }

    /**
     * Activates the native blood-red pulsating screen vignette by temporarily assigning
     * a custom WorldBorder with high warning distance. Automatically cleans up.
     */
    public static void triggerRedVignette(Player player, int durationTicks, ChurchSMP plugin) {
        if (player == null || !player.isOnline()) return;

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(player.getLocation());
        border.setSize(10000);
        border.setWarningDistance(999999);

        player.setWorldBorder(border);
        activeCustomBorders.put(player.getUniqueId(), border);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setWorldBorder(null);
                }
                activeCustomBorders.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /**
     * Activates the native frosty cyan freeze screen vignette.
     */
    public static void triggerFrostVignette(Player player, int freezeTicks) {
        if (player == null || !player.isOnline()) return;
        player.setFreezeTicks(Math.max(player.getFreezeTicks(), freezeTicks));
    }

    /**
     * Activates a creeping void shadow vignette.
     */
    public static void triggerDarknessVignette(Player player, int durationTicks) {
        if (player == null || !player.isOnline()) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, durationTicks, 0, false, false, false));
    }

    /**
     * Clears all active custom vignettes for a player.
     */
    public static void clearVignettes(Player player) {
        if (player == null) return;
        player.setWorldBorder(null);
        player.setFreezeTicks(0);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        activeCustomBorders.remove(player.getUniqueId());
    }

    /**
     * Comprehensive Sin-Themed Screen FX & Vignette Fade:
     * Plays the vintage colored flash, vignette pulse, and audio immersion matching the Sin.
     */
    public static void triggerSinScreenFX(Player player, SinGemType sin, ChurchSMP plugin) {
        if (player == null || !player.isOnline()) return;

        switch (sin) {
            case WRATH -> {
                flashScreen(player, "#ff1a1a", 40, 200, 700);
                triggerRedVignette(player, 35, plugin);
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.85f);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);
            }
            case GREED -> {
                flashScreen(player, "#ffaa00", 70, 240, 650);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.4f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.6f);
            }
            case GLUTTONY -> {
                flashScreen(player, "#55ff44", 60, 200, 600);
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 0.7f);
                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.9f);
            }
            case LUST -> {
                flashScreen(player, "#ff44aa", 80, 240, 700);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.8f);
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.8f);
            }
            case ENVY -> {
                flashScreen(player, "#00cc55", 60, 220, 650);
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.8f);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }
            case PRIDE -> {
                flashScreen(player, "#aa22ff", 70, 260, 800);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.5f, 0.8f);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.5f);
            }
            case SLOTH -> {
                flashScreen(player, "#00e5ff", 80, 280, 850);
                triggerFrostVignette(player, 60);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
                player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1.2f, 0.5f);
            }
            case SIN -> {
                flashScreen(player, "#ffffff", 20, 320, 1100);
                triggerRedVignette(player, 40, plugin);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1.2f);
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.6f);
            }
            case ZEN -> {
                flashScreen(player, "#d8b4fe", 30, 260, 900);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 1.5f);
                player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 1.4f);
            }
        }
    }

    /**
     * Triggers screen shaking with configurable hardness levels (1 to 5):
     * Level 1: Subtle subterranean rumble (lowest level) - gentle camera jitter & deep hum
     * Level 2: Moderate seismic tremor - rhythmic camera tilt & warden heartbeat
     * Level 3: Violent ground quake - camera shock jolts & explosive crackle
     * Level 4: Cataclysmic shockwave - heavy camera shake & sonic boom resonance
     * Level 5: Apocalyptic rift blast - maximum screen distortion & concussive disorient
     */
    public static void triggerScreenShake(Player player, int hardnessLevel, int durationTicks, ChurchSMP plugin) {
        if (player == null || !player.isOnline()) return;
        int level = Math.max(1, Math.min(5, hardnessLevel));

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                elapsed++;

                // 1. Camera Tilt & Jolt via native LivingEntity hurt animation and look offset
                float intensity = switch (level) {
                    case 1 -> 0.4f;
                    case 2 -> 0.9f;
                    case 3 -> 1.8f;
                    case 4 -> 3.2f;
                    case 5 -> 4.8f;
                    default -> 1.0f;
                };

                // Paper 1.20+ native camera tilt
                if (level >= 2 && elapsed % (level == 2 ? 14 : (level == 3 ? 8 : (level == 4 ? 4 : 2))) == 0) {
                    try {
                        player.sendHurtAnimation((float) (Math.random() * 360));
                    } catch (Throwable ignored) {}
                }

                // Micro camera jitter
                Location loc = player.getLocation();
                float dy = (float) ((Math.random() - 0.5) * intensity * 0.7);
                float dp = (float) ((Math.random() - 0.5) * intensity * 0.5);
                loc.setYaw(loc.getYaw() + dy);
                loc.setPitch(Math.max(-90f, Math.min(90f, loc.getPitch() + dp)));
                player.teleport(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

                // Sound design based on level
                if (elapsed == 1 || elapsed % 20 == 0) {
                    switch (level) {
                        case 1 -> {
                            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.6f);
                            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.8f);
                        }
                        case 2 -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.7f);
                            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 0.8f);
                        }
                        case 3 -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);
                            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                        }
                        case 4 -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.8f);
                            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.7f);
                        }
                        case 5 -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
                            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
                            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.5f);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void triggerScreenShakeAll(int hardnessLevel, int durationTicks, ChurchSMP plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            triggerScreenShake(p, hardnessLevel, durationTicks, plugin);
        }
    }

    /**
     * Cleans up all active screen flash text displays.
     */
    public static void cleanupAllFlashes() {
        for (TextDisplay td : activeFlashDisplays.values()) {
            if (td != null && td.isValid()) {
                td.remove();
            }
        }
        activeFlashDisplays.clear();
    }
}

