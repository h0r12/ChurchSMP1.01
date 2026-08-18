package com.churchsmp.shrine;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder so ShrineListener can tell an Offering GUI apart from any
 * other open inventory in {@code InventoryCloseEvent}.
 */
public class OfferingGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
