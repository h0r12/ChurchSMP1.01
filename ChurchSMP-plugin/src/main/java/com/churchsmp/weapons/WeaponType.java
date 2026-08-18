package com.churchsmp.weapons;

import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Material;

/**
 * The 7 special weapons: 3 Good, 3 Evil, 1 Nullified.
 * "category" decides which alignment tiers may activate the weapon's abilities.
 */
public enum WeaponType {

    BLADE_OF_ARCHANGEL(
            "blade_of_archangel", "Blade of the Archangel", Material.NETHERITE_SWORD,
            Category.GOOD,
            "Ability 1: Radiant Barrier (Resistance + Absorption)",
            "Ability 2: Holy Nova (AoE damage to Evil, heal to Good)"
    ),
    SWORD_OF_DAVID(
            "sword_of_david", "Sword of David", Material.GOLDEN_SWORD,
            Category.GOOD,
            "Ability 1: Smite Beam (bonus dmg vs. Undead/Evil)",
            "Ability 2: Giant Slayer (guaranteed crit + Strength)"
    ),
    STAFF_OF_MOSES(
            "staff_of_moses", "Staff of Moses", Material.WARPED_FUNGUS_ON_A_STICK,
            Category.GOOD,
            "Ability 1: Parting Wave (knockback line)",
            "Ability 2: Sea Path (temporary water bridge)"
    ),
    SCYTHE_OF_CAIN(
            "scythe_of_cain", "Scythe of Cain", Material.NETHERITE_HOE,
            Category.EVIL,
            "Ability 1: Lifesteal Strike",
            "Ability 2: Mark of Cain (DOT + reveal through walls)"
    ),
    TRIDENT_OF_LEVIATHAN(
            "trident_of_leviathan", "Trident of Leviathan", Material.TRIDENT,
            Category.EVIL,
            "Ability 1: Whirlpool Pull",
            "Ability 2: Leviathan's Roar (fear: blind + slow)"
    ),
    BLADE_OF_JUDAS(
            "blade_of_judas", "Blade of Judas", Material.IRON_SWORD,
            Category.EVIL,
            "Ability 1: Backstab + Smoke Escape",
            "Ability 2: Thirty Pieces of Silver (self-sac guaranteed crit)"
    ),
    STAFF_OF_SOLOMON(
            "staff_of_solomon", "Staff of Solomon", Material.BLAZE_ROD,
            Category.NULLIFIED,
            "Ability 1: Judgment (reveal nearby alignments)",
            "Ability 2: Wisdom's Verdict (nullify abilities in radius)"
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
