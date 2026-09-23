package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ChurchAdminCommand implements CommandExecutor, TabCompleter {

    private final ChurchSMP plugin;

    public ChurchAdminCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("churchsmp.admin")) {
            sender.sendMessage(Component.text("You lack permission to use /churchadmin. (Run '/op <your_name>' in the server console)", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "resetcooldown" -> {
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                plugin.getCooldownManager().resetAllCooldowns(target);
                sender.sendMessage(Component.text("All cooldowns reset for " + target.getName() + "!", NamedTextColor.GREEN));
                target.sendMessage(Component.text("âœ¦ Your cooldowns have been reset by an administrator.", NamedTextColor.YELLOW));
            }
            case "setalignment" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /churchadmin setalignment <player> <GOOD|EVIL|NULLIFIED>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                try {
                    Alignment alignment = Alignment.valueOf(args[2].toUpperCase(Locale.ROOT));
                    plugin.getAlignmentManager().setAlignment(target, alignment);
                    sender.sendMessage(Component.text("Updated alignment of " + target.getName() + " to " + alignment.name(), NamedTextColor.GREEN));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid alignment! Must be GOOD, EVIL, or NULLIFIED.", NamedTextColor.RED));
                }
            }
            case "giveweapon" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /churchadmin giveweapon <player> <weapon_id> [extra_arg]", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(args[2]);
                if (weapon == null) {
                    sender.sendMessage(Component.text("Unknown weapon ID: " + args[2], NamedTextColor.RED));
                    return true;
                }

                ItemStack item;
                if (weapon instanceof com.churchsmp.weapon.Grim grim && args.length >= 4) {
                    int kills = 0;
                    try { kills = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                    item = grim.createItemWithKills(kills);
                } else if (weapon instanceof com.churchsmp.weapon.VoidBreaker vb && args.length >= 4) {
                    boolean useDensity = !args[3].equalsIgnoreCase("breach");
                    item = vb.createItemWithEnchant(useDensity);
                } else {
                    item = weapon.createItem();
                }

                target.getInventory().addItem(item);
                sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN).append(weapon.getDisplayName()).append(Component.text(" to " + target.getName())));
            }
            case "givegem" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /churchadmin givegem <player> <gem_name>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                try {
                    SinGemType gem = SinGemType.valueOf(args[2].toUpperCase(Locale.ROOT));
                    target.getInventory().addItem(plugin.getSinGemManager().createGemItem(gem));
                    sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN).append(gem.getFormattedName()).append(Component.text(" to " + target.getName())));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid gem! Choose from: Wrath, Greed, Gluttony, Lust, Envy, Pride, Sloth", NamedTextColor.RED));
                }
            }
            case "menu" -> {
                if (sender instanceof Player p) {
                    plugin.getWeaponMenuManager().openMenu(p);
                } else {
                    sender.sendMessage(Component.text("Only players can open the weapon menu.", NamedTextColor.RED));
                }
            }
            case "giveimpiety" -> {
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(com.churchsmp.item.ImpietyItem.createImpietyItem(plugin));
                sender.sendMessage(Component.text("Gave Impiety weapon selector to " + target.getName() + "!", NamedTextColor.GREEN));
                target.sendMessage(Component.text("✦ You received the Impiety weapon selector. Right-click to open!", NamedTextColor.GOLD));
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("ChurchSMP config reloaded successfully!", NamedTextColor.GREEN));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        sender.sendMessage(mm.deserialize(com.churchsmp.util.TextUtil.formatCommandHeader("CHURCHADMIN COMMANDS")));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("menu") + "</bold></gold> <white>-</white> <gray>Opens the interactive Legendary Weapon menu</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("giveimpiety") + " [player]</bold></gold> <white>-</white> <gray>Gives the Impiety right-click menu item</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("resetcooldown") + " [player]</bold></gold> <white>-</white> <gray>Resets all ability cooldowns</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("setalignment") + " <player> <alignment></bold></gold> <white>-</white> <gray>Sets player's alignment (GOOD, EVIL, NULLIFIED)</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("giveweapon") + " <player> <weapon_id></bold></gold> <white>-</white> <gray>Spawns a legendary weapon</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("givegem") + " <player> <gem_name></bold></gold> <white>-</white> <gray>Spawns a Sin Relic gem</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("reload") + "</bold></gold> <white>-</white> <gray>Reloads configuration file</gray>"));
        sender.sendMessage(mm.deserialize("<gold>══════════════════════════════════════════</gold>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("menu", "giveimpiety", "resetcooldown", "setalignment", "giveweapon", "givegem", "reload", "help"), args[0]);
        }
        if (args.length == 2) {
            return null; // suggest player names
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setalignment")) {
                return filter(Arrays.stream(Alignment.values()).map(Enum::name).toList(), args[2]);
            }
            if (args[0].equalsIgnoreCase("giveweapon")) {
                return filter(plugin.getWeaponManager().getAllWeapons().stream().map(LegendaryWeapon::getId).toList(), args[2]);
            }
            if (args[0].equalsIgnoreCase("givegem")) {
                return filter(Arrays.stream(SinGemType.values()).map(Enum::name).toList(), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
