package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Excalibur's passive: crouching reveals every nearby player through a
 * colored Glow, tinted to match their alignment tier. Since glow color in
 * vanilla is read from the *viewer's own* scoreboard (not a global one),
 * each Excalibur holder gets their own private scoreboard with one team
 * per tier — so only they see the alignment colors; everyone else who
 * happens to see the Glow (if any) just sees the default color.
 */
public class ExcaliburPassives implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final AlignmentManager alignmentManager;
    private final Map<UUID, Scoreboard> revealBoards = new HashMap<>();

    public ExcaliburPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.alignmentManager = plugin.getAlignmentManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tick(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // twice a second
    }

    private boolean isHoldingExcalibur(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.BLADE_OF_ARCHANGEL;
    }

    private void tick(Player player) {
        if (!player.isSneaking() || !isHoldingExcalibur(player)) return;

        Scoreboard board = revealBoards.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (nearby.getLocation().distanceSquared(player.getLocation()) > 20 * 20) continue;

            AlignmentTier tier = alignmentManager.getTier(nearby);
            Team team = board.getTeam(tier.name());
            if (team == null) {
                team = board.registerNewTeam(tier.name());
                team.setColor(tierChatColor(tier));
            }
            if (!team.hasEntry(nearby.getName())) {
                team.addEntry(nearby.getName());
            }
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false));
        }
    }

    private ChatColor tierChatColor(AlignmentTier tier) {
        return switch (tier) {
            case FALLEN -> ChatColor.DARK_RED;
            case WICKED -> ChatColor.GRAY;
            case NULLIFIED -> ChatColor.WHITE;
            case RIGHTEOUS -> ChatColor.YELLOW;
            case SAINT -> ChatColor.GOLD;
        };
    }
}
