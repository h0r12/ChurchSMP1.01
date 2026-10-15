package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.particle.ParticleAnimationManager;
import com.churchsmp.particle.WandSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WandListener implements Listener {

    private final ChurchSMP plugin;
    private final ParticleAnimationManager animationManager;
    private final MiniMessage miniMessage;

    public WandListener(ChurchSMP plugin, ParticleAnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!animationManager.isWand(item)) return;

        event.setCancelled(true);
        WandSession session = animationManager.getSession(player);
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                plugin.getParticleStudioGuiManager().openStudio(player, session);
                return;
            }
            int pts = session.stampActivePreset(player.getEyeLocation(), plugin);
            player.sendMessage(miniMessage.deserialize("<#d8b4fe>Stamped <white>" + session.getActivePreset().name() + "</white> <gray>(" + pts + " pts)</gray> at <white>Frame " + session.getCurrentFrame() + "</white></#d8b4fe>"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 1.6f);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                animationManager.toggleLoop(player, session);
            } else {
                animationManager.playPreview(player, session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!animationManager.isWand(item)) return;

        event.setCancelled(true);
        WandSession session = animationManager.getSession(player);

        if (player.isSneaking()) {
            // Shift + F = UNDO
            if (session.undo()) {
                player.sendMessage(miniMessage.deserialize("<gold>Undo successful. <gray>(Restored previous state)</gray></gold>"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.2f);
            } else {
                player.sendMessage(miniMessage.deserialize("<gray>Nothing to undo.</gray>"));
            }
        } else {
            // F = REDO
            if (session.redo()) {
                player.sendMessage(miniMessage.deserialize("<gold>Redo successful. <gray>(Restored undone state)</gray></gold>"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.7f);
            } else {
                player.sendMessage(miniMessage.deserialize("<gray>Nothing to redo.</gray>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();

        if (!animationManager.isWand(dropped)) return;

        event.setCancelled(true);
        WandSession session = animationManager.getSession(player);

        if (player.isSneaking()) {
            // Shift + Q = Previous Frame
            session.prevFrame();
            player.sendMessage(miniMessage.deserialize("<aqua>Timeline: <white>Frame " + session.getCurrentFrame() + "</white></aqua>"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        } else {
            // Q = Next Frame
            session.nextFrame();
            player.sendMessage(miniMessage.deserialize("<aqua>Timeline: <white>Frame " + session.getCurrentFrame() + "</white></aqua>"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
        }
    }
}
