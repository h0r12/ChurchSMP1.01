package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
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

        // /church ritual or /ritual command
        if (label.equalsIgnoreCase("ritual") || (args.length > 0 && args[0].equalsIgnoreCase("ritual"))) {
            ItemStack held = player.getInventory().getItemInMainHand();
            plugin.getGemAbilityExecutor().preloadRelic(player, held);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reroll")) {
            plugin.getSinGemManager().rerollGemInHand(player);
            return true;
        }

        // /church guide — shows detailed breakdown of player's attuned Sin Gem
        if (args.length > 0 && args[0].equalsIgnoreCase("guide")) {
            showPlayerGemGuide(player);
            return true;
        }

        // Default: show gem guide directly
        showPlayerGemGuide(player);
        return true;
    }

    private void showPlayerGemGuide(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) {
            player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
            player.sendMessage(miniMessage.deserialize("<gold>✦ <yellow><bold>" + TextUtil.toSmallCaps("Sin Gem Guide") + "</bold></yellow> ✦</gold>"));
            player.sendMessage(miniMessage.deserialize("<red>✦ " + TextUtil.toSmallCaps("You have not attuned to any Sin Gem yet!") + " ✦</red>"));
            player.sendMessage(miniMessage.deserialize("<gray>" + TextUtil.toSmallCaps("Complete the Forsaking Ritual or right-click an unattuned Sin Gem to attune.") + "</gray>"));
            player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
            return;
        }

        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
        player.sendMessage(miniMessage.deserialize("<gold>✦ <bold>" + TextUtil.toSmallCaps("Relic Gem") + ":</bold> </gold>").append(gem.getFormattedName()));
        player.sendMessage(miniMessage.deserialize("<gray><i>\"" + gem.getDescription() + "\"</i></gray>"));
        player.sendMessage(Component.empty());

        switch (gem) {
            case WRATH -> {
                player.sendMessage(miniMessage.deserialize("  <red><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></red>"));
                player.sendMessage(miniMessage.deserialize("  <dark_red>•</dark_red> <white><bold>" + TextUtil.toSmallCaps("Bloodfeast") + ":</bold></white> <gray>Deals </gray><yellow>+0.75 " + TextUtil.toSmallCaps("Attack Damage") + "</yellow> <gray>per missing heart.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <dark_red>•</dark_red> <white><bold>" + TextUtil.toSmallCaps("Bloodlust") + ":</bold></white> <gray>Kills or skill triggers grant </gray><yellow>" + TextUtil.toSmallCaps("Speed III") + "</yellow> <gray>for 10s.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <dark_red>•</dark_red> <white><bold>" + TextUtil.toSmallCaps("BloodPrice") + ":</bold></white> <gray>At ≤ -50 Alignment, deal </gray><green>+15% " + TextUtil.toSmallCaps("melee damage") + "</green><gray>, but take </gray><red>10% " + TextUtil.toSmallCaps("extra true damage") + "</red><gray>.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <red><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></red><yellow><bold>" + TextUtil.toSmallCaps("Blood Scythe") + "</bold></yellow> <dark_gray>[<white>Sneak + LMB</white> | <gray>40s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Weapon gains [Fury] for 8s (guaranteed crits, true fire damage).</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Each attack charges +1/5 Revenge (max 5).</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <red><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></red><yellow><bold>" + TextUtil.toSmallCaps("Overdrive") + "</bold></yellow> <dark_gray>[<white>LMB Attack</white> | <gray>60s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Consumes 2 Revenge stacks to enchant your next hit.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Spawns a 5x5 ring of flames for 2s inflicting Stun, Darkness, and 0.5 True Damage/tick.</gray>"));
            }
            case GREED -> {
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <yellow>•</yellow> <white><bold>" + TextUtil.toSmallCaps("Preloaded") + ":</bold></white> <gray>/ritual preloads items into your offhand Relic slot to customize Ability 2.</gray>"));
                player.sendMessage(miniMessage.deserialize("  <yellow>•</yellow> <white><bold>" + TextUtil.toSmallCaps("Gold Siphon") + ":</bold></white> <gray>Melee hits have 2% chance to steal 1 Gapple or Ender Pearl from enemy hotbar (20s target CD).</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></gold><yellow><bold>" + TextUtil.toSmallCaps("Taxing Ray") + "</bold></yellow> <dark_gray>[<white>RMB</white> | <gray>32s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Fires an 8-block gold beam that seals offhand & hotbar for 4s (prevents item swap & shield block).</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></gold><yellow><bold>" + TextUtil.toSmallCaps("Taken") + "</bold></yellow> <dark_gray>[<white>Sneak + RMB</white> | <gray>120s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Consumes preloaded /ritual item:</gray>"));
                player.sendMessage(miniMessage.deserialize("    <yellow>• 64 Ores:</yellow> <gray>Grants Absorption IV for 10s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <yellow>• Netherite Sword:</yellow> <gray>Grants Strength III for 7.5s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <yellow>• Player Head:</yellow> <gray>30s particle tether siphoning 10% of target's dealt damage.</gray>"));
            }
            case GLUTTONY -> {
                player.sendMessage(miniMessage.deserialize("  <green><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></green>"));
                player.sendMessage(miniMessage.deserialize("  <dark_green>•</dark_green> <white><bold>" + TextUtil.toSmallCaps("Bitter Feast") + ":</bold></white> <gray>Junk food acts like Golden Apples. Clean cooked food inflicts Hunger III & Nausea.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <green><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></green><yellow><bold>" + TextUtil.toSmallCaps("Devour Buff") + "</bold></yellow> <dark_gray>[<white>LMB Attack</white> | <gray>120s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Melee hit strips 10s off target's active potion buffs.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Grants self Resistance for 10s and Absorption scaling on buff count.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <green><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></green><yellow><bold>" + TextUtil.toSmallCaps("Acid Spout") + "</bold></yellow> <dark_gray>[<white>Sneak + LMB</white> | <gray>40s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Spawns green acid puddle at feet for 12.5s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Enemies inside suffer Wither II and 3x Armor Durability damage.</gray>"));
            }
            case LUST -> {
                player.sendMessage(miniMessage.deserialize("  <light_purple><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></light_purple>"));
                player.sendMessage(miniMessage.deserialize("  <pink>•</pink> <white><bold>" + TextUtil.toSmallCaps("Narcissism") + ":</bold></white> <gray>Taking damage from enemy players grants Speed III for 2s.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <light_purple><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></light_purple><yellow><bold>" + TextUtil.toSmallCaps("Narcissus Mirror") + "</bold></yellow> <dark_gray>[<white>LMB Attack</white> | <gray>30s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Summons 2 illusory clones around target.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Enemy attacks landing on clones heal you for 50% of damage dealt.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <light_purple><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></light_purple><yellow><bold>" + TextUtil.toSmallCaps("Vanity Shield") + "</bold></yellow> <dark_gray>[<white>Sneak + LMB</white> | <gray>45s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Cleanses all current debuffs.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Each cleansed debuff grants 2 Hearts of Regen II & forces enemies within 5b to snap 180° away.</gray>"));
            }
            case ENVY -> {
                player.sendMessage(miniMessage.deserialize("  <dark_aqua><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></dark_aqua>"));
                player.sendMessage(miniMessage.deserialize("  <aqua>•</aqua> <white><bold>" + TextUtil.toSmallCaps("Shame Scaling") + ":</bold></white> <gray>Deals </gray><yellow>+1.5 extra damage</yellow> <gray>for every armor tier or stat buff tier target has above yours.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <dark_aqua><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></dark_aqua><yellow><bold>" + TextUtil.toSmallCaps("Mirror of Shame") + "</bold></yellow> <dark_gray>[<white>LMB Attack</white> | <gray>70s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Banishes target into 3x3 chamber for 3.5s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>100% of outgoing damage attempted inside is reflected back onto themselves as true damage.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <dark_aqua><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></dark_aqua><yellow><bold>" + TextUtil.toSmallCaps("Shadow Covet") + "</bold></yellow> <dark_gray>[<white>RMB</white> | <gray>25s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Spawns a shadowy clone for 3s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Looking at it inflicts Darkness and steals 20% of target's Speed & Resistance for 6s.</gray>"));
            }
            case PRIDE -> {
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></gold>"));
                player.sendMessage(miniMessage.deserialize("  <yellow>•</yellow> <white><bold>" + TextUtil.toSmallCaps("Unbroken Ego") + ":</bold></white> <gray>Holding Sneak stationary continuously builds Pride Charges (+1/sec up to 5 max).</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></gold><yellow><bold>" + TextUtil.toSmallCaps("Sovereign Charge") + "</bold></yellow> <dark_gray>[<white>RMB</white> | <gray>18s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Consumes charges to lunge forward up to 10 blocks.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Deals 2 hearts of damage on collision and pierces shields.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <gold><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></gold><yellow><bold>" + TextUtil.toSmallCaps("Tombstone Duel") + "</bold></yellow> <dark_gray>[<white>Sneak + RMB</white> | <gray>50s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Traps you and target in 6x6 ring of pillars for 6s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Securing a kill inside restores 100% HP and enhances active potions to 3x duration & 2x strength.</gray>"));
            }
            case SLOTH -> {
                player.sendMessage(miniMessage.deserialize("  <blue><bold>✦ " + TextUtil.toSmallCaps("Passives") + ":</bold></blue>"));
                player.sendMessage(miniMessage.deserialize("  <dark_blue>•</dark_blue> <white><bold>" + TextUtil.toSmallCaps("Heavy Rhythm") + ":</bold></white> <gray>Blocks villager trading and animal breeding. Permanent Hunger I.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <blue><bold>✦ " + TextUtil.toSmallCaps("Ability 1") + " — </bold></blue><yellow><bold>" + TextUtil.toSmallCaps("Temporal Echo") + "</bold></yellow> <dark_gray>[<white>Sneak + LMB</white> | <gray>35s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Summons a trailing shadow ghost that freezes incoming projectiles and applies Slowness II to nearby players.</gray>"));
                player.sendMessage(Component.empty());
                player.sendMessage(miniMessage.deserialize("  <blue><bold>✦ " + TextUtil.toSmallCaps("Ability 2") + " — </bold></blue><yellow><bold>" + TextUtil.toSmallCaps("Delayed Stasis") + "</bold></yellow> <dark_gray>[<white>RMB</white> | <gray>45s CD</gray>]</dark_gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Negates all incoming damage for 6s.</gray>"));
                player.sendMessage(miniMessage.deserialize("    <gray>Once expired, 50% of negated damage is slowly applied as tick-damage over 10s.</gray>"));
            }
        }

        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = List.of("guide", "ritual", "reroll");
            List<String> matches = new ArrayList<>();
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    matches.add(s);
                }
            }
            return matches;
        }
        return List.of();
    }
}
