package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WandHologramTask extends BukkitRunnable {

    private final ChurchSMP plugin;
    private final ParticleAnimationManager animationManager;
    private final ParticleAlias onionSkinParticle;
    private final ParticleAlias anchorParticle;

    public WandHologramTask(ChurchSMP plugin, ParticleAnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.onionSkinParticle = new ParticleAlias(Particle.DUST, Color.fromRGB(160, 160, 180), 0.65f, "ghost");
        this.anchorParticle = new ParticleAlias(Particle.DUST, Color.fromRGB(216, 180, 254), 0.8f, "anchor");
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (!animationManager.isWand(mainHand)) continue;

            WandSession session = animationManager.getSession(player);
            if (session.isLooping() || session.isRecording()) continue;

            Location eyeLoc = player.getEyeLocation();
            int curFrameIdx = session.getCurrentFrame();
            AnimationFrame curFrame = session.getAnimation().getFrames().get(curFrameIdx);

            // 1. Render Onion Skin (Ghost of previous frame)
            if (curFrameIdx > 0) {
                AnimationFrame prevFrame = session.getAnimation().getFrames().get(curFrameIdx - 1);
                if (prevFrame != null && !prevFrame.getElements().isEmpty()) {
                    spawnGhostFrame(eyeLoc, prevFrame);
                }
            }

            // 2. Render Active Current Frame Hologram
            if (curFrame != null && !curFrame.getElements().isEmpty()) {
                curFrame.spawn(eyeLoc, plugin);
            } else {
                // If frame is empty, show focal center anchor at (activeX, activeY, 2.0 + activeZ)
                Vector dir = eyeLoc.getDirection().normalize();
                Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                Vector up = right.clone().crossProduct(dir).normalize();

                Location centerLoc = eyeLoc.clone()
                        .add(right.clone().multiply(session.getActiveXOffset()))
                        .add(up.clone().multiply(session.getActiveYOffset()))
                        .add(dir.clone().multiply(2.0 + session.getActiveZOffset()));
                anchorParticle.spawn(centerLoc, 1, 0.0, 1.0, plugin);
            }
        }
    }

    private void spawnGhostFrame(Location eyeLoc, AnimationFrame frame) {
        Vector dir = eyeLoc.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        for (FrameElement elem : frame.getElements()) {
            Vector offset = elem.getLocalOffset();
            Location pLoc = eyeLoc.clone()
                    .add(right.clone().multiply(offset.getX()))
                    .add(up.clone().multiply(offset.getY()))
                    .add(dir.clone().multiply(offset.getZ()));

            onionSkinParticle.spawn(pLoc, 1, 0.0, 0.7, plugin);
        }
    }
}
