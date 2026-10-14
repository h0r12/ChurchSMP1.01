package com.churchsmp.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A physical-looking chain made of small red BlockDisplay links.
 * Links alternate 90° roll like real chain links and sag slightly in the middle.
 * Call {@link #update(Location, Location)} every tick, and {@link #remove()} when done.
 */
public class RedChain {

    private static final BlockData LINK_A = Material.REDSTONE_BLOCK.createBlockData();
    private static final BlockData LINK_B = Material.RED_CONCRETE.createBlockData();
    private static final float THICKNESS = 0.11f;
    private static final float WIDTH = 0.22f;

    private final List<BlockDisplay> links = new ArrayList<>();
    private boolean removed = false;

    public RedChain(Location start, int linkCount) {
        World world = start.getWorld();
        if (world == null) return;
        for (int i = 0; i < linkCount; i++) {
            final int idx = i;
            BlockDisplay link = world.spawn(start, BlockDisplay.class, d -> {
                d.setBlock(idx % 2 == 0 ? LINK_A : LINK_B);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setInterpolationDuration(1);
                d.setViewRange(2.0f);
            });
            links.add(link);
        }
    }

    public boolean isRemoved() {
        return removed;
    }

    /** Re-positions every link along the line from -> to (with gravity sag). */
    public void update(Location from, Location to) {
        if (removed || links.isEmpty() || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        Vector diff = to.toVector().subtract(from.toVector());
        double len = diff.length();
        if (len < 0.05) return;
        Vector3f dir = new Vector3f((float) (diff.getX() / len), (float) (diff.getY() / len), (float) (diff.getZ() / len));

        int n = links.size();
        float linkLen = (float) Math.min(0.6, Math.max(0.12, (len / n) * 1.15));
        double sagAmount = Math.min(0.7, len * 0.04);

        Quaternionf baseRot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);

        for (int i = 0; i < n; i++) {
            BlockDisplay link = links.get(i);
            if (!link.isValid()) continue;

            double frac = (i + 0.5) / n;
            double sag = -Math.sin(frac * Math.PI) * sagAmount;
            Location pos = from.clone().add(diff.clone().multiply(frac)).add(0, sag, 0);

            // Alternate roll by 90° like real interlocking chain links
            Quaternionf rot = new Quaternionf(baseRot).rotateZ(i % 2 == 0 ? 0f : (float) (Math.PI / 2));
            Vector3f scale = new Vector3f(WIDTH, THICKNESS, linkLen);
            // Center the block model (0..1 cube) around the entity origin
            Vector3f translation = rot.transform(new Vector3f(-scale.x / 2f, -scale.y / 2f, -scale.z / 2f));

            link.teleport(pos);
            link.setInterpolationDelay(0);
            link.setTransformation(new Transformation(translation, rot, scale, new Quaternionf()));
        }
    }

    public void remove() {
        if (removed) return;
        removed = true;
        for (BlockDisplay link : links) {
            if (link.isValid()) link.remove();
        }
        links.clear();
    }
}
