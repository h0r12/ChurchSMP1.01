package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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

        // /church ritual or /ritual command — Opens the Ritual Sacrifice inventory GUI!
        if (label.equalsIgnoreCase("ritual") || (args.length > 0 && args[0].equalsIgnoreCase("ritual"))) {
            plugin.getGemAbilityExecutor().openRitualInventory(player);
            return true;
        }

        // /church null or /null — toggles entry and exit from the Liminal Null Control Room
        if (label.equalsIgnoreCase("null") || (args.length > 0 && (args[0].equalsIgnoreCase("null") || args[0].equalsIgnoreCase("liminalnull")))) {
            plugin.getFinaleManager().toggleLiminalNull(player);
            return true;
        }

        // /church forsake or /forsake — triggers or recovers Forsaking ritual
        if (label.equalsIgnoreCase("forsake") || (args.length > 0 && args[0].equalsIgnoreCase("forsake"))) {
            if (plugin.getForsakingRitualManager().isInRitual(player)) {
                player.sendMessage(miniMessage.deserialize("<gold>✦ <yellow>" + TextUtil.toSmallCaps("You are currently in the Forsaking Ritual! Aim at a gem and click.") + "</yellow> ✦</gold>"));
                return true;
            }
            if (plugin.getSinGemManager().isAttuned(player)) {
                SinGemType attuned = plugin.getSinGemManager().getAttunedGem(player);
                if (!plugin.getSinGemManager().hasGemInInventory(player)) {
                    plugin.getSinGemManager().giveGemToPlayer(player, attuned);
                    player.sendMessage(miniMessage.deserialize("<gold>✦ [FORSAKING] <white>" + TextUtil.toSmallCaps("Restored your attuned Sin Gem") + ": </white></gold>").append(attuned.getFormattedName()));
                } else {
                    player.sendMessage(miniMessage.deserialize("<gold>✦ [FORSAKING] <gray>" + TextUtil.toSmallCaps("You are already attuned to") + " </gray></gold>").append(attuned.getFormattedName()));
                }
                return true;
            }
            plugin.getForsakingRitualManager().startRitual(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reroll")) {
            plugin.getSinGemManager().rerollGemInHand(player);
            return true;
        }

        // /church testclone <living_rig|kinetic|spectral|all|clear>
        if (args.length > 0 && (args[0].equalsIgnoreCase("testclone") || args[0].equalsIgnoreCase("clone"))) {
            handleTestCloneCommand(player, args);
            return true;
        }

        // /church guide [gemName] — shows detailed breakdown of player's attuned or requested Sin Gem
        if (args.length > 0 && args[0].equalsIgnoreCase("guide")) {
            String gemArg = (args.length > 1) ? args[1] : null;
            showPlayerGemGuide(player, gemArg);
            return true;
        }

        // Default: show gem guide directly
        showPlayerGemGuide(player, null);
        return true;
    }

    private void handleTestCloneCommand(Player player, String[] args) {
        String sub = (args.length > 1) ? args[1].toLowerCase(java.util.Locale.ROOT) : "all";

        if (sub.equals("clear") || sub.equals("remove") || sub.equals("despawn")) {
            com.churchsmp.util.CloneUtil.clearTestClones(player);
            player.sendMessage(miniMessage.deserialize("<gold>✦ [TEST CLONE] <green>" + TextUtil.toSmallCaps("All test doppelgängers cleared!") + "</green> ✦</gold>"));
            return;
        }

        // Clear existing test clones first for clean side-by-side testing
        com.churchsmp.util.CloneUtil.clearTestClones(player);

        boolean forceSkin = false;
        for (String a : args) {
            if (a.equalsIgnoreCase("skin") || a.equalsIgnoreCase("head") || a.equalsIgnoreCase("face")) {
                forceSkin = true;
            }
        }

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        String headModeNote = forceSkin
                ? "<yellow>(Forced Player Skin Face Mode)</yellow>"
                : (player.getInventory().getHelmet() != null && player.getInventory().getHelmet().getType() != Material.AIR
                ? "<aqua>(Equipped Your Helmet)</aqua>"
                : "<yellow>(Equipped Your Player Skin Face)</yellow>");

        if (sub.equals("all") || sub.equals("skin")) {
            // Spawn all 3 side-by-side
            spawnSingleTestClone(player, com.churchsmp.util.CloneUtil.CloneModelType.LIVING_RIG,
                    loc.clone().add(dir.clone().multiply(3.5)).add(right.clone().multiply(-2.2)),
                    miniMessage.deserialize("<aqua><bold>[Model A] Living Rig</bold></aqua>"), forceSkin);

            spawnSingleTestClone(player, com.churchsmp.util.CloneUtil.CloneModelType.KINETIC,
                    loc.clone().add(dir.clone().multiply(3.5)),
                    miniMessage.deserialize("<yellow><bold>[Model B] Kinetic Puppet</bold></yellow>"), forceSkin);

            spawnSingleTestClone(player, com.churchsmp.util.CloneUtil.CloneModelType.SPECTRAL,
                    loc.clone().add(dir.clone().multiply(3.5)).add(right.clone().multiply(2.2)),
                    miniMessage.deserialize("<light_purple><bold>[Model C] Spectral Mirage</bold></light_purple>"), forceSkin);

            player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
            player.sendMessage(miniMessage.deserialize("<gold>✦ <yellow><bold>" + TextUtil.toSmallCaps("Spawned 3 Human Doppelgängers Side-by-Side") + "</bold></yellow> " + headModeNote + " ✦</gold>"));
            player.sendMessage(miniMessage.deserialize("<aqua>• Model A (Living Rig):</aqua> <gray>Invisible host + natural-posture human puppet. (No zombie arms!)</gray>"));
            player.sendMessage(miniMessage.deserialize("<yellow>• Model B (Kinetic Puppet):</yellow> <gray>Procedural walking animation & weapon swing physics.</gray>"));
            player.sendMessage(miniMessage.deserialize("<light_purple>• Model C (Spectral Mirage):</light_purple> <gray>Translucent sorrow/grief soul phantom.</gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>Tip: Type </gray><yellow>/church testclone all skin</yellow> <gray>to see skin face or </gray><yellow>/church testclone all</yellow> <gray>for helmet.</gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>Type </gray><yellow>/church testclone clear</yellow> <gray>when done testing.</gray>"));
            player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
            return;
        }

        com.churchsmp.util.CloneUtil.CloneModelType chosen = switch (sub) {
            case "living_rig", "living", "rig", "a" -> com.churchsmp.util.CloneUtil.CloneModelType.LIVING_RIG;
            case "kinetic", "puppet", "b" -> com.churchsmp.util.CloneUtil.CloneModelType.KINETIC;
            case "spectral", "ghost", "c" -> com.churchsmp.util.CloneUtil.CloneModelType.SPECTRAL;
            default -> com.churchsmp.util.CloneUtil.CloneModelType.LIVING_RIG;
        };

        Location spawnLoc = loc.clone().add(dir.clone().multiply(3.0));
        spawnSingleTestClone(player, chosen, spawnLoc, miniMessage.deserialize("<yellow><bold>[Test Doppelgänger]</bold></yellow>"), forceSkin);
        player.sendMessage(miniMessage.deserialize("<gold>✦ [TEST CLONE] <green>" + TextUtil.toSmallCaps("Spawned " + chosen.name() + " Doppelgänger!") + "</green> " + headModeNote + " <gray>(Use /church testclone clear to remove)</gray> ✦</gold>"));
    }

    private void spawnSingleTestClone(Player player, com.churchsmp.util.CloneUtil.CloneModelType model, Location loc, Component name, boolean forceSkin) {
        com.churchsmp.util.CloneUtil.CloneConfig cfg = new com.churchsmp.util.CloneUtil.CloneConfig();
        cfg.owner = player;
        cfg.location = loc;
        cfg.modelType = model;
        cfg.displayName = name;
        cfg.showNameTag = true;
        cfg.forceSkinHead = forceSkin;
        cfg.durationTicks = 12000; // 10 minutes test lifespan
        cfg.followOwner = false; // Stand still for easy inspection
        cfg.attackDamage = 0.0; // Don't hurt the player during inspection

        org.bukkit.entity.LivingEntity clone = com.churchsmp.util.CloneUtil.spawnRealisticClone(plugin, cfg);
        if (clone != null) {
            com.churchsmp.util.CloneUtil.registerTestClone(player, clone);
        }
    }

    private void showPlayerGemGuide(Player player, String requestedGem) {
        SinGemType gem = null;
        if (requestedGem != null) {
            try {
                gem = SinGemType.valueOf(requestedGem.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                for (SinGemType t : SinGemType.values()) {
                    if (t.name().equalsIgnoreCase(requestedGem) || t.getDisplayName().equalsIgnoreCase(requestedGem)) {
                        gem = t;
                        break;
                    }
                }
            }
        }

        if (gem == null) {
            gem = plugin.getSinGemManager().getAttunedGem(player);
        }

        if (gem == null) {
            gem = plugin.getSinGemManager().getHeldGem(player);
        }

        if (gem == null) {
            player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
            player.sendMessage(miniMessage.deserialize("<gold>✦ <yellow><bold>" + TextUtil.toSmallCaps("Sin Gem Guide Directory") + "</bold></yellow> ✦</gold>"));
            player.sendMessage(miniMessage.deserialize("<gray>You are not attuned to any Sin Gem yet, but you can read any guide:</gray>"));
            for (SinGemType type : SinGemType.values()) {
                player.sendMessage(miniMessage.deserialize("  <gold>•</gold> ").append(type.getFormattedName())
                        .append(miniMessage.deserialize(" <gray>— Type </gray><yellow>/church guide " + type.name().toLowerCase() + "</yellow>")));
            }
            player.sendMessage(miniMessage.deserialize("<gray><i>Tip: Complete the Forsaking Ritual or right-click an unattuned Sin Gem to attune.</i></gray>"));
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
            List<String> sub = List.of("guide", "null", "ritual", "reroll", "forsake", "testclone");
            List<String> matches = new ArrayList<>();
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    matches.add(s);
                }
            }
            return matches;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("testclone") || args[0].equalsIgnoreCase("clone"))) {
            List<String> sub = List.of("all", "living_rig", "kinetic", "spectral", "clear");
            List<String> matches = new ArrayList<>();
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(s);
                }
            }
            return matches;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("testclone") || args[0].equalsIgnoreCase("clone"))) {
            List<String> sub = List.of("skin", "helmet");
            List<String> matches = new ArrayList<>();
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[2].toLowerCase())) {
                    matches.add(s);
                }
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("guide")) {
            List<String> matches = new ArrayList<>();
            for (SinGemType t : SinGemType.values()) {
                if (t.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(t.name().toLowerCase());
                }
            }
            return matches;
        }
        return List.of();
    }
}
