package com.churchsmp.shrine;

import com.churchsmp.ChurchSMP;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Tracks which world block positions act as Altars, Offering boxes, and
 * Confession booths. Any block material can be designated — an admin simply
 * targets the block and runs `/churchadmin shrine add <type>`.
 */
public class ShrineManager {

    private final ChurchSMP plugin;
    private final File file;
    private final Map<ShrineType, Set<String>> shrines = new HashMap<>();

    public ShrineManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shrines.yml");
        for (ShrineType type : ShrineType.values()) {
            shrines.put(type, new HashSet<>());
        }
        load();
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void register(ShrineType type, Location loc) {
        shrines.get(type).add(key(loc));
        save();
    }

    public void unregister(ShrineType type, Location loc) {
        shrines.get(type).remove(key(loc));
        save();
    }

    /** Returns the shrine type at this location, or null if it isn't a registered shrine block. */
    public ShrineType getType(Location loc) {
        String k = key(loc);
        for (Map.Entry<ShrineType, Set<String>> entry : shrines.entrySet()) {
            if (entry.getValue().contains(k)) return entry.getKey();
        }
        return null;
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (ShrineType type : ShrineType.values()) {
            for (String entry : yaml.getStringList(type.name())) {
                shrines.get(type).add(entry);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ShrineType type : ShrineType.values()) {
            yaml.set(type.name(), new java.util.ArrayList<>(shrines.get(type)));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save shrines", e);
        }
    }
}
