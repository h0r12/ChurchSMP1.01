package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class WandActionBarTask extends BukkitRunnable {

    private final ChurchSMP plugin;
    private final ParticleAnimationManager animationManager;
    private final MiniMessage miniMessage;

    public WandActionBarTask(ChurchSMP plugin, ParticleAnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (animationManager.isWand(mainHand)) {
                WandSession session = animationManager.getSession(player);
                String posStr = String.format(java.util.Locale.ROOT, " <dark_gray>|</dark_gray> Pos: <white>X:%+.1f Y:%+.1f Z:%+.1f</white>", session.getActiveXOffset(), session.getActiveYOffset(), session.getActiveZOffset());
                String velStr = (session.getActiveVelX() != 0 || session.getActiveVelY() != 0 || session.getActiveVelZ() != 0)
                        ? String.format(java.util.Locale.ROOT, " <dark_gray>|</dark_gray> Vel: <white>%+.1f/%+.1f/%+.1f</white>", session.getActiveVelX(), session.getActiveVelY(), session.getActiveVelZ())
                        : "";
                String rotStr = (session.getActiveRotX() != 0 || session.getActiveRotY() != 0 || session.getActiveRotZ() != 0)
                        ? String.format(java.util.Locale.ROOT, " <dark_gray>|</dark_gray> Rot: <white>%.0f°/%.0f°/%.0f°</white>", session.getActiveRotX(), session.getActiveRotY(), session.getActiveRotZ())
                        : "";
                String decayStr = (session.getActiveLifespan() > 1)
                        ? String.format(java.util.Locale.ROOT, " <dark_gray>|</dark_gray> Decay: <white>%df</white>", session.getActiveLifespan())
                        : "";
                String bar = String.format(
                        java.util.Locale.ROOT,
                        "<#d8b4fe>Frame: <white>%d</white> <dark_gray>|</dark_gray> Preset: <white>%s</white>%s%s%s%s <dark_gray>|</dark_gray> <gold>[Shift+Left: GUI | F/Shift+F: Redo/Undo]</gold></#d8b4fe>",
                        session.getCurrentFrame(),
                        session.getActivePreset().name(),
                        posStr,
                        velStr,
                        rotStr,
                        decayStr
                );
                player.sendActionBar(miniMessage.deserialize(bar));
            }
        }
    }
}
