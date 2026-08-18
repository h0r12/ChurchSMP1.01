package com.churchsmp.weapons;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks players temporarily silenced by "Wisdom's Verdict" — while nullified,
 * no weapon ability (good or evil) may be activated, and passive alignment
 * effects are suppressed by EffectListener.
 */
public class NullifiedZoneManager {

    private final Map<UUID, Long> nullifiedUntil = new HashMap<>();

    public void nullify(Player player, long durationTicks) {
        long millis = durationTicks * 50L;
        nullifiedUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public boolean isNullified(Player player) {
        Long until = nullifiedUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }
}
