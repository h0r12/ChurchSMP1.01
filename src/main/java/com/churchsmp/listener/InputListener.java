package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class InputListener implements Listener {

    private final ChurchSMP plugin;

    // Interactive blocks that right-clicks should not hijack
    private final Set<Material> interactiveBlocks = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST,
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER, Material.DISPENSER,
            Material.DROPPER, Material.BREWING_STAND, Material.CRAFTING_TABLE, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.ENCHANTING_TABLE,
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON
    );

    public InputListener(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Swap hands activation (F key on Java).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if holding a legendary weapon
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            event.setCancelled(true);
            triggerWeaponAbility(player, weapon, player.isSneaking());
            return;
        }

        // Check if holding a sin gem
        SinGemType gem = plugin.getSinGemManager().getGemType(mainHand);
        if (gem != null) {
            event.setCancelled(true);
            plugin.getGemAbilityExecutor().triggerGemAbility(player);
        }
    }

    /**
     * Right click activation (Bedrock friendly, scoped so it doesn't break vanilla interactions).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();

        // If clicking an interactive block (chest, door, etc.), don't intercept!
        if (clicked != null) {
            Material type = clicked.getType();
            if (interactiveBlocks.contains(type) ||
                    Tag.DOORS.isTagged(type) ||
                    Tag.TRAPDOORS.isTagged(type) ||
                    Tag.FENCE_GATES.isTagged(type) ||
                    Tag.BUTTONS.isTagged(type)) {
                return;
            }
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if it's an unattuned gem being right-clicked to attune
        SinGemType gemItem = plugin.getSinGemManager().getGemType(mainHand);
        if (gemItem != null) {
            if (!plugin.getSinGemManager().isAttuned(player)) {
                event.setCancelled(true);
                plugin.getSinGemManager().attune(player, gemItem);
                mainHand.subtract(1);
                return;
            } else {
                // Attuned player activating gem
                event.setCancelled(true);
                plugin.getGemAbilityExecutor().triggerGemAbility(player);
                return;
            }
        }

        // Legendary weapon right-click handling
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon == null) return;

        // Verify alignment first
        if (!plugin.getAlignmentManager().canWield(player, weapon.getRequiredAlignment())) {
            return;
        }

        boolean secondary = player.isSneaking();
        String abilityKey = weapon.getId() + (secondary ? "_secondary" : "_primary");

        // Only hijack if ability is NOT on cooldown!
        if (!plugin.getCooldownManager().isOnCooldown(player, abilityKey)) {
            event.setCancelled(true);
            triggerWeaponAbility(player, weapon, secondary);
        }
    }

    private void triggerWeaponAbility(Player player, LegendaryWeapon weapon, boolean secondary) {
        if (!plugin.getAlignmentManager().canWield(player, weapon.getRequiredAlignment())) {
            player.sendMessage(Component.text("âœ¦ Your soul's alignment prevents you from channeling " + weapon.getId() + "!", NamedTextColor.RED));
            return;
        }

        if (secondary) {
            weapon.executeSecondary(player);
        } else {
            weapon.executePrimary(player);
        }
    }
}
