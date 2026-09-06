package com.churchsmp.weapons;

import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Material;

/**
 * The 7 special weapons: 3 Good, 3 Evil, 1 Nullified.
 * "category" decides which alignment tiers may activate the weapon's abilities.
 */
public enum WeaponType {

    BLADE_OF_ARCHANGEL(
            "blade_of_archangel", "Excalibur", Material.NETHERITE_SWORD,
            Category.GOOD,
            "Ability 1: Accelerated Nova (4s charge, true-dmg burst, 45s cd)",
            "Ability 2: Altar's Pin (5s pull, sword smash, 80s cd)"
    ),
    SWORD_OF_DAVID(
            "sword_of_david", "Luminescence Spear", Material.TRIDENT,
            Category.GOOD,
            "Ability 1: Unseen Pierce (4x teleport-lunge, 4.5 dmg total)",
            "Ability 2: Giant Slayer (guaranteed crit + Strength)"
    ),
    STAFF_OF_MOSES(
            "staff_of_moses", "Mayim", Material.NETHERITE_SWORD,
            Category.GOOD,
            "Ability 1: Frost Edge (escalating chill, 20s, 30s cd after)",
            "Ability 2: Entangle Freeze (1s charge, stun or AoE freeze)"
    ),
    SCYTHE_OF_CAIN(
            "scythe_of_cain", "Scythe of Cain", Material.NETHERITE_HOE,
            Category.EVIL,
            "Ability 1: Lifesteal Strike",
            "Ability 2: Mark of Cain (DOT + reveal through walls)"
    ),
    SORROWESS(
            "sorrowess", "Sorrowess", Material.TRIDENT,
            Category.EVIL,
            "Ability 1: Grief Shards (5 floating daggers, 1 dmg each)",
            "Ability 2: Bloody Rain (free riptide 30s, wither+darkness zone)"
    ),
    BLADE_OF_JUDAS(
            "blade_of_judas", "Judas", Material.NETHERITE_AXE,
            Category.EVIL,
            "Ability 1: Hemorrhaged Mold (3 wither-skull charges, stun/lightning)",
            "Ability 2: Thirty Pieces of Silver (3-heart sac, Strength III 15s)"
    ),
    VOIDBREAKER(
            "voidbreaker", "VoidBreaker", Material.MACE,
            Category.NULLIFIED,
            "Ability 1 (Density mode): Spiral Boom | (Breach mode): Lightless Ph\u014ds",
            "Ability 2: Spaced Bound (toggle Density/Breach + dash)"
    );

    public enum Category { GOOD, EVIL, NULLIFIED }

    private final String id;
    private final String displayName;
    private final Material material;
    private final Category category;
    private final String ability1Desc;
    private final String ability2Desc;

    WeaponType(String id, String displayName, Material material, Category category,
               String ability1Desc, String ability2Desc) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.category = category;
        this.ability1Desc = ability1Desc;
        this.ability2Desc = ability2Desc;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public Category getCategory() { return category; }
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
