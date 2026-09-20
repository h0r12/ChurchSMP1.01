package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.LegendaryWeapon;
import com.churchsmp.weapon.Mayim;
import com.churchsmp.weapon.Sorrowess;
import com.churchsmp.weapon.VoidBreaker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);

        if (weapon instanceof VoidBreaker vb) {
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);
            vb.handleDoubleJump(player);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);

        // Allow flight in air if holding Voidbreaker so double jump triggers
        if (weapon instanceof VoidBreaker) {
            if (player.isOnGround()) {
                player.setAllowFlight(true);
            }
        }

        // Mayim Honor check: cannot hold any offhand while having Mayim in main hand
        if (weapon instanceof Mayim) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off.getType() != Material.AIR) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                player.getInventory().addItem(off);
                player.sendMessage(Component.text("âœ¦ Mayim's Honor forbids holding anything in your offhand!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack current = player.getInventory().getItem(event.getNewSlot());

        LegendaryWeapon prevW = plugin.getWeaponManager().getWeapon(previous);
        LegendaryWeapon currW = plugin.getWeaponManager().getWeapon(current);

        if (prevW instanceof Sorrowess sw) {
            sw.removeBraveBuff(player);
        }
        if (currW instanceof Sorrowess sw) {
            sw.applyBraveBuff(player);
        }

        // Disable flight if switching away from Voidbreaker
        if (prevW instanceof VoidBreaker && !(currW instanceof VoidBreaker)) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);
        if (weapon instanceof Sorrowess sw) {
            sw.removeBraveBuff(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBossBarManager().removeBossBar(event.getPlayer());
    }
}
