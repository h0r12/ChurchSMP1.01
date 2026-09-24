package com.churchsmp.ritual;

import com.churchsmp.ChurchSMP;
import com.churchsmp.gem.SinGemType;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

                    // Directed particle beam from crosshair toward focused gem
                    Location beamStart = eye.clone().add(lookDir.clone().multiply(0.6));
                    Vector beamVec = bestItem.getLocation().toVector().subtract(beamStart.toVector());
                    int steps = 6;
                    for (int s = 1; s <= steps; s++) {
                        Location stepLoc = beamStart.clone().add(beamVec.clone().multiply((double) s / steps));
                        player.getWorld().spawnParticle(Particle.END_ROD, stepLoc, 1, 0, 0, 0, 0);
                    }

                    // Structured halo ring around the focused item
                    TextColor btc = bestGem.getColor();
                    Particle.DustOptions focusDust = new Particle.DustOptions(Color.fromRGB(btc.red(), btc.green(), btc.blue()), 1.2f);
                    for (int deg = 0; deg < 360; deg += 60) {
                        double rad = Math.toRadians(deg + (session.ticks * 6));
                        Location ringPt = bestItem.getLocation().clone().add(Math.cos(rad) * 0.45, Math.sin(rad) * 0.45, 0);
                        player.getWorld().spawnParticle(Particle.DUST, ringPt, 1, 0, 0, 0, 0, focusDust);
                    }

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
     * FORSAKING FINALE WITH SIN-THEMED DIRECTED PARTICLES:
     * 1. Player floats up smoothly via Levitation.
     * 2. The chosen Sin determines the exact color theme & accent for all visual effects.
     * 3. Beams from remaining gems shoot cleanly into player's chest using the Sin's theme.
     * 4. A directed vertical power column funnels upward from player's chest to the scaling gem.
     * 5. Two crisp geometric horizontal mandala rings rotate at waist and feet.
     * 6. Clean dual astrolabe orbital rings spin around the scaling 3D gem.
     * 7. Single clean climax delivers the gem safely.
     */
    private void finishRitualChoice(Player player, RitualSession session, SinGemType chosenGem, Item chosenItem) {
        // Tag as completed in PDC
        player.getPersistentDataContainer().set(forsakingKey, PersistentDataType.BOOLEAN, true);

        // Remove initial freeze effects
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.GLOWING);

        // 1. Float the player up into the air smoothly
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 75, 1, false, false, false));

        // 2. Resolve colors matching the chosen Sin
        TextColor tc = chosenGem.getColor();
        Color sinColor = Color.fromRGB(tc.red(), tc.green(), tc.blue());
        Color accentColor = switch (chosenGem) {
            case WRATH -> Color.fromRGB(255, 120, 20);     // Molten fire orange
            case GREED -> Color.fromRGB(255, 255, 160);    // Brilliant yellow-white
            case GLUTTONY -> Color.fromRGB(160, 255, 80);  // Acid lime
            case LUST -> Color.fromRGB(255, 180, 240);     // Pastel rose
            case ENVY -> Color.fromRGB(180, 255, 255);     // Bright aquamarine
            case PRIDE -> Color.fromRGB(255, 255, 255);    // Diamond pure white
            case SLOTH -> Color.fromRGB(160, 100, 255);    // Void purple
        };

        // Sound cues for awakening
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.9f);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.2f);

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
                d.setGlowColorOverride(sinColor); // Exactly the chosen sin's color!
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        new Quaternionf()
                ));
            });
            scalingEntity = display;
            if (chosenItem.isValid()) chosenItem.remove();
        } catch (Throwable ignored) {
            chosenItem.setGravity(false);
            chosenItem.setPickupDelay(Integer.MAX_VALUE);
            chosenItem.setCanMobPickup(false);
            scalingEntity = chosenItem;
        }

        final Entity centralGem = scalingEntity;
        Particle.DustOptions sinDust = new Particle.DustOptions(sinColor, 1.6f);
        Particle.DustOptions accentDust = new Particle.DustOptions(accentColor, 1.3f);

        new BukkitRunnable() {
            int t = 0;
            boolean finished = false;
            double rotation = session.rotation;

            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }
                t++;

                // Stop condition or player disconnect
                if (t > 70 || !player.isOnline()) {
                    finished = true;
                    cancel();
                    try {
                        cleanupAndDeliverForsake(player, chosenGem, linkedItems, centralGem, sinColor, accentColor);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("Error in forsake cleanup: " + ex.getMessage());
                    }
                    return;
                }

                rotation += 0.20;
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

                // 2. Directed straight laser beams from each unchosen gem into player's chest (Sin themed)
                double orbitRadius = 3.0 - (t * 0.015);
                for (int i = 0; i < linkedItems.size(); i++) {
                    Item it = linkedItems.get(i);
                    if (!it.isValid()) continue;

                    double angle = rotation + (i * (2 * Math.PI / Math.max(1, linkedItems.size())));
                    double yOffset = Math.sin(t * 0.12 + i) * 0.35;
                    Location itemLoc = pChest.clone().add(Math.cos(angle) * orbitRadius, yOffset, Math.sin(angle) * orbitRadius);
                    it.teleport(itemLoc);

                    // Clean directed straight line from gem to chest
                    Vector toPlayer = pChest.toVector().subtract(itemLoc.toVector());
                    int beamPoints = 8;
                    for (int step = 1; step <= beamPoints; step++) {
                        Location beamPoint = itemLoc.clone().add(toPlayer.clone().multiply((double) step / beamPoints));
                        player.getWorld().spawnParticle(Particle.DUST, beamPoint, 1, 0, 0, 0, 0, (step % 2 == 0) ? sinDust : accentDust);
                    }
                }

                // 3. Directed upward power column: flowing straight upward from player's chest into the scaling gem
                Vector upVector = pGemLoc.toVector().subtract(pChest.toVector());
                int colSteps = 6;
                for (int step = 1; step <= colSteps; step++) {
                    Location colPt = pChest.clone().add(upVector.clone().multiply((double) step / colSteps));
                    player.getWorld().spawnParticle(Particle.DUST, colPt, 1, 0, 0, 0, 0, sinDust);
                    if (step % 2 == 0) {
                        player.getWorld().spawnParticle(Particle.END_ROD, colPt, 1, 0, 0.04, 0, 0.01);
                    }
                }

                // 4. Structured geometric horizontal mandala rings around the player
                // Waist ring (rotating clockwise)
                for (int d = 0; d < 360; d += 30) {
                    double rad = Math.toRadians(d + (rotation * 40));
                    Location ring1 = pChest.clone().add(Math.cos(rad) * 1.3, -0.2, Math.sin(rad) * 1.3);
                    player.getWorld().spawnParticle(Particle.DUST, ring1, 1, 0, 0, 0, 0, sinDust);
                }
                // Feet ring (rotating counter-clockwise)
                for (int d = 0; d < 360; d += 24) {
                    double rad = Math.toRadians(d - (rotation * 30));
                    Location ring2 = player.getLocation().add(Math.cos(rad) * 1.7, 0.1, Math.sin(rad) * 1.7);
                    player.getWorld().spawnParticle(Particle.DUST, ring2, 1, 0, 0, 0, 0, accentDust);
                }

                // 5. Clean astrolabe dual rings around the scaling gem overhead
                // Horizontal ring around the gem
                for (int d = 0; d < 360; d += 45) {
                    double rad = Math.toRadians(d + (rotation * 50));
                    Location gemRing = pGemLoc.clone().add(Math.cos(rad) * 0.85, 0, Math.sin(rad) * 0.85);
                    player.getWorld().spawnParticle(Particle.DUST, gemRing, 1, 0, 0, 0, 0, sinDust);
                }
                // Tilted 45-degree vertical ring around the gem
                for (int d = 0; d < 360; d += 45) {
                    double rad = Math.toRadians(d - (rotation * 50));
                    Location tiltedPt = pGemLoc.clone().add(Math.cos(rad) * 0.7, Math.sin(rad) * 0.7, Math.cos(rad) * 0.4);
                    player.getWorld().spawnParticle(Particle.DUST, tiltedPt, 1, 0, 0, 0, 0, accentDust);
                }

                // 6. Rhythmic pulse beats (clean and harmonic)
                if (t == 20 || t == 40 || t == 60) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f + (t * 0.01f));
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);
                    player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.5, 0), 1, Color.WHITE);
                }

                // Ambient rising chime
                if (t % 8 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f + (t * 0.015f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupAndDeliverForsake(Player player, SinGemType chosenGem, List<Item> linkedItems, Entity centralGem, Color sinColor, Color accentColor) {
        // Remove temporary visual entities safely
        try {
            if (centralGem != null && centralGem.isValid()) centralGem.remove();
            for (Item it : linkedItems) {
                if (it.isValid()) {
                    it.remove();
                }
            }
        } catch (Throwable ignored) {}

        if (!player.isOnline()) return;

        // Structured, directed climax: expanding ground disk & vertical beam of light
        Location pCenter = player.getLocation().add(0, 1.5, 0);
        player.getWorld().strikeLightningEffect(pCenter);
        player.getWorld().spawnParticle(Particle.FLASH, pCenter, 2, 0.1, 0.1, 0.1, 0);

        Particle.DustOptions sinDust = new Particle.DustOptions(sinColor, 2.0f);
        Particle.DustOptions accentDust = new Particle.DustOptions(accentColor, 1.6f);

        // Directed horizontal shockwave disk along the ground
        Location ground = player.getLocation().add(0, 0.1, 0);
        for (double r = 1.0; r <= 3.5; r += 0.8) {
            for (int d = 0; d < 360; d += 20) {
                double rad = Math.toRadians(d);
                ground.getWorld().spawnParticle(Particle.DUST,
                        ground.clone().add(Math.cos(rad) * r, 0, Math.sin(rad) * r),
                        1, 0, 0, 0, 0, sinDust);
            }
        }

        // Directed vertical pillar of light shooting straight up into the heavens
        for (double y = 0; y <= 10.0; y += 0.5) {
            Location pBeam = player.getLocation().add(0, y, 0);
            pBeam.getWorld().spawnParticle(Particle.DUST, pBeam, 1, 0, 0, 0, 0, sinDust);
            if ((int) y % 2 == 0) {
                pBeam.getWorld().spawnParticle(Particle.END_ROD, pBeam, 1, 0, 0.05, 0, 0.01);
            }
        }

        player.playSound(pCenter, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.2f);
        player.playSound(pCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.6f, 1.0f);

        // Safe landing
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
        player.setFallDistance(0);

        // Attune the player
        plugin.getSinGemManager().attune(player, chosenGem);

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
