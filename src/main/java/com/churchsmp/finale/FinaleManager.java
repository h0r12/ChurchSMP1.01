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
     * PHYSICAL ALTAR & CONTROL ROOM GENERATION
     * ------------------------------------------------------------- */

    public void spawnAltar(Location loc) {
        World w = loc.getWorld();
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        // Build a 3x3 base
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.CRYING_OBSIDIAN);
            }
        }
        // Center pillar
        w.getBlockAt(cx, cy, cz).setType(Material.LODESTONE);
        w.getBlockAt(cx, cy + 1, cz).setType(Material.DRAGON_EGG);

        // Spawn some particles to mark it
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 1200) cancel(); // runs for 1 minute
                w.spawnParticle(Particle.PORTAL, cx + 0.5, cy + 1.5, cz + 0.5, 10, 0.5, 0.5, 0.5, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private Location controlRoomLocation = null;

    public void teleportToPhysicalControlRoom(Player player) {
        if (controlRoomLocation == null) {
            World w = Bukkit.getWorlds().get(0);
            int cx = 10000;
            int cy = 250;
            int cz = 10000;

            // Generate 11x7x11 Bedrock Room
            for (int x = -5; x <= 5; x++) {
                for (int y = 0; y <= 6; y++) {
                    for (int z = -5; z <= 5; z++) {
                        if (y == 0 || y == 6 || x == -5 || x == 5 || z == -5 || z == 5) {
                            w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.BEDROCK);
                        } else {
                            w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.AIR);
                        }
                    }
                }
            }

            // Decorate inside
            w.getBlockAt(cx, cy, cz).setType(Material.CRYING_OBSIDIAN); // Center floor
            w.getBlockAt(cx, cy + 1, cz).setType(Material.LODESTONE); // Visual center
            w.getBlockAt(cx, cy + 6, cz).setType(Material.GLOWSTONE);

            // Place Buttons on the North Wall (z = -4)
            placeControlButton(w, cx - 2, cy + 2, cz - 4, "Armor Tier", "Cycles Max Armor");
            placeControlButton(w, cx - 1, cy + 2, cz - 4, "Potion Cap", "Cycles Max Potion");
            placeControlButton(w, cx, cy + 2, cz - 4, "Toggle Event", "Start/Stop Events");
            placeControlButton(w, cx + 1, cy + 2, cz - 4, "Revive All", "Utopia Decree");
            placeControlButton(w, cx + 2, cy + 2, cz - 4, "Great Reset", "End the World");

            // More Configs on East Wall (x = 4)
            placeControlButton(w, cx + 4, cy + 2, cz - 2, "Natural Regen", "Toggle On/Off");
            placeControlButton(w, cx + 4, cy + 2, cz, "Time Lock", "Toggle Night/Day");
            placeControlButton(w, cx + 4, cy + 2, cz + 2, "Keep Inventory", "Toggle On/Off");

            controlRoomLocation = new Location(w, cx + 0.5, cy + 1, cz + 3.5, 180, 0); // Face North
        }

        player.teleport(controlRoomLocation);
        player.setGameMode(GameMode.ADVENTURE);
        player.sendMessage(miniMessage.deserialize("<dark_purple><bold>✦ WELCOME TO THE LIMINAL NULL ✦</bold></dark_purple>"));
        player.sendMessage(miniMessage.deserialize("<gray>Use the buttons on the walls to rewrite the laws of the universe.</gray>"));
        
        if (plugin.getAdvancementManager() != null) {
            plugin.getAdvancementManager().grantAdvancement(player, "liminal_null");
        }
    }

    private void placeControlButton(World w, int x, int y, int z, String title, String sub) {
        org.bukkit.block.Block block = w.getBlockAt(x, y, z);
        block.setType(Material.WARPED_BUTTON);
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Switch btn) {
            // Facing out from wall
            if (z == 10000 - 4) btn.setFacing(org.bukkit.block.BlockFace.SOUTH);
            else if (x == 10000 + 4) btn.setFacing(org.bukkit.block.BlockFace.WEST);
            block.setBlockData(btn);
        }

        // Place sign above button
        org.bukkit.block.Block signBlock = w.getBlockAt(x, y + 1, z);
        signBlock.setType(Material.WARPED_WALL_SIGN);
        if (signBlock.getBlockData() instanceof org.bukkit.block.data.type.WallSign signData) {
            if (z == 10000 - 4) signData.setFacing(org.bukkit.block.BlockFace.SOUTH);
            else if (x == 10000 + 4) signData.setFacing(org.bukkit.block.BlockFace.WEST);
            signBlock.setBlockData(signData);
        }

        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) signBlock.getState();
        sign.line(0, Component.text("[ " + title + " ]", NamedTextColor.GOLD, TextDecoration.BOLD));
        sign.line(1, Component.text(sub, NamedTextColor.GRAY));
        sign.update();
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

    public boolean isInLiminalNull(Player player) {
        Location loc = player.getLocation();
        if (controlRoomLocation == null) return false;
        return loc.getWorld().equals(controlRoomLocation.getWorld()) && loc.distanceSquared(controlRoomLocation) < 1000;
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
            player.setGameMode(GameMode.SURVIVAL);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.2f);
            player.sendMessage(miniMessage.deserialize("<yellow>✦ Returned from the Liminal Null to physical reality.</yellow>"));
        } else {
            // Teleport into Liminal Null
            previousLocations.put(player.getUniqueId(), player.getLocation());
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
            teleportToPhysicalControlRoom(player);
        }
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
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // 1. Check for Altar interaction (Dragon Egg on Lodestone with Crying Obsidian)
        if (clicked.getType() == Material.DRAGON_EGG) {
            org.bukkit.block.Block below = clicked.getLocation().subtract(0, 1, 0).getBlock();
            if (below.getType() == Material.LODESTONE) {
                event.setCancelled(true); // Don't teleport the egg
                if (!isPurgeActive()) {
                    plugin.getFinaleManager().startPurge(player, false);
                    Bukkit.broadcast(miniMessage.deserialize("<gold><bold>✦ THE ALTAR HAS BEEN CLAIMED ✦</bold></gold>"));
                    Bukkit.broadcast(miniMessage.deserialize("<gray>" + player.getName() + " has become the Juggernaut!</gray>"));
                    
                    if (plugin.getAdvancementManager() != null) {
                        plugin.getAdvancementManager().grantAdvancement(player, "become_juggernaut");
                        // Also grant to anyone online that the altar spawned, if not already
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            plugin.getAdvancementManager().grantAdvancement(p, "altar_spawn");
                        }
                    }
                } else {
                    player.sendMessage(miniMessage.deserialize("<red>The Purge is already active.</red>"));
                }
                return;
            }
        }

        // 2. Check for Control Room buttons
        if (clicked.getType() == Material.WARPED_BUTTON && controlRoomLocation != null) {
            if (clicked.getWorld().equals(controlRoomLocation.getWorld()) && clicked.getLocation().distanceSquared(controlRoomLocation) < 225) {
                if (!isJuggernaut(player.getUniqueId()) && !player.hasPermission("churchsmp.admin")) {
                    player.sendMessage(miniMessage.deserialize("<red>Only the Juggernaut commands the Liminal Null.</red>"));
                    return;
                }
                
                org.bukkit.block.Block signBlock = clicked.getLocation().add(0, 1, 0).getBlock();
                if (signBlock.getState() instanceof org.bukkit.block.Sign sign) {
                    String titleText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase(java.util.Locale.ROOT);
                    
                    if (titleText.contains("armor tier")) {
                        this.maxArmorTier = this.maxArmorTier.next();
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                        Bukkit.broadcast(miniMessage.deserialize("<gold>✦ [CONTROL ROOM] Max Armor Tier adjusted to: </gold>" + maxArmorTier.getDisplay()));
                    } else if (titleText.contains("potion cap")) {
                        this.maxPotionLevel = (this.maxPotionLevel + 1) % 3;
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                        String potStr = (maxPotionLevel == 0) ? "UNCAPPED" : (maxPotionLevel == 1 ? "MAX LEVEL I" : "MAX LEVEL II");
                        Bukkit.broadcast(miniMessage.deserialize("<aqua>✦ [CONTROL ROOM] Max Potion Potency adjusted to: </aqua><yellow>" + potStr + "</yellow>"));
                    } else if (titleText.contains("toggle event")) {
                        if (plugin.getChurchEventManager().isEventActive()) {
                            plugin.getChurchEventManager().stopCurrentEvent();
                            player.sendMessage(miniMessage.deserialize("<yellow>✦ Active server event stopped.</yellow>"));
                        } else {
                            plugin.getChurchEventManager().startEvent(com.churchsmp.event.ChurchEventManager.EventType.BLOOD_MOON, 20 * 60 * 15);
                            player.sendMessage(miniMessage.deserialize("<dark_red>✦ Blood Moon cataclysm invoked upon the realm.</dark_red>"));
                        }
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    } else if (titleText.contains("revive all")) {
                        decreeUtopiaRevive(player);
                    } else if (titleText.contains("great reset")) {
                        decreeGreatReset(player);
                    } else if (titleText.contains("natural regen")) {
                        boolean gamerule = Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.NATURAL_REGENERATION));
                        for (World w : Bukkit.getWorlds()) {
                            w.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, !gamerule);
                        }
                        Bukkit.broadcast(miniMessage.deserialize("<green>✦ [CONTROL ROOM] Natural Regeneration is now " + (!gamerule ? "ON" : "OFF") + ".</green>"));
                    } else if (titleText.contains("time lock")) {
                        boolean gamerule = Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE));
                        for (World w : Bukkit.getWorlds()) {
                            w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, !gamerule);
                        }
                        Bukkit.broadcast(miniMessage.deserialize("<blue>✦ [CONTROL ROOM] Time Cycle is now " + (!gamerule ? "ON" : "OFF") + ".</blue>"));
                    } else if (titleText.contains("keep inventory")) {
                        boolean gamerule = Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY));
                        for (World w : Bukkit.getWorlds()) {
                            w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, !gamerule);
                        }
                        Bukkit.broadcast(miniMessage.deserialize("<yellow>✦ [CONTROL ROOM] Keep Inventory is now " + (!gamerule ? "ON" : "OFF") + ".</yellow>"));
                    }
                }
            }
        }
    }
}
