package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Shows each ability's current status as plain action-bar text ("Ready! |
 * Ready!", or "12s | Ready!" while on cooldown) whenever a player holds a
 * legendary weapon — action bar renders just above the hotbar/health bar,
 * not as a boss bar. VoidBreaker shows three entries (its Ability 1 splits
 * into two separate cooldown buckets depending on Density/Breach mode);
 * every other weapon shows two.
 *
 * Note this shares the action bar with every other transient message this
 * plugin sends (Bite procs, Gloom's "sinks into Depression", etc.) — those
 * will still flash over this display for the instant they're sent, since
 * only one action-bar message can be visible at a time, but this resumes
 * on the very next tick. An active-ability's own countdown (Frost Edge's
 * 20s window, Accelerated Nova's charge, etc.) still uses a real boss bar
 * via CooldownBarDisplay — this only replaces the idle "is it ready yet"
 * status that used to be a second, always-on boss bar.
 */
public class PersistentCooldownDisplay {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;

    public PersistentCooldownDisplay(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tick(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        WeaponType type = weaponManager.getWeaponType(held);
        if (type == null) return; // nothing to show, and nothing to actively clear either — it'll just not refresh

        int[] buckets = type == WeaponType.VOIDBREAKER ? new int[]{1, 3, 2} : new int[]{1, 2};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            long remaining = weaponManager.getRemainingCooldownSeconds(player, type, buckets[i]);
            line.append(remaining > 0 ? remaining + "s" : "Ready!");
            if (i < buckets.length - 1) line.append(" | ");
        }
        player.sendActionBar(Component.text(line.toString(), NamedTextColor.AQUA));
    }
}
