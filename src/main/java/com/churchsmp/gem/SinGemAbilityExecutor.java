package com.churchsmp.gem;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SinGemAbilityExecutor {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Random random = new Random();

    // Wrath state
    private final Map<UUID, Long> furyEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> revengeStacks = new ConcurrentHashMap<>();
    private final Set<UUID> overdriveArmed = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Greed state
    private final Map<UUID, Map<UUID, Long>> goldSiphonCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sealedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeTethers = new ConcurrentHashMap<>(); // Greed Player -> Target Player
    private final Map<UUID, ItemStack> preloadedRitualItems = new ConcurrentHashMap<>();

    // Lust state
    private final Set<UUID> lustCloneIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, UUID> cloneOwnerMap = new ConcurrentHashMap<>(); // Clone UUID -> Lust Player UUID

    // Envy state
    private final Map<UUID, Location> shameChambers = new ConcurrentHashMap<>(); // Target UUID -> Chamber Center
    private final Map<UUID, Long> shameChamberEndTimes = new ConcurrentHashMap<>();

    // Pride state
    private final Map<UUID, Integer> prideCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Location> prideLastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> tombstoneArenas = new ConcurrentHashMap<>(); // Player UUID -> Arena Center
    private final Map<UUID, Long> tombstoneEndTimes = new ConcurrentHashMap<>();

    // Sloth state
    private final Map<UUID, Double> delayedStasisAccumulated = new ConcurrentHashMap<>();
    private final Map<UUID, Long> delayedStasisEndTimes = new ConcurrentHashMap<>();

    public SinGemAbilityExecutor(ChurchSMP plugin) {
        this.plugin = plugin;
        startTickingLoops();
    }

    private void startTickingLoops() {
        // Pride charges accumulator (every 20 ticks = 1 sec)
        // Sloth Hunger applicator
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
                    if (gem == null) continue;

                    // PRIDE: Holding sneak stationary gives +1 charge/sec up to 5
                    if (gem == SinGemType.PRIDE) {
                        UUID uuid = player.getUniqueId();
                        if (player.isSneaking()) {
                            Location curr = player.getLocation();
                            Location prev = prideLastLocations.get(uuid);
                            if (prev != null && curr.getWorld().equals(prev.getWorld()) && curr.distanceSquared(prev) < 0.05) {
                                int current = prideCharges.getOrDefault(uuid, 0);
                                if (current < 5) {
                                    current++;
                                    prideCharges.put(uuid, current);
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f + (current * 0.15f));
                                    player.sendActionBar(miniMessage.deserialize("<gold>✦ " + TextUtil.toSmallCaps("Pride Charges") + ": <yellow>[" + "■".repeat(current) + "□".repeat(5 - current) + "] " + current + "/5 ✦</gold>"));
                                }
                            }
                            prideLastLocations.put(uuid, curr);
                        } else {
                            prideCharges.put(uuid, 0);
                            prideLastLocations.remove(uuid);
                        }
                    }

                    // SLOTH: Permanent Hunger I
                    if (gem == SinGemType.SLOTH) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 0, false, false, true));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // -------------------------------------------------------------------------
    // INPUT HANDLERS
    // -------------------------------------------------------------------------

    /**
     * Triggered on Sneak + Left Click (Attack / Air Swing)
     */
    public boolean handleSneakLMB(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                // Ability 1: Blood Scythe (Sneak + LMB | 40s CD)
                String key = "gem_wrath_scythe";
                if (checkCooldown(player, key, 40)) return true;
                activateBloodScythe(player);
                return true;
            }
            case GLUTTONY -> {
                // Ability 2: Acid Spout (Sneak + LMB | 40s CD)
                String key = "gem_gluttony_acid";
                if (checkCooldown(player, key, 40)) return true;
                activateAcidSpout(player);
                return true;
            }
            case LUST -> {
                // Ability 2: Vanity Shield (Sneak + LMB | 45s CD)
                String key = "gem_lust_shield";
                if (checkCooldown(player, key, 45)) return true;
                activateVanityShield(player);
                return true;
            }
            case SLOTH -> {
                // Ability 1: Temporal Echo (Sneak + LMB | 35s CD)
                String key = "gem_sloth_echo";
                if (checkCooldown(player, key, 35)) return true;
                activateTemporalEcho(player);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Triggered on LMB Attack (Melee hit without sneak)
     */
    public boolean handleLMBAttack(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                // Ability 2: Overdrive (LMB Attack | 60s CD) - Requires >= 2 Revenge Stacks
                UUID uuid = player.getUniqueId();
                int rev = revengeStacks.getOrDefault(uuid, 0);
                if (rev >= 2) {
                    String key = "gem_wrath_overdrive";
                    if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                        plugin.getCooldownManager().setCooldown(player, key, 60);
                        revengeStacks.put(uuid, rev - 2);
                        activateOverdrive(player, target);
                        grantBloodlust(player);
                        return true;
                    }
                }
            }
            case GLUTTONY -> {
                // Ability 1: Devour Buff (LMB Attack | 120s CD)
                String key = "gem_gluttony_devour";
                if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                    plugin.getCooldownManager().setCooldown(player, key, 120);
                    activateDevourBuff(player, target);
                    return true;
                }
            }
            case LUST -> {
                // Ability 1: Narcissus Mirror (LMB Attack | 30s CD)
                String key = "gem_lust_mirror";
                if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                    plugin.getCooldownManager().setCooldown(player, key, 30);
                    activateNarcissusMirror(player, target);
                    return true;
                }
            }
            case ENVY -> {
                // Ability 1: Mirror of Shame (LMB Attack | 70s CD)
                String key = "gem_envy_mirror";
                if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                    plugin.getCooldownManager().setCooldown(player, key, 70);
                    activateMirrorOfShame(player, target);
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    /**
     * Triggered on RMB (Right Click without sneak)
     */
    public boolean handleRMB(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return false;

        switch (gem) {
            case GREED -> {
                // Ability 1: Taxing Ray (RMB | 32s CD)
                String key = "gem_greed_ray";
                if (checkCooldown(player, key, 32)) return true;
                activateTaxingRay(player);
                return true;
            }
            case ENVY -> {
                // Ability 2: Shadow Covet (RMB | 25s CD)
                String key = "gem_envy_covet";
                if (checkCooldown(player, key, 25)) return true;
                activateShadowCovet(player);
                return true;
            }
            case PRIDE -> {
                // Ability 1: Sovereign Charge (RMB | 18s CD)
                String key = "gem_pride_charge";
                if (checkCooldown(player, key, 18)) return true;
                activateSovereignCharge(player);
                return true;
            }
            case SLOTH -> {
                // Ability 2: Delayed Stasis (RMB | 45s CD)
                String key = "gem_sloth_stasis";
                if (checkCooldown(player, key, 45)) return true;
                activateDelayedStasis(player);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Triggered on Sneak + RMB
     */
    public boolean handleSneakRMB(Player player) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return false;

        switch (gem) {
            case GREED -> {
                // Ability 2: Taken (Sneak + RMB | 120s CD)
                String key = "gem_greed_taken";
                if (checkCooldown(player, key, 120)) return true;
                activateTaken(player);
                return true;
            }
            case PRIDE -> {
                // Ability 2: Tombstone Duel (Sneak + RMB | 50s CD)
                String key = "gem_pride_duel";
                if (checkCooldown(player, key, 50)) return true;
                activateTombstoneDuel(player);
                return true;
            }
            default -> { return false; }
        }
    }

    private boolean checkCooldown(Player player, String key, int cdSeconds) {
        if (plugin.getCooldownManager().isOnCooldown(player, key)) {
            double remaining = plugin.getCooldownManager().getRemainingCooldownSeconds(player, key);
            player.sendMessage(miniMessage.deserialize("<red>✦ <white>" + TextUtil.toSmallCaps("Ability is on cooldown") + ":</white> " + String.format("%.1f", remaining) + "s</red>"));
            return true;
        }
        plugin.getCooldownManager().setCooldown(player, key, cdSeconds);
        return false;
    }

    // -------------------------------------------------------------------------
    // 1. WRATH IMPLEMENTATION
    // -------------------------------------------------------------------------
    private void activateBloodScythe(Player player) {
        long end = System.currentTimeMillis() + 8000;
        furyEndTimes.put(player.getUniqueId(), end);
        grantBloodlust(player);

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.5f);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);
        player.sendMessage(miniMessage.deserialize("<red><bold>✦ [FURY] " + TextUtil.toSmallCaps("Blood Scythe ignited! Guaranteed crits & true fire damage for 8s.") + " ✦</bold></red>"));

        new BukkitRunnable() {
            int ticks = 40; // 8 seconds (every 4 ticks)
            @Override
            public void run() {
                if (!player.isOnline() || System.currentTimeMillis() > end) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 0.8, 0), 4, 0.2, 0.4, 0.2, 0.02);
                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void activateOverdrive(Player player, LivingEntity target) {
        Location center = target.getLocation();
        player.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
        player.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.6f);

        new BukkitRunnable() {
            int ticks = 40; // 2 seconds
            @Override
            public void run() {
                if (ticks <= 0) {
                    cancel();
                    return;
                }

                // 5x5 ring of flames
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                            Location fLoc = center.clone().add(x, 0.1, z);
                            fLoc.getWorld().spawnParticle(Particle.FLAME, fLoc, 3, 0.1, 0.3, 0.1, 0.02);
                            fLoc.getWorld().spawnParticle(Particle.LAVA, fLoc, 1, 0, 0, 0, 0);
                        }
                    }
                }

                // Inflict Stun, Darkness, and 0.5 True Damage every 4 ticks
                if (ticks % 4 == 0) {
                    for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, 3.0)) {
                        if (e.equals(player)) continue;
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 128, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
                        e.damage(1.0, player); // 0.5 heart = 1.0 hp
                    }
                }

                ticks -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void grantBloodlust(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.6f);
    }

    public boolean isFuryActive(Player player) {
        Long end = furyEndTimes.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() <= end;
    }

    public void addRevengeStack(Player player) {
        UUID uuid = player.getUniqueId();
        int rev = revengeStacks.getOrDefault(uuid, 0);
        if (rev < 5) {
            rev++;
            revengeStacks.put(uuid, rev);
            player.sendActionBar(miniMessage.deserialize("<red>✦ [FURY] " + TextUtil.toSmallCaps("Revenge Stacks") + ": <yellow>" + rev + "/5</yellow> ✦</red>"));
        }
    }

    // -------------------------------------------------------------------------
    // 2. GREED IMPLEMENTATION
    // -------------------------------------------------------------------------
    public void preloadRelic(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(miniMessage.deserialize("<red>✦ " + TextUtil.toSmallCaps("You must hold an item to preload into your Relic slot!") + " ✦</red>"));
            return;
        }
        ItemStack copy = item.clone();
        player.getInventory().setItemInOffHand(copy);
        preloadedRitualItems.put(player.getUniqueId(), copy);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        player.sendMessage(miniMessage.deserialize("<gold>✦ [GREED PRELOAD] <white>" + TextUtil.toSmallCaps("Preloaded into Relic slot") + ": </white><yellow>" + copy.getType().name() + " x" + copy.getAmount() + "</yellow> ✦</gold>"));
    }

    public void checkGoldSiphon(Player attacker, Player target) {
        UUID aId = attacker.getUniqueId();
        UUID tId = target.getUniqueId();

        Map<UUID, Long> targetCooldowns = goldSiphonCooldowns.computeIfAbsent(aId, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        Long last = targetCooldowns.get(tId);
        if (last != null && now - last < 20000) return; // 20s internal CD

        if (random.nextDouble() > 0.02) return; // 2% chance

        // Search target's hotbar (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack it = target.getInventory().getItem(i);
            if (it != null && (it.getType() == Material.GOLDEN_APPLE || it.getType() == Material.ENCHANTED_GOLDEN_APPLE || it.getType() == Material.ENDER_PEARL)) {
                ItemStack stolen = it.clone();
                stolen.setAmount(1);
                it.subtract(1);

                attacker.getInventory().addItem(stolen);
                targetCooldowns.put(tId, now);

                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.2f, 1.2f);
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);
                target.playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);

                attacker.sendMessage(miniMessage.deserialize("<gold>✦ [GOLD SIPHON] <yellow>" + TextUtil.toSmallCaps("You siphoned 1 " + stolen.getType().name() + " from " + target.getName() + "'s hotbar!") + " ✦</yellow></gold>"));
                target.sendMessage(miniMessage.deserialize("<red>✦ [GOLD SIPHON] <yellow>" + attacker.getName() + " " + TextUtil.toSmallCaps("stole 1 " + stolen.getType().name() + " directly from your hotbar!") + " ✦</yellow></red>"));
                break;
            }
        }
    }

    private void activateTaxingRay(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 2.0f);

        // Raytrace 8 blocks
        double maxDist = 8.0;
        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, dir, maxDist, 0.5, e -> e instanceof LivingEntity && !e.equals(player));

        // Particle beam
        for (double d = 0.5; d <= maxDist; d += 0.4) {
            Location pLoc = eye.clone().add(dir.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.3f));
            player.getWorld().spawnParticle(Particle.CRIT, pLoc, 1, 0, 0, 0, 0.02);
        }

        if (hit != null && hit.getHitEntity() instanceof Player target) {
            sealedPlayers.put(target.getUniqueId(), System.currentTimeMillis() + 4000);
            target.playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.8f);
            target.sendMessage(miniMessage.deserialize("<gold>✦ [TAXING RAY] <red>" + TextUtil.toSmallCaps("Your offhand and hotbar have been sealed for 4s!") + " ✦</red></gold>"));
            player.sendMessage(miniMessage.deserialize("<gold>✦ [TAXING RAY] <green>" + TextUtil.toSmallCaps("Taxing Ray hit " + target.getName() + "! Sealed for 4s.") + " ✦</green></gold>"));

            new BukkitRunnable() {
                int t = 20;
                @Override
                public void run() {
                    if (t <= 0 || !target.isOnline()) { cancel(); return; }
                    target.getWorld().spawnParticle(Particle.WAX_ON, target.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.02);
                    t -= 2;
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    public boolean isSealed(Player player) {
        Long until = sealedPlayers.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    private void activateTaken(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType() == Material.AIR) {
            player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("Your offhand Relic slot is empty! Use /ritual to preload an item.") + " ✦</red>"));
            return;
        }

        Material mat = offhand.getType();
        String name = mat.name();

        if (name.contains("ORE") || name.startsWith("RAW_")) {
            if (offhand.getAmount() >= 64) {
                offhand.setAmount(offhand.getAmount() - 64);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 3, false, false)); // Absorption IV
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
                player.sendMessage(miniMessage.deserialize("<gold>✦ [TAKEN: 64 ORES] <yellow>" + TextUtil.toSmallCaps("Consumed 64 ores: Granted Absorption IV for 10s!") + " ✦</yellow></gold>"));
            } else {
                player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("Need a full stack (64) of ores to consume!") + " ✦</red>"));
            }
        } else if (mat == Material.NETHERITE_SWORD) {
            offhand.subtract(1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 150, 2, false, false)); // Strength III for 7.5s
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.2f, 1.4f);
            player.sendMessage(miniMessage.deserialize("<gold>✦ [TAKEN: NETHERITE SWORD] <red>" + TextUtil.toSmallCaps("Consumed Netherite Sword: Granted Strength III for 7.5s!") + " ✦</red></gold>"));
        } else if (mat == Material.PLAYER_HEAD) {
            offhand.subtract(1);
            Player nearest = null;
            double minDist = 15.0;
            for (Player p : player.getWorld().getPlayers()) {
                if (p.equals(player)) continue;
                double d = p.getLocation().distance(player.getLocation());
                if (d < minDist) {
                    minDist = d;
                    nearest = p;
                }
            }
            if (nearest != null) {
                final Player tetherTarget = nearest;
                activeTethers.put(player.getUniqueId(), tetherTarget.getUniqueId());
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.8f);
                player.sendMessage(miniMessage.deserialize("<gold>✦ [TAKEN: PLAYER HEAD] <purple>" + TextUtil.toSmallCaps("Tethered to " + tetherTarget.getName() + " for 30s! Siphoning 10% damage.") + " ✦</purple></gold>"));

                new BukkitRunnable() {
                    int ticks = 30 * 20;
                    @Override
                    public void run() {
                        if (ticks <= 0 || !player.isOnline() || !tetherTarget.isOnline()) {
                            activeTethers.remove(player.getUniqueId());
                            cancel();
                            return;
                        }
                        Location pLoc = player.getLocation().add(0, 1.2, 0);
                        Location tLoc = tetherTarget.getLocation().add(0, 1.2, 0);
                        Vector line = tLoc.toVector().subtract(pLoc.toVector());
                        int pts = 6;
                        for (int i = 0; i <= pts; i++) {
                            Location pt = pLoc.clone().add(line.clone().multiply((double) i / pts));
                            pt.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.0f));
                        }
                        ticks -= 10;
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            } else {
                player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("No enemy player within 15 blocks to tether!") + " ✦</red>"));
            }
        } else {
            player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("Invalid preloaded item! Must be 64 Ores, Netherite Sword, or Player Head.") + " ✦</red>"));
        }
    }

    public void checkTetherDamage(Player damager, double damage) {
        for (Map.Entry<UUID, UUID> entry : activeTethers.entrySet()) {
            if (entry.getValue().equals(damager.getUniqueId())) {
                Player greedPlayer = Bukkit.getPlayer(entry.getKey());
                if (greedPlayer != null && greedPlayer.isOnline()) {
                    double heal = damage * 0.10;
                    double newHp = Math.min(greedPlayer.getAttribute(Attribute.MAX_HEALTH).getValue(), greedPlayer.getHealth() + heal);
                    greedPlayer.setHealth(newHp);
                    greedPlayer.getWorld().spawnParticle(Particle.HEART, greedPlayer.getLocation().add(0, 1.5, 0), 2, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. GLUTTONY IMPLEMENTATION
    // -------------------------------------------------------------------------
    public void handleGluttonyFood(Player player, ItemStack food, PlayerItemConsumeEvent event) {
        Material mat = food.getType();

        // Junk food behaves like Golden Apple
        if (mat == Material.ROTTEN_FLESH || mat == Material.SPIDER_EYE || mat == Material.POISONOUS_POTATO) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.removePotionEffect(PotionEffectType.HUNGER);
                player.removePotionEffect(PotionEffectType.POISON);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false));
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
                player.setSaturation(Math.min(20f, player.getSaturation() + 9.6f));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.2f, 1.2f);
                player.sendMessage(miniMessage.deserialize("<green>✦ [BITTER FEAST] <white>" + TextUtil.toSmallCaps("Junk food digested into pure vitality!") + " ✦</white></green>"));
            }, 1L);
        }

        // Good food inflicts Hunger III & Nausea
        if (mat == Material.GOLDEN_APPLE || mat == Material.ENCHANTED_GOLDEN_APPLE ||
                mat == Material.COOKED_BEEF || mat == Material.COOKED_PORKCHOP || mat == Material.COOKED_MUTTON ||
                mat == Material.COOKED_CHICKEN || mat == Material.COOKED_SALMON || mat == Material.COOKED_COD || mat == Material.COOKED_RABBIT) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 2, false, false)); // Hunger III
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 0.6f);
                player.sendMessage(miniMessage.deserialize("<red>✦ [BITTER FEAST] <dark_red>" + TextUtil.toSmallCaps("Clean food sours in your mouth! Hunger III & Nausea inflicted.") + " ✦</dark_red></red>"));
            }, 1L);
        }
    }

    private void activateDevourBuff(Player player, LivingEntity target) {
        int strippedCount = 0;
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects())) {
            int newDur = Math.max(0, effect.getDuration() - 200); // strip 10s (200 ticks)
            target.removePotionEffect(effect.getType());
            if (newDur > 0) {
                target.addPotionEffect(new PotionEffect(effect.getType(), newDur, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
            }
            strippedCount++;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0, false, false));
        if (strippedCount > 0) {
            int amp = Math.min(3, strippedCount - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, amp, false, false));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.2f, 0.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.05);
        player.sendMessage(miniMessage.deserialize("<green>✦ [DEVOUR BUFF] <white>" + TextUtil.toSmallCaps("Devoured 10s from target buffs! Gained Resistance & Absorption.") + " ✦</white></green>"));
    }

    private void activateAcidSpout(Player player) {
        Location origin = player.getLocation().getBlock().getLocation().add(0.5, 0.05, 0.5);
        player.playSound(origin, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.7f);
        player.playSound(origin, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.8f);

        new BukkitRunnable() {
            int ticks = 250; // 12.5 seconds (250 ticks)
            @Override
            public void run() {
                if (ticks <= 0) {
                    cancel();
                    return;
                }

                // Green acid puddle particles
                origin.getWorld().spawnParticle(Particle.DUST, origin, 8, 1.5, 0.1, 1.5, 0, new Particle.DustOptions(Color.fromRGB(50, 205, 50), 1.2f));
                origin.getWorld().spawnParticle(Particle.ITEM_SLIME, origin, 3, 1.2, 0.1, 1.2, 0.02);

                if (ticks % 10 == 0) {
                    for (LivingEntity e : origin.getWorld().getNearbyLivingEntities(origin, 2.5)) {
                        if (e.equals(player)) continue;
                        e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, false, false));

                        // 3x Armor Durability damage
                        if (e instanceof Player targetPlayer) {
                            for (ItemStack armor : targetPlayer.getInventory().getArmorContents()) {
                                if (armor != null && armor.getItemMeta() instanceof Damageable dmg) {
                                    dmg.setDamage(dmg.getDamage() + 3);
                                    armor.setItemMeta(dmg);
                                }
                            }
                        }
                    }
                }

                ticks -= 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // -------------------------------------------------------------------------
    // 4. LUST IMPLEMENTATION
    // -------------------------------------------------------------------------
    private void activateNarcissusMirror(Player player, LivingEntity target) {
        Location tLoc = target.getLocation();
        player.playSound(tLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);

        for (int i = 0; i < 2; i++) {
            double angle = (Math.PI * i) + (Math.PI / 4.0);
            Location spawnLoc = tLoc.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);

            Zombie clone = (Zombie) target.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            clone.setAdult();
            clone.setAI(false);
            clone.setInvulnerable(false);
            clone.setSilent(true);
            clone.setCustomNameVisible(true);
            clone.customName(miniMessage.deserialize("<pink>✦ " + player.getName() + "'s Reflection ✦</pink>"));

            lustCloneIds.add(clone.getUniqueId());
            cloneOwnerMap.put(clone.getUniqueId(), player.getUniqueId());

            clone.getWorld().spawnParticle(Particle.HEART, clone.getLocation().add(0, 1.0, 0), 5, 0.3, 0.3, 0.3, 0.02);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                lustCloneIds.remove(clone.getUniqueId());
                cloneOwnerMap.remove(clone.getUniqueId());
                if (clone.isValid()) {
                    clone.getWorld().spawnParticle(Particle.BLOCK, clone.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, Material.AMETHYST_BLOCK.createBlockData());
                    clone.remove();
                }
            }, 120L); // 6 seconds
        }
        player.sendMessage(miniMessage.deserialize("<pink>✦ [NARCISSUS MIRROR] <white>" + TextUtil.toSmallCaps("Summoned 2 reflections around target! Attacks on them heal you 50%.") + " ✦</white></pink>"));
    }

    public boolean handleCloneDamage(Entity damagee, double damage) {
        if (lustCloneIds.contains(damagee.getUniqueId())) {
            UUID ownerId = cloneOwnerMap.get(damagee.getUniqueId());
            if (ownerId != null) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.isOnline()) {
                    double heal = damage * 0.50;
                    double newHp = Math.min(owner.getAttribute(Attribute.MAX_HEALTH).getValue(), owner.getHealth() + heal);
                    owner.setHealth(newHp);
                    owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                    owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0);
                }
            }
            return true;
        }
        return false;
    }

    private void activateVanityShield(Player player) {
        int cleansed = 0;
        List<PotionEffectType> debuffs = List.of(
                PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS,
                PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.NAUSEA
        );

        for (PotionEffectType type : debuffs) {
            if (player.hasPotionEffect(type)) {
                player.removePotionEffect(type);
                cleansed++;
            }
        }

        if (cleansed > 0) {
            // Each cleansed debuff grants 2 hearts (duration for 4 hp regen = 100 ticks per stack)
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, cleansed * 100, 1, false, false));
        }

        // Force surrounding enemies within 5 blocks to snap 180 degrees away
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 5.0)) {
            if (e.equals(player)) continue;
            Location loc = e.getLocation();
            loc.setYaw((loc.getYaw() + 180f) % 360f);
            e.teleport(loc);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.2, 0), 2, Color.FUCHSIA);
        player.sendMessage(miniMessage.deserialize("<pink>✦ [VANITY SHIELD] <white>" + TextUtil.toSmallCaps("Cleansed " + cleansed + " debuffs! Forced nearby enemies to look away.") + " ✦</white></pink>"));
    }

    // -------------------------------------------------------------------------
    // 5. ENVY IMPLEMENTATION
    // -------------------------------------------------------------------------
    public double getShameScalingBonus(Player attacker, LivingEntity target) {
        int attackerTier = getArmorTier(attacker);
        int targetTier = (target instanceof Player p) ? getArmorTier(p) : 2;

        int attackerBuffs = countPositiveBuffs(attacker);
        int targetBuffs = countPositiveBuffs(target);

        int tierDiff = Math.max(0, (targetTier + targetBuffs) - (attackerTier + attackerBuffs));
        return tierDiff * 1.5;
    }

    private int getArmorTier(Player player) {
        int score = 0;
        for (ItemStack it : player.getInventory().getArmorContents()) {
            if (it == null) continue;
            String n = it.getType().name();
            if (n.startsWith("LEATHER")) score += 1;
            else if (n.startsWith("GOLDEN") || n.startsWith("CHAINMAIL")) score += 2;
            else if (n.startsWith("IRON")) score += 3;
            else if (n.startsWith("DIAMOND")) score += 4;
            else if (n.startsWith("NETHERITE")) score += 5;
        }
        return score / 4;
    }

    private int countPositiveBuffs(LivingEntity entity) {
        int c = 0;
        for (PotionEffect e : entity.getActivePotionEffects()) {
            PotionEffectType t = e.getType();
            if (t.equals(PotionEffectType.SPEED) || t.equals(PotionEffectType.STRENGTH) ||
                    t.equals(PotionEffectType.RESISTANCE) || t.equals(PotionEffectType.REGENERATION) ||
                    t.equals(PotionEffectType.ABSORPTION) || t.equals(PotionEffectType.FIRE_RESISTANCE)) {
                c++;
            }
        }
        return c;
    }

    private void activateMirrorOfShame(Player player, LivingEntity target) {
        Location center = target.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        shameChambers.put(target.getUniqueId(), center);
        long end = System.currentTimeMillis() + 3500;
        shameChamberEndTimes.put(target.getUniqueId(), end);

        player.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 1.2f);
        target.sendMessage(miniMessage.deserialize("<dark_aqua>✦ [MIRROR OF SHAME] <white>" + TextUtil.toSmallCaps("Banished to Chamber for 3.5s! All outgoing damage is reflected.") + " ✦</white></dark_aqua>"));

        new BukkitRunnable() {
            int ticks = 35;
            @Override
            public void run() {
                if (ticks <= 0 || !target.isValid() || System.currentTimeMillis() > end) {
                    shameChambers.remove(target.getUniqueId());
                    shameChamberEndTimes.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                // 3x3 barrier ring particles
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                            for (double y = 0; y <= 2.5; y += 0.8) {
                                Location bLoc = center.clone().add(x * 1.5, y, z * 1.5);
                                bLoc.getWorld().spawnParticle(Particle.DUST, bLoc, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0, 206, 209), 1.2f));
                            }
                        }
                    }
                }

                // Keep target inside 3x3
                if (target.getLocation().distanceSquared(center) > 4.0) {
                    target.teleport(center);
                }

                ticks -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public boolean isInsideShameChamber(UUID targetId) {
        Long end = shameChamberEndTimes.get(targetId);
        return end != null && System.currentTimeMillis() <= end;
    }

    private void activateShadowCovet(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location cloneLoc = player.getLocation().add(dir.multiply(2.5));

        player.playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1.2f, 0.8f);

        // Spawn a shadowy stand/clone visual for 3s
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(cloneLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);

        new BukkitRunnable() {
            int ticks = 60; // 3 seconds
            @Override
            public void run() {
                if (ticks <= 0 || !stand.isValid()) {
                    stand.remove();
                    cancel();
                    return;
                }

                stand.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, stand.getLocation().add(0, 1.0, 0), 8, 0.3, 0.6, 0.3, 0.02);
                stand.getWorld().spawnParticle(Particle.LARGE_SMOKE, stand.getLocation().add(0, 1.0, 0), 4, 0.2, 0.5, 0.2, 0.01);

                // Check looking-at by enemies
                for (Player enemy : stand.getWorld().getPlayers()) {
                    if (enemy.equals(player)) continue;
                    Vector toStand = stand.getLocation().toVector().subtract(enemy.getEyeLocation().toVector()).normalize();
                    if (enemy.getEyeLocation().getDirection().dot(toStand) > 0.8) {
                        enemy.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false));
                        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1, false, false));
                        enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1, false, false));

                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0, false, false));
                    }
                }

                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        player.sendMessage(miniMessage.deserialize("<dark_aqua>✦ [SHADOW COVET] <white>" + TextUtil.toSmallCaps("Shadow clone spawned! Foes looking at it have stats stolen.") + " ✦</white></dark_aqua>"));
    }

    // -------------------------------------------------------------------------
    // 6. PRIDE IMPLEMENTATION
    // -------------------------------------------------------------------------
    private void activateSovereignCharge(Player player) {
        int charges = prideCharges.getOrDefault(player.getUniqueId(), 0);
        prideCharges.put(player.getUniqueId(), 0);

        double power = 1.0 + (charges * 0.4);
        Vector dir = player.getEyeLocation().getDirection().normalize().multiply(power).setY(0.2);
        player.setVelocity(dir);

        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                if (t > 15 || !player.isOnline()) { cancel(); return; }

                player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0, 1.0, 0), 1, Color.WHITE);
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.0, 0), 3, 0.2, 0.2, 0.2, 0.05);

                for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 2.0)) {
                    if (e.equals(player)) continue;
                    e.damage(4.0, player); // 2 hearts

                    if (e instanceof Player targetPlayer && targetPlayer.isBlocking()) {
                        targetPlayer.setCooldown(Material.SHIELD, 100); // 5s shield disable
                        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage(miniMessage.deserialize("<gold>✦ [SOVEREIGN CHARGE] <white>" + TextUtil.toSmallCaps("Consumed " + charges + " charges for piercing lunge!") + " ✦</white></gold>"));
    }

    private void activateTombstoneDuel(Player player) {
        LivingEntity target = null;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 10.0)) {
            if (e.equals(player)) continue;
            Vector toE = e.getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            if (player.getEyeLocation().getDirection().dot(toE) > 0.6) {
                target = e;
                break;
            }
        }
        if (target == null) {
            for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 6.0)) {
                if (!e.equals(player)) {
                    target = e;
                    break;
                }
            }
        }
        activateTombstoneDuel(player, target);
    }

    private void activateTombstoneDuel(Player player, LivingEntity target) {
        Location center = (target != null) ? player.getLocation().add(target.getLocation()).multiply(0.5) : player.getLocation();
        tombstoneArenas.put(player.getUniqueId(), center);
        long end = System.currentTimeMillis() + 6000;
        tombstoneEndTimes.put(player.getUniqueId(), end);

        player.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        player.sendMessage(miniMessage.deserialize("<gold>✦ [TOMBSTONE DUEL] <white>" + TextUtil.toSmallCaps("6x6 Arena erected! Securing a kill inside restores 100% HP!") + " ✦</white></gold>"));

        new BukkitRunnable() {
            int ticks = 120; // 6 seconds
            @Override
            public void run() {
                if (ticks <= 0 || !player.isOnline() || System.currentTimeMillis() > end) {
                    tombstoneArenas.remove(player.getUniqueId());
                    tombstoneEndTimes.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // 6x6 ring of pillars
                int radius = 3;
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI / 16) * i;
                    Location pLoc = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    for (double y = 0; y <= 3.0; y += 0.6) {
                        pLoc.getWorld().spawnParticle(Particle.DUST, pLoc.clone().add(0, y, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(245, 245, 220), 1.2f));
                    }
                }

                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public void checkTombstoneKill(Player killer) {
        Location arena = tombstoneArenas.get(killer.getUniqueId());
        if (arena != null && killer.getLocation().distanceSquared(arena) <= 16.0) {
            double maxHp = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
            killer.setHealth(maxHp);
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
            killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, killer.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);

            // Enhance active potions to 3x duration and 2x strength
            for (PotionEffect e : new ArrayList<>(killer.getActivePotionEffects())) {
                killer.removePotionEffect(e.getType());
                killer.addPotionEffect(new PotionEffect(e.getType(), e.getDuration() * 3, Math.min(4, e.getAmplifier() * 2 + 1)));
            }

            killer.sendMessage(miniMessage.deserialize("<gold>✦ [TOMBSTONE DUEL] <yellow><bold>" + TextUtil.toSmallCaps("DUEL WON! 100% HP RESTORED & POTIONS EMPOWERED!") + "</bold></yellow> ✦</gold>"));
        }
    }

    // -------------------------------------------------------------------------
    // 7. SLOTH IMPLEMENTATION
    // -------------------------------------------------------------------------
    private void activateTemporalEcho(Player player) {
        Location pLoc = player.getLocation();
        player.playSound(pLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.7f);

        new BukkitRunnable() {
            int ticks = 100; // 5 seconds
            @Override
            public void run() {
                if (ticks <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }

                pLoc.getWorld().spawnParticle(Particle.PORTAL, pLoc.clone().add(0, 1.0, 0), 12, 1.5, 0.5, 1.5, 0.02);
                pLoc.getWorld().spawnParticle(Particle.SOUL, pLoc.clone().add(0, 0.5, 0), 4, 1.2, 0.3, 1.2, 0.01);

                // Freeze incoming projectiles
                for (Entity entity : pLoc.getWorld().getNearbyEntities(pLoc, 4.0, 4.0, 4.0)) {
                    if (entity instanceof Projectile proj) {
                        proj.setVelocity(new Vector(0, -0.05, 0));
                    }
                    if (entity instanceof Player enemy && !enemy.equals(player)) {
                        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                    }
                }

                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        player.sendMessage(miniMessage.deserialize("<blue>✦ [TEMPORAL ECHO] <white>" + TextUtil.toSmallCaps("Temporal ghost freezing incoming arrows and slowing foes!") + " ✦</white></blue>"));
    }

    private void activateDelayedStasis(Player player) {
        long end = System.currentTimeMillis() + 6000;
        delayedStasisEndTimes.put(player.getUniqueId(), end);
        delayedStasisAccumulated.put(player.getUniqueId(), 0.0);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
        player.sendMessage(miniMessage.deserialize("<blue>✦ [DELAYED STASIS] <white>" + TextUtil.toSmallCaps("Negating all damage for 6s! 50% will be slowly repaid over 10s.") + " ✦</white></blue>"));

        new BukkitRunnable() {
            int ticks = 120; // 6 seconds
            @Override
            public void run() {
                if (ticks <= 0 || !player.isOnline()) {
                    // Stasis expired: apply 50% of negated damage over 10 seconds
                    delayedStasisEndTimes.remove(player.getUniqueId());
                    double accumulated = delayedStasisAccumulated.getOrDefault(player.getUniqueId(), 0.0);
                    delayedStasisAccumulated.remove(player.getUniqueId());

                    if (accumulated > 0 && player.isOnline()) {
                        double totalToTake = accumulated * 0.50;
                        double tickDamage = Math.max(0.5, totalToTake / 10.0);

                        player.sendMessage(miniMessage.deserialize("<dark_blue>✦ [STASIS EXPIRED] <red>" + TextUtil.toSmallCaps("Stasis debt arriving: " + String.format("%.1f", totalToTake) + " damage over 10s!") + " ✦</red></dark_blue>"));

                        new BukkitRunnable() {
                            int second = 0;
                            @Override
                            public void run() {
                                second++;
                                if (second > 10 || !player.isOnline()) {
                                    cancel();
                                    return;
                                }
                                player.damage(tickDamage);
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f);
                                player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1.0, 0), 2);
                            }
                        }.runTaskTimer(plugin, 20L, 20L);
                    }

                    cancel();
                    return;
                }

                player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1.0, 0), 5, 0.4, 0.6, 0.4, 0.02);
                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public boolean isStasisActive(Player player) {
        Long end = delayedStasisEndTimes.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() <= end;
    }

    public void recordStasisDamage(Player player, double dmg) {
        UUID uuid = player.getUniqueId();
        double current = delayedStasisAccumulated.getOrDefault(uuid, 0.0);
        delayedStasisAccumulated.put(uuid, current + dmg);
    }

    // -------------------------------------------------------------------------
    // COMBAT HOOKS & PASSIVE RESOLUTION
    // -------------------------------------------------------------------------

    public void onPlayerHitEntity(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);
        if (gem == null) return;

        // Wrath Bloodfeast & BloodPrice & Fury
        if (gem == SinGemType.WRATH) {
            double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            double missingHearts = Math.max(0, (maxHp - player.getHealth()) / 2.0);
            event.setDamage(event.getDamage() + (missingHearts * 0.75));

            int align = plugin.getAlignmentManager().getAlignmentScore(player);
            if (align <= -50) {
                event.setDamage(event.getDamage() * 1.15); // +15% melee damage
            }

            if (isFuryActive(player)) {
                target.setFireTicks(80);
                addRevengeStack(player);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            }
        }

        // Greed Gold Siphon
        if (gem == SinGemType.GREED && target instanceof Player targetPlayer) {
            checkGoldSiphon(player, targetPlayer);
        }

        // Envy Shame Scaling
        if (gem == SinGemType.ENVY) {
            double bonus = getShameScalingBonus(player, target);
            if (bonus > 0) {
                event.setDamage(event.getDamage() + bonus);
            }
        }
    }

    public void onPlayerDamaged(Player player, EntityDamageEvent event) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(player);

        // Narcissus Clone Hit Intercept
        if (handleCloneDamage(player, event.getDamage())) {
            event.setCancelled(true);
            return;
        }

        // Delayed Stasis Check
        if (isStasisActive(player)) {
            recordStasisDamage(player, event.getDamage());
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.5f);
            return;
        }

        if (gem == null) return;

        // Wrath BloodPrice True Damage penalty
        if (gem == SinGemType.WRATH) {
            int align = plugin.getAlignmentManager().getAlignmentScore(player);
            if (align <= -50) {
                event.setDamage(event.getDamage() * 1.10);
            }
        }

        // Lust Narcissism
        if (gem == SinGemType.LUST && event instanceof EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof Player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false)); // Speed III for 2s
        }
    }

    public void onPlayerKill(Player killer, LivingEntity victim) {
        SinGemType gem = plugin.getSinGemManager().getAttunedGem(killer);
        if (gem == null) return;

        if (gem == SinGemType.WRATH) {
            grantBloodlust(killer);
        } else if (gem == SinGemType.PRIDE) {
            checkTombstoneKill(killer);
        }
    }

    public double getDamageMultiplier(Player player) {
        return 1.0;
    }
}
