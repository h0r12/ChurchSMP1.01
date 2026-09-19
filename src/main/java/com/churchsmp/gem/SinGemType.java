package com.churchsmp.gem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

public enum SinGemType {
    WRATH("Wrath", Material.REDSTONE_BLOCK, TextColor.color(0xFF2222),
            "Harness uncontrollable fury and explosive strength."),
    GREED("Greed", Material.GOLD_BLOCK, TextColor.color(0xFFD700),
            "Multiply your fortune and extract wealth from the fallen."),
    GLUTTONY("Gluttony", Material.SLIME_BLOCK, TextColor.color(0x32CD32),
            "Devour enemy vitality and feed endlessly on their hunger."),
    LUST("Lust", Material.AMETHYST_BLOCK, TextColor.color(0xFF69B4),
            "Charm your foes with an irresistible, binding magnetic pull."),
    ENVY("Envy", Material.EMERALD_BLOCK, TextColor.color(0x00CED1),
            "Covet and steal the strengths and positive buffs of rivals."),
    PRIDE("Pride", Material.QUARTZ_BLOCK, TextColor.color(0xFFF8DC),
            "Reach untouchable perfection; devastating power that shatters if struck."),
    SLOTH("Sloth", Material.CRYING_OBSIDIAN, TextColor.color(0x4682B4),
            "Slow time and movement around you; an immovable anchor of apathy.");

    private final String displayName;
    private final Material iconMaterial;
    private final TextColor color;
    private final String description;

    SinGemType(String displayName, Material iconMaterial, TextColor color, String description) {
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public TextColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public Component getFormattedName() {
        return Component.text("Relic Gem of " + displayName, color).decorate(TextDecoration.BOLD);
    }
}
