package com.churchsmp.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

/**
 * High-performance mathematical particle VFX engine for ChurchSMP (Paper 1.21.11).
 * Implements parametric geometry, vector interpolation, directional slash arcs,
 * undulating sine-wave shockwaves, 3D exponential funnels, and tether chains.
 */
public final class VFXUtil {

    private VFXUtil() {}

    /**
     * Vector line interpolation stepping between point A and point B (draw_line).
     */
    public static void drawLine(Location from, Location to, Particle particle, double step, Object data) {
        if (from == null || to == null || from.getWorld() == null) return;
        World world = from.getWorld();
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist < 0.001) return;
        dir.normalize();

        for (double d = 0; d <= dist; d += step) {
            Location pt = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(particle, pt, 1, 0, 0, 0, 0, data);
        }
    }

    /**
     * Interlocking tether chain between two points with gravitational catenary sag
     * and a traveling electrical spark pulse.
     */
    public static void drawTetherChain(Location from, Location to, int links, Color chainColor, boolean sparkPulse, int sparkIndex) {
        if (from == null || to == null || from.getWorld() == null || links <= 1) return;
        World world = from.getWorld();
        Vector diff = to.toVector().subtract(from.toVector());
        Particle.DustOptions chainDust = new Particle.DustOptions(chainColor, 1.3f);

        for (int i = 0; i <= links; i++) {
            double frac = (double) i / links;
            // Parabolic gravity sag in middle
            double sag = -Math.sin(frac * Math.PI) * 0.45;
            Location pt = from.clone().add(diff.clone().multiply(frac)).add(0, sag, 0);

            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, chainDust);

            if (sparkPulse && (i == sparkIndex % (links + 1))) {
                world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.01);
                world.spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    /**
     * Physical red block chain made of redstone block & red concrete particles.
     * Renders parabolic catenary sag with 3D block texture crumbs and electrical sparks.
     */
    public static void drawRedBlockChain(Location from, Location to, int links, boolean sparkPulse, int sparkIndex) {
        if (from == null || to == null || from.getWorld() == null || links <= 1) return;
        World world = from.getWorld();
        Vector diff = to.toVector().subtract(from.toVector());
        BlockData redstone = Material.REDSTONE_BLOCK.createBlockData();
        BlockData concrete = Material.RED_CONCRETE.createBlockData();
        Particle.DustOptions glowDust = new Particle.DustOptions(Color.fromRGB(240, 20, 20), 1.2f);

        for (int i = 0; i <= links; i++) {
            double frac = (double) i / links;
            double sag = -Math.sin(frac * Math.PI) * 0.40;
            Location pt = from.clone().add(diff.clone().multiply(frac)).add(0, sag, 0);

            BlockData linkData = (i % 2 == 0) ? redstone : concrete;
            world.spawnParticle(Particle.BLOCK, pt, 4, 0.05, 0.05, 0.05, 0.0, linkData);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, glowDust);

            if (sparkPulse && (i == sparkIndex % (links + 1))) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.05, 0.05, 0.05, 0.02);
                world.spawnParticle(Particle.CRIMSON_SPORE, pt, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    /**
     * Directional razor-sharp crescent slash arc matching player swing view angle.
     * Generates a 120° dual-tone particle curve projected in front of the caster.
     */
    public static void drawSlashArc(Location origin, Vector direction, double radius, double arcAngleDegrees,
                                   int points, Color coreColor, Color edgeColor, float tiltDegrees) {
        if (origin == null || origin.getWorld() == null || direction == null) return;
        World world = origin.getWorld();
        Vector forward = direction.clone().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) {
            right = new Vector(1, 0, 0);
        }
        up = right.clone().crossProduct(forward).normalize();

        // Apply tilt rotation
        if (tiltDegrees != 0) {
            double tiltRad = Math.toRadians(tiltDegrees);
            right = right.clone().multiply(Math.cos(tiltRad)).add(up.clone().multiply(Math.sin(tiltRad))).normalize();
            up = right.clone().crossProduct(forward).normalize();
        }

        double halfArc = Math.toRadians(arcAngleDegrees) / 2.0;
        Particle.DustOptions coreDust = new Particle.DustOptions(coreColor, 1.6f);
        Particle.DustOptions edgeDust = new Particle.DustOptions(edgeColor, 1.2f);

        for (int i = 0; i <= points; i++) {
            double angle = -halfArc + (halfArc * 2.0 * i / points);
            Vector slice = forward.clone().multiply(Math.cos(angle)).add(right.clone().multiply(Math.sin(angle)));
            Location pt = origin.clone().add(slice.multiply(radius));

            // Core high-intensity particle
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, coreDust);

            // Edge glowing spark
            if (i % 2 == 0) {
                Location edgePt = pt.clone().add(up.clone().multiply(0.08));
                world.spawnParticle(Particle.DUST, edgePt, 1, 0, 0, 0, 0, edgeDust);
            }
        }
    }

    /**
     * Undulating sine-wave shockwave ring (Big Papi formula: y = amplitude * sin(theta * frequency)).
     * Creates an electrified, vibrating circular energy barrier on the floor.
     */
    public static void drawUndulatingSineRing(Location center, double radius, int segments, int waveFrequency,
                                             double waveAmplitude, double timeOffset, Color color, float dustSize) {
        if (center == null || center.getWorld() == null || segments <= 0) return;
        World world = center.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, dustSize);

        for (int i = 0; i <= segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double waveY = waveAmplitude * Math.sin(angle * waveFrequency + timeOffset);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location pt = center.clone().add(x, 0.08 + waveY, z);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);

            if (i % (segments / 4) == 0) {
                world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.005);
            }
        }
    }

    /**
     * Expanding concentric ground crater shockwave with stone debris chunks and sonic boom.
     */
    public static void drawExpandingCraterShockwave(Location center, double currentRadius, Color innerColor, Color outerColor, boolean spawnDebris) {
        if (center == null || center.getWorld() == null || currentRadius <= 0.1) return;
        World world = center.getWorld();
        int points = Math.max(16, (int) (currentRadius * 12));
        Particle.DustOptions innerDust = new Particle.DustOptions(innerColor, 1.8f);
        Particle.DustOptions outerDust = new Particle.DustOptions(outerColor, 1.2f);

        for (int i = 0; i <= points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;

            // Clamped to floor
            Location pt = center.clone().add(x, 0.06, z);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, innerDust);

            if (currentRadius > 1.2 && i % 2 == 0) {
                Location subPt = center.clone().add(Math.cos(angle) * (currentRadius - 0.4), 0.06, Math.sin(angle) * (currentRadius - 0.4));
                world.spawnParticle(Particle.DUST, subPt, 1, 0, 0, 0, 0, outerDust);
            }
        }

        if (spawnDebris) {
            BlockData stoneData = Material.STONE.createBlockData();
            BlockData deepslateData = Material.DEEPSLATE.createBlockData();
            for (int d = 0; d < 6; d++) {
                double da = Math.random() * 2 * Math.PI;
                double dr = Math.random() * currentRadius;
                Location chunkLoc = center.clone().add(Math.cos(da) * dr, 0.2, Math.sin(da) * dr);
                world.spawnParticle(Particle.BLOCK, chunkLoc, 2, 0.1, 0.25, 0.1, 0.05, (d % 2 == 0) ? stoneData : deepslateData);
            }
        }
    }

    /**
     * 3D Exponential Tornado / Funnel Vortex (Big Papi 16:30 formula).
     * r = baseRadius * exp(tier / scale), y = tier^2 / heightScale, with vector lines connecting vertices.
     * Can be inverted for Gluttony's inward vacuum suction.
     */
    public static void drawExponentialTornado(Location origin, double baseRadius, double maxHeight, int tiers,
                                             double time, boolean inverted, Color bottomColor, Color topColor) {
        if (origin == null || origin.getWorld() == null || tiers <= 1) return;
        World world = origin.getWorld();
        Location[] vertices = new Location[tiers];

        for (int i = 0; i < tiers; i++) {
            double progress = (double) i / (tiers - 1);
            double y = inverted ? (maxHeight * (1.0 - progress)) : (Math.pow(progress, 1.8) * maxHeight);
            double r = inverted ? (baseRadius * (1.0 - progress * 0.75)) : (baseRadius * Math.exp(progress * 1.2));
            double angVel = (time * 0.1) * (1.0 + progress * 0.4);
            double angle = ((2 * Math.PI / 5) * i) + angVel;

            double x = Math.sin(angle) * r;
            double z = Math.cos(angle) * r;
            vertices[i] = origin.clone().add(x, y, z);
        }

        // Draw connected line mesh between vertices
        for (int i = 0; i < tiers - 1; i++) {
            Location a = vertices[i];
            Location b = vertices[i + 1];
            double frac = (double) i / tiers;
            Color tierColor = interpolateColor(bottomColor, topColor, frac);
            Particle.DustOptions dust = new Particle.DustOptions(tierColor, 1.4f);

            // Vector line stepping
            Vector dir = b.toVector().subtract(a.toVector());
            double dist = dir.length();
            if (dist > 0.01) {
                dir.normalize();
                for (double d = 0; d <= dist; d += 0.45) {
                    Location pt = a.clone().add(dir.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
                }
            }

            if (i % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, a, 1, 0, 0, 0, 0.005);
            }
        }
    }

    /**
     * 3D Gimbal Ring in arbitrary rotated space (yaw, pitch, roll).
     */
    public static void drawGimbalRing(Location center, double radius, int segments, double yaw, double pitch, double roll, Color color, float size) {
        if (center == null || center.getWorld() == null || segments <= 0) return;
        World world = center.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);

        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cr = Math.cos(roll), sr = Math.sin(roll);

        for (int i = 0; i <= segments; i++) {
            double a = (2 * Math.PI / segments) * i;
            double x = Math.cos(a) * radius;
            double y = 0;
            double z = Math.sin(a) * radius;

            // Yaw
            double x1 = x * cyaw - z * syaw;
            double z1 = x * syaw + z * cyaw;

            // Pitch
            double y2 = y * cp - z1 * sp;
            double z2 = y * sp + z1 * cp;

            // Roll
            double x3 = x1 * cr - y2 * sr;
            double y3 = x1 * sr + y2 * cr;

            Location pt = center.clone().add(x3, y3, z2);
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Smoothly interpolates between two RGB colors.
     */
    public static Color interpolateColor(Color a, Color b, double factor) {
        factor = Math.max(0.0, Math.min(1.0, factor));
        int red = (int) (a.getRed() + (b.getRed() - a.getRed()) * factor);
        int green = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * factor);
        int blue = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * factor);
        return Color.fromRGB(red, green, blue);
    }
}
