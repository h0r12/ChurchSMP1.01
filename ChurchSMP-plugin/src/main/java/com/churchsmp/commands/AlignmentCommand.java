package com.churchsmp.commands;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AlignmentCommand implements CommandExecutor {

    private final ChurchSMP plugin;
    private final AlignmentManager alignment;

    public AlignmentCommand(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignment = plugin.getAlignmentManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return true;
        }

        double score = alignment.getScore(target);
        AlignmentTier tier = alignment.getTier(target);

        sender.sendMessage(Component.text(target.getName() + "'s Alignment", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Score: ", NamedTextColor.GRAY)
                .append(Component.text((int) score + " / 100", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Tier: ", NamedTextColor.GRAY)
                .append(Component.text(tier.getLabel(), tier.getColor())));
        return true;
    }
}
