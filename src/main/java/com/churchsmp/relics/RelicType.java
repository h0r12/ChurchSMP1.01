package com.churchsmp.relics;

import org.bukkit.Material;

/**
 * The 7 sin relics, granted on join (see RelicJoinListener). Only WRATH is
 * fully implemented right now — the rest are placeholders so the enum
 * shape (and anything that iterates all RelicTypes, like the crafting
 * gem-choice list) doesn't need to change again as each one gets built.
 */
public enum RelicType {

    WRATH(
            "wrath", "Wrath", "Love of Fury", Material.REDSTONE,
            new String[]{
                    "Bloodfeast: +0.75 attack damage per missing heart.",
                    "Bloodlust: kills or ability triggers grant Speed III for 10s.",
                    "BloodPrice: below -50 alignment, +15% melee damage but +10% damage taken."
            },
            "Ability 1: Blood Scythe (Sneak+hit, 8s Fury: guaranteed crit + true fire dmg, builds Revenge, 40s cd)",
            "Ability 2: Overdrive (hit with 2+ Revenge: 5x5 flame ring, Stun+Darkness+DoT, 60s cd)"
    ),
    GREED("greed", "Greed", "Burning Golds", Material.GOLD_NUGGET,
            new String[]{
                    "Preloaded: /ritual <ores|sword|head> preloads an item for Ability 2 to consume.",
                    "Gold Siphon: melee hits have a 2% chance to steal a Golden Apple or Ender Pearl from the target's hotbar (20s cd per target)."
            },
            "Ability 1: Taxing Ray (RMB, 8-block gold beam seals offhand+hotbar slot 4s, 32s cd)",
            "Ability 2: Taken (Sneak+RMB, consumes /ritual preload, 120s cd)"),
    GLUTTONY("gluttony", "Gluttony", "Food of Bitterness", Material.ROTTEN_FLESH, new String[0],
            "Ability 1: Devour Buff (not yet implemented)",
            "Ability 2: Acid Spout (not yet implemented)"),
    LUST("lust", "Lust", "Self Love", Material.PINK_DYE, new String[0],
            "Ability 1: Narcissus Mirror (not yet implemented)",
            "Ability 2: Vanity Shield (not yet implemented)"),
    ENVY("envy", "Envy", "Reflection of Shame", Material.EMERALD, new String[0],
            "Ability 1: Mirror of Shame (not yet implemented)",
            "Ability 2: Shadow Covet (not yet implemented)"),
    PRIDE("pride", "Pride", "Dignity of the Dead", Material.AMETHYST_SHARD, new String[0],
            "Ability 1: Sovereign Charge (not yet implemented)",
            "Ability 2: Tombstone Duel (not yet implemented)"),
    SLOTH("sloth", "Sloth", "Slowed Echo", Material.GRAY_DYE, new String[0],
            "Ability 1: Temporal Echo (not yet implemented)",
            "Ability 2: Delayed Stasis (not yet implemented)");

    private final String id;
    private final String displayName;
    private final String subtitle;
    private final Material material;
    private final String[] passives;
    private final String ability1Desc;
    private final String ability2Desc;

    RelicType(String id, String displayName, String subtitle, Material material,
              String[] passives, String ability1Desc, String ability2Desc) {
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.material = material;
        this.passives = passives;
        this.ability1Desc = ability1Desc;
        this.ability2Desc = ability2Desc;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSubtitle() { return subtitle; }
    public Material getMaterial() { return material; }
    public String[] getPassives() { return passives; }
    public String getAbility1Desc() { return ability1Desc; }
    public String getAbility2Desc() { return ability2Desc; }

    public static RelicType fromId(String id) {
        for (RelicType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
