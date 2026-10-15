package com.churchsmp.particle;

import java.util.Locale;

public enum EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_SINE,
    EASE_OUT_SINE,
    EASE_IN_OUT_SINE,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_IN_EXPO,
    EASE_OUT_EXPO,
    EASE_IN_BACK,
    EASE_OUT_BACK,
    EASE_IN_ELASTIC,
    EASE_OUT_ELASTIC,
    EASE_IN_BOUNCE,
    EASE_OUT_BOUNCE;

    public static double evaluate(EasingType type, double t) {
        if (type == null) type = LINEAR;
        t = Math.max(0.0, Math.min(1.0, t));

        final double c1 = 1.70158;
        final double c3 = c1 + 1.0;

        switch (type) {
            case LINEAR -> {
                return t;
            }
            case EASE_IN -> {
                return t * t * t;
            }
            case EASE_OUT -> {
                return 1.0 - Math.pow(1.0 - t, 3.0);
            }
            case EASE_IN_SINE -> {
                return 1.0 - Math.cos((t * Math.PI) / 2.0);
            }
            case EASE_OUT_SINE -> {
                return Math.sin((t * Math.PI) / 2.0);
            }
            case EASE_IN_OUT_SINE -> {
                return -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            }
            case EASE_IN_QUAD -> {
                return t * t;
            }
            case EASE_OUT_QUAD -> {
                return 1.0 - (1.0 - t) * (1.0 - t);
            }
            case EASE_IN_OUT_QUAD -> {
                return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            }
            case EASE_IN_CUBIC -> {
                return t * t * t;
            }
            case EASE_OUT_CUBIC -> {
                return 1.0 - Math.pow(1.0 - t, 3.0);
            }
            case EASE_IN_OUT_CUBIC -> {
                return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            }
            case EASE_IN_EXPO -> {
                return t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            }
            case EASE_OUT_EXPO -> {
                return t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
            }
            case EASE_IN_BACK -> {
                return c3 * t * t * t - c1 * t * t;
            }
            case EASE_OUT_BACK -> {
                return 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
            }
            case EASE_IN_ELASTIC -> {
                if (t == 0.0) return 0.0;
                if (t == 1.0) return 1.0;
                double c4 = (2.0 * Math.PI) / 3.0;
                return -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * c4);
            }
            case EASE_OUT_ELASTIC -> {
                if (t == 0.0) return 0.0;
                if (t == 1.0) return 1.0;
                double c4 = (2.0 * Math.PI) / 3.0;
                return Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
            case EASE_OUT_BOUNCE -> {
                return bounce(t);
            }
            case EASE_IN_BOUNCE -> {
                return 1.0 - bounce(1.0 - t);
            }
            default -> {
                return t;
            }
        }
    }

    private static double bounce(double t) {
        final double n1 = 7.5625;
        final double d1 = 2.75;

        if (t < 1.0 / d1) {
            return n1 * t * t;
        } else if (t < 2.0 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        } else if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        } else {
            t -= 2.625 / d1;
            return n1 * t * t + 0.984375;
        }
    }

    public static EasingType fromString(String name) {
        if (name == null || name.isBlank()) return LINEAR;
        String clean = name.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            // Check common aliases
            if (clean.equals("EASEIN") || clean.equals("IN")) return EASE_IN;
            if (clean.equals("EASEOUT") || clean.equals("OUT")) return EASE_OUT;
            if (clean.equals("EASEINOUT") || clean.equals("INOUT")) return EASE_IN_OUT_CUBIC;
            if (clean.equals("BOUNCE")) return EASE_OUT_BOUNCE;
            if (clean.equals("ELASTIC")) return EASE_OUT_ELASTIC;
            if (clean.equals("BACK")) return EASE_OUT_BACK;
            if (clean.equals("EXPO")) return EASE_OUT_EXPO;
            if (clean.equals("SINE")) return EASE_OUT_SINE;
            return LINEAR;
        }
    }
}
