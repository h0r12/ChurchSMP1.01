package com.churchsmp.alignment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public enum Alignment {
    GOOD("Good", NamedTextColor.AQUA, "Walk the path of righteousness."),
    EVIL("Evil", NamedTextColor.DARK_RED, "Embrace the shadows of sin."),
    NULLIFIED("Nullified", NamedTextColor.GRAY, "Unbound by celestial or abyssal ties.");

    private final String displayName;
    private final NamedTextColor color;
    private final String description;

    Alignment(String displayName, NamedTextColor color, String description) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public Component getFormattedComponent() {
        return Component.text(displayName, color).decorate(TextDecoration.BOLD);
    }
}
