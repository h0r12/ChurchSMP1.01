package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public class CraftingGuardListener implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final AlignmentManager alignmentManager;

    public CraftingGuardListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.alignmentManager = plugin.getAlignmentManager();
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        WeaponType type = weaponManager.getWeaponType(result);
        if (type == null) return;

        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player) {
                AlignmentTier tier = alignmentManager.getTier(player);
                if (!type.isUsableBy(tier)) {
                    event.getInventory().setResult(null);
                    player.sendActionBar(Component.text(
                            "Your alignment is unworthy to forge the " + type.getDisplayName() + ".",
                            NamedTextColor.RED));
                }
                return; // crafting tables only ever have one relevant viewer
            }
        }
    }
}
