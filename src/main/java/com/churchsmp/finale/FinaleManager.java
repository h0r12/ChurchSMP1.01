package com.churchsmp.finale;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the ChurchSMP Finale:
 * - Juggernaut Purge (/churchadmin purge <player> and Survival Bounty Purge)
 * - Obscura bestowal and Juggernaut colossal buffs
 * - Global Sin Shattering across all online players
 * - Lost Soul spectator mode with ambient particles
 * - Liminal Null Control Room (/church null) with customizable server rules
 */
public class FinaleManager implements Listener {

    public enum ArmorTierCap {
        UNCAPPED("Uncapped", Material.NETHERITE_CHESTPLATE, TextColor.color(0x55FF55)),
        NETHERITE("Netherite Max", Material.NETHERITE_CHESTPLATE, TextColor.color(0x4F4846)),
        DIAMOND("Diamond Max", Material.DIAMOND_CHESTPLATE, TextColor.color(0x4dedf4)),
        IRON("Iron Max", Material.IRON_CHESTPLATE, TextColor.color(0xd8d8d8)),
        LEATHER("Leather Max", Material.LEATHER_CHESTPLATE, TextColor.color(0xc4693b));

        private final String display;
        private final Material icon;
        private final TextColor color;

        ArmorTierCap(String display, Material icon, TextColor color) {
            this.display = display;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplay() { return display; }
        public Material getIcon() { return icon; }
        public TextColor getColor() { return color; }

        public ArmorTierCap next() {
            ArmorTierCap[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public boolean isExceeding(Material armorPiece) {
            if (this == UNCAPPED || armorPiece == null) return false;
            String name = armorPiece.name();
            switch (this) {
                case LEATHER -> {
                    return name.contains("IRON_") || name.contains("GOLDEN_") || name.contains("CHAINMAIL_") ||
                            name.contains("DIAMOND_") || name.contains("NETHERITE_");
                }
                case IRON -> {
                    return name.contains("DIAMOND_") || name.contains("NETHERITE_");
                }
                case DIAMOND -> {
                    return name.contains("NETHERITE_");
                }
                case NETHERITE -> {
                    return false;
                }
                default -> {
                    return false;
                }
            }
        }
    }

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Juggernaut & Purge State
    private UUID juggernautUuid = null;
    private BossBar juggernautBossBar = null;
    private final Set<SinGemType> brokenSins = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> lostSouls = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<SinGemType>> sinKillTracker = new ConcurrentHashMap<>();
    private final Map<UUID, Location> previousLocations = new ConcurrentHashMap<>();

    // Server Control Room Settings
    private ArmorTierCap maxArmorTier = ArmorTierCap.UNCAPPED;
    private int maxPotionLevel = 0; // 0 = uncapped, 1 = Max I, 2 = Max II
    private boolean resetInitiated = false;

    // Background Tasks
    private BukkitTask ambientSoulTask = null;
    private BukkitTask juggernautPerkTask = null;
    private BukkitTask armorEnforcementTask = null;

    public FinaleManager(ChurchSMP plugin) {
        this.plugin = plugin;
        startAmbientTasks();
    }

    /* -------------------------------------------------------------
     * PURGE ACTIVATION & TERMINATION
     * ------------------------------------------------------------- */

    public boolean isPurgeActive() {
        return juggernautUuid != null;
    }

    public UUID getJuggernautUuid() {
        return juggernautUuid;
    }

    public boolean isJuggernaut(UUID uuid) {
        return juggernautUuid != null && juggernautUuid.equals(uuid);
    }

    public boolean isLostSoul(UUID uuid) {
        return lostSouls.contains(uuid);
    }

    public Set<SinGemType> getBrokenSins() {
        return Collections.unmodifiableSet(brokenSins);
    }

    /**
     * Starts the Purge designating the specified player as Juggernaut.
     */
    public void startPurge(Player juggernaut, boolean survivalAscension) {
        if (juggernaut == null || !juggernaut.isOnline()) return;

        this.juggernautUuid = juggernaut.getUniqueId();
        this.brokenSins.clear();
        this.resetInitiated = false;

        // Bestow Obscura
        ItemStack obscura = RelicItem.createRelic(plugin, RelicItem.RelicType.OBSCURA);
        if (!juggernaut.getInventory().contains(obscura.getType())) {
            juggernaut.getInventory().addItem(obscura);
        }

        // Apply Juggernaut Colossal Attributes
        AttributeInstance maxHealthAttr = juggernaut.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(100.0); // 50 Hearts
            juggernaut.setHealth(100.0);
        }
        applyJuggernautBuffs(juggernaut);

        // Create Global BossBar
        updateBossBar(juggernaut);

        // Middle Screen Alert to all players
        Component mainTitle = miniMessage.deserialize("<gradient:#FF0000:#8B0000><bold>THE PURGE HAS BEGUN</bold></gradient>");
        Component subTitle = miniMessage.deserialize("<gold><bold>" + juggernaut.getName() + "</bold> <yellow>has awakened as the Juggernaut.</yellow></gold>");
        Title title = Title.title(mainTitle, subTitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 0.6f);
            if (juggernautBossBar != null) {
                p.showBossBar(juggernautBossBar);
            }
        }

        // Global Chat Broadcast
        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>⚔ CHURCHSMP FINALE: PURGE OF CREATION ⚔</bold></dark_red>"));
        if (survivalAscension) {
            Bukkit.broadcast(miniMessage.deserialize("<white>" + juggernaut.getName() + "</white> <gray>has harvested three Sin Gem wielders and unlocked the</gray> <red><bold>JUGGERNAUT</bold></red> <gray>ascension!</gray>"));
        } else {
            Bukkit.broadcast(miniMessage.deserialize("<white>" + juggernaut.getName() + "</white> <gray>was decreed by divine providence to lead the</gray> <red><bold>JUGGERNAUT PURGE</bold></red><gray>!</gray>"));
        }
        Bukkit.broadcast(miniMessage.deserialize("<dark_purple>✦ All seven Sin Relics must be eradicated before reality fractures.</dark_purple>"));
        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));
    }

    /**
     * Clears the Purge, resetting Juggernaut status and reviving all Lost Souls.
     */
    public void clearPurge(Player adminCaller) {
        if (juggernautBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(juggernautBossBar);
            }
            juggernautBossBar = null;
        }

        // Reset Juggernaut health & attributes
        if (juggernautUuid != null) {
            Player juggernaut = Bukkit.getPlayer(juggernautUuid);
            if (juggernaut != null && juggernaut.isOnline()) {
                AttributeInstance maxHealthAttr = juggernaut.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(20.0);
                    if (juggernaut.getHealth() > 20.0) juggernaut.setHealth(20.0);
                }
                juggernaut.removePotionEffect(PotionEffectType.RESISTANCE);
                juggernaut.removePotionEffect(PotionEffectType.STRENGTH);
                juggernaut.removePotionEffect(PotionEffectType.SPEED);
                juggernaut.removePotionEffect(PotionEffectType.GLOWING);
                juggernaut.sendMessage(miniMessage.deserialize("<yellow>✦ Your Juggernaut mantle has dissolved.</yellow>"));
            }
        }

        // Revive all Lost Souls back to survival
        for (UUID soulUuid : new HashSet<>(lostSouls)) {
            Player soul = Bukkit.getPlayer(soulUuid);
            if (soul != null && soul.isOnline()) {
                restoreLostSoul(soul);
            }
        }
        lostSouls.clear();
        brokenSins.clear();
        sinKillTracker.clear();
        juggernautUuid = null;
        resetInitiated = false;

        // Reset Server rule settings to default
        maxArmorTier = ArmorTierCap.UNCAPPED;
        maxPotionLevel = 0;

        Component notice = miniMessage.deserialize("<gold>✦ [PURGE] <green>The Purge has been cleared. All Lost Souls have been restored to reality!</green> ✦</gold>");
        Bukkit.broadcast(notice);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
    }

    /* -------------------------------------------------------------
     * SIN SHATTERING & KILL HANDLING
     * ------------------------------------------------------------- */

    /**
     * Handles death events involving Juggernauts or prospective survival ascenders.
     */
    public void onPlayerKill(Player killer, Player victim) {
        if (killer == null || victim == null) return;

        // Case 1: Killer is active Juggernaut
        if (isJuggernaut(killer.getUniqueId())) {
            SinGemType victimSin = plugin.getSinGemManager().getAttunedGem(victim);
            if (victimSin == null) {
                victimSin = plugin.getSinGemManager().getHeldGem(victim);
            }

            // Shatter that Sin if present
            if (victimSin != null) {
                shatterSin(victimSin, killer, victim);
            }

            // Put victim into "Lost Soul" spectator mode
            makeLostSoul(victim);
            return;
        }

        // Case 2: Purge is NOT active — Track "The Bounty Purge" for Survival Ascension
        if (!isPurgeActive()) {
            SinGemType victimSin = plugin.getSinGemManager().getAttunedGem(victim);
            if (victimSin == null) {
                victimSin = plugin.getSinGemManager().getHeldGem(victim);
            }

            if (victimSin != null) {
                Set<SinGemType> harvested = sinKillTracker.computeIfAbsent(killer.getUniqueId(), k -> new HashSet<>());
                harvested.add(victimSin);

                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                killer.sendMessage(miniMessage.deserialize("<dark_purple>✦ [BOUNTY PURGE] Slayed Sin: <bold>" + victimSin.getDisplayName() + "</bold> (" + harvested.size() + "/3 required for Juggernaut Ascension)</dark_purple>"));

                if (harvested.size() >= 3) {
                    startPurge(killer, true);
                }
            }
        }
    }

    /**
     * Shatters a Sin globally across EVERY player on the server!
     */
    public void shatterSin(SinGemType sin, Player juggernaut, Player victim) {
        if (sin == null) return;
        boolean firstTime = brokenSins.add(sin);

        // Update BossBar
        if (juggernaut != null) {
            updateBossBar(juggernaut);
        }

        // Global chat broadcast with EXACT requested text:
        // "[Sin Broken] lost its definition across every creation."
        Component broadcastMsg = miniMessage.deserialize(
                "<gold>⚡</gold> <dark_red><bold>[" + sin.getDisplayName() + " Broken]</bold> lost its definition across every creation.</dark_red> <gold>⚡</gold>"
        );
        Bukkit.broadcast(broadcastMsg);

        // Global Middle Screen Alert
        Component shatterTitle = miniMessage.deserialize("<dark_red><bold>" + sin.getDisplayName().toUpperCase(Locale.ROOT) + " BROKEN</bold></dark_red>");
        Component shatterSub = miniMessage.deserialize("<gray>Lost its definition across every creation.</gray>");
        Title title = Title.title(shatterTitle, shatterSub, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800)));

        // Global sounds and inventory scanning across ALL online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);

            // Scan inventory for this Sin's Gem and shatter it
            boolean hadGem = false;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && plugin.getSinGemManager().getGemType(item) == sin) {
                    p.getInventory().setItem(i, null);
                    hadGem = true;
                }
            }

            if (hadGem) {
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, new Particle.DustOptions(org.bukkit.Color.fromRGB(40, 40, 40), 1.5f));
                p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, sin.getIconMaterial().createBlockData());
                p.sendMessage(miniMessage.deserialize("<dark_red>⚡ The " + sin.getDisplayName() + " Gem inside your inventory cracked and dissolved into dust!</dark_red>"));
            }
        }

        // Check if all 7 sins are broken!
        if (brokenSins.size() >= 7) {
            onAllSinsBroken(juggernaut);
        }
    }

    /**
     * Triggered when all 7 sins have been broken. Invites Juggernaut to the Liminal Null.
     */
    private void onAllSinsBroken(Player juggernaut) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);
        }

        Component titleText = miniMessage.deserialize("<gradient:#4B0082:#00CED1><bold>ALL SINS EXTINCT</bold></gradient>");
        Component subText = miniMessage.deserialize("<gold>Reality is unanchored. The Liminal Null awaits.</gold>");
        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<dark_purple><bold>✦ ALL SEVEN SINS HAVE BEEN EXTIRPATED FROM CREATION ✦</bold></dark_purple>"));
        Bukkit.broadcast(miniMessage.deserialize("<gray>The ancient cycle of sin is severed. The Juggernaut holds the fate of this realm.</gray>"));
        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));

        if (juggernaut != null && juggernaut.isOnline()) {
            juggernaut.showTitle(Title.title(titleText, subText, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            juggernaut.sendMessage(miniMessage.deserialize("\n<gradient:#4B0082:#00CED1><bold>✦ [THE LIMINAL NULL BECKONS] ✦</bold></gradient>"));
            juggernaut.sendMessage(miniMessage.deserialize("<white>All definitions have collapsed. Step into the central control room to decide the realm's fate.</white>"));
            juggernaut.sendMessage(miniMessage.deserialize("<gold><bold>Type <yellow><underlined>/church null</underlined></yellow> to enter the Liminal Null Control Room.</bold></gold>\n"));
        }
    }

    /* -------------------------------------------------------------
     * "LOST SOUL" SPECTATOR MODE
     * ------------------------------------------------------------- */

    public void makeLostSoul(Player victim) {
        if (victim == null || !victim.isOnline()) return;

        lostSouls.add(victim.getUniqueId());
        victim.setGameMode(GameMode.SPECTATOR);

        Component mainTitle = miniMessage.deserialize("<dark_gray><bold>LOST SOUL</bold></dark_gray>");
        Component subTitle = miniMessage.deserialize("<gray>Purified by the Juggernaut... Your essence floats in limbo.</gray>");
        victim.showTitle(Title.title(mainTitle, subTitle, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        victim.playSound(victim.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.8f);

        victim.sendMessage(miniMessage.deserialize("<dark_gray>☠ You have become a <white><bold>Lost Soul</bold></white>. You wander as an ethereal specter until the Juggernaut's decree.</dark_gray>"));
    }

    public void restoreLostSoul(Player soul) {
        if (soul == null || !soul.isOnline()) return;
        lostSouls.remove(soul.getUniqueId());
        soul.setGameMode(GameMode.SURVIVAL);

        AttributeInstance maxHealthAttr = soul.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(20.0);
            soul.setHealth(20.0);
        }

        Component title = miniMessage.deserialize("<green><bold>SOUL RESTORED</bold></green>");
        Component sub = miniMessage.deserialize("<white>You have been returned to physical reality.</white>");
        soul.showTitle(Title.title(title, sub, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        soul.playSound(soul.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    /* -------------------------------------------------------------
     * LIMINAL NULL & CONTROL ROOM CONSOLE
     * ------------------------------------------------------------- */

    public Location getLiminalNullPlatform() {
        World world = Bukkit.getWorlds().get(0);
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.THE_END) {
                world = w;
                break;
            }
        }

        Location center = new Location(world, 0.5, 180.0, 0.5);

        // Generate clean circular void control platform if not present
        if (center.clone().subtract(0, 1, 0).getBlock().getType() != Material.CRYING_OBSIDIAN) {
            int radius = 6;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        Material mat = (x * x + z * z <= 3) ? Material.PURPUR_BLOCK : Material.CRYING_OBSIDIAN;
                        center.clone().add(x, -1, z).getBlock().setType(mat);
                        center.clone().add(x, 0, z).getBlock().setType(Material.AIR);
                        center.clone().add(x, 1, z).getBlock().setType(Material.AIR);
                        center.clone().add(x, 2, z).getBlock().setType(Material.AIR);
                    }
                }
            }
            center.clone().subtract(0, 1, 0).getBlock().setType(Material.BEACON);
        }

        return center;
    }

    public boolean isInLiminalNull(Player player) {
        Location loc = player.getLocation();
        Location nullLoc = getLiminalNullPlatform();
        return loc.getWorld().equals(nullLoc.getWorld()) && loc.distanceSquared(nullLoc) < 400;
    }

    /**
     * Toggles player between Liminal Null and their survival location.
     */
    public void toggleLiminalNull(Player player) {
        if (!isJuggernaut(player.getUniqueId()) && !player.hasPermission("churchsmp.admin")) {
            player.sendMessage(miniMessage.deserialize("<red>Only the Juggernaut (or an Administrator) may breach the Liminal Null.</red>"));
            return;
        }

        if (isInLiminalNull(player)) {
            // Return to previous location
            Location prev = previousLocations.remove(player.getUniqueId());
            if (prev == null || prev.getWorld() == null) {
                prev = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            player.teleport(prev);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
            player.sendMessage(miniMessage.deserialize("<yellow>✦ Returned from the Liminal Null to physical reality.</yellow>"));
        } else {
            // Teleport into Liminal Null
            previousLocations.put(player.getUniqueId(), player.getLocation());
            Location target = getLiminalNullPlatform();
            player.teleport(target);
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
            player.sendMessage(miniMessage.deserialize("<gradient:#4B0082:#00CED1><bold>✦ Welcome to the Liminal Null Control Room. ✦</bold></gradient>"));
            player.sendMessage(miniMessage.deserialize("<gray>Opening master control console...</gray>"));
            openControlRoomGUI(player);
        }
    }

    /**
     * Opens the 27-slot Liminal Null Master Control Room GUI.
     */
    public void openControlRoomGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("LIMINAL NULL CONTROL ROOM", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

        // Background filler glass
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 10: Max Armor Tier Selector
        ItemStack armorItem = new ItemStack(maxArmorTier.getIcon());
        ItemMeta aMeta = armorItem.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(Component.text("Max Armor Tier: ", NamedTextColor.GOLD)
                    .append(Component.text(maxArmorTier.getDisplay(), maxArmorTier.getColor()).decorate(TextDecoration.BOLD)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Controls the maximum allowed armor tier", NamedTextColor.GRAY));
            lore.add(Component.text("across all mortal players in the server.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Current: ", NamedTextColor.WHITE).append(Component.text(maxArmorTier.getDisplay(), maxArmorTier.getColor())));
            lore.add(Component.text("✦ Click to Cycle Tier", NamedTextColor.YELLOW));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            aMeta.lore(lore);
            aMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            armorItem.setItemMeta(aMeta);
        }
        inv.setItem(10, armorItem);

        // Slot 12: Max Potion Stats Selector
        String potStr = (maxPotionLevel == 0) ? "UNCAPPED" : (maxPotionLevel == 1 ? "MAX LEVEL I" : "MAX LEVEL II");
        ItemStack potItem = new ItemStack(Material.BREWING_STAND);
        ItemMeta pMeta = potItem.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(Component.text("Max Potion Potency: ", NamedTextColor.AQUA)
                    .append(Component.text(potStr, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Caps the maximum amplifier on potion effects", NamedTextColor.GRAY));
            lore.add(Component.text("to ensure balanced post-purge skirmishes.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Current: ", NamedTextColor.WHITE).append(Component.text(potStr, NamedTextColor.YELLOW)));
            lore.add(Component.text("✦ Click to Cycle Potency", NamedTextColor.YELLOW));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            pMeta.lore(lore);
            potItem.setItemMeta(pMeta);
        }
        inv.setItem(12, potItem);

        // Slot 14: Cataclysm / Atmospheric Toggle
        ItemStack weatherItem = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta wMeta = weatherItem.getItemMeta();
        if (wMeta != null) {
            wMeta.displayName(Component.text("Divine Interventions", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Invoke celestial anomalies upon the world:", NamedTextColor.GRAY));
            lore.add(Component.text("• Trigger Blood Moon", NamedTextColor.DARK_RED));
            lore.add(Component.text("• Trigger Holy Inquisition", NamedTextColor.GOLD));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Toggle Event", NamedTextColor.YELLOW));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            wMeta.lore(lore);
            weatherItem.setItemMeta(wMeta);
        }
        inv.setItem(14, weatherItem);

        // Slot 16: The Great Choice - Option A: Revive Everyone (Utopia)
        ItemStack reviveItem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta rMeta = reviveItem.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(miniMessage.deserialize("<green><bold>REVIVE EVERYONE (UTOPIA)</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Pardon all mortal souls.", NamedTextColor.WHITE));
            lore.add(Component.text("Clears all Lost Souls and restores all players", NamedTextColor.GRAY));
            lore.add(Component.text("back into physical reality with full health.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Active Lost Souls: ", NamedTextColor.AQUA).append(Component.text(lostSouls.size(), NamedTextColor.YELLOW)));
            lore.add(Component.text("✦ Click to Decree Utopia", NamedTextColor.GREEN));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            rMeta.lore(lore);
            reviveItem.setItemMeta(rMeta);
        }
        inv.setItem(16, reviveItem);

        // Slot 22: The Great Choice - Option B: End the Server (The Great Reset)
        ItemStack resetItem = new ItemStack(Material.WITHER_ROSE);
        ItemMeta resMeta = resetItem.getItemMeta();
        if (resMeta != null) {
            resMeta.displayName(miniMessage.deserialize("<dark_red><bold>END THE SERVER (GREAT RESET)</bold></dark_red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("The final cataclysm of creation.", NamedTextColor.WHITE));
            lore.add(Component.text("Triggers a global cataclysmic event, extinguishing", NamedTextColor.RED));
            lore.add(Component.text("the server into the eternal void.", NamedTextColor.RED));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Click to Initiate Cataclysm", NamedTextColor.DARK_RED));
            lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
            resMeta.lore(lore);
            resetItem.setItemMeta(resMeta);
        }
        inv.setItem(22, resetItem);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Executes the Utopia Revive decree.
     */
    public void decreeUtopiaRevive(Player decreeGiver) {
        for (UUID soulUuid : new HashSet<>(lostSouls)) {
            Player soul = Bukkit.getPlayer(soulUuid);
            if (soul != null && soul.isOnline()) {
                restoreLostSoul(soul);
            }
        }
        lostSouls.clear();

        Component title = miniMessage.deserialize("<green><bold>UTOPIA DECREED</bold></green>");
        Component sub = miniMessage.deserialize("<white>" + (decreeGiver != null ? decreeGiver.getName() : "The Juggernaut") + " has restored life to every creation!</white>");
        Title t = Title.title(title, sub, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.0f);
        }

        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));
        Bukkit.broadcast(miniMessage.deserialize("<green><bold>✦ THE GREAT UTOPIA RESTORATION ✦</bold></green>"));
        Bukkit.broadcast(miniMessage.deserialize("<white>The Juggernaut chose mercy. All Lost Souls have been restored to physical reality.</white>"));
        Bukkit.broadcast(miniMessage.deserialize("<gold>════════════════════════════════════════════════════════════</gold>"));
    }

    /**
     * Executes the Cataclysm End-Server decree.
     */
    public void decreeGreatReset(Player decreeGiver) {
        if (resetInitiated) return;
        this.resetInitiated = true;

        Bukkit.broadcast(miniMessage.deserialize("<dark_red>════════════════════════════════════════════════════════════</dark_red>"));
        Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>☠ THE GREAT RESET INITIATED ☠</bold></dark_red>"));
        Bukkit.broadcast(miniMessage.deserialize("<white>" + (decreeGiver != null ? decreeGiver.getName() : "The Juggernaut") + " has condemned creation to the void.</white>"));
        Bukkit.broadcast(miniMessage.deserialize("<gray>Reality disintegrates in 10 seconds...</gray>"));
        Bukkit.broadcast(miniMessage.deserialize("<dark_red>════════════════════════════════════════════════════════════</dark_red>"));

        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    Component cTitle = miniMessage.deserialize("<dark_red><bold>DISINTEGRATION: " + countdown + "</bold></dark_red>");
                    Component cSub = miniMessage.deserialize("<gray>All creation is dissolving...</gray>");
                    Title t = Title.title(cTitle, cSub, Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200)));

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(t);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 1, false, false));
                        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 20, 1.0, 1.0, 1.0, 0.1);
                    }
                    countdown--;
                } else {
                    // Final catastrophic flash
                    cancel();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 0.5f);
                        p.showTitle(Title.title(
                                miniMessage.deserialize("<black><bold>VOID CONSUMED</bold></black>"),
                                miniMessage.deserialize("<red>The cycle is concluded.</red>"),
                                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(2))
                        ));
                    }
                    Bukkit.broadcast(miniMessage.deserialize("<dark_red><bold>Creation has ended.</bold></dark_red>"));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /* -------------------------------------------------------------
     * BACKGROUND TASKS & JUGGERNAUT BUFFS
     * ------------------------------------------------------------- */

    private void applyJuggernautBuffs(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
    }

    private void updateBossBar(Player juggernaut) {
        Component title = miniMessage.deserialize(
                "<gradient:#FF0000:#8B0000><bold>⚔ JUGGERNAUT: " + juggernaut.getName() + "</bold></gradient> <gray>| Sins Shattered: </gray><yellow><bold>" + brokenSins.size() + "/7</bold></yellow>"
        );
        float progress = Math.min(1.0f, brokenSins.size() / 7.0f);

        if (juggernautBossBar == null) {
            juggernautBossBar = BossBar.bossBar(title, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(juggernautBossBar);
            }
        } else {
            juggernautBossBar.name(title);
            juggernautBossBar.progress(progress);
        }
    }

    private void startAmbientTasks() {
        // 1. Ambient Moody Particles for Lost Souls
        this.ambientSoulTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : lostSouls) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        Location loc = p.getLocation().add(0, 0.8, 0);
                        p.getWorld().spawnParticle(Particle.SOUL, loc, 3, 0.3, 0.4, 0.3, 0.02);
                        p.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.3, 0.2, 0.01);
                        p.getWorld().spawnParticle(Particle.ASH, loc, 4, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);

        // 2. Juggernaut Aura & Knockback/Health Maintainer
        this.juggernautPerkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (juggernautUuid == null) return;
                Player juggernaut = Bukkit.getPlayer(juggernautUuid);
                if (juggernaut == null || !juggernaut.isOnline()) return;

                // Ensure buffs are sustained
                if (!juggernaut.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                    applyJuggernautBuffs(juggernaut);
                }

                // Ambient crimson cleansing aura around Juggernaut
                Location loc = juggernaut.getLocation().add(0, 1.0, 0);
                juggernaut.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.4, 0.6, 0.4, new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.2f));
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // 3. Armor Tier Enforcement Task
        this.armorEnforcementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (maxArmorTier == ArmorTierCap.UNCAPPED) return;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isJuggernaut(p.getUniqueId())) continue; // Juggernaut bypasses armor cap

                    ItemStack[] armor = p.getInventory().getArmorContents();
                    boolean modified = false;
                    for (int i = 0; i < armor.length; i++) {
                        ItemStack piece = armor[i];
                        if (piece != null && maxArmorTier.isExceeding(piece.getType())) {
                            armor[i] = null;
                            p.getWorld().dropItemNaturally(p.getLocation(), piece);
                            modified = true;
                        }
                    }
                    if (modified) {
                        p.getInventory().setArmorContents(armor);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 0.5f);
                        p.sendMessage(miniMessage.deserialize("<red>✦ Your armor exceeds the server cap (Max: " + maxArmorTier.getDisplay() + ") and was unequipped!</red>"));
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    /* -------------------------------------------------------------
     * EVENT LISTENERS FOR ENFORCEMENT & GUI
     * ------------------------------------------------------------- */

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (juggernautBossBar != null) {
            player.showBossBar(juggernautBossBar);
        }
        if (isLostSoul(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(miniMessage.deserialize("<dark_gray>☠ You are still a Lost Soul awaiting the Juggernaut's decree.</dark_gray>"));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isLostSoul(player.getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (isLostSoul(event.getPlayer().getUniqueId()) && event.getNewGameMode() != GameMode.SPECTATOR) {
            if (!event.getPlayer().hasPermission("churchsmp.admin")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(miniMessage.deserialize("<red>Lost Souls cannot return to physical reality until decree.</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (maxPotionLevel <= 0) return;
        if (event.getNewEffect() == null) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (isJuggernaut(player.getUniqueId())) return;

        PotionEffect effect = event.getNewEffect();
        if (effect.getAmplifier() >= maxPotionLevel) {
            event.setCancelled(true);
            PotionEffect capped = new PotionEffect(
                    effect.getType(),
                    effect.getDuration(),
                    maxPotionLevel - 1,
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
            );
            player.addPotionEffect(capped);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if clicking inside Liminal Null Control Room GUI
        if (event.getView().title().equals(Component.text("LIMINAL NULL CONTROL ROOM", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))) {
            event.setCancelled(true);

            if (!isJuggernaut(player.getUniqueId()) && !player.hasPermission("churchsmp.admin")) {
                player.closeInventory();
                return;
            }

            int slot = event.getRawSlot();
            switch (slot) {
                case 10 -> {
                    // Cycle Armor Tier Cap
                    this.maxArmorTier = this.maxArmorTier.next();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    openControlRoomGUI(player);
                    Bukkit.broadcast(miniMessage.deserialize("<gold>✦ [CONTROL ROOM] Max Armor Tier adjusted to: </gold>" + maxArmorTier.getDisplay()));
                }
                case 12 -> {
                    // Cycle Potion Cap
                    this.maxPotionLevel = (this.maxPotionLevel + 1) % 3; // 0, 1, 2
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    openControlRoomGUI(player);
                    String potStr = (maxPotionLevel == 0) ? "UNCAPPED" : (maxPotionLevel == 1 ? "MAX LEVEL I" : "MAX LEVEL II");
                    Bukkit.broadcast(miniMessage.deserialize("<aqua>✦ [CONTROL ROOM] Max Potion Potency adjusted to: </aqua><yellow>" + potStr + "</yellow>"));
                }
                case 14 -> {
                    // Toggle Event
                    if (plugin.getChurchEventManager().isEventActive()) {
                        plugin.getChurchEventManager().stopCurrentEvent();
                        player.sendMessage(miniMessage.deserialize("<yellow>✦ Active server event stopped.</yellow>"));
                    } else {
                        plugin.getChurchEventManager().startEvent(com.churchsmp.event.ChurchEventManager.EventType.BLOOD_MOON, 20 * 60 * 15);
                        player.sendMessage(miniMessage.deserialize("<dark_red>✦ Blood Moon cataclysm invoked upon the realm.</dark_red>"));
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    openControlRoomGUI(player);
                }
                case 16 -> {
                    // Revive Everyone (Utopia)
                    decreeUtopiaRevive(player);
                    player.closeInventory();
                }
                case 22 -> {
                    // End the Server (Great Reset)
                    decreeGreatReset(player);
                    player.closeInventory();
                }
            }
        }
    }
}
