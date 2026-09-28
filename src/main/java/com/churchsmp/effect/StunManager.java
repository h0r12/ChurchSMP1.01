package com.churchsmp.effect;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal True Stun Manager.
 * Locks the stunned entity 100% in place — even mid-air — by neutralizing velocity,
 * disabling gravity during the stun, and locking XYZ movement.
 * Completely eliminates the vanilla bug where jumping with Slowness launches players into the sky.
 */
public class StunManager implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Entity UUID -> Expiration timestamp in milliseconds
    private final Map<UUID, Long> stunnedUntil = new ConcurrentHashMap<>();
    // Entity UUID -> Frozen Location (XYZ locked, yaw/pitch allowed)
    private final Map<UUID, Location> lockedLocations = new ConcurrentHashMap<>();
    // Entity UUID -> Original gravity state
    private final Map<UUID, Boolean> originalGravity = new ConcurrentHashMap<>();

    public StunManager(ChurchSMP plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Applies a True Stun to the target for the specified duration in ticks.
     */
    public void applyTrueStun(LivingEntity target, int durationTicks, String reason) {
        if (target == null || !target.isValid() || target.isDead()) return;

        UUID id = target.getUniqueId();
        long expireTime = System.currentTimeMillis() + (durationTicks * 50L);
        stunnedUntil.put(id, expireTime);

        // Record locked location and freeze velocity
        Location currentLoc = target.getLocation().clone();
        lockedLocations.put(id, currentLoc);
        target.setVelocity(new Vector(0, 0, 0));

        // Save and disable gravity so mid-air targets stay suspended motionless
        if (!originalGravity.containsKey(id)) {
            originalGravity.put(id, target.hasGravity());
        }
        target.setGravity(false);

        // Vanilla slowness 255 + Jump boost 128 (completely suppresses vanilla jump impulses)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 255, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks, 128, false, false, false));

        // Sound and particle indicator
        target.getWorld().playSound(currentLoc, Sound.BLOCK_CHAIN_PLACE, 1.2f, 1.8f);
        target.getWorld().playSound(currentLoc, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.9f);
        target.getWorld().spawnParticle(Particle.CRIT, currentLoc.clone().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3, 0.05);

        if (target instanceof Player player) {
            String label = (reason != null && !reason.isEmpty()) ? reason : "STUNNED";
            player.sendActionBar(miniMessage.deserialize("<gold>✦ <yellow><bold>" + TextUtil.toSmallCaps(label) + "</bold></yellow> <gray>(" + (durationTicks / 20.0) + "s)</gray> ✦</gold>"));
        }

        // Schedule unfreeze
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isStunned(target)) {
                // If not extended by another stun
                if (System.currentTimeMillis() >= stunnedUntil.getOrDefault(id, 0L)) {
                    removeStun(target);
                }
            }
        }, durationTicks + 1L);
    }

    /**
     * Checks if the entity is currently stunned.
     */
    public boolean isStunned(LivingEntity entity) {
        if (entity == null) return false;
        Long expire = stunnedUntil.get(entity.getUniqueId());
        return expire != null && System.currentTimeMillis() < expire;
    }

    /**
     * Removes the stun immediately and restores gravity.
     */
    public void removeStun(LivingEntity target) {
        if (target == null) return;
        UUID id = target.getUniqueId();
        stunnedUntil.remove(id);
        lockedLocations.remove(id);

        Boolean prevGrav = originalGravity.remove(id);
        if (prevGrav != null && target.isValid()) {
            target.setGravity(prevGrav);
        }

        if (target.isValid()) {
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.removePotionEffect(PotionEffectType.JUMP_BOOST);
            target.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isStunned(player)) return;

        Location locked = lockedLocations.get(player.getUniqueId());
        if (locked == null) {
            locked = player.getLocation().clone();
            lockedLocations.put(player.getUniqueId(), locked);
        }

        // Keep position identical to locked location, but allow looking around
        Location to = event.getTo();
        Location fixed = locked.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());

        event.setTo(fixed);
        player.setVelocity(new Vector(0, 0, 0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isStunned(entity)) return;

        // Cancel knockback impulses while stunned
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isStunned(entity) && entity.isValid()) {
                entity.setVelocity(new Vector(0, 0, 0));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (isStunned(player)) {
            // Update locked location if teleported by plugin
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                lockedLocations.put(player.getUniqueId(), event.getTo().clone());
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (stunnedUntil.isEmpty()) return;
            long now = System.currentTimeMillis();
            stunnedUntil.entrySet().removeIf(entry -> {
                if (now >= entry.getValue()) {
                    UUID id = entry.getKey();
                    lockedLocations.remove(id);
                    Boolean prev = originalGravity.remove(id);
                    LivingEntity entity = (LivingEntity) Bukkit.getEntity(id);
                    if (entity != null && entity.isValid()) {
                        if (prev != null) entity.setGravity(prev);
                        entity.removePotionEffect(PotionEffectType.SLOWNESS);
                        entity.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    }
                    return true;
                }
                return false;
            });
        }, 10L, 10L);
    }
}
