package com.churchsmp.gem;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.CloneUtil;
import com.churchsmp.util.TextUtil;
import com.churchsmp.util.VFXUtil;
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
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;

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

    public static final Component RITUAL_GUI_TITLE_COMPONENT = MiniMessage.miniMessage()
            .deserialize("<gold><bold>✦ " + TextUtil.toSmallCaps("Ritual Sacrifice") + " ✦</bold></gold>");

    // Pride state
    private final Map<UUID, Integer> prideCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Location> prideLastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> tombstoneArenas = new ConcurrentHashMap<>(); // Player UUID -> Arena Center
    private final Map<UUID, Long> tombstoneEndTimes = new ConcurrentHashMap<>();

    // Sloth state
    private final Map<UUID, Double> delayedStasisAccumulated = new ConcurrentHashMap<>();
    private final Map<UUID, Long> delayedStasisEndTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSneakLMBTimes = new ConcurrentHashMap<>();
    private final Set<UUID> floatingSlothTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // SIN (Mythic Gem) state
    private final Map<UUID, Long> bloodfeastResetCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sinRustyBloodTethers = new ConcurrentHashMap<>(); // Caster UUID -> Victim UUID
    private final Map<UUID, Long> sinRustyBloodTetherExpires = new ConcurrentHashMap<>(); // Caster UUID -> Expire Timestamp
    private final Map<UUID, BukkitRunnable> sinRustyBloodSiphonTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> flagitiousMarkedEntities = new ConcurrentHashMap<>(); // Victim UUID -> Mark Expire Timestamp
    private final Map<UUID, Long> flagitiousAirborneComboTargets = new ConcurrentHashMap<>(); // Victim UUID -> Airborne Combo Expire Timestamp
    private final Set<UUID> flagitiousCasters = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SinGemAbilityExecutor(ChurchSMP plugin) {
        this.plugin = plugin;
        startTickingLoops();
    }

    public SinGemType getEffectiveGem(Player player) {
        if (player == null) return null;
        return plugin.getSinGemManager().getHeldGem(player);
    }

    public boolean hasGemInHand(Player player, SinGemType gem) {
        return plugin.getSinGemManager().isHoldingGem(player, gem);
    }

    public LivingEntity findTargetInLook(Player player, double maxDist) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDist, 0.8, e -> e instanceof LivingEntity && !e.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity le) {
            return le;
        }
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), maxDist)) {
            if (e.equals(player)) continue;
            Vector toE = e.getLocation().toVector().subtract(eye.toVector()).normalize();
            if (eye.getDirection().dot(toE) > 0.6) {
                return e;
            }
        }
        return null;
    }

    private void startTickingLoops() {
        // Pride charges accumulator (every 20 ticks = 1 sec)
        // Sloth Hunger applicator
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    SinGemType gem = getEffectiveGem(player);
                    if (gem == null) {
                        prideCharges.put(player.getUniqueId(), 0);
                        prideLastLocations.remove(player.getUniqueId());
                        continue;
                    }

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
                                    player.sendMessage(miniMessage.deserialize("<gold>✦ " + TextUtil.toSmallCaps("Pride Charges") + ": <yellow>[" + "■".repeat(current) + "□".repeat(5 - current) + "] " + current + "/5 ✦</gold>"));
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
        long now = System.currentTimeMillis();
        Long last = lastSneakLMBTimes.get(player.getUniqueId());
        if (last != null && now - last < 350) return false;
        lastSneakLMBTimes.put(player.getUniqueId(), now);

        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                String key = "gem_wrath_scythe";
                if (checkCooldown(player, key, 40)) return true;
                activateBloodScythe(player);
                return true;
            }
            case GLUTTONY -> {
                String key = "gem_gluttony_acid";
                if (checkCooldown(player, key, 40)) return true;
                activateAcidSpout(player);
                return true;
            }
            case LUST -> {
                String key = "gem_lust_shield";
                if (checkCooldown(player, key, 45)) return true;
                activateVanityShield(player);
                return true;
            }
            case SLOTH -> {
                String key = "gem_sloth_echo";
                if (checkCooldown(player, key, 35)) return true;
                activateTemporalEcho(player);
                return true;
            }
            case GREED -> {
                String key = "gem_greed_ray";
                if (checkCooldown(player, key, 32)) return true;
                activateTaxingRay(player);
                return true;
            }
            case ENVY -> {
                String key = "gem_envy_covet";
                if (checkCooldown(player, key, 25)) return true;
                activateShadowCovet(player);
                return true;
            }
            case PRIDE -> {
                String key = "gem_pride_charge";
                if (checkCooldown(player, key, 18)) return true;
                activateSovereignCharge(player);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Triggered on LMB Attack (Melee hit without sneak)
     */
    public boolean handleLMBAttack(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                // Kinetic Trigger: Sprint Attack -> Blood Rush
                if (player.isSprinting()) {
                    String key = "gem_wrath_rush";
                    if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                        plugin.getCooldownManager().setCooldown(player, key, 8);
                        executeWrathBloodRush(player, target, event);
                        return true;
                    }
                }
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
            case SLOTH -> {
                UUID targetId = target.getUniqueId();
                // Air hit on floating target -> Meteor Spike
                if (floatingSlothTargets.contains(targetId) && (!player.isOnGround() || player.getFallDistance() > 0.05f)) {
                    executeSlothMeteorSpike(player, target, event);
                    return true;
                } else if (!floatingSlothTargets.contains(targetId)) {
                    // Ground hit -> Zero-G Float Suspension
                    String key = "gem_sloth_float";
                    if (!plugin.getCooldownManager().isOnCooldown(player, key)) {
                        plugin.getCooldownManager().setCooldown(player, key, 10);
                        executeSlothZeroGFloat(player, target);
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
     * Triggered on RMB (Right Click without sneak) — Executes Primary Ability (Skill 1)
     */
    public boolean handleRMB(Player player) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                // Ability 1: Blood Scythe (RMB | 40s CD)
                String key = "gem_wrath_scythe";
                if (checkCooldown(player, key, 40)) return true;
                activateBloodScythe(player);
                return true;
            }
            case GREED -> {
                // Ability 1: Taxing Ray (RMB | 32s CD)
                String key = "gem_greed_ray";
                if (checkCooldown(player, key, 32)) return true;
                activateTaxingRay(player);
                return true;
            }
            case GLUTTONY -> {
                // Ability 1: Devour Buff (RMB | 120s CD)
                String key = "gem_gluttony_devour";
                if (checkCooldown(player, key, 120)) return true;
                activateDevourBuff(player, findTargetInLook(player, 6.0));
                return true;
            }
            case LUST -> {
                // Ability 1: Narcissus Mirror (RMB | 30s CD)
                String key = "gem_lust_mirror";
                if (checkCooldown(player, key, 30)) return true;
                activateNarcissusMirror(player, findTargetInLook(player, 8.0));
                return true;
            }
            case ENVY -> {
                // Ability 1: Mirror of Shame (RMB | 70s CD)
                LivingEntity target = findTargetInLook(player, 8.0);
                if (target == null) {
                    player.sendMessage(miniMessage.deserialize("<red>✦ [MIRROR OF SHAME] " + TextUtil.toSmallCaps("Aim at an enemy within 8 blocks to trap them in shame!") + " ✦</red>"));
                    return true;
                }
                String key = "gem_envy_mirror";
                if (checkCooldown(player, key, 70)) return true;
                activateMirrorOfShame(player, target);
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
                // Ability 1: Temporal Echo (RMB | 35s CD)
                String key = "gem_sloth_echo";
                if (checkCooldown(player, key, 35)) return true;
                activateTemporalEcho(player);
                return true;
            }
            case SIN -> {
                // Skill 1: Rusty Blood (RMB | 35s CD)
                handleRustyBloodClick(player);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Triggered on Sneak + RMB — Executes Secondary Ability (Skill 2)
     */
    public boolean handleSneakRMB(Player player) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return false;

        switch (gem) {
            case WRATH -> {
                // Ability 2: Overdrive (Sneak + RMB | 60s CD)
                UUID uuid = player.getUniqueId();
                int rev = revengeStacks.getOrDefault(uuid, 0);
                if (rev < 2) {
                    player.sendMessage(miniMessage.deserialize("<red>✦ [OVERDRIVE] " + TextUtil.toSmallCaps("Requires 2 Revenge Stacks (currently ") + rev + "/2)! Attack or take hits during Fury to gain Revenge Stacks. ✦</red>"));
                    return true;
                }
                String key = "gem_wrath_overdrive";
                if (checkCooldown(player, key, 60)) return true;
                revengeStacks.put(uuid, rev - 2);
                activateOverdrive(player, findTargetInLook(player, 8.0));
                grantBloodlust(player);
                return true;
            }
            case GREED -> {
                // Ability 2: Taken (Sneak + RMB | 120s CD)
                String key = "gem_greed_taken";
                if (plugin.getCooldownManager().isOnCooldown(player, key)) {
                    double remaining = plugin.getCooldownManager().getRemainingCooldownSeconds(player, key);
                    player.sendMessage(miniMessage.deserialize("<red>✦ <white>" + TextUtil.toSmallCaps("Ability is on cooldown") + ":</white> " + String.format("%.1f", remaining) + "s</red>"));
                    return true;
                }
                if (activateTaken(player)) {
                    plugin.getCooldownManager().setCooldown(player, key, 120);
                }
                return true;
            }
            case GLUTTONY -> {
                // Ability 2: Acid Spout (Sneak + RMB | 40s CD)
                String key = "gem_gluttony_acid";
                if (checkCooldown(player, key, 40)) return true;
                activateAcidSpout(player);
                return true;
            }
            case LUST -> {
                // Ability 2: Vanity Shield (Sneak + RMB | 45s CD)
                String key = "gem_lust_shield";
                if (checkCooldown(player, key, 45)) return true;
                activateVanityShield(player);
                return true;
            }
            case ENVY -> {
                // Ability 2: Shadow Covet (Sneak + RMB | 25s CD)
                String key = "gem_envy_covet";
                if (checkCooldown(player, key, 25)) return true;
                activateShadowCovet(player);
                return true;
            }
            case PRIDE -> {
                // Ability 2: Tombstone Duel (Sneak + RMB | 50s CD)
                String key = "gem_pride_duel";
                if (checkCooldown(player, key, 50)) return true;
                activateTombstoneDuel(player);
                return true;
            }
            case SLOTH -> {
                // Ability 2: Delayed Stasis (Sneak + RMB | 45s CD)
                String key = "gem_sloth_stasis";
                if (checkCooldown(player, key, 45)) return true;
                activateDelayedStasis(player);
                return true;
            }
            case SIN -> {
                // Skill 2: Veins of Flagitious (Sneak + RMB | 50s CD)
                String key = "gem_sin_flagitious";
                if (checkCooldown(player, key, 50)) return true;
                activateVeinsOfFlagitious(player);
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
        Location center = (target != null) ? target.getLocation() : player.getLocation().add(player.getEyeLocation().getDirection().multiply(3.0));
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
                        plugin.getStunManager().applyTrueStun(e, 40, "WRATH STUN");
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
            player.sendMessage(miniMessage.deserialize("<red>✦ [FURY] " + TextUtil.toSmallCaps("Revenge Stacks") + ": <yellow>" + rev + "/5</yellow> ✦</red>"));
        }
    }

    private void executeWrathBloodRush(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        player.setVelocity(dir.clone().multiply(1.5).setY(0.12));

        // Razor-sharp crescent blade arc in crimson and lava tones
        VFXUtil.drawSlashArc(player.getEyeLocation().add(dir.clone().multiply(0.4)), dir, 2.2, 120, 24,
                Color.fromRGB(180, 0, 0), Color.fromRGB(255, 69, 0), 20.0f);

        // Ground scorch trail with lingering embers
        Location from = player.getLocation();
        Location to = target.getLocation();
        VFXUtil.drawLine(from, to, Particle.FLAME, 0.4, null);
        VFXUtil.drawLine(from.clone().add(0, 0.05, 0), to.clone().add(0, 0.05, 0), Particle.DUST, 0.4,
                new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.4f));

        // Bonus piercing impact and ignite
        event.setDamage(event.getDamage() + 4.5);
        target.setFireTicks(60);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
        player.sendMessage(miniMessage.deserialize("<red>✦ [BLOOD RUSH] <white>" + TextUtil.toSmallCaps("Piercing dash through " + target.getName() + "!") + " ✦</white></red>"));
    }

    public void executeWrathRetributionSpikes(Player victim, LivingEntity damager, double damage) {
        double reflect = damage * 0.35;
        damager.damage(reflect, victim);

        // Dual vector spikes from victim to damager
        Particle.DustOptions spikeDust = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.6f);
        VFXUtil.drawLine(victim.getEyeLocation(), damager.getEyeLocation(), Particle.DUST, 0.35, spikeDust);

        damager.getWorld().playSound(damager.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.6f);
        damager.getWorld().playSound(damager.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.8f);
        victim.sendMessage(miniMessage.deserialize("<red>✦ [RETRIBUTION] <white>" + TextUtil.toSmallCaps("Reflected " + String.format(Locale.US, "%.1f", reflect) + " damage!") + " ✦</white></red>"));
    }

    // -------------------------------------------------------------------------
    // 2. GREED IMPLEMENTATION & RITUAL INVENTORY
    // -------------------------------------------------------------------------
    public void openRitualInventory(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(player, 9, RITUAL_GUI_TITLE_COMPONENT);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<dark_gray>✦ " + TextUtil.toSmallCaps("Ritual Altar") + " ✦</dark_gray>"));
            meta.lore(List.of(
                    miniMessage.deserialize("<gray>Place your sacrifice item in slot 5 (center).</gray>"),
                    miniMessage.deserialize("<yellow>• 64 Ores <gray>(Absorption IV)</gray></yellow>"),
                    miniMessage.deserialize("<yellow>• Netherite Sword <gray>(Strength III)</gray></yellow>"),
                    miniMessage.deserialize("<yellow>• Player Head <gray>(Damage Tether)</gray></yellow>")
            ));
            border.setItemMeta(meta);
        }

        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                inv.setItem(i, border);
            }
        }

        ItemStack current = preloadedRitualItems.get(player.getUniqueId());
        if (current != null && current.getType() != Material.AIR) {
            inv.setItem(4, current.clone());
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    public void setPreloadedItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            preloadedRitualItems.remove(player.getUniqueId());
        } else {
            preloadedRitualItems.put(player.getUniqueId(), item.clone());
        }
    }

    public void removePreloadedItem(Player player) {
        preloadedRitualItems.remove(player.getUniqueId());
    }

    public ItemStack getPreloadedItem(Player player) {
        return preloadedRitualItems.get(player.getUniqueId());
    }

    public void preloadRelic(Player player, ItemStack item) {
        openRitualInventory(player);
    }

    public String getGemAbilityCooldownKey(SinGemType gem, boolean sneak) {
        if (gem == null) return null;
        if (!sneak) {
            return switch (gem) {
                case WRATH -> "gem_wrath_scythe";
                case GREED -> "gem_greed_ray";
                case GLUTTONY -> "gem_gluttony_devour";
                case LUST -> "gem_lust_mirror";
                case ENVY -> "gem_envy_mirror";
                case PRIDE -> "gem_pride_charge";
                case SLOTH -> "gem_sloth_echo";
                case SIN -> "gem_sin_rusty_blood";
            };
        } else {
            return switch (gem) {
                case WRATH -> "gem_wrath_overdrive";
                case GREED -> "gem_greed_taken";
                case GLUTTONY -> "gem_gluttony_acid";
                case LUST -> "gem_lust_shield";
                case ENVY -> "gem_envy_covet";
                case PRIDE -> "gem_pride_duel";
                case SLOTH -> "gem_sloth_stasis";
                case SIN -> "gem_sin_flagitious";
            };
        }
    }

    public boolean isGemAbilityOnCooldown(Player player, boolean sneak) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return false;
        if (!sneak && gem == SinGemType.SIN && hasRustyBloodRecast(player)) {
            return false;
        }
        String key = getGemAbilityCooldownKey(gem, sneak);
        return key != null && plugin.getCooldownManager().isOnCooldown(player, key);
    }

    public double getGemAbilityRemainingCooldown(Player player, boolean sneak) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return 0.0;
        if (!sneak && gem == SinGemType.SIN && hasRustyBloodRecast(player)) {
            return 0.0;
        }
        String key = getGemAbilityCooldownKey(gem, sneak);
        if (key == null) return 0.0;
        return plugin.getCooldownManager().getRemainingCooldownSeconds(player, key);
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

    private boolean activateTaken(Player player) {
        ItemStack item = preloadedRitualItems.get(player.getUniqueId());
        boolean fromPreload = true;

        if (item == null || item.getType() == Material.AIR) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR && plugin.getSinGemManager().getGemType(offhand) == null) {
                item = offhand;
                fromPreload = false;
            }
        }

        if (item == null || item.getType() == Material.AIR) {
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null) {
                    Material m = invItem.getType();
                    if ((m.name().contains("ORE") || m.name().startsWith("RAW_")) && invItem.getAmount() >= 64) {
                        item = invItem;
                        fromPreload = false;
                        break;
                    } else if (m == Material.NETHERITE_SWORD || m == Material.PLAYER_HEAD) {
                        item = invItem;
                        fromPreload = false;
                        break;
                    }
                }
            }
        }

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("No eligible sacrifice found! Preload with /ritual or hold 64 Ores, Netherite Sword, or Player Head.") + " ✦</red>"));
            return false;
        }

        Material mat = item.getType();
        String name = mat.name();

        if (name.contains("ORE") || name.startsWith("RAW_")) {
            if (item.getAmount() >= 64) {
                item.setAmount(item.getAmount() - 64);
                if (fromPreload && item.getAmount() <= 0) preloadedRitualItems.remove(player.getUniqueId());
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 3, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
                player.sendMessage(miniMessage.deserialize("<gold>✦ [TAKEN: 64 ORES] <yellow>" + TextUtil.toSmallCaps("Consumed 64 ores: Granted Absorption IV for 10s!") + " ✦</yellow></gold>"));
                return true;
            } else {
                player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("Need a full stack (64) of ores to consume!") + " ✦</red>"));
                return false;
            }
        } else if (mat == Material.NETHERITE_SWORD) {
            item.subtract(1);
            if (fromPreload && item.getAmount() <= 0) preloadedRitualItems.remove(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 150, 2, false, false));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.2f, 1.4f);
            player.sendMessage(miniMessage.deserialize("<gold>✦ [TAKEN: NETHERITE SWORD] <red>" + TextUtil.toSmallCaps("Consumed Netherite Sword: Granted Strength III for 7.5s!") + " ✦</red></gold>"));
            return true;
        } else if (mat == Material.PLAYER_HEAD) {
            item.subtract(1);
            if (fromPreload && item.getAmount() <= 0) preloadedRitualItems.remove(player.getUniqueId());
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
                        VFXUtil.drawTetherChain(pLoc, tLoc, 16, Color.fromRGB(255, 215, 0), true, ticks / 10);
                        ticks -= 10;
                    }
                }.runTaskTimer(plugin, 0L, 10L);
                return true;
            } else {
                player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("No enemy player within 15 blocks to tether!") + " ✦</red>"));
                return false;
            }
        } else {
            player.sendMessage(miniMessage.deserialize("<red>✦ [TAKEN] " + TextUtil.toSmallCaps("Invalid preloaded item! Must be 64 Ores, Netherite Sword, or Player Head.") + " ✦</red>"));
            return false;
        }
    }

    public void checkTetherDamage(Player damager, double damage) {
        for (Map.Entry<UUID, UUID> entry : activeTethers.entrySet()) {
            if (entry.getValue().equals(damager.getUniqueId())) {
                Player greedPlayer = Bukkit.getPlayer(entry.getKey());
                if (greedPlayer != null && greedPlayer.isOnline()) {
                    double heal = damage * 0.10;
                    double newHp = Math.min(greedPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), greedPlayer.getHealth() + heal);
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
        Location mawLoc = (target != null) ? target.getLocation() : player.getLocation();
        VFXUtil.drawExponentialTornado(mawLoc, 2.2, 3.0, 12, 10.0, true, Color.fromRGB(34, 139, 34), Color.fromRGB(0, 100, 0));

        if (target == null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 1, false, false));
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.2f, 0.8f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0, 1.0, 0), 8, 0.25, 0.25, 0.25, 0.02);
            player.sendMessage(miniMessage.deserialize("<green>✦ [DEVOUR BUFF] <white>" + TextUtil.toSmallCaps("Self-Devoured! Gained Resistance II, Absorption II & Saturation.") + " ✦</white></green>"));
            return;
        }

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
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1.0, 0), 8, 0.25, 0.25, 0.25, 0.02);
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

                // Green acid puddle particles with undulating sine wave shockwave
                origin.getWorld().spawnParticle(Particle.DUST, origin, 5, 0.6, 0.05, 0.6, 0, new Particle.DustOptions(Color.fromRGB(50, 205, 50), 1.2f));
                origin.getWorld().spawnParticle(Particle.ITEM_SLIME, origin, 2, 0.5, 0.05, 0.5, 0.01);
                if (ticks % 8 == 0) {
                    VFXUtil.drawUndulatingSineRing(origin, 2.2, 28, 4, 0.12, ticks * 0.2, Color.fromRGB(50, 205, 50), 1.3f);
                }

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
        Location tLoc = (target != null) ? target.getLocation() : player.getLocation().add(player.getEyeLocation().getDirection().multiply(2.5));
        player.playSound(tLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        player.playSound(tLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.4f);

        NamespacedKey lustKey = new NamespacedKey(plugin, "lust_clone");

        for (int i = 0; i < 2; i++) {
            double angle = (Math.PI * i) + (Math.PI / 4.0);
            Location spawnLoc = tLoc.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);

            CloneUtil.CloneConfig cfg = new CloneUtil.CloneConfig();
            cfg.owner = player;
            cfg.location = spawnLoc;
            cfg.displayName = miniMessage.deserialize("<pink>✦ " + player.getName() + "'s Reflection ✦</pink>");
            cfg.tagKey = lustKey;
            cfg.tagValue = player.getUniqueId().toString();
            cfg.durationTicks = 120; // 6 seconds
            cfg.movementSpeed = 0.32;
            cfg.formationOffset = new Vector(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
            cfg.followOwner = (target == null);
            cfg.syncSneak = true;
            cfg.attackRange = 3.2;
            cfg.attackDamage = 0.5;
            cfg.jumpCrit = true;
            cfg.onTick = z -> {
                Location cLoc = z.getLocation().add(0, 1.0, 0);
                z.getWorld().spawnParticle(Particle.HEART, cLoc, 1, 0.2, 0.2, 0.2, 0.01);
                z.getWorld().spawnParticle(Particle.DUST, cLoc, 1, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 105, 180), 1.0f));
            };
            cfg.onDespawn = z -> {
                lustCloneIds.remove(z.getUniqueId());
                cloneOwnerMap.remove(z.getUniqueId());
                z.getWorld().spawnParticle(Particle.BLOCK, z.getLocation().add(0, 1.0, 0), 8, 0.25, 0.25, 0.25, 0.02, Material.AMETHYST_BLOCK.createBlockData());
            };

            LivingEntity clone = CloneUtil.spawnRealisticClone(plugin, cfg);
            if (clone != null) {
                lustCloneIds.add(clone.getUniqueId());
                cloneOwnerMap.put(clone.getUniqueId(), player.getUniqueId());
                if (target != null && clone instanceof org.bukkit.entity.Mob mob) {
                    mob.setTarget(target);
                }
                clone.getWorld().spawnParticle(Particle.HEART, clone.getLocation().add(0, 1.0, 0), 8, 0.3, 0.3, 0.3, 0.02);
            }
        }
        player.sendMessage(miniMessage.deserialize("<pink>✦ [NARCISSUS MIRROR] <white>" + TextUtil.toSmallCaps("Summoned 2 reflections! Attacks on them heal you 50%.") + " ✦</white></pink>"));
    }

    public boolean handleCloneDamage(Entity damagee, double damage) {
        if (lustCloneIds.contains(damagee.getUniqueId())) {
            UUID ownerId = cloneOwnerMap.get(damagee.getUniqueId());
            if (ownerId != null) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.isOnline()) {
                    double heal = damage * 0.50;
                    double newHp = Math.min(owner.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), owner.getHealth() + heal);
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
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.7f);

        NamespacedKey envyKey = new NamespacedKey(plugin, "envy_shadow_clone");

        CloneUtil.CloneConfig cfg = new CloneUtil.CloneConfig();
        cfg.owner = player;
        cfg.location = cloneLoc;
        cfg.displayName = miniMessage.deserialize("<dark_aqua>✦ " + player.getName() + "'s Shadow ✦</dark_aqua>");
        cfg.tagKey = envyKey;
        cfg.tagValue = player.getUniqueId().toString();
        cfg.durationTicks = 60; // 3 seconds
        cfg.movementSpeed = 0.28;
        cfg.formationOffset = dir.clone().multiply(2.5);
        cfg.followOwner = false;
        cfg.syncSneak = true;
        cfg.attackRange = 3.0;
        cfg.attackDamage = 0.5;
        cfg.jumpCrit = false;
        cfg.onTick = clone -> {
            Location sLoc = clone.getLocation().add(0, 1.0, 0);
            clone.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, sLoc, 4, 0.25, 0.5, 0.25, 0.02);
            clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, sLoc, 3, 0.2, 0.4, 0.2, 0.01);

            // Check looking-at by enemies
            for (Player enemy : clone.getWorld().getPlayers()) {
                if (enemy.equals(player)) continue;
                Vector toClone = clone.getLocation().toVector().subtract(enemy.getEyeLocation().toVector()).normalize();
                if (enemy.getEyeLocation().getDirection().dot(toClone) > 0.8) {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1, false, false));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1, false, false));

                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0, false, false));
                }
            }
        };
        cfg.onDespawn = clone -> {
            clone.getWorld().spawnParticle(Particle.SMOKE, clone.getLocation().add(0, 1.0, 0), 6, 0.25, 0.35, 0.25, 0.02);
            clone.getWorld().spawnParticle(Particle.SOUL, clone.getLocation().add(0, 1.0, 0), 4, 0.2, 0.3, 0.2, 0.02);
        };

        CloneUtil.spawnRealisticClone(plugin, cfg);

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
            double maxHp = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            killer.setHealth(maxHp);
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
            killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, killer.getLocation().add(0, 1.2, 0), 14, 0.35, 0.35, 0.35, 0.04);

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

                pLoc.getWorld().spawnParticle(Particle.PORTAL, pLoc.clone().add(0, 1.0, 0), 6, 0.4, 0.3, 0.4, 0.02);
                pLoc.getWorld().spawnParticle(Particle.SOUL, pLoc.clone().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0.01);

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

    private void executeSlothZeroGFloat(Player player, LivingEntity target) {
        floatingSlothTargets.add(target.getUniqueId());
        target.setVelocity(new Vector(0, 0.48, 0));

        player.playSound(target.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.2f, 1.6f);
        player.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.8f);
        player.sendMessage(miniMessage.deserialize("<blue>✦ [ZERO-G SUSPENSION] <white>" + TextUtil.toSmallCaps("Floated " + target.getName() + "! Strike from the air to Meteor Spike!") + " ✦</white></blue>"));
        if (target instanceof Player tp) {
            tp.sendMessage(miniMessage.deserialize("<blue>✦ [ZERO-G] <white>" + TextUtil.toSmallCaps("Suspended in zero gravity!") + " ✦</white></blue>"));
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 2;
                if (ticks >= 50 || !target.isValid() || target.isDead() || !floatingSlothTargets.contains(target.getUniqueId())) {
                    floatingSlothTargets.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                // Hovering sway in air
                target.setVelocity(new Vector(0, Math.sin(ticks * 0.25) * 0.04, 0));

                // Undulating sine ring on the floor beneath target (Big Papi formula)
                Location ground = target.getLocation().clone();
                ground.setY(target.getWorld().getHighestBlockYAt(ground) + 0.08);
                VFXUtil.drawUndulatingSineRing(ground, 1.8, 32, 4, 0.18, ticks * 0.15, Color.fromRGB(65, 105, 225), 1.5f);
            }
        }.runTaskTimer(plugin, 5L, 2L);
    }

    private void executeSlothMeteorSpike(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        floatingSlothTargets.remove(target.getUniqueId());

        // Spike target downward violently
        target.setVelocity(new Vector(0, -2.5, 0));

        // Attacker bounce recoil
        Vector pVel = player.getVelocity();
        player.setVelocity(new Vector(pVel.getX(), 0.5, pVel.getZ()));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.8f);
        player.sendMessage(miniMessage.deserialize("<dark_blue>✦ [METEOR SPIKE] <aqua>" + TextUtil.toSmallCaps("Slammed " + target.getName() + " into the floor!") + " ✦</aqua></dark_blue>"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (ticks > 25 || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                if (target.isOnGround() || ticks > 10) {
                    Location impact = target.getLocation();
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
                    impact.getWorld().playSound(impact, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
                    impact.getWorld().playSound(impact, Sound.BLOCK_HEAVY_CORE_FALL, 2.0f, 0.5f);

                    // Expanding crater shockwave with stone debris chunks
                    VFXUtil.drawExpandingCraterShockwave(impact, 2.8, Color.fromRGB(65, 105, 225), Color.fromRGB(25, 25, 112), true);
                    target.damage(10.0, player);
                    plugin.getStunManager().applyTrueStun(target, 30, "METEOR SPIKE");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // COMBAT HOOKS & PASSIVE RESOLUTION
    // -------------------------------------------------------------------------

    public void onPlayerHitEntity(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        SinGemType gem = getEffectiveGem(player);
        if (gem == null) return;

        // Wrath Bloodfeast & BloodPrice & Fury
        if (gem == SinGemType.WRATH) {
            double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double missingHearts = Math.max(0, (maxHp - player.getHealth()) / 2.0);
            event.setDamage(event.getDamage() + (missingHearts * 0.75));

            int align = plugin.getAlignmentManager().getAlignmentScore(player);
            if (align <= -50) {
                event.setDamage(event.getDamage() * 1.15); // +15% melee damage
            }

            if (isFuryActive(player)) {
                target.setFireTicks(80);
                addRevengeStack(player);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 5, 0.2, 0.2, 0.2, 0.02);
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
        SinGemType gem = getEffectiveGem(player);

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

        // Wrath BloodPrice True Damage penalty and Retribution Spikes
        if (gem == SinGemType.WRATH) {
            int align = plugin.getAlignmentManager().getAlignmentScore(player);
            if (align <= -50) {
                event.setDamage(event.getDamage() * 1.10);
            }
            if (event instanceof EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof LivingEntity damager && !damager.equals(player)) {
                executeWrathRetributionSpikes(player, damager, event.getDamage());
            }
        }

        // Bloodfeast Passive: When health drops below 4 HP (< 2 hearts)
        if (gem == SinGemType.SIN) {
            double finalHealth = player.getHealth() - event.getFinalDamage();
            if (finalHealth > 0 && finalHealth < 4.0) {
                triggerBloodfeastLowHealth(player);
            }
        }
    }

    public void onPlayerKill(Player killer, LivingEntity victim) {
        SinGemType gem = getEffectiveGem(killer);
        if (gem == null) return;

        if (gem == SinGemType.WRATH) {
            grantBloodlust(killer);
        } else if (gem == SinGemType.PRIDE) {
            checkTombstoneKill(killer);
        }
    }

    public String getCustomActiveStatus(Player player, SinGemType gem, boolean secondary) {
        if (player == null || gem == null) return null;
        UUID uuid = player.getUniqueId();
        if (!secondary) {
            if (gem == SinGemType.SIN) {
                if (hasRustyBloodRecast(player)) {
                    Long exp = sinRustyBloodTetherExpires.get(uuid);
                    if (exp != null) {
                        double rem = Math.max(0, (exp - System.currentTimeMillis()) / 1000.0);
                        return "ʀᴇᴄᴀꜱᴛ (" + String.format(Locale.US, "%.1f", rem) + "ꜱ)";
                    }
                    return "ʀᴇᴄᴀꜱᴛ ʀᴇᴀᴅʏ";
                }
            } else if (gem == SinGemType.WRATH) {
                if (isFuryActive(player)) {
                    Long end = furyEndTimes.get(uuid);
                    if (end != null) {
                        double rem = Math.max(0, (end - System.currentTimeMillis()) / 1000.0);
                        return "ᴀᴄᴛɪᴠᴇ (" + String.format(Locale.US, "%.1f", rem) + "ꜱ)";
                    }
                }
            } else if (gem == SinGemType.PRIDE) {
                int charges = prideCharges.getOrDefault(uuid, 0);
                if (charges > 0) {
                    return charges + "/5 ᴄʜᴀʀɢᴇꜱ";
                }
            }
        } else {
            if (gem == SinGemType.SIN) {
                if (flagitiousCasters.contains(uuid)) {
                    return "ꜱᴛᴇᴇʀɪɴɢ ᴄᴏʀᴇ";
                }
                if (!flagitiousAirborneComboTargets.isEmpty()) {
                    return "ᴄᴏᴍʙᴏ ʀᴇᴀᴅʏ";
                }
            } else if (gem == SinGemType.WRATH) {
                int stacks = revengeStacks.getOrDefault(uuid, 0);
                if (stacks > 0) {
                    return stacks + "/2 ꜱᴛᴀᴄᴋꜱ";
                }
            } else if (gem == SinGemType.SLOTH) {
                if (isStasisActive(player)) {
                    Long end = delayedStasisEndTimes.get(uuid);
                    if (end != null) {
                        double rem = Math.max(0, (end - System.currentTimeMillis()) / 1000.0);
                        return "ᴀᴄᴛɪᴠᴇ (" + String.format(Locale.US, "%.1f", rem) + "ꜱ)";
                    }
                }
            }
        }
        return null;
    }

    public double getDamageMultiplier(Player player) {
        return 1.0;
    }

    // -------------------------------------------------------------------------
    // 8. MYTHIC SIN GEM: RUSTY BLOOD & VEINS OF FLAGITIOUS IMPLEMENTATION
    // -------------------------------------------------------------------------

    public boolean hasRustyBloodRecast(Player player) {
        UUID uuid = player.getUniqueId();
        Long exp = sinRustyBloodTetherExpires.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            clearRustyBloodTether(player);
            return false;
        }
        return sinRustyBloodTethers.containsKey(uuid);
    }

    public void clearRustyBloodTether(Player player) {
        UUID uuid = player.getUniqueId();
        sinRustyBloodTethers.remove(uuid);
        sinRustyBloodTetherExpires.remove(uuid);
        BukkitRunnable task = sinRustyBloodSiphonTasks.remove(uuid);
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
        }
    }

    public boolean isFlagitiousMarked(UUID victimId) {
        Long exp = flagitiousMarkedEntities.get(victimId);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            flagitiousMarkedEntities.remove(victimId);
            return false;
        }
        return true;
    }

    public boolean isFlagitiousAirborne(UUID victimId) {
        Long exp = flagitiousAirborneComboTargets.get(victimId);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            flagitiousAirborneComboTargets.remove(victimId);
            return false;
        }
        return true;
    }

    public LivingEntity findAirborneComboTarget(Player player, double maxDist) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : flagitiousAirborneComboTargets.entrySet()) {
            if (now > entry.getValue()) {
                flagitiousAirborneComboTargets.remove(entry.getKey());
                continue;
            }
            Entity ent = Bukkit.getEntity(entry.getKey());
            if (ent instanceof LivingEntity target && target.isValid() && !target.isDead() && !target.equals(player)) {
                if (player.getWorld().equals(target.getWorld()) && player.getLocation().distanceSquared(target.getLocation()) <= (maxDist * maxDist)) {
                    return target;
                }
            }
        }
        return null;
    }

    private void handleRustyBloodClick(Player player) {
        if (hasRustyBloodRecast(player)) {
            // Recast: Slam if target is above player / airborne; Drill Cyclone if grounded
            UUID victimId = sinRustyBloodTethers.get(player.getUniqueId());
            Entity ent = victimId != null ? Bukkit.getEntity(victimId) : null;
            if (!(ent instanceof LivingEntity victim) || !victim.isValid() || victim.isDead()) {
                clearRustyBloodTether(player);
                player.sendMessage(miniMessage.deserialize("<red>✦ Tethered victim was lost or slain! ✦</red>"));
                return;
            }

            boolean isAirborne = (victim.getLocation().getY() - player.getLocation().getY() >= 2.5) || isFlagitiousAirborne(victimId);
            if (isAirborne) {
                executeRustyBloodAerialSlam(player, victim);
            } else {
                executeRustyBloodDrillCyclone(player, victim);
            }
        } else {
            // Initial throw: 35s CD
            String key = "gem_sin_rusty_blood";
            if (checkCooldown(player, key, 35)) return;
            executeRustyBloodThrow(player);
        }
    }

    private void executeRustyBloodThrow(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        // Check if there is an airborne combo victim for 100% lock-on hit
        LivingEntity comboTarget = findAirborneComboTarget(player, 35.0);
        if (comboTarget != null) {
            // 100% Lock-on guaranteed hit!
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.4f, 1.8f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 0.6f);
            player.sendMessage(miniMessage.deserialize("<gold><bold>✦ [100% COMBO LOCK-ON]</bold></gold> <yellow>Chains locked onto airborne victim! Press RMB again to slam!</yellow>"));
            VFXUtil.drawTetherChain(eye, comboTarget.getLocation().add(0, 1.0, 0), 16, Color.fromRGB(220, 20, 20), true, 0);
            startRustyBloodSiphon(player, comboTarget);
            return;
        }

        // Standard projectile launch: thrown rotating redstone block with physical link chain
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_THROW, 1.2f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.2f);

        // Spawn visual block display or fallback
        Location spawnLoc = eye.clone().add(dir.clone().multiply(1.2));
        Entity visualEntity = null;
        try {
            org.bukkit.entity.ItemDisplay display = player.getWorld().spawn(spawnLoc, org.bukkit.entity.ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.REDSTONE_BLOCK));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0, 0, 0),
                        new org.joml.Quaternionf(),
                        new org.joml.Vector3f(0.6f, 0.6f, 0.6f),
                        new org.joml.Quaternionf()
                ));
            });
            visualEntity = display;
        } catch (Throwable ignored) {}

        final Entity blockDisplay = visualEntity;
        Location currentLoc = spawnLoc.clone();

        new BukkitRunnable() {
            int step = 0;
            double rotation = 0;

            @Override
            public void run() {
                step++;
                if (step > 25 || !player.isOnline()) {
                    cancel();
                    if (blockDisplay != null && blockDisplay.isValid()) blockDisplay.remove();
                    return;
                }

                currentLoc.add(dir.clone().multiply(1.4));
                rotation += 0.4;

                if (blockDisplay != null && blockDisplay.isValid()) {
                    blockDisplay.teleport(currentLoc);
                }

                // Render physical link chain from player to projectile
                Location handLoc = player.getEyeLocation().add(0, -0.3, 0);
                VFXUtil.drawTetherChain(handLoc, currentLoc, 10, Color.fromRGB(180, 0, 0), true, step);

                // Rotating redstone aura particles
                player.getWorld().spawnParticle(Particle.DUST, currentLoc, 4, 0.15, 0.15, 0.15, new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.4f));
                player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, currentLoc, 2, 0.1, 0.1, 0.1, 0.02);

                // Check collision with living entities
                for (LivingEntity target : currentLoc.getWorld().getNearbyLivingEntities(currentLoc, 1.5)) {
                    if (target.equals(player)) continue;
                    cancel();
                    if (blockDisplay != null && blockDisplay.isValid()) blockDisplay.remove();

                    target.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.4f, 0.8f);
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.4f);
                    target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1.0, 0), 20, 0.3, 0.3, 0.3, Material.REDSTONE_BLOCK.createBlockData());
                    player.sendMessage(miniMessage.deserialize("<dark_red><bold>✦ [RUSTY BLOOD]</bold></dark_red> <red>Chain latched! Siphoning health (6s). Press RMB again to Drill or Slam!</red>"));

                    startRustyBloodSiphon(player, target);
                    return;
                }

                // Check collision with solid blocks
                if (currentLoc.getBlock().getType().isSolid()) {
                    cancel();
                    if (blockDisplay != null && blockDisplay.isValid()) blockDisplay.remove();
                    currentLoc.getWorld().playSound(currentLoc, Sound.BLOCK_CHAIN_BREAK, 1.2f, 0.9f);
                    currentLoc.getWorld().spawnParticle(Particle.BLOCK, currentLoc, 15, 0.2, 0.2, 0.2, Material.REDSTONE_BLOCK.createBlockData());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void startRustyBloodSiphon(Player player, LivingEntity victim) {
        UUID pId = player.getUniqueId();
        UUID vId = victim.getUniqueId();
        sinRustyBloodTethers.put(pId, vId);
        long expireTime = System.currentTimeMillis() + 6000L;
        sinRustyBloodTetherExpires.put(pId, expireTime);

        BukkitRunnable siphonTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (!player.isOnline() || !victim.isValid() || victim.isDead() || System.currentTimeMillis() > expireTime) {
                    clearRustyBloodTether(player);
                    cancel();
                    return;
                }

                Location pLoc = player.getLocation().add(0, 1.2, 0);
                Location vLoc = victim.getLocation().add(0, 1.0, 0);
                if (pLoc.distanceSquared(vLoc) > 1024.0) { // 32 blocks max
                    player.sendMessage(miniMessage.deserialize("<gray>✦ Rusty Blood tether snapped: target too far!</gray>"));
                    clearRustyBloodTether(player);
                    cancel();
                    return;
                }

                // Physical red particle link chain
                if (tick % 2 == 0) {
                    VFXUtil.drawTetherChain(pLoc, vLoc, 12, Color.fromRGB(180, 0, 0), true, tick / 2);
                }

                // Drains health every second (20 ticks) for 6s
                if (tick % 20 == 0) {
                    dealTrueDamage(victim, 1.0, player);
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0));
                    player.getWorld().playSound(pLoc, Sound.ENTITY_GENERIC_DRINK, 0.6f, 1.5f);
                    victim.getWorld().playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f);
                    VFXUtil.drawLine(vLoc, pLoc, Particle.DUST, 0.6, new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.2f));
                }
            }
        };
        siphonTask.runTaskTimer(plugin, 1L, 1L);
        sinRustyBloodSiphonTasks.put(pId, siphonTask);
    }

    private void executeRustyBloodAerialSlam(Player player, LivingEntity victim) {
        clearRustyBloodTether(player);
        boolean isCombo = isFlagitiousMarked(victim.getUniqueId()) || isFlagitiousAirborne(victim.getUniqueId());
        double slamDamage = isCombo ? 12.0 : 8.0; // 6 true hearts if comboed, 4 true hearts normal
        flagitiousAirborneComboTargets.remove(victim.getUniqueId());
        flagitiousMarkedEntities.remove(victim.getUniqueId());

        Location vLoc = victim.getLocation();
        Location pLoc = player.getLocation();

        player.getWorld().playSound(vLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.5f);
        player.getWorld().playSound(pLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.8f);

        // Pull victim violently downward and leap player towards them
        victim.setVelocity(new Vector(0, -3.2, 0));
        Vector leap = vLoc.toVector().subtract(pLoc.toVector()).normalize().multiply(1.2).setY(1.0);
        player.setVelocity(leap);

        // Constricting chain spiral on victim
        for (int i = 0; i < 20; i++) {
            double angle = i * 0.6;
            Location cPt = vLoc.clone().add(Math.cos(angle) * 0.8, -i * 0.12, Math.sin(angle) * 0.8);
            victim.getWorld().spawnParticle(Particle.DUST, cPt, 2, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.6f));
        }

        new BukkitRunnable() {
            int wait = 0;
            @Override
            public void run() {
                wait++;
                boolean landed = victim.isOnGround() || wait >= 12 || !victim.isValid();
                if (landed) {
                    cancel();
                    Location impact = victim.getLocation();

                    // 1. Crater shockwave rings (3 expanding tiers)
                    VFXUtil.drawExpandingCraterShockwave(impact, 4.5, Color.fromRGB(180, 0, 0), Color.fromRGB(60, 0, 0), true);
                    impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
                    impact.getWorld().spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.5, 0), 40, 1.0, 0.5, 1.0, Material.REDSTONE_BLOCK.createBlockData());
                    impact.getWorld().spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.5, 0), 25, 0.8, 0.5, 0.8, Material.NETHERITE_BLOCK.createBlockData());
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                    impact.getWorld().playSound(impact, Sound.ITEM_MACE_SMASH_GROUND, 1.8f, 0.5f);
                    impact.getWorld().playSound(impact, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 1.2f);

                    // 2. Direct slam damage
                    dealTrueDamage(victim, slamDamage, player);

                    // 3. Shockwave AoE damage to nearby enemies
                    for (LivingEntity nearby : impact.getWorld().getNearbyLivingEntities(impact, 5.0)) {
                        if (nearby.equals(player) || nearby.equals(victim)) continue;
                        dealTrueDamage(nearby, 4.0, player);
                        Vector kb = nearby.getLocation().toVector().subtract(impact.toVector()).normalize().setY(0.4).multiply(1.2);
                        nearby.setVelocity(kb);
                    }

                    // 4. Instantly self reset cooldown!
                    plugin.getCooldownManager().resetCooldown(player, "gem_sin_rusty_blood");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.8f);
                    player.sendTitle("§4§lAERIAL CRATER SLAM", isCombo ? "§c§l6 TRUE HEARTS • COMBO DETONATION" : "§c4 TRUE HEARTS • COOLDOWN RESET", 5, 25, 10);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void executeRustyBloodDrillCyclone(Player player, LivingEntity victim) {
        clearRustyBloodTether(player);
        Vector dir = player.getLocation().getDirection().setY(0.12).normalize();
        player.setVelocity(dir.clone().multiply(2.2));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);

        Set<UUID> hitEnemies = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                if (t > 10 || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location pLoc = player.getLocation().add(0, 1.0, 0);
                // Drill helix particles around player
                for (int h = 0; h < 2; h++) {
                    double angle = (t * 0.8) + (h * Math.PI);
                    Location pPt = pLoc.clone().add(Math.cos(angle) * 0.8, Math.sin(angle) * 0.8, 0);
                    player.getWorld().spawnParticle(Particle.DUST, pPt, 3, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.8f));
                    player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, pPt, 2, 0.1, 0.1, 0.1, 0.05);
                }
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0.2, 0.2, 0.2, 0);

                // Pierce through enemies
                for (LivingEntity target : player.getWorld().getNearbyLivingEntities(pLoc, 2.4)) {
                    if (target.equals(player) || hitEnemies.contains(target.getUniqueId())) continue;
                    hitEnemies.add(target.getUniqueId());

                    // Breaking shields
                    if (target instanceof Player targetPlayer && targetPlayer.isBlocking()) {
                        targetPlayer.setCooldown(Material.SHIELD, 100);
                        targetPlayer.getWorld().playSound(targetPlayer.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);
                        targetPlayer.sendMessage(miniMessage.deserialize("<red>✦ [SHIELD CRUSH] Your shield was shattered by Blood Drill! ✦</red>"));
                    }

                    // Deal 3 true hearts (6.0 true damage)
                    dealTrueDamage(target, 6.0, player);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);

                    // Cooldown reduction for each enemy pierced
                    double rem = plugin.getCooldownManager().getRemainingCooldownSeconds(player, "gem_sin_rusty_blood");
                    if (rem > 0) {
                        plugin.getCooldownManager().setCooldown(player, "gem_sin_rusty_blood", Math.max(0, (long) (rem - 6.0)));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void activateVeinsOfFlagitious(Player player) {
        UUID uuid = player.getUniqueId();
        flagitiousCasters.add(uuid);

        // Invisibility & equipment hide
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 85, 0, false, false, false));
        broadcastEquipmentMask(player, true);

        Location coreLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        Set<UUID> caughtVictims = new HashSet<>();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.5f);
        player.sendTitle("§4§lVEINS OF FLAGITIOUS", "§cSteer the Crimson Core • Accelerating (8m/s)", 5, 20, 10);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (tick > 80 || !player.isOnline()) { // 4.0 seconds (80 ticks)
                    cancel();
                    finishVeinsOfFlagitious(player, caughtVictims);
                    return;
                }

                // Acceleration from 0.10 to 0.40 blocks/tick (8 blocks/sec)
                double speed = 0.10 + (tick * (0.30 / 80.0));
                Vector dir = player.getEyeLocation().getDirection().normalize();
                coreLoc.add(dir.clone().multiply(speed));

                // Gently glide player along with the core
                player.setVelocity(dir.clone().multiply(speed * 0.75));

                // Dark red core center
                player.getWorld().spawnParticle(Particle.DUST, coreLoc, 6, 0.12, 0.12, 0.12, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 2.2f));
                player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, coreLoc, 4, 0.2, 0.2, 0.2, 0.05);
                player.getWorld().spawnParticle(Particle.PORTAL, coreLoc, 3, 0.1, 0.1, 0.1, 0.2);

                // 2 Orbiting white orbs
                double theta = tick * 0.35;
                for (int o = 0; o < 2; o++) {
                    double oAngle = theta + (o * Math.PI);
                    Location orbLoc = coreLoc.clone().add(Math.cos(oAngle) * 0.85, Math.sin(oAngle * 2.0) * 0.25, Math.sin(oAngle) * 0.85);
                    player.getWorld().spawnParticle(Particle.END_ROD, orbLoc, 1, 0, 0, 0, 0.01);
                    player.getWorld().spawnParticle(Particle.DUST, orbLoc, 2, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.5f));
                }

                // Trailing void/crimson smoke behind core
                Vector back = dir.clone().multiply(-0.4);
                Location trailLoc = coreLoc.clone().add(back);
                player.getWorld().spawnParticle(Particle.SQUID_INK, trailLoc, 2, 0.05, 0.05, 0.05, 0.02);
                player.getWorld().spawnParticle(Particle.DUST, trailLoc, 3, 0.1, 0.1, 0.1, new Particle.DustOptions(Color.fromRGB(60, 0, 0), 1.4f));

                if (tick % 10 == 0) {
                    player.getWorld().playSound(coreLoc, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.8f);
                }

                // Check for caught entities
                for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(coreLoc, 2.5)) {
                    if (entity.equals(player) || caughtVictims.contains(entity.getUniqueId())) continue;
                    caughtVictims.add(entity.getUniqueId());
                    flagitiousMarkedEntities.put(entity.getUniqueId(), System.currentTimeMillis() + 10000L);

                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.9f);
                    entity.getWorld().spawnParticle(Particle.CRIMSON_SPORE, entity.getLocation().add(0, 1.0, 0), 20, 0.4, 0.4, 0.4, 0.1);
                    player.sendMessage(miniMessage.deserialize("<dark_red><bold>✦ [VEINS OF FLAGITIOUS]</bold></dark_red> <red>Ensnared </red><white>" + entity.getName() + "</white><red>! Marked for Reverse Lightning launch!</red>"));
                    if (entity instanceof Player pVictim) {
                        pVictim.sendMessage(miniMessage.deserialize("<dark_red><bold>☠ [FLAGITIOUS MARK]</bold></dark_red> <red>You have been caught by the void particle! Retaliation draws near...</red>"));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishVeinsOfFlagitious(Player player, Set<UUID> caughtVictims) {
        flagitiousCasters.remove(player.getUniqueId());
        broadcastEquipmentMask(player, false);

        if (!player.isOnline()) return;

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.4f, 1.2f);

        for (UUID victimId : caughtVictims) {
            Entity ent = Bukkit.getEntity(victimId);
            if (!(ent instanceof LivingEntity victim) || !victim.isValid() || victim.isDead()) continue;

            Location vLoc = victim.getLocation();

            // Reverse red particle lightning bolt shooting upward from ground to Y+50!
            for (int y = 0; y <= 50; y++) {
                Location boltPt = vLoc.clone().add(0, y, 0);
                victim.getWorld().spawnParticle(Particle.DUST, boltPt, 3, 0.15, 0.15, 0.15, new Particle.DustOptions(Color.fromRGB(255, 20, 20), 2.2f));
                if (y % 2 == 0) {
                    victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, boltPt, 1, 0.1, 0.1, 0.1, 0.02);
                }
            }
            victim.getWorld().playSound(vLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f, 1.5f);
            victim.getWorld().playSound(vLoc, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.7f);

            // Explode as shockwave 1 dmg
            dealTrueDamage(victim, 1.0, player);
            victim.getWorld().spawnParticle(Particle.EXPLOSION, vLoc.clone().add(0, 1.0, 0), 2, 0.3, 0.3, 0.3, 0);

            // Flung upward 40 blocks (Y velocity ~2.65)
            victim.setVelocity(new Vector(0, 2.65, 0));

            // Mark as airborne combo target for 6 seconds
            flagitiousAirborneComboTargets.put(victimId, System.currentTimeMillis() + 6000L);
        }

        if (!caughtVictims.isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<gold><bold>✦ [AIRBORNE COMBO ACTIVE]</bold></gold> <yellow>Victims flung 40m into the sky! Throw </yellow><red><bold>Rusty Blood</bold></red><yellow> now for 100% lock-on & 6 TRUE HEARTS slam!</yellow>"));
            player.sendTitle("§e§lCOMBO WINDOW ACTIVE", "§6Throw Rusty Blood • 100% Lock-on & 6 True Hearts", 5, 25, 10);
        }
    }

    private void broadcastEquipmentMask(Player player, boolean hide) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            if (pm == null) return;
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, player.getEntityId());
            List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> slots = new ArrayList<>();
            ItemStack air = new ItemStack(Material.AIR);
            for (EnumWrappers.ItemSlot slot : EnumWrappers.ItemSlot.values()) {
                ItemStack item;
                if (hide) {
                    item = air;
                } else {
                    item = switch (slot) {
                        case MAINHAND -> player.getInventory().getItemInMainHand();
                        case OFFHAND -> player.getInventory().getItemInOffHand();
                        case HEAD -> player.getInventory().getHelmet();
                        case CHEST -> player.getInventory().getChestplate();
                        case LEGS -> player.getInventory().getLeggings();
                        case FEET -> player.getInventory().getBoots();
                        default -> air;
                    };
                    if (item == null) item = air;
                }
                slots.add(new com.comphenix.protocol.wrappers.Pair<>(slot, item));
            }
            packet.getSlotStackPairLists().write(0, slots);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    pm.sendServerPacket(viewer, packet);
                }
            }
        } catch (Throwable ignored) {}
    }

    public void triggerBloodfeastLowHealth(Player player) {
        UUID uuid = player.getUniqueId();
        Long nextAllowed = bloodfeastResetCooldowns.get(uuid);
        long now = System.currentTimeMillis();
        if (nextAllowed != null && now < nextAllowed) return;

        bloodfeastResetCooldowns.put(uuid, now + 45000L); // 45s internal CD

        // Instant Cooldown Reset!
        plugin.getCooldownManager().resetAllCooldowns(player);

        // 2x Clocking Pacing for 15 seconds!
        plugin.getCooldownManager().setDoublePacing(uuid, 15000L);

        // Apocalyptic effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.8f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.9f, 0.6f);

        player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, player.getLocation().add(0, 1.2, 0), 40, 0.5, 0.8, 0.5, 0.15);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1.0, 0), 10, 0.3, 0.4, 0.3, 0.1);

        player.sendTitle("§4§lBLOODFEAST", "§cCooldowns Reset • 2x Clocking Activated", 5, 30, 10);
        player.sendMessage(miniMessage.deserialize("<dark_red><bold>☠ [BLOODFEAST OVERDRIVE] ☠</bold></dark_red> <red>Health dropped below 4 HP! All cooldowns instantly reset & ticking at 2x pacing for 15s!</red>"));
    }

    private void dealTrueDamage(LivingEntity target, double damage, Player source) {
        if (target == null || target.isDead()) return;
        double newHealth = Math.max(0.0, target.getHealth() - damage);
        if (newHealth <= 0.0) {
            target.damage(1000.0, source);
        } else {
            target.setHealth(newHealth);
            target.playHurtAnimation(0.0f);
            target.damage(0.001, source);
        }
    }
}
