package com.churchsmp.relics;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * On first join, gives the player a random unclaimed sin gem. Right-clicking
 * an unclaimed gem permanently attunes them to that sin (consuming the gem)
 * — but only if they haven't already attuned to one. A separate Reroll
 * Token lets them swap an unclaimed gem they're holding for a new random
 * one before they commit, but doesn't touch an already-attuned relic.
 */
public class RelicJoinListener implements Listener {

    private final ChurchSMP plugin;
    private final RelicManager relicManager;
    private final NamespacedKey rerollTokenKey;

    public RelicJoinListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.relicManager = plugin.getRelicManager();
        this.rerollTokenKey = new NamespacedKey(plugin, "relic_reroll_token");
    }

    public ItemStack createRerollToken() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Reroll Token", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click while holding an unclaimed sin gem", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true),
                Component.text("to swap it for a new random one.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true)
        ));
        meta.getPersistentDataContainer().set(rerollTokenKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isRerollToken(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(rerollTokenKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(relicManager.getReceivedStarterGemKey(), PersistentDataType.BYTE)) return; // already got one

        pdc.set(relicManager.getReceivedStarterGemKey(), PersistentDataType.BYTE, (byte) 1);
        RelicType[] types = RelicType.values();
        RelicType random = types[(int) (Math.random() * types.length)];
        player.getInventory().addItem(relicManager.createGem(random));
        player.sendMessage(Component.text("A " + random.getDisplayName() + " Gem calls to you. Right-click it to attune, or find a Reroll Token to try again.",
                NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        RelicType gemType = relicManager.getRelicType(held);
        if (gemType != null) {
            event.setCancelled(true);
            if (relicManager.getAssignedRelic(player) != null) {
                player.sendMessage(Component.text("You're already attuned to a sin — this gem does nothing for you now.", NamedTextColor.RED));
                return;
            }
            held.setAmount(held.getAmount() - 1);
            relicManager.assignRelic(player, gemType);
            player.sendMessage(Component.text("You are now attuned to " + gemType.getDisplayName() + ".", NamedTextColor.LIGHT_PURPLE));
            player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            return;
        }

        if (isRerollToken(held)) {
            event.setCancelled(true);
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack stack : contents) {
                RelicType existing = relicManager.getRelicType(stack);
                if (existing != null) {
                    stack.setAmount(stack.getAmount() - 1);
                    held.setAmount(held.getAmount() - 1);
                    RelicType[] types = RelicType.values();
                    RelicType random = types[(int) (Math.random() * types.length)];
                    player.getInventory().addItem(relicManager.createGem(random));
                    player.sendMessage(Component.text("Rerolled into a " + random.getDisplayName() + " Gem.", NamedTextColor.AQUA));
                    return;
                }
            }
            player.sendMessage(Component.text("You need an unclaimed sin gem in your inventory to reroll.", NamedTextColor.RED));
        }
    }
}
