package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Shows a depleting boss bar for the duration of a cooldown so the player
 * can actually see how much longer they have to wait, instead of only
 * finding out via an action-bar message when they try (and fail) to use
 * the ability again.
 */
public class CooldownBarDisplay {

    public static void show(ChurchSMP plugin, Player player, String abilityLabel, int totalSeconds) {
        if (totalSeconds <= 0) return;

        BossBar bar = Bukkit.createBossBar(abilityLabel + " — " + totalSeconds + "s", BarColor.PURPLE, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(1.0);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed++;
                int remaining = totalSeconds - elapsed;
                if (remaining <= 0 || !player.isOnline()) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0, (double) remaining / totalSeconds));
                bar.setTitle(abilityLabel + " — " + remaining + "s");
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
