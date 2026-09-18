package com.churchsmp.relics;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Greed's Passive 1, Preloaded: /ritual consumes an item from the player's
 * main hand and stages it for their next Taken (ability 2) activation.
 * Staged this way (on the player, via command) rather than as a literal
 * physical "Relic slot" item, since requiring an actual Netherite Sword or
 * a full ore stack to permanently sit in the offhand slot would cost the
 * player real inventory utility (no shield, no off-hand torch, etc.) just
 * to have Taken armed — that seemed like an unintended tax on the passive.
 */
public class RitualCommand implements CommandExecutor {

    private final ChurchSMP plugin;

    public RitualCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can perform a ritual.", NamedTextColor.RED));
            return true;
        }
        if (plugin.getRelicManager().getAssignedRelic(player) != RelicType.GREED) {
            player.sendMessage(Component.text("Only those attuned to Greed can perform a ritual.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /ritual <ores|sword|head>", NamedTextColor.RED));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String choice = args[0].toLowerCase();

        switch (choice) {
            case "ores" -> {
                if (!held.getType().name().endsWith("_ORE") || held.getAmount() < 64) {
                    player.sendMessage(Component.text("Hold a stack of 64 ores in your main hand.", NamedTextColor.RED));
                    return true;
                }
                held.setAmount(held.getAmount() - 64);
                stage(player, "ORES", "64 Ores");
            }
            case "sword" -> {
                if (held.getType() != Material.NETHERITE_SWORD) {
                    player.sendMessage(Component.text("Hold a Netherite Sword in your main hand.", NamedTextColor.RED));
                    return true;
                }
                held.setAmount(held.getAmount() - 1);
                stage(player, "SWORD", "a Netherite Sword");
            }
            case "head" -> {
                if (held.getType() != Material.PLAYER_HEAD) {
                    player.sendMessage(Component.text("Hold a Player Head in your main hand.", NamedTextColor.RED));
                    return true;
                }
                held.setAmount(held.getAmount() - 1);
                stage(player, "HEAD", "a Player Head");
            }
            default -> player.sendMessage(Component.text("Usage: /ritual <ores|sword|head>", NamedTextColor.RED));
        }
        return true;
    }

    private void stage(Player player, String type, String description) {
        plugin.getRelicManager().setGreedPreload(player, type);
        player.sendMessage(Component.text("The ritual consumes " + description + " — Taken is now primed.", NamedTextColor.GOLD));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
    }
}
