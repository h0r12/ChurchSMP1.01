package com.churchsmp.command;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
                    sender.sendMessage(Component.text("Invalid gem! Choose from: Wrath, Greed, Gluttony, Lust, Envy, Pride, Sloth, Sin", NamedTextColor.RED));
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
            case "givecore" -> {
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(com.churchsmp.item.LiminalCoreItem.createCore(plugin));
                sender.sendMessage(Component.text("Gave Liminal Event Core to " + target.getName() + "!", NamedTextColor.GREEN));
                target.sendMessage(Component.text("✦ You received a Liminal Event Core!", NamedTextColor.GOLD));
            }
            case "giverelic" -> {
                if (args.length == 2 && args[1].equalsIgnoreCase("all")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Specify a player from console: /churchadmin giverelic <player> all", NamedTextColor.RED));
                        return true;
                    }
                    giveAllRelics(p, sender);
                    return true;
                }
                if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    giveAllRelics(target, sender);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /churchadmin giverelic [player] <all|iniquity|impiety|obscura>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                try {
                    com.churchsmp.item.RelicItem.RelicType type = com.churchsmp.item.RelicItem.RelicType.valueOf(args[2].toUpperCase(Locale.ROOT));
                    target.getInventory().addItem(com.churchsmp.item.RelicItem.createRelic(plugin, type));
                    sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN).append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(type.getDisplayName())).append(Component.text(" to " + target.getName())));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid relic type! Choose from: all, iniquity, impiety, obscura", NamedTextColor.RED));
                }
            }
            case "forsake" -> {
                Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                plugin.getForsakingRitualManager().resetAndStartRitual(target);
                sender.sendMessage(Component.text("Reset and initiated the Forsaking Ritual for " + target.getName() + "!", NamedTextColor.GREEN));
            }
            case "event" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin event <bloodmoon|inquisition|stop>", NamedTextColor.RED));
                    return true;
                }
                String eventSub = args[1].toLowerCase(Locale.ROOT);
                switch (eventSub) {
                    case "bloodmoon" -> {
                        plugin.getChurchEventManager().startEvent(com.churchsmp.event.ChurchEventManager.EventType.BLOOD_MOON, 20 * 60 * 15);
                        sender.sendMessage(Component.text("Started Blood Moon event!", NamedTextColor.DARK_RED));
                    }
                    case "inquisition" -> {
                        plugin.getChurchEventManager().startEvent(com.churchsmp.event.ChurchEventManager.EventType.HOLY_INQUISITION, 20 * 60 * 15);
                        sender.sendMessage(Component.text("Started Holy Inquisition event!", NamedTextColor.GOLD));
                    }
                    case "stop" -> {
                        plugin.getChurchEventManager().stopCurrentEvent();
                        sender.sendMessage(Component.text("Stopped active server event.", NamedTextColor.YELLOW));
                    }
                    default -> sender.sendMessage(Component.text("Unknown event type! Use bloodmoon, inquisition, or stop.", NamedTextColor.RED));
                }
            }
            case "purge" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin purge <quest|player|clear|spawn_altar>", NamedTextColor.RED));
                    return true;
                }
                if (args[1].equalsIgnoreCase("quest")) {
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /churchadmin purge quest <start|stop> [player]", NamedTextColor.RED));
                        return true;
                    }
                    if (args[2].equalsIgnoreCase("start")) {
                        Player target = (args.length > 3) ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                        if (target == null) {
                            sender.sendMessage(Component.text("Player not found. Specify an online player.", NamedTextColor.RED));
                            return true;
                        }
                        plugin.getFinaleManager().startPurge(target, false);
                        sender.sendMessage(Component.text("Initiated ChurchSMP Purge Quest with " + target.getName() + " as the Juggernaut!", NamedTextColor.GOLD));
                        return true;
                    } else if (args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("clear")) {
                        plugin.getFinaleManager().clearPurge(sender instanceof Player p ? p : null);
                        sender.sendMessage(Component.text("ChurchSMP Purge Quest stopped and cleared.", NamedTextColor.GREEN));
                        return true;
                    } else {
                        sender.sendMessage(Component.text("Usage: /churchadmin purge quest <start|stop> [player]", NamedTextColor.RED));
                        return true;
                    }
                }
                if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("stop") || args[1].equalsIgnoreCase("reset")) {
                    plugin.getFinaleManager().clearPurge(sender instanceof Player p ? p : null);
                    sender.sendMessage(Component.text("ChurchSMP Finale Purge cleared successfully.", NamedTextColor.GREEN));
                    return true;
                }
                if (args[1].equalsIgnoreCase("spawn_altar")) {
                    if (sender instanceof Player p) {
                        plugin.getFinaleManager().spawnAltar(p.getLocation());
                        sender.sendMessage(Component.text("Spawned the Purge Altar at your location!", NamedTextColor.GOLD));
                    } else {
                        sender.sendMessage(Component.text("Only players can spawn the altar.", NamedTextColor.RED));
                    }
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                    return true;
                }
                plugin.getFinaleManager().startPurge(target, false);
                sender.sendMessage(Component.text("Initiated ChurchSMP Purge with " + target.getName() + " as the Juggernaut!", NamedTextColor.GOLD));
            }
            case "set" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("wand")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Only players can receive the Abyss Setup Wand.", NamedTextColor.RED));
                        return true;
                    }
                    plugin.getAbyssManager().giveSetupWand(p);
                    return true;
                }
                if (args[1].equalsIgnoreCase("blacksun")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Only players can set the Black Sun location.", NamedTextColor.RED));
                        return true;
                    }
                    Location loc = p.getLocation();
                    plugin.getStartEventManager().setCustomCenter(loc);
                    sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            "<green>✦ Black Sun & Genesis Ceremony center set to: <gold>" + loc.getWorld().getName() + " (" + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()) + ")</gold> ✦</green>"
                    ));
                    p.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
                    p.playSound(loc, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.5f);
                } else {
                    sender.sendMessage(Component.text("Unknown set target! Usage: /churchadmin set [wand|blacksun]", NamedTextColor.RED));
                }
            }
            case "abyss" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin abyss <wand|set|reset|climax>", NamedTextColor.RED));
                    return true;
                }
                String abyssSub = args[1].toLowerCase(Locale.ROOT);
                switch (abyssSub) {
                    case "wand" -> {
                        if (!(sender instanceof Player p)) {
                            sender.sendMessage(Component.text("Only players can receive the Abyss Setup Wand.", NamedTextColor.RED));
                            return true;
                        }
                        plugin.getAbyssManager().giveSetupWand(p);
                    }
                    case "set" -> {
                        if (args.length < 3) {
                            if (sender instanceof Player p) {
                                plugin.getAbyssManager().giveSetupWand(p);
                                return true;
                            }
                            sender.sendMessage(Component.text("Usage: /churchadmin abyss set <main|entrance|wrath|envy|pride|gluttony|greed|lust|sloth>", NamedTextColor.RED));
                            return true;
                        }
                        if (!(sender instanceof Player p)) {
                            sender.sendMessage(Component.text("Only players can set Abyss locations.", NamedTextColor.RED));
                            return true;
                        }
                        String target = args[2].toLowerCase(Locale.ROOT);
                        Location loc = p.getLocation();
                        if (target.equalsIgnoreCase("main")) {
                            plugin.getAbyssManager().startMainAltar(loc);
                            p.sendMessage(Component.text("✦ Abyss Main Altar (Bedrock + 7 Sin Dual Orbit) set to your location!", NamedTextColor.GOLD));
                        } else if (target.equalsIgnoreCase("entrance")) {
                            plugin.getAbyssManager().setEntranceLocation(loc);
                            p.sendMessage(Component.text("✦ Abyss Entrance location set to your location!", NamedTextColor.GOLD));
                        } else {
                            try {
                                SinGemType sin = SinGemType.valueOf(target.toUpperCase(Locale.ROOT));
                                plugin.getAbyssManager().setSlotLocation(sin, loc);
                                p.sendMessage(Component.text("✦ Abyss Pedestal Slot for " + sin.getDisplayName() + " set to your location!", NamedTextColor.GREEN));
                            } catch (IllegalArgumentException e) {
                                p.sendMessage(Component.text("Unknown sin or target! Choose: main, entrance, wrath, envy, pride, gluttony, greed, lust, sloth", NamedTextColor.RED));
                            }
                        }
                    }
                    case "reset" -> {
                        plugin.getAbyssManager().cleanupAltar();
                        if (plugin.getAbyssManager().getMainAltarLocation() != null) {
                            plugin.getAbyssManager().startMainAltar(plugin.getAbyssManager().getMainAltarLocation());
                        }
                        sender.sendMessage(Component.text("✦ Abyss Altar and active displays reset!", NamedTextColor.GREEN));
                    }
                    case "climax" -> {
                        if (!(sender instanceof Player p)) {
                            sender.sendMessage(Component.text("Only players can trigger Abyss climax test.", NamedTextColor.RED));
                            return true;
                        }
                        plugin.getAbyssManager().triggerGrandVoidClimax(p);
                        p.sendMessage(Component.text("✦ Triggering Grand Void Climax Test!", NamedTextColor.DARK_RED));
                    }
                    default -> sender.sendMessage(Component.text("Usage: /churchadmin abyss <set|reset|climax>", NamedTextColor.RED));
                }
            }
            case "start" -> {
                if (plugin.getStartEventManager() != null) {
                    if (args.length > 1 && args[1].equalsIgnoreCase("force")) {
                        plugin.getStartEventManager().forceProgress(sender);
                    } else {
                        Player p = (sender instanceof Player player) ? player : null;
                        plugin.getStartEventManager().startEvent(p);
                    }
                } else {
                    sender.sendMessage(Component.text("StartEventManager is not available.", NamedTextColor.RED));
                }
            }
            case "blacksun" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can summon the Black Sun.", NamedTextColor.RED));
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    plugin.getStartEventManager().stopTestBlackSun(player);
                    player.sendMessage(Component.text("✦ Celestial Black Sun test dismissed.", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.getStartEventManager().startTestBlackSun(player);
                player.sendMessage(Component.text("✦ Celestial Black Sun manifested above you! Type '/churchadmin blacksun stop' to dismiss.", NamedTextColor.GOLD));
            }
            case "immersive" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin immersive <shake|vintage|vignette|flash|strobe|letterbox|clear>", NamedTextColor.RED));
                    return true;
                }
                String imSub = args[1].toLowerCase(Locale.ROOT);
                switch (imSub) {
                    case "shake" -> {
                        int level = 1;
                        if (args.length > 2) {
                            try { level = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                        }
                        int duration = 40;
                        if (args.length > 3) {
                            try { duration = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                        }
                        Player target = (args.length > 4) ? Bukkit.getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.triggerScreenShake(target, level, duration, plugin);
                            sender.sendMessage(Component.text("✦ Triggered Screen Shake Level " + level + " (" + duration + " ticks) for " + target.getName() + "!", NamedTextColor.GOLD));
                        } else {
                            com.churchsmp.util.CinematicVFXUtil.triggerScreenShakeAll(level, duration, plugin);
                            sender.sendMessage(Component.text("✦ Triggered Global Screen Shake Level " + level + " (" + duration + " ticks) for all players!", NamedTextColor.GOLD));
                        }
                    }
                    case "vintage" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Component.text("Usage: /churchadmin immersive vintage <sepia|noir|blood|frost|solar|void|neon> [player]", NamedTextColor.RED));
                            return true;
                        }
                        com.churchsmp.util.CinematicVFXUtil.VintageStyle style = parseVintageStyle(args[2]);
                        if (style == null) {
                            sender.sendMessage(Component.text("Unknown vintage style! Choose: sepia, noir, blood, frost, solar, void, neon", NamedTextColor.RED));
                            return true;
                        }
                        Player target = (args.length > 3) ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.flashVintage(target, style, plugin);
                            sender.sendMessage(Component.text("✦ Triggered " + style.getDisplayName() + " for " + target.getName() + "!", NamedTextColor.GOLD));
                        } else {
                            for (Player p : Bukkit.getOnlinePlayers()) com.churchsmp.util.CinematicVFXUtil.flashVintage(p, style, plugin);
                            sender.sendMessage(Component.text("✦ Triggered " + style.getDisplayName() + " for all online players!", NamedTextColor.GOLD));
                        }
                    }
                    case "vignette" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Component.text("Usage: /churchadmin immersive vignette <red|frost|dark|clear> [player] [ticks]", NamedTextColor.RED));
                            return true;
                        }
                        Player target = (args.length > 3) ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                        int duration = 60;
                        if (args.length > 4) {
                            try { duration = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {}
                        }
                        applyVignette(sender, target, args[2], duration);
                    }
                    case "flash" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Component.text("Usage: /churchadmin immersive flash <#hexColor> [inMs] [stayMs] [outMs] [player]", NamedTextColor.RED));
                            return true;
                        }
                        String hex = args[2].startsWith("#") ? args[2] : "#" + args[2];
                        int in = args.length > 3 ? Integer.parseInt(args[3]) : 50;
                        int stay = args.length > 4 ? Integer.parseInt(args[4]) : 200;
                        int out = args.length > 5 ? Integer.parseInt(args[5]) : 600;
                        Player target = (args.length > 6) ? Bukkit.getPlayer(args[6]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.flashScreen(target, hex, in, stay, out);
                            sender.sendMessage(Component.text("✦ Flashed " + hex + " on " + target.getName() + "'s screen.", NamedTextColor.GOLD));
                        } else {
                            for (Player p : Bukkit.getOnlinePlayers()) com.churchsmp.util.CinematicVFXUtil.flashScreen(p, hex, in, stay, out);
                            sender.sendMessage(Component.text("✦ Flashed " + hex + " on all screens.", NamedTextColor.GOLD));
                        }
                    }
                    case "strobe" -> {
                        if (args.length < 3) {
                            sender.sendMessage(Component.text("Usage: /churchadmin immersive strobe <#hexColor> [count] [player]", NamedTextColor.RED));
                            return true;
                        }
                        String hex = args[2].startsWith("#") ? args[2] : "#" + args[2];
                        int count = args.length > 3 ? Integer.parseInt(args[3]) : 3;
                        Player target = (args.length > 4) ? Bukkit.getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.flashStrobe(target, hex, count, plugin);
                            sender.sendMessage(Component.text("✦ Triggered " + count + " strobe flashes of " + hex + " for " + target.getName() + "!", NamedTextColor.GOLD));
                        } else {
                            for (Player p : Bukkit.getOnlinePlayers()) com.churchsmp.util.CinematicVFXUtil.flashStrobe(p, hex, count, plugin);
                            sender.sendMessage(Component.text("✦ Triggered " + count + " strobe flashes of " + hex + " for all players!", NamedTextColor.GOLD));
                        }
                    }
                    case "letterbox" -> {
                        if (args.length < 4) {
                            sender.sendMessage(Component.text("Usage: /churchadmin immersive letterbox <title> <subtitle> [player]", NamedTextColor.RED));
                            return true;
                        }
                        String titleText = args[2].replace("_", " ");
                        String subText = args[3].replace("_", " ");
                        Player target = (args.length > 4) ? Bukkit.getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.triggerLetterbox(target, titleText, subText, 60);
                            sender.sendMessage(Component.text("✦ Sent letterbox cinematic to " + target.getName() + "!", NamedTextColor.GOLD));
                        } else {
                            for (Player p : Bukkit.getOnlinePlayers()) com.churchsmp.util.CinematicVFXUtil.triggerLetterbox(p, titleText, subText, 60);
                            sender.sendMessage(Component.text("✦ Sent letterbox cinematic to all players!", NamedTextColor.GOLD));
                        }
                    }
                    case "clear" -> {
                        Player target = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                        if (target != null) {
                            com.churchsmp.util.CinematicVFXUtil.clearVignettes(target);
                            sender.sendMessage(Component.text("✦ Cleared all active screen vignettes for " + target.getName() + ".", NamedTextColor.GREEN));
                        } else {
                            for (Player p : Bukkit.getOnlinePlayers()) com.churchsmp.util.CinematicVFXUtil.clearVignettes(p);
                            sender.sendMessage(Component.text("✦ Cleared all screen vignettes for all players.", NamedTextColor.GREEN));
                        }
                    }
                    default -> sender.sendMessage(Component.text("Usage: /churchadmin immersive <shake|vintage|vignette|flash|strobe|letterbox|clear>", NamedTextColor.RED));
                }
            }
            case "shake" -> {
                int level = (args.length > 1) ? Math.max(1, Math.min(5, Integer.parseInt(args[1]))) : 1;
                int duration = (args.length > 2) ? Integer.parseInt(args[2]) : 40;
                Player target = (args.length > 3) ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                if (target != null) {
                    com.churchsmp.util.CinematicVFXUtil.triggerScreenShake(target, level, duration, plugin);
                    sender.sendMessage(Component.text("✦ Triggered Screen Shake Level " + level + " (" + duration + " ticks) for " + target.getName() + "!", NamedTextColor.GOLD));
                } else {
                    com.churchsmp.util.CinematicVFXUtil.triggerScreenShakeAll(level, duration, plugin);
                    sender.sendMessage(Component.text("✦ Triggered Global Screen Shake Level " + level + " (" + duration + " ticks) for all players!", NamedTextColor.GOLD));
                }
            }
            case "fx" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin fx <sin> [player]", NamedTextColor.RED));
                    return true;
                }
                SinGemType sin;
                try {
                    sin = SinGemType.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid sin! Choose from: Wrath, Greed, Gluttony, Lust, Envy, Pride, Sloth, Sin", NamedTextColor.RED));
                    return true;
                }
                Player target = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                com.churchsmp.util.CinematicVFXUtil.triggerSinScreenFX(target, sin, plugin);
                sender.sendMessage(Component.text("✦ Triggered " + sin.name() + " cinematic screen FX for " + target.getName() + "!", NamedTextColor.GREEN));
            }
            case "vintage" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin vintage <sepia|noir|blood|frost|solar|void|neon> [player]", NamedTextColor.RED));
                    return true;
                }
                com.churchsmp.util.CinematicVFXUtil.VintageStyle style;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "sepia" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.SEPIA_FILM;
                    case "noir" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.NOIR_SHADOW;
                    case "blood" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.BLOOD_HAZE;
                    case "frost" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.GLACIAL_FROST;
                    case "solar" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.SOLAR_ECLIPSE;
                    case "void" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.VOID_ABYSS;
                    case "neon" -> style = com.churchsmp.util.CinematicVFXUtil.VintageStyle.NEON_CYBER;
                    default -> {
                        sender.sendMessage(Component.text("Unknown vintage style! Choose: sepia, noir, blood, frost, solar, void, neon", NamedTextColor.RED));
                        return true;
                    }
                }
                Player target = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                com.churchsmp.util.CinematicVFXUtil.flashVintage(target, style, plugin);
                sender.sendMessage(Component.text("✦ Triggered " + style.getDisplayName() + " for " + target.getName() + "!", NamedTextColor.GOLD));
            }
            case "vignette" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin vignette <red|frost|dark|clear> [player] [ticks]", NamedTextColor.RED));
                    return true;
                }
                Player target = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                int duration = 60; // 3 seconds default
                if (args.length > 3) {
                    try { duration = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "red" -> {
                        com.churchsmp.util.CinematicVFXUtil.triggerRedVignette(target, duration, plugin);
                        sender.sendMessage(Component.text("✦ Applied Blood-Red warning vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.RED));
                    }
                    case "frost" -> {
                        com.churchsmp.util.CinematicVFXUtil.triggerFrostVignette(target, duration);
                        sender.sendMessage(Component.text("✦ Applied Glacial Frost vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.AQUA));
                    }
                    case "dark" -> {
                        com.churchsmp.util.CinematicVFXUtil.triggerDarknessVignette(target, duration);
                        sender.sendMessage(Component.text("✦ Applied Void Darkness vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.DARK_GRAY));
                    }
                    case "clear" -> {
                        com.churchsmp.util.CinematicVFXUtil.clearVignettes(target);
                        sender.sendMessage(Component.text("✦ Cleared all active screen vignettes for " + target.getName() + ".", NamedTextColor.GREEN));
                    }
                    default -> sender.sendMessage(Component.text("Unknown vignette type! Choose: red, frost, dark, clear", NamedTextColor.RED));
                }
            }
            case "flash" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /churchadmin flash <#hexColor> [fadeInMs] [stayMs] [fadeOutMs] [player]", NamedTextColor.RED));
                    return true;
                }
                String hex = args[1].startsWith("#") ? args[1] : "#" + args[1];
                int in = args.length > 2 ? Integer.parseInt(args[2]) : 50;
                int stay = args.length > 3 ? Integer.parseInt(args[3]) : 200;
                int out = args.length > 4 ? Integer.parseInt(args[4]) : 600;
                Player target = (args.length > 5) ? Bukkit.getPlayer(args[5]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                com.churchsmp.util.CinematicVFXUtil.flashScreen(target, hex, in, stay, out);
                sender.sendMessage(Component.text("✦ Flashed " + hex + " on " + target.getName() + "'s screen (" + in + "ms in, " + stay + "ms stay, " + out + "ms out).", NamedTextColor.GOLD));
            }
            case "null" -> {
                if (args.length < 2) {
                    if (sender instanceof Player p) {
                        plugin.getFinaleManager().toggleLiminalNull(p);
                    } else {
                        sender.sendMessage(Component.text("Usage: /churchadmin null <enchant|border|potions|player>", NamedTextColor.RED));
                    }
                    return true;
                }
                String nullSub = args[1].toLowerCase(Locale.ROOT);
                switch (nullSub) {
                    case "enchant" -> {
                        if (sender instanceof Player p) {
                            plugin.getNullRoomManager().openEnchantmentMenu(p);
                        } else {
                            sender.sendMessage(Component.text("Only players can open the Enchantment menu.", NamedTextColor.RED));
                        }
                    }
                    case "border" -> {
                        if (args.length > 2) {
                            try {
                                double size = Double.parseDouble(args[2]);
                                plugin.getNullRoomManager().setWorldBorderSize(size);
                                sender.sendMessage(Component.text("World Border adjusted to: " + (int) size + " blocks.", NamedTextColor.GREEN));
                            } catch (NumberFormatException e) {
                                sender.sendMessage(Component.text("Invalid border size number: " + args[2], NamedTextColor.RED));
                            }
                        } else {
                            sender.sendMessage(Component.text("Current World Border size: " + (int) plugin.getNullRoomManager().getWorldBorderSize() + " blocks.", NamedTextColor.GOLD));
                        }
                    }
                    case "potions" -> {
                        if (sender instanceof Player p) {
                            plugin.getNullRoomManager().openPotionMenu(p);
                        } else {
                            sender.sendMessage(Component.text("Only players can open the Potions menu.", NamedTextColor.RED));
                        }
                    }
                    case "player" -> {
                        if (sender instanceof Player p) {
                            if (args.length > 2) {
                                Player target = Bukkit.getPlayer(args[2]);
                                if (target != null && target.isOnline()) {
                                    plugin.getNullRoomManager().openPlayerActionMenu(p, target);
                                } else {
                                    p.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                                }
                            } else {
                                plugin.getNullRoomManager().openStevePlayerMenu(p);
                            }
                        } else {
                            sender.sendMessage(Component.text("Only players can open the Player menu.", NamedTextColor.RED));
                        }
                    }
                    default -> sender.sendMessage(Component.text("Usage: /churchadmin null <enchant|border|potions|player>", NamedTextColor.RED));
                }
            }
            case "particle" -> handleParticleCommand(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("ChurchSMP config reloaded successfully!", NamedTextColor.GREEN));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private com.churchsmp.util.CinematicVFXUtil.VintageStyle parseVintageStyle(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "sepia" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.SEPIA_FILM;
            case "noir" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.NOIR_SHADOW;
            case "blood" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.BLOOD_HAZE;
            case "frost" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.GLACIAL_FROST;
            case "solar" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.SOLAR_ECLIPSE;
            case "void" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.VOID_ABYSS;
            case "neon" -> com.churchsmp.util.CinematicVFXUtil.VintageStyle.NEON_CYBER;
            default -> null;
        };
    }

    private void applyVignette(CommandSender sender, Player target, String type, int duration) {
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) applyVignette(sender, p, type, duration);
            return;
        }
        switch (type.toLowerCase(Locale.ROOT)) {
            case "red" -> {
                com.churchsmp.util.CinematicVFXUtil.triggerRedVignette(target, duration, plugin);
                sender.sendMessage(Component.text("✦ Applied Blood-Red warning vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.RED));
            }
            case "frost" -> {
                com.churchsmp.util.CinematicVFXUtil.triggerFrostVignette(target, duration);
                sender.sendMessage(Component.text("✦ Applied Glacial Frost vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.AQUA));
            }
            case "dark" -> {
                com.churchsmp.util.CinematicVFXUtil.triggerDarknessVignette(target, duration);
                sender.sendMessage(Component.text("✦ Applied Void Darkness vignette to " + target.getName() + " for " + duration + " ticks.", NamedTextColor.DARK_GRAY));
            }
            case "clear" -> {
                com.churchsmp.util.CinematicVFXUtil.clearVignettes(target);
                sender.sendMessage(Component.text("✦ Cleared all active screen vignettes for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown vignette type! Choose: red, frost, dark, clear", NamedTextColor.RED));
        }
    }

    private void handleParticleCommand(CommandSender sender, String[] args) {
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ca particle <gui|wand|play|reload> [...]", NamedTextColor.RED));
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu", "studio" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the Particle Studio GUI.", NamedTextColor.RED));
                    return;
                }
                plugin.getParticleStudioGuiManager().openStudio(player, plugin.getParticleAnimationManager().getSession(player));
            }
            case "reload" -> {
                plugin.getParticleAnimationManager().loadAnimations();
                sender.sendMessage(mm.deserialize("<#d8b4fe>Reloaded particle animations from animations.yml!</#d8b4fe>"));
            }
            case "play" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /ca particle play <animation_name> [player]", NamedTextColor.RED));
                    return;
                }
                Player target = (args.length > 3) ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or target required.", NamedTextColor.RED));
                    return;
                }
                com.churchsmp.particle.ParticleAnimation anim = plugin.getParticleAnimationManager().getSavedAnimation(args[2]);
                if (anim == null) {
                    sender.sendMessage(Component.text("Animation not found: " + args[2], NamedTextColor.RED));
                    return;
                }
                plugin.getParticleAnimationManager().playAnimation(target.getEyeLocation(), anim, target);
                sender.sendMessage(mm.deserialize("<#d8b4fe>Playing animation <white>" + anim.getName() + "</white> at " + target.getName() + ".</#d8b4fe>"));
            }
            case "wand", "want" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use the particle wand.", NamedTextColor.RED));
                    return;
                }
                com.churchsmp.particle.WandSession session = plugin.getParticleAnimationManager().getSession(player);

                if (args.length == 2) {
                    player.getInventory().addItem(plugin.getParticleAnimationManager().createWandItem());
                    player.sendMessage(mm.deserialize("<#d8b4fe>Particle Wand given! <gray>(Shift+Left Click or /ca particle gui to open Studio)</gray></#d8b4fe>"));
                    return;
                }

                if (args.length >= 5) {
                    try {
                        double dx = Double.parseDouble(args[2].replace("+", ""));
                        double dy = Double.parseDouble(args[3].replace("+", ""));
                        double dz = Double.parseDouble(args[4].replace("+", ""));
                        session.setCoordinates(dx, dy, dz);
                        player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand 3D coordinates set to <white>X:%+.2f Y:%+.2f Z:%+.2f</white></#d8b4fe>", dx, dy, dz)));
                        return;
                    } catch (NumberFormatException ignored) {}
                }

                if (args.length == 3) {
                    try {
                        double directY = Double.parseDouble(args[2].replace("+", ""));
                        session.setActiveYOffset(directY);
                        player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand Y-offset set to <white>%+.2f</white></#d8b4fe>", directY)));
                        return;
                    } catch (NumberFormatException ignored) {}
                }

                String wandSub = args[2].toLowerCase(Locale.ROOT);
                switch (wandSub) {
                    case "gui", "menu", "studio" -> {
                        plugin.getParticleStudioGuiManager().openStudio(player, session);
                    }
                    case "set" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand set <alias/type> [color] [size]", NamedTextColor.RED));
                            return;
                        }
                        String rawSpec = args[3];
                        if (args.length >= 5) {
                            rawSpec = args[3] + ":" + args[4];
                            if (args.length >= 6) {
                                rawSpec += ":" + args[5];
                            }
                        }
                        com.churchsmp.particle.ParticleAlias alias = com.churchsmp.particle.ParticleAlias.fromString(rawSpec);
                        session.setActiveParticle(alias);
                        player.sendMessage(mm.deserialize("<#d8b4fe>Wand particle set to <white>" + alias.getRawIdentifier() + "</white></#d8b4fe>"));
                    }
                    case "present", "preset" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand preset <star_4|ring|slash|spiral|helix|hypocycloid|rose|lissajous|bolt|bolt_veins>", NamedTextColor.RED));
                            return;
                        }
                        com.churchsmp.particle.MathPreset preset = com.churchsmp.particle.MathPreset.fromString(args[3]);
                        session.setActivePreset(preset);
                        player.sendMessage(mm.deserialize("<#d8b4fe>Wand preset set to <white>" + preset.name() + "</white></#d8b4fe>"));
                    }
                    case "x" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand x <+num|-num>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double xVal = Double.parseDouble(args[3].replace("+", ""));
                            session.setActiveXOffset(xVal);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand X-offset set to <white>%+.2f</white></#d8b4fe>", xVal)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid number: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "y", "yoffset", "height" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand y <+num|-num>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double yVal = Double.parseDouble(args[3].replace("+", ""));
                            session.setActiveYOffset(yVal);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand Y-offset set to <white>%+.2f</white></#d8b4fe>", yVal)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid number: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "z", "dist", "distance" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand z <+num|-num>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double zVal = Double.parseDouble(args[3].replace("+", ""));
                            session.setActiveZOffset(zVal);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand Z-offset set to <white>%+.2f</white></#d8b4fe>", zVal)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid number: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "pos", "position", "coords", "coord", "offset" -> {
                        if (args.length < 6) {
                            player.sendMessage(Component.text("Usage: /ca particle wand pos <X> <Y> <Z>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double px = Double.parseDouble(args[3].replace("+", ""));
                            double py = Double.parseDouble(args[4].replace("+", ""));
                            double pz = Double.parseDouble(args[5].replace("+", ""));
                            session.setCoordinates(px, py, pz);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand 3D coordinates set to <white>X:%+.2f Y:%+.2f Z:%+.2f</white></#d8b4fe>", px, py, pz)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Coordinates must be valid numbers.", NamedTextColor.RED));
                        }
                    }
                    case "move", "translate", "nudge" -> {
                        if (args.length < 6) {
                            player.sendMessage(Component.text("Usage: /ca particle wand move <dX> <dY> <dZ> [all|frame]", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double mx = Double.parseDouble(args[3].replace("+", ""));
                            double my = Double.parseDouble(args[4].replace("+", ""));
                            double mz = Double.parseDouble(args[5].replace("+", ""));
                            boolean allFrames = args.length >= 7 && args[6].equalsIgnoreCase("all");
                            if (allFrames) {
                                int total = session.translateAllFrames(mx, my, mz);
                                player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Moved <white>%d particles</white> across all frames by <white>X:%+.2f Y:%+.2f Z:%+.2f</white>!</#d8b4fe>", total, mx, my, mz)));
                            } else {
                                int moved = session.translateCurrentFrame(mx, my, mz);
                                player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Moved <white>%d particles</white> in Frame %d by <white>X:%+.2f Y:%+.2f Z:%+.2f</white>!</#d8b4fe>", moved, session.getCurrentFrame(), mx, my, mz)));
                            }
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Move offsets must be valid numbers.", NamedTextColor.RED));
                        }
                    }
                    case "motion", "vel", "velocity" -> {
                        if (args.length < 6) {
                            player.sendMessage(Component.text("Usage: /ca particle wand motion <vX> <vY> <vZ>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double vx = Double.parseDouble(args[3].replace("+", ""));
                            double vy = Double.parseDouble(args[4].replace("+", ""));
                            double vz = Double.parseDouble(args[5].replace("+", ""));
                            session.setVelocity(vx, vy, vz);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand trajectory velocity set to <white>X:%+.2f Y:%+.2f Z:%+.2f per frame</white></#d8b4fe>", vx, vy, vz)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Velocity values must be valid numbers.", NamedTextColor.RED));
                        }
                    }
                    case "ease", "easing" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand ease <linear|ease_in_cubic|ease_out_cubic|ease_out_back|...>", NamedTextColor.RED));
                            return;
                        }
                        com.churchsmp.particle.EasingType ease = com.churchsmp.particle.EasingType.fromString(args[3]);
                        session.setActiveEasing(ease);
                        player.sendMessage(mm.deserialize("<#d8b4fe>Wand easing curve set to <white>" + ease.name() + "</white></#d8b4fe>"));
                    }
                    case "speed", "duration" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand speed <ticks>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            int spd = Integer.parseInt(args[3]);
                            session.setActiveSpeed(spd);
                            player.sendMessage(mm.deserialize("<#d8b4fe>Wand duration set to <white>" + spd + " ticks</white></#d8b4fe>"));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid integer ticks: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "scale", "size" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand scale <multiplier>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            double scl = Double.parseDouble(args[3]);
                            session.setActiveScale(scl);
                            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand scale set to <white>%.2fx</white></#d8b4fe>", scl)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid scale: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "frame" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand frame <frame_number>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            int fNum = Integer.parseInt(args[3]);
                            session.setCurrentFrame(fNum);
                            player.sendMessage(mm.deserialize("<aqua>Timeline jump: <white>Frame " + session.getCurrentFrame() + "</white></aqua>"));
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid frame number: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "preview" -> {
                        plugin.getParticleAnimationManager().playPreview(player, session);
                    }
                    case "loop" -> {
                        plugin.getParticleAnimationManager().toggleLoop(player, session);
                    }
                    case "stop" -> {
                        session.stopLoop();
                        player.sendMessage(mm.deserialize("<gray>Loop preview stopped.</gray>"));
                    }
                    case "clear" -> {
                        if (args.length >= 4 && (args[3].equalsIgnoreCase("frame") || args[3].equalsIgnoreCase("current"))) {
                            if (session.clearCurrentFrame()) {
                                player.sendMessage(mm.deserialize("<#d8b4fe>Cleared all elements in <white>Frame " + session.getCurrentFrame() + "</white>.</#d8b4fe>"));
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
                            } else {
                                player.sendMessage(mm.deserialize("<gray>Frame " + session.getCurrentFrame() + " is already empty.</gray>"));
                            }
                        } else {
                            session.clearAnimation();
                            player.sendMessage(mm.deserialize("<#d8b4fe>Cleared all animation frames.</#d8b4fe>"));
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
                        }
                    }
                    case "copy" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand copy <from_frame_number>", NamedTextColor.RED));
                            return;
                        }
                        try {
                            int srcFrame = Integer.parseInt(args[3]);
                            int copied = session.copyFrame(srcFrame);
                            if (copied > 0) {
                                player.sendMessage(mm.deserialize("<#d8b4fe>Copied <white>" + copied + " elements</white> from <white>Frame " + srcFrame + "</white> into <white>Frame " + session.getCurrentFrame() + "</white>!</#d8b4fe>"));
                                com.churchsmp.particle.AnimationFrame cf = session.getAnimation().getFrames().get(session.getCurrentFrame());
                                if (cf != null) cf.spawn(player.getEyeLocation(), plugin);
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                            } else {
                                player.sendMessage(Component.text("Frame " + srcFrame + " does not exist or has no elements.", NamedTextColor.RED));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid frame number: " + args[3], NamedTextColor.RED));
                        }
                    }
                    case "rotate" -> {
                        handleRotationSubcommand(player, session, args, mm);
                    }
                    case "rot" -> {
                        if (args.length >= 6) {
                            handleRotationSubcommand(player, session, args, mm);
                        } else {
                            handleDecaySubcommand(player, session, args, mm);
                        }
                    }
                    case "rotting", "decay", "lifespan", "persist" -> {
                        handleDecaySubcommand(player, session, args, mm);
                    }
                    case "spin" -> {
                        handleSpinSubcommand(player, session, args, mm);
                    }
                    case "tween", "inbetween" -> {
                        if (args.length < 5) {
                            player.sendMessage(Component.text("Usage: /ca particle wand tween <from_frame> <to_frame> [easing]", NamedTextColor.RED));
                            return;
                        }
                        try {
                            int fromF = Integer.parseInt(args[3]);
                            int toF = Integer.parseInt(args[4]);
                            com.churchsmp.particle.EasingType ease = (args.length >= 6) ? com.churchsmp.particle.EasingType.fromString(args[5]) : session.getActiveEasing();

                            int generated = session.tweenFrames(fromF, toF, ease);
                            if (generated > 0) {
                                player.sendMessage(mm.deserialize("<#d8b4fe>Auto-Tweened <white>" + generated + " intermediate frames</white> between Frame " + fromF + " and Frame " + toF + " with <white>" + ease.name() + "</white> easing!</#d8b4fe>"));
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
                            } else {
                                player.sendMessage(Component.text("Failed to tween: Ensure both Frame " + fromF + " and Frame " + toF + " exist, have particles, and are separated by at least 1 frame.", NamedTextColor.RED));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Frame numbers must be valid integers.", NamedTextColor.RED));
                        }
                    }
                    case "record", "rec" -> {
                        int duration = 20;
                        double dist = 2.5;
                        if (args.length >= 4) {
                            try { duration = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                        }
                        if (args.length >= 5) {
                            try { dist = Double.parseDouble(args[4]); } catch (NumberFormatException ignored) {}
                        }
                        plugin.getParticleAnimationManager().startMotionRecording(player, session, duration, dist);
                    }
                    case "undo" -> {
                        if (session.undo()) {
                            player.sendMessage(mm.deserialize("<gold>Undo successful. <gray>(Restored previous state)</gray></gold>"));
                        } else {
                            player.sendMessage(mm.deserialize("<gray>Nothing to undo.</gray>"));
                        }
                    }
                    case "redo" -> {
                        if (session.redo()) {
                            player.sendMessage(mm.deserialize("<gold>Redo successful. <gray>(Restored undone state)</gray></gold>"));
                        } else {
                            player.sendMessage(mm.deserialize("<gray>Nothing to redo.</gray>"));
                        }
                    }
                    case "export" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand export <name>", NamedTextColor.RED));
                            return;
                        }
                        String animName = args[3].toLowerCase(Locale.ROOT);
                        session.getAnimation().setName(animName);
                        plugin.getParticleAnimationManager().saveAnimationToConfig(session.getAnimation());
                        String portable = session.getAnimation().toPortableString();
                        player.sendMessage(mm.deserialize("<#d8b4fe>Exported animation <white>" + animName + "</white> to animations.yml!</#d8b4fe>"));
                        player.sendMessage(mm.deserialize("<gray>Portable Pattern: <click:copy_to_clipboard:'" + portable + "'><hover:show_text:'<gray>Click to copy</gray>'><white>" + portable + "</white></hover></click></gray>"));
                    }
                    case "import" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand import <pattern_string>", NamedTextColor.RED));
                            return;
                        }
                        String patternStr = args[3];
                        com.churchsmp.particle.ParticleAnimation imported = com.churchsmp.particle.ParticleAnimation.fromPortableString(patternStr);
                        if (imported != null) {
                            session.setAnimation(imported);
                            player.sendMessage(mm.deserialize("<#d8b4fe>Imported animation <white>" + imported.getName() + "</white> (" + imported.getFrames().size() + " frames) into wand session!</#d8b4fe>"));
                        } else {
                            player.sendMessage(Component.text("Failed to parse pattern string! Must start with PAT:...", NamedTextColor.RED));
                        }
                    }
                    case "template", "animtemplate" -> {
                        if (args.length < 4) {
                            player.sendMessage(Component.text("Usage: /ca particle wand template <slash|shockwave|vortex|thrust|slam|cross|orbit>", NamedTextColor.RED));
                            return;
                        }
                        com.churchsmp.particle.AnimationTemplate template = com.churchsmp.particle.AnimationTemplate.fromString(args[3]);
                        session.applyTemplate(template);
                        if (!session.isLooping()) {
                            plugin.getParticleAnimationManager().toggleLoop(player, session);
                        }
                        player.sendMessage(mm.deserialize("<#d8b4fe>Applied Template: <white>" + template.getDisplayName() + "</white> <gray>(" + template.getFrameCount() + " frames)</gray>! Auto-looping in 3D preview.</#d8b4fe>"));
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
                    }
                    default -> player.sendMessage(Component.text("Unknown wand command. Use: set, preset, y, ease, speed, scale, frame, copy, rotate, template, clear, preview, loop, stop, undo, redo, export, import", NamedTextColor.RED));
                }
            }
            case "template" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can apply templates.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /ca particle template <slash|shockwave|vortex|thrust|slam|cross|orbit>", NamedTextColor.RED));
                    return;
                }
                com.churchsmp.particle.WandSession session = plugin.getParticleAnimationManager().getSession(player);
                com.churchsmp.particle.AnimationTemplate template = com.churchsmp.particle.AnimationTemplate.fromString(args[2]);
                session.applyTemplate(template);
                if (!session.isLooping()) {
                    plugin.getParticleAnimationManager().toggleLoop(player, session);
                }
                player.sendMessage(mm.deserialize("<#d8b4fe>Applied Template: <white>" + template.getDisplayName() + "</white> <gray>(" + template.getFrameCount() + " frames)</gray>! Auto-looping in 3D preview.</#d8b4fe>"));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
            }
            default -> sender.sendMessage(Component.text("Usage: /ca particle <wand|play|template|reload>", NamedTextColor.RED));
        }
    }

    private void handleRotationSubcommand(Player player, com.churchsmp.particle.WandSession session, String[] args, net.kyori.adventure.text.minimessage.MiniMessage mm) {
        if (args.length < 6) {
            player.sendMessage(Component.text("Usage: /ca particle wand rotate <X_pitch> <Y_yaw> <Z_roll> [frame]", NamedTextColor.RED));
            return;
        }
        try {
            double rx = Double.parseDouble(args[3]);
            double ry = Double.parseDouble(args[4]);
            double rz = Double.parseDouble(args[5]);
            boolean applyToFrame = (args.length >= 7 && args[6].equalsIgnoreCase("frame"));

            session.setRotation(rx, ry, rz);
            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Wand rotation set to <white>Pitch: %.1f° | Yaw: %.1f° | Roll: %.1f°</white></#d8b4fe>", rx, ry, rz)));

            if (applyToFrame || (session.getAnimation().getFrames().containsKey(session.getCurrentFrame()) && !session.getAnimation().getFrames().get(session.getCurrentFrame()).getElements().isEmpty())) {
                int rotCount = session.rotateCurrentFrame(rx, ry, rz);
                if (rotCount > 0) {
                    player.sendMessage(mm.deserialize("<#d8b4fe>Rotated <white>" + rotCount + " elements</white> in <white>Frame " + session.getCurrentFrame() + "</white>!</#d8b4fe>"));
                    com.churchsmp.particle.AnimationFrame cf = session.getAnimation().getFrames().get(session.getCurrentFrame());
                    if (cf != null) cf.spawn(player.getEyeLocation(), plugin);
                }
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid rotation angles! Must be numbers.", NamedTextColor.RED));
        }
    }

    private void handleDecaySubcommand(Player player, com.churchsmp.particle.WandSession session, String[] args, net.kyori.adventure.text.minimessage.MiniMessage mm) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /ca particle wand decay <frames|apply|fade> [all/frame|true/false]", NamedTextColor.RED));
            return;
        }
        try {
            int frames = Integer.parseInt(args[3]);
            session.setActiveLifespan(frames);
            player.sendMessage(mm.deserialize("<#d8b4fe>Rotting / Decay lifespan set to <white>" + frames + " frames</white> <gray>(particles persist for " + frames + " frames then disappear)</gray>.</#d8b4fe>"));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        } catch (NumberFormatException e) {
            if (args[3].equalsIgnoreCase("fade") || args[3].equalsIgnoreCase("shrink")) {
                boolean toggle = (args.length >= 5) ? Boolean.parseBoolean(args[4]) : !session.isDecayShrink();
                session.setDecayShrink(toggle);
                player.sendMessage(mm.deserialize("<#d8b4fe>Decay shrink/fade set to <white>" + toggle + "</white>.</#d8b4fe>"));
            } else if (args[3].equalsIgnoreCase("apply")) {
                boolean all = (args.length < 5 || !args[4].equalsIgnoreCase("frame"));
                if (all) {
                    int added = session.applyDecayToAllFrames();
                    player.sendMessage(mm.deserialize("<#d8b4fe>Applied decay across ALL frames: generated <white>" + added + " trailing points</white> (" + session.getActiveLifespan() + " frames lifespan).</#d8b4fe>"));
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
                } else {
                    int added = session.applyDecayToCurrentFrame();
                    player.sendMessage(mm.deserialize("<#d8b4fe>Applied decay to Frame " + session.getCurrentFrame() + ": generated <white>" + added + " trailing points</white> (" + session.getActiveLifespan() + " frames lifespan).</#d8b4fe>"));
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                }
            } else {
                player.sendMessage(Component.text("Invalid decay parameter: " + args[3] + ". Use <number>, 'apply [all|frame]', or 'fade [true|false]'.", NamedTextColor.RED));
            }
        }
    }

    private void handleSpinSubcommand(Player player, com.churchsmp.particle.WandSession session, String[] args, net.kyori.adventure.text.minimessage.MiniMessage mm) {
        if (args.length < 6) {
            player.sendMessage(Component.text("Usage: /ca particle wand spin <X_delta> <Y_delta> <Z_delta>", NamedTextColor.RED));
            return;
        }
        try {
            double sx = Double.parseDouble(args[3]);
            double sy = Double.parseDouble(args[4]);
            double sz = Double.parseDouble(args[5]);
            session.setSpin(sx, sy, sz);
            player.sendMessage(mm.deserialize(String.format(Locale.ROOT, "<#d8b4fe>Frame spin delta set to <white>ΔX: %.1f° | ΔY: %.1f° | ΔZ: %.1f° per frame</white></#d8b4fe>", sx, sy, sz)));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid spin angles! Must be numbers.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        sender.sendMessage(mm.deserialize(com.churchsmp.util.TextUtil.formatCommandHeader("CHURCHADMIN COMMANDS")));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("particle") + " wand</bold></gold> <white>-</white> <gray>In-Game Particle Animation & Math VFX Studio Wand</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("set") + " blacksun</bold></gold> <white>-</white> <gray>Sets current location as Black Sun & Ceremony center</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("start") + "</bold></gold> <white>-</white> <gray>Initiates collective Genesis Ceremony (25-block border, Y:300 glide)</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("blacksun") + " <start|stop></bold></gold> <white>-</white> <gray>Summons or dismisses rotating Celestial Black Sun</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("purge") + " quest <start|stop> [player]</bold></gold> <white>-</white> <gray>Starts or stops Juggernaut Purge Quest</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("immersive") + " <shake|vintage|vignette|flash|strobe|letterbox|clear></bold></gold> <white>-</white> <gray>Immersive VFX hub</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("shake") + " <1-5> [duration] [player]</bold></gold> <white>-</white> <gray>Screen shaking with hardness levels 1-5</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("fx") + " <sin> [player]</bold></gold> <white>-</white> <gray>Triggers native vintage Sin screen flash & sound FX</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("vintage") + " <sepia|noir|blood|frost|solar|void|neon> [player]</bold></gold> <white>-</white> <gray>Vintage film wash & dissolve</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("vignette") + " <red|frost|dark|clear> [player] [ticks]</bold></gold> <white>-</white> <gray>Applies native screen vignettes</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("flash") + " <#hex> [inMs] [stayMs] [outMs] [player]</bold></gold> <white>-</white> <gray>Custom chromatic flash & fade</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("null") + " <enchant|border|potions|player></bold></gold> <white>-</white> <gray>Controls Liminal Null PvP and Player systems</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("menu") + "</bold></gold> <white>-</white> <gray>Opens the interactive Legendary Weapon menu</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("givecore") + " [player]</bold></gold> <white>-</white> <gray>Gives a Liminal Event Core</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("giverelic") + " <player> <type></bold></gold> <white>-</white> <gray>Gives a Liminal Path relic selector (iniquity, impiety, obscura)</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("forsake") + " [player]</bold></gold> <white>-</white> <gray>Triggers the 360° Forsaking Path Ritual</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("event") + " <bloodmoon|inquisition|stop></bold></gold> <white>-</white> <gray>Controls automated server events</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("giveimpiety") + " [player]</bold></gold> <white>-</white> <gray>Gives the Impiety right-click menu item</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("resetcooldown") + " [player]</bold></gold> <white>-</white> <gray>Resets all ability cooldowns</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("setalignment") + " <player> <alignment></bold></gold> <white>-</white> <gray>Sets player's alignment (GOOD, EVIL, NULLIFIED)</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("giveweapon") + " <player> <weapon_id></bold></gold> <white>-</white> <gray>Spawns a legendary weapon</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("givegem") + " <player> <gem_name></bold></gold> <white>-</white> <gray>Spawns a Sin Relic gem</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("purge") + " <player|clear></bold></gold> <white>-</white> <gray>Initiates or clears the ChurchSMP Finale Purge</gray>"));
        sender.sendMessage(mm.deserialize("  <gold><bold>/churchadmin " + com.churchsmp.util.TextUtil.toSmallCaps("reload") + "</bold></gold> <white>-</white> <gray>Reloads configuration file</gray>"));
        sender.sendMessage(mm.deserialize("<gold>══════════════════════════════════════════</gold>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("abyss", "particle", "set", "immersive", "shake", "menu", "start", "blacksun", "fx", "vintage", "vignette", "flash", "null", "givecore", "giverelic", "forsake", "event", "giveimpiety", "resetcooldown", "setalignment", "giveweapon", "givegem", "purge", "reload", "help"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("particle")) {
                return filter(List.of("gui", "wand", "play", "reload", "template"), args[1]);
            }
            if (args[0].equalsIgnoreCase("abyss")) {
                return filter(List.of("wand", "set", "reset", "climax"), args[1]);
            }
            if (args[0].equalsIgnoreCase("set")) {
                return filter(List.of("wand", "blacksun"), args[1]);
            }
            if (args[0].equalsIgnoreCase("immersive")) {
                return filter(List.of("shake", "vintage", "vignette", "flash", "strobe", "letterbox", "clear"), args[1]);
            }
            if (args[0].equalsIgnoreCase("shake")) {
                return filter(List.of("1", "2", "3", "4", "5"), args[1]);
            }
            if (args[0].equalsIgnoreCase("blacksun")) {
                return filter(List.of("start", "stop"), args[1]);
            }
            if (args[0].equalsIgnoreCase("fx")) {
                return filter(Arrays.stream(SinGemType.values()).map(Enum::name).toList(), args[1]);
            }
            if (args[0].equalsIgnoreCase("vintage")) {
                return filter(List.of("sepia", "noir", "blood", "frost", "solar", "void", "neon"), args[1]);
            }
            if (args[0].equalsIgnoreCase("vignette")) {
                return filter(List.of("red", "frost", "dark", "clear"), args[1]);
            }
            if (args[0].equalsIgnoreCase("flash")) {
                return filter(List.of("#d4a373", "#ff1a1a", "#00e5ff", "#ffb703", "#aa22ff", "#ffffff"), args[1]);
            }
            if (args[0].equalsIgnoreCase("start")) {
                return filter(List.of("force"), args[1]);
            }
            if (args[0].equalsIgnoreCase("null")) {
                return filter(List.of("enchant", "border", "potions", "player"), args[1]);
            }
            if (args[0].equalsIgnoreCase("event")) {
                return filter(List.of("bloodmoon", "inquisition", "stop"), args[1]);
            }
            if (args[0].equalsIgnoreCase("purge")) {
                List<String> options = new ArrayList<>();
                options.add("quest");
                options.add("clear");
                options.add("spawn_altar");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
                return filter(options, args[1]);
            }
            return null; // suggest player names
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("particle")) {
                if (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("want")) {
                    return filter(List.of("gui", "set", "preset", "pos", "x", "y", "z", "move", "motion", "ease", "speed", "scale", "frame", "copy", "rotate", "rot", "decay", "lifespan", "spin", "tween", "template", "record", "clear", "preview", "loop", "stop", "undo", "redo", "export", "import", "+0.5", "+1.0", "-0.5"), args[2]);
                }
                if (args[1].equalsIgnoreCase("template")) {
                    return filter(List.of("slash", "shockwave", "vortex", "thrust", "slam", "cross", "orbit"), args[2]);
                }
                if (args[1].equalsIgnoreCase("play")) {
                    return filter(new ArrayList<>(plugin.getParticleAnimationManager().getSavedAnimationNames()), args[2]);
                }
            }
            if (args[0].equalsIgnoreCase("abyss") && args[1].equalsIgnoreCase("set")) {
                return filter(List.of("main", "entrance", "wrath", "greed", "gluttony", "lust", "envy", "pride", "sloth"), args[2]);
            }
            if (args[0].equalsIgnoreCase("purge") && args[1].equalsIgnoreCase("quest")) {
                return filter(List.of("start", "stop"), args[2]);
            }
            if (args[0].equalsIgnoreCase("immersive")) {
                if (args[1].equalsIgnoreCase("shake")) return filter(List.of("1", "2", "3", "4", "5"), args[2]);
                if (args[1].equalsIgnoreCase("vintage")) return filter(List.of("sepia", "noir", "blood", "frost", "solar", "void", "neon"), args[2]);
                if (args[1].equalsIgnoreCase("vignette")) return filter(List.of("red", "frost", "dark", "clear"), args[2]);
                if (args[1].equalsIgnoreCase("flash") || args[1].equalsIgnoreCase("strobe")) return filter(List.of("#d4a373", "#ff1a1a", "#00e5ff", "#ffb703", "#aa22ff", "#ffffff"), args[2]);
                if (args[1].equalsIgnoreCase("clear")) return null;
            }
            if (args[0].equalsIgnoreCase("shake")) {
                return filter(List.of("20", "40", "60", "100"), args[2]);
            }
            if (args[0].equalsIgnoreCase("fx") || args[0].equalsIgnoreCase("vintage") || args[0].equalsIgnoreCase("vignette")) {
                return null; // suggest player names
            }
            if (args[0].equalsIgnoreCase("null") && args[1].equalsIgnoreCase("player")) {
                return null; // suggest player names
            }
            if (args[0].equalsIgnoreCase("null") && args[1].equalsIgnoreCase("border")) {
                return filter(List.of("500", "1000", "5000", "10000", "15000", "20000", "25000", "30000"), args[2]);
            }
            if (args[0].equalsIgnoreCase("giverelic")) {
                List<String> options = new ArrayList<>();
                options.add("all");
                for (com.churchsmp.item.RelicItem.RelicType t : com.churchsmp.item.RelicItem.RelicType.values()) {
                    options.add(t.name().toLowerCase(Locale.ROOT));
                }
                return filter(options, args[2]);
            }
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
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("particle") && (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("want"))) {
                if (args[2].equalsIgnoreCase("set")) {
                    return filter(List.of("dustzen", "dustred", "dustwhite", "dustgold", "dustblack", "dustcyan", "dustgreen", "dustorange", "dustpink", "dustblue", "fireworkexplosion", "sweep", "flash", "sonicboom", "endrod", "soulflame", "flame", "gust", "electric", "redstoneblockdisplay", "blockdisplay:amethyst_block", "itemdisplay:amethyst_shard"), args[3]);
                }
                if (args[2].equalsIgnoreCase("present") || args[2].equalsIgnoreCase("preset")) {
                    return filter(List.of("point", "star_4", "star_8", "hypocycloid", "ring", "slash", "helix", "spiral", "lissajous", "rose", "bolt", "bolt_veins"), args[3]);
                }
                if (args[2].equalsIgnoreCase("template") || args[2].equalsIgnoreCase("animtemplate")) {
                    return filter(List.of("slash", "shockwave", "vortex", "thrust", "slam", "cross", "orbit"), args[3]);
                }
                if (args[2].equalsIgnoreCase("ease") || args[2].equalsIgnoreCase("easing")) {
                    return filter(List.of("linear", "ease_in_sine", "ease_out_sine", "ease_in_cubic", "ease_out_cubic", "ease_out_expo", "ease_out_back", "ease_out_elastic", "ease_out_bounce"), args[3]);
                }
                if (args[2].equalsIgnoreCase("x") || args[2].equalsIgnoreCase("y") || args[2].equalsIgnoreCase("z") || args[2].equalsIgnoreCase("dist") || args[2].equalsIgnoreCase("height") || args[2].equalsIgnoreCase("yoffset")) {
                    return filter(List.of("+0.5", "+1.0", "+1.5", "+2.0", "+3.0", "+5.0", "-0.5", "-1.0", "0.0"), args[3]);
                }
                if (args[2].equalsIgnoreCase("pos") || args[2].equalsIgnoreCase("position") || args[2].equalsIgnoreCase("coords") || args[2].equalsIgnoreCase("move") || args[2].equalsIgnoreCase("translate")) {
                    return filter(List.of("0", "1", "2", "3", "5", "-1", "-2"), args[3]);
                }
                if (args[2].equalsIgnoreCase("motion") || args[2].equalsIgnoreCase("vel")) {
                    return filter(List.of("0", "0.5", "1.0", "2.0"), args[3]);
                }
                if (args[2].equalsIgnoreCase("speed") || args[2].equalsIgnoreCase("duration")) {
                    return filter(List.of("5", "10", "15", "20", "40"), args[3]);
                }
                if (args[2].equalsIgnoreCase("scale") || args[2].equalsIgnoreCase("size")) {
                    return filter(List.of("0.5", "1.0", "1.5", "2.0", "3.0"), args[3]);
                }
                if (args[2].equalsIgnoreCase("clear")) {
                    return filter(List.of("all", "frame"), args[3]);
                }
                if (args[2].equalsIgnoreCase("copy")) {
                    return filter(List.of("0", "1", "2", "3", "4"), args[3]);
                }
                if (args[2].equalsIgnoreCase("rotate") || args[2].equalsIgnoreCase("rot")) {
                    return filter(List.of("1", "2", "3", "0", "45", "90", "180", "-45", "-90"), args[3]);
                }
                if (args[2].equalsIgnoreCase("decay") || args[2].equalsIgnoreCase("lifespan") || args[2].equalsIgnoreCase("rotting") || args[2].equalsIgnoreCase("persist")) {
                    return filter(List.of("1", "2", "3", "4", "5", "8", "apply", "fade", "shrink"), args[3]);
                }
                if (args[2].equalsIgnoreCase("spin")) {
                    return filter(List.of("0", "15", "30", "45", "90", "-15", "-30", "-45"), args[3]);
                }
                if (args[2].equalsIgnoreCase("tween") || args[2].equalsIgnoreCase("inbetween")) {
                    return filter(List.of("0", "1", "2", "3", "4", "5"), args[3]);
                }
                if (args[2].equalsIgnoreCase("record") || args[2].equalsIgnoreCase("rec")) {
                    return filter(List.of("10", "20", "30", "40", "60"), args[3]);
                }
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("wand")) {
                if (args[2].equalsIgnoreCase("decay") && args[3].equalsIgnoreCase("apply")) {
                    return filter(List.of("all", "frame"), args[4]);
                }
            }
            if (args[0].equalsIgnoreCase("purge") && args[1].equalsIgnoreCase("quest") && args[2].equalsIgnoreCase("start")) {
                return null; // suggest player names
            }
            if (args[0].equalsIgnoreCase("immersive") && args[1].equalsIgnoreCase("shake")) {
                return filter(List.of("20", "40", "60", "100"), args[3]);
            }
            if (args[0].equalsIgnoreCase("immersive") && (args[1].equalsIgnoreCase("vintage") || args[1].equalsIgnoreCase("vignette"))) {
                return null; // suggest player names
            }
        }
        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("particle") && (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("want"))) {
                if (args[2].equalsIgnoreCase("pos") || args[2].equalsIgnoreCase("position") || args[2].equalsIgnoreCase("coords") || args[2].equalsIgnoreCase("move") || args[2].equalsIgnoreCase("translate")) {
                    return filter(List.of("0", "1", "2", "3", "5", "-1", "-2"), args[4]);
                }
                if (args[2].equalsIgnoreCase("motion") || args[2].equalsIgnoreCase("vel")) {
                    return filter(List.of("0", "0.5", "1.0", "2.0"), args[4]);
                }
                if (args[2].equalsIgnoreCase("rotate") || args[2].equalsIgnoreCase("rot")) {
                    return filter(List.of("0", "45", "90", "180", "-45", "-90"), args[4]);
                }
                if (args[2].equalsIgnoreCase("spin")) {
                    return filter(List.of("0", "15", "30", "45", "90", "-15", "-30", "-45"), args[4]);
                }
                if (args[2].equalsIgnoreCase("tween") || args[2].equalsIgnoreCase("inbetween")) {
                    return filter(List.of("3", "5", "8", "10", "15", "20"), args[4]);
                }
                if (args[2].equalsIgnoreCase("record") || args[2].equalsIgnoreCase("rec")) {
                    return filter(List.of("1.5", "2.0", "2.5", "3.0", "4.0"), args[4]);
                }
            }
        }
        if (args.length == 6) {
            if (args[0].equalsIgnoreCase("particle") && (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("want"))) {
                if (args[2].equalsIgnoreCase("pos") || args[2].equalsIgnoreCase("position") || args[2].equalsIgnoreCase("coords") || args[2].equalsIgnoreCase("move") || args[2].equalsIgnoreCase("translate")) {
                    return filter(List.of("0", "1", "2", "3", "5", "-1", "-2"), args[5]);
                }
                if (args[2].equalsIgnoreCase("motion") || args[2].equalsIgnoreCase("vel")) {
                    return filter(List.of("0", "0.5", "1.0", "2.0"), args[5]);
                }
                if (args[2].equalsIgnoreCase("rotate") || args[2].equalsIgnoreCase("rot")) {
                    return filter(List.of("0", "45", "90", "180", "-45", "-90"), args[5]);
                }
                if (args[2].equalsIgnoreCase("spin")) {
                    return filter(List.of("0", "15", "30", "45", "90", "-15", "-30", "-45"), args[5]);
                }
                if (args[2].equalsIgnoreCase("tween") || args[2].equalsIgnoreCase("inbetween")) {
                    return filter(List.of("linear", "ease_in_sine", "ease_out_sine", "ease_in_cubic", "ease_out_cubic", "ease_out_expo", "ease_out_back", "ease_out_elastic", "ease_out_bounce"), args[5]);
                }
            }
        }
        if (args.length == 7) {
            if (args[0].equalsIgnoreCase("particle") && (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("want"))) {
                if (args[2].equalsIgnoreCase("move") || args[2].equalsIgnoreCase("translate")) {
                    return filter(List.of("frame", "all"), args[6]);
                }
                if (args[2].equalsIgnoreCase("rotate") || args[2].equalsIgnoreCase("rot")) {
                    return filter(List.of("frame"), args[6]);
                }
            }
        }
        return List.of();
    }

    private void giveAllRelics(Player target, CommandSender sender) {
        // 1. Path Relics
        for (com.churchsmp.item.RelicItem.RelicType type : com.churchsmp.item.RelicItem.RelicType.values()) {
            target.getInventory().addItem(com.churchsmp.item.RelicItem.createRelic(plugin, type));
        }
        // 2. All 8 Sin Gems (Wrath, Greed, Gluttony, Lust, Envy, Pride, Sloth, and Master SIN!)
        for (SinGemType sin : SinGemType.values()) {
            target.getInventory().addItem(plugin.getSinGemManager().createGemItem(sin));
        }
        // 3. Liminal Event Core
        target.getInventory().addItem(com.churchsmp.item.LiminalCoreItem.createCore(plugin));

        sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<green>✦ Successfully gave <gold><bold>ALL RELICS & SIN GEMS</bold></gold> to <aqua>" + target.getName() + "</aqua>!</green>"
        ));
        target.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<gold><bold>✦ YOU HAVE RECEIVED ALL RELICS, SIN GEMS, AND EVENT CORES! ✦</bold></gold>"
        ));
        target.playSound(target.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
    }

    private List<String> filter(List<String> list, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
