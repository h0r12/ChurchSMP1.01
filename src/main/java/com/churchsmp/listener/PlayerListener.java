package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.LegendaryWeapon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final ChurchSMP plugin;

    public PlayerListener(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getSinGemManager().checkAndGiveFirstGem(player);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            weapon.onCrouch(player, event.isSneaking());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBossBarManager().removeBossBar(event.getPlayer());
    }
}
