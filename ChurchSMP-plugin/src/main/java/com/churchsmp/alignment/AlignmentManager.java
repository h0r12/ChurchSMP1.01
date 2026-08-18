package com.churchsmp.alignment;

import com.churchsmp.ChurchSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central manager for player alignment scores: loading/saving, tier lookup,
 * applying deed point changes, and the periodic decay-toward-zero task.
 */
public class AlignmentManager {

    private final ChurchSMP plugin;
    private final Map<UUID, PlayerAlignmentData> dataMap = new HashMap<>();
    private final File storageFile;
    private double minScore;
    private double maxScore;

    public AlignmentManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "alignment-data.yml");
        reloadThresholds();
        load();
    }

    public void reloadThresholds() {
        FileConfiguration cfg = plugin.getConfig();
        this.minScore = cfg.getDouble("alignment.min", -100);
        this.maxScore = cfg.getDouble("alignment.max", 100);
    }

    public PlayerAlignmentData getData(Player player) {
        return dataMap.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerAlignmentData(id, 0));
    }

    public double getScore(Player player) {
        return getData(player).getScore();
    }

    /** Applies a deed's point delta and returns the resulting tier. */
    public AlignmentTier applyDeed(Player player, double delta) {
        PlayerAlignmentData data = getData(player);
        data.addPoints(delta, minScore, maxScore);
        return getTier(data.getScore());
    }

    public AlignmentTier getTier(Player player) {
        return getTier(getScore(player));
    }

    public AlignmentTier getTier(double score) {
        FileConfiguration cfg = plugin.getConfig();
        double saint = cfg.getDouble("alignment.tiers.saint", 61);
        double righteous = cfg.getDouble("alignment.tiers.righteous", 21);
        double nullifiedLow = cfg.getDouble("alignment.tiers.nullified-low", -20);
        double wicked = cfg.getDouble("alignment.tiers.wicked", -60);

        if (score >= saint) return AlignmentTier.SAINT;
        if (score >= righteous) return AlignmentTier.RIGHTEOUS;
        if (score >= nullifiedLow) return AlignmentTier.NULLIFIED;
        if (score >= wicked) return AlignmentTier.WICKED;
        return AlignmentTier.FALLEN;
    }

    /** Ticks decay for every online player; call once per in-game day. */
    public void tickDailyDecay() {
        double decay = plugin.getConfig().getDouble("alignment.decay-per-day", 2);
        for (PlayerAlignmentData data : dataMap.values()) {
            if (data.getScore() > 0) {
                data.addPoints(-Math.min(decay, data.getScore()), minScore, maxScore);
            } else if (data.getScore() < 0) {
                data.addPoints(Math.min(decay, -data.getScore()), minScore, maxScore);
            }
            data.resetDailyCaps();
        }
        save();
    }

    public void load() {
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double score = yaml.getDouble(key + ".score", 0);
                PlayerAlignmentData data = new PlayerAlignmentData(uuid, score);
                data.addTithedToday(yaml.getInt(key + ".tithedToday", 0));
                dataMap.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerAlignmentData data : dataMap.values()) {
            String key = data.getUuid().toString();
            yaml.set(key + ".score", data.getScore());
            yaml.set(key + ".tithedToday", data.getTithedToday());
        }
        try {
            yaml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save alignment data", e);
        }
    }
}
