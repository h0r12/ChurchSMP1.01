package com.churchsmp.advancement;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class AdvancementManager {

    private final ChurchSMP plugin;

    public AdvancementManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    public void generateDatapack() {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File datapackFolder = new File(worldFolder, "datapacks/churchsmp_advancements");
        
        boolean isNew = false;
        if (!datapackFolder.exists()) {
            datapackFolder.mkdirs();
            isNew = true;
        }

        File packMeta = new File(datapackFolder, "pack.mcmeta");
        if (!packMeta.exists()) {
            writeJson(packMeta, """
                    {
                      "pack": {
                        "pack_format": 48,
                        "description": "ChurchSMP Custom Advancements"
                      }
                    }
                    """);
        }

        File advFolder = new File(datapackFolder, "data/churchsmp/advancement");
        if (!advFolder.exists()) {
            advFolder.mkdirs();
        }

        // Generate JSONs
        createAdvancement(advFolder, "root", "minecraft:end_crystal", "The Path of Sin", "Enter the Church SMP", "task", null, true, false);
        
        // Relics
        createAdvancement(advFolder, "craft_liminal_core", "minecraft:end_crystal", "Core of Reality", "Craft a Liminal Core.", "task", "churchsmp:root", true, false);
        createAdvancement(advFolder, "choose_iniquity", "minecraft:ghast_tear", "Embrace Iniquity", "Obtain the Iniquity Relic.", "task", "churchsmp:craft_liminal_core", true, false);
        createAdvancement(advFolder, "choose_impiety", "minecraft:amethyst_shard", "Embrace Impiety", "Obtain the Impiety Relic.", "task", "churchsmp:craft_liminal_core", true, false);
        createAdvancement(advFolder, "choose_obscura", "minecraft:echo_shard", "The Voided Path", "Obtain the Obscura Relic.", "task", "churchsmp:craft_liminal_core", true, false);

        // Weapons
        createAdvancement(advFolder, "obtain_voidbreaker", "minecraft:mace", "Shatter the Void", "Obtain the VoidBreaker.", "goal", "churchsmp:choose_obscura", true, false);
        createAdvancement(advFolder, "obtain_grim", "minecraft:netherite_hoe", "Death's Embrace", "Obtain the Grim Scythe.", "goal", "churchsmp:choose_iniquity", true, false);
        createAdvancement(advFolder, "obtain_judas", "minecraft:netherite_axe", "Traitor's Kiss", "Obtain the Judas Axe.", "goal", "churchsmp:choose_iniquity", true, false);
        createAdvancement(advFolder, "obtain_sorrowess", "minecraft:trident", "Weeping Tides", "Obtain the Sorrowess Trident.", "goal", "churchsmp:choose_impiety", true, false);
        createAdvancement(advFolder, "obtain_excalibur", "minecraft:golden_sword", "Holy Light", "Obtain the Excalibur.", "goal", "churchsmp:choose_impiety", true, false);
        createAdvancement(advFolder, "obtain_luminescence", "minecraft:trident", "Spear of the Heavens", "Obtain the Luminescence Spear.", "goal", "churchsmp:choose_impiety", true, false);
        createAdvancement(advFolder, "obtain_mayim", "minecraft:diamond_sword", "Frozen Depths", "Obtain the Mayim.", "goal", "churchsmp:choose_impiety", true, false);

        // Finale
        createAdvancement(advFolder, "forsaking_ritual", "minecraft:crying_obsidian", "The Forsaking", "Toss a Liminal Core into the void to begin the Purge setup.", "challenge", "churchsmp:craft_liminal_core", true, false);
        createAdvancement(advFolder, "altar_spawn", "minecraft:lodestone", "The End Begins", "The Purge Altar has manifested.", "task", "churchsmp:forsaking_ritual", true, true);
        createAdvancement(advFolder, "become_juggernaut", "minecraft:dragon_egg", "The Juggernaut", "Claim the Altar and become the Juggernaut.", "challenge", "churchsmp:altar_spawn", true, true);
        createAdvancement(advFolder, "liminal_null", "minecraft:bedrock", "Control the Realm", "Enter the Liminal Null control room.", "challenge", "churchsmp:become_juggernaut", true, true);

        if (isNew) {
            plugin.getLogger().info("--------------------------------------------------");
            plugin.getLogger().info("ChurchSMP Advancements datapack generated!");
            plugin.getLogger().info("Please run /minecraft:reload to load them.");
            plugin.getLogger().info("--------------------------------------------------");
        }
        
        startTrackerTask();
    }

    private void startTrackerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean hasCore = false;
                boolean hasIniq = false, hasImp = false, hasObs = false;
                boolean hasVoid = false, hasGrim = false, hasJudas = false, hasSorr = false, hasExcal = false, hasLumi = false, hasMayim = false;

                for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                    if (item == null) continue;
                    
                    if (com.churchsmp.item.LiminalCoreItem.isLiminalCore(plugin, item)) hasCore = true;
                    else if (com.churchsmp.item.RelicItem.isRelic(plugin, item, com.churchsmp.item.RelicItem.RelicType.INIQUITY)) hasIniq = true;
                    else if (com.churchsmp.item.RelicItem.isRelic(plugin, item, com.churchsmp.item.RelicItem.RelicType.IMPIETY)) hasImp = true;
                    else if (com.churchsmp.item.RelicItem.isRelic(plugin, item, com.churchsmp.item.RelicItem.RelicType.OBSCURA)) hasObs = true;
                    
                    com.churchsmp.weapon.LegendaryWeapon weapon = plugin.getWeaponManager().getWeaponFromItem(item);
                    if (weapon != null) {
                        String id = weapon.getId();
                        if (id.equals("voidbreaker")) hasVoid = true;
                        else if (id.equals("grim")) hasGrim = true;
                        else if (id.equals("judas")) hasJudas = true;
                        else if (id.equals("sorrowess")) hasSorr = true;
                        else if (id.equals("excalibur")) hasExcal = true;
                        else if (id.equals("luminescencespear")) hasLumi = true;
                        else if (id.equals("mayim")) hasMayim = true;
                    }
                }

                if (hasCore) grantAdvancement(player, "craft_liminal_core");
                if (hasIniq) grantAdvancement(player, "choose_iniquity");
                if (hasImp) grantAdvancement(player, "choose_impiety");
                if (hasObs) grantAdvancement(player, "choose_obscura");
                if (hasVoid) grantAdvancement(player, "obtain_voidbreaker");
                if (hasGrim) grantAdvancement(player, "obtain_grim");
                if (hasJudas) grantAdvancement(player, "obtain_judas");
                if (hasSorr) grantAdvancement(player, "obtain_sorrowess");
                if (hasExcal) grantAdvancement(player, "obtain_excalibur");
                if (hasLumi) grantAdvancement(player, "obtain_luminescence");
                if (hasMayim) grantAdvancement(player, "obtain_mayim");
            }
        }, 100L, 100L); // Check every 5 seconds
    }

    private void createAdvancement(File folder, String name, String icon, String title, String description, String frame, String parent, boolean showToast, boolean announce) {
        File file = new File(folder, name + ".json");
        if (file.exists()) return;

        String parentStr = parent != null ? "\"parent\": \"" + parent + "\"," : "";
        String background = parent == null ? "\"background\": \"minecraft:textures/gui/advancements/backgrounds/end.png\"," : "";

        String json = """
                {
                  %s
                  "display": {
                    "icon": { "id": "%s" },
                    "title": "%s",
                    "description": "%s",
                    "frame": "%s",
                    "show_toast": %b,
                    "announce_to_chat": %b,
                    "hidden": false,
                    %s
                  },
                  "criteria": {
                    "impossible": {
                      "trigger": "minecraft:impossible"
                    }
                  }
                }
                """.formatted(parentStr, icon, title, description, frame, showToast, announce, background);
        
        // Remove trailing comma from display block if background is empty
        if (background.isEmpty()) {
            json = json.replace("\"hidden\": false,\n                    \n                  }", "\"hidden\": false\n                  }");
        }

        writeJson(file, json);
    }

    private void writeJson(File file, String content) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void grantAdvancement(Player player, String name) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        Advancement adv = Bukkit.getAdvancement(key);
        if (adv != null) {
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String criteria : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteria);
            }
        }
    }
}
