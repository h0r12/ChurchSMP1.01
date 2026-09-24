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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

        // Obscura (Voided / Neutrality): Requires Liminal Core + Echo Shard + End Crystal + Obsidian
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
            // Cancel default instant craft & consume grid items
            event.setCancelled(true);
            CraftingInventory inv = event.getInventory();
            ItemStack[] matrix = inv.getMatrix();
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null && matrix[i].getAmount() > 0) {
                    matrix[i].setAmount(matrix[i].getAmount() - 1);
                    if (matrix[i].getAmount() <= 0) {
                        matrix[i] = null;
                    }
                }
            }
            inv.setMatrix(matrix);
            inv.setResult(null);

            // Close GUI so the player experiences the full-screen cinematic
            player.closeInventory();

            // Sound alerts on craft!
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.4f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.4f);

            // Trigger floating animation with scaling relic and linked items
            playInsaneCraftCinematic(player, relic);

            player.sendMessage(miniMessage.deserialize(
                    "<gold>✦ <white><bold>The altar resonated with cosmic energy and forged [</bold></white><yellow><bold>" + relic.getId().toUpperCase() + "</bold></yellow><white><bold>]!</bold></white></gold>"
            ));
        }
    }

    /**
     * CRAFTING CINEMATIC:
     * 1. Player floats up into the air with Levitation.
     * 2. Orbiting ingredient items circle the player with particle energy beams linked to player's chest.
     * 3. Forged relic floats overhead and SCALES BIGGER in real 3D.
     * 4. Climax delivers relic safely with single celebratory flash and sound.
     */
    public void playInsaneCraftCinematic(Player player, RelicItem.RelicType relic) {
        // 1. Float the player up into the air smoothly
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 65, 1, false, false, false));

        // 2. Determine theme ingredients to orbit around player
        List<ItemStack> ingredients = new ArrayList<>();
        Color themeColor;
        Color accentColor;

        switch (relic) {
            case IMPIETY -> {
                ingredients.add(new ItemStack(Material.TOTEM_OF_UNDYING));
                ingredients.add(new ItemStack(Material.GOLD_BLOCK));
                ingredients.add(new ItemStack(Material.DIAMOND_BLOCK));
                ingredients.add(new ItemStack(Material.NETHER_STAR));
                ingredients.add(LiminalCoreItem.createCore(plugin));
                themeColor = Color.fromRGB(255, 215, 0); // Gold
                accentColor = Color.fromRGB(255, 255, 255); // White
            }
            case INIQUITY -> {
                ingredients.add(new ItemStack(Material.WITHER_SKELETON_SKULL));
                ingredients.add(new ItemStack(Material.NETHERITE_INGOT));
                ingredients.add(new ItemStack(Material.CRYING_OBSIDIAN));
                ingredients.add(new ItemStack(Material.SOUL_SAND));
                ingredients.add(LiminalCoreItem.createCore(plugin));
                themeColor = Color.fromRGB(180, 0, 0); // Crimson
                accentColor = Color.fromRGB(30, 30, 30); // Dark
            }
            default -> { // OBSCURA
                ingredients.add(new ItemStack(Material.ECHO_SHARD));
                ingredients.add(new ItemStack(Material.END_CRYSTAL));
                ingredients.add(new ItemStack(Material.OBSIDIAN));
                ingredients.add(new ItemStack(Material.DRAGON_BREATH));
                ingredients.add(LiminalCoreItem.createCore(plugin));
                themeColor = Color.fromRGB(0, 200, 255); // Cyan
                accentColor = Color.fromRGB(147, 50, 200); // Purple
            }
        }

        // Spawn orbiting ingredient items
        List<Item> orbitingItems = new ArrayList<>();
        for (ItemStack ing : ingredients) {
            Item it = player.getWorld().dropItem(player.getLocation().add(0, 1.0, 0), ing);
            it.setGravity(false);
            it.setPickupDelay(Integer.MAX_VALUE);
            it.setCanMobPickup(false);
            it.setVelocity(new Vector(0, 0, 0));
            orbitingItems.add(it);
        }

        // Spawn central scaling Relic (using ItemDisplay with fallback)
        ItemStack relicStack = RelicItem.createRelic(plugin, relic);
        Entity scalingEntity = null;
        try {
            Location relicSpawn = player.getEyeLocation().add(0, 1.8, 0);
            ItemDisplay display = player.getWorld().spawn(relicSpawn, ItemDisplay.class, d -> {
                d.setItemStack(relicStack);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(themeColor);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        new Quaternionf()
                ));
            });
            scalingEntity = display;
        } catch (Throwable ignored) {
            // Fallback for non-display entity environments
            Item fallbackItem = player.getWorld().dropItem(player.getEyeLocation().add(0, 1.8, 0), relicStack);
            fallbackItem.setGravity(false);
            fallbackItem.setPickupDelay(Integer.MAX_VALUE);
            fallbackItem.setCanMobPickup(false);
            fallbackItem.setVelocity(new Vector(0, 0, 0));
            scalingEntity = fallbackItem;
        }

        final Entity centralRelic = scalingEntity;
        Particle.DustOptions dustTheme = new Particle.DustOptions(themeColor, 1.5f);
        Particle.DustOptions dustAccent = new Particle.DustOptions(accentColor, 1.3f);

        new BukkitRunnable() {
            int t = 0;
            boolean finished = false;
            double rotation = 0;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                t++;

                // Stop condition or player logout
                if (t > 60 || !player.isOnline()) {
                    finished = true;
                    cancel();
                    try {
                        cleanupAndDeliver(player, relic, orbitingItems, centralRelic);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("Error in craft cleanup: " + ex.getMessage());
                    }
                    return;
                }

                rotation += 0.25;
                Location pChest = player.getLocation().add(0, 1.3, 0);
                Location pRelicLoc = player.getEyeLocation().add(0, 1.8, 0);

                // Keep central relic hovering above player's head and scale bigger!
                if (centralRelic != null && centralRelic.isValid()) {
                    centralRelic.teleport(pRelicLoc);
                    if (centralRelic instanceof ItemDisplay itemDisplay) {
                        float scale = 1.0f + (t * 0.035f); // Scale grows up to 3.1x!
                        try {
                            itemDisplay.setTransformation(new Transformation(
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf().rotateY((float) rotation),
                                    new Vector3f(scale, scale, scale),
                                    new Quaternionf()
                            ));
                        } catch (Throwable ignored) {}
                    }
                }

                // 1. Move and link all orbiting ingredient items to the player!
                double orbitRadius = 2.4 - (t * 0.015);
                for (int i = 0; i < orbitingItems.size(); i++) {
                    Item it = orbitingItems.get(i);
                    if (!it.isValid()) continue;

                    double angle = rotation + (i * (2 * Math.PI / orbitingItems.size()));
                    double yOffset = Math.sin(t * 0.15 + i) * 0.4;
                    Location itemLoc = pChest.clone().add(Math.cos(angle) * orbitRadius, yOffset, Math.sin(angle) * orbitRadius);
                    it.teleport(itemLoc);

                    // Dense particle energy beam linking the item to player's chest!
                    Vector toPlayer = pChest.toVector().subtract(itemLoc.toVector());
                    int beamPoints = 8;
                    for (int step = 1; step <= beamPoints; step++) {
                        Location beamPoint = itemLoc.clone().add(toPlayer.clone().multiply((double) step / beamPoints));
                        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, beamPoint, 1, 0, 0, 0, 0);
                        if (step % 2 == 0) {
                            player.getWorld().spawnParticle(Particle.DUST, beamPoint, 1, 0, 0, 0, 0, dustTheme);
                        }
                    }
                }

                // 2. Halo particles around the scaling relic
                player.getWorld().spawnParticle(Particle.END_ROD, pRelicLoc, 2, 0.2, 0.2, 0.2, 0.04);
                player.getWorld().spawnParticle(Particle.DUST, pRelicLoc, 4, 0.3, 0.3, 0.3, 0, dustAccent);

                // 3. Dual-helix vortex spiraling up around the player
                for (int h = 0; h < 2; h++) {
                    double helixAngle = (rotation * 1.5) + (h * Math.PI) + (t * 0.1);
                    double helixY = ((t * 0.08) % 3.0);
                    Location hLoc = player.getLocation().add(Math.cos(helixAngle) * 1.2, helixY, Math.sin(helixAngle) * 1.2);
                    player.getWorld().spawnParticle(Particle.DUST, hLoc, 1, 0, 0, 0, 0, (h == 0) ? dustTheme : dustAccent);
                }

                // 4. Harmonic cosmic pulses during ascension (clean chimes, NO spammy sonic booms)
                if (t == 15 || t == 30 || t == 45) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f + (t * 0.01f));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
                    player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.5, 0), 1, Color.WHITE);
                }

                // Ambient rising chime
                if (t % 8 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.2f + (t * 0.01f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupAndDeliver(Player player, RelicItem.RelicType relic, List<Item> orbitingItems, Entity centralRelic) {
        // Remove temporary visual entities safely
        try {
            if (centralRelic != null && centralRelic.isValid()) centralRelic.remove();
            for (Item it : orbitingItems) {
                if (it.isValid()) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {}

        if (!player.isOnline()) return;

        // Single clean climax completion burst
        Location pCenter = player.getLocation().add(0, 1.5, 0);
        player.getWorld().strikeLightningEffect(pCenter);
        player.getWorld().spawnParticle(Particle.FLASH, pCenter, 3, 0.2, 0.2, 0.2, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pCenter, 40, 0.6, 0.6, 0.6, 0.2);
        player.playSound(pCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);

        // Slow falling and safe landing
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
        player.setFallDistance(0);

        // Deliver the relic item safely (with overflow drop handling)
        ItemStack finalRelic = RelicItem.createRelic(plugin, relic);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(finalRelic);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}
