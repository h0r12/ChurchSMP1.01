package com.churchsmp.menu;

import com.churchsmp.ChurchSMP;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WeaponChoiceManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static class ChoiceHolder implements InventoryHolder {
        private final RelicItem.RelicType relicType;
        public ChoiceHolder(RelicItem.RelicType relicType) { this.relicType = relicType; }
        public RelicItem.RelicType getRelicType() { return relicType; }
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    private final Map<UUID, Map<Integer, String>> playerSlotMap = new ConcurrentHashMap<>();

    public WeaponChoiceManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
        }
        return filler;
    }

    /**
     * Opens the choosing menu for the respective Liminal Path Item:
     * Iniquity: Sins Weapons (Judas, Sorrowess, Grim)
     * Impiety: God Weapons (Excalibur, Luminescence Spear, Mayim)
     * Obscura: Voided Weapons (Voidbreaker)
     */
    public void openChoiceMenu(Player player, RelicItem.RelicType relicType) {
        String titleStr = switch (relicType) {
            case INIQUITY -> "CHOOSE SINS WEAPON";
            case IMPIETY -> "CHOOSE GOD WEAPON";
            case OBSCURA -> "CHOOSE VOID WEAPON";
        };

        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader(titleStr));
        Inventory inv = Bukkit.createInventory(new ChoiceHolder(relicType), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        Map<Integer, String> slots = new HashMap<>();

        if (relicType == RelicItem.RelicType.INIQUITY) {
            // Sins: Judas (11), Sorrowess (13), Grim (15)
            setChoiceItem(inv, 11, "judas", slots);
            setChoiceItem(inv, 13, "sorrowess", slots);
            setChoiceItem(inv, 15, "grim", slots);
        } else if (relicType == RelicItem.RelicType.IMPIETY) {
            // God: Excalibur (11), Luminescence Spear (13), Mayim (15)
            setChoiceItem(inv, 11, "excalibur", slots);
            setChoiceItem(inv, 13, "luminescence_spear", slots);
            setChoiceItem(inv, 15, "mayim", slots);
        } else {
            // Voided: Voidbreaker (13)
            setChoiceItem(inv, 13, "voidbreaker", slots);
        }

        playerSlotMap.put(player.getUniqueId(), slots);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.5f);
    }

    private void setChoiceItem(Inventory inv, int slot, String weaponId, Map<Integer, String> slots) {
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
        if (weapon != null) {
            ItemStack item = weapon.createItem();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(miniMessage.deserialize("<gold>✦ <yellow><bold>" + TextUtil.toSmallCaps("Click to Choose this Weapon") + "</bold></yellow></gold>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slots.put(slot, weaponId);
        }
    }

    @EventHandler
    public void onChoiceClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ChoiceHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        Map<Integer, String> slots = playerSlotMap.get(player.getUniqueId());
        if (slots == null) return;

        String weaponId = slots.get(slot);
        if (weaponId == null) return;

        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
        if (weapon == null) return;

        // Check if player already carries a legendary
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String wid = it.getItemMeta().getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "weapon_id"), org.bukkit.persistence.PersistentDataType.STRING);
            if (wid != null && plugin.getWeaponManager().getWeapon(wid) != null) {
                player.sendMessage(Component.text("✦ Your Existing Actions Disciplines: You may only carry one legendary weapon at a time!", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
        }

        // Consume 1 relic from hand or inventory
        consumeRelic(player, holder.getRelicType());

        // Set player's alignment score according to relic path
        plugin.getAlignmentManager().setAlignmentScore(player, holder.getRelicType().getTargetScore());

        // Broadcast choice to everyone with sound alert
        Component broadcast = miniMessage.deserialize("<gold>✦ <yellow>" + player.getName() + "</yellow> <gray>has chosen the legendary relic </gray></gold>")
                .append(weapon.getDisplayName())
                .append(miniMessage.deserialize("<gold>! ✦</gold>"));
        Bukkit.broadcast(broadcast);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.2f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
        }

        player.closeInventory();

        // Run the epic overhead spinning/orbiting weapon chosen animation
        playWeaponChosenAnimation(player, weapon);
    }

    private void consumeRelic(Player player, RelicItem.RelicType type) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it != null && RelicItem.getRelicType(it, plugin) == type) {
                it.subtract(1);
                return;
            }
        }
    }

    /**
     * Special animation for chosen weapon:
     * Spawns weapon above player's head, spins & orbits rapidly with theme particles & sounds,
     * then lands into player's inventory!
     */
    public void playWeaponChosenAnimation(Player player, LegendaryWeapon weapon) {
        Location startLoc = player.getEyeLocation().add(0, 1.2, 0);
        ItemStack itemToDrop = weapon.createItem();
        Item floating = player.getWorld().dropItem(startLoc, itemToDrop);
        floating.setGravity(false);
        floating.setPickupDelay(Integer.MAX_VALUE);
        floating.setCanMobPickup(false);
        floating.setVelocity(new Vector(0, 0, 0));

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.8f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || !floating.isValid() || ticks > 50) {
                    floating.remove();
                    if (player.isOnline()) {
                        player.getInventory().addItem(weapon.createItem());
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
                        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1, 0), 2, Color.WHITE);
                        player.sendMessage(miniMessage.deserialize("<gold>✦ <white><bold>" + TextUtil.toSmallCaps("You have chosen") + ": </bold></white></gold>")
                                .append(weapon.getDisplayName())
                                .append(miniMessage.deserialize("<gold>! <gray>" + TextUtil.toSmallCaps("Your path is sealed.") + "</gray> ✦</gold>")));
                    }
                    cancel();
                    return;
                }

                angle += 0.35;
                double radius = Math.max(0.2, 1.0 - (ticks * 0.015));
                double yOffset = 1.0 + Math.sin(ticks * 0.2) * 0.3;
                Location targetLoc = player.getLocation().add(Math.cos(angle) * radius, yOffset + 1.2, Math.sin(angle) * radius);
                floating.teleport(targetLoc);

                // Weapon theme colored particles
                if (weapon.getId().equals("excalibur") || weapon.getId().equals("luminescence_spear") || weapon.getId().equals("mayim")) {
                    player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, targetLoc, 3, 0.1, 0.1, 0.1, 0.05);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targetLoc, 2, 0.1, 0.1, 0.1, 0.02);
                } else if (weapon.getId().equals("voidbreaker")) {
                    player.getWorld().spawnParticle(Particle.PORTAL, targetLoc, 6, 0.2, 0.2, 0.2, 0.2);
                    player.getWorld().spawnParticle(Particle.DRAGON_BREATH, targetLoc, 2, 0.1, 0.1, 0.1, 0.02, 0.5f);
                } else {
                    // Sins / Evil
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, targetLoc, 3, 0.1, 0.1, 0.1, 0.02);
                    player.getWorld().spawnParticle(Particle.SMOKE, targetLoc, 2, 0.1, 0.1, 0.1, 0.02);
                }

                if (ticks % 10 == 0) {
                    player.playSound(targetLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.5f + (ticks * 0.01f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
