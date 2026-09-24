package com.churchsmp.ritual;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        final List<SinGemType> gems;
        BukkitTask task;
        Item focusedItem = null;
        SinGemType focusedGem = null;
        double rotation = 0;
        int ticks = 0;

        RitualSession(Player player, List<Item> items, List<SinGemType> gems) {
            this.player = player;
            this.items = items;
            this.gems = gems;
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

        Component title = miniMessage.deserialize("<gold>✦ <gradient:#FFD700:#FFFFFF><bold>" + TextUtil.toSmallCaps("Choose Your Sin Gem") + "</bold></gradient> <gold>✦</gold>");
        Component subtitle = miniMessage.deserialize("<yellow>" + TextUtil.toSmallCaps("Aim crosshair at an orbiting gem & Click to Attune") + "</yellow>");
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(500))));

        // 3. Spawn all 7 Sin Gems floating in a halo around the player
        Location center = player.getLocation().add(0, 1.2, 0);
        SinGemType[] gemTypes = SinGemType.values();
        List<Item> haloItems = new ArrayList<>();
        List<SinGemType> haloGems = new ArrayList<>();

        for (int i = 0; i < gemTypes.length; i++) {
            SinGemType gem = gemTypes[i];
            double angle = (2 * Math.PI / gemTypes.length) * i;
            Location itemLoc = center.clone().add(Math.cos(angle) * 2.8, 0, Math.sin(angle) * 2.8);

            Item item = player.getWorld().dropItem(itemLoc, plugin.getSinGemManager().createGemItem(gem));
            item.setGravity(false);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setVelocity(new Vector(0, 0, 0));
            item.setCustomNameVisible(true);
            item.customName(gem.getFormattedName());

            haloItems.add(item);
            haloGems.add(gem);
        }

        RitualSession session = new RitualSession(player, haloItems, haloGems);
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
                SinGemType bestGem = null;
                double bestDot = 0.93; // tight targeting cone (~21 degrees)

                for (int i = 0; i < session.items.size(); i++) {
                    Item it = session.items.get(i);
                    if (!it.isValid()) continue;
                    Vector toIt = it.getLocation().toVector().subtract(eye.toVector()).normalize();
                    double dot = lookDir.dot(toIt);
                    if (dot > bestDot) {
                        bestDot = dot;
                        bestItem = it;
                        bestGem = session.gems.get(i);
                    }
                }

                // Focused on a gem
                if (bestGem != null && bestItem != null) {
                    session.focusedGem = bestGem;
                    session.focusedItem = bestItem;

                    // Particle beam from player toward focused gem
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

                    Component actionMsg = miniMessage.deserialize("<gold>✦ <yellow><bold>FOCUSED:</bold></yellow> </gold>")
                            .append(bestGem.getFormattedName())
                            .append(miniMessage.deserialize(" <dark_gray>•</dark_gray> <white><bold>[CLICK]</bold></white> <green>to Attune!</green> ✦</gold>"));
                    player.sendActionBar(actionMsg);
                } else {
                    session.focusedGem = null;
                    session.focusedItem = null;

                    player.sendActionBar(miniMessage.deserialize(
                            "<gray>✦ <yellow>" + TextUtil.toSmallCaps("Aim crosshair at an orbiting gem & Click to Attune") + "</yellow> ✦</gray>"
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
        if (session.focusedGem != null && session.focusedItem != null) {
            // Gem chosen!
            activeRituals.remove(player.getUniqueId());
            session.task.cancel();
            finishRitualChoice(player, session, session.focusedGem, session.focusedItem);
        } else {
            // Not focused on any gem
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            player.sendActionBar(miniMessage.deserialize(
                    "<red>✦ <bold>" + TextUtil.toSmallCaps("Aim directly at an orbiting gem with your crosshair!") + "</bold> ✦</red>"
            ));
        }
    }

    /**
     * INSANE FORSAKING FINALE:
     * 1. Player floats up into the sky via Levitation.
     * 2. All 6 unchosen Sin Gems remain in orbit around the rising player.
     * 3. High-density electric energy beams connect from every unchosen gem into the player's chest!
     * 4. The chosen Forsake gem ascends overhead and SCALES BIGGER in real 3D up to 3.2x!
     * 5. Orbital lightning strikes around the player at rhythmic intervals with expanding sonic shockwaves.
     * 6. Dual-helix cosmic vortex rises around the player.
     * 7. Supernova climax: direct lightning strike, sonic boom, implosion absorption, and divine deliverance.
     */
    private void finishRitualChoice(Player player, RitualSession session, SinGemType chosenGem, Item chosenItem) {
        // Tag as completed in PDC
        player.getPersistentDataContainer().set(forsakingKey, PersistentDataType.BOOLEAN, true);

        // Remove initial freeze effects
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.GLOWING);

        // 1. Float the player up into the air!
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 75, 1, false, false, false));

        // Sound cues for awakening
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);

        // Separate the unchosen gems to keep them orbiting and linked to the player
        List<Item> linkedItems = new ArrayList<>();
        for (Item item : session.items) {
            if (item.equals(chosenItem)) continue;
            if (item.isValid()) {
                linkedItems.add(item);
            }
        }

        // Spawn central scaling Forsake Gem entity (ItemDisplay with fallback)
        ItemStack gemStack = plugin.getSinGemManager().createGemItem(chosenGem);
        Entity scalingEntity = null;
        try {
            Location gemSpawn = player.getEyeLocation().add(0, 1.8, 0);
            ItemDisplay display = player.getWorld().spawn(gemSpawn, ItemDisplay.class, d -> {
                d.setItemStack(gemStack);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        new Quaternionf()
                ));
            });
            scalingEntity = display;
            // Hide the original item entity
            if (chosenItem.isValid()) chosenItem.remove();
        } catch (Throwable ignored) {
            // Fallback for non-display entity environments
            chosenItem.setGravity(false);
            chosenItem.setPickupDelay(Integer.MAX_VALUE);
            chosenItem.setCanMobPickup(false);
            scalingEntity = chosenItem;
        }

        final Entity centralGem = scalingEntity;
        Color gemColor = Color.fromRGB(255, 215, 0);
        Particle.DustOptions gemDust = new Particle.DustOptions(gemColor, 1.5f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 1.2f);

        new BukkitRunnable() {
            int t = 0;
            double rotation = session.rotation;

            @Override
            public void run() {
                t++;

                // Stop condition or player disconnect
                if (t > 70 || !player.isOnline()) {
                    cleanupAndDeliverForsake(player, chosenGem, linkedItems, centralGem);
                    cancel();
                    return;
                }

                rotation += 0.22;
                Location pChest = player.getLocation().add(0, 1.3, 0);
                Location pGemLoc = player.getEyeLocation().add(0, 1.8, 0);

                // 1. Central Forsake Gem: Hovers overhead and SCALES BIGGER in real 3D!
                if (centralGem != null && centralGem.isValid()) {
                    centralGem.teleport(pGemLoc);
                    if (centralGem instanceof ItemDisplay itemDisplay) {
                        float scale = 1.0f + (t * 0.035f); // Scales up to ~3.45x!
                        try {
                            itemDisplay.setTransformation(new Transformation(
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf().rotateY((float) rotation),
                                    new Vector3f(scale, scale, scale),
                                    new Quaternionf()
                            ));
                        } catch (Throwable ignored) {}
                    }
                }

                // 2. All unchosen gems orbit around the player and LINK with particle energy beams!
                double orbitRadius = 3.0 - (t * 0.015);
                for (int i = 0; i < linkedItems.size(); i++) {
                    Item it = linkedItems.get(i);
                    if (!it.isValid()) continue;

                    double angle = rotation + (i * (2 * Math.PI / Math.max(1, linkedItems.size())));
                    double yOffset = Math.sin(t * 0.12 + i) * 0.45;
                    Location itemLoc = pChest.clone().add(Math.cos(angle) * orbitRadius, yOffset, Math.sin(angle) * orbitRadius);
                    it.teleport(itemLoc);

                    // Energy beam linking each gem straight to player's heart!
                    Vector toPlayer = pChest.toVector().subtract(itemLoc.toVector());
                    int beamPoints = 9;
                    for (int step = 1; step <= beamPoints; step++) {
                        Location beamPoint = itemLoc.clone().add(toPlayer.clone().multiply((double) step / beamPoints));
                        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, beamPoint, 1, 0, 0, 0, 0);
                        if (step % 2 == 0) {
                            player.getWorld().spawnParticle(Particle.END_ROD, beamPoint, 1, 0, 0, 0, 0);
                        }
                    }
                }

                // 3. Insane halo & orbital rings around the scaling Forsake Gem
                player.getWorld().spawnParticle(Particle.END_ROD, pGemLoc, 4, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pGemLoc, 5, 0.4, 0.4, 0.4, 0.08);
                player.getWorld().spawnParticle(Particle.DUST, pGemLoc, 6, 0.5, 0.5, 0.5, 0, gemDust);

                // 4. Dual-helix ascending vortex particles spiraling up around the player
                for (int h = 0; h < 2; h++) {
                    double helixAngle = (rotation * 1.6) + (h * Math.PI) + (t * 0.1);
                    double helixY = ((t * 0.07) % 3.2);
                    Location hLoc = player.getLocation().add(Math.cos(helixAngle) * 1.3, helixY, Math.sin(helixAngle) * 1.3);
                    player.getWorld().spawnParticle(Particle.DUST, hLoc, 1, 0, 0, 0, 0, (h == 0) ? gemDust : whiteDust);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hLoc, 1, 0, 0, 0, 0);
                }

                // 5. Orbital Lightning strikes & Expanding Ground Shockwaves
                if (t == 15 || t == 30 || t == 45 || t == 60) {
                    double boltAngle = (t / 15.0) * (Math.PI / 2.0);
                    Location bolt1 = player.getLocation().add(Math.cos(boltAngle) * 4.2, 0, Math.sin(boltAngle) * 4.2);
                    Location bolt2 = player.getLocation().add(Math.cos(boltAngle + Math.PI) * 4.2, 0, Math.sin(boltAngle + Math.PI) * 4.2);

                    // Strike real visual lightning
                    player.getWorld().strikeLightningEffect(bolt1);
                    player.getWorld().strikeLightningEffect(bolt2);

                    // Expanding ground shockwave
                    Location ground = player.getLocation();
                    player.getWorld().spawnParticle(Particle.SONIC_BOOM, ground, 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.FLASH, ground.add(0, 0.2, 0), 2, 0.2, 0.1, 0.2, 0);

                    // Thunderous audio
                    player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.1f);
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.4f);
                }

                // Ambient rising chime
                if (t % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.0f + (t * 0.015f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupAndDeliverForsake(Player player, SinGemType chosenGem, List<Item> linkedItems, Entity centralGem) {
        // Remove temporary visual entities
        if (centralGem != null && centralGem.isValid()) centralGem.remove();
        for (Item it : linkedItems) {
            if (it.isValid()) {
                it.getWorld().spawnParticle(Particle.ITEM, it.getLocation(), 15, 0.1, 0.1, 0.1, 0.05, it.getItemStack());
                it.remove();
            }
        }

        if (!player.isOnline()) return;

        // Climax Supernova explosion
        Location pCenter = player.getLocation().add(0, 1.5, 0);
        player.getWorld().strikeLightningEffect(pCenter);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, pCenter, 2, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.FLASH, pCenter, 6, 0.4, 0.4, 0.4, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pCenter, 100, 1.2, 1.2, 1.2, 0.3);
        player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, pCenter, 3, 0.2, 0.2, 0.2, 0);

        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.6f, 1.0f);

        // Safe landing
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
        player.setFallDistance(0);

        // Attune the player
        plugin.getSinGemManager().attune(player, chosenGem);

        // Deliver item to player inventory
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(plugin.getSinGemManager().createGemItem(chosenGem));
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        // Broadcast to all players on server
        Component broadcastMsg = miniMessage.deserialize("<gold>✦ <yellow>" + player.getName() + "</yellow> <gray>has completed the Forsaking Ritual and attuned to </gray></gold>")
                .append(chosenGem.getFormattedName())
                .append(miniMessage.deserialize("<gold>! ✦</gold>"));
        Bukkit.broadcast(broadcastMsg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.2f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
        }

        // Cinematic resolution title
        Component title = miniMessage.deserialize("<gold>✦ <gradient:#FFD700:#FFFFFF><bold>" + TextUtil.toSmallCaps("Destiny Sealed") + "</bold></gradient> <gold>✦</gold>");
        player.showTitle(Title.title(title, chosenGem.getFormattedName(), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))));

        // Starter lore & guide in chat
        player.sendMessage(miniMessage.deserialize("<gold>══════════════════════════════════════════════════</gold>"));
        player.sendMessage(miniMessage.deserialize("<gold>✦ <white><bold>" + TextUtil.toSmallCaps("Forsaking Sealed") + ":</bold></white> </gold>")
                .append(chosenGem.getFormattedName()));
        player.sendMessage(Component.text("  You have permanently attuned your soul to this Relic Gem.", NamedTextColor.GRAY));
        player.sendMessage(miniMessage.deserialize("  <gray>Type <yellow>/church guide</yellow> <white>" + TextUtil.toSmallCaps("to view your abilities and passives!") + "</white></gray>"));
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
