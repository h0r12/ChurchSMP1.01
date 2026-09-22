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
import org.bukkit.inventory.ItemStack;
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
                player.sendMessage(Component.text("✦ Hold a legendary weapon to see its guide!", NamedTextColor.YELLOW));
                showGeneralGuide(player);
            }
            return true;
        }

        // Default: show general guide
        showGeneralGuide(player);
        return true;
    }

    private void showWeaponGuide(Player player, LegendaryWeapon weapon) {
        player.sendMessage(Component.text("================[ Weapon Guide ]================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("  ").append(weapon.getDisplayName()));
        player.sendMessage(Component.text("  Alignment: ", NamedTextColor.GRAY).append(weapon.getRequiredAlignment().getFormattedComponent()));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  [F] Primary: ", NamedTextColor.GOLD)
                .append(Component.text(weapon.getPrimaryAbilityName(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  [Shift+F] Secondary: ", NamedTextColor.GOLD)
                .append(Component.text(weapon.getSecondaryAbilityName(), NamedTextColor.YELLOW)));

        // Weapon-specific tips
        switch (weapon.getId()) {
            case "excalibur" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Hopeful — Every hit causes Glowing on the target", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • Wings — Complete fall damage immunity", NamedTextColor.WHITE));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Acceleration Nova:", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    10s charge — fires a celestial beam projectile.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("  Altar Pining:", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Pulls all nearby enemies inward, then a celestial sword", NamedTextColor.GRAY));
                player.sendMessage(Component.text("    slams down dealing 2.5 hearts + 3s stun.", NamedTextColor.GRAY));
            }
            case "sorrowess" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Forming — Crouch to summon water (reverting in 3s)", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • Brave — +2 hearts while Sorrowess is in inventory", NamedTextColor.WHITE));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Grief Shards:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Fires 5 spirit shards at a target for 2.5 hearts total.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("  Bloody Rain:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Summons 4 illusion Phantom clones walking 4 directions.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("    Clones multiply into 4 when attacked (max 16).", NamedTextColor.GRAY));
            }
            case "voidbreaker" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Voidfeels — Double jump in air (5s CD)", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • Rifted — Sneak + double jump launches at crosshair", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • Bound — Mace hits gain +1 dash charge", NamedTextColor.WHITE));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Fractured:", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Arms next hit with Fallen debuff + 2s stun.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("  Crumble:", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Hit 3 times, 4th hit triggers devastating aftershock.", NamedTextColor.GRAY));
            }
            case "luminescence_spear" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Bolt — Fall damage triggers electric shockwave", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • LightStealing — Trident throw gives Darkness (60s CD)", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • BurningBones — Faster attack speed", NamedTextColor.WHITE));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Dash:", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Dash 6 blocks forward, deal 2 hearts on impact,", NamedTextColor.GRAY));
                player.sendMessage(Component.text("    marks hit targets with CrescentEclipse. (12s CD)", NamedTextColor.GRAY));
                player.sendMessage(Component.text("  SawRay:", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("    Activate mode, melee-hit a marked target 3 times,", NamedTextColor.GRAY));
                player.sendMessage(Component.text("    charges Antimatter Saw. Press F again to fire (2 hearts).", NamedTextColor.GRAY));
            }
            case "mayim" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Honor — Equipping Mayim stows offhand temporarily", NamedTextColor.WHITE));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Primary ability: [F]", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  Secondary ability: [Shift+F]", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            }
            case "judas" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Bloodlust — Cannot regenerate health while holding Judas", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • Discipline — Judas passives apply to ALL weapon attacks", NamedTextColor.WHITE));
            }
            case "grim" -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("  • Without Grim in inventory: max health = 10 hearts", NamedTextColor.WHITE));
                player.sendMessage(Component.text("  • With Grim: max health = 20 hearts", NamedTextColor.WHITE));
            }
        }
        player.sendMessage(Component.text("================================================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
    }

    private void showGeneralGuide(Player player) {
        Alignment alignment = plugin.getAlignmentManager().getAlignment(player);
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);

        player.sendMessage(Component.text("================[ ChurchSMP Guide ]================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("  Tip: Use /church guide while holding a weapon for details!", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ Your Alignment: ", NamedTextColor.GRAY).append(alignment.getFormattedComponent()));
        player.sendMessage(Component.text("  " + alignment.getDescription(), NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        if (gem != null) {
            player.sendMessage(Component.text("✦ Attuned Sin Gem: ", NamedTextColor.GRAY).append(gem.getFormattedName()));
            player.sendMessage(Component.text("  " + gem.getDescription(), NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(Component.text("✦ Attuned Sin Gem: ", NamedTextColor.GRAY)
                    .append(Component.text("None (Hold gem and Right-Click to attune)", NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("  Tip: Use ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("/church reroll", NamedTextColor.AQUA))
                    .append(Component.text(" to reroll your gem before attuning.", NamedTextColor.DARK_GRAY)));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ Legendary Weapons:", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        for (LegendaryWeapon w : plugin.getWeaponManager().getAllWeapons()) {
            player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(w.getDisplayName())
                    .append(Component.text(" — [F] " + w.getPrimaryAbilityName() + " | [Shift+F] " + w.getSecondaryAbilityName(), NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("  Hold a weapon and use /church guide for details.", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("==================================================", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
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
