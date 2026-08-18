package com.churchsmp.sermon;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sermon start [durationSeconds] [radius]
 * /sermon cancel
 */
public class SermonCommand implements CommandExecutor {

    private final ChurchSMP plugin;

    public SermonCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player preacher)) {
            sender.sendMessage(Component.text("Only players can hold a sermon.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /sermon <start|cancel>", NamedTextColor.RED));
            return true;
        }

        SermonManager sermons = plugin.getSermonManager();

        if (args[0].equalsIgnoreCase("cancel")) {
            sermons.cancelEarly();
            sender.sendMessage(Component.text("Sermon cancelled.", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (sermons.isActive()) {
                sender.sendMessage(Component.text("A sermon is already in progress.", NamedTextColor.RED));
                return true;
            }
            int duration = args.length > 1 ? parseIntSafe(args[1], 60) : 60;
            double radius = args.length > 2 ? parseDoubleSafe(args[2], 20) : 20;
            sermons.start(preacher, duration, radius);
            return true;
        }

        sender.sendMessage(Component.text("Usage: /sermon <start|cancel>", NamedTextColor.RED));
        return true;
    }

    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private double parseDoubleSafe(String s, double fallback) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return fallback; }
    }
}
