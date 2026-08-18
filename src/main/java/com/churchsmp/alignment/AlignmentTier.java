package com.churchsmp.alignment;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The five alignment tiers a player can occupy, from Fallen to Saint.
 */
public enum AlignmentTier {
    FALLEN("Fallen", NamedTextColor.DARK_RED),
    WICKED("Wicked", NamedTextColor.GRAY),
    NULLIFIED("Nullified", NamedTextColor.WHITE),
    RIGHTEOUS("Righteous", NamedTextColor.YELLOW),
    SAINT("Saint", NamedTextColor.GOLD);

    private final String label;
    private final NamedTextColor color;

    AlignmentTier(String label, NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public boolean isGood() {
        return this == RIGHTEOUS || this == SAINT;
    }

    public boolean isEvil() {
        return this == WICKED || this == FALLEN;
    }

    public boolean isNullified() {
        return this == NULLIFIED;
    }
}
