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

    public String getPrimaryAbilityName() {
        return switch (this) {
            case WRATH -> "Blood Scythe";
            case GREED -> "Taxing Ray";
            case GLUTTONY -> "Devour Buff";
            case LUST -> "Narcissus Mirror";
            case ENVY -> "Mirror of Shame";
            case PRIDE -> "Sovereign Charge";
            case SLOTH -> "Temporal Echo";
        };
    }

    public String getSecondaryAbilityName() {
        return switch (this) {
            case WRATH -> "Overdrive";
            case GREED -> "Taken";
            case GLUTTONY -> "Acid Spout";
            case LUST -> "Vanity Shield";
            case ENVY -> "Shadow Covet";
            case PRIDE -> "Tombstone Duel";
            case SLOTH -> "Delayed Stasis";
        };
    }

    public String getPrimaryCooldownKey() {
        return switch (this) {
            case WRATH -> "gem_wrath_scythe";
            case GREED -> "gem_greed_ray";
            case GLUTTONY -> "gem_gluttony_devour";
            case LUST -> "gem_lust_mirror";
            case ENVY -> "gem_envy_mirror";
            case PRIDE -> "gem_pride_charge";
            case SLOTH -> "gem_sloth_echo";
        };
    }

    public String getSecondaryCooldownKey() {
        return switch (this) {
            case WRATH -> "gem_wrath_overdrive";
            case GREED -> "gem_greed_taken";
            case GLUTTONY -> "gem_gluttony_acid";
            case LUST -> "gem_lust_shield";
            case ENVY -> "gem_envy_covet";
            case PRIDE -> "gem_pride_duel";
            case SLOTH -> "gem_sloth_stasis";
        };
    }

    public String getThemeGradientTag() {
        return switch (this) {
            case WRATH -> "<gradient:#8B0000:#FF2222>";
            case GREED -> "<gradient:#FFD700:#FFA500>";
            case GLUTTONY -> "<gradient:#2E8B57:#32CD32>";
            case LUST -> "<gradient:#FF69B4:#FF1493>";
            case ENVY -> "<gradient:#008080:#00CED1>";
            case PRIDE -> "<gradient:#FFD700:#FFF8DC>";
            case SLOTH -> "<gradient:#4169E1:#4682B4>";
        };
    }
}
