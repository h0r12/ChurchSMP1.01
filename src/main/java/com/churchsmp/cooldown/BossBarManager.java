package com.churchsmp.cooldown;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {

    private final ChurchSMP plugin;
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    public BossBarManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Shows a colored boss bar for an active ability countdown.
     */
    public void showActiveCountdown(Player player, String abilityName, BossBar.Color color, int totalDurationSeconds) {
        if (!plugin.getConfig().getBoolean("settings.bossbar_enabled", true)) {
            return;
        }

        // Hide any existing bar for this player
        removeBossBar(player);

        BossBar bar = BossBar.bossBar(
                Component.text(abilityName + " Active", NamedTextColor.GOLD),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );

        player.showBossBar(bar);
        activeBars.put(player.getUniqueId(), bar);

        new BukkitRunnable() {
            int ticksLeft = totalDurationSeconds * 20;
            final int initialTicks = ticksLeft;

            @Override
            public void run() {
                if (!player.isOnline() || ticksLeft <= 0) {
                    player.hideBossBar(bar);
                    activeBars.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                float progress = Math.max(0.0f, Math.min(1.0f, (float) ticksLeft / initialTicks));
                bar.progress(progress);
                bar.name(Component.text(abilityName + " (", NamedTextColor.GOLD)
                        .append(Component.text(String.format(java.util.Locale.US, "%.1fs", ticksLeft / 20.0), NamedTextColor.YELLOW))
                        .append(Component.text(")", NamedTextColor.GOLD)));

                ticksLeft -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void removeBossBar(Player player) {
        BossBar existing = activeBars.remove(player.getUniqueId());
        if (existing != null) {
            player.hideBossBar(existing);
        }
    }
}
