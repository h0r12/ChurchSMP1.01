package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enforces: a player can only hold ONE legendary weapon at a time.
 * If they already have one and try to pick up / click another, it is denied.
 */
public class LegendaryPickupListener implements Listener {

    private final ChurchSMP plugin;
    private final NamespacedKey weaponIdKey;

    public LegendaryPickupListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack picked = event.getItem().getItemStack();
        if (!isLegendary(picked)) return;

        // Check if player already has a legendary in their inventory
        if (alreadyHasLegendary(player, picked)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        if (!isLegendary(cursor)) return;

        if (alreadyHasLegendary(player, cursor)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    private boolean isLegendary(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String wid = item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
        return wid != null && plugin.getWeaponManager().getWeapon(wid) != null;
    }

    /**
     * Returns true if the player already holds a DIFFERENT legendary than {@code incoming}.
     */
    private boolean alreadyHasLegendary(Player player, ItemStack incoming) {
        String incomingId = getWeaponId(incoming);
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String wid = it.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
            if (wid == null) continue;
            LegendaryWeapon w = plugin.getWeaponManager().getWeapon(wid);
            if (w == null) continue;
            // If found a different legendary (not the same weapon id), deny
            if (!wid.equals(incomingId)) return true;
        }
        return false;
    }

    private String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
    }

    private void sendDenyMessage(Player player) {
        player.sendMessage(Component.text("✦ Your Existing Actions Disciplines", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("  You may only carry one legendary weapon at a time.", NamedTextColor.DARK_GRAY));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
    }
}
