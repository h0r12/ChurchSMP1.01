package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Every weapon has its own small ambient particle "signature" that plays
 * continuously while it's held in the main hand — this is what actually
 * delivers the looped-spiral / expanding-ring look from the concept art,
 * since a flat item texture can't animate in 3D space on its own.
 */
public class WeaponPassiveEffects {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private int globalTick = 0;

    public WeaponPassiveEffects(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                globalTick++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    WeaponType type = weaponManager.getWeaponType(held);
                    if (type != null) {
                        play(type, player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 4L); // every 4 ticks (0.2s), starting 1s after enable
    }

    private void play(WeaponType type, Player player) {
        switch (type) {
            case BLADE_OF_ARCHANGEL -> archangelTrail(player);
            case SWORD_OF_DAVID -> davidPulseRing(player);
            case STAFF_OF_MOSES -> mosesSpiral(player);
            case SCYTHE_OF_CAIN -> cainSwirl(player);
            case TRIDENT_OF_LEVIATHAN -> leviathanTrail(player);
            case BLADE_OF_JUDAS -> judasFlicker(player);
            case VOIDBREAKER -> voidbreakerSpiral(player);
        }
    }

    private void archangelTrail(Player player) {
        player.getWorld().spawnParticle(Particle.END_ROD,
                player.getLocation().add(0, 1.3, 0), 2, 0.3, 0.2, 0.3, 0.01);
    }

    /** Expands outward from the player and resets every 2 seconds (40 ticks). */
    private void davidPulseRing(Player player) {
        int phase = globalTick % 40; // 0..39, one full expand cycle every 2s
        double radius = (phase / 40.0) * 2.2;
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            player.getWorld().spawnParticle(Particle.WITCH,
                    player.getLocation().add(x, 0.1, z), 0, 0, 0, 0, 0);
        }
    }

    /** A slow rotating spiral climbing around the player. */
    private void mosesSpiral(Player player) {
        double angle = globalTick * 0.3;
        double height = ((globalTick % 30) / 30.0) * 2.0;
        double radius = 0.8;
        Vector offset = new Vector(radius * Math.cos(angle), height, radius * Math.sin(angle));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(offset), 1, 0, 0, 0, 0);
    }

    private void cainSwirl(Player player) {
        double angle = -globalTick * 0.5; // opposite rotation direction, feels more sinister
        double radius = 0.7;
        Vector offset = new Vector(radius * Math.cos(angle), 0.9, radius * Math.sin(angle));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(140, 10, 10), 1f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(offset), 1, 0, 0, 0, 0, dust);
    }

    private void leviathanTrail(Player player) {
        player.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                player.getLocation().add(0, 1.1, 0), 2, 0.2, 0.2, 0.2, 0);
    }

    private void judasFlicker(Player player) {
        if (globalTick % 5 != 0) return; // sparse, twitchy flicker rather than a smooth trail
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(160, 20, 20), 0.8f);
        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add((Math.random() - 0.5) * 0.6, 1 + Math.random() * 0.4, (Math.random() - 0.5) * 0.6),
                1, 0, 0, 0, 0, dust);
    }

    /** Dark looped spiral, matching the VoidBreaker concept art. */
    private void voidbreakerSpiral(Player player) {
        double angle = globalTick * 0.45;
        double radius = 0.6;
        Vector offset = new Vector(radius * Math.cos(angle), 1.1 + Math.sin(globalTick * 0.1) * 0.2, radius * Math.sin(angle));
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(offset), 1, 0, 0, 0, 0);
        if (globalTick % 10 == 0) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1f);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.1, 0), 1, 0, 0, 0, 0, dust);
        }
    }
}
