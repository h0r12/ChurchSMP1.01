package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChurchCommand implements CommandExecutor, TabCompleter {

    private final ChurchSMP plugin;

    public ChurchCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view the ChurchSMP guide.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reroll")) {
            plugin.getSinGemManager().rerollGemInHand(player);
            return true;
        }

        // Display Guide / Profile
        Alignment alignment = plugin.getAlignmentManager().getAlignment(player);
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);

        player.sendMessage(Component.text("================[ ChurchSMP Guide ]================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("✦ Your Alignment: ", NamedTextColor.GRAY).append(alignment.getFormattedComponent()));
        player.sendMessage(Component.text("  " + alignment.getDescription(), NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        if (gem != null) {
            player.sendMessage(Component.text("✦ Attuned Sin Gem: ", NamedTextColor.GRAY).append(gem.getFormattedName()));
            player.sendMessage(Component.text("  " + gem.getDescription(), NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(Component.text("✦ Attuned Sin Gem: ", NamedTextColor.GRAY).append(Component.text("None (Hold gem and Right-Click to attune)", NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("  Tip: Use ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("/church reroll", NamedTextColor.AQUA))
                    .append(Component.text(" before attuning if you want a different gem.", NamedTextColor.DARK_GRAY)));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ Legendary Weapons:", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        for (LegendaryWeapon w : plugin.getWeaponManager().getAllWeapons()) {
            player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(w.getDisplayName())
                    .append(Component.text(" (" + w.getRequiredAlignment().getDisplayName() + ")", NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("=================================================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            if ("reroll".startsWith(args[0].toLowerCase())) sub.add("reroll");
            if ("guide".startsWith(args[0].toLowerCase())) sub.add("guide");
            return sub;
        }
        return List.of();
    }
}
