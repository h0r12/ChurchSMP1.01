package com.churchsmp.recipe;

import com.churchsmp.ChurchSMP;
import com.churchsmp.item.LiminalCoreItem;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;

public class ChurchRecipeManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChurchRecipeManager(ChurchSMP plugin) {
        this.plugin = plugin;
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

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        RelicItem.RelicType relic = RelicItem.getRelicType(result, plugin);
        if (relic != null) {
            // Sound alerts on craft!
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.6f);

            // Trigger non-laggy particle implosion animation
            playOrbitingCraftAnimation(player);

            player.sendMessage(miniMessage.deserialize(
                    "<gold>✦ <white><bold>The altar resonated with cosmic energy and forged [</bold></white><yellow><bold>" + relic.getId().toUpperCase() + "</bold></yellow><white><bold>]!</bold></white></gold>"
            ));
        }
    }

    /**
     * High-performance 3D particle implosion craft animation (Zero entity lag).
     */
    public void playOrbitingCraftAnimation(Player player) {
        Location center = player.getLocation().add(0, 1.0, 0);

        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.4f);
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(147, 50, 180), 1.4f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 20 || !player.isOnline()) {
                    if (player.isOnline()) {
                        player.getWorld().spawnParticle(Particle.FLASH, center, 1, Color.WHITE);
                        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 25, 0.3, 0.3, 0.3, 0.1);
                    }
                    cancel();
                    return;
                }

                angle += 0.35;
                double radius = Math.max(0.1, 1.6 - (ticks * 0.075));
                double yBob = Math.sin(ticks * 0.3) * 0.15;

                for (int i = 0; i < 4; i++) {
                    double itemAngle = angle + (i * (Math.PI / 2.0));
                    Location loc = center.clone().add(Math.cos(itemAngle) * radius, yBob, Math.sin(itemAngle) * radius);
                    center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, (i % 2 == 0) ? goldDust : purpleDust);
                    center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
