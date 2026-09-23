package com.churchsmp.alignment;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class AlignmentManager {

    private final ChurchSMP plugin;
    private final NamespacedKey alignmentKey;
    private final NamespacedKey alignmentScoreKey;

    public AlignmentManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignmentKey = new NamespacedKey(plugin, "alignment");
        this.alignmentScoreKey = new NamespacedKey(plugin, "alignment_score");
    }

    /**
     * Gets the player's numerical alignment score from -100 to +100.
     * Higher score (>0) = Good/Holy (longer & stronger potion effects, bonus EXP, Smite > Sharpness).
     * Lower score (<0) = Evil/Sin (unholy damage, wither, bleed).
     * Score = 0 = Nullified / Neutral.
     */
    public int getAlignmentScore(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Integer score = pdc.get(alignmentScoreKey, PersistentDataType.INTEGER);
        if (score != null) {
            return Math.max(-100, Math.min(100, score));
        }

        // Check legacy string alignment
        String stored = pdc.get(alignmentKey, PersistentDataType.STRING);
        if (stored != null) {
            if ("GOOD".equalsIgnoreCase(stored)) return 100;
            if ("EVIL".equalsIgnoreCase(stored)) return -100;
        }
        return 0;
    }

    /**
     * Sets the player's alignment score (-100 to +100).
     */
    public void setAlignmentScore(Player player, int score) {
        int clamped = Math.max(-100, Math.min(100, score));
        player.getPersistentDataContainer().set(alignmentScoreKey, PersistentDataType.INTEGER, clamped);

        Alignment align = getAlignment(player);
        player.getPersistentDataContainer().set(alignmentKey, PersistentDataType.STRING, align.name());

        String scoreStr = (clamped > 0 ? "+" + clamped : String.valueOf(clamped));
        Component msg = MiniMessage.miniMessage().deserialize(
                "<gold>✦ <white>" + TextUtil.toSmallCaps("Alignment Score") + ": </white><yellow><bold>" + scoreStr + "</bold></yellow> "
        ).append(align.getFormattedComponent());
        player.sendMessage(msg);
    }

    /**
     * Retrieves the player's current alignment enum derived from their score.
     */
    public Alignment getAlignment(Player player) {
        int score = getAlignmentScore(player);
        if (score > 0) return Alignment.GOOD;
        if (score < 0) return Alignment.EVIL;
        return Alignment.NULLIFIED;
    }

    /**
     * Sets the player's alignment and updates score to match (+100 for GOOD, -100 for EVIL, 0 for NULLIFIED).
     */
    public void setAlignment(Player player, Alignment alignment) {
        int targetScore = switch (alignment) {
            case GOOD -> 100;
            case EVIL -> -100;
            case NULLIFIED -> 0;
        };
        setAlignmentScore(player, targetScore);
    }

    /**
     * Checks whether a player meets the required alignment for an item.
     * GOOD weapons require score > 0.
     * EVIL weapons require score < 0.
     * NULLIFIED weapons can be wielded by anyone.
     */
    public boolean canWield(Player player, Alignment required) {
        if (required == Alignment.NULLIFIED) {
            return true;
        }
        int score = getAlignmentScore(player);
        if (required == Alignment.GOOD) {
            return score > 0;
        }
        if (required == Alignment.EVIL) {
            return score < 0;
        }
        return true;
    }

    /**
     * Bonus multiplier for EXP gain based on positive alignment.
     */
    public double getExpMultiplier(Player player) {
        int score = getAlignmentScore(player);
        if (score <= 0) return 1.0;
        return 1.0 + (score / 100.0) * 0.75; // Up to +75% bonus EXP at +100
    }

    /**
     * Bonus multiplier for positive potion effect durations.
     */
    public double getPotionDurationMultiplier(Player player) {
        int score = getAlignmentScore(player);
        if (score <= 0) return 1.0;
        return 1.0 + (score / 100.0) * 0.50; // Up to +50% longer potion duration at +100
    }

    /**
     * Bonus Smite damage scaling: Smite deals more damage than Sharpness at positive alignment.
     */
    public double getSmiteBonusDamage(Player player) {
        int score = getAlignmentScore(player);
        if (score <= 0) return 0.0;
        return (score / 100.0) * 6.0; // Up to 6.0 bonus damage (3 hearts)
    }
}
