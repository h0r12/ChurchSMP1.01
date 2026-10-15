package com.churchsmp.cooldown;

import com.churchsmp.ChurchSMP;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final ChurchSMP plugin;

    // UUID -> (AbilityKey -> Expiration Timestamp in millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    
    // UUID -> (AbilityKey -> Active Expiration Timestamp in millis)
    private final Map<UUID, Map<String, Long>> activeAbilities = new ConcurrentHashMap<>();

    // UUID -> Expiration Timestamp in millis for 2x pacing mode
    private final Map<UUID, Long> doublePacingEndTimes = new ConcurrentHashMap<>();

    public CooldownManager(ChurchSMP plugin) {
        this.plugin = plugin;
        startDoublePacingTask();
    }

    private void startDoublePacingTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Long> entry : doublePacingEndTimes.entrySet()) {
                    if (now > entry.getValue()) {
                        doublePacingEndTimes.remove(entry.getKey());
                        continue;
                    }
                    UUID uuid = entry.getKey();
                    Map<String, Long> userCds = cooldowns.get(uuid);
                    if (userCds != null && !userCds.isEmpty()) {
                        for (Map.Entry<String, Long> cdEntry : userCds.entrySet()) {
                            long exp = cdEntry.getValue();
                            if (exp > now) {
                                cdEntry.setValue(exp - 500L);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public void setDoublePacing(UUID uuid, long durationMillis) {
        doublePacingEndTimes.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    public boolean hasDoublePacing(UUID uuid) {
        Long expireAt = doublePacingEndTimes.get(uuid);
        if (expireAt == null) return false;
        if (System.currentTimeMillis() > expireAt) {
            doublePacingEndTimes.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Start a cooldown for a specific player and ability ID.
     */
    public void setCooldown(Player player, String abilityKey, long seconds) {
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(abilityKey, expireAt);
    }

    /**
     * Start an active ability duration for a specific player and ability ID.
     */
    public void setActiveDuration(Player player, String abilityKey, long seconds) {
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);
        activeAbilities.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(abilityKey, expireAt);
    }

    /**
     * Convenience alias for setActiveDuration.
     */
    public void setActive(Player player, String abilityKey, long seconds) {
        setActiveDuration(player, abilityKey, seconds);
    }

    public void setActive(Player player, String abilityKey, int seconds) {
        setActiveDuration(player, abilityKey, seconds);
    }

    /**
     * Check if an ability is currently in its active state.
     */
    public boolean isActive(Player player, String abilityKey) {
        Map<String, Long> userActive = activeAbilities.get(player.getUniqueId());
        if (userActive == null) return false;
        Long expireAt = userActive.get(abilityKey);
        if (expireAt == null) return false;
        return System.currentTimeMillis() < expireAt;
    }

    /**
     * Remaining active time in seconds.
     */
    public double getActiveRemainingSeconds(Player player, String abilityKey) {
        Map<String, Long> userActive = activeAbilities.get(player.getUniqueId());
        if (userActive == null) return 0.0;
        Long expireAt = userActive.get(abilityKey);
        if (expireAt == null) return 0.0;
        long diff = expireAt - System.currentTimeMillis();
        return diff > 0 ? Math.round((diff / 1000.0) * 10.0) / 10.0 : 0.0;
    }

    /**
     * Check if an ability is on cooldown.
     */
    public boolean isOnCooldown(Player player, String abilityKey) {
        Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) return false;
        Long expireAt = userCooldowns.get(abilityKey);
        if (expireAt == null) return false;
        return System.currentTimeMillis() < expireAt;
    }

    /**
     * Get remaining cooldown in seconds with one decimal point.
     */
    public double getRemainingCooldownSeconds(Player player, String abilityKey) {
        Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns == null) return 0.0;
        Long expireAt = userCooldowns.get(abilityKey);
        if (expireAt == null) return 0.0;
        long diff = expireAt - System.currentTimeMillis();
        return diff > 0 ? Math.round((diff / 1000.0) * 10.0) / 10.0 : 0.0;
    }

    /**
     * Resets a specific cooldown for a player.
     */
    public void resetCooldown(Player player, String abilityKey) {
        Map<String, Long> userCooldowns = cooldowns.get(player.getUniqueId());
        if (userCooldowns != null) {
            userCooldowns.remove(abilityKey);
        }
    }

    public void removeCooldown(Player player, String abilityKey) {
        resetCooldown(player, abilityKey);
    }

    /**
     * Resets all cooldowns for a player.
     */
    public void resetAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
        activeAbilities.remove(player.getUniqueId());
    }
}
