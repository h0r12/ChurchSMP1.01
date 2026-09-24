package com.churchsmp.recipe;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.item.LiminalCoreItem;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class ChurchRecipeManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChurchRecipeManager(ChurchSMP plugin) {
        this.plugin = plugin;
        registerWeaponRecipes();
    }

    private void registerWeaponRecipes() {
        // Excalibur
        registerWeaponRecipe("recipe_excalibur", "excalibur", SinGemType.PRIDE, Material.NETHER_STAR, Material.NETHERITE_SWORD);
        // Luminescence Spear
        registerWeaponRecipe("recipe_luminescence_spear", "luminescence_spear", SinGemType.GREED, Material.AMETHYST_SHARD, Material.TRIDENT);
        // Mayim
        registerWeaponRecipe("recipe_mayim", "mayim", SinGemType.GLUTTONY, Material.HEART_OF_THE_SEA, Material.NETHERITE_SWORD);
        // Judas
        registerWeaponRecipe("recipe_judas", "judas", SinGemType.WRATH, Material.GOLD_BLOCK, Material.NETHERITE_AXE);
        // Sorrowess
        registerWeaponRecipe("recipe_sorrowess", "sorrowess", SinGemType.ENVY, Material.CRYING_OBSIDIAN, Material.TRIDENT);
        // VoidBreaker
        registerWeaponRecipe("recipe_voidbreaker", "voidbreaker", SinGemType.WRATH, Material.ECHO_SHARD, Material.MACE);
        // Grim
        registerWeaponRecipe("recipe_grim", "grim", SinGemType.SLOTH, Material.WITHER_SKELETON_SKULL, Material.NETHERITE_SWORD);

        registerRelicRecipes();
    }

    private void registerRelicRecipes() {
        ItemStack core = LiminalCoreItem.createCore(plugin);
        RecipeChoice.ExactChoice coreChoice = new RecipeChoice.ExactChoice(core);

        // Impiety (God / Holiness): Requires Liminal Core + Totem + Diamond Block + Gold Block + Nether Star
        try {
            NamespacedKey key = new NamespacedKey(plugin, "recipe_impiety");
            try { Bukkit.removeRecipe(key); } catch (Throwable ignored) {}
            ShapedRecipe recipe = new ShapedRecipe(key, RelicItem.createRelic(plugin, RelicItem.RelicType.IMPIETY));
            recipe.shape(" G ", "TCT", " D ");
            recipe.setIngredient('G', Material.GOLD_BLOCK);
            recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
            recipe.setIngredient('C', coreChoice);
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            Bukkit.addRecipe(recipe);
        } catch (Throwable ignored) {}

        // Iniquity (Evil / Sins): Requires Liminal Core + Wither Skull + Netherite Ingot + Crying Obsidian
        try {
            NamespacedKey key = new NamespacedKey(plugin, "recipe_iniquity");
            try { Bukkit.removeRecipe(key); } catch (Throwable ignored) {}
            ShapedRecipe recipe = new ShapedRecipe(key, RelicItem.createRelic(plugin, RelicItem.RelicType.INIQUITY));
            recipe.shape(" W ", "NCN", " O ");
            recipe.setIngredient('W', Material.WITHER_SKELETON_SKULL);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('C', coreChoice);
            recipe.setIngredient('O', Material.CRYING_OBSIDIAN);
            Bukkit.addRecipe(recipe);
        } catch (Throwable ignored) {}

        // Obscura (Voided / Neutrality): Requires Liminal Core + Echo Shard + End Crystal + Heavy Core
        try {
            NamespacedKey key = new NamespacedKey(plugin, "recipe_obscura");
            try { Bukkit.removeRecipe(key); } catch (Throwable ignored) {}
            ShapedRecipe recipe = new ShapedRecipe(key, RelicItem.createRelic(plugin, RelicItem.RelicType.OBSCURA));
            recipe.shape(" E ", "YCY", " X ");
            recipe.setIngredient('E', Material.ECHO_SHARD);
            recipe.setIngredient('Y', Material.END_CRYSTAL);
            recipe.setIngredient('C', coreChoice);
            recipe.setIngredient('X', Material.OBSIDIAN);
            Bukkit.addRecipe(recipe);
        } catch (Throwable ignored) {}
    }

    private void registerWeaponRecipe(String recipeKeyStr, String weaponId, SinGemType gemType, Material coreMaterial, Material baseMaterial) {
        try {
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
            if (weapon == null) return;

            NamespacedKey key = new NamespacedKey(plugin, recipeKeyStr);
            try { Bukkit.removeRecipe(key); } catch (Throwable ignored) {}

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
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(result);
        RelicItem.RelicType relic = RelicItem.getRelicType(result, plugin);

        if (weapon != null || relic != null) {
            // Trigger 3D orbiting recipe ingredients animation
            List<ItemStack> ingredients = new ArrayList<>();
            for (ItemStack matrixItem : event.getInventory().getMatrix()) {
                if (matrixItem != null && !matrixItem.getType().isAir()) {
                    ingredients.add(matrixItem.clone());
                }
            }

            playOrbitingCraftAnimation(player, ingredients, result.clone());

            String itemName = weapon != null ? TextUtil.toSmallCaps(weapon.getId().replace('_', ' ')) : TextUtil.toSmallCaps(relic.getId());
            player.sendMessage(miniMessage.deserialize(
                    "<gold>✦ <white><bold>The altar resonated with cosmic energy and forged [</bold></white><yellow><bold>" + itemName + "</bold></yellow><white><bold>]!</bold></white></gold>"
            ));
        }
    }

    /**
     * Custom 3D animation of recipe ingredients orbiting in a circle around the crafting location,
     * converging into the center before flashing and delivering the crafted item!
     */
    public void playOrbitingCraftAnimation(Player player, List<ItemStack> ingredients, ItemStack resultItem) {
        Location center = player.getLocation().add(0, 1.2, 0);
        List<Item> floatingItems = new ArrayList<>();

        int count = Math.max(1, ingredients.size());
        for (int i = 0; i < count; i++) {
            ItemStack stack = ingredients.get(i % ingredients.size());
            Item item = player.getWorld().dropItem(center, stack);
            item.setGravity(false);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setVelocity(new Vector(0, 0, 0));
            floatingItems.add(item);
        }

        player.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.8f);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 40 || !player.isOnline()) {
                    for (Item item : floatingItems) {
                        if (item.isValid()) item.remove();
                    }
                    if (player.isOnline()) {
                        player.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.5f);
                        player.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
                        player.getWorld().spawnParticle(Particle.FLASH, center, 2, Color.WHITE);
                        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 35, 0.4, 0.4, 0.4, 0.15);
                    }
                    cancel();
                    return;
                }

                angle += 0.25;
                double currentRadius = Math.max(0.1, 1.4 - (ticks * 0.03));
                double yBob = Math.sin(ticks * 0.25) * 0.2;

                for (int i = 0; i < floatingItems.size(); i++) {
                    Item item = floatingItems.get(i);
                    if (!item.isValid()) continue;
                    double itemAngle = angle + (i * (2 * Math.PI / floatingItems.size()));
                    Location loc = center.clone().add(Math.cos(itemAngle) * currentRadius, yBob, Math.sin(itemAngle) * currentRadius);
                    item.teleport(loc);

                    center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0, 0, 0, 0);
                    center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.2f));
                }

                if (ticks % 8 == 0) {
                    player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f + (ticks * 0.02f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
