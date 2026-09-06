package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** VoidBreaker's three passives: Voidfeels, Cloud, and Fallbreak. */
public class VoidbreakerPassives implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final Map<UUID, Long> lastDoubleJump = new HashMap<>();
    private final Set<UUID> grantedFlight = new HashSet<>();

    public VoidbreakerPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    private boolean isHoldingVoidbreaker(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.VOIDBREAKER;
    }

    /**
     * Cloud's double jump only works because the client sends a
     * flight-toggle request on double-space — but only if allowFlight is
     * already true server-side. Survival/adventure players holding
     * VoidBreaker get that granted here (and revoked the moment they let
     * go), purely so the double-jump packet fires; creative/spectator
     * players already have real flight and are left alone entirely.
     */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

                    boolean holding = isHoldingVoidbreaker(player);
                    if (holding && !grantedFlight.contains(player.getUniqueId())) {
                        grantedFlight.add(player.getUniqueId());
                        player.setAllowFlight(true);
                    } else if (!holding && grantedFlight.remove(player.getUniqueId())) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    /** Passive 1, Voidfeels: wind charges thrown while holding VoidBreaker fly at 2x velocity. */
    @EventHandler
    public void onWindChargeLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof WindCharge charge)) return;
        if (!(charge.getShooter() instanceof Player player)) return;
        if (!isHoldingVoidbreaker(player)) return;

        charge.setVelocity(charge.getVelocity().multiply(2.0));
    }

    /**
     * Passive 2, Cloud: a double jump on a 5s cooldown, achieved by
     * intercepting the flight-toggle request the client sends on
     * double-space (see start() for why allowFlight has to be granted
     * first) and replacing it with an upward boost instead of real flight.
     */
    @EventHandler
    public void onDoubleJumpAttempt(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!isHoldingVoidbreaker(player)) return;

        event.setCancelled(true);
        player.setFlying(false);

        long now = System.currentTimeMillis();
        long last = lastDoubleJump.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 5_000L) return;
        lastDoubleJump.put(player.getUniqueId(), now);

        var velocity = player.getVelocity();
        player.setVelocity(new org.bukkit.util.Vector(velocity.getX(), 0.9, velocity.getZ()));
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
    }

    /** Passive 3, Fallbreak: no fall damage while holding VoidBreaker. */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!isHoldingVoidbreaker(player)) return;

        event.setCancelled(true);
    }
}
