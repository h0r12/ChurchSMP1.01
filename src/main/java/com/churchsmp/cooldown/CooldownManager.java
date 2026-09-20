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

    public CooldownManager(ChurchSMP plugin) {
        this.plugin = plugin;
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
     * Resets all cooldowns for a player.
     */
    public void resetAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
        activeAbilities.remove(player.getUniqueId());
    }
}
