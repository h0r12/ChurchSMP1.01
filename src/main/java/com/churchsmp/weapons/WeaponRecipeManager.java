package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Thematic (arbitrary but flavorful) crafting recipes for each weapon.
 * These are intentionally craftable by anyone — CraftingGuardListener is
 * what actually blocks the result unless the crafter's alignment tier
 * matches the weapon's category, so a Fallen player can gather the
 * ingredients but the altar/table will refuse to yield the finished item.
 *
 * Every recipe now also requires exactly one sin gem (any of the 7 —
 * they're generic currency here, not restricted to matching the weapon's
 * category) as the price of forging a legendary weapon, consuming it in
 * the process. Where the original shape had an unused blank slot, the
 * gem was added there so existing ingredient costs weren't touched;
 * Excalibur's grid was already completely full, so one of its four
 * Netherite Ingots was swapped out for the gem slot instead.
 */
public class WeaponRecipeManager {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final com.churchsmp.relics.RelicManager relicManager;

    public WeaponRecipeManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.relicManager = plugin.getRelicManager();
    }

    public void registerAll() {
        register(WeaponType.BLADE_OF_ARCHANGEL,
                new String[]{"NDN", "DBD", "NGX"},
                Map(
                        'N', Material.NETHERITE_INGOT,
                        'D', Material.DIAMOND,
                        'B', Material.BOOK,
                        'G', Material.GOLD_BLOCK
                ));

        register(WeaponType.SWORD_OF_DAVID,
                new String[]{"XG ", "GBG", " S "},
                Map(
                        'G', Material.GOLD_INGOT,
                        'B', Material.BOOK,
                        'S', Material.STICK
                ));

        register(WeaponType.STAFF_OF_MOSES,
                new String[]{"XW ", " W ", "PWP"},
                Map(
                        'W', Material.WARPED_FUNGUS_ON_A_STICK,
                        'P', Material.PRISMARINE_SHARD
                ));

        register(WeaponType.SCYTHE_OF_CAIN,
                new String[]{"NNR", "XSF", "S  "},
                Map(
                        'N', Material.NETHERITE_SCRAP,
                        'R', Material.ROTTEN_FLESH,
                        'S', Material.STICK,
                        'F', Material.WITHER_ROSE
                ));

        register(WeaponType.SORROWESS,
                new String[]{"XT ", "PTP", " T "},
                Map(
                        'T', Material.TRIDENT,
                        'P', Material.PRISMARINE_CRYSTALS
                ));

        register(WeaponType.BLADE_OF_JUDAS,
                new String[]{"XI ", "ISI", " C "},
                Map(
                        'I', Material.IRON_INGOT,
                        'S', Material.STICK,
                        'C', Material.GOLD_NUGGET // "thirty pieces of silver" — represented with gold nuggets
                ));

        register(WeaponType.VOIDBREAKER,
                new String[]{"XQ ", "BRB", " Q "},
                Map(
                        'Q', Material.QUARTZ,
                        'B', Material.BLAZE_ROD,
                        'R', Material.END_ROD
                ));
    }

    private void register(WeaponType type, String[] shape, java.util.Map<Character, Material> ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + type.getId());
        ItemStack result = weaponManager.createWeapon(type);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        for (var entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        recipe.setIngredient('X', new RecipeChoice.ExactChoice(relicManager.allGems()));
        Bukkit.addRecipe(recipe);
    }

    // small helper so recipe definitions above read as literal maps
    private java.util.Map<Character, Material> Map(Object... pairs) {
        java.util.Map<Character, Material> map = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Character) pairs[i], (Material) pairs[i + 1]);
        }
        return map;
    }
}
