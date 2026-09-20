package com.churchsmp.menu;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class WeaponMenuManager implements Listener, InventoryHolder {

    private final ChurchSMP plugin;
    private final Map<Integer, String> slotToWeaponId = new HashMap<>();

    public WeaponMenuManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        Component title = MiniMessage.miniMessage().deserialize("<gradient:#FFD700:#FFA500><bold>Legendary Weapons</bold></gradient>");
        Inventory inv = Bukkit.createInventory(this, 36, title);

        // Fill background with dark gray stained glass
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Weapon slots layout
        // Row 1: Excalibur (10), Mayim (12), Judas (14), Sorrowess (16)
        // Row 2: Luminescence Spear (20), Voidbreaker (22), Grim Scythe (24)
        slotToWeaponId.clear();
        setSlotWeapon(inv, 10, "excalibur");
        setSlotWeapon(inv, 12, "mayim");
        setSlotWeapon(inv, 14, "judas");
        setSlotWeapon(inv, 16, "sorrowess");
        setSlotWeapon(inv, 20, "luminescence_spear");
        setSlotWeapon(inv, 22, "voidbreaker");
        setSlotWeapon(inv, 24, "grim");

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    private void setSlotWeapon(Inventory inv, int slot, String weaponId) {
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
        if (weapon != null) {
            inv.setItem(slot, weapon.createItem());
            slotToWeaponId.put(slot, weaponId);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WeaponMenuManager)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        String weaponId = slotToWeaponId.get(slot);
        if (weaponId != null) {
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
            if (weapon != null) {
                ItemStack weaponItem = weapon.createItem();
                player.getInventory().addItem(weaponItem);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                player.sendMessage(Component.text("✦ Received ", NamedTextColor.GREEN)
                        .append(weapon.getDisplayName())
                        .append(Component.text("!", NamedTextColor.GREEN)));
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 36);
    }
}
