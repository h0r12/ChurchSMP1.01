package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemAbilityExecutor;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RitualInventoryListener implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RitualInventoryListener(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(SinGemAbilityExecutor.RITUAL_GUI_TITLE_COMPONENT)) return;

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();

        // If clicking in top inventory
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            // Only slot 4 (center) is interactive! Slots 0..3 and 5..8 are decorative borders
            if (rawSlot != 4) {
                event.setCancelled(true);
            }
        } else {
            // Shift-clicking from player inventory into the GUI
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() != Material.AIR) {
                    ItemStack slot4 = top.getItem(4);
                    if (slot4 == null || slot4.getType() == Material.AIR) {
                        top.setItem(4, current.clone());
                        event.setCurrentItem(null);
                        event.setCancelled(true);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().title().equals(SinGemAbilityExecutor.RITUAL_GUI_TITLE_COMPONENT)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && slot != 4) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().title().equals(SinGemAbilityExecutor.RITUAL_GUI_TITLE_COMPONENT)) return;

        ItemStack item = event.getInventory().getItem(4);
        if (item != null && item.getType() != Material.AIR) {
            plugin.getGemAbilityExecutor().setPreloadedItem(player, item.clone());
            player.sendMessage(miniMessage.deserialize("<gold>✦ [RITUAL SACRIFICE] <white>" + TextUtil.toSmallCaps("Sacrifice updated") + ": </white><yellow>" + item.getType().name() + " x" + item.getAmount() + "</yellow> ✦</gold>"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        } else {
            plugin.getGemAbilityExecutor().removePreloadedItem(player);
            player.sendMessage(miniMessage.deserialize("<gray>✦ [RITUAL SACRIFICE] " + TextUtil.toSmallCaps("Sacrifice slot is empty.") + " ✦</gray>"));
        }
    }
}
