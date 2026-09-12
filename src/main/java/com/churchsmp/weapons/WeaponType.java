package com.churchsmp.weapons;

import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Material;

/**
 * The 7 special weapons: 3 Good, 3 Evil, 1 Nullified.
 * "category" decides which alignment tiers may activate the weapon's abilities.
 */
public enum WeaponType {

    BLADE_OF_ARCHANGEL(
            "blade_of_archangel", "Excalibur", "Light fused Blade.", Material.NETHERITE_SWORD,
            Category.GOOD,
            new String[]{
                    "Hopeful: every attack gives the target Glowing.",
                    "Wings: you take no fall damage.",
                    "Crouching reveals nearby players, color-coded by alignment."
            },
            "Ability 1: Accelerated Nova (4s charge, true-dmg burst, 45s cd)",
            "Ability 2: Altar's Pin (5s pull, sword smash, 80s cd)"
    ),
    SWORD_OF_DAVID(
            "sword_of_david", "Luminescence Spear", "The lighten spike of shadows.", Material.TRIDENT,
            Category.GOOD,
            new String[]{
                    "Flash: a thrown hit inflicts Glowing + a lightning strike + Weakness (2s), 5s cd.",
                    "Bolt: landing from a fall explodes based on fall speed, 25s cd."
            },
            "Ability 1: Unseen Pierce (4x teleport-lunge, 4.5 dmg total)",
            "Ability 2: Glare (toggle Loyalty VI / Riptide VI)"
    ),
    STAFF_OF_MOSES(
            "staff_of_moses", "Mayim", "Holy water.", Material.NETHERITE_SWORD,
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
            "scythe_of_cain", "Luminous Cain", "Obsession Gazer", Material.NETHERITE_HOE,
            Category.EVIL,
            new String[0],
            "Ability 1: Lifesteal Strike",
            "Ability 2: Mark of Cain (DOT + reveal through walls)"
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
            "blade_of_judas", "Judas", "Decayed blood.", Material.NETHERITE_AXE,
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
                    "Crumble: a 10+ block slam builds Sin (1/7-7/7); the 8th hit doubles your damage with an Aftershock.",
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

    /** Whether the given alignment tier is allowed to activate this weapon. */
    public boolean isUsableBy(AlignmentTier tier) {
        return switch (category) {
            case GOOD -> tier.isGood();
            case EVIL -> tier.isEvil();
            case NULLIFIED -> tier.isNullified();
        };
    }

    public static WeaponType fromId(String id) {
        for (WeaponType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
