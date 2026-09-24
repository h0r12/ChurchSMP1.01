package com.churchsmp.cooldown;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Map: Player UUID -> Map of (BarKey -> BossBar)
    private final Map<UUID, Map<String, BossBar>> playerBars = new ConcurrentHashMap<>();

    public BossBarManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Shows a colored boss bar for an active ability countdown with small caps and weapon theme gradient.
     */
    public void showActiveCountdown(Player player, String abilityName, BossBar.Color color, int totalDurationSeconds) {
        showActiveCountdown(player, abilityName, "<gradient:#FFFFFF:#FFD700>", color, totalDurationSeconds);
    }

    public void showActiveCountdown(Player player, LegendaryWeapon weapon, String abilityName, int totalDurationSeconds) {
        showActiveCountdown(player, abilityName, weapon.getThemeGradientTag(), weapon.getThemeBossBarColor(), totalDurationSeconds);
    }

    public void showActiveCountdown(Player player, String abilityName, String gradientTag, BossBar.Color color, int totalDurationSeconds) {
        if (!plugin.getConfig().getBoolean("settings.bossbar_enabled", true)) return;

        String key = "active_" + abilityName.toLowerCase(Locale.ROOT);
        removeBar(player, key);

        String smallName = TextUtil.toSmallCaps(abilityName);
        Component initialTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + totalDurationSeconds + "ꜱ</bold></gradient>");

        BossBar bar = BossBar.bossBar(initialTitle, 1.0f, color, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        playerBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, bar);

        new BukkitRunnable() {
            int ticksLeft = totalDurationSeconds * 20;
            final int initialTicks = ticksLeft;

            @Override
            public void run() {
                if (!player.isOnline() || ticksLeft <= 0) {
                    player.hideBossBar(bar);
                    removeBar(player, key);
                    cancel();
                    return;
                }

                float progress = Math.max(0.0f, Math.min(1.0f, (float) ticksLeft / initialTicks));
                bar.progress(progress);

                double secondsLeft = ticksLeft / 20.0;
                String timeFormatted = String.format(Locale.US, "%.1f", secondsLeft);
                Component updatedTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + timeFormatted + "ꜱ</bold></gradient>");
                bar.name(updatedTitle);

                ticksLeft -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Shows a boss bar for passive ability cooldown countdowns:
     * e.g., "ꜱᴏᴜʟᴛᴀᴋɪɴɢ | 10ꜱ" with weapon theme color in small caps font.
     */
    public void showPassiveCooldown(Player player, LegendaryWeapon weapon, String passiveName, int totalSeconds) {
        if (!plugin.getConfig().getBoolean("settings.bossbar_enabled", true)) return;

        String key = "passive_" + passiveName.toLowerCase(Locale.ROOT);
        removeBar(player, key);

        String gradientTag = weapon != null ? weapon.getThemeGradientTag() : "<gradient:#FFFFFF:#FFD700>";
        BossBar.Color color = weapon != null ? weapon.getThemeBossBarColor() : BossBar.Color.WHITE;
        String smallName = TextUtil.toSmallCaps(passiveName);

        Component initialTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + totalSeconds + "ꜱ</bold></gradient>");
        BossBar bar = BossBar.bossBar(initialTitle, 1.0f, color, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        playerBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, bar);

        new BukkitRunnable() {
            int ticksLeft = totalSeconds * 20;
            final int initialTicks = ticksLeft;

            @Override
            public void run() {
                if (!player.isOnline() || ticksLeft <= 0) {
                    player.hideBossBar(bar);
                    removeBar(player, key);
                    cancel();
                    return;
                }

                float progress = Math.max(0.0f, Math.min(1.0f, (float) ticksLeft / initialTicks));
                bar.progress(progress);

                double secondsLeft = ticksLeft / 20.0;
                String timeFormatted = String.format(Locale.US, "%.1f", secondsLeft);
                Component updatedTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + timeFormatted + "ꜱ</bold></gradient>");
                bar.name(updatedTitle);

                ticksLeft -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void showPassiveCooldown(Player player, String passiveName, BossBar.Color color, int totalSeconds) {
        if (!plugin.getConfig().getBoolean("settings.bossbar_enabled", true)) return;

        String key = "passive_" + passiveName.toLowerCase(Locale.ROOT);
        removeBar(player, key);

        String gradientTag = "<gradient:#FFFFFF:#FFD700>";
        String smallName = TextUtil.toSmallCaps(passiveName);

        Component initialTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + totalSeconds + "ꜱ</bold></gradient>");
        BossBar bar = BossBar.bossBar(initialTitle, 1.0f, color != null ? color : BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        playerBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, bar);

        new BukkitRunnable() {
            int ticksLeft = totalSeconds * 20;
            final int initialTicks = ticksLeft;

            @Override
            public void run() {
                if (!player.isOnline() || ticksLeft <= 0) {
                    player.hideBossBar(bar);
                    removeBar(player, key);
                    cancel();
                    return;
                }

                float progress = Math.max(0.0f, Math.min(1.0f, (float) ticksLeft / initialTicks));
                bar.progress(progress);

                double secondsLeft = ticksLeft / 20.0;
                String timeFormatted = String.format(Locale.US, "%.1f", secondsLeft);
                Component updatedTitle = miniMessage.deserialize(gradientTag + "<bold>" + smallName + " | " + timeFormatted + "ꜱ</bold></gradient>");
                bar.name(updatedTitle);

                ticksLeft -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void removeBar(Player player, String key) {
        Map<String, BossBar> map = playerBars.get(player.getUniqueId());
        if (map != null) {
            BossBar old = map.remove(key);
            if (old != null) {
                player.hideBossBar(old);
            }
        }
    }

    public void removeBossBar(Player player) {
        Map<String, BossBar> map = playerBars.remove(player.getUniqueId());
        if (map != null) {
            for (BossBar b : map.values()) {
                player.hideBossBar(b);
            }
            map.clear();
        }
    }
}
