package com.churchsmp.ritual;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.util.TextUtil;
import com.churchsmp.weapon.LegendaryWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ForsakingRitualManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey forsakingKey;

    private static class RitualSession {
        final Player player;
        final List<Item> items;
        final List<LegendaryWeapon> weapons;
        BukkitTask task;
        Item focusedItem = null;
        LegendaryWeapon focusedWeapon = null;
        double rotation = 0;
        int ticks = 0;

        RitualSession(Player player, List<Item> items, List<LegendaryWeapon> weapons) {
            this.player = player;
            this.items = items;
            this.weapons = weapons;
        }
    }

    private final Map<UUID, RitualSession> activeRituals = new ConcurrentHashMap<>();

    public ForsakingRitualManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.forsakingKey = new NamespacedKey(plugin, "has_completed_forsaking");
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.getPersistentDataContainer().has(forsakingKey, PersistentDataType.BOOLEAN)) {
            // Delay 2 seconds after join for smooth chunk loading
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    startRitual(player);
                }
            }, 40L);
        }
    }

    public void startRitual(Player player) {
        // Clean up previous ritual if active
        cleanupPlayer(player);

        // 1. Temporarily freeze/levitate player smoothly so they can look around
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2400, 255, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 2400, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 2400, 0, false, false));

        // 2. Play opening chime sounds and display cinematic title
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);

        Component title = miniMessage.deserialize("<gold>✦ <gradient:#FFD700:#FFFFFF><bold>" + TextUtil.toSmallCaps("Choose Your Destiny") + "</bold></gradient> <gold>✦</gold>");
        Component subtitle = miniMessage.deserialize("<yellow>" + TextUtil.toSmallCaps("Aim crosshair at an orbiting relic & Click to Claim") + "</yellow>");
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(500))));

        // 3. Spawn all 7 Legendary Relics floating in a halo around the player
        Location center = player.getLocation().add(0, 1.2, 0);
        List<String> weaponIds = List.of("excalibur", "luminescence_spear", "mayim", "judas", "sorrowess", "voidbreaker", "grim");
        List<Item> haloItems = new ArrayList<>();
        List<LegendaryWeapon> haloWeapons = new ArrayList<>();

        for (int i = 0; i < weaponIds.size(); i++) {
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponIds.get(i));
            if (weapon == null) continue;

            double angle = (2 * Math.PI / weaponIds.size()) * i;
            Location itemLoc = center.clone().add(Math.cos(angle) * 2.8, 0, Math.sin(angle) * 2.8);

            Item item = player.getWorld().dropItem(itemLoc, weapon.createItem());
            item.setGravity(false);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setVelocity(new Vector(0, 0, 0));
            item.setCustomNameVisible(true);
            item.customName(weapon.getDisplayName());

            haloItems.add(item);
            haloWeapons.add(weapon);
        }

        RitualSession session = new RitualSession(player, haloItems, haloWeapons);
        activeRituals.put(player.getUniqueId(), session);

        // 4. Smooth slow orbit & crosshair raycast detection loop
        session.task = new BukkitRunnable() {
            @Override
            public void run() {
                session.ticks++;
                if (!player.isOnline() || session.ticks > 2400) {
                    cleanupPlayer(player);
                    cancel();
                    return;
                }

                // Slow, majestic orbit
                session.rotation += 0.02;
                Location pLoc = player.getLocation().add(0, 1.2, 0);

                for (int i = 0; i < session.items.size(); i++) {
                    Item item = session.items.get(i);
                    if (!item.isValid()) continue;
                    double angle = session.rotation + (i * (2 * Math.PI / session.items.size()));
                    double yWave = Math.sin(session.ticks * 0.06 + i) * 0.12;
                    Location loc = pLoc.clone().add(Math.cos(angle) * 2.8, yWave, Math.sin(angle) * 2.8);
                    item.teleport(loc);
                }

                // Crosshair Raycast / Dot product calculation
                Location eye = player.getEyeLocation();
                Vector lookDir = eye.getDirection().normalize();

                Item bestItem = null;
                LegendaryWeapon bestWeapon = null;
                double bestDot = 0.93; // tight targeting cone (~21 degrees)

                for (int i = 0; i < session.items.size(); i++) {
                    Item it = session.items.get(i);
                    if (!it.isValid()) continue;
                    Vector toIt = it.getLocation().toVector().subtract(eye.toVector()).normalize();
                    double dot = lookDir.dot(toIt);
                    if (dot > bestDot) {
                        bestDot = dot;
                        bestItem = it;
                        bestWeapon = session.weapons.get(i);
                    }
                }

                // Focused on a weapon
                if (bestWeapon != null && bestItem != null) {
                    session.focusedWeapon = bestWeapon;
                    session.focusedItem = bestItem;

                    // Particle beam from player toward focused weapon
                    Location beamStart = eye.clone().add(lookDir.clone().multiply(0.6));
                    Vector beamVec = bestItem.getLocation().toVector().subtract(beamStart.toVector());
                    int steps = 5;
                    for (int s = 1; s <= steps; s++) {
                        Location stepLoc = beamStart.clone().add(beamVec.clone().multiply((double) s / steps));
                        player.getWorld().spawnParticle(Particle.END_ROD, stepLoc, 1, 0, 0, 0, 0);
                    }

                    // Halo ring around the focused item
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bestItem.getLocation(), 4, 0.2, 0.2, 0.2, 0.02);

                    if (session.ticks % 4 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.8f);
                    }

                    player.sendActionBar(miniMessage.deserialize(
                            "<gold>✦ <yellow><bold>FOCUSED:</bold></yellow> " +
                            bestWeapon.getDisplayName() +
                            " <dark_gray>•</dark_gray> <white><bold>[CLICK]</bold></white> <green>to Claim!</green> ✦</gold>"
                    ));
                } else {
                    session.focusedWeapon = null;
                    session.focusedItem = null;

                    player.sendActionBar(miniMessage.deserialize(
                            "<gray>✦ <yellow>" + TextUtil.toSmallCaps("Aim crosshair at an orbiting relic & Click to Claim") + "</yellow> ✦</gray>"
                    ));
                }

                // Ambient periodic chime
                if (session.ticks % 25 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // Handle clicks for crosshair selection
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        RitualSession session = activeRituals.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        handleSelectionAttempt(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        RitualSession session = activeRituals.get(player.getUniqueId());
        if (session == null) return;

        handleSelectionAttempt(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            RitualSession session = activeRituals.get(player.getUniqueId());
            if (session != null) {
                event.setCancelled(true);
                handleSelectionAttempt(player, session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        RitualSession session = activeRituals.get(player.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            handleSelectionAttempt(player, session);
        }
    }

    private void handleSelectionAttempt(Player player, RitualSession session) {
        if (session.focusedWeapon != null && session.focusedItem != null) {
            // Weapon chosen!
            activeRituals.remove(player.getUniqueId());
            session.task.cancel();
            finishRitualChoice(player, session, session.focusedWeapon, session.focusedItem);
        } else {
            // Not focused on any weapon
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            player.sendActionBar(miniMessage.deserialize(
                    "<red>✦ <bold>" + TextUtil.toSmallCaps("Aim directly at an orbiting relic with your crosshair!") + "</bold> ✦</red>"
            ));
        }
    }

    private void finishRitualChoice(Player player, RitualSession session, LegendaryWeapon chosenWeapon, Item chosenItem) {
        // Tag as completed in PDC
        player.getPersistentDataContainer().set(forsakingKey, PersistentDataType.BOOLEAN, true);

        // Remove potion effects
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.GLOWING);

        // Sound cues for resolution
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.7f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

        // Shatter all unchosen relics into smoke and fragments
        for (Item item : session.items) {
            if (item.equals(chosenItem)) continue;
            if (item.isValid()) {
                item.getWorld().spawnParticle(Particle.ITEM, item.getLocation(), 20, 0.2, 0.2, 0.2, 0.05, item.getItemStack());
                item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation(), 15, 0.2, 0.2, 0.2, 0.02);
                item.setGravity(true);
                item.setVelocity(new Vector(0, -0.6, 0));
                Bukkit.getScheduler().runTaskLater(plugin, item::remove, 15L);
            }
        }

        // Chosen relic rises upward and centers above player's head
        if (chosenItem.isValid()) {
            chosenItem.setGravity(false);
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t > 30 || !player.isOnline() || !chosenItem.isValid()) {
                        chosenItem.remove();
                        deliverWeapon(player, chosenWeapon);
                        cancel();
                        return;
                    }

                    Location pHead = player.getEyeLocation().add(0, 1.2, 0);
                    Location curr = chosenItem.getLocation();
                    Vector toHead = pHead.toVector().subtract(curr.toVector()).multiply(0.2);
                    chosenItem.setVelocity(toHead);

                    chosenItem.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, chosenItem.getLocation(), 5, 0.1, 0.1, 0.1, 0.05);
                    chosenItem.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, chosenItem.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } else {
            deliverWeapon(player, chosenWeapon);
        }
    }

    private void deliverWeapon(Player player, LegendaryWeapon chosenWeapon) {
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.6f, 1.2f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.5, 0), 3, Color.WHITE);

        // Deliver item to player inventory
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(chosenWeapon.createItem());
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }

        // Set player's initial alignment based on chosen relic
        String wid = chosenWeapon.getId();
        if (wid.equals("judas") || wid.equals("sorrowess") || wid.equals("grim")) {
            plugin.getAlignmentManager().setAlignment(player, Alignment.EVIL);
        } else if (wid.equals("excalibur") || wid.equals("luminescence_spear") || wid.equals("mayim")) {
            plugin.getAlignmentManager().setAlignment(player, Alignment.GOOD);
        } else {
            plugin.getAlignmentManager().setAlignment(player, Alignment.NULLIFIED);
        }

        // Cinematic resolution title
        Component title = miniMessage.deserialize("<gold>✦ <gradient:#FFD700:#FFFFFF><bold>" + TextUtil.toSmallCaps("Destiny Sealed") + "</bold></gradient> <gold>✦</gold>");
        player.showTitle(Title.title(title, chosenWeapon.getDisplayName(), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))));

        // Starter lore & guide in chat
        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
        player.sendMessage(miniMessage.deserialize("<gold>✦ <white><bold>" + TextUtil.toSmallCaps("Forsaking Sealed") + ":</bold></white> " + chosenWeapon.getDisplayName() + "</gold>"));
        player.sendMessage(Component.text("  You have bound your soul to this legendary relic.", NamedTextColor.GRAY));
        player.sendMessage(miniMessage.deserialize("  <gray>Type <yellow>/church guide</yellow> " + TextUtil.toSmallCaps("to view abilities, passives & alignment!") + "</gray>"));
        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    private void cleanupPlayer(Player player) {
        RitualSession session = activeRituals.remove(player.getUniqueId());
        if (session != null) {
            if (session.task != null) session.task.cancel();
            for (Item item : session.items) {
                if (item.isValid()) item.remove();
            }
        }
    }
}
