package com.churchsmp.gem;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SinGemManager {

    private final ChurchSMP plugin;
    private final NamespacedKey gemTypeKey;
    private final NamespacedKey attunedKey;
    private final NamespacedKey receivedFirstGemKey;
    private final Random random = new Random();

    public SinGemManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.gemTypeKey = new NamespacedKey(plugin, "gem_type");
        this.attunedKey = new NamespacedKey(plugin, "attuned_gem");
        this.receivedFirstGemKey = new NamespacedKey(plugin, "received_first_gem");
    }

    public NamespacedKey getGemTypeKey() {
        return gemTypeKey;
    }

    /**
     * Builds an ItemStack representing a Sin Gem.
     */
    public ItemStack createGemItem(SinGemType type) {
        ItemStack item = new ItemStack(type.getIconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(type.getFormattedName());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Sin Relic: ", NamedTextColor.GRAY)
                    .append(Component.text(type.getDisplayName(), type.getColor()).decorate(TextDecoration.BOLD)));
            lore.add(Component.text(type.getDescription(), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Skill 1 [RMB]: ", NamedTextColor.GOLD)
                    .append(Component.text(type.getPrimaryAbilityName(), NamedTextColor.YELLOW)));
            lore.add(Component.text("✦ Skill 2 [Shift + RMB]: ", NamedTextColor.GOLD)
                    .append(Component.text(type.getSecondaryAbilityName(), NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Hold in hand to channel powers", NamedTextColor.AQUA));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(gemTypeKey, PersistentDataType.STRING, type.name());
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets the SinGemType from an ItemStack, or null if not a sin gem.
     */
    public SinGemType getGemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String val = pdc.get(gemTypeKey, PersistentDataType.STRING);
        if (val != null) {
            try {
                return SinGemType.valueOf(val.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }
        // Fallback: check item display name
        Component name = item.getItemMeta().displayName();
        if (name != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
            for (SinGemType type : SinGemType.values()) {
                if (plain.toLowerCase(Locale.ROOT).contains(type.getDisplayName().toLowerCase(Locale.ROOT))) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a player has attuned to any gem.
     */
    public boolean isAttuned(Player player) {
        return player.getPersistentDataContainer().has(attunedKey, PersistentDataType.STRING);
    }

    /**
     * Gets the player's attuned gem type, or null if none.
     */
    public SinGemType getAttunedGem(Player player) {
        String val = player.getPersistentDataContainer().get(attunedKey, PersistentDataType.STRING);
        if (val == null) return null;
        try {
            return SinGemType.valueOf(val.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the SinGemType held by the player in either main hand or offhand.
     */
    public SinGemType getHeldGem(Player player) {
        if (player == null) return null;
        SinGemType main = getGemType(player.getInventory().getItemInMainHand());
        if (main != null) return main;
        return getGemType(player.getInventory().getItemInOffHand());
    }

    /**
     * Checks if the player is holding the specified SinGemType in either main hand or offhand.
     */
    public boolean isHoldingGem(Player player, SinGemType gem) {
        if (player == null || gem == null) return false;
        SinGemType main = getGemType(player.getInventory().getItemInMainHand());
        if (main == gem) return true;
        SinGemType off = getGemType(player.getInventory().getItemInOffHand());
        return off == gem;
    }

    /**
     * Permanently attunes a player to a sin gem type.
     */
    public boolean attune(Player player, SinGemType type) {
        if (isAttuned(player)) {
            player.sendMessage(Component.text("You are already permanently attuned to the " + getAttunedGem(player).getDisplayName() + " gem!", NamedTextColor.RED));
            return false;
        }

        player.getPersistentDataContainer().set(attunedKey, PersistentDataType.STRING, type.name());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage(Component.text("âš” You have permanently attuned your soul to the ", NamedTextColor.GOLD)
                .append(Component.text(type.getDisplayName(), type.getColor()).decorate(TextDecoration.BOLD))
                .append(Component.text(" Relic Gem!", NamedTextColor.GOLD)));

        return true;
    }

    /**
     * Gives a random gem on first join if not already received.
     */
    public void checkAndGiveFirstGem(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(receivedFirstGemKey, PersistentDataType.BYTE)) {
            pdc.set(receivedFirstGemKey, PersistentDataType.BYTE, (byte) 1);
            SinGemType[] values = SinGemType.values();
            SinGemType chosen = values[random.nextInt(values.length)];
            player.getInventory().addItem(createGemItem(chosen));

            player.sendMessage(Component.text("âœ¦ A mysterious relic materialized in your inventory: ", NamedTextColor.YELLOW)
                    .append(chosen.getFormattedName()));
            player.sendMessage(Component.text("âœ¦ Right-click to attune, or use /church reroll before attuning!", NamedTextColor.GRAY));
        }
    }

    /**
     * Allows pre-attunement rerolling of an un-attuned gem in main hand.
     */
    public boolean rerollGemInHand(Player player) {
        if (isAttuned(player)) {
            player.sendMessage(Component.text("You are already permanently attuned! Rerolls are forbidden.", NamedTextColor.RED));
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        SinGemType current = getGemType(item);
        if (current == null) {
            player.sendMessage(Component.text("You must hold an unattuned Sin Gem to reroll it!", NamedTextColor.RED));
            return false;
        }

        SinGemType[] all = SinGemType.values();
        SinGemType next;
        do {
            next = all[random.nextInt(all.length)];
        } while (next == current && all.length > 1);

        player.getInventory().setItemInMainHand(createGemItem(next));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.sendMessage(Component.text("âœ¦ Your gem resonated and transformed into: ", NamedTextColor.GREEN)
                .append(next.getFormattedName()));
        return true;
    }
}
