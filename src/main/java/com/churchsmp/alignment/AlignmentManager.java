package com.churchsmp.alignment;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class AlignmentManager {

    private final ChurchSMP plugin;
    private final NamespacedKey alignmentKey;

    public AlignmentManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentKey = new NamespacedKey(plugin, "alignment");
    }

    /**
     * Retrieves the player's current alignment from PersistentDataContainer.
     */
    public Alignment getAlignment(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String stored = pdc.get(alignmentKey, PersistentDataType.STRING);
        if (stored == null) {
            String defaultStr = plugin.getConfig().getString("alignments.default", "NULLIFIED");
            try {
                return Alignment.valueOf(defaultStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Alignment.NULLIFIED;
            }
        }
        try {
            return Alignment.valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Alignment.NULLIFIED;
        }
    }

    /**
     * Sets the player's alignment and persists it.
     */
    public void setAlignment(Player player, Alignment alignment) {
        player.getPersistentDataContainer().set(alignmentKey, PersistentDataType.STRING, alignment.name());
        player.sendMessage(Component.text("Your alignment has shifted to ", NamedTextColor.GRAY)
                .append(alignment.getFormattedComponent())
                .append(Component.text(".", NamedTextColor.GRAY)));
    }

    /**
     * Checks whether a player meets the required alignment for an item.
     */
    public boolean canWield(Player player, Alignment required) {
        if (required == Alignment.NULLIFIED) {
            return true;
        }
        return getAlignment(player) == required;
    }
}
