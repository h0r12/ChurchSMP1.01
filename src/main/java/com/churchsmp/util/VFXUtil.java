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

    /**
     * Crisp Dendritic Fractal Lightning Tree (Screenshots 1 & 2).
     * Uses recursive bifurcation with jagged perpendicular displacements,
     * decaying branch lengths, and ultra-dense zero-spread dust particles.
     */
    public static void drawFractalLightningTree(Location origin, Vector direction, double length,
                                                int depth, double branchAngle, Color color, float dustSize) {
        if (origin == null || origin.getWorld() == null || depth <= 0 || length <= 0.25) return;
        World world = origin.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, dustSize);

        int subSegments = Math.max(3, (int) (length / 0.55));
        Location current = origin.clone();
        Vector dirNorm = direction.clone().normalize();

        for (int i = 0; i < subSegments; i++) {
            double segLen = length / subSegments;
            Vector ortho1 = getPerpendicularVector(dirNorm);
            Vector ortho2 = dirNorm.clone().crossProduct(ortho1).normalize();
            double jx = (Math.random() - 0.5) * 0.40;
            double jy = (Math.random() - 0.5) * 0.40;
            Vector stepVec = dirNorm.clone().multiply(segLen).add(ortho1.multiply(jx)).add(ortho2.multiply(jy));

            Location next = current.clone().add(stepVec);

            double dist = next.distance(current);
            Vector lineDir = next.toVector().subtract(current.toVector()).normalize();
            for (double d = 0; d <= dist; d += 0.12) {
                Location pt = current.clone().add(lineDir.clone().multiply(d));
                world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
            }

            if (depth > 1 && Math.random() < 0.40 && i > 0) {
                Vector branchDir = rotateVectorRandomly(dirNorm, branchAngle);
                drawFractalLightningTree(next, branchDir, length * 0.55, depth - 1, branchAngle * 1.15, color, Math.max(0.6f, dustSize * 0.85f));
            }

            current = next;
        }

        if (depth > 1) {
            int forks = Math.random() < 0.4 ? 3 : 2;
            for (int f = 0; f < forks; f++) {
                Vector forkDir = rotateVectorRandomly(dirNorm, branchAngle);
                drawFractalLightningTree(current, forkDir, length * 0.65, depth - 1, branchAngle * 1.15, color, Math.max(0.6f, dustSize * 0.85f));
            }
        } else {
            world.spawnParticle(Particle.ELECTRIC_SPARK, current, 1, 0, 0, 0, 0.01);
            if (Math.random() < 0.3) {
                world.spawnParticle(Particle.END_ROD, current, 1, 0, 0, 0, 0.005);
            }
        }
    }

    /**
     * Radial Starburst Lightning Discharge (Screenshot 2).
     * Radiates 8-14 dendritic branches horizontally and upward across a 360° hemisphere.
     */
    public static void drawRadialLightningBurst(Location apex, int branchCount, double maxRadius, Color color, float dustSize) {
        if (apex == null || apex.getWorld() == null) return;
        World world = apex.getWorld();
        world.spawnParticle(Particle.FLASH, apex, 1, Color.RED);
        world.spawnParticle(Particle.END_ROD, apex, 6, 0.2, 0.2, 0.2, 0.08);

        for (int i = 0; i < branchCount; i++) {
            double angle = (2 * Math.PI / branchCount) * i + (Math.random() - 0.5) * 0.3;
            double elevation = 0.12 + Math.random() * 0.60;
            Vector dir = new Vector(Math.cos(angle), elevation, Math.sin(angle)).normalize();
            double len = maxRadius * (0.65 + Math.random() * 0.35);

            drawFractalLightningTree(apex, dir, len, 3, 38.0, color, dustSize);
        }
    }

    /**
     * Hyperbolic Parametric Streamline Canopy & Twin Helix (Screenshot 3).
     * Renders a blooming trumpet/funnel of curved mathematical streamlines
     * with an intertwined dual helix plasma core.
     */
    public static void drawParametricCanopy(Location baseCenter, double baseRadius, double topRadius,
                                            double height, int streamerCount, double swirlAngleRad,
                                            Color coreColor, Color rimColor, float dustSize) {
        if (baseCenter == null || baseCenter.getWorld() == null) return;
        World world = baseCenter.getWorld();
        Particle.DustOptions coreDust = new Particle.DustOptions(coreColor, dustSize * 1.2f);
        BlockData redstoneData = Material.REDSTONE_BLOCK.createBlockData();

        // 1. Central Twin Helix Core
        double helixSteps = height * 7.0;
        for (int s = 0; s <= helixSteps; s++) {
            double y = (s / helixSteps) * height;
            double angle1 = (s * 0.40);
            double angle2 = angle1 + Math.PI;

            Location h1 = baseCenter.clone().add(Math.cos(angle1) * 0.35, y, Math.sin(angle1) * 0.35);
            Location h2 = baseCenter.clone().add(Math.cos(angle2) * 0.35, y, Math.sin(angle2) * 0.35);

            world.spawnParticle(Particle.DUST, h1, 1, 0, 0, 0, 0, coreDust);
            world.spawnParticle(Particle.DUST, h2, 1, 0, 0, 0, 0, coreDust);

            if (s % 5 == 0) {
                world.spawnParticle(Particle.BLOCK, h1, 1, 0, 0, 0, 0, redstoneData);
            }
        }

        // 2. Hyperbolic Flaring Streamlines (Canopy Wings)
        double yStep = 0.22;
        int yCount = (int) (height / yStep);

        for (int k = 0; k < streamerCount; k++) {
            double baseAngle = (2.0 * Math.PI / streamerCount) * k;

            for (int step = 0; step <= yCount; step++) {
                double frac = (double) step / yCount;
                double y = frac * height;

                // Hyperbolic / power curve radius expansion
                double r = baseRadius + (topRadius - baseRadius) * Math.pow(frac, 2.2);

                // Curved swirl
                double angle = baseAngle + (swirlAngleRad * Math.pow(frac, 1.4));

                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;

                Location pt = baseCenter.clone().add(x, y, z);
                Color ptColor = interpolateColor(coreColor, rimColor, frac);
                Particle.DustOptions ptDust = new Particle.DustOptions(ptColor, (float) Math.max(0.6f, dustSize * (1.0 - frac * 0.25)));

                world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, ptDust);

                if (step == yCount && k % 2 == 0) {
                    world.spawnParticle(Particle.CRIMSON_SPORE, pt, 1, 0, 0, 0, 0.01);
                }
            }
        }
    }

    /**
     * Razor-Sharp Spiral Slash Ribbon (Screenshot 4).
     * Renders an ultra-clean logarithmic/Archimedean spiral ribbon in an arbitrary 3D plane
     * with dense parallel micro-tracks, zero particle scatter, and crisp tapering.
     */
    public static void drawCrispSpiralRibbon(Location center, Vector normal, double startRadius,
                                             double endRadius, double startAngle, double sweepAngle,
                                             int parallelStrands, Color color, float dustSize) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        Vector norm = (normal != null && normal.lengthSquared() > 0.001) ? normal.clone().normalize() : new Vector(0, 1, 0);

        Vector u = getPerpendicularVector(norm);
        Vector v = norm.clone().crossProduct(u).normalize();

        Particle.DustOptions dust = new Particle.DustOptions(color, dustSize);
        int steps = Math.max(28, (int) (Math.abs(sweepAngle) * 32));

        for (int strand = 0; strand < parallelStrands; strand++) {
            double widthOffset = (strand - (parallelStrands - 1) / 2.0) * 0.055;

            for (int i = 0; i <= steps; i++) {
                double frac = (double) i / steps;
                double angle = startAngle + (sweepAngle * frac);
                double r = (startRadius + (endRadius - startRadius) * frac) + widthOffset;
                if (r < 0.05) continue;

                float taper = (float) Math.sin(frac * Math.PI);
                float curSize = Math.max(0.45f, dustSize * taper);
                Particle.DustOptions curDust = (taper < 0.95f) ? new Particle.DustOptions(color, curSize) : dust;

                Vector ptVec = u.clone().multiply(Math.cos(angle) * r).add(v.clone().multiply(Math.sin(angle) * r));
                Location pt = center.clone().add(ptVec);

                world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, curDust);
            }
        }
    }

    public static Vector getPerpendicularVector(Vector vec) {
        Vector v = vec.clone().normalize();
        if (Math.abs(v.getY()) < 0.9) {
            return new Vector(-v.getZ(), 0, v.getX()).normalize();
        } else {
            return new Vector(1, 0, 0).crossProduct(v).normalize();
        }
    }

    public static Vector rotateVectorRandomly(Vector baseDir, double maxAngleDegrees) {
        Vector dir = baseDir.clone().normalize();
        Vector perp1 = getPerpendicularVector(dir);
        Vector perp2 = dir.clone().crossProduct(perp1).normalize();

        double angleRad = Math.toRadians(maxAngleDegrees * (0.45 + Math.random() * 0.55));
        double phi = Math.random() * 2.0 * Math.PI;

        double sinA = Math.sin(angleRad);
        double cosA = Math.cos(angleRad);

        Vector offset = perp1.multiply(Math.cos(phi) * sinA).add(perp2.multiply(Math.sin(phi) * sinA));
        return dir.multiply(cosA).add(offset).normalize();
    }
}
