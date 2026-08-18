package com.churchsmp.commands;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /donate <player> — gives the item currently held in the sender's main hand
 * to the target player and registers it as a good deed.
 */
public class DonateCommand implements CommandExecutor {

    private final ChurchSMP plugin;
    private final AlignmentManager alignment;

    public DonateCommand(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignment = plugin.getAlignmentManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can donate items.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /donate <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            sender.sendMessage(Component.text("Hold an item in your main hand to donate it.", NamedTextColor.RED));
            return true;
        }

        target.getInventory().addItem(held.clone());
        player.getInventory().setItemInMainHand(null);

        double points = plugin.getConfig().getDouble("good-deeds.donate-command", 3);
        alignment.applyDeed(player, points);

        player.sendMessage(Component.text("You donated to " + target.getName() + ". (+" + (int) points + " alignment)",
                NamedTextColor.GREEN));
        target.sendMessage(Component.text(player.getName() + " donated an item to you.", NamedTextColor.GREEN));
        return true;
    }
}
