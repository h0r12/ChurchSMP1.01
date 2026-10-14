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
            "Slow time and movement around you; an immovable anchor of apathy."),
    SIN("SIN", Material.NETHERITE_BLOCK, TextColor.color(0xDC2626),
            "Weighed by a thousand losses, the definition of retaliation.");

    public static final SinGemType[] BASE_SINS = { WRATH, GREED, GLUTTONY, LUST, ENVY, PRIDE, SLOTH };

    public boolean isMythic() {
        return this == SIN;
    }

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

    // Palette for SIN: Black -> Charcoal -> Dark Slate -> Silver -> Dark Slate -> Charcoal -> Black
    private static final int[][] SIN_PALETTE = {
            { 10, 10, 10 },    // #0A0A0A
            { 45, 45, 45 },    // #2D2D2D
            { 80, 80, 80 },    // #505050
            { 130, 130, 130 }, // #828282
            { 190, 190, 190 }, // #BEBEBE
            { 130, 130, 130 }, // #828282
            { 80, 80, 80 },    // #505050
            { 45, 45, 45 }     // #2D2D2D
    };

    // Palette for Gem: Pure White -> Ivory -> Pale Gold -> Brilliant Gold -> Deep Amber -> Brilliant Gold -> Pale Gold -> Ivory
    private static final int[][] GEM_PALETTE = {
            { 255, 255, 255 }, // #FFFFFF
            { 255, 248, 225 }, // #FFF8E1
            { 255, 235, 128 }, // #FFE480
            { 255, 215, 0 },   // #FFD700
            { 255, 179, 0 },   // #FFB300
            { 255, 215, 0 },   // #FFD700
            { 255, 235, 128 }, // #FFE480
            { 255, 248, 225 }  // #FFF8E1
    };

    public static String getSinColorHex(double phase) {
        return interpolatePalette(SIN_PALETTE, phase);
    }

    public static String getGemColorHex(double phase) {
        return interpolatePalette(GEM_PALETTE, phase);
    }

    private static String interpolatePalette(int[][] palette, double phase) {
        int n = palette.length;
        double p = (phase % n + n) % n;
        int idx = (int) p;
        int next = (idx + 1) % n;
        double frac = p - idx;

        int r = (int) Math.round(palette[idx][0] + (palette[next][0] - palette[idx][0]) * frac);
        int g = (int) Math.round(palette[idx][1] + (palette[next][1] - palette[idx][1]) * frac);
        int b = (int) Math.round(palette[idx][2] + (palette[next][2] - palette[idx][2]) * frac);

        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static String getAnimatedSinGemMiniMessage(long tick) {
        double t = tick * 0.15;
        String s1 = getSinColorHex(t);
        String s2 = getSinColorHex(t + 1.2);
        String s3 = getSinColorHex(t + 2.4);

        String g1 = getGemColorHex(t);
        String g2 = getGemColorHex(t + 1.2);
        String g3 = getGemColorHex(t + 2.4);

        return "<bold><gradient:" + s1 + ":" + s2 + ":" + s3 + ">SIN</gradient></bold> <gradient:" + g1 + ":" + g2 + ":" + g3 + ">Gem</gradient>";
    }

    public static Component getAnimatedSinGemName(long tick) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(getAnimatedSinGemMiniMessage(tick));
    }

    public Component getFormattedName() {
        if (this == SIN) {
            return getAnimatedSinGemName(System.currentTimeMillis() / 100);
        }
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
            case SIN -> "Rusty Blood";
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
            case SIN -> "Veins of Flagitious";
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
            case SIN -> "gem_sin_rusty_blood";
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
            case SIN -> "gem_sin_flagitious";
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
            case SIN -> "<gradient:#000000:#DC2626:#FFD700>";
        };
    }

    public String getDynamicThemeGradientTag(long tick) {
        if (this == SIN) {
            double t = tick * 0.15;
            String c1 = getSinColorHex(t);
            String c2 = "#DC2626";
            String c3 = getGemColorHex(t + 1.5);
            return "<gradient:" + c1 + ":" + c2 + ":" + c3 + ">";
        }
        return getThemeGradientTag();
    }
}
