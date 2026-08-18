package com.churchsmp.sermon;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A sermon is a timed gathering: whoever is still within range of the
 * starting location when the timer ends receives the sermon-attendance
 * deed. Players who leave the radius partway through are dropped from
 * the attendee set (checked every 5 seconds while the sermon runs).
 */
public class SermonManager {

    private final ChurchSMP plugin;
    private boolean active = false;
    private Location location;
    private double radius;
    private Set<UUID> currentAttendees;
    private BukkitTask task;

    public SermonManager(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public boolean start(Player preacher, int durationSeconds, double radius) {
        if (active) return false;

        this.active = true;
        this.location = preacher.getLocation();
        this.radius = radius;
        this.currentAttendees = new HashSet<>();
        refreshAttendees();

        Bukkit.broadcast(Component.text(preacher.getName() + " begins a sermon! Gather nearby to listen.",
                NamedTextColor.GOLD));

        int checkIntervalTicks = 100; // 5s
        int totalTicks = durationSeconds * 20;

        task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += checkIntervalTicks;
                refreshAttendees();
                if (elapsed >= totalTicks) {
                    finish();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, checkIntervalTicks, checkIntervalTicks);

        return true;
    }

    private void refreshAttendees() {
        Set<UUID> stillPresent = new HashSet<>();
        for (Player p : location.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(location) <= radius * radius) {
                if (currentAttendees.isEmpty() || currentAttendees.contains(p.getUniqueId())) {
                    stillPresent.add(p.getUniqueId());
                }
            }
        }
        // On the very first pass currentAttendees is empty, so seed it with whoever is present now.
        currentAttendees = currentAttendees.isEmpty() ? stillPresent : intersect(currentAttendees, stillPresent);
    }

    private Set<UUID> intersect(Set<UUID> a, Set<UUID> b) {
        Set<UUID> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private void finish() {
        double points = plugin.getConfig().getDouble("good-deeds.sermon-attendance", 5);
        for (UUID id : currentAttendees) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.getAlignmentManager().applyDeed(p, points);
                p.sendMessage(Component.text("You stayed for the whole sermon. (+" + (int) points + " alignment)",
                        NamedTextColor.GREEN));
            }
        }
        Bukkit.broadcast(Component.text("The sermon has ended. " + currentAttendees.size()
                + " stayed to the end.", NamedTextColor.GOLD));
        active = false;
    }

    public void cancelEarly() {
        if (task != null) task.cancel();
        active = false;
    }
}
