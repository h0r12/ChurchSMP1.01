package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;

public class ParticleAlias {

    public enum Type {
        PARTICLE,
        BLOCK_DISPLAY,
        ITEM_DISPLAY
    }

    private final Type type;
    private final Particle particle;
    private final Color color;
    private final float size;
    private final Material displayMaterial;
    private final String rawIdentifier;

    public ParticleAlias(Particle particle, Color color, float size, String rawIdentifier) {
        this.type = Type.PARTICLE;
        this.particle = particle;
        this.color = color;
        this.size = size;
        this.displayMaterial = null;
        this.rawIdentifier = rawIdentifier;
    }

    public ParticleAlias(Type type, Material displayMaterial, String rawIdentifier) {
        this.type = type;
        this.particle = null;
        this.color = null;
        this.size = 1.0f;
        this.displayMaterial = displayMaterial;
        this.rawIdentifier = rawIdentifier;
    }

    public Type getType() {
        return type;
    }

    public Particle getParticle() {
        return particle;
    }

    public Color getColor() {
        return color;
    }

    public float getSize() {
        return size;
    }

    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    public String getRawIdentifier() {
        return rawIdentifier;
    }

    /**
     * Parses a string alias into a ParticleAlias instance.
     */
    public static ParticleAlias fromString(String input) {
        if (input == null || input.isBlank()) {
            return new ParticleAlias(Particle.DUST, Color.fromRGB(216, 180, 254), 1.5f, "dustzen");
        }

        String raw = input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Display Entities
        if (lower.equals("redstoneblockdisplay")) {
            return new ParticleAlias(Type.BLOCK_DISPLAY, Material.REDSTONE_BLOCK, "redstoneblockdisplay");
        }
        if (lower.startsWith("blockdisplay:")) {
            String matName = lower.substring("blockdisplay:".length()).toUpperCase(Locale.ROOT);
            Material mat = Material.matchMaterial(matName);
            if (mat == null || !mat.isBlock()) mat = Material.REDSTONE_BLOCK;
            return new ParticleAlias(Type.BLOCK_DISPLAY, mat, "blockdisplay:" + mat.name().toLowerCase(Locale.ROOT));
        }
        if (lower.startsWith("itemdisplay:")) {
            String matName = lower.substring("itemdisplay:".length()).toUpperCase(Locale.ROOT);
            Material mat = Material.matchMaterial(matName);
            if (mat == null || !mat.isItem()) mat = Material.AMETHYST_SHARD;
            return new ParticleAlias(Type.ITEM_DISPLAY, mat, "itemdisplay:" + mat.name().toLowerCase(Locale.ROOT));
        }

        // 2. Color Dust Aliases
        if (lower.startsWith("dustred")) {
            float s = extractTrailingFloat(lower, "dustred", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(255, 30, 30), s, "dustred");
        }
        if (lower.startsWith("dustwhite")) {
            float s = extractTrailingFloat(lower, "dustwhite", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(255, 255, 255), s, "dustwhite");
        }
        if (lower.startsWith("dustzen") || lower.startsWith("dustpurple") || lower.startsWith("dustlilac")) {
            float s = extractTrailingFloat(lower, lower.startsWith("dustzen") ? "dustzen" : (lower.startsWith("dustpurple") ? "dustpurple" : "dustlilac"), 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(216, 180, 254), s, "dustzen");
        }
        if (lower.startsWith("dustgold") || lower.startsWith("dustyellow")) {
            float s = extractTrailingFloat(lower, lower.startsWith("dustgold") ? "dustgold" : "dustyellow", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(255, 215, 0), s, "dustgold");
        }
        if (lower.startsWith("dustblack")) {
            float s = extractTrailingFloat(lower, "dustblack", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(20, 20, 20), s, "dustblack");
        }
        if (lower.startsWith("dustcyan")) {
            float s = extractTrailingFloat(lower, "dustcyan", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(0, 230, 255), s, "dustcyan");
        }
        if (lower.startsWith("dustgreen") || lower.startsWith("dustlime")) {
            float s = extractTrailingFloat(lower, lower.startsWith("dustgreen") ? "dustgreen" : "dustlime", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(50, 255, 50), s, "dustgreen");
        }
        if (lower.startsWith("dustorange")) {
            float s = extractTrailingFloat(lower, "dustorange", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(255, 140, 0), s, "dustorange");
        }
        if (lower.startsWith("dustpink")) {
            float s = extractTrailingFloat(lower, "dustpink", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(255, 105, 180), s, "dustpink");
        }
        if (lower.startsWith("dustblue")) {
            float s = extractTrailingFloat(lower, "dustblue", 1.5f);
            return new ParticleAlias(Particle.DUST, Color.fromRGB(30, 144, 255), s, "dustblue");
        }

        // Custom Hex Dust: dust:#HEX:size or dust:#HEX
        if (lower.startsWith("dust:#") || lower.startsWith("dust#")) {
            try {
                String hexPart = raw.substring(raw.indexOf('#') + 1);
                float s = 1.5f;
                if (hexPart.contains(":") || hexPart.contains(",")) {
                    String[] parts = hexPart.split("[: ,]");
                    hexPart = parts[0];
                    if (parts.length > 1) {
                        s = Float.parseFloat(parts[1]);
                    }
                }
                int rgb = Integer.parseInt(hexPart, 16);
                return new ParticleAlias(Particle.DUST, Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF), s, "dust:#" + hexPart);
            } catch (Exception ignored) {
            }
        }

        // 3. Combat & Aesthetic Particle Aliases
        if (lower.equals("fireworkexplosion") || lower.equals("firework")) {
            return new ParticleAlias(Particle.FIREWORK, null, 1.0f, "fireworkexplosion");
        }
        if (lower.equals("sweep") || lower.equals("sweepattack") || lower.equals("sweep_attack")) {
            return new ParticleAlias(Particle.SWEEP_ATTACK, null, 1.0f, "sweep");
        }
        if (lower.equals("flash")) {
            return new ParticleAlias(Particle.FLASH, Color.WHITE, 1.0f, "flash");
        }
        if (lower.equals("sonicboom") || lower.equals("sonic_boom")) {
            return new ParticleAlias(Particle.SONIC_BOOM, null, 1.0f, "sonicboom");
        }
        if (lower.equals("endrod") || lower.equals("end_rod")) {
            return new ParticleAlias(Particle.END_ROD, null, 1.0f, "endrod");
        }
        if (lower.equals("soulflame") || lower.equals("soul_fire_flame")) {
            return new ParticleAlias(Particle.SOUL_FIRE_FLAME, null, 1.0f, "soulflame");
        }
        if (lower.equals("flame")) {
            return new ParticleAlias(Particle.FLAME, null, 1.0f, "flame");
        }
        if (lower.equals("gust") || lower.equals("windcharge") || lower.equals("wind_charge")) {
            return new ParticleAlias(Particle.GUST, null, 1.0f, "gust");
        }
        if (lower.equals("electric") || lower.equals("electric_spark") || lower.equals("spark")) {
            return new ParticleAlias(Particle.ELECTRIC_SPARK, null, 1.0f, "electric");
        }
        if (lower.equals("crit")) {
            return new ParticleAlias(Particle.CRIT, null, 1.0f, "crit");
        }
        if (lower.equals("magiccrit") || lower.equals("enchanted_hit")) {
            return new ParticleAlias(Particle.ENCHANTED_HIT, null, 1.0f, "magiccrit");
        }
        if (lower.equals("portal")) {
            return new ParticleAlias(Particle.PORTAL, null, 1.0f, "portal");
        }
        if (lower.equals("witch")) {
            return new ParticleAlias(Particle.WITCH, null, 1.0f, "witch");
        }
        if (lower.equals("enchant")) {
            return new ParticleAlias(Particle.ENCHANT, null, 1.0f, "enchant");
        }
        if (lower.equals("dragonbreath") || lower.equals("dragon_breath")) {
            return new ParticleAlias(Particle.DRAGON_BREATH, null, 1.0f, "dragonbreath");
        }
        if (lower.equals("trialspawner") || lower.equals("trial_spawner_detection")) {
            return new ParticleAlias(Particle.TRIAL_SPAWNER_DETECTION, null, 1.0f, "trialspawner");
        }

        // 4. Fallback to Bukkit Particle enum
        try {
            Particle p = Particle.valueOf(lower.toUpperCase(Locale.ROOT));
            if (p == Particle.DUST) {
                return new ParticleAlias(Particle.DUST, Color.fromRGB(216, 180, 254), 1.5f, "dustzen");
            }
            if (p == Particle.FLASH) {
                return new ParticleAlias(Particle.FLASH, Color.WHITE, 1.0f, "flash");
            }
            return new ParticleAlias(p, null, 1.0f, lower);
        } catch (IllegalArgumentException ignored) {
        }

        // Default fallback
        return new ParticleAlias(Particle.DUST, Color.fromRGB(216, 180, 254), 1.5f, "dustzen");
    }

    private static float extractTrailingFloat(String input, String prefix, float def) {
        if (input.length() > prefix.length()) {
            String rem = input.substring(prefix.length()).replace(":", "").replace("_", "").trim();
            try {
                return Float.parseFloat(rem);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    /**
     * Spawns this particle or display entity at the specified world location.
     */
    public void spawn(Location loc, int count, double speed, double scaleMultiplier, ChurchSMP plugin) {
        if (loc == null || loc.getWorld() == null) return;

        if (type == Type.BLOCK_DISPLAY) {
            spawnTemporaryBlockDisplay(loc, scaleMultiplier, plugin);
            return;
        }

        if (type == Type.ITEM_DISPLAY) {
            spawnTemporaryItemDisplay(loc, scaleMultiplier, plugin);
            return;
        }

        if (particle == null) return;

        int finalCount = Math.max(1, count);
        float finalSize = (float) (this.size * scaleMultiplier);

        if (particle == Particle.DUST) {
            Color c = (this.color != null) ? this.color : Color.WHITE;
            loc.getWorld().spawnParticle(Particle.DUST, loc, finalCount, 0, 0, 0, speed, new Particle.DustOptions(c, finalSize));
        } else if (particle == Particle.FLASH) {
            Color c = (this.color != null) ? this.color : Color.WHITE;
            loc.getWorld().spawnParticle(Particle.FLASH, loc, finalCount, 0, 0, 0, speed, c);
        } else {
            loc.getWorld().spawnParticle(particle, loc, finalCount, 0, 0, 0, speed);
        }
    }

    private void spawnTemporaryBlockDisplay(Location loc, double scaleMultiplier, ChurchSMP plugin) {
        Material mat = (displayMaterial != null && displayMaterial.isBlock()) ? displayMaterial : Material.REDSTONE_BLOCK;
        float baseScale = (float) (0.35f * scaleMultiplier);

        BlockDisplay bd = (BlockDisplay) loc.getWorld().spawnEntity(loc.clone().subtract(baseScale / 2.0, baseScale / 2.0, baseScale / 2.0), EntityType.BLOCK_DISPLAY);
        bd.setBlock(mat.createBlockData());
        bd.setBillboard(Display.Billboard.CENTER);
        bd.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(baseScale, baseScale, baseScale),
                new AxisAngle4f(0, 0, 0, 1)
        ));
        bd.setInterpolationDuration(10);
        bd.setInterpolationDelay(0);

        // Auto-remove after 10 ticks (0.5s)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (bd.isValid()) {
                bd.remove();
            }
        }, 10L);
    }

    private void spawnTemporaryItemDisplay(Location loc, double scaleMultiplier, ChurchSMP plugin) {
        Material mat = (displayMaterial != null && displayMaterial.isItem()) ? displayMaterial : Material.AMETHYST_SHARD;
        float baseScale = (float) (0.45f * scaleMultiplier);

        ItemDisplay id = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        id.setItemStack(new ItemStack(mat));
        id.setBillboard(Display.Billboard.CENTER);
        id.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(baseScale, baseScale, baseScale),
                new AxisAngle4f(0, 0, 0, 1)
        ));
        id.setInterpolationDuration(10);
        id.setInterpolationDelay(0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (id.isValid()) {
                id.remove();
            }
        }, 10L);
    }

    @Override
    public String toString() {
        return rawIdentifier;
    }
}
