package com.churchsmp.finale;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Reworked Liminal Null Control Room:
 * 1. Physical 3D Pedestals with [ < ] [ Value ] [ > ] Sliders.
 * 2. Fine-grained PvP Enchantment & Potion caps.
 * 3. World Border Slider with an orbiting, dynamically scaling blue particle hologram.
 * 4. Humanoid "Steve" console with red chains and red ritual ring beneath his feet.
 * 5. Player management: Teleport & freeze, Release, Make Invincible, BAN with duration slider, Spectate, Kick.
 * 6. Admin subcommands: /churchadmin null enchant, border, potions, player.
 */
public class NullRoomManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Null Room Coordinates
    public static final int ROOM_X = 10000;
    public static final int ROOM_Y = 250;
    public static final int ROOM_Z = 10000;

    // PvP Enchantment Caps (Level cap: 0 = disabled)
    public final Map<Enchantment, Integer> enchantmentCaps = new ConcurrentHashMap<>();

    // PvP Potion Caps (0 = disabled, 1 = Level I, 2 = Level II)
    public final Map<PotionEffectType, Integer> potionCaps = new ConcurrentHashMap<>();

    // World Border Size (default 10,000 blocks)
    private double worldBorderSize = 10000.0;
    private static final double[] BORDER_STEPS = {500, 1000, 2500, 5000, 7500, 10000, 15000, 20000, 25000, 30000};
    private int borderStepIndex = 5; // default 10k

    // Steve Console & Player Management
    private UUID selectedPlayerId = null;
    private final Map<UUID, Location> frozenPreviousLocs = new ConcurrentHashMap<>();
    private final Set<UUID> frozenInRing = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> invinciblePlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> tempBannedUntil = new ConcurrentHashMap<>();

    // Ban duration slider values in seconds (20s, 60s, 300s, 3600s, -1 for perm)
    private static final int[] BAN_DURATIONS = {20, 60, 300, 3600, -1};
    private int banDurationIndex = 0; // default 20s

    // Entities in Null Room
    private final List<Entity> spawnedRoomEntities = new ArrayList<>();
    private ArmorStand steveStand = null;
    private BukkitTask roomParticleTask = null;

    // Pedestal Sliders tracking: map tag -> TextDisplay
    private final Map<String, TextDisplay> sliderDisplays = new ConcurrentHashMap<>();

    public NullRoomManager(ChurchSMP plugin) {
        this.plugin = plugin;
        initDefaultCaps();
    }

    private void initDefaultCaps() {
        enchantmentCaps.put(Enchantment.SHARPNESS, 5);
        enchantmentCaps.put(Enchantment.PROTECTION, 4);
        enchantmentCaps.put(Enchantment.POWER, 5);
        enchantmentCaps.put(Enchantment.FEATHER_FALLING, 4);
        enchantmentCaps.put(Enchantment.THORNS, 3);
        enchantmentCaps.put(Enchantment.KNOCKBACK, 2);

        potionCaps.put(PotionEffectType.STRENGTH, 2);
        potionCaps.put(PotionEffectType.SPEED, 2);
        potionCaps.put(PotionEffectType.REGENERATION, 2);
        potionCaps.put(PotionEffectType.INSTANT_HEALTH, 2);
        potionCaps.put(PotionEffectType.RESISTANCE, 2);
        potionCaps.put(PotionEffectType.INVISIBILITY, 1);
    }

    public Location getControlRoomSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return new Location(w, ROOM_X + 0.5, ROOM_Y + 1.0, ROOM_Z + 3.5, 180, 0);
    }

    public boolean isInNullRoom(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World w = Bukkit.getWorlds().get(0);
        if (!loc.getWorld().equals(w)) return false;
        return Math.abs(loc.getX() - ROOM_X) <= 6 && Math.abs(loc.getZ() - ROOM_Z) <= 6 && Math.abs(loc.getY() - ROOM_Y) <= 8;
    }

    /**
     * Builds and populates the Liminal Null physical control room.
     */
    public void generateOrRefreshControlRoom() {
        World w = Bukkit.getWorlds().get(0);

        // 1. Build Bedrock Shell (11x7x11)
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = -5; z <= 5; z++) {
                    if (y == 0 || y == 6 || x == -5 || x == 5 || z == -5 || z == 5) {
                        w.getBlockAt(ROOM_X + x, ROOM_Y + y, ROOM_Z + z).setType(Material.BEDROCK);
                    } else {
                        w.getBlockAt(ROOM_X + x, ROOM_Y + y, ROOM_Z + z).setType(Material.AIR);
                    }
                }
            }
        }

        // Center floor & lighting
        w.getBlockAt(ROOM_X, ROOM_Y, ROOM_Z).setType(Material.CRYING_OBSIDIAN);
        w.getBlockAt(ROOM_X, ROOM_Y + 6, ROOM_Z).setType(Material.GLOWSTONE);

        cleanupRoomEntities();

        // 2. Build Physical Pedestals with 3D Sliders
        // Pedestal 1: World Border (West side: x = -3, z = -2)
        createPedestal(w, ROOM_X - 3, ROOM_Y + 1, ROOM_Z - 2, "slider_border",
                "World Border", formatBorderSize(worldBorderSize));

        // Pedestal 2: Enchantments Overview (North side: x = -1, z = -4)
        createPedestal(w, ROOM_X - 1, ROOM_Y + 1, ROOM_Z - 4, "slider_enchant",
                "PvP Enchants", "Sharpness V / Prot IV (Click GUI)");

        // Pedestal 3: Potions Overview (North side: x = 1, z = -4)
        createPedestal(w, ROOM_X + 1, ROOM_Y + 1, ROOM_Z - 4, "slider_potions",
                "PvP Potions", "Strength II / Speed II (Click GUI)");

        // Pedestal 4: Ban / Kick Duration (East side: x = 3, z = -2)
        createPedestal(w, ROOM_X + 3, ROOM_Y + 1, ROOM_Z - 2, "slider_ban_time",
                "Punish Timer", formatBanDuration(BAN_DURATIONS[banDurationIndex]));

        // 3. Build Orbiting Steve Console (Center North: x = 0, z = -1)
        spawnSteveConsole(w, new Location(w, ROOM_X + 0.5, ROOM_Y + 1.0, ROOM_Z - 1.5, 0, 0));

        // 4. Start Room Particle Tasks (Border Hologram, Red Ritual Ring & Chains)
        startRoomParticleLoop();
    }

    private void createPedestal(World w, int x, int y, int z, String tag, String title, String value) {
        w.getBlockAt(x, y - 1, z).setType(Material.LODESTONE);
        w.getBlockAt(x, y, z).setType(Material.AIR);

        Location base = new Location(w, x + 0.5, y + 0.8, z + 0.5);

        // Center Text Display
        TextDisplay center = w.spawn(base, TextDisplay.class, td -> {
            td.text(miniMessage.deserialize("<gold><bold>[ " + title + " ]</bold></gold>\n<yellow>" + value + "</yellow>"));
            td.setBillboard(Display.Billboard.CENTER);
            td.getPersistentDataContainer().set(new NamespacedKey(plugin, "slider_tag"), PersistentDataType.STRING, tag + "_center");
        });
        spawnedRoomEntities.add(center);
        sliderDisplays.put(tag, center);

        // Decrement [ < ] Button Display
        TextDisplay left = w.spawn(base.clone().add(-0.6, 0, 0), TextDisplay.class, td -> {
            td.text(miniMessage.deserialize("<red><bold>[ < ]</bold></red>"));
            td.setBillboard(Display.Billboard.CENTER);
            td.getPersistentDataContainer().set(new NamespacedKey(plugin, "slider_action"), PersistentDataType.STRING, tag + "_dec");
        });
        spawnedRoomEntities.add(left);

        // Increment [ > ] Button Display
        TextDisplay right = w.spawn(base.clone().add(0.6, 0, 0), TextDisplay.class, td -> {
            td.text(miniMessage.deserialize("<green><bold>[ > ]</bold></green>"));
            td.setBillboard(Display.Billboard.CENTER);
            td.getPersistentDataContainer().set(new NamespacedKey(plugin, "slider_action"), PersistentDataType.STRING, tag + "_inc");
        });
        spawnedRoomEntities.add(right);
    }

    private void spawnSteveConsole(World w, Location loc) {
        this.steveStand = w.spawn(loc, ArmorStand.class, stand -> {
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.customName(miniMessage.deserialize("<red><bold>✦ STEVE • PLAYER CONSOLE ✦</bold></red>"));
            stand.setCustomNameVisible(true);
            stand.setCanPickupItems(false);
            stand.setGravity(false);

            // Steve skin player head
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setPlayerProfile(Bukkit.createProfile("MHF_Steve"));
                head.setItemMeta(meta);
            }
            stand.setItem(EquipmentSlot.HEAD, head);
            stand.setItem(EquipmentSlot.CHEST, new ItemStack(Material.CYAN_TERRACOTTA));
            stand.setItem(EquipmentSlot.LEGS, new ItemStack(Material.BLUE_CONCRETE));
            stand.setItem(EquipmentSlot.FEET, new ItemStack(Material.GRAY_CONCRETE));
            stand.setItem(EquipmentSlot.HAND, new ItemStack(Material.CHAIN));

            stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "steve_console"), PersistentDataType.BYTE, (byte) 1);
        });
        spawnedRoomEntities.add(steveStand);

        // Action Buttons hovering above Steve
        TextDisplay actions = w.spawn(loc.clone().add(0, 2.3, 0), TextDisplay.class, td -> {
            td.text(miniMessage.deserialize("<yellow>[ Right-Click to Select Player / Actions ]</yellow>"));
            td.setBillboard(Display.Billboard.CENTER);
        });
        spawnedRoomEntities.add(actions);
    }

    private void startRoomParticleLoop() {
        if (roomParticleTask != null) roomParticleTask.cancel();

        World w = Bukkit.getWorlds().get(0);
        Location borderPedestal = new Location(w, ROOM_X - 2.5, ROOM_Y + 2.0, ROOM_Z - 2.0);
        Location steveLoc = new Location(w, ROOM_X + 0.5, ROOM_Y + 1.0, ROOM_Z - 1.5);

        roomParticleTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                // 1. World Border Rotating Hologram: Blue circle scaling with slider
                // Scale factor: radius from 0.4 up to 1.8 blocks based on worldBorderSize
                double normBorder = (worldBorderSize - 500) / 29500.0;
                double holoRadius = 0.4 + (normBorder * 1.4);

                double rot = ticks * 0.1;
                for (int d = 0; d < 360; d += 30) {
                    double rad = Math.toRadians(d + (ticks * 4));
                    Location p = borderPedestal.clone().add(Math.cos(rad) * holoRadius, Math.sin(rot + rad) * 0.15, Math.sin(rad) * holoRadius);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0, 0, 0, 0.01);
                    w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.2f));
                }

                // 2. Red Ritual Ring beneath Steve's feet
                for (int d = 0; d < 360; d += 20) {
                    double rad = Math.toRadians(d);
                    Location ring = steveLoc.clone().add(Math.cos(rad) * 1.5, 0.05, Math.sin(rad) * 1.5);
                    w.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.3f));
                    if (d % 60 == 0) {
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, ring, 1, 0, 0, 0, 0.01);
                    }
                }

                // 3. Red Chains orbiting horizontally around Steve
                ItemStack chainItem = new ItemStack(Material.CHAIN);
                for (int i = 0; i < 3; i++) {
                    double chainAngle = Math.toRadians((ticks * 5) + (i * 120));
                    Location chainLoc = steveLoc.clone().add(Math.cos(chainAngle) * 1.2, 1.1 + Math.sin(ticks * 0.1 + i) * 0.2, Math.sin(chainAngle) * 1.2);
                    w.spawnParticle(Particle.ITEM, chainLoc, 1, 0, 0, 0, 0, chainItem);
                    w.spawnParticle(Particle.DUST, chainLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 30), 1.4f));
                }

                // 4. Freeze particle effect on target if inside ritual ring
                for (UUID id : frozenInRing) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        Location pLoc = p.getLocation();
                        w.spawnParticle(Particle.DUST, pLoc.add(0, 1.0, 0), 2, 0.3, 0.4, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.2f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Interacting with 3D displays in the Null Room.
     */
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!isInNullRoom(player.getLocation())) return;
        if (!player.hasPermission("churchsmp.admin")) return;

        // Check if interacting with Steve
        if (entity.getPersistentDataContainer().has(new NamespacedKey(plugin, "steve_console"), PersistentDataType.BYTE)) {
            openStevePlayerMenu(player);
            return;
        }

        // Check if interacting with Slider Buttons
        String action = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, "slider_action"), PersistentDataType.STRING);
        if (action != null) {
            handleSliderAction(player, action);
            return;
        }

        String centerTag = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, "slider_tag"), PersistentDataType.STRING);
        if (centerTag != null) {
            if (centerTag.contains("slider_enchant")) {
                openEnchantmentMenu(player);
            } else if (centerTag.contains("slider_potions")) {
                openPotionMenu(player);
            }
        }
    }

    private void handleSliderAction(Player player, String action) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);

        if (action.startsWith("slider_border")) {
            if (action.endsWith("_inc")) {
                if (borderStepIndex < BORDER_STEPS.length - 1) borderStepIndex++;
            } else {
                if (borderStepIndex > 0) borderStepIndex--;
            }
            this.worldBorderSize = BORDER_STEPS[borderStepIndex];
            World w = Bukkit.getWorlds().get(0);
            w.getWorldBorder().setSize(worldBorderSize);

            updateSliderDisplay("slider_border", "World Border", formatBorderSize(worldBorderSize));
            Bukkit.broadcast(miniMessage.deserialize("<aqua>✦ [NULL CONSOLE] World Border adjusted to: </aqua><yellow>" + formatBorderSize(worldBorderSize) + "</yellow>"));

        } else if (action.startsWith("slider_ban_time")) {
            if (action.endsWith("_inc")) {
                if (banDurationIndex < BAN_DURATIONS.length - 1) banDurationIndex++;
            } else {
                if (banDurationIndex > 0) banDurationIndex--;
            }
            int duration = BAN_DURATIONS[banDurationIndex];
            updateSliderDisplay("slider_ban_time", "Punish Timer", formatBanDuration(duration));
            player.sendMessage(miniMessage.deserialize("<gold>✦ Punish duration set to: <yellow>" + formatBanDuration(duration) + "</yellow></gold>"));

        } else if (action.startsWith("slider_enchant")) {
            openEnchantmentMenu(player);
        } else if (action.startsWith("slider_potions")) {
            openPotionMenu(player);
        }
    }

    private void updateSliderDisplay(String tag, String title, String value) {
        TextDisplay td = sliderDisplays.get(tag);
        if (td != null && td.isValid()) {
            td.text(miniMessage.deserialize("<gold><bold>[ " + title + " ]</bold></gold>\n<yellow>" + value + "</yellow>"));
        }
    }

    private String formatBorderSize(double size) {
        if (size >= 1000) {
            return String.format(Locale.US, "%.0fk Blocks", size / 1000.0);
        }
        return String.format(Locale.US, "%.0f Blocks", size);
    }

    private String formatBanDuration(int seconds) {
        if (seconds < 0) return "PERMANENT";
        if (seconds < 60) return seconds + " Seconds";
        if (seconds < 3600) return (seconds / 60) + " Minutes";
        return (seconds / 3600) + " Hours";
    }

    /* --------------------------------------------------------------------------
     * STEVE CONSOLE: PLAYER SELECTION & ACTIONS
     * -------------------------------------------------------------------------- */

    public void openStevePlayerMenu(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Steve Console: Select Player", NamedTextColor.DARK_RED));

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.displayName(Component.text(target.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

                List<Component> lore = new ArrayList<>();
                boolean isFrozen = frozenInRing.contains(target.getUniqueId());
                boolean isInvinc = invinciblePlayers.contains(target.getUniqueId());

                lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                        .append(Component.text(isFrozen ? "FROZEN IN RING" : "Free", isFrozen ? NamedTextColor.RED : NamedTextColor.GREEN)));
                lore.add(Component.text("Invincible: ", NamedTextColor.GRAY)
                        .append(Component.text(isInvinc ? "YES" : "NO", isInvinc ? NamedTextColor.GOLD : NamedTextColor.GRAY)));
                lore.add(Component.empty());
                lore.add(Component.text("▶ Click to manage this player", NamedTextColor.YELLOW));

                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        admin.openInventory(inv);
    }

    public void openPlayerActionMenu(Player admin, Player target) {
        this.selectedPlayerId = target.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Manage: " + target.getName(), NamedTextColor.DARK_PURPLE));

        boolean isFrozen = frozenInRing.contains(target.getUniqueId());
        boolean isInvinc = invinciblePlayers.contains(target.getUniqueId());
        int banSec = BAN_DURATIONS[banDurationIndex];

        // Slot 10: Teleport & Freeze / Release
        ItemStack freezeItem = new ItemStack(isFrozen ? Material.BARRIER : Material.ENDER_PEARL);
        ItemMeta fMeta = freezeItem.getItemMeta();
        fMeta.displayName(Component.text(isFrozen ? "✦ Release to Survival ✦" : "✦ Teleport to Ritual Ring ✦", isFrozen ? NamedTextColor.GREEN : NamedTextColor.RED));
        fMeta.lore(List.of(
                Component.text(isFrozen ? "Unfreezes player and returns them to previous location" : "Teleports player into Steve's red ritual ring and freezes them", NamedTextColor.GRAY)
        ));
        freezeItem.setItemMeta(fMeta);
        inv.setItem(10, freezeItem);

        // Slot 12: Make Invincible (Godmode Toggle)
        ItemStack invincItem = new ItemStack(isInvinc ? Material.TOTEM_OF_UNDYING : Material.SHIELD);
        ItemMeta iMeta = invincItem.getItemMeta();
        iMeta.displayName(Component.text(isInvinc ? "✦ Remove Invincibility ✦" : "✦ Make Invincible (Godmode) ✦", isInvinc ? NamedTextColor.RED : NamedTextColor.GOLD));
        iMeta.lore(List.of(
                Component.text(isInvinc ? "Target becomes vulnerable to damage again" : "Target cannot take any form of damage", NamedTextColor.GRAY)
        ));
        invincItem.setItemMeta(iMeta);
        inv.setItem(12, invincItem);

        // Slot 14: BAN / Tempban
        ItemStack banItem = new ItemStack(Material.ANVIL);
        ItemMeta bMeta = banItem.getItemMeta();
        bMeta.displayName(Component.text("✦ BAN Target (" + formatBanDuration(banSec) + ") ✦", NamedTextColor.DARK_RED));
        bMeta.lore(List.of(
                Component.text("Bans the player for the duration chosen on the slider", NamedTextColor.GRAY),
                Component.text("Click to execute ban with instant undo", NamedTextColor.YELLOW)
        ));
        banItem.setItemMeta(bMeta);
        inv.setItem(14, banItem);

        // Slot 15: Spectate
        boolean isSpec = target.getGameMode() == GameMode.SPECTATOR;
        ItemStack specItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta sMeta = specItem.getItemMeta();
        sMeta.displayName(Component.text(isSpec ? "✦ Return to Survival ✦" : "✦ Force Spectator Mode ✦", NamedTextColor.AQUA));
        sMeta.lore(List.of(
                Component.text(isSpec ? "Restores player to Survival GameMode" : "Turns target into a spectator", NamedTextColor.GRAY)
        ));
        specItem.setItemMeta(sMeta);
        inv.setItem(15, specItem);

        // Slot 16: Kick (< 20s or slider)
        ItemStack kickItem = new ItemStack(Material.IRON_DOOR);
        ItemMeta kMeta = kickItem.getItemMeta();
        kMeta.displayName(Component.text("✦ Kick Player (" + formatBanDuration(banSec) + ") ✦", NamedTextColor.GOLD));
        kMeta.lore(List.of(
                Component.text("Disconnects the player with a re-entry cooldown", NamedTextColor.GRAY)
        ));
        kickItem.setItemMeta(kMeta);
        inv.setItem(16, kickItem);

        admin.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = event.getView().getTitle();

        if (title.contains("Steve Console: Select Player")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getItemMeta() instanceof SkullMeta meta && meta.getOwningPlayer() != null) {
                Player target = meta.getOwningPlayer().getPlayer();
                if (target != null && target.isOnline()) {
                    openPlayerActionMenu(admin, target);
                } else {
                    admin.sendMessage(miniMessage.deserialize("<red>Target is offline.</red>"));
                }
            }
            return;
        }

        if (title.contains("Manage: ")) {
            event.setCancelled(true);
            if (selectedPlayerId == null) return;
            Player target = Bukkit.getPlayer(selectedPlayerId);
            if (target == null || !target.isOnline()) {
                admin.sendMessage(miniMessage.deserialize("<red>Target is no longer online.</red>"));
                admin.closeInventory();
                return;
            }

            int slot = event.getRawSlot();
            int duration = BAN_DURATIONS[banDurationIndex];

            switch (slot) {
                case 10 -> { // Freeze / Release
                    if (frozenInRing.contains(target.getUniqueId())) {
                        releasePlayerFromRing(target);
                        admin.sendMessage(miniMessage.deserialize("<green>✦ Released " + target.getName() + " back to survival location. ✦</green>"));
                    } else {
                        teleportAndFreezeInRing(target);
                        admin.sendMessage(miniMessage.deserialize("<red>✦ Teleported and locked " + target.getName() + " in Steve's ritual ring! ✦</red>"));
                    }
                    openPlayerActionMenu(admin, target);
                }
                case 12 -> { // Godmode
                    if (invinciblePlayers.contains(target.getUniqueId())) {
                        invinciblePlayers.remove(target.getUniqueId());
                        target.setInvulnerable(false);
                        admin.sendMessage(miniMessage.deserialize("<yellow>✦ " + target.getName() + " is no longer invincible.</yellow>"));
                    } else {
                        invinciblePlayers.add(target.getUniqueId());
                        target.setInvulnerable(true);
                        admin.sendMessage(miniMessage.deserialize("<gold>✦ " + target.getName() + " is now INVINCIBLE (Godmode ON)!</gold>"));
                    }
                    openPlayerActionMenu(admin, target);
                }
                case 14 -> { // Ban
                    executeTempBan(target, duration, "Banned by Church Admin for " + formatBanDuration(duration));
                    admin.sendMessage(miniMessage.deserialize("<dark_red>✦ Banned " + target.getName() + " for " + formatBanDuration(duration) + "! ✦</dark_red>"));
                    admin.closeInventory();
                }
                case 15 -> { // Spectator
                    if (target.getGameMode() == GameMode.SPECTATOR) {
                        target.setGameMode(GameMode.SURVIVAL);
                        admin.sendMessage(miniMessage.deserialize("<green>✦ Restored " + target.getName() + " to Survival.</green>"));
                    } else {
                        target.setGameMode(GameMode.SPECTATOR);
                        admin.sendMessage(miniMessage.deserialize("<aqua>✦ Forced " + target.getName() + " into Spectator Mode.</aqua>"));
                    }
                    openPlayerActionMenu(admin, target);
                }
                case 16 -> { // Kick
                    target.kick(Component.text("Kicked by Church Admin for " + formatBanDuration(duration), NamedTextColor.RED));
                    admin.sendMessage(miniMessage.deserialize("<gold>✦ Kicked " + target.getName() + " from the realm.</gold>"));
                    admin.closeInventory();
                }
            }
        }

        if (title.contains("PvP Enchantment Caps")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null) return;

            Enchantment ench = getEnchantFromMenuSlot(event.getRawSlot());
            if (ench != null) {
                int cur = enchantmentCaps.getOrDefault(ench, 0);
                int max = ench.getMaxLevel();
                if (event.isRightClick()) {
                    cur = (cur <= 0) ? max : cur - 1;
                } else {
                    cur = (cur >= max) ? 0 : cur + 1;
                }
                enchantmentCaps.put(ench, cur);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                openEnchantmentMenu(admin);
            }
        }

        if (title.contains("PvP Potion Caps")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null) return;

            PotionEffectType pot = getPotionFromMenuSlot(event.getRawSlot());
            if (pot != null) {
                int cur = potionCaps.getOrDefault(pot, 0);
                cur = (cur + 1) % 3; // 0 -> 1 -> 2 -> 0
                potionCaps.put(pot, cur);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                openPotionMenu(admin);
            }
        }
    }

    public void teleportAndFreezeInRing(Player target) {
        World w = Bukkit.getWorlds().get(0);
        Location ringLoc = new Location(w, ROOM_X + 0.5, ROOM_Y + 1.0, ROOM_Z - 1.5, target.getYaw(), target.getPitch());

        frozenPreviousLocs.put(target.getUniqueId(), target.getLocation());
        frozenInRing.add(target.getUniqueId());

        target.teleport(ringLoc);
        plugin.getStunManager().applyTrueStun(target, 72000, "Liminal Null Containment");
        target.playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        target.sendMessage(miniMessage.deserialize("<red><bold>✦ You have been detained inside the Liminal Null Ritual Ring! ✦</bold></red>"));
    }

    public void releasePlayerFromRing(Player target) {
        frozenInRing.remove(target.getUniqueId());
        plugin.getStunManager().removeStun(target);

        Location prev = frozenPreviousLocs.remove(target.getUniqueId());
        if (prev == null || prev.getWorld() == null) {
            prev = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        target.teleport(prev);
        target.playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.4f, 1.0f);
        target.sendMessage(miniMessage.deserialize("<green>✦ Released from the Liminal Null! Returned to reality. ✦</green>"));
    }

    public void executeTempBan(Player player, int seconds, String reason) {
        if (seconds > 0) {
            tempBannedUntil.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        }
        player.kick(Component.text(reason, NamedTextColor.RED));
    }

    public void undoBan(UUID playerId) {
        tempBannedUntil.remove(playerId);
    }

    @EventHandler
    public void onLoginCheckBan(PlayerLoginEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Long expiry = tempBannedUntil.get(id);
        if (expiry != null) {
            if (System.currentTimeMillis() < expiry) {
                long remainingSec = (expiry - System.currentTimeMillis()) / 1000L;
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                        Component.text("You are temporarily banned for " + remainingSec + " more seconds.", NamedTextColor.RED));
            } else {
                tempBannedUntil.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoveFrozenInRing(PlayerMoveEvent event) {
        if (frozenInRing.contains(event.getPlayer().getUniqueId())) {
            if (event.hasChangedBlock()) {
                event.setTo(event.getFrom());
            }
        }
    }

    /* --------------------------------------------------------------------------
     * PVP ENCHANTMENT & POTION CAP MENUS & ENFORCEMENT
     * -------------------------------------------------------------------------- */

    public void openEnchantmentMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("PvP Enchantment Caps", NamedTextColor.GOLD));

        int[] slots = {10, 11, 12, 14, 15, 16};
        Enchantment[] enchs = {
                Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.POWER,
                Enchantment.FEATHER_FALLING, Enchantment.THORNS, Enchantment.KNOCKBACK
        };

        for (int i = 0; i < enchs.length; i++) {
            Enchantment e = enchs[i];
            int cap = enchantmentCaps.getOrDefault(e, e.getMaxLevel());
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = book.getItemMeta();
            meta.displayName(Component.text(e.getKey().getKey().toUpperCase(Locale.ROOT), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Current Cap: " + (cap == 0 ? "DISABLED" : "Level " + cap), cap == 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                    Component.text("Max Vanilla: Level " + e.getMaxLevel(), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Left-Click: +1 Level | Right-Click: -1 Level", NamedTextColor.DARK_GRAY)
            ));
            book.setItemMeta(meta);
            inv.setItem(slots[i], book);
        }

        player.openInventory(inv);
    }

    private Enchantment getEnchantFromMenuSlot(int slot) {
        return switch (slot) {
            case 10 -> Enchantment.SHARPNESS;
            case 11 -> Enchantment.PROTECTION;
            case 12 -> Enchantment.POWER;
            case 14 -> Enchantment.FEATHER_FALLING;
            case 15 -> Enchantment.THORNS;
            case 16 -> Enchantment.KNOCKBACK;
            default -> null;
        };
    }

    public void openPotionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("PvP Potion Caps", NamedTextColor.AQUA));

        int[] slots = {10, 11, 12, 14, 15, 16};
        PotionEffectType[] pots = {
                PotionEffectType.STRENGTH, PotionEffectType.SPEED, PotionEffectType.REGENERATION,
                PotionEffectType.INSTANT_HEALTH, PotionEffectType.RESISTANCE, PotionEffectType.INVISIBILITY
        };

        for (int i = 0; i < pots.length; i++) {
            PotionEffectType p = pots[i];
            int cap = potionCaps.getOrDefault(p, 2);
            ItemStack potItem = new ItemStack(Material.POTION);
            ItemMeta meta = potItem.getItemMeta();
            meta.displayName(Component.text(p.getName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Current Cap: " + (cap == 0 ? "DISABLED" : (cap == 1 ? "Level I" : "Level II")), cap == 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                    Component.empty(),
                    Component.text("Click to cycle Cap (0 -> I -> II)", NamedTextColor.DARK_GRAY)
            ));
            potItem.setItemMeta(meta);
            inv.setItem(slots[i], potItem);
        }

        player.openInventory(inv);
    }

    private PotionEffectType getPotionFromMenuSlot(int slot) {
        return switch (slot) {
            case 10 -> PotionEffectType.STRENGTH;
            case 11 -> PotionEffectType.SPEED;
            case 12 -> PotionEffectType.REGENERATION;
            case 14 -> PotionEffectType.INSTANT_HEALTH;
            case 15 -> PotionEffectType.RESISTANCE;
            case 16 -> PotionEffectType.INVISIBILITY;
            default -> null;
        };
    }

    /**
     * Enforce PvP Enchantment damage caps in combat.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.hasItemMeta()) {
            int sharpLvl = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
            int sharpCap = enchantmentCaps.getOrDefault(Enchantment.SHARPNESS, 5);
            if (sharpCap == 0) {
                // Completely zero out sharpness bonus
                event.setDamage(Math.max(1.0, event.getDamage() - (0.5 * sharpLvl + 0.5)));
            } else if (sharpLvl > sharpCap) {
                double diff = (sharpLvl - sharpCap) * 0.5;
                event.setDamage(Math.max(1.0, event.getDamage() - diff));
            }
        }
    }

    /**
     * Enforce PvP Potion Caps on potion application.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionApply(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        PotionEffect next = event.getNewEffect();
        if (next == null) return;

        PotionEffectType type = next.getType();
        if (potionCaps.containsKey(type)) {
            int cap = potionCaps.get(type);
            int amplifier = next.getAmplifier(); // 0 = Level 1, 1 = Level 2

            if (cap == 0) {
                // Disabled
                event.setCancelled(true);
            } else if (amplifier >= cap) {
                // Clamp amplifier to cap - 1
                event.setCancelled(true);
                Player p = (Player) event.getEntity();
                p.addPotionEffect(new PotionEffect(type, next.getDuration(), cap - 1, next.isAmbient(), next.hasParticles(), next.hasIcon()));
            }
        }
    }

    public void setWorldBorderSize(double size) {
        this.worldBorderSize = size;
        World w = Bukkit.getWorlds().get(0);
        w.getWorldBorder().setSize(size);
        updateSliderDisplay("slider_border", "World Border", formatBorderSize(size));
    }

    public double getWorldBorderSize() {
        return worldBorderSize;
    }

    public void cleanupRoomEntities() {
        for (Entity e : spawnedRoomEntities) {
            if (e != null && e.isValid()) e.remove();
        }
        spawnedRoomEntities.clear();
        sliderDisplays.clear();
    }
}
