package com.churchsmp.recipe;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.weapon.LegendaryWeapon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public class ChurchRecipeManager implements Listener {

    private final ChurchSMP plugin;

    public ChurchRecipeManager(ChurchSMP plugin) {
        this.plugin = plugin;
        registerWeaponRecipes();
    }

    private void registerWeaponRecipes() {
        // Excalibur: requires Nether Star, Netherite Sword, Gold, and Pride Gem
        registerWeaponRecipe("recipe_excalibur", "excalibur", SinGemType.PRIDE, Material.NETHER_STAR, Material.NETHERITE_SWORD);

        // Luminescence Spear: requires Trident, Amethyst Shard, and Greed Gem
        registerWeaponRecipe("recipe_luminescence_spear", "luminescence_spear", SinGemType.GREED, Material.AMETHYST_SHARD, Material.TRIDENT);

        // Mayim: requires Heart of the Sea, Netherite Sword, and Gluttony Gem
        registerWeaponRecipe("recipe_mayim", "mayim", SinGemType.GLUTTONY, Material.HEART_OF_THE_SEA, Material.NETHERITE_SWORD);

        // Judas: requires Netherite Axe, Gold Block, and Wrath Gem
        registerWeaponRecipe("recipe_judas", "judas", SinGemType.WRATH, Material.GOLD_BLOCK, Material.NETHERITE_AXE);

        // Sorrowess: requires Trident, Crying Obsidian, and Envy Gem
        registerWeaponRecipe("recipe_sorrowess", "sorrowess", SinGemType.ENVY, Material.CRYING_OBSIDIAN, Material.TRIDENT);

        // VoidBreaker: requires Mace, Echo Shards, and Wrath Gem
        registerWeaponRecipe("recipe_voidbreaker", "voidbreaker", SinGemType.WRATH, Material.ECHO_SHARD, Material.MACE);

        // Grim: requires Netherite Sword, Wither Skeleton Skull, and Sloth Gem
        registerWeaponRecipe("recipe_grim", "grim", SinGemType.SLOTH, Material.WITHER_SKELETON_SKULL, Material.NETHERITE_SWORD);
    }

    private void registerWeaponRecipe(String recipeKeyStr, String weaponId, SinGemType gemType, Material coreMaterial, Material baseMaterial) {
        try {
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
            if (weapon == null) return;

            NamespacedKey key = new NamespacedKey(plugin, recipeKeyStr);
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {}

            ShapedRecipe recipe = new ShapedRecipe(key, weapon.createItem());
            recipe.shape(" G ", " C ", " B ");

            ItemStack gemItem = plugin.getSinGemManager().createGemItem(gemType);
            recipe.setIngredient('G', new RecipeChoice.ExactChoice(gemItem));
            recipe.setIngredient('C', coreMaterial);
            recipe.setIngredient('B', baseMaterial);

            Bukkit.addRecipe(recipe);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register recipe for " + weaponId + ": " + t.getMessage());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        // Validation during crafting to ensure sin gem is consumed
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(result);
        if (weapon != null) {
            // Player successfully crafted a legendary weapon!
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                player.sendMessage(net.kyori.adventure.text.Component.text("⚔ The altar resonated with your sin relic and forged ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                        .append(weapon.getDisplayName())
                        .append(net.kyori.adventure.text.Component.text("!", net.kyori.adventure.text.format.NamedTextColor.GOLD)));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }
}
