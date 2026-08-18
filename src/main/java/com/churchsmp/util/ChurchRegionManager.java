package com.churchsmp.util;

import com.churchsmp.ChurchSMP;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Simple axis-aligned cuboid regions marking "consecrated ground" — used by
 * DeedListener (desecration / building-on-holy-ground deeds) and
 * EffectListener (holy-ground slowness / burn for Wicked & Fallen players).
 *
 * Not a replacement for WorldGuard — intended as a self-contained default so
 * the plugin works out of the box. Swap in a WorldGuard flag check here if
 * the server already uses it.
 */
public class ChurchRegionManager {

    public record Region(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public boolean contains(Location loc) {
            if (!loc.getWorld().getName().equals(world)) return false;
            double x = loc.getX(), y = loc.getY(), z = loc.getZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    private final ChurchSMP plugin;
    private final File file;
    private final List<Region> regions = new ArrayList<>();

    public ChurchRegionManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
        load();
    }

    public void addRegion(Location corner1, Location corner2) {
        regions.add(new Region(corner1.getWorld().getName(),
                corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ(),
                corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ()));
        save();
    }

    public boolean isHolyGround(Location loc) {
        for (Region r : regions) {
            if (r.contains(loc)) return true;
        }
        return false;
    }

    public List<Region> getRegions() {
        return regions;
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> list = yaml.getList("regions");
        if (list == null) return;
        for (Object o : list) {
            if (o instanceof java.util.Map<?, ?> m) {
                regions.add(new Region(
                        (String) m.get("world"),
                        (int) m.get("x1"), (int) m.get("y1"), (int) m.get("z1"),
                        (int) m.get("x2"), (int) m.get("y2"), (int) m.get("z2")));
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        for (Region r : regions) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("world", r.world());
            m.put("x1", r.x1()); m.put("y1", r.y1()); m.put("z1", r.z1());
            m.put("x2", r.x2()); m.put("y2", r.y2()); m.put("z2", r.z2());
            list.add(m);
        }
        yaml.set("regions", list);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save church regions", e);
        }
    }
}
