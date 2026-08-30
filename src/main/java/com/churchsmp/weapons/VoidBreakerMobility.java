package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VoidBreaker's own passive: double jump (survival players can't normally
 * fly, so we grant flight just so the "start flying" double-space input
 * fires, then immediately cancel the flight and turn it into a jump boost
 * instead) and full fall-damage immunity while it's held.
 */
public class VoidBreakerMobility implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;

    /** Players we've force-granted flight to, so we know it's safe to revert. */
    private final Set<UUID> grantedFlight = new HashSet<>();
    private final Map<UUID, Long> lastDoubleJump = new HashMap<>();

    public VoidBreakerMobility(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    boolean holding = isHoldingVoidBreaker(player);
                    boolean eligible = player.getGameMode() == GameMode.SURVIVAL
                            || player.getGameMode() == GameMode.ADVENTURE;

                    if (holding && eligible && !player.getAllowFlight()) {
                        player.setAllowFlight(true);
                        grantedFlight.add(player.getUniqueId());
                    } else if (!holding && grantedFlight.remove(player.getUniqueId())) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private boolean isHoldingVoidBreaker(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.VOIDBREAKER;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isHoldingVoidBreaker(player)) return;
        if (!grantedFlight.contains(player.getUniqueId())) return; // wasn't our doing — leave real flight alone

        event.setCancelled(true);
        player.setFlying(false);

        long now = System.currentTimeMillis();
        long last = lastDoubleJump.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 700) return; // small reuse guard so it can't be chained instantly
        lastDoubleJump.put(player.getUniqueId(), now);

        Vector boost = player.getLocation().getDirection().multiply(0.6);
        boost.setY(0.9);
        player.setVelocity(boost);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.3, 0.1, 0.3, 0.03);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.5f);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (isHoldingVoidBreaker(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!isHoldingVoidBreaker(damager)) return;

        event.getEntity().getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                event.getEntity().getLocation().add(0, 1, 0), 1);
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.3f);
    }
}
