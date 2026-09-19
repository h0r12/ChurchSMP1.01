package com.churchsmp.cooldown;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionBarCooldownTask extends BukkitRunnable {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ActionBarCooldownTask(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String format = plugin.getConfig().getString("settings.action_bar_format",
                "<gray>[<gold>%skill1_name%: %skill1_status%<gray>] <dark_gray>| <gray>[<gold>%skill2_name%: %skill2_status%<gray>]");

        String readyStr = plugin.getConfig().getString("settings.status_ready", "<green><bold>Ready!</bold></green>");
        String activeStr = plugin.getConfig().getString("settings.status_active", "<aqua><bold>Active (%time%s)</bold></aqua>");
        String cdStr = plugin.getConfig().getString("settings.status_cooldown", "<red><bold>%time%s</bold></red>");

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);

            if (weapon == null) {
                // Not holding a legendary weapon; nothing displayed on action bar.
                continue;
            }

            // Skill 1 Status
            String s1Key = weapon.getId() + "_primary";
            String s1Status;
            if (plugin.getCooldownManager().isActive(player, s1Key)) {
                double rem = plugin.getCooldownManager().getActiveRemainingSeconds(player, s1Key);
                s1Status = activeStr.replace("%time%", String.format(java.util.Locale.US, "%.1f", rem));
            } else if (plugin.getCooldownManager().isOnCooldown(player, s1Key)) {
                double rem = plugin.getCooldownManager().getRemainingCooldownSeconds(player, s1Key);
                s1Status = cdStr.replace("%time%", String.format(java.util.Locale.US, "%.1f", rem));
            } else {
                s1Status = readyStr;
            }

            // Skill 2 Status
            String s2Key = weapon.getId() + "_secondary";
            String s2Status;
            if (plugin.getCooldownManager().isActive(player, s2Key)) {
                double rem = plugin.getCooldownManager().getActiveRemainingSeconds(player, s2Key);
                s2Status = activeStr.replace("%time%", String.format(java.util.Locale.US, "%.1f", rem));
            } else if (plugin.getCooldownManager().isOnCooldown(player, s2Key)) {
                double rem = plugin.getCooldownManager().getRemainingCooldownSeconds(player, s2Key);
                s2Status = cdStr.replace("%time%", String.format(java.util.Locale.US, "%.1f", rem));
            } else {
                s2Status = readyStr;
            }

            String parsed = format
                    .replace("%skill1_name%", weapon.getPrimaryAbilityName())
                    .replace("%skill1_status%", s1Status)
                    .replace("%skill2_name%", weapon.getSecondaryAbilityName())
                    .replace("%skill2_status%", s2Status);

            Component component = miniMessage.deserialize(parsed);
            player.sendActionBar(component);
        }
    }
}
