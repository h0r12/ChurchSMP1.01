package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChurchCommand implements CommandExecutor, TabCompleter {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChurchCommand(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use ChurchSMP commands.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reroll")) {
            plugin.getSinGemManager().rerollGemInHand(player);
            return true;
        }

        // /church guide — show weapon-specific info if holding a legendary
        if (args.length > 0 && args[0].equalsIgnoreCase("guide")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            LegendaryWeapon heldWeapon = plugin.getWeaponManager().getWeapon(held);

            if (heldWeapon != null) {
                showWeaponGuide(player, heldWeapon);
            } else {
                player.sendMessage(miniMessage.deserialize("<gold>✦ <white><bold>" + TextUtil.toSmallCaps("Hold a legendary weapon to see its guide!") + "</bold></white></gold>"));
                showGeneralGuide(player);
            }
            return true;
        }

        // Default: show general guide
        showGeneralGuide(player);
        return true;
    }

    private void showWeaponGuide(Player player, LegendaryWeapon weapon) {
        player.sendMessage(miniMessage.deserialize(TextUtil.formatCommandHeader("WEAPON GUIDE")));
        player.sendMessage(Component.text("  ").append(weapon.getDisplayName()));
        player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ </bold><white>" + TextUtil.toSmallCaps("Alignment") + ": </white></gold>").append(weapon.getRequiredAlignment().getFormattedComponent()));
        player.sendMessage(Component.empty());

        player.sendMessage(miniMessage.deserialize("  <white><bold>[ғ] </bold></white><gold><bold>" + TextUtil.toSmallCaps("Primary") + ":</bold></gold> <yellow>" + weapon.getPrimaryAbilityName() + "</yellow>"));
        player.sendMessage(miniMessage.deserialize("  <white><bold>[ꜱʜɪғᴛ+ғ] </bold></white><gold><bold>" + TextUtil.toSmallCaps("Secondary") + ":</bold></gold> <yellow>" + weapon.getSecondaryAbilityName() + "</yellow>"));

        // Weapon-specific tips
        switch (weapon.getId()) {
            case "excalibur" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Hopeful") + "</bold></white> <gray>— Every hit causes Glowing on the target</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Wings") + "</bold></white> <gray>— Complete fall damage immunity</gray></gold>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Acceleration Nova") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>10s charge — fires a celestial beam projectile.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Altar Pining") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Pulls all nearby enemies inward, then a celestial sword</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>slams down dealing 2.5 hearts + 3s stun.</gray>"));
            }
            case "sorrowess" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Forming") + "</bold></white> <gray>— Crouch to summon water (reverting in 3s)</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Brave") + "</bold></white> <gray>— +2 hearts while Sorrowess is in inventory</gray></gold>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Grief Shards") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Fires 5 spirit shards at a target for 2.5 hearts total.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Bloody Rain") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Summons 4 illusion Phantom clones walking 4 directions.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Clones multiply into 4 when attacked (max 16).</gray>"));
            }
            case "voidbreaker" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Voidfeels") + "</bold></white> <gray>— Double jump in air (5s CD)</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Rifted") + "</bold></white> <gray>— Sneak + double jump launches at crosshair</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Bound") + "</bold></white> <gray>— Mace hits gain +1 dash charge</gray></gold>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Fractured") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Arms next hit with Fallen debuff + 2s stun.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Crumble") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Hit 3 times, 4th hit triggers devastating aftershock.</gray>"));
            }
            case "luminescence_spear" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Bolt") + "</bold></white> <gray>— Fall damage triggers electric shockwave (60s CD)</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("LightStealing") + "</bold></white> <gray>— Trident hit inflicts Darkness (60s CD)</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("BurningBones") + "</bold></white> <gray>— Faster attack speed of a sword</gray></gold>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Dash") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Dash 6 blocks forward, deal 2 hearts on impact,</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>marks hit targets with CrescentEclipse. (12s CD)</gray>"));
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("SawRay") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Activate mode, melee-hit a marked target 3 times,</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>charges Antimatter Saw. Press F again to fire (2 hearts).</gray>"));
            }
            case "mayim" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Honor") + "</bold></white> <gray>— Equipping Mayim stows offhand temporarily</gray></gold>"));
            }
            case "judas" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Bloodlust") + "</bold></white> <gray>— Cannot regenerate health while holding Judas</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Bite") + "</bold></white> <gray>— 25% chance to inflict Wither & Nausea (30s CD)</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Discipline") + "</bold></white> <gray>— Judas passives apply to ALL weapon attacks</gray></gold>"));
            }
            case "grim" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Soultaking") + "</bold></white> <gray>— Harvests souls on kills, 10s cooldown</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Reaper") + "</bold></white> <gray>— Permanent max hearts with weapon</gray></gold>"));
                player.sendMessage(miniMessage.deserialize("  <gold>• <white><bold>" + TextUtil.toSmallCaps("Health Penalty") + "</bold></white> <gray>— Max health capped to 10 hearts when Grim not in inventory</gray></gold>"));
            }
        }
        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════</gold>"));
    }

    private void showGeneralGuide(Player player) {
        Alignment alignment = plugin.getAlignmentManager().getAlignment(player);
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);

        player.sendMessage(miniMessage.deserialize(TextUtil.formatCommandHeader("CHURCHSMP GUIDE")));
        player.sendMessage(miniMessage.deserialize("  <gray>" + TextUtil.toSmallCaps("Tip") + ": <yellow>/church guide " + TextUtil.toSmallCaps("while holding a weapon for full ability details!") + "</yellow></gray>"));
        player.sendMessage(Component.empty());

        player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ </bold><white><bold>" + TextUtil.toSmallCaps("Your Alignment") + ": </bold></white></gold>").append(alignment.getFormattedComponent()));
        player.sendMessage(Component.text("    " + alignment.getDescription(), NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        if (gem != null) {
            player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ </bold><white><bold>" + TextUtil.toSmallCaps("Attuned Sin Gem") + ": </bold></white></gold>").append(gem.getFormattedName()));
            player.sendMessage(Component.text("    " + gem.getDescription(), NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ </bold><white><bold>" + TextUtil.toSmallCaps("Attuned Sin Gem") + ": </bold></white><yellow>" + TextUtil.toSmallCaps("None (Right-Click gem to attune)") + "</yellow></gold>"));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ </bold><white><bold>" + TextUtil.toSmallCaps("Legendary Weapons") + ":</bold></white></gold>"));
        for (LegendaryWeapon w : plugin.getWeaponManager().getAllWeapons()) {
            player.sendMessage(Component.text("   • ", NamedTextColor.DARK_GRAY)
                    .append(w.getDisplayName())
                    .append(miniMessage.deserialize(" <gray>— [ғ] <white>" + TextUtil.toSmallCaps(w.getPrimaryAbilityName()) + "</white> | [ꜱʜɪғᴛ+ғ] <white>" + TextUtil.toSmallCaps(w.getSecondaryAbilityName()) + "</white></gray>")));
        }
        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════</gold>"));
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
