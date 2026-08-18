package com.churchsmp.listeners;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Maps in-world gameplay events onto the good/evil deed point table in config.yml.
 * This is a starting set covering the deeds listed in the design doc; extend
 * with more listeners (tithe GUI, altar block, confession booth, sermons) as
 * those custom blocks/GUIs are built out.
 */
public class DeedListener implements Listener {

    private final ChurchSMP plugin;
    private final AlignmentManager alignment;

    public DeedListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.alignment = plugin.getAlignmentManager();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---- Evil: murder ----
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        double points = cfg().getDouble("evil-deeds.murder-player", -15);
        alignment.applyDeed(killer, points);
        notify(killer, "Alignment shifts for the taking of a life.", points);
    }

    // ---- Evil: killing villagers ----
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() != EntityType.VILLAGER) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        double points = cfg().getDouble("evil-deeds.kill-villager", -8);
        alignment.applyDeed(killer, points);
        notify(killer, "The village mourns.", points);
    }

    // ---- Good: curing a zombie villager ----
    @EventHandler
    public void onZombieVillagerCure(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.ZombieVillager zv)) return;

        Player curer = findNearestPlayer(zv.getLocation(), 8);
        if (curer == null) return;

        double points = cfg().getDouble("good-deeds.cure-zombie-villager", 10);
        alignment.applyDeed(curer, points);
        notify(curer, "A soul is redeemed through your hand.", points);
    }

    // ---- Good: breeding animals near church ground ----
    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player breeder)) return;
        if (!plugin.getChurchRegionManager().isHolyGround(event.getEntity().getLocation())) return;

        double points = cfg().getDouble("good-deeds.breed-animal-near-church", 2);
        alignment.applyDeed(breeder, points);
        notify(breeder, "Life flourishes on holy ground.", points);
    }

    // ---- Evil: desecrating holy ground (fire/TNT placement) ----
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getChurchRegionManager().isHolyGround(event.getBlock().getLocation())) return;

        org.bukkit.Material type = event.getBlock().getType();
        boolean desecrating = type == org.bukkit.Material.TNT || type == org.bukkit.Material.FIRE
                || type == org.bukkit.Material.LAVA;
        if (!desecrating) return;

        double points = cfg().getDouble("evil-deeds.desecrate-holy-ground", -12);
        alignment.applyDeed(player, points);
        notify(player, "You defile consecrated ground.", points);
    }

    // ---- Evil: griefing protected church blocks ----
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getChurchRegionManager().isHolyGround(event.getBlock().getLocation())) return;
        if (player.isOp() || player.hasPermission("churchsmp.admin")) return;

        double points = cfg().getDouble("evil-deeds.griefing-church", -10);
        alignment.applyDeed(player, points);
        notify(player, "Breaking sacred stone weighs on your soul.", points);
    }

    // ---- helpers ----

    private Player findNearestPlayer(org.bukkit.Location loc, double radius) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof Player p) {
                double d = p.getLocation().distanceSquared(loc);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    private void notify(Player player, String flavorText, double points) {
        NamedTextColor color = points >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        String sign = points >= 0 ? "+" : "";
        player.sendMessage(Component.text(flavorText + " (" + sign + (int) points + " alignment)", color));
    }
}
