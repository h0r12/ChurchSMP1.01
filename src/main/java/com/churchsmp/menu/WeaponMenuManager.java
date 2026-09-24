package com.churchsmp.menu;

import com.churchsmp.ChurchSMP;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeaponMenuManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Menu holders for identifying menu types
    public static class MainHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class WeaponsHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class CraftingHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class RecipesHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class EventsHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    private final Map<Integer, String> weaponSlots = new HashMap<>();

    public WeaponMenuManager(ChurchSMP plugin) {
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

    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Main Menu") + "</bold></white></gold>"));
            back.setItemMeta(meta);
        }
        return back;
    }

    // ─── 1. MAIN HUB MENU ────────────────────────────────────────────────────────
    public void openMenu(Player player) {
        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader("CHURCH ADMIN HUB"));
        Inventory inv = Bukkit.createInventory(new MainHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Slot 10: Weapons
        ItemStack weaponsBtn = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta wMeta = weaponsBtn.getItemMeta();
        if (wMeta != null) {
            wMeta.displayName(miniMessage.deserialize("<gold>⚔ <white><bold>" + TextUtil.toSmallCaps("Legendary Weapons") + "</bold></white></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Browse and spawn all 7 legendary relics.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Open", NamedTextColor.YELLOW));
            wMeta.lore(lore);
            weaponsBtn.setItemMeta(wMeta);
        }
        inv.setItem(10, weaponsBtn);

        // Slot 12: Crafting & Altar
        ItemStack craftingBtn = new ItemStack(Material.ANVIL);
        ItemMeta cMeta = craftingBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(miniMessage.deserialize("<gold>⚒ <white><bold>" + TextUtil.toSmallCaps("Altar & Relic Crafting") + "</bold></white></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Spawn or craft Iniquity, Impiety, and Obscura.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Open", NamedTextColor.YELLOW));
            cMeta.lore(lore);
            craftingBtn.setItemMeta(cMeta);
        }
        inv.setItem(12, craftingBtn);

        // Slot 14: Crafting Recipes Viewer
        ItemStack recipesBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta rMeta = recipesBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(miniMessage.deserialize("<gold>📖 <white><bold>" + TextUtil.toSmallCaps("Crafting Recipes") + "</bold></white></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("View crafting recipes for relics and weapons.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Open", NamedTextColor.YELLOW));
            rMeta.lore(lore);
            recipesBtn.setItemMeta(rMeta);
        }
        inv.setItem(14, recipesBtn);

        // Slot 16: Server Events
        ItemStack eventsBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta eMeta = eventsBtn.getItemMeta();
        if (eMeta != null) {
            eMeta.displayName(miniMessage.deserialize("<gold>⚡ <white><bold>" + TextUtil.toSmallCaps("Server Events") + "</bold></white></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Trigger celestial and unholy server events.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Open", NamedTextColor.YELLOW));
            eMeta.lore(lore);
            eventsBtn.setItemMeta(eMeta);
        }
        inv.setItem(16, eventsBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    // ─── 2. WEAPONS SUBMENU ──────────────────────────────────────────────────────
    public void openWeaponsMenu(Player player) {
        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader("LEGENDARY WEAPONS"));
        Inventory inv = Bukkit.createInventory(new WeaponsHolder(), 36, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        weaponSlots.clear();
        setWeaponSlot(inv, 10, "excalibur");
        setWeaponSlot(inv, 12, "mayim");
        setWeaponSlot(inv, 14, "judas");
        setWeaponSlot(inv, 16, "sorrowess");
        setWeaponSlot(inv, 20, "luminescence_spear");
        setWeaponSlot(inv, 22, "voidbreaker");
        setWeaponSlot(inv, 24, "grim");

        inv.setItem(31, createBackButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void setWeaponSlot(Inventory inv, int slot, String weaponId) {
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
        if (weapon != null) {
            inv.setItem(slot, weapon.createItem());
            weaponSlots.put(slot, weaponId);
        }
    }

    // ─── 3. CRAFTING & RELIC SUBMENU ─────────────────────────────────────────────
    public void openCraftingMenu(Player player) {
        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader("ALTAR & RELICS"));
        Inventory inv = Bukkit.createInventory(new CraftingHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Iniquity (Evil)
        inv.setItem(11, RelicItem.createRelic(plugin, RelicItem.RelicType.INIQUITY));
        // Impiety (Good)
        inv.setItem(13, RelicItem.createRelic(plugin, RelicItem.RelicType.IMPIETY));
        // Obscura (Nullified)
        inv.setItem(15, RelicItem.createRelic(plugin, RelicItem.RelicType.OBSCURA));

        inv.setItem(22, createBackButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    // ─── 4. CRAFTING RECIPES VIEWER ──────────────────────────────────────────────
    public void openRecipesMenu(Player player) {
        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader("RECIPES VIEWER"));
        Inventory inv = Bukkit.createInventory(new RecipesHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(10, createRecipeItem("Iniquity", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Wither Skull + Netherite Ingots + Liminal Core + Crying Obsidian"));
        inv.setItem(12, createRecipeItem("Impiety", Material.RECOVERY_COMPASS, "Gold Block + Totems of Undying + Liminal Core + Diamond Block"));
        inv.setItem(14, createRecipeItem("Obscura", Material.ECHO_SHARD, "Echo Shard + End Crystals + Liminal Core + Obsidian"));
        inv.setItem(16, createRecipeItem("Weapons", Material.CRAFTING_TABLE, "Top: Sin Gem | Mid: Core Catalyst | Bottom: Base Weapon"));

        inv.setItem(22, createBackButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private ItemStack createRecipeItem(String name, Material mat, String recipeSummary) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<gold>✦ <white><bold>" + TextUtil.toSmallCaps(name) + " " + TextUtil.toSmallCaps("Recipe") + "</bold></white></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(recipeSummary, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─── 5. SERVER EVENTS SUBMENU ────────────────────────────────────────────────
    public void openEventsMenu(Player player) {
        Component title = miniMessage.deserialize(TextUtil.formatCommandHeader("SERVER EVENTS"));
        Inventory inv = Bukkit.createInventory(new EventsHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Event 1: Blood Moon / Discipline
        ItemStack bloodMoon = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta bMeta = bloodMoon.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(miniMessage.deserialize("<gradient:#8B0000:#FF0000><bold>" + TextUtil.toSmallCaps("Discipline Blood Moon") + "</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Triggers server-wide soul gathering & crimson skies.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Trigger", NamedTextColor.RED));
            bMeta.lore(lore);
            bloodMoon.setItemMeta(bMeta);
        }
        inv.setItem(11, bloodMoon);

        // Event 2: Celestial Altar Descends
        ItemStack celestial = new ItemStack(Material.BEACON);
        ItemMeta cMeta = celestial.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(miniMessage.deserialize("<gradient:#FFFFFF:#FFD700><bold>" + TextUtil.toSmallCaps("Celestial Altar Descends") + "</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Summons holy beams and divine blessings on online players.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Trigger", NamedTextColor.YELLOW));
            cMeta.lore(lore);
            celestial.setItemMeta(cMeta);
        }
        inv.setItem(13, celestial);

        // Event 3: Void Rift Collapse
        ItemStack voidRift = new ItemStack(Material.ENDER_EYE);
        ItemMeta vMeta = voidRift.getItemMeta();
        if (vMeta != null) {
            vMeta.displayName(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>" + TextUtil.toSmallCaps("Void Rift Collapse") + "</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Opens localized gravitational anomalies in the world.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Trigger", NamedTextColor.DARK_PURPLE));
            vMeta.lore(lore);
            voidRift.setItemMeta(vMeta);
        }
        inv.setItem(15, voidRift);

        inv.setItem(22, createBackButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    // ─── CLICK HANDLERS ──────────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;

        // Check if it's one of our menus
        if (holder instanceof MainHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 10) openWeaponsMenu(player);
            else if (slot == 12) openCraftingMenu(player);
            else if (slot == 14) openRecipesMenu(player);
            else if (slot == 16) openEventsMenu(player);
        } else if (holder instanceof WeaponsHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 31) {
                openMenu(player);
                return;
            }
            String weaponId = weaponSlots.get(slot);
            if (weaponId != null) {
                LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponId);
                if (weapon != null) {
                    player.getInventory().addItem(weapon.createItem());
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                    player.sendMessage(Component.text("✦ Received ", NamedTextColor.GREEN).append(weapon.getDisplayName()));
                }
            }
        } else if (holder instanceof CraftingHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 22) {
                openMenu(player);
                return;
            }
            if (slot == 11) {
                player.getInventory().addItem(RelicItem.createRelic(plugin, RelicItem.RelicType.INIQUITY));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                player.sendMessage(Component.text("✦ Received Iniquity!", NamedTextColor.DARK_RED));
            } else if (slot == 13) {
                player.getInventory().addItem(RelicItem.createRelic(plugin, RelicItem.RelicType.IMPIETY));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                player.sendMessage(Component.text("✦ Received Impiety!", NamedTextColor.GOLD));
            } else if (slot == 15) {
                player.getInventory().addItem(RelicItem.createRelic(plugin, RelicItem.RelicType.OBSCURA));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                player.sendMessage(Component.text("✦ Received Obscura!", NamedTextColor.DARK_PURPLE));
            }
        } else if (holder instanceof RecipesHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                openMenu(player);
            }
        } else if (holder instanceof EventsHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 22) {
                openMenu(player);
                return;
            }
            if (slot == 11) {
                // Trigger Blood Moon event
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 2, 0), 40, 1.0, 1.0, 1.0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 2.0f));
                    p.sendMessage(miniMessage.deserialize("<gradient:#8B0000:#FF0000><bold>✦ ᴛʜᴇ ᴅɪꜱᴄɪᴘʟɪɴᴇ ʙʟᴏᴏᴅ ᴍᴏᴏɴ ʜᴀꜱ ᴀᴡᴀᴋᴇɴᴇᴅ!</bold></gradient>"));
                }
                player.closeInventory();
            } else if (slot == 13) {
                // Trigger Celestial Altar event
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.5f);
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.8, 1.2, 0.8, 0.2);
                    p.sendMessage(miniMessage.deserialize("<gradient:#FFFFFF:#FFD700><bold>✦ ᴛʜᴇ ᴄᴇʟᴇꜱᴛɪᴀʟ ᴀʟᴛᴀʀ ʙᴇꜱᴛᴏᴡꜱ ɪᴛꜱ ɢʀᴀᴄᴇ!</bold></gradient>"));
                }
                player.closeInventory();
            } else if (slot == 15) {
                // Trigger Void Rift event
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.2f);
                    p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.8, 1.0, 0.8, 0.4);
                    p.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#9400D3><bold>✦ ᴀ ᴠᴏɪᴅ ʀɪғᴛ ʜᴀꜱ ᴄᴏʟʟᴀᴘꜱᴇᴅ ᴛʜᴇ ᴀᴛᴍᴏꜱᴘʜᴇʀᴇ!</bold></gradient>"));
                }
                player.closeInventory();
            }
        }
    }
}
