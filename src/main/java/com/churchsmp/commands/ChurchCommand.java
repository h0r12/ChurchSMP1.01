package com.churchsmp.commands;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapons.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /church guide — tells you what the legendary weapon in your hand actually
 * does, since the item's own tooltip only has room for the short version.
 */
public class ChurchCommand implements CommandExecutor {

    private final ChurchSMP plugin;

    public ChurchCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("guide")) {
            sender.sendMessage(Component.text("Usage: /church guide", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use the guide.", NamedTextColor.RED));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        WeaponType type = plugin.getWeaponManager().getWeaponType(held);
        if (type == null) {
            player.sendMessage(Component.text("Hold a legendary weapon and try again.", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("===== " + type.getDisplayName() + " =====", NamedTextColor.GOLD));
        player.sendMessage(Component.text(type.getSubtitle(), NamedTextColor.DARK_GRAY));

        if (type.getPassives().length > 0) {
            player.sendMessage(Component.text("Passives:", NamedTextColor.GREEN));
            for (String passive : type.getPassives()) {
                player.sendMessage(Component.text("  \u25B8 " + passive, NamedTextColor.GREEN));
            }
        }

        player.sendMessage(Component.text(type.getAbility1Desc(), NamedTextColor.AQUA));
        player.sendMessage(Component.text(type.getAbility2Desc(), NamedTextColor.LIGHT_PURPLE));

        boolean usable = type.isUsableBy(plugin.getAlignmentManager().getTier(player));
        player.sendMessage(Component.text("Requires: " + type.getCategory().name() + " alignment", NamedTextColor.DARK_GRAY)
                .append(Component.text(usable ? "  (you qualify)" : "  (you do not currently qualify)",
                        usable ? NamedTextColor.GREEN : NamedTextColor.RED)));

        return true;
    }
}
