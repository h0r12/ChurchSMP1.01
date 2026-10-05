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
import org.bukkit.Sound;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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
        if (plugin.getGemAbilityExecutor().isSealed(player)) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>✦ Your offhand is sealed by Taxing Ray! ✦</red>"));
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if holding a legendary weapon
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            event.setCancelled(true);
            triggerWeaponAbility(player, weapon, player.isSneaking());
            return;
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
        if (plugin.getGemAbilityExecutor().isSealed(player)) {
            event.setCancelled(true);
            return;
        }

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
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 1. Check if Relic item (Iniquity, Impiety, Obscura) is right-clicked to choose path weapon
        com.churchsmp.item.RelicItem.RelicType relic = com.churchsmp.item.RelicItem.getRelicType(mainHand, plugin);
        if (relic != null) {
            event.setCancelled(true);
            plugin.getWeaponChoiceManager().openChoiceMenu(player, relic);
            return;
        }

        // 2. Check Legendary Weapon right-click abilities on mainHand first
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon instanceof com.churchsmp.weapon.Grim grim && !player.isSneaking()) {
            event.setCancelled(true);
            grim.throwScythe(player);
            return;
        }

        if (weapon instanceof com.churchsmp.weapon.Sorrowess sorrowess) {
            if (player.isSneaking()) {
                // Sneak + Right Click triggers Sorrowess secondary (Bloody Rain Clones)!
                event.setCancelled(true);
                triggerWeaponAbility(player, weapon, true);
                return;
            }
            // Block charging if Riptide is currently on cooldown
            String cdKey = sorrowess.getId() + "_riptide";
            if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
                event.setCancelled(true);
                int rem = (int) Math.ceil(plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey));
                player.sendActionBar(miniMessage.deserialize("<red>✦ Sorrowess Riptide on Cooldown: " + rem + "s ✦</red>"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
                return;
            }
            if (offHand.getType() == Material.SHIELD) {
                // Cancel main-hand interaction so the offhand shield can block
                event.setCancelled(true);
            }
            // If no shield and not on cooldown, allow vanilla Trident charge -> ProjectileLaunchEvent -> Riptide dash
            return;
        }

        // VoidBreaker: RMB detonates armed shockwave (crumble 5/5)
        if (weapon instanceof com.churchsmp.weapon.VoidBreaker vb) {
            if (vb.hasShockwaveArmed(player)) {
                event.setCancelled(true);
                vb.detonateShockwave(player);
                return;
            }
            // VoidBreaker has no other right-click action
            return;
        }

        // 3. Check Sin Gem: Priority to mainHand; offHand gem only if mainHand is empty/non-usable or player sneaking
        SinGemType mainHandGem = plugin.getSinGemManager().getGemType(mainHand);
        SinGemType offHandGem = plugin.getSinGemManager().getGemType(offHand);

        SinGemType gemToActivate = null;
        if (mainHandGem != null) {
            gemToActivate = mainHandGem;
        } else if (offHandGem != null) {
            // Gem is in offhand: if mainhand has a usable item (food, shield, pearl, etc.), allow mainhand item!
            if (!player.isSneaking() && isMainHandUsable(mainHand)) {
                return;
            }
            gemToActivate = offHandGem;
        }

        if (gemToActivate != null) {
            boolean sneak = player.isSneaking();
            // If the gem ability is on cooldown: show actionbar timer, but do NOT cancel event!
            if (plugin.getGemAbilityExecutor().isGemAbilityOnCooldown(player, sneak)) {
                double rem = plugin.getGemAbilityExecutor().getGemAbilityRemainingCooldown(player, sneak);
                player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<red>✦ " + com.churchsmp.util.TextUtil.toSmallCaps("Cooldown") + ": " + String.format(java.util.Locale.US, "%.1f", rem) + "s ✦</red>"));
                return;
            }

            // Auto-attune if not yet attuned to this gem
            if (!plugin.getSinGemManager().isAttuned(player)) {
                plugin.getSinGemManager().attune(player, gemToActivate);
            }

            event.setCancelled(true);
            if (sneak) {
                plugin.getGemAbilityExecutor().handleSneakRMB(player);
            } else {
                plugin.getGemAbilityExecutor().handleRMB(player);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Trident trident) {
            if (trident.getShooter() instanceof Player player) {
                ItemStack item = trident.getItem();
                LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);
                if (weapon instanceof com.churchsmp.weapon.Sorrowess sorrowess) {
                    event.setCancelled(true);
                    String cdKey = sorrowess.getId() + "_riptide";
                    if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
                        int rem = (int) Math.ceil(plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey));
                        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<red>✦ Sorrowess Riptide on Cooldown: " + rem + "s ✦</red>"));
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
                        return;
                    }
                    // Instead of throwing, trigger the custom Riptide dash!
                    sorrowess.executeRiptide(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRiptide(org.bukkit.event.player.PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);
        if (weapon instanceof com.churchsmp.weapon.Sorrowess sorrowess) {
            String cdKey = sorrowess.getId() + "_riptide";
            if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
                // Arrest velocity if attempting vanilla riptide while on cooldown
                player.setVelocity(new Vector(0, -0.1, 0));
                int rem = (int) Math.ceil(plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey));
                player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("<red>✦ Sorrowess Riptide on Cooldown: " + rem + "s ✦</red>"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
                return;
            }
            sorrowess.executeRiptide(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            if (plugin.getGemAbilityExecutor().handleSneakLMB(player)) {
                event.setCancelled(true);
            }
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

    private boolean isMainHandUsable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material mat = item.getType();
        if (mat.isEdible()) return true;
        return switch (mat) {
            case SHIELD, BOW, CROSSBOW, ENDER_PEARL, CHORUS_FRUIT, POTION,
                 SPLASH_POTION, LINGERING_POTION, MILK_BUCKET, HONEY_BOTTLE,
                 WIND_CHARGE, TRIDENT, FIREWORK_ROCKET, FISHING_ROD -> true;
            default -> false;
        };
    }
}
