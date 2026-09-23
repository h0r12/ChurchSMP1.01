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

        // Check if Relic item (Iniquity, Impiety, Obscura) is right-clicked (single use, consumed)
        com.churchsmp.item.RelicItem.RelicType relic = com.churchsmp.item.RelicItem.getRelicType(mainHand, plugin);
        if (relic != null) {
            event.setCancelled(true);
            mainHand.subtract(1);

            plugin.getAlignmentManager().setAlignmentScore(player, relic.getTargetScore());

            if (relic == com.churchsmp.item.RelicItem.RelicType.IMPIETY) {
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.8f);
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.2);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gradient:#FFFFFF:#FFD700><bold>✦ ᴜꜱᴇᴅ [ɪᴍᴘɪᴇᴛʏ]! ʏᴏᴜʀ ᴀʟɪɢɴᴍᴇɴᴛ ɪꜱ ɴᴏᴡ ɢᴏᴏᴅ (+100). ɢᴏᴏᴅ ᴡᴇᴀᴘᴏɴꜱ ᴜɴʟᴏᴄᴋᴇᴅ.</bold></gradient>"));
            } else if (relic == com.churchsmp.item.RelicItem.RelicType.INIQUITY) {
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
                player.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.1);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gradient:#FF0000:#8B0000><bold>✦ ᴜꜱᴇᴅ [ɪɴɪǫᴜɪᴛʏ]! ʏᴏᴜʀ ᴀʟɪɢɴᴍᴇɴᴛ ɪꜱ ɴᴏᴡ ᴇᴠɪʟ (-100). ᴇᴠɪʟ ᴡᴇᴀᴘᴏɴꜱ ᴜɴʟᴏᴄᴋᴇᴅ.</bold></gradient>"));
            } else {
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.5f);
                player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.5);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gradient:#4B0082:#00CED1><bold>✦ ᴜꜱᴇᴅ [ᴏʙꜱᴄᴜʀᴀ]! ʏᴏᴜʀ ᴀʟɪɢɴᴍᴇɴᴛ ɪꜱ ɴᴏᴡ ɴᴜʟʟɪғɪᴇᴅ (0). ɴᴜʟʟɪғɪᴇᴅ ᴡᴇᴀᴘᴏɴꜱ ᴜɴʟᴏᴄᴋᴇᴅ.</bold></gradient>"));
            }
            return;
        }

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

        // Grim Scythe right-click throw (mouse button activation for regular abilities has been removed)
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon instanceof com.churchsmp.weapon.Grim grim) {
            event.setCancelled(true);
            grim.throwScythe(player);
        }
    }

    private void triggerWeaponAbility(Player player, LegendaryWeapon weapon, boolean secondary) {
        if (!plugin.getAlignmentManager().canWield(player, weapon.getRequiredAlignment())) {
            player.sendMessage(Component.text("✦ Your soul's alignment prevents you from channeling " + weapon.getId() + "!", NamedTextColor.RED));
            return;
        }

        String abilityName = secondary ? weapon.getSecondaryAbilityName() : weapon.getPrimaryAbilityName();
        String cdKey = weapon.getId() + (secondary ? "_secondary" : "_primary");

        boolean success = secondary ? weapon.executeSecondary(player) : weapon.executePrimary(player);
        if (success) {
            int cd = (int) Math.ceil(plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey));
            if (cd > 0) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(com.churchsmp.util.TextUtil.getAbilityUsedMessage(weapon, abilityName, cd)));
            }
        }
    }
}
