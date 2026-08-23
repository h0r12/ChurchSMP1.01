package com.churchsmp.listeners;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Alignment is intentionally NOT a power mechanic: evil doesn't weaken you
 * and good doesn't strengthen you. Tier still gates which weapons you can
 * activate (WeaponListener) and drives Confession/shrine mechanics, but no
 * passive combat buffs or debuffs are applied here anymore.
 *
 * What's left is purely cosmetic flavor: Fallen players singe briefly when
 * they cross onto consecrated ground, as a visible (non-mechanical) sign
 * they don't belong there. Nothing here changes damage, speed, or health.
 */
public class EffectListener implements Listener {

    private final ChurchSMP plugin;
    private final AlignmentManager alignment;

    public EffectListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignment = plugin.getAlignmentManager();
    }

    /** Kept for compatibility with ChurchSMP#onEnable — currently a no-op. */
    public void startTicking() {
        // Intentionally empty: alignment no longer drives passive buffs/debuffs.
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // only bother checking on an actual block-position change
        }

        AlignmentTier tier = alignment.getTier(player);
        if (tier != AlignmentTier.FALLEN) return;
        if (!plugin.getChurchRegionManager().isHolyGround(player.getLocation())) return;

        // Purely cosmetic — a puff of smoke, no fire, no damage, no debuff.
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.2, 0),
                10, 0.3, 0.1, 0.3, 0.01);
    }
}
