package com.churchsmp.particle;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ParticleStudioGuiManager implements Listener {

    private final ChurchSMP plugin;
    private final ParticleAnimationManager animationManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Inventory holders to identify menus
    public static class StudioHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class ShapePickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class MotionPickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class TweenPickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class ParticlePickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class EasingPickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
    public static class TemplatePickerHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    public ParticleStudioGuiManager(ChurchSMP plugin, ParticleAnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    private ItemStack createFiller(Material mat) {
        ItemStack filler = new ItemStack(mat);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private ItemStack createFiller() {
        return createFiller(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack createButton(Material mat, String name, List<String> loreLines, boolean glint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<!i>" + name));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(miniMessage.deserialize("<!i>" + line));
                }
                meta.lore(lore);
            }
            if (glint) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. MAIN PARTICLE STUDIO CHEST GUI (54 SLOTS) - CLEAN HUB & CONTENT DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────────
    public void openStudio(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>PARTICLE STUDIO</bold></gradient> <dark_gray>|</dark_gray> <gray>Frame <white>" + session.getCurrentFrame() + "</white></gray>");
        Inventory inv = Bukkit.createInventory(new StudioHolder(), 54, title);

        ItemStack border = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack darkFiller = createFiller(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, darkFiller);
        }
        // Dividers
        for (int i = 18; i <= 26; i++) inv.setItem(i, border);
        for (int i = 36; i <= 44; i++) inv.setItem(i, border);

        int curFrame = session.getCurrentFrame();
        AnimationFrame frameObj = session.getAnimation().getFrames().get(curFrame);
        int elemCount = (frameObj != null) ? frameObj.getElements().size() : 0;
        int maxFrames = Math.max(curFrame, session.getAnimation().getMaxFrameIndex()) + 1;

        int totalPoints = 0;
        List<Integer> keyframesWithParticles = new ArrayList<>();
        for (Map.Entry<Integer, AnimationFrame> entry : session.getAnimation().getFrames().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().getElements().isEmpty()) {
                totalPoints += entry.getValue().getElements().size();
                keyframesWithParticles.add(entry.getKey());
            }
        }
        Collections.sort(keyframesWithParticles);
        String keyframeSummary = keyframesWithParticles.isEmpty() ? "None (Empty)" : keyframesWithParticles.toString();

        boolean looping = session.isLooping();

        // ── ROW 0: TIMELINE & CONTENT OVERVIEW HERO (Slots 0 - 8) ───────────────
        inv.setItem(0, createButton(
                Material.ARROW,
                "<aqua>◀◀ -5 Frames</aqua>",
                List.of(
                        "<gray>Jump backward 5 frames along timeline.</gray>",
                        "<yellow>Click to Step -5</yellow>"
                ),
                false
        ));

        inv.setItem(1, createButton(
                Material.SPECTRAL_ARROW,
                "<aqua>◀ -1 Frame</aqua>",
                List.of(
                        "<gray>Step back one single frame.</gray>",
                        "<yellow>Click to Step -1</yellow>"
                ),
                false
        ));

        inv.setItem(2, createButton(
                Material.PAPER,
                "<white><bold>Jump to Frame 0</bold></white>",
                List.of(
                        "<gray>Return to the starting keyframe.</gray>",
                        "<yellow>Click to Jump</yellow>"
                ),
                curFrame == 0
        ));

        inv.setItem(3, border);

        inv.setItem(4, createButton(
                Material.NETHER_STAR,
                "<#d8b4fe><bold>" + session.getAnimation().getName() + "</bold></#d8b4fe> <gray>(Content Overview)</gray>",
                List.of(
                        "<gray>Timeline Position: <white>Frame " + curFrame + "</white> <gray>/ " + maxFrames + "</gray></gray>",
                        "<gray>Points in this Frame: <white>" + elemCount + " elements</white></gray>",
                        "<gray>Total Animation Points: <white>" + totalPoints + " elements</white></gray>",
                        "<gray>Active Keyframes: <white>" + keyframeSummary + "</white></gray>",
                        " ",
                        "<gray>Active Particle: <#d8b4fe>" + session.getActiveParticle().getRawIdentifier() + "</#d8b4fe></gray>",
                        "<gray>Active Shape: <aqua>" + session.getActivePreset().name() + "</aqua></gray>",
                        "<gray>3D Offset: <white>X=" + String.format(Locale.ROOT, "%+.2f", session.getActiveXOffset()) +
                                " Y=" + String.format(Locale.ROOT, "%+.2f", session.getActiveYOffset()) +
                                " Z=" + String.format(Locale.ROOT, "%+.2f", session.getActiveZOffset()) + "</white></gray>",
                        "<gray>Trajectory Vel: <white>X=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelX()) +
                                " Y=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelY()) +
                                " Z=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelZ()) + "/f</white></gray>",
                        "<gray>Rot / Spin: <white>P" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotX()) +
                                " Y" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotY()) +
                                " R" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotZ()) +
                                " (Spin Y: " + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinY()) + ")</white></gray>",
                        "<gray>Lifespan / Easing: <white>" + session.getActiveLifespan() + " frames (" + (session.isDecayShrink() ? "Shrink" : "Static") + ") | " + session.getActiveEasing().name() + "</white></gray>",
                        "<gray>World 3D Loop: <white>" + (looping ? "<green>ACTIVE LOOPING</green>" : "<yellow>IDLE</yellow>") + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+1 Frame</white></yellow>",
                        "<yellow>Right-Click: <white>-1 Frame</white></yellow>",
                        "<yellow>Shift-Click: <white>Jump to Frame 0</white></yellow>"
                ),
                true
        ));

        inv.setItem(5, border);

        inv.setItem(6, createButton(
                Material.EMERALD,
                "<green><bold>+ Add / Jump to End Frame</bold></green>",
                List.of(
                        "<gray>Jump directly to the next free frame</gray>",
                        "<gray>ready to stamp new elements.</gray>",
                        "<gray>Next free frame: <white>Frame " + maxFrames + "</white></gray>",
                        "<yellow>Click to Advance</yellow>"
                ),
                false
        ));

        inv.setItem(7, createButton(
                Material.SPECTRAL_ARROW,
                "<aqua>+1 Frame ▶</aqua>",
                List.of(
                        "<gray>Step forward one frame.</gray>",
                        "<yellow>Click to Step +1</yellow>"
                ),
                false
        ));

        inv.setItem(8, createButton(
                Material.ARROW,
                "<aqua>+5 Frames ▶▶</aqua>",
                List.of(
                        "<gray>Advance 5 frames along timeline.</gray>",
                        "<yellow>Click to Step +5</yellow>"
                ),
                false
        ));

        // ── ROW 1: QUICK PLAYBACK & INSTANT ACTIONS (Slots 9 - 17) ──────────────
        inv.setItem(10, createButton(
                Material.LIME_CONCRETE,
                "<green><bold>Play Preview Once ▶</bold></green>",
                List.of(
                        "<gray>Renders your animation timeline once</gray>",
                        "<gray>directly at your crosshair.</gray>",
                        " ",
                        "<yellow>Click to Play Preview</yellow>"
                ),
                false
        ));

        inv.setItem(11, createButton(
                looping ? Material.REDSTONE_BLOCK : Material.DAYLIGHT_DETECTOR,
                looping ? "<gold><bold>Stop 3D Loop Preview ⏹</bold></gold>" : "<aqua><bold>Start 3D Loop Preview 🔁</bold></aqua>",
                List.of(
                        "<gray>Continuously loops your animation in the world.</gray>",
                        "<gray>Current State: <white>" + (looping ? "ACTIVE LOOPING" : "IDLE") + "</white></gray>",
                        " ",
                        "<yellow>Click to Toggle 3D Loop</yellow>"
                ),
                looping
        ));

        inv.setItem(13, createButton(
                Material.GOLDEN_PICKAXE,
                "<#d8b4fe><bold>Stamp Active Preset Now</bold></#d8b4fe>",
                List.of(
                        "<gray>Stamps active shape (<white>" + session.getActivePreset().name() + "</white>)</gray>",
                        "<gray>into Frame " + curFrame + " at current 3D position.</gray>",
                        " ",
                        "<yellow>Click to Stamp Shape</yellow>"
                ),
                true
        ));

        inv.setItem(15, createButton(
                Material.LIGHT_BLUE_DYE,
                "<aqua><bold>Undo Action ↩</bold></aqua>",
                List.of(
                        "<gray>Reverts to previous snapshot.</gray>",
                        "<gray>(Wand shortcut: <white>Shift + F</white>)</gray>",
                        " ",
                        "<yellow>Click to Undo</yellow>"
                ),
                false
        ));

        inv.setItem(16, createButton(
                Material.LIME_DYE,
                "<green><bold>Redo Action ↪</bold></green>",
                List.of(
                        "<gray>Restores undone changes.</gray>",
                        "<gray>(Wand shortcut: <white>F</white>)</gray>",
                        " ",
                        "<yellow>Click to Redo</yellow>"
                ),
                false
        ));

        // ── ROW 3: THE 5 DEDICATED STUDIO PORTALS (Slots 27 - 35) ────────────────
        inv.setItem(28, createButton(
                Material.IRON_SWORD,
                "<#d8b4fe><bold>1. Math Shape Forge</bold></#d8b4fe>",
                List.of(
                        "<gray>Choose and stamp mathematical VFX curves:</gray>",
                        "<gray>Point, Star, Ring, Slash, Helix, Lissajous, Rose, Bolt.</gray>",
                        " ",
                        "<gray>Current Shape: <white>" + session.getActivePreset().name() + "</white></gray>",
                        "<yellow>Click to Open Shape Forge</yellow>"
                ),
                true
        ));

        boolean hasMotion = session.getActiveVelX() != 0 || session.getActiveVelY() != 0 || session.getActiveVelZ() != 0;
        boolean hasOffset = session.getActiveXOffset() != 0 || session.getActiveYOffset() != 0 || session.getActiveZOffset() != 0;
        inv.setItem(29, createButton(
                Material.MINECART,
                "<aqua><bold>2. 3D Position & Motion Tuner</bold></aqua>",
                List.of(
                        "<gray>Fine-tune 3D (X, Y, Z) coordinates, projectile</gray>",
                        "<gray>velocity trajectories, 3D rotation, and scale.</gray>",
                        " ",
                        "<gray>Active Offset: <white>(" + String.format(Locale.ROOT, "%+.2f, %+.2f, %+.2f", session.getActiveXOffset(), session.getActiveYOffset(), session.getActiveZOffset()) + ")</white></gray>",
                        "<gray>Active Velocity: <white>(" + String.format(Locale.ROOT, "%+.1f, %+.1f, %+.1f", session.getActiveVelX(), session.getActiveVelY(), session.getActiveVelZ()) + "/f)</white></gray>",
                        "<yellow>Click to Open Motion Studio</yellow>"
                ),
                hasMotion || hasOffset
        ));

        inv.setItem(31, createButton(
                Material.AMETHYST_SHARD,
                "<light_purple><bold>3. Particle VFX & Color Library</bold></light_purple>",
                List.of(
                        "<gray>Browse 30+ vibrant dust colors, combat sparks,</gray>",
                        "<gray>spell swirls, and 3D miniature block displays.</gray>",
                        " ",
                        "<gray>Active Particle: <white>" + session.getActiveParticle().getRawIdentifier() + "</white></gray>",
                        "<yellow>Click to Open VFX Library</yellow>"
                ),
                true
        ));

        inv.setItem(33, createButton(
                Material.HEART_OF_THE_SEA,
                "<gold><bold>4. Keyframe & Tweening Studio</bold></gold>",
                List.of(
                        "<gray>Smart Auto-Tween between keyframes, frame rotting/decay,</gray>",
                        "<gray>easing curves, pacing ticks, and real-time motion recording.</gray>",
                        " ",
                        "<gray>Decay Lifespan: <white>" + session.getActiveLifespan() + " frames</white></gray>",
                        "<gray>Easing: <white>" + session.getActiveEasing().name() + "</white></gray>",
                        "<yellow>Click to Open Keyframe Studio</yellow>"
                ),
                true
        ));

        inv.setItem(34, createButton(
                Material.KNOWLEDGE_BOOK,
                "<green><bold>5. 1-Click VFX Templates</bold></green>",
                List.of(
                        "<gray>Instantly load complete 3D combat animations:</gray>",
                        "<gray>Blade Slash, Shockwave, Vortex, Thrust, Slam, Cross, Orbit.</gray>",
                        " ",
                        "<gray>Available: <white>7 Complete VFX Presets</white></gray>",
                        "<yellow>Click to Browse Templates</yellow>"
                ),
                true
        ));

        // ── ROW 5: LIFECYCLE, STORAGE & UTILITY (Slots 45 - 53) ──────────────────
        inv.setItem(45, createButton(
                Material.LAVA_BUCKET,
                "<gold><bold>Clear Current Frame</bold></gold>",
                List.of(
                        "<gray>Erases all points in Frame " + curFrame + ".</gray>",
                        "<gray>(Fully protected by Undo / Shift + F)</gray>",
                        " ",
                        "<yellow>Click to Clear Frame</yellow>"
                ),
                false
        ));

        inv.setItem(47, createButton(
                Material.BARRIER,
                "<red><bold>Clear Entire Animation</bold></red>",
                List.of(
                        "<gray>Erases the entire animation timeline.</gray>",
                        "<gray>(Fully protected by Undo / Shift + F)</gray>",
                        " ",
                        "<red>Click to Reset Animation</red>"
                ),
                false
        ));

        inv.setItem(49, createButton(
                Material.CHEST,
                "<gold><bold>Save to animations.yml</bold></gold>",
                List.of(
                        "<gray>Saves active animation permanently to disk.</gray>",
                        "<gray>Name: <white>" + session.getAnimation().getName() + "</white></gray>",
                        " ",
                        "<yellow>Click to Save</yellow>"
                ),
                false
        ));

        inv.setItem(51, createButton(
                Material.REDSTONE_TORCH,
                "<yellow><bold>Reload animations.yml</bold></yellow>",
                List.of(
                        "<gray>Reloads all custom saved animations</gray>",
                        "<gray>from ChurchSMP/animations.yml.</gray>",
                        " ",
                        "<yellow>Click to Reload</yellow>"
                ),
                false
        ));

        inv.setItem(53, createButton(
                Material.IRON_DOOR,
                "<red><bold>Close Studio</bold></red>",
                List.of("<gray>Exit the Particle Studio GUI.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.3f);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. SHAPE & PRESET FORGE SUB-MENU (27 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openShapeMenu(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>SHAPE & PRESET FORGE</bold></gradient> <dark_gray>|</dark_gray> <gray>Select Shape</gray>");
        Inventory inv = Bukkit.createInventory(new ShapePickerHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        MathPreset curPreset = session.getActivePreset();

        addShapeItem(inv, 9, Material.STONE_BUTTON, MathPreset.POINT, "Point", "Single focal point at crosshair anchor.", curPreset);
        addShapeItem(inv, 10, Material.NETHER_STAR, MathPreset.STAR_4, "4-Point Star", "Parametric astroid hypocycloid star.", curPreset);
        addShapeItem(inv, 11, Material.FIREWORK_STAR, MathPreset.STAR_8, "8-Point Star", "Radiant eight-pointed celestial star.", curPreset);
        addShapeItem(inv, 12, Material.ECHO_SHARD, MathPreset.RING, "Ring / Circle", "Perfect planar circular halo.", curPreset);
        addShapeItem(inv, 13, Material.IRON_SWORD, MathPreset.SLASH, "Slash / Blade Arc", "Dynamic curved melee blade sweep.", curPreset);
        addShapeItem(inv, 14, Material.BREEZE_ROD, MathPreset.HELIX, "Helix / Vortex", "Rising 3D conical helical spiral.", curPreset);
        addShapeItem(inv, 15, Material.LEAD, MathPreset.LISSAJOUS, "Lissajous Knot", "Complex 3D harmonic knot curve.", curPreset);
        addShapeItem(inv, 16, Material.ALLIUM, MathPreset.ROSE, "Rose / Polar Petals", "Rhodonea polar mathematical petals.", curPreset);
        addShapeItem(inv, 17, Material.LIGHTNING_ROD, MathPreset.BOLT_VEINS, "Lightning Bolt Veins", "Fractal electric branching lightning arc.", curPreset);

        inv.setItem(22, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void addShapeItem(Inventory inv, int slot, Material mat, MathPreset preset, String name, String desc, MathPreset activePreset) {
        boolean selected = (preset == activePreset);
        inv.setItem(slot, createButton(
                mat,
                "<#d8b4fe><bold>" + name + "</bold></#d8b4fe>",
                List.of(
                        "<gray>" + desc + "</gray>",
                        " ",
                        selected ? "<green>✔ CURRENTLY SELECTED</green>" : "<yellow>Left-Click: Select</yellow>",
                        "<yellow>Right-Click / Shift: Select & Stamp Now</yellow>"
                ),
                selected
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. 3D POSITION & MOTION TUNER SUB-MENU (36 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openMotionMenu(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>3D POSITION & MOTION TUNER</bold></gradient>");
        Inventory inv = Bukkit.createInventory(new MotionPickerHolder(), 36, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // Center Status Card
        inv.setItem(4, createButton(
                Material.COMPASS,
                "<aqua><bold>3D Spatial Coordinates & Motion</bold></aqua>",
                List.of(
                        "<gray>Focal Offset: <white>X=" + String.format(Locale.ROOT, "%+.2f", session.getActiveXOffset()) +
                                " Y=" + String.format(Locale.ROOT, "%+.2f", session.getActiveYOffset()) +
                                " Z=" + String.format(Locale.ROOT, "%+.2f", session.getActiveZOffset()) + " (Base 2.0)</white></gray>",
                        "<gray>Trajectory Velocity: <white>X=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelX()) +
                                " Y=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelY()) +
                                " Z=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelZ()) + "/frame</white></gray>",
                        "<gray>3D Rotation: <white>Pitch=" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotX()) +
                                " Yaw=" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotY()) +
                                " Roll=" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotZ()) + "</white></gray>",
                        "<gray>Scale: <white>" + String.format(Locale.ROOT, "%.2fx", session.getActiveScale()) + "</white></gray>",
                        "<gray>Continuous Spin: <white>Yaw=" + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinY()) +
                                " Pitch=" + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinX()) + "</white></gray>"
                ),
                true
        ));

        // Row 1: Offsets & Scale (Slots 9-17)
        inv.setItem(10, createButton(
                Material.SLIME_BALL,
                "<green><bold>Scale Multiplier</bold></green>",
                List.of(
                        "<gray>Controls the geometric size of shapes.</gray>",
                        "<gray>Current: <white>" + String.format(Locale.ROOT, "%.2fx", session.getActiveScale()) + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+0.25x</white></yellow>",
                        "<yellow>Right-Click: <white>-0.25x</white></yellow>",
                        "<yellow>Shift-Click: <white>Reset to 1.0x</white></yellow>"
                ),
                session.getActiveScale() != 1.0
        ));

        inv.setItem(12, createButton(
                Material.FEATHER,
                "<white><bold>X-Offset (Left / Right)</bold></white>",
                List.of(
                        "<gray>Shifts particles horizontally relative to crosshair.</gray>",
                        "<gray>Current: <white>" + String.format(Locale.ROOT, "%+.2f blocks", session.getActiveXOffset()) + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+0.5 Right</white></yellow>",
                        "<yellow>Right-Click: <white>-0.5 Left</white></yellow>",
                        "<yellow>Shift-Click: <white>Reset to 0.0</white></yellow>"
                ),
                session.getActiveXOffset() != 0.0
        ));

        inv.setItem(13, createButton(
                Material.WIND_CHARGE,
                "<white><bold>Y-Offset (Height Up / Down)</bold></white>",
                List.of(
                        "<gray>Shifts particles vertically relative to eyes.</gray>",
                        "<gray>Current: <white>" + String.format(Locale.ROOT, "%+.2f blocks", session.getActiveYOffset()) + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+0.5 Up</white></yellow>",
                        "<yellow>Right-Click: <white>-0.5 Down</white></yellow>",
                        "<yellow>Shift-Click: <white>Reset to 0.0</white></yellow>"
                ),
                session.getActiveYOffset() != 0.0
        ));

        inv.setItem(14, createButton(
                Material.ENDER_EYE,
                "<aqua><bold>Z-Offset (Distance Forward / Back)</bold></aqua>",
                List.of(
                        "<gray>Shifts particles forward/backward in front of you.</gray>",
                        "<gray>Current: <white>" + String.format(Locale.ROOT, "%+.2f blocks (Base 2.0)", session.getActiveZOffset()) + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+0.5 Forward</white></yellow>",
                        "<yellow>Right-Click: <white>-0.5 Back</white></yellow>",
                        "<yellow>Shift-Click: <white>Reset to 0.0</white></yellow>"
                ),
                session.getActiveZOffset() != 0.0
        ));

        inv.setItem(16, createButton(
                Material.ARMOR_STAND,
                "<gold><bold>3D Rotation (Pitch / Yaw / Roll)</bold></gold>",
                List.of(
                        "<gray>Controls 3D angular orientation.</gray>",
                        "<gray>Pitch (X): <white>" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotX()) + "</white></gray>",
                        "<gray>Yaw (Y): <white>" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotY()) + "</white></gray>",
                        "<gray>Roll (Z): <white>" + String.format(Locale.ROOT, "%.0f°", session.getActiveRotZ()) + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+15° Pitch</white></yellow>",
                        "<yellow>Right-Click: <white>+15° Yaw</white></yellow>",
                        "<yellow>Shift-Click: <white>+15° Roll</white></yellow>"
                ),
                session.getActiveRotX() != 0 || session.getActiveRotY() != 0 || session.getActiveRotZ() != 0
        ));

        // Row 2: Motion Dynamics & Spin (Slots 18-26)
        boolean hasMotion = session.getActiveVelX() != 0 || session.getActiveVelY() != 0 || session.getActiveVelZ() != 0;
        inv.setItem(19, createButton(
                Material.MINECART,
                "<light_purple><bold>Trajectory Motion (Velocity)</bold></light_purple>",
                List.of(
                        "<gray>Move particles across frames (projectiles/geysers).</gray>",
                        "<gray>Motion: <white>X=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelX()) +
                                " Y=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelY()) +
                                " Z=" + String.format(Locale.ROOT, "%+.1f", session.getActiveVelZ()) + "/f</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>Forward Motion (+0.5 Z / frame)</white></yellow>",
                        "<yellow>Right-Click: <white>Rising Motion (+0.5 Y / frame)</white></yellow>",
                        "<yellow>Shift-Click: <white>Reset Motion to (0, 0, 0)</white></yellow>"
                ),
                hasMotion
        ));

        inv.setItem(21, createButton(
                Material.CLOCK,
                "<light_purple><bold>Continuous Spin</bold></light_purple>",
                List.of(
                        "<gray>Rotational velocity added automatically per frame.</gray>",
                        "<gray>Spin: <white>X=" + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinX()) +
                                " Y=" + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinY()) +
                                " Z=" + String.format(Locale.ROOT, "%.0f°", session.getActiveSpinZ()) + "</white></gray>",
                        " ",
                        "<yellow>Click to Cycle Spin Presets</yellow>"
                ),
                session.getActiveSpinX() != 0 || session.getActiveSpinY() != 0 || session.getActiveSpinZ() != 0
        ));

        inv.setItem(23, createButton(
                Material.HOPPER,
                "<yellow><bold>Shift Frame Particles by Offset</bold></yellow>",
                List.of(
                        "<gray>Translates existing particles in Frame " + session.getCurrentFrame() + "</gray>",
                        "<gray>by current active offsets: <white>(" + String.format(Locale.ROOT, "%+.2f, %+.2f, %+.2f", session.getActiveXOffset(), session.getActiveYOffset(), session.getActiveZOffset()) + ")</white></gray>",
                        " ",
                        "<yellow>Click to Translate Frame</yellow>"
                ),
                false
        ));

        inv.setItem(25, createButton(
                Material.DISPENSER,
                "<gold><bold>Shift ALL Frames by Offset</bold></gold>",
                List.of(
                        "<gray>Translates particles in ALL frames</gray>",
                        "<gray>by current active offsets: <white>(" + String.format(Locale.ROOT, "%+.2f, %+.2f, %+.2f", session.getActiveXOffset(), session.getActiveYOffset(), session.getActiveZOffset()) + ")</white></gray>",
                        " ",
                        "<yellow>Click to Translate Animation</yellow>"
                ),
                false
        ));

        inv.setItem(31, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. KEYFRAME & TWEENING STUDIO SUB-MENU (36 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openTweenMenu(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>KEYFRAME & TWEENING STUDIO</bold></gradient>");
        Inventory inv = Bukkit.createInventory(new TweenPickerHolder(), 36, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        int curFrame = session.getCurrentFrame();
        int prevKeyframe = session.findPreviousNonEmptyFrame(curFrame);

        // Center Overview Card
        inv.setItem(4, createButton(
                Material.CLOCK,
                "<gold><bold>Keyframing & Interpolation Hub</bold></gold>",
                List.of(
                        "<gray>Target Frame: <white>Frame " + curFrame + "</white></gray>",
                        "<gray>Previous Stamped Frame: <white>Frame " + prevKeyframe + "</white></gray>",
                        "<gray>Lifespan / Rotting: <white>" + session.getActiveLifespan() + " frames (" + (session.isDecayShrink() ? "Shrink" : "Static") + ")</white></gray>",
                        "<gray>Active Easing Curve: <white>" + session.getActiveEasing().name() + "</white></gray>",
                        "<gray>Playback Speed: <white>" + session.getActiveSpeed() + " ticks/frame</white></gray>"
                ),
                true
        ));

        // Core Tweening Controls (Slots 10, 12, 14, 16)
        inv.setItem(10, createButton(
                Material.HEART_OF_THE_SEA,
                "<aqua><bold>Smart Auto-Tween Timeline</bold></aqua>",
                List.of(
                        "<gray>Smart-interpolates 3D motion, positions, scales,</gray>",
                        "<gray>and particles smoothly between keyframes.</gray>",
                        "<gray>(If destination frame is empty, auto-clones keyframe!)</gray>",
                        " ",
                        "<yellow>Left-Click: <white>Smart-Tween from Frame " + prevKeyframe + " ➔ " + curFrame + "</white></yellow>",
                        "<yellow>Right-Click: <white>Smart-Tween from Frame 0 ➔ " + curFrame + "</white></yellow>"
                ),
                true
        ));

        inv.setItem(12, createButton(
                Material.ROTTEN_FLESH,
                "<red><bold>Rotting / Decay Lifespan</bold></red>",
                List.of(
                        "<gray>How many frames particles survive before disappearing.</gray>",
                        "<gray>Lifespan: <white>" + session.getActiveLifespan() + " frames</white></gray>",
                        "<gray>Fade/Shrink: <white>" + (session.isDecayShrink() ? "ON" : "OFF") + "</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+1 Frame</white></yellow>",
                        "<yellow>Right-Click: <white>-1 Frame</white></yellow>",
                        "<yellow>Shift-Click: <white>Toggle Fade/Shrink</white></yellow>"
                ),
                session.getActiveLifespan() > 1
        ));

        inv.setItem(14, createButton(
                Material.REPEATER,
                "<aqua><bold>Animation Easing Curve</bold></aqua>",
                List.of(
                        "<gray>Mathematical curve for tweening & transitions.</gray>",
                        "<gray>Current: <yellow>" + session.getActiveEasing().name() + "</yellow></gray>",
                        " ",
                        "<yellow>Left-Click: <white>Next Easing Curve</white></yellow>",
                        "<yellow>Right-Click: <white>Open Easing Menu</white></yellow>"
                ),
                false
        ));

        inv.setItem(16, createButton(
                Material.SUGAR,
                "<yellow><bold>Playback Speed / Ticks</bold></yellow>",
                List.of(
                        "<gray>Tick delay pacing per frame.</gray>",
                        "<gray>Current: <white>" + session.getActiveSpeed() + " ticks</white></gray>",
                        " ",
                        "<yellow>Left-Click: <white>+1 Tick</white></yellow>",
                        "<yellow>Right-Click: <white>-1 Tick</white></yellow>"
                ),
                false
        ));

        // Advanced Tools (Slots 20, 21, 22, 24)
        int copySrc = Math.max(0, curFrame - 1);
        inv.setItem(20, createButton(
                Material.WRITABLE_BOOK,
                "<yellow><bold>Copy Previous Frame</bold></yellow>",
                List.of(
                        "<gray>Duplicates all particles from Frame " + copySrc + "</gray>",
                        "<gray>into current Frame " + curFrame + ".</gray>",
                        " ",
                        "<yellow>Click to Copy</yellow>"
                ),
                false
        ));

        inv.setItem(21, createButton(
                Material.FERMENTED_SPIDER_EYE,
                "<red><bold>Apply Decay to Current Frame</bold></red>",
                List.of(
                        "<gray>Propagates points in Frame " + curFrame + " forward</gray>",
                        "<gray>across <white>" + session.getActiveLifespan() + " frames</white> (disappear after " + session.getActiveLifespan() + "f).</gray>",
                        " ",
                        "<yellow>Click to Apply Decay</yellow>"
                ),
                session.getActiveLifespan() > 1
        ));

        inv.setItem(22, createButton(
                Material.DRAGON_BREATH,
                "<light_purple><bold>Apply Decay to ALL Frames</bold></light_purple>",
                List.of(
                        "<gray>Propagates points in ALL keyframes forward</gray>",
                        "<gray>across <white>" + session.getActiveLifespan() + " frames</white> creating lingering trails.</gray>",
                        " ",
                        "<yellow>Click to Apply to All</yellow>"
                ),
                session.getActiveLifespan() > 1
        ));

        inv.setItem(24, createButton(
                Material.RECOVERY_COMPASS,
                "<red><bold>Real-Time Motion Recording</bold></red>",
                List.of(
                        "<gray>Sweep your crosshair freely in the air</gray>",
                        "<gray>like a VR paintbrush to record fluid motion!</gray>",
                        "<gray>(Lingers for active lifespan: <white>" + session.getActiveLifespan() + "f</white>)</gray>",
                        " ",
                        "<yellow>Click to Record: <white>60 Ticks (3 Seconds)</white></yellow>",
                        "<gray>(Closes GUI & begins 3s countdown)</gray>"
                ),
                true
        ));

        inv.setItem(31, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. PARTICLE PICKER SUB-MENU (54 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openParticlePicker(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>PARTICLE LIBRARY</bold></gradient> <dark_gray>|</dark_gray> <gray>Select Particle</gray>");
        Inventory inv = Bukkit.createInventory(new ParticlePickerHolder(), 54, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Color Dusts
        addPickerItem(inv, 0, Material.AMETHYST_SHARD, "dustzen", "Dust Zen (Lilac)", "Church purple / lilac dust.", session);
        addPickerItem(inv, 1, Material.REDSTONE, "dustred", "Dust Red", "Vibrant crimson dust.", session);
        addPickerItem(inv, 2, Material.CYAN_DYE, "dustcyan", "Dust Cyan", "Luminous cyan dust.", session);
        addPickerItem(inv, 3, Material.GLOWSTONE_DUST, "dustgold", "Dust Gold", "Radiant holy gold dust.", session);
        addPickerItem(inv, 4, Material.INK_SAC, "dustblack", "Dust Black", "Dark void obsidian dust.", session);
        addPickerItem(inv, 5, Material.BONE_MEAL, "dustwhite", "Dust White", "Pure alabaster white dust.", session);
        addPickerItem(inv, 6, Material.LIME_DYE, "dustgreen", "Dust Green", "Emerald leaf green dust.", session);
        addPickerItem(inv, 7, Material.ORANGE_DYE, "dustorange", "Dust Orange", "Blaze orange dust.", session);
        addPickerItem(inv, 8, Material.PINK_DYE, "dustpink", "Dust Pink", "Soft pastel cherry dust.", session);
        addPickerItem(inv, 9, Material.LAPIS_LAZULI, "dustblue", "Dust Blue", "Deep ocean sapphire dust.", session);
        addPickerItem(inv, 10, Material.YELLOW_DYE, "dustyellow", "Dust Yellow", "Bright sunburst yellow dust.", session);
        addPickerItem(inv, 11, Material.PURPLE_DYE, "dustpurple", "Dust Purple", "Imperial amethyst purple dust.", session);

        // Combat & Elemental VFX
        addPickerItem(inv, 18, Material.END_ROD, "endrod", "End Rod", "Celestial white sparkle beam.", session);
        addPickerItem(inv, 19, Material.BLAZE_POWDER, "flame", "Flame", "Fiery orange flame sparks.", session);
        addPickerItem(inv, 20, Material.SOUL_CAMPFIRE, "soulflame", "Soul Flame", "Cyan spirit soul flame.", session);
        addPickerItem(inv, 21, Material.LIGHTNING_ROD, "electric", "Electric Spark", "Branching copper sparks.", session);
        addPickerItem(inv, 22, Material.ECHO_SHARD, "sculksoul", "Sculk Soul", "Rising deep dark souls.", session);
        addPickerItem(inv, 23, Material.BREWING_STAND, "witch", "Witch", "Purple potion spell swirl.", session);
        addPickerItem(inv, 24, Material.QUARTZ, "crit", "Critical Strike", "Sharp brown impact crit hit.", session);
        addPickerItem(inv, 25, Material.ENCHANTING_TABLE, "magiccrit", "Magic Crit", "Turquoise enchanted strike.", session);
        addPickerItem(inv, 26, Material.OBSIDIAN, "portal", "Portal", "Purple dimensional swirls.", session);
        addPickerItem(inv, 27, Material.IRON_SWORD, "sweep", "Sweep Attack", "Sweeping horizontal sword slash.", session);
        addPickerItem(inv, 28, Material.WIND_CHARGE, "gust", "Wind Gust", "Bursting air wind charge.", session);
        addPickerItem(inv, 29, Material.FIREWORK_ROCKET, "fireworkexplosion", "Firework", "Sparkling burst explosion.", session);
        addPickerItem(inv, 30, Material.DRAGON_BREATH, "dragonbreath", "Dragon Breath", "Lingering magenta mist.", session);
        addPickerItem(inv, 31, Material.TOTEM_OF_UNDYING, "totemofundying", "Totem Sparks", "Golden green resurrection sparks.", session);
        addPickerItem(inv, 32, Material.CAMPFIRE, "smoke", "Smoke", "Rising atmospheric smoke.", session);

        // 3D Block Displays
        addPickerItem(inv, 36, Material.REDSTONE_BLOCK, "redstoneblockdisplay", "Redstone Block", "Miniature glowing block display.", session);
        addPickerItem(inv, 37, Material.DIAMOND_BLOCK, "blockdisplay:diamond_block", "Diamond Block", "Miniature diamond block display.", session);
        addPickerItem(inv, 38, Material.EMERALD_BLOCK, "blockdisplay:emerald_block", "Emerald Block", "Miniature emerald block display.", session);
        addPickerItem(inv, 39, Material.AMETHYST_BLOCK, "blockdisplay:amethyst_block", "Amethyst Block", "Miniature amethyst cluster block.", session);
        addPickerItem(inv, 40, Material.GOLD_BLOCK, "blockdisplay:gold_block", "Gold Block", "Miniature gold block display.", session);
        addPickerItem(inv, 41, Material.CRYING_OBSIDIAN, "blockdisplay:crying_obsidian", "Crying Obsidian", "Miniature weeping obsidian display.", session);
        addPickerItem(inv, 42, Material.SEA_LANTERN, "blockdisplay:sea_lantern", "Sea Lantern", "Miniature aquatic lantern display.", session);
        addPickerItem(inv, 43, Material.GLOWSTONE, "blockdisplay:glowstone", "Glowstone", "Miniature incandescent block display.", session);

        // Back Button
        inv.setItem(49, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void addPickerItem(Inventory inv, int slot, Material mat, String alias, String name, String desc, WandSession session) {
        boolean active = session.getActiveParticle().getRawIdentifier().equalsIgnoreCase(alias);
        inv.setItem(slot, createButton(
                mat,
                "<#d8b4fe><bold>" + name + "</bold></#d8b4fe>",
                List.of(
                        "<gray>" + desc + "</gray>",
                        "<dark_gray>Alias: " + alias + "</dark_gray>",
                        " ",
                        active ? "<green>✔ CURRENTLY SELECTED</green>" : "<yellow>Click to Select</yellow>"
                ),
                active
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. EASING PICKER SUB-MENU (27 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openEasingPicker(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>EASING CURVES</bold></gradient> <dark_gray>|</dark_gray> <gray>Select Curve</gray>");
        Inventory inv = Bukkit.createInventory(new EasingPickerHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        EasingType[] curves = EasingType.values();
        Material[] mats = new Material[] {
                Material.STICK, Material.SLIME_BALL, Material.MAGMA_CREAM, Material.ENDER_PEARL,
                Material.IRON_INGOT, Material.GOLD_INGOT, Material.SLIME_BLOCK, Material.STRING
        };

        for (int i = 0; i < curves.length && i < 8; i++) {
            EasingType ease = curves[i];
            boolean active = session.getActiveEasing() == ease;
            inv.setItem(10 + i, createButton(
                    mats[i % mats.length],
                    "<aqua><bold>" + ease.name() + "</bold></aqua>",
                    List.of(
                            "<gray>Velocity interpolation profile.</gray>",
                            " ",
                            active ? "<green>✔ CURRENT CURVE</green>" : "<yellow>Click to Select</yellow>"
                    ),
                    active
            ));
        }

        inv.setItem(22, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. TEMPLATE PICKER SUB-MENU (27 SLOTS)
    // ─────────────────────────────────────────────────────────────────────────────
    public void openTemplatePicker(Player player, WandSession session) {
        Component title = miniMessage.deserialize("<gradient:#ffffff:#d8b4fe><bold>ANIMATION TEMPLATES</bold></gradient> <dark_gray>|</dark_gray> <gray>1-Click VFX</gray>");
        Inventory inv = Bukkit.createInventory(new TemplatePickerHolder(), 27, title);

        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        AnimationTemplate[] templates = AnimationTemplate.values();
        int[] slots = new int[] { 10, 11, 12, 13, 14, 15, 16 };

        for (int i = 0; i < templates.length && i < slots.length; i++) {
            AnimationTemplate tmpl = templates[i];
            inv.setItem(slots[i], createButton(
                    tmpl.getIcon(),
                    "<#d8b4fe><bold>" + tmpl.getDisplayName() + "</bold></#d8b4fe>",
                    List.of(
                            "<gray>" + tmpl.getDescription() + "</gray>",
                            "<gray>Frames: <white>" + tmpl.getFrameCount() + " keyframes</white></gray>",
                            " ",
                            "<yellow>Click to Load & Loop in 3D</yellow>"
                    ),
                    true
            ));
        }

        inv.setItem(22, createButton(
                Material.ARROW,
                "<gold>⬅ <white><bold>" + TextUtil.toSmallCaps("Back to Studio") + "</bold></white></gold>",
                List.of("<gray>Return to Particle Studio main menu.</gray>"),
                false
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 8. INVENTORY CLICK HANDLERS
    // ─────────────────────────────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof StudioHolder)
                && !(holder instanceof ShapePickerHolder)
                && !(holder instanceof MotionPickerHolder)
                && !(holder instanceof TweenPickerHolder)
                && !(holder instanceof ParticlePickerHolder)
                && !(holder instanceof EasingPickerHolder)
                && !(holder instanceof TemplatePickerHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        WandSession session = animationManager.getSession(player);
        ClickType click = event.getClick();

        if (holder instanceof StudioHolder) {
            handleStudioClick(player, session, slot, click);
        } else if (holder instanceof ShapePickerHolder) {
            handleShapePickerClick(player, session, slot, click);
        } else if (holder instanceof MotionPickerHolder) {
            handleMotionPickerClick(player, session, slot, click);
        } else if (holder instanceof TweenPickerHolder) {
            handleTweenPickerClick(player, session, slot, click);
        } else if (holder instanceof ParticlePickerHolder) {
            handleParticlePickerClick(player, session, slot);
        } else if (holder instanceof EasingPickerHolder) {
            handleEasingPickerClick(player, session, slot);
        } else if (holder instanceof TemplatePickerHolder) {
            handleTemplatePickerClick(player, session, slot);
        }
    }

    private void handleStudioClick(Player player, WandSession session, int slot, ClickType click) {
        int curFrame = session.getCurrentFrame();

        switch (slot) {
            // Row 0: Timeline controls
            case 0 -> { // -5 frames
                session.setCurrentFrame(Math.max(0, curFrame - 5));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
                openStudio(player, session);
            }
            case 1 -> { // -1 frame
                session.setCurrentFrame(Math.max(0, curFrame - 1));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                openStudio(player, session);
            }
            case 2 -> { // Jump 0
                session.setCurrentFrame(0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openStudio(player, session);
            }
            case 4 -> { // Status card click
                if (click.isShiftClick()) {
                    session.setCurrentFrame(0);
                } else if (click.isRightClick()) {
                    session.setCurrentFrame(Math.max(0, curFrame - 1));
                } else {
                    session.setCurrentFrame(curFrame + 1);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                openStudio(player, session);
            }
            case 6 -> { // Add / jump to end frame
                int nextFree = Math.max(curFrame, session.getAnimation().getMaxFrameIndex()) + 1;
                session.setCurrentFrame(nextFree);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
                openStudio(player, session);
            }
            case 7 -> { // +1 frame
                session.setCurrentFrame(curFrame + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                openStudio(player, session);
            }
            case 8 -> { // +5 frames
                session.setCurrentFrame(curFrame + 5);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                openStudio(player, session);
            }

            // Row 1: Quick Playback & Actions
            case 10 -> animationManager.playPreview(player, session);
            case 11 -> {
                animationManager.toggleLoop(player, session);
                openStudio(player, session);
            }
            case 13 -> { // Stamp Active Preset Now
                int stamped = session.stampActivePreset(player.getEyeLocation(), plugin);
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Stamped <white>" + session.getActivePreset().name() + "</white> <gray>(" + stamped + " pts)</gray> into Frame <white>" + curFrame + "</white></#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
                openStudio(player, session);
            }
            case 15 -> { // Undo
                if (session.undo()) {
                    player.sendMessage(miniMessage.deserialize("<gold>Undo successful. <gray>(Restored previous snapshot)</gray></gold>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.2f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<gray>Nothing to undo.</gray>"));
                }
                openStudio(player, session);
            }
            case 16 -> { // Redo
                if (session.redo()) {
                    player.sendMessage(miniMessage.deserialize("<gold>Redo successful. <gray>(Restored undone snapshot)</gray></gold>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.7f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<gray>Nothing to redo.</gray>"));
                }
                openStudio(player, session);
            }

            // Row 3: Dedicated Studio Portals
            case 28 -> openShapeMenu(player, session);
            case 29 -> openMotionMenu(player, session);
            case 31 -> openParticlePicker(player, session);
            case 33 -> openTweenMenu(player, session);
            case 34 -> openTemplatePicker(player, session);

            // Row 5: Lifecycle & Utility
            case 45 -> { // Clear frame
                if (session.clearCurrentFrame()) {
                    player.sendMessage(miniMessage.deserialize("<gold>Cleared particles in Frame " + curFrame + ". <gray>(Undoable via Shift + F)</gray></gold>"));
                    player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 0.7f, 1.2f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<gray>Frame " + curFrame + " was already empty.</gray>"));
                }
                openStudio(player, session);
            }
            case 47 -> { // Clear animation
                session.clearAnimation();
                player.sendMessage(miniMessage.deserialize("<red>Cleared entire particle animation timeline! <gray>(Shift + F to Undo)</gray></red>"));
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.0f);
                openStudio(player, session);
            }
            case 49 -> { // Save config
                animationManager.saveAnimationToConfig(session.getAnimation());
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Saved animation <white>" + session.getAnimation().getName() + "</white> to <gray>animations.yml</gray>!</#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.2f);
            }
            case 51 -> { // Reload config
                animationManager.loadAnimations();
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Reloaded custom particle animations from animations.yml</#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
            }
            case 53 -> { // Close studio
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
        }
    }

    private void handleShapePickerClick(Player player, WandSession session, int slot, ClickType click) {
        if (slot == 22) {
            openStudio(player, session);
            return;
        }

        MathPreset selected = switch (slot) {
            case 9 -> MathPreset.POINT;
            case 10 -> MathPreset.STAR_4;
            case 11 -> MathPreset.STAR_8;
            case 12 -> MathPreset.RING;
            case 13 -> MathPreset.SLASH;
            case 14 -> MathPreset.HELIX;
            case 15 -> MathPreset.LISSAJOUS;
            case 16 -> MathPreset.ROSE;
            case 17 -> MathPreset.BOLT_VEINS;
            default -> null;
        };

        if (selected != null) {
            session.setActivePreset(selected);
            if (click.isRightClick() || click.isShiftClick()) {
                int stamped = session.stampActivePreset(player.getEyeLocation(), plugin);
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Stamped <white>" + selected.name() + "</white> <gray>(" + stamped + " pts)</gray> into Frame " + session.getCurrentFrame() + "</#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
            } else {
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Active shape preset set to <white>" + selected.name() + "</white></#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            }
            openShapeMenu(player, session);
        }
    }

    private void handleMotionPickerClick(Player player, WandSession session, int slot, ClickType click) {
        if (slot == 31) {
            openStudio(player, session);
            return;
        }

        switch (slot) {
            case 10 -> { // Scale
                if (click.isShiftClick()) {
                    session.setActiveScale(1.0);
                } else if (click.isRightClick()) {
                    session.setActiveScale(Math.max(0.25, session.getActiveScale() - 0.25));
                } else {
                    session.setActiveScale(Math.min(5.0, session.getActiveScale() + 0.25));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openMotionMenu(player, session);
            }
            case 12 -> { // X-Offset
                if (click.isShiftClick()) {
                    session.setActiveXOffset(0.0);
                } else if (click.isRightClick()) {
                    session.setActiveXOffset(session.getActiveXOffset() - 0.5);
                } else {
                    session.setActiveXOffset(session.getActiveXOffset() + 0.5);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openMotionMenu(player, session);
            }
            case 13 -> { // Y-Offset
                if (click.isShiftClick()) {
                    session.setActiveYOffset(0.0);
                } else if (click.isRightClick()) {
                    session.setActiveYOffset(session.getActiveYOffset() - 0.5);
                } else {
                    session.setActiveYOffset(session.getActiveYOffset() + 0.5);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openMotionMenu(player, session);
            }
            case 14 -> { // Z-Offset
                if (click.isShiftClick()) {
                    session.setActiveZOffset(0.0);
                } else if (click.isRightClick()) {
                    session.setActiveZOffset(session.getActiveZOffset() - 0.5);
                } else {
                    session.setActiveZOffset(session.getActiveZOffset() + 0.5);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openMotionMenu(player, session);
            }
            case 16 -> { // 3D Rotation
                if (click.isShiftClick()) {
                    session.setActiveRotZ((session.getActiveRotZ() + 15.0) % 360.0);
                } else if (click.isRightClick()) {
                    session.setActiveRotY((session.getActiveRotY() + 15.0) % 360.0);
                } else {
                    session.setActiveRotX((session.getActiveRotX() + 15.0) % 360.0);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openMotionMenu(player, session);
            }
            case 19 -> { // Trajectory Velocity
                if (click.isShiftClick()) {
                    session.setVelocity(0.0, 0.0, 0.0);
                } else if (click.isRightClick()) {
                    double nextY = (session.getActiveVelY() >= 1.5) ? 0.0 : session.getActiveVelY() + 0.5;
                    session.setActiveVelY(nextY);
                } else {
                    double nextZ = (session.getActiveVelZ() >= 2.0) ? 0.0 : session.getActiveVelZ() + 0.5;
                    session.setActiveVelZ(nextZ);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                openMotionMenu(player, session);
            }
            case 21 -> { // Spin
                if (session.getActiveSpinY() == 0 && session.getActiveSpinX() == 0) {
                    session.setSpin(0, 15, 0); // Yaw 15
                } else if (session.getActiveSpinY() == 15 && session.getActiveSpinX() == 0) {
                    session.setSpin(15, 0, 0); // Pitch 15
                } else if (session.getActiveSpinX() == 15) {
                    session.setSpin(15, 15, 15); // Full 3D corkscrew
                } else {
                    session.setSpin(0, 0, 0); // Off
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                openMotionMenu(player, session);
            }
            case 23 -> { // Move Frame Particles by Offset
                double dx = session.getActiveXOffset();
                double dy = session.getActiveYOffset();
                double dz = session.getActiveZOffset();
                if (dx == 0 && dy == 0 && dz == 0) {
                    player.sendMessage(miniMessage.deserialize("<yellow>Tune X, Y, or Z offset first to move particles!</yellow>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                } else {
                    int moved = session.translateCurrentFrame(dx, dy, dz);
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Shifted <white>" + moved + " points</white> in Frame " + session.getCurrentFrame() + " by (" + String.format(Locale.ROOT, "%+.2f, %+.2f, %+.2f", dx, dy, dz) + ")</#d8b4fe>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                }
                openMotionMenu(player, session);
            }
            case 25 -> { // Move All Frames by Offset
                double dx = session.getActiveXOffset();
                double dy = session.getActiveYOffset();
                double dz = session.getActiveZOffset();
                if (dx == 0 && dy == 0 && dz == 0) {
                    player.sendMessage(miniMessage.deserialize("<yellow>Tune X, Y, or Z offset first to move particles!</yellow>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                } else {
                    int moved = session.translateAllFrames(dx, dy, dz);
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Shifted <white>" + moved + " points</white> across ALL frames by (" + String.format(Locale.ROOT, "%+.2f, %+.2f, %+.2f", dx, dy, dz) + ")</#d8b4fe>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
                }
                openMotionMenu(player, session);
            }
        }
    }

    private void handleTweenPickerClick(Player player, WandSession session, int slot, ClickType click) {
        if (slot == 31) {
            openStudio(player, session);
            return;
        }

        int curFrame = session.getCurrentFrame();

        switch (slot) {
            case 10 -> { // Smart Auto-Tween
                int fromFrame = click.isRightClick() ? 0 : session.findPreviousNonEmptyFrame(curFrame);
                if (curFrame <= fromFrame + 1) {
                    player.sendMessage(miniMessage.deserialize("<red>Cannot tween: Need at least 1 intermediate frame between Frame " + fromFrame + " and Frame " + curFrame + "</red>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
                int tweened = session.smartAutoTween(fromFrame, curFrame);
                if (tweened > 0) {
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Smart Auto-Tweened <white>" + tweened + " intermediate frames</white> between Frame " + fromFrame + " ➔ " + curFrame + " <gray>(" + session.getActiveEasing().name() + ")</gray></#d8b4fe>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<red>Ensure Frame " + fromFrame + " has stamped particles to interpolate from.</red>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                openTweenMenu(player, session);
            }
            case 12 -> { // Decay lifespan
                if (click.isShiftClick()) {
                    session.setDecayShrink(!session.isDecayShrink());
                } else if (click.isRightClick()) {
                    session.setActiveLifespan(Math.max(1, session.getActiveLifespan() - 1));
                } else {
                    session.setActiveLifespan(Math.min(20, session.getActiveLifespan() + 1));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                openTweenMenu(player, session);
            }
            case 14 -> { // Easing curve
                if (click.isRightClick()) {
                    openEasingPicker(player, session);
                } else {
                    EasingType[] vals = EasingType.values();
                    int nextOrd = (session.getActiveEasing().ordinal() + 1) % vals.length;
                    session.setActiveEasing(vals[nextOrd]);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                    openTweenMenu(player, session);
                }
            }
            case 16 -> { // Speed
                int delta = click.isRightClick() ? -1 : 1;
                session.setActiveSpeed(Math.max(1, session.getActiveSpeed() + delta));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                openTweenMenu(player, session);
            }
            case 20 -> { // Copy previous frame
                int src = Math.max(0, curFrame - 1);
                int copied = session.copyFrame(src);
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Copied " + copied + " points from Frame " + src + " into Frame " + curFrame + "</#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                openTweenMenu(player, session);
            }
            case 21 -> { // Apply decay to current frame
                int added = session.applyDecayToCurrentFrame();
                if (added > 0) {
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Applied decay: Created <white>" + added + " trailing points</white> forward across <white>" + session.getActiveLifespan() + " frames</white> from Frame " + curFrame + ".</#d8b4fe>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<yellow>Frame " + curFrame + " has no particles, or lifespan is set to 1.</yellow>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                openTweenMenu(player, session);
            }
            case 22 -> { // Apply decay to all frames
                int added = session.applyDecayToAllFrames();
                if (added > 0) {
                    player.sendMessage(miniMessage.deserialize("<#d8b4fe>Applied decay to ALL frames: Generated <white>" + added + " trailing decay points</white> (" + session.getActiveLifespan() + " frame lifespan).</#d8b4fe>"));
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
                } else {
                    player.sendMessage(miniMessage.deserialize("<yellow>Animation is empty, or lifespan is set to 1.</yellow>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                openTweenMenu(player, session);
            }
            case 24 -> { // Real-Time Motion Recording
                player.closeInventory();
                animationManager.startMotionRecording(player, session, 60, 3.0);
            }
        }
    }

    private void handleParticlePickerClick(Player player, WandSession session, int slot) {
        if (slot == 49) {
            openStudio(player, session);
            return;
        }

        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getItemMeta() == null || item.getItemMeta().lore() == null) return;

        List<Component> lore = item.getItemMeta().lore();
        for (Component line : lore) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("Alias: ")) {
                String alias = plain.substring("Alias: ".length()).trim();
                session.setActiveParticle(ParticleAlias.fromString(alias));
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Particle set to <white>" + alias + "</white></#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
                openStudio(player, session);
                return;
            }
        }
    }

    private void handleEasingPickerClick(Player player, WandSession session, int slot) {
        if (slot == 22) {
            openTweenMenu(player, session);
            return;
        }

        EasingType[] curves = EasingType.values();
        int idx = slot - 10;
        if (idx >= 0 && idx < curves.length) {
            session.setActiveEasing(curves[idx]);
            player.sendMessage(miniMessage.deserialize("<#d8b4fe>Easing curve set to <white>" + curves[idx].name() + "</white></#d8b4fe>"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            openTweenMenu(player, session);
        }
    }

    private void handleTemplatePickerClick(Player player, WandSession session, int slot) {
        if (slot == 22) {
            openStudio(player, session);
            return;
        }

        int[] slots = new int[] { 10, 11, 12, 13, 14, 15, 16 };
        AnimationTemplate[] templates = AnimationTemplate.values();

        for (int i = 0; i < slots.length && i < templates.length; i++) {
            if (slots[i] == slot) {
                AnimationTemplate tmpl = templates[i];
                session.applyTemplate(tmpl);
                if (!session.isLooping()) {
                    animationManager.toggleLoop(player, session);
                }
                player.sendMessage(miniMessage.deserialize("<#d8b4fe>Applied template <white>" + tmpl.getDisplayName() + "</white> <gray>(" + tmpl.getFrameCount() + " frames)</gray>! Auto-looping 3D preview.</#d8b4fe>"));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
                openStudio(player, session);
                return;
            }
        }
    }
}
