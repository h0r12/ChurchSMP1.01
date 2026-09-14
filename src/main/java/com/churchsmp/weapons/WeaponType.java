package com.churchsmp.weapons;

import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Material;

/**
 * The 7 special weapons: 3 Good, 3 Evil, 1 Nullified.
 * "category" decides which alignment tiers may activate the weapon's abilities.
 */
public enum WeaponType {

    BLADE_OF_ARCHANGEL(
            "excalibur", "Excalibur", "Forged by God's Will", Material.NETHERITE_SWORD,
            Category.GOOD,
            new String[]{
                    "Hopeful: every attack gives the target Glowing, colored by their alignment.",
                    "Wings: no fall damage or high-speed-impact damage while it's anywhere in your inventory.",
                    "Crouching reveals nearby players, color-coded by alignment."
            },
            "Ability 1: Accelerated Nova (10s charge, sonic-boom-line true dmg, 60s cd)",
            "Ability 2: Altar Pining (launch + slam, true dmg twice, 105s cd)"
    ),
    SWORD_OF_DAVID(
            "luminescence_spear", "Luminescence Spear", "As the Shadow Fails Behind", Material.TRIDENT,
            Category.GOOD,
            new String[]{
                    "Bolt: landing from a fall explodes based on fall speed, 60s cd.",
                    "LightStealing: a thrown hit inflicts Darkness for 10s, 60s cd.",
                    "BurningBones: melee hits are sword-tier damage, gated by a cooldown (trident-tier otherwise)."
            },
            "Ability 1: Blink (3 charges, 6-block dash + lightning trail)",
            "Ability 2: SunEclipse (mark + delayed beam, 130s cd)"
    ),
    STAFF_OF_MOSES(
            "mayim", "Mayim", "Holy water.", Material.NETHERITE_SWORD,
            Category.GOOD,
            new String[]{
                    "Water Mighty: Strength I on land, Strength III in water.",
                    "Rust: a chance on hit to corrode their armor and mend yours.",
                    "Icy Path: you slide like you're on ice while holding it."
            },
            "Ability 1: Frost Edge (escalating chill, 20s, 30s cd after)",
            "Ability 2: Entangle Freeze (1s charge, stun or AoE freeze)"
    ),
    SCYTHE_OF_CAIN(
            "grim", "Grim", "And Soul Cycle Spirals Again.", Material.NETHERITE_SWORD,
            Category.EVIL,
            new String[]{
                    "Disgusts: nearby entities periodically get Nausea + Poison for 2s.",
                    "Soultaking: drop-key throws the sword to steal 2 hearts, teleport behind the target, and blind them for 10s. 60s cd.",
                    "Reaper: kills (tracked on the sword itself) grant +1 max heart and extend potion effects by 20s. Sneak to check its kill count."
            },
            "Ability 1: HollowedOut (next hit debuffs + 40% action-fail, 15s, 60s cd)",
            "Ability 2: Dark Particle (next hit Sharpness X, stacking hearts, 25s, 80s cd)"
    ),
    SORROWESS(
            "sorrowess", "Sorrowess", "Its-a-sorrowy day...", Material.TRIDENT,
            Category.EVIL,
            new String[]{
                    "Forming: crouching summons water at your feet.",
                    "Brave: holding it grants +2 max hearts, lost when you drop it."
            },
            "Ability 1: Grief Shards (5 white items, 2.5 true dmg + Bleedout)",
            "Ability 2: Gloom (crits inflict Depressed: -20% armor, 10s, 60s cd)"
    ),
    BLADE_OF_JUDAS(
            "judas", "Judas", "Decayed blood.", Material.NETHERITE_AXE,
            Category.EVIL,
            new String[]{
                    "Bloodfeast: you cannot regenerate at all while holding it.",
                    "Unfree: a periodic chance of a random debuff (Judas's gift).",
                    "Bite: 5% chance per hit for Wither + Nausea + Blindness, 90s cd."
            },
            "Ability 1: Hemorrhaged Mold (3 wither-skull charges, stun/lightning)",
            "Ability 2: Thirty Pieces of Silver (3-heart sac, Strength III 15s)"
    ),
    VOIDBREAKER(
            "voidbreaker", "VoidBreaker", "The Abandoned Unknowing.", Material.MACE,
            Category.NULLIFIED,
            new String[]{
                    "Voidfeels: double jump, 5s cd.",
                    "Crumble: a 10+ block slam counts down from 7(+Fractured uses); hitting 0 doubles your damage against nearby opponents only.",
                    "Rifted: sneak + double jump launches you toward your crosshair, 30s cd (halved by each slam)."
            },
            "Ability 1 (Density): Fractured (empowered Crumble, 75s cd) | (Breach): Infection (thrown Fallen mark, 130s cd)",
            "Ability 2: Bound (toggle Density VI+Wind Burst / Breach VI, 3s cd)"
    );

    public enum Category { GOOD, EVIL, NULLIFIED }

    private final String id;
    private final String displayName;
    private final String subtitle;
    private final Material material;
    private final Category category;
    private final String[] passives;
    private final String ability1Desc;
    private final String ability2Desc;

    WeaponType(String id, String displayName, String subtitle, Material material, Category category,
               String[] passives, String ability1Desc, String ability2Desc) {
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.material = material;
        this.category = category;
        this.passives = passives;
        this.ability1Desc = ability1Desc;
        this.ability2Desc = ability2Desc;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSubtitle() { return subtitle; }
    public Material getMaterial() { return material; }
    public Category getCategory() { return category; }
    public String[] getPassives() { return passives; }
    public String getAbility1Desc() { return ability1Desc; }
    public String getAbility2Desc() { return ability2Desc; }

    /**
     * Hex color stops for this weapon's gradient name, taken directly from
     * your RGBirdflop screenshots. Where the screenshot showed the exact
     * per-letter output (VoidBreaker, Excalibur), the array has one entry
     * per letter — no interpolation needed. Where it only showed the 3
     * control colors (Mayim, Sorrowess, Judas), the array has 3 entries
     * and gets interpolated evenly across however many letters the name
     * has. Luminescence Spear's output was cut off in the screenshot, so
     * this reconstructs it as a 3-stop white/navy/white gradient across
     * the full "Luminescence Spear" string, matching the symmetric
     * pattern the visible portion showed.
     */
    public int[] getNameGradient() {
        return switch (this) {
            case BLADE_OF_ARCHANGEL -> new int[]{
                    0xFFFFFF, 0xDCD7CD, 0xB9B09B, 0x968869, 0x736037, 0x968869, 0xB9B09B, 0xDCD7CD, 0xFFFFFF};
            case SWORD_OF_DAVID -> new int[]{0xFFFFFF, 0x1A2886, 0xFFFFFF};
            case STAFF_OF_MOSES -> new int[]{0xFFFFFF, 0x00FEFF, 0x184390};
            case SCYTHE_OF_CAIN -> new int[]{0x143309, 0x435C3A, 0x72856B, 0xA1AD9D};
            case SORROWESS -> new int[]{0xFFFFFF, 0xFF6B6B, 0x341313};
            case BLADE_OF_JUDAS -> new int[]{0x5D0000, 0xFF0000, 0x341313};
            case VOIDBREAKER -> new int[]{
                    0x585858, 0x797979, 0x9B9B9B, 0xBCBCBC, 0xDEDEDE, 0xFFFFFF,
                    0xDEDDE1, 0xBCBCC3, 0x9B9AA4, 0x797986, 0x585768};
        };
    }

    /**
     * Boss bar color for this weapon's active-ability countdown. Vanilla
     * boss bars only support 7 fixed colors (PINK, BLUE, RED, GREEN,
     * YELLOW, PURPLE, WHITE) — no gold, cyan, gray, or black exist, and
     * there's no way to distinguish "dark red" from "bright red" as two
     * separate colors. Closest available substitutes, each used exactly
     * once so every weapon still reads as visually distinct:
     * Excalibur=gold->YELLOW, Luminescence Spear=white->WHITE,
     * Mayim=light cyan->BLUE, VoidBreaker=gray/black->PURPLE (closest
     * "dark/moody" option), Judas=dark red->RED, Grim=dark green->GREEN,
     * Sorrowess=bright red->PINK (RED was already taken by Judas).
     */
    public org.bukkit.boss.BarColor getBarColor() {
        return switch (this) {
            case BLADE_OF_ARCHANGEL -> org.bukkit.boss.BarColor.YELLOW;
            case SWORD_OF_DAVID -> org.bukkit.boss.BarColor.WHITE;
            case STAFF_OF_MOSES -> org.bukkit.boss.BarColor.BLUE;
            case VOIDBREAKER -> org.bukkit.boss.BarColor.PURPLE;
            case BLADE_OF_JUDAS -> org.bukkit.boss.BarColor.RED;
            case SCYTHE_OF_CAIN -> org.bukkit.boss.BarColor.GREEN;
            case SORROWESS -> org.bukkit.boss.BarColor.PINK;
        };
    }

    /** Whether the given alignment tier is allowed to activate this weapon. */
    public boolean isUsableBy(AlignmentTier tier) {
        return switch (category) {
            case GOOD -> tier.isGood();
            case EVIL -> tier.isEvil();
            case NULLIFIED -> tier.isNullified();
        };
    }

    private static final java.util.Map<String, String> LEGACY_IDS = java.util.Map.of(
            "blade_of_archangel", "excalibur",
            "sword_of_david", "luminescence_spear",
            "staff_of_moses", "mayim",
            "blade_of_judas", "judas",
            "scythe_of_cain", "grim"
    );

    /** Recognizes both current and pre-rename item IDs, so weapons crafted before an ID rename keep working. */
    public static WeaponType fromId(String id) {
        String resolved = LEGACY_IDS.getOrDefault(id, id);
        for (WeaponType type : values()) {
            if (type.id.equals(resolved)) return type;
        }
        return null;
    }
}
