package com.churchsmp.shrine;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import com.churchsmp.alignment.PlayerAlignmentData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShrineListener implements Listener {

    private final ChurchSMP plugin;
    private final ShrineManager shrines;
    private final AlignmentManager alignment;

    public ShrineListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.shrines = plugin.getShrineManager();
        this.alignment = plugin.getAlignmentManager();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        ShrineType type = shrines.getType(event.getClickedBlock().getLocation());
        if (type == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        switch (type) {
            case ALTAR -> handleAltar(player);
            case OFFERING -> handleOffering(player);
            case CONFESSION -> handleConfession(player);
        }
    }

    private void handleAltar(Player player) {
        PlayerAlignmentData data = alignment.getData(player);
        int dailyCap = plugin.getConfig().getInt("good-deeds.altar-prayer-daily-cap", 5);
        if (data.getPrayersToday() >= dailyCap) {
            msg(player, "You have already prayed enough for today. Return tomorrow.", NamedTextColor.YELLOW);
            return;
        }
        double points = plugin.getConfig().getDouble("good-deeds.altar-prayer", 1);
        data.incrementPrayersToday();
        alignment.applyDeed(player, points);
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, player.getLocation().add(0, 1.5, 0),
                20, 0.3, 0.5, 0.3, 0.02);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.5f);
        msg(player, "You offer a prayer. (+" + (int) points + " alignment)", NamedTextColor.GREEN);
    }

    private void handleOffering(Player player) {
        OfferingGuiHolder holder = new OfferingGuiHolder();
        Inventory gui = Bukkit.createInventory(holder, 9,
                Component.text("Church Offering", NamedTextColor.GOLD));
        holder.setInventory(gui);
        player.openInventory(gui);
        msg(player, "Place items to tithe them to the Church.", NamedTextColor.AQUA);
    }

    private void handleConfession(Player player) {
        AlignmentTier tier = alignment.getTier(player);
        if (!tier.isEvil()) {
            msg(player, "You have no need for confession.", NamedTextColor.GRAY);
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            msg(player, "Bring an offering in hand to confess and atone.", NamedTextColor.YELLOW);
            return;
        }

        PlayerAlignmentData data = alignment.getData(player);
        int dailyCap = plugin.getConfig().getInt("confession.daily-cap", 30);
        if (data.getTithedToday() >= dailyCap) {
            msg(player, "You have atoned enough for today. Return tomorrow.", NamedTextColor.YELLOW);
            return;
        }

        int amount = held.getAmount();
        double perItem = plugin.getConfig().getDouble("confession.points-per-item", 2);
        int allowed = Math.min(amount, dailyCap - data.getTithedToday());
        double points = allowed * perItem;

        held.setAmount(amount - allowed);
        data.addTithedToday(allowed);
        alignment.applyDeed(player, points);

        player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, player.getLocation().add(0, 1, 0),
                25, 0.4, 0.6, 0.4, 0.02);
        msg(player, "You confess and atone. (+" + (int) points + " alignment)", NamedTextColor.GREEN);
    }

    @EventHandler
    public void onOfferingClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof OfferingGuiHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        PlayerAlignmentData data = alignment.getData(player);
        int dailyCap = plugin.getConfig().getInt("good-deeds.tithe-daily-cap", 40);
        double perItem = plugin.getConfig().getDouble("good-deeds.tithe-per-item", 2);

        int totalItems = 0;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                totalItems += item.getAmount();
            }
        }
        if (totalItems == 0) return;

        int remainingCap = Math.max(0, dailyCap - data.getTithedToday());
        int counted = Math.min(totalItems, remainingCap);
        double points = counted * perItem;

        // Items placed in the offering box are consumed regardless of the cap —
        // they were given to the Church either way.
        event.getInventory().clear();

        if (points <= 0) {
            msg(player, "Your offering is accepted, though today's blessing is spent.", NamedTextColor.YELLOW);
            return;
        }

        data.addTithedToday(counted);
        alignment.applyDeed(player, points);
        msg(player, "Your tithe is accepted. (+" + (int) points + " alignment)", NamedTextColor.GREEN);
    }

    private void msg(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }
}
