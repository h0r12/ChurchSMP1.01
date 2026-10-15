package com.churchsmp.particle;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public enum MathPreset {
    POINT,
    STAR_4,
    STAR_8,
    HYPOCYCLOID,
    RING,
    SLASH,
    HELIX,
    SPIRAL,
    LISSAJOUS,
    ROSE,
    BOLT,
    BOLT_VEINS;

    private static final Random RNG = new Random();

    public static MathPreset fromString(String name) {
        if (name == null || name.isBlank()) return STAR_4;
        String clean = name.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            if (clean.equals("STAR") || clean.equals("4STAR")) return STAR_4;
            if (clean.equals("8STAR")) return STAR_8;
            if (clean.equals("CIRCLE")) return RING;
            if (clean.equals("ARC") || clean.equals("SWEEP")) return SLASH;
            if (clean.equals("VORTEX")) return HELIX;
            if (clean.equals("VEINS") || clean.equals("LIGHTNING") || clean.equals("BOLTVEINS")) return BOLT_VEINS;
            return STAR_4;
        }
    }

    /**
     * Generates a list of relative local vectors (^X right, ^Y up, ^Z forward).
     */
    public List<Vector> generatePoints(double scale) {
        List<Vector> points = new ArrayList<>();
        double s = Math.max(0.1, scale);

        switch (this) {
            case POINT -> {
                points.add(new Vector(0, 0, 0));
            }
            case STAR_4, HYPOCYCLOID -> {
                // Parametric Astroid / 4-pointed hypocycloid: x = R*cos^3(t), y = R*sin^3(t)
                int steps = 36;
                double r = 1.6 * s;
                for (int i = 0; i < steps; i++) {
                    double t = (2.0 * Math.PI * i) / steps;
                    double cos = Math.cos(t);
                    double sin = Math.sin(t);
                    double x = r * cos * cos * cos;
                    double y = r * sin * sin * sin;
                    points.add(new Vector(x, y, 0));
                }
            }
            case STAR_8 -> {
                // Dual 4-star or 8-pointed star
                int steps = 48;
                double rOuter = 1.8 * s;
                double rInner = 0.6 * s;
                for (int i = 0; i < steps; i++) {
                    double t = (2.0 * Math.PI * i) / steps;
                    double r = (i % 6 < 3) ? rOuter : rInner;
                    double x = r * Math.cos(t);
                    double y = r * Math.sin(t);
                    points.add(new Vector(x, y, 0));
                }
            }
            case RING -> {
                int steps = 32;
                double r = 1.5 * s;
                for (int i = 0; i < steps; i++) {
                    double t = (2.0 * Math.PI * i) / steps;
                    points.add(new Vector(r * Math.cos(t), 0, r * Math.sin(t)));
                }
            }
            case SLASH -> {
                // Diagonal curved blade wave: swings from top-left to bottom-right forward
                int steps = 24;
                double r = 2.0 * s;
                for (int i = 0; i <= steps; i++) {
                    double frac = (double) i / steps; // 0 to 1
                    double angle = Math.toRadians(-55.0 + (110.0 * frac));
                    double x = r * Math.sin(angle);
                    double y = (0.8 - (1.6 * frac)) * s;
                    double z = (r * Math.cos(angle) * 0.5) + (0.5 * s);
                    points.add(new Vector(x, y, z));
                }
            }
            case HELIX -> {
                int steps = 40;
                double r = 1.0 * s;
                double h = 2.5 * s;
                for (int i = 0; i < steps; i++) {
                    double frac = (double) i / steps;
                    double t = frac * 4.0 * Math.PI; // 2 revolutions
                    double x = r * Math.cos(t);
                    double z = r * Math.sin(t);
                    double y = (frac * h) - (h / 2.0);
                    points.add(new Vector(x, y, z));
                }
            }
            case SPIRAL -> {
                int steps = 36;
                double maxR = 2.0 * s;
                for (int i = 0; i < steps; i++) {
                    double frac = (double) i / steps;
                    double t = frac * 4.0 * Math.PI;
                    double r = frac * maxR;
                    points.add(new Vector(r * Math.cos(t), 0, r * Math.sin(t)));
                }
            }
            case LISSAJOUS -> {
                int steps = 48;
                double a = 1.4 * s;
                double b = 1.0 * s;
                double c = 1.4 * s;
                for (int i = 0; i < steps; i++) {
                    double t = (2.0 * Math.PI * i) / steps;
                    double x = a * Math.sin(3.0 * t);
                    double y = b * Math.sin(2.0 * t);
                    double z = c * Math.cos(3.0 * t);
                    points.add(new Vector(x, y, z));
                }
            }
            case ROSE -> {
                // Maurer rose curve: r = sin(4 * theta)
                int steps = 48;
                double maxR = 1.6 * s;
                for (int i = 0; i < steps; i++) {
                    double t = (2.0 * Math.PI * i) / steps;
                    double r = maxR * Math.abs(Math.sin(4.0 * t));
                    double x = r * Math.cos(t);
                    double y = r * Math.sin(t);
                    points.add(new Vector(x, y, 0));
                }
            }
            case BOLT -> {
                // Single jagged lightning bolt forward
                Vector start = new Vector(0, 0, 0);
                Vector end = new Vector(0, 0, 3.5 * s);
                generateFractalLine(start, end, 3, 0.4 * s, points);
            }
            case BOLT_VEINS -> {
                // Central trunk + 5 branching lightning veins!
                Vector start = new Vector(0, 0, 0);
                Vector trunkEnd = new Vector(0, 0, 4.0 * s);
                List<Vector> trunk = new ArrayList<>();
                generateFractalLine(start, trunkEnd, 3, 0.4 * s, trunk);
                points.addAll(trunk);

                // Add branching veins off intermediate points
                int branchCount = Math.min(5, trunk.size() / 3);
                for (int b = 1; b <= branchCount; b++) {
                    int idx = (trunk.size() / (branchCount + 1)) * b;
                    if (idx < trunk.size()) {
                        Vector branchStart = trunk.get(idx);
                        double side = (b % 2 == 0) ? 1.0 : -1.0;
                        double bx = branchStart.getX() + (side * (1.2 + RNG.nextDouble() * 0.8) * s);
                        double by = branchStart.getY() + ((RNG.nextDouble() - 0.5) * 1.0 * s);
                        double bz = branchStart.getZ() + ((RNG.nextDouble() * 1.5) * s);
                        Vector branchEnd = new Vector(bx, by, bz);
                        generateFractalLine(branchStart, branchEnd, 2, 0.25 * s, points);
                    }
                }
            }
        }

        return points;
    }

    private static void generateFractalLine(Vector a, Vector b, int depth, double maxJitter, List<Vector> out) {
        if (depth <= 0) {
            out.add(a.clone());
            out.add(b.clone());
            return;
        }

        Vector mid = a.clone().add(b).multiply(0.5);
        double jx = (RNG.nextDouble() - 0.5) * maxJitter;
        double jy = (RNG.nextDouble() - 0.5) * maxJitter;
        double jz = (RNG.nextDouble() - 0.5) * maxJitter;
        mid.add(new Vector(jx, jy, jz));

        generateFractalLine(a, mid, depth - 1, maxJitter * 0.6, out);
        generateFractalLine(mid, b, depth - 1, maxJitter * 0.6, out);
    }
}
