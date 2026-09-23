package com.churchsmp.cooldown;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

public class ActionBarCooldownTask extends BukkitRunnable {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ActionBarCooldownTask(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // "action bar cooldown to weapons theme color with READY! to white with small caps"
        final String readyStr = "<white><bold>ʀᴇᴀᴅʏ!</bold></white>";

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);

            if (weapon == null) {
                continue;
            }

            String grad = weapon.getThemeGradientTag();

            // Skill 1 Status
            String s1Key = weapon.getId() + "_primary";
            String s1Name = grad + "<bold>" + TextUtil.toSmallCaps(weapon.getPrimaryAbilityName()) + "</bold></gradient>";
            String s1Status;
            String customS1 = weapon.getCustomActiveStatus(player, false);
            if (customS1 != null) {
                s1Status = grad + "<bold>" + customS1 + "</bold></gradient>";
            } else if (plugin.getCooldownManager().isActive(player, s1Key)) {
                double rem = plugin.getCooldownManager().getActiveRemainingSeconds(player, s1Key);
                s1Status = grad + "<bold>ᴀᴄᴛɪᴠᴇ (" + String.format(Locale.US, "%.1f", rem) + "ꜱ)</bold></gradient>";
            } else if (plugin.getCooldownManager().isOnCooldown(player, s1Key)) {
                double rem = plugin.getCooldownManager().getRemainingCooldownSeconds(player, s1Key);
                s1Status = grad + "<bold>" + String.format(Locale.US, "%.1f", rem) + "ꜱ</bold></gradient>";
            } else {
                s1Status = readyStr;
            }

            // Skill 2 Status
            String s2Key = weapon.getId() + "_secondary";
            String s2Name = grad + "<bold>" + TextUtil.toSmallCaps(weapon.getSecondaryAbilityName()) + "</bold></gradient>";
            String s2Status;
            String customS2 = weapon.getCustomActiveStatus(player, true);
            if (customS2 != null) {
                s2Status = grad + "<bold>" + customS2 + "</bold></gradient>";
            } else if (plugin.getCooldownManager().isActive(player, s2Key)) {
                double rem = plugin.getCooldownManager().getActiveRemainingSeconds(player, s2Key);
                s2Status = grad + "<bold>ᴀᴄᴛɪᴠᴇ (" + String.format(Locale.US, "%.1f", rem) + "ꜱ)</bold></gradient>";
            } else if (plugin.getCooldownManager().isOnCooldown(player, s2Key)) {
                double rem = plugin.getCooldownManager().getRemainingCooldownSeconds(player, s2Key);
                s2Status = grad + "<bold>" + String.format(Locale.US, "%.1f", rem) + "ꜱ</bold></gradient>";
            } else {
                s2Status = readyStr;
            }

            String formatted = "<dark_gray>[ " + s1Name + "<gray>: " + s1Status + " <dark_gray>] <gray>| <dark_gray>[ " + s2Name + "<gray>: " + s2Status + " <dark_gray>]";
            Component component = miniMessage.deserialize(formatted);
            player.sendActionBar(component);
        }
    }
}
