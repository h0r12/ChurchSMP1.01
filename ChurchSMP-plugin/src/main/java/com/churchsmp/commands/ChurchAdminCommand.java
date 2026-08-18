package com.churchsmp.commands;

import com.churchsmp.ChurchSMP;
import com.churchsmp.shrine.ShrineType;
import com.churchsmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /churchadmin give <player> <weaponId>
 * /churchadmin set <player> <score>
 * /churchadmin region wand                    (gives the region-selection wand)
 * /churchadmin region create                  (saves a region from the wand's two corners)
 * /churchadmin shrine add <altar|offering|confession>   (registers the block you're looking at)
 */
public class ChurchAdminCommand implements CommandExecutor {

    private final ChurchSMP plugin;

    public ChurchAdminCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /churchadmin <give|set|region|shrine> ...", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "set" -> handleSet(sender, args);
            case "region" -> handleRegion(sender, args);
            case "shrine" -> handleShrine(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void handleRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can manage regions.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /churchadmin region <wand|create>", NamedTextColor.RED));
            return;
        }
        if (args[1].equalsIgnoreCase("wand")) {
            player.getInventory().addItem(plugin.getRegionWandListener().createWand());
            sender.sendMessage(Component.text(
                    "Region wand given. Left-click corner 1, right-click corner 2, then /churchadmin region create.",
                    NamedTextColor.GREEN));
            return;
        }
        if (args[1].equalsIgnoreCase("create")) {
            Location pos1 = plugin.getRegionWandListener().getPos1(player);
            Location pos2 = plugin.getRegionWandListener().getPos2(player);
            if (pos1 == null || pos2 == null) {
                sender.sendMessage(Component.text("Set both corners with the region wand first.", NamedTextColor.RED));
                return;
            }
            plugin.getChurchRegionManager().addRegion(pos1, pos2);
            sender.sendMessage(Component.text("Church region created.", NamedTextColor.GREEN));
            return;
        }
        sender.sendMessage(Component.text("Usage: /churchadmin region <wand|create>", NamedTextColor.RED));
    }

    private void handleShrine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can register shrines.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("add")) {
            sender.sendMessage(Component.text("Usage: /churchadmin shrine add <altar|offering|confession>", NamedTextColor.RED));
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            sender.sendMessage(Component.text("Look at a block within 6 blocks to register it.", NamedTextColor.RED));
            return;
        }
        ShrineType type;
        try {
            type = ShrineType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown shrine type. Use altar, offering, or confession.", NamedTextColor.RED));
            return;
        }
        plugin.getShrineManager().register(type, target.getLocation());
        sender.sendMessage(Component.text("Registered " + type.name().toLowerCase() + " at "
                + target.getX() + ", " + target.getY() + ", " + target.getZ(), NamedTextColor.GREEN));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /churchadmin give <player> <weaponId>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        WeaponType type = WeaponType.fromId(args[2].toLowerCase());
        if (type == null) {
            StringBuilder ids = new StringBuilder();
            for (WeaponType t : WeaponType.values()) ids.append(t.getId()).append(" ");
            sender.sendMessage(Component.text("Unknown weapon. Valid IDs: " + ids, NamedTextColor.RED));
            return;
        }
        target.getInventory().addItem(plugin.getWeaponManager().createWeapon(type));
        sender.sendMessage(Component.text("Gave " + type.getDisplayName() + " to " + target.getName(), NamedTextColor.GREEN));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /churchadmin set <player> <score>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        try {
            double score = Double.parseDouble(args[2]);
            double current = plugin.getAlignmentManager().getScore(target);
            plugin.getAlignmentManager().applyDeed(target, score - current);
            sender.sendMessage(Component.text("Set " + target.getName() + "'s alignment to " + (int) score, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Score must be a number.", NamedTextColor.RED));
        }
    }
}
