package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ParticleAnimationManager {

    private final ChurchSMP plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, WandSession> sessions;
    private final Map<String, ParticleAnimation> savedAnimations;
    private final MiniMessage miniMessage;
    private final File animationsFile;
    private FileConfiguration animationsConfig;

    public ParticleAnimationManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "particle_wand");
        this.sessions = new HashMap<>();
        this.savedAnimations = new HashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
        this.animationsFile = new File(plugin.getDataFolder(), "animations.yml");

        loadAnimations();
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    public WandSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), WandSession::new);
    }

    public ItemStack createWandItem() {
        ItemStack wand = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<!i><gradient:#ffffff:#d8b4fe><bold>Particle Wand</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(miniMessage.deserialize("<!i><gray>Keyframe Animation & Math VFX Studio</gray>"));
            lore.add(Component.empty());
            lore.add(miniMessage.deserialize("<!i><#d8b4fe>Left Click: <white>Stamp Active Preset</white></#d8b4fe>"));
            lore.add(miniMessage.deserialize("<!i><#d8b4fe>Shift + Left Click: <white>Open Studio Chest GUI</white></#d8b4fe>"));
            lore.add(miniMessage.deserialize("<!i><#d8b4fe>Right Click: <white>Preview Animation</white></#d8b4fe>"));
            lore.add(miniMessage.deserialize("<!i><#d8b4fe>Sneak + Right Click: <white>Toggle 3D Loop Preview</white></#d8b4fe>"));
            lore.add(miniMessage.deserialize("<!i><gold>F: <white>Redo</white> <dark_gray>|</dark_gray> <gold>Shift + F: <white>Undo</white></gold>"));
            lore.add(miniMessage.deserialize("<!i><aqua>Q: <white>Frame +1</white> <dark_gray>|</dark_gray> <aqua>Shift + Q: <white>Frame -1</white></aqua>"));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void playAnimation(Location anchor, ParticleAnimation animation, Player playerAnchor) {
        if (anchor == null || animation == null) return;
        int maxFrame = animation.getMaxFrameIndex();

        new BukkitRunnable() {
            int currentTick = 0;

            @Override
            public void run() {
                Location currentAnchor = (playerAnchor != null && playerAnchor.isOnline()) ? playerAnchor.getEyeLocation() : anchor;

                AnimationFrame frame = animation.getFrames().get(currentTick);
                if (frame != null) {
                    frame.spawn(currentAnchor, plugin);
                }

                currentTick++;
                if (currentTick > maxFrame) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void playPreview(Player player, WandSession session) {
        if (player == null || session == null) return;
        ParticleAnimation anim = session.getAnimation();
        if (anim.getFrames().isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<gray>Animation timeline is empty. Left click to stamp shapes.</gray>"));
            return;
        }

        int maxFrame = Math.max(session.getCurrentFrame(), anim.getMaxFrameIndex());
        long speed = Math.max(1L, session.getActiveSpeed());

        player.sendMessage(miniMessage.deserialize("<#d8b4fe>Playing preview <dark_gray>(<white>Frames 0 ➔ " + maxFrame + "</white> @ " + speed + " ticks/f)</dark_gray></#d8b4fe>"));

        new BukkitRunnable() {
            int frameIdx = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                AnimationFrame frame = anim.getFrames().get(frameIdx);
                if (frame != null) {
                    frame.spawn(player.getEyeLocation(), plugin);
                }
                frameIdx++;
                if (frameIdx > maxFrame) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, speed);
    }

    public void toggleLoop(Player player, WandSession session) {
        if (player == null || session == null) return;

        if (session.isLooping()) {
            session.stopLoop();
            player.sendMessage(miniMessage.deserialize("<gray>Loop preview stopped.</gray>"));
            return;
        }

        ParticleAnimation anim = session.getAnimation();
        if (anim.getFrames().isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<gray>Timeline is empty. Cannot loop.</gray>"));
            return;
        }

        int maxFrame = Math.max(session.getCurrentFrame(), anim.getMaxFrameIndex());
        int pauseFrames = 4;
        int loopDuration = maxFrame + pauseFrames;
        long speed = Math.max(1L, session.getActiveSpeed());

        player.sendMessage(miniMessage.deserialize("<#d8b4fe>3D Loop preview active <gray>(@ " + speed + " ticks/f)</gray>. <gray>Sneak + Right Click again to stop.</gray></#d8b4fe>"));

        Location staticAnchor = player.getEyeLocation();

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                int localFrame = tick % loopDuration;
                if (localFrame <= maxFrame) {
                    AnimationFrame frame = anim.getFrames().get(localFrame);
                    if (frame != null) {
                        frame.spawn(staticAnchor, plugin);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, speed);

        session.setLoopTask(task);
    }

    public void startMotionRecording(Player player, WandSession session, int durationTicks, double distance) {
        if (player == null || session == null) return;
        if (session.isRecording()) {
            session.stopRecording();
            player.sendMessage(miniMessage.deserialize("<gray>Motion recording cancelled.</gray>"));
            return;
        }

        final int duration = Math.max(5, Math.min(100, durationTicks));
        final double dist = Math.max(1.0, Math.min(10.0, distance));

        session.setRecording(true);
        Location startAnchor = player.getEyeLocation().clone();

        org.bukkit.util.Vector forwardAxis = startAnchor.getDirection().normalize();
        org.bukkit.util.Vector upDir = new org.bukkit.util.Vector(0, 1, 0);
        org.bukkit.util.Vector rightAxis;
        if (Math.abs(forwardAxis.getY()) > 0.98) {
            rightAxis = new org.bukkit.util.Vector(1, 0, 0);
        } else {
            rightAxis = forwardAxis.clone().crossProduct(upDir).normalize();
        }
        org.bukkit.util.Vector upAxis = rightAxis.clone().crossProduct(forwardAxis).normalize();

        int startFrame = session.getCurrentFrame();
        player.sendMessage(miniMessage.deserialize("<#d8b4fe>Real-Time Motion Recording started! <white>Sweep your crosshair across the screen...</white></#d8b4fe>"));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            org.bukkit.util.Vector lastLocal = null;

            @Override
            public void run() {
                if (!player.isOnline() || !session.isRecording()) {
                    session.stopRecording();
                    cancel();
                    return;
                }

                Location currentEye = player.getEyeLocation();
                Location tipWorld = currentEye.clone().add(currentEye.getDirection().normalize().multiply(dist));

                // Project tip into startAnchor local coordinate frame
                org.bukkit.util.Vector delta = tipWorld.toVector().subtract(startAnchor.toVector());
                double lx = delta.dot(rightAxis);
                double ly = delta.dot(upAxis);
                double lz = delta.dot(forwardAxis);
                org.bukkit.util.Vector curLocal = new org.bukkit.util.Vector(lx, ly, lz);

                int targetFrameIdx = startFrame + tick;
                int lifespan = Math.max(1, session.getActiveLifespan());
                boolean shrink = session.isDecayShrink();

                org.bukkit.util.Vector mid = null;
                if (lastLocal != null && lastLocal.distance(curLocal) > 0.3) {
                    mid = lastLocal.clone().add(curLocal).multiply(0.5);
                }

                // Add sample with decay persistence across lifespan frames
                for (int lf = 0; lf < lifespan; lf++) {
                    int subFrameIdx = targetFrameIdx + lf;
                    AnimationFrame frame = session.getAnimation().getOrCreateFrame(subFrameIdx);
                    double decayScale = (shrink && lifespan > 1)
                            ? session.getActiveScale() * Math.max(0.15, 1.0 - (0.85 * ((double) lf / lifespan)))
                            : session.getActiveScale();

                    frame.addElement(new FrameElement(session.getActiveParticle(), curLocal, decayScale, 1, 0.0, EasingType.LINEAR));
                    if (mid != null) {
                        frame.addElement(new FrameElement(session.getActiveParticle(), mid, decayScale, 1, 0.0, EasingType.LINEAR));
                    }
                }
                lastLocal = curLocal.clone();

                // Live visual particle feedback at world tip
                session.getActiveParticle().spawn(tipWorld, 1, 0.0, session.getActiveScale(), plugin);
                player.playSound(tipWorld, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, (float) (1.0 + (tick * 0.03)));

                String recBar = String.format(java.util.Locale.ROOT, "<red><bold>● REC</bold></red> <white>Frame %d</white> <dark_gray>(%d / %d ticks | Decay: %df)</dark_gray>", targetFrameIdx, tick + 1, duration, lifespan);
                player.sendActionBar(miniMessage.deserialize(recBar));

                tick++;
                if (tick >= duration) {
                    session.stopRecording();
                    session.setCurrentFrame(startFrame + duration + (lifespan > 1 ? lifespan - 1 : 0));
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Recorded <white>" + duration + " frames</white> of real-time motion <gray>(Lifespan: " + lifespan + " frames)</gray>! Right Click to preview, Shift+F to undo.</#d8b4fe>"));
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        session.setRecordTask(task);
    }

    public void saveAnimationToConfig(ParticleAnimation animation) {
        if (animation == null) return;
        savedAnimations.put(animation.getName(), animation);

        animationsConfig.set("animations." + animation.getName() + ".portable", animation.toPortableString());
        try {
            animationsConfig.save(animationsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save animations.yml: " + e.getMessage());
        }
    }

    public ParticleAnimation getSavedAnimation(String name) {
        if (name == null) return null;
        return savedAnimations.get(name);
    }

    public Set<String> getSavedAnimationNames() {
        return savedAnimations.keySet();
    }

    public void loadAnimations() {
        savedAnimations.clear();
        if (!animationsFile.exists()) {
            plugin.saveResource("animations.yml", false);
        }
        animationsConfig = YamlConfiguration.loadConfiguration(animationsFile);

        ConfigurationSection section = animationsConfig.getConfigurationSection("animations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String portable = section.getString(key + ".portable");
                if (portable != null && !portable.isBlank()) {
                    ParticleAnimation anim = ParticleAnimation.fromPortableString(portable);
                    if (anim != null) {
                        anim.setName(key);
                        savedAnimations.put(key, anim);
                    }
                }
            }
        }

        // Ensure default presets exist
        if (!savedAnimations.containsKey("undying_star_slash")) {
            ParticleAnimation defaultSlash = new ParticleAnimation("undying_star_slash");
            // F0: blade swing start
            defaultSlash.getOrCreateFrame(0).addElement(new FrameElement(ParticleAlias.fromString("dustwhite"), new org.bukkit.util.Vector(-1.0, 0.6, 1.5), 1.6, 1, 0, EasingType.LINEAR));
            // F1: blade mid
            defaultSlash.getOrCreateFrame(1).addElement(new FrameElement(ParticleAlias.fromString("dustzen"), new org.bukkit.util.Vector(0.0, 0.2, 2.0), 1.5, 1, 0, EasingType.EASE_OUT_CUBIC));
            // F2: star finale
            for (org.bukkit.util.Vector pt : MathPreset.STAR_4.generatePoints(1.5)) {
                defaultSlash.getOrCreateFrame(2).addElement(new FrameElement(ParticleAlias.fromString("dustzen"), pt.clone().setZ(2.5), 1.5, 1, 0, EasingType.EASE_OUT_BACK));
            }
            defaultSlash.getOrCreateFrame(2).addElement(new FrameElement(ParticleAlias.fromString("fireworkexplosion"), new org.bukkit.util.Vector(0, 0, 2.5), 1.0, 1, 0, EasingType.LINEAR));
            saveAnimationToConfig(defaultSlash);
        }

        if (!savedAnimations.containsKey("zen_bolt_veins")) {
            ParticleAnimation boltVeins = new ParticleAnimation("zen_bolt_veins");
            for (org.bukkit.util.Vector pt : MathPreset.BOLT_VEINS.generatePoints(1.2)) {
                boltVeins.getOrCreateFrame(0).addElement(new FrameElement(ParticleAlias.fromString("dustzen"), pt, 1.4, 1, 0, EasingType.LINEAR));
            }
            saveAnimationToConfig(boltVeins);
        }
    }
}
