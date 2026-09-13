package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a persistent two-line display while a player holds any legendary
 * weapon: a top bar with each ability's remaining cooldown pipe-separated
 * ("67s | 123s | 420s"), and a bottom bar with the weapon's name. VoidBreaker
 * shows three numbers (its Ability 1 splits into two separate cooldown
 * buckets depending on Density/Breach mode); every other weapon shows two.
 * Both bars disappear the moment the weapon leaves their hand.
 */
public class PersistentCooldownDisplay {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, BossBar> cooldownBars = new HashMap<>();
    private final Map<UUID, BossBar> nameBars = new HashMap<>();

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

        if (type == null) {
            remove(player);
            return;
        }

        int[] buckets = type == WeaponType.VOIDBREAKER ? new int[]{1, 3, 2} : new int[]{1, 2};
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            long remaining = weaponManager.getRemainingCooldownSeconds(player, type, buckets[i]);
            line.append(remaining > 0 ? remaining + "s" : "Ready");
            if (i < buckets.length - 1) line.append(" | ");
        }

        UUID id = player.getUniqueId();
        BossBar cooldownBar = cooldownBars.computeIfAbsent(id, k -> {
            BossBar bar = Bukkit.createBossBar(line.toString(), BarColor.PURPLE, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
        cooldownBar.setTitle(line.toString());

        BossBar nameBar = nameBars.computeIfAbsent(id, k -> {
            BossBar bar = Bukkit.createBossBar(type.getDisplayName(), BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
        if (!nameBar.getTitle().equals(type.getDisplayName())) {
            nameBar.setTitle(type.getDisplayName());
        }
    }

    private void remove(Player player) {
        UUID id = player.getUniqueId();
        BossBar cooldownBar = cooldownBars.remove(id);
        if (cooldownBar != null) cooldownBar.removeAll();
        BossBar nameBar = nameBars.remove(id);
        if (nameBar != null) nameBar.removeAll();
    }
}
