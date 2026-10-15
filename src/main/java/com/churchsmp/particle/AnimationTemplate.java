package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum AnimationTemplate {

    BLADE_SLASH("Blade Slash", "Dynamic sweeping sword blade trail with acceleration", 10, Material.IRON_SWORD),
    EXPANDING_SHOCKWAVE("Expanding Shockwave", "Circular energy ring exploding outward from center", 12, Material.ECHO_SHARD),
    RISING_VORTEX("Rising Vortex", "Double-helix spiral vortex surging into the sky", 16, Material.BREEZE_ROD),
    PIERCING_THRUST("Piercing Thrust", "Concentrated linear beam surge with impact burst", 8, Material.LIGHTNING_ROD),
    GROUND_SLAM("Ground Slam", "Crater shockwave slamming into the ground", 10, Material.ANVIL),
    CROSS_SLASH("Cross Slash", "Dual crossing diagonal blade slashes (X-Strike)", 14, Material.NETHERITE_SWORD),
    ORBIT_SHIELD("Orbit Shield", "Tri-orb runic shield rotating smoothly around caster", 16, Material.HEART_OF_THE_SEA);

    private final String displayName;
    private final String description;
    private final int frameCount;
    private final Material icon;

    AnimationTemplate(String displayName, String description, int frameCount, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.frameCount = frameCount;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public Material getIcon() {
        return icon;
    }

    public static AnimationTemplate fromString(String name) {
        if (name == null || name.isBlank()) return BLADE_SLASH;
        String clean = name.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            if (clean.contains("SLASH") || clean.contains("SWEEP")) return BLADE_SLASH;
            if (clean.contains("WAVE") || clean.contains("RING")) return EXPANDING_SHOCKWAVE;
            if (clean.contains("VORTEX") || clean.contains("TORNADO") || clean.contains("HELIX")) return RISING_VORTEX;
            if (clean.contains("THRUST") || clean.contains("SPEAR") || clean.contains("BEAM")) return PIERCING_THRUST;
            if (clean.contains("SLAM") || clean.contains("CRATER")) return GROUND_SLAM;
            if (clean.contains("CROSS") || clean.contains("X")) return CROSS_SLASH;
            if (clean.contains("ORBIT") || clean.contains("SHIELD")) return ORBIT_SHIELD;
            return BLADE_SLASH;
        }
    }

    /**
     * Generates all keyframes of this animation template into the target ParticleAnimation.
     */
    public void generateInto(ParticleAnimation animation, ParticleAlias particle, double baseScale) {
        if (animation == null) return;
        animation.clear();
        animation.setName("anim_" + name().toLowerCase(Locale.ROOT));

        double scale = Math.max(0.4, baseScale);

        switch (this) {
            case BLADE_SLASH -> generateBladeSlash(animation, particle, scale);
            case EXPANDING_SHOCKWAVE -> generateExpandingShockwave(animation, particle, scale);
            case RISING_VORTEX -> generateRisingVortex(animation, particle, scale);
            case PIERCING_THRUST -> generatePiercingThrust(animation, particle, scale);
            case GROUND_SLAM -> generateGroundSlam(animation, particle, scale);
            case CROSS_SLASH -> generateCrossSlash(animation, particle, scale);
            case ORBIT_SHIELD -> generateOrbitShield(animation, particle, scale);
        }
    }

    private void generateBladeSlash(ParticleAnimation anim, ParticleAlias part, double s) {
        int steps = 10;
        int ptsPerFrame = 16;
        double radius = 2.2 * s;

        for (int f = 0; f < steps; f++) {
            double t = (double) f / (steps - 1);
            double eased = EasingType.evaluate(EasingType.EASE_OUT, t);

            double yawDeg = -55.0 + (110.0 * eased); // sweep left to right
            double pitchDeg = -15.0 + (30.0 * eased);
            double arcScale = s * (0.8 + (0.5 * Math.sin(t * Math.PI)));

            AnimationFrame frame = anim.getOrCreateFrame(f);
            double radYaw = Math.toRadians(yawDeg);
            double radPitch = Math.toRadians(pitchDeg);

            for (int p = 0; p < ptsPerFrame; p++) {
                double frac = (double) p / ptsPerFrame;
                double arcAngle = Math.toRadians(-40.0 + (80.0 * frac));

                double lx = Math.sin(radYaw + arcAngle) * radius;
                double ly = Math.sin(radPitch) * radius + (arcAngle * 0.4);
                double lz = Math.cos(radYaw + arcAngle) * radius + 1.2;

                frame.addElement(new FrameElement(part, new Vector(lx, ly, lz), arcScale, 1, 0.0, EasingType.EASE_OUT));
            }
        }
    }

    private void generateExpandingShockwave(ParticleAnimation anim, ParticleAlias part, double s) {
        int totalFrames = 12;
        int circlePts = 28;

        for (int f = 0; f < totalFrames; f++) {
            double t = (double) f / (totalFrames - 1);
            double eased = EasingType.evaluate(EasingType.EASE_OUT, t);
            double r = (0.5 + (3.0 * eased)) * s;
            double elemScale = s * Math.max(0.2, 1.2 * (1.0 - (0.6 * t)));

            AnimationFrame frame = anim.getOrCreateFrame(f);
            for (int i = 0; i < circlePts; i++) {
                double angle = (2.0 * Math.PI * i) / circlePts;
                double x = r * Math.cos(angle);
                double z = (r * Math.sin(angle)) + 2.0;
                double y = -0.5 + (0.3 * Math.sin(t * Math.PI));

                frame.addElement(new FrameElement(part, new Vector(x, y, z), elemScale, 1, 0.0, EasingType.EASE_OUT));
            }
        }
    }

    private void generateRisingVortex(ParticleAnimation anim, ParticleAlias part, double s) {
        int totalFrames = 16;
        int helixSteps = 20;

        for (int f = 0; f < totalFrames; f++) {
            double frameT = (double) f / totalFrames;
            double baseRot = frameT * (2.0 * Math.PI * 2.0); // 2 full rotations

            AnimationFrame frame = anim.getOrCreateFrame(f);
            for (int i = 0; i < helixSteps; i++) {
                double strandT = (double) i / helixSteps;
                double r = (0.6 + (1.2 * strandT)) * s;
                double y = -1.0 + (3.5 * strandT);

                // Strand 1
                double theta1 = baseRot + (strandT * 4.0 * Math.PI);
                double x1 = r * Math.cos(theta1);
                double z1 = r * Math.sin(theta1) + 2.0;
                frame.addElement(new FrameElement(part, new Vector(x1, y, z1), s * 0.9, 1, 0.0, EasingType.LINEAR));

                // Strand 2 (180 deg offset)
                double theta2 = theta1 + Math.PI;
                double x2 = r * Math.cos(theta2);
                double z2 = r * Math.sin(theta2) + 2.0;
                frame.addElement(new FrameElement(part, new Vector(x2, y, z2), s * 0.9, 1, 0.0, EasingType.LINEAR));
            }
        }
    }

    private void generatePiercingThrust(ParticleAnimation anim, ParticleAlias part, double s) {
        int totalFrames = 8;

        for (int f = 0; f < totalFrames; f++) {
            double t = (double) f / (totalFrames - 1);
            double eased = EasingType.evaluate(EasingType.EASE_OUT_CUBIC, t);
            double maxReach = 5.0 * s;
            double currentZ = 0.5 + (maxReach * eased);

            AnimationFrame frame = anim.getOrCreateFrame(f);

            // Beam line from caster to tip
            int lineSegments = 14;
            for (int i = 0; i < lineSegments; i++) {
                double frac = (double) i / lineSegments;
                double z = 0.5 + (currentZ - 0.5) * frac;
                double jitterX = (Math.sin(i * 3.7 + f) * 0.08) * s;
                double jitterY = (Math.cos(i * 2.3 + f) * 0.08) * s;
                frame.addElement(new FrameElement(part, new Vector(jitterX, jitterY, z), s * 0.8, 1, 0.0, EasingType.LINEAR));
            }

            // Expanding impact ring at tip on latter half
            if (f >= 3) {
                int ringPts = 12;
                double ringR = ((f - 2) * 0.25) * s;
                for (int p = 0; p < ringPts; p++) {
                    double ang = (2.0 * Math.PI * p) / ringPts;
                    double rx = ringR * Math.cos(ang);
                    double ry = ringR * Math.sin(ang);
                    frame.addElement(new FrameElement(part, new Vector(rx, ry, currentZ), s * 0.7, 1, 0.0, EasingType.EASE_OUT));
                }
            }
        }
    }

    private void generateGroundSlam(ParticleAnimation anim, ParticleAlias part, double s) {
        int totalFrames = 10;

        for (int f = 0; f < totalFrames; f++) {
            AnimationFrame frame = anim.getOrCreateFrame(f);

            if (f < 3) {
                // Descending meteor streak
                double downT = f / 2.0;
                double y = 2.5 - (3.5 * downT);
                frame.addElement(new FrameElement(part, new Vector(0, y, 2.5), s * 1.5, 3, 0.05, EasingType.EASE_IN));
            } else {
                // Impact & ground shockwave
                double impactT = (double) (f - 3) / (totalFrames - 4);
                double eased = EasingType.evaluate(EasingType.EASE_OUT, impactT);
                double r = (0.4 + (3.2 * eased)) * s;
                double elemScale = s * Math.max(0.2, 1.3 * (1.0 - impactT * 0.7));

                int starPts = 20;
                for (int i = 0; i < starPts; i++) {
                    double ang = (2.0 * Math.PI * i) / starPts;
                    double x = r * Math.cos(ang);
                    double z = (r * Math.sin(ang)) + 2.5;
                    frame.addElement(new FrameElement(part, new Vector(x, -1.0, z), elemScale, 1, 0.0, EasingType.EASE_OUT));
                }
            }
        }
    }

    private void generateCrossSlash(ParticleAnimation anim, ParticleAlias part, double s) {
        int half = 7;
        int pts = 14;
        double len = 2.4 * s;

        // Slash 1: Top-Right to Bottom-Left (Frames 0 to 6)
        for (int f = 0; f < half; f++) {
            double t = (double) f / (half - 1);
            double eased = EasingType.evaluate(EasingType.EASE_OUT, t);
            AnimationFrame frame = anim.getOrCreateFrame(f);

            for (int p = 0; p < pts; p++) {
                double frac = (double) p / pts;
                double prog = -len + (2.0 * len * frac * eased);
                // 45 degree tilt
                double x = prog * 0.707;
                double y = prog * 0.707;
                frame.addElement(new FrameElement(part, new Vector(x, y, 2.0), s * 1.0, 1, 0.0, EasingType.EASE_OUT));
            }
        }

        // Slash 2: Top-Left to Bottom-Right (Frames 7 to 13)
        for (int f = 0; f < half; f++) {
            double t = (double) f / (half - 1);
            double eased = EasingType.evaluate(EasingType.EASE_OUT, t);
            AnimationFrame frame = anim.getOrCreateFrame(half + f);

            // Keep faint residual glow of Slash 1 at center
            frame.addElement(new FrameElement(part, new Vector(0, 0, 2.0), s * 1.2, 2, 0.02, EasingType.LINEAR));

            for (int p = 0; p < pts; p++) {
                double frac = (double) p / pts;
                double prog = -len + (2.0 * len * frac * eased);
                // -45 degree tilt
                double x = -prog * 0.707;
                double y = prog * 0.707;
                frame.addElement(new FrameElement(part, new Vector(x, y, 2.0), s * 1.0, 1, 0.0, EasingType.EASE_OUT));
            }
        }
    }

    private void generateOrbitShield(ParticleAnimation anim, ParticleAlias part, double s) {
        int totalFrames = 16;
        int orbs = 3;
        double radius = 1.8 * s;

        for (int f = 0; f < totalFrames; f++) {
            double baseAngle = ((2.0 * Math.PI) * f) / totalFrames;
            AnimationFrame frame = anim.getOrCreateFrame(f);

            for (int o = 0; o < orbs; o++) {
                double orbAngle = baseAngle + (o * (2.0 * Math.PI / orbs));
                double x = radius * Math.cos(orbAngle);
                double z = radius * Math.sin(orbAngle) + 1.8;
                double y = 0.2 * Math.sin(orbAngle * 2.0); // subtle bobbing

                frame.addElement(new FrameElement(part, new Vector(x, y, z), s * 1.3, 1, 0.0, EasingType.LINEAR));
                // Add a small trailing spark behind each orb
                double trailAngle = orbAngle - 0.25;
                double tx = radius * Math.cos(trailAngle);
                double tz = radius * Math.sin(trailAngle) + 1.8;
                frame.addElement(new FrameElement(part, new Vector(tx, y, tz), s * 0.7, 1, 0.0, EasingType.LINEAR));
            }
        }
    }
}
