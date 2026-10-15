package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class FrameElement {

    private final ParticleAlias alias;
    private final Vector localOffset;
    private final double scale;
    private final int count;
    private final double speed;
    private final EasingType easing;

    public FrameElement(ParticleAlias alias, Vector localOffset, double scale, int count, double speed, EasingType easing) {
        this.alias = alias;
        this.localOffset = localOffset != null ? localOffset : new Vector(0, 0, 0);
        this.scale = scale > 0 ? scale : 1.0;
        this.count = Math.max(1, count);
        this.speed = speed;
        this.easing = easing != null ? easing : EasingType.LINEAR;
    }

    public ParticleAlias getAlias() {
        return alias;
    }

    public Vector getLocalOffset() {
        return localOffset;
    }

    public double getScale() {
        return scale;
    }

    public int getCount() {
        return count;
    }

    public double getSpeed() {
        return speed;
    }

    public EasingType getEasing() {
        return easing;
    }

    /**
     * Converts local caret coordinates (^left/right, ^up, ^forward) to world coordinates.
     */
    public Location toWorldLocation(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) return null;

        Vector forward = anchor.getDirection().normalize();
        Vector upDir = new Vector(0, 1, 0);
        Vector right;

        if (Math.abs(forward.getY()) > 0.98) {
            right = new Vector(1, 0, 0);
        } else {
            right = forward.clone().crossProduct(upDir).normalize();
        }
        Vector localUp = right.clone().crossProduct(forward).normalize();

        double x = localOffset.getX();
        double y = localOffset.getY();
        double z = localOffset.getZ();

        Location worldLoc = anchor.clone();
        worldLoc.add(right.multiply(x));
        worldLoc.add(localUp.multiply(y));
        worldLoc.add(forward.multiply(z));

        return worldLoc;
    }

    /**
     * Spawns this element in the world relative to anchor.
     */
    public void spawn(Location anchor, ChurchSMP plugin) {
        Location target = toWorldLocation(anchor);
        if (target != null && alias != null) {
            alias.spawn(target, count, speed, scale, plugin);
        }
    }

    /**
     * Serializes this element into portable text format.
     */
    public String serialize() {
        String pStr = (alias != null) ? alias.getRawIdentifier() : "dustzen";
        String offStr = String.format(java.util.Locale.ROOT, "^%.2f,^%.2f,^%.2f", localOffset.getX(), localOffset.getY(), localOffset.getZ());
        return pStr + ":" + offStr + ":" + String.format(java.util.Locale.ROOT, "%.2f", scale) + ":" + count + ":" + easing.name();
    }

    public static FrameElement deserialize(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            String[] parts = str.split(":");
            if (parts.length < 2) return null;

            ParticleAlias alias = ParticleAlias.fromString(parts[0]);
            Vector offset = new Vector(0, 0, 0);

            String coords = parts[1].replace("^", "").replace("~", "");
            String[] cParts = coords.split("[, ]");
            if (cParts.length >= 3) {
                double x = Double.parseDouble(cParts[0]);
                double y = Double.parseDouble(cParts[1]);
                double z = Double.parseDouble(cParts[2]);
                offset = new Vector(x, y, z);
            }

            double scale = (parts.length > 2) ? Double.parseDouble(parts[2]) : 1.0;
            int count = (parts.length > 3) ? Integer.parseInt(parts[3]) : 1;
            EasingType ease = (parts.length > 4) ? EasingType.fromString(parts[4]) : EasingType.LINEAR;

            return new FrameElement(alias, offset, scale, count, 0.0, ease);
        } catch (Exception e) {
            return null;
        }
    }
}
