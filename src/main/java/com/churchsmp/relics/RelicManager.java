package com.churchsmp.relics;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates/reads the 7 sin gems, tracks which relic a player has permanently
 * attuned to (stored on the player's own PersistentDataContainer, not the
 * item — once attuned, the gem is consumed and the class sticks regardless
 * of what's held), and tracks relic ability cooldowns the same way
 * WeaponManager does for weapons.
 */
public class RelicManager {

    private final ChurchSMP plugin;
    private final NamespacedKey relicTypeKey;
    private final NamespacedKey assignedRelicKey;
    private final NamespacedKey receivedStarterGemKey;
    private final Map<UUID, Map<Integer, Long>> cooldowns = new HashMap<>();

    public RelicManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.relicTypeKey = new NamespacedKey(plugin, "relic_type");
        this.assignedRelicKey = new NamespacedKey(plugin, "assigned_relic");
        this.receivedStarterGemKey = new NamespacedKey(plugin, "received_starter_gem");
    }

    public NamespacedKey getRelicTypeKey() { return relicTypeKey; }
    public NamespacedKey getReceivedStarterGemKey() { return receivedStarterGemKey; }

    /** Builds the raw, unclaimed gem for a sin — right-clicking it is what actually attunes a player (see RelicJoinListener). */
    public ItemStack createGem(RelicType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName() + " Gem", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.getSubtitle(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        if (type.getPassives().length > 0) {
            for (String passive : type.getPassives()) {
                lore.add(Component.text("\u25B8 " + passive, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }
        lore.add(Component.text(type.getAbility1Desc(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(type.getAbility2Desc(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to permanently attune to this sin.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(relicTypeKey, PersistentDataType.STRING, type.getId());
        item.setItemMeta(meta);
        return item;
    }

    /** Null if this item isn't one of the 7 sin gems. */
    public RelicType getRelicType(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(relicTypeKey, PersistentDataType.STRING);
        return id == null ? null : RelicType.fromId(id);
    }

    /** Every possible gem, for the crafting-recipe exact-choice list — any sin's gem can be spent toward a weapon. */
    public List<ItemStack> allGems() {
        List<ItemStack> gems = new ArrayList<>();
        for (RelicType type : RelicType.values()) {
            gems.add(createGem(type));
        }
        return gems;
    }

    /** Null if the player hasn't attuned to a sin yet. */
    public RelicType getAssignedRelic(Player player) {
        String id = player.getPersistentDataContainer().get(assignedRelicKey, PersistentDataType.STRING);
        return id == null ? null : RelicType.fromId(id);
    }

    /** Permanently assigns a relic to the player — this sticks regardless of what they're holding afterward. */
    public void assignRelic(Player player, RelicType type) {
        player.getPersistentDataContainer().set(assignedRelicKey, PersistentDataType.STRING, type.getId());
    }

    public boolean isOnCooldown(Player player, int ability) {
        Long readyAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(ability);
        return readyAt != null && readyAt > System.currentTimeMillis();
    }

    public long getRemainingCooldownSeconds(Player player, int ability) {
        Long readyAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(ability);
        if (readyAt == null) return 0;
        return Math.max(0, (readyAt - System.currentTimeMillis()) / 1000);
    }

    public void putOnCooldown(Player player, int ability, int seconds) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + seconds * 1000L);
    }
}
