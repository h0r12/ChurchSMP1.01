package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grim's three passives: Disgusts (ambient debuff pulse), Soultaking
 * (triggered by pressing the drop-item key — the most natural "throw"
 * input available, and one Bedrock players have too), and Reaper (kill
 * tracking stored on the sword item itself, not the player).
 */
public class GrimPassives implements Listener {

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final NamespacedKey killCountKey;
    private final NamespacedKey soulSwordKey;
    private final NamespacedKey reaperHeartsKey;
    private final Map<UUID, Long> soultakingReadyAt = new HashMap<>();
    private final Map<UUID, ItemStack> thrownItems = new HashMap<>();

    public GrimPassives(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.killCountKey = new NamespacedKey(plugin, "grim_kill_count");
        this.soulSwordKey = new NamespacedKey(plugin, "grim_thrown_sword");
        this.reaperHeartsKey = new NamespacedKey(plugin, "reaper_hearts_bonus");
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    disgustsPulse(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // every 2s, matching the debuff's own duration
    }

    private boolean isHoldingGrim(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return weaponManager.getWeaponType(held) == WeaponType.SCYTHE_OF_CAIN;
    }

    // ============================================================
    // Disgusts
    // ============================================================

    private void disgustsPulse(Player player) {
        if (!isHoldingGrim(player)) return;
        for (Entity e : player.getNearbyEntities(4, 3, 4)) {
            if (e instanceof LivingEntity target && !target.equals(player)) {
                target.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA, 40, 0));
                target.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 40, 0));
            }
        }
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0, 1, 0), 8, 0.6, 0.5, 0.6, 0.02);
    }

    // ============================================================
    // Soultaking — pressing the drop key throws the sword instead
    // ============================================================

    @EventHandler
    public void onDropTriggersThrow(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (weaponManager.getWeaponType(dropped) != WeaponType.SCYTHE_OF_CAIN) return;

        event.setCancelled(true);
        event.getItemDrop().remove();

        // HollowedOut's active window replaces the normal throw entirely.
        if (plugin.getWeaponAbilities().isHollowedOutArmed(player)) {
            plugin.getWeaponAbilities().tryStartHollowedOutCharge(player);
            return;
        }

        UUID id = player.getUniqueId();
        long readyAt = soultakingReadyAt.getOrDefault(id, 0L);
        if (System.currentTimeMillis() < readyAt) {
            long remaining = (readyAt - System.currentTimeMillis()) / 1000;
            player.sendActionBar(Component.text("Soultaking: " + remaining + "s left", NamedTextColor.RED));
            return;
        }
        soultakingReadyAt.put(id, System.currentTimeMillis() + 60_000L);

        ItemStack thrown = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));

        Location eye = player.getEyeLocation();
        var sword = player.getWorld().spawn(eye, org.bukkit.entity.ThrownExpBottle.class, s -> {
            s.setShooter(player);
            s.setVelocity(eye.getDirection().multiply(1.8));
            s.getPersistentDataContainer().set(soulSwordKey, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        thrownItems.put(sword.getUniqueId(), thrown);

        player.getWorld().spawnParticle(Particle.SOUL, eye, 20, 0.2, 0.2, 0.2, 0.05);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
    }

    @EventHandler
    public void onSoultakingHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.ThrownExpBottle sword)) return;
        String throwerId = sword.getPersistentDataContainer().get(soulSwordKey, PersistentDataType.STRING);
        if (throwerId == null) return;
        event.setCancelled(true);

        Player thrower = Bukkit.getPlayer(UUID.fromString(throwerId));
        ItemStack returnedItem = thrownItems.remove(sword.getUniqueId());
        Location landedAt = sword.getLocation();
        sword.remove();

        if (thrower == null) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            target.damage(4, thrower); // steal 2 hearts
            thrower.setHealth(Math.min(thrower.getHealth() + 4,
                    thrower.getAttribute(Attribute.MAX_HEALTH).getValue()));
            target.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 200, 0)); // 10s

            Location behind = target.getLocation().subtract(target.getLocation().getDirection().setY(0).normalize());
            behind.setDirection(thrower.getLocation().getDirection());
            thrower.teleport(behind);
            thrower.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
            thrower.playSound(thrower.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        }

        if (returnedItem != null) {
            var leftovers = thrower.getInventory().addItem(returnedItem);
            leftovers.values().forEach(item -> thrower.getWorld().dropItem(landedAt, item));
        }
    }

    // ============================================================
    // Reaper
    // ============================================================

    /** Kills are tracked on the sword item's own PersistentDataContainer, not the player. */
    @EventHandler
    public void onReaperKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack held = killer.getInventory().getItemInMainHand();
        if (weaponManager.getWeaponType(held) != WeaponType.SCYTHE_OF_CAIN) return;

        ItemMeta meta = held.getItemMeta();
        int kills = meta.getPersistentDataContainer().getOrDefault(killCountKey, PersistentDataType.INTEGER, 0);
        meta.getPersistentDataContainer().set(killCountKey, PersistentDataType.INTEGER, kills + 1);
        held.setItemMeta(meta);
        refreshReaperHearts(killer);

        killer.getWorld().spawnParticle(Particle.SOUL, killer.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.03);
    }

    /** The sword's kill count directly grants max hearts while held — no separate player-side tracking. */
    @EventHandler
    public void onHeldItemChangeUpdatesHearts(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshReaperHearts(event.getPlayer()));
    }

    private void refreshReaperHearts(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(reaperHeartsKey);

        ItemStack held = player.getInventory().getItemInMainHand();
        if (weaponManager.getWeaponType(held) != WeaponType.SCYTHE_OF_CAIN) return;
        ItemMeta meta = held.getItemMeta();
        int kills = meta.getPersistentDataContainer().getOrDefault(killCountKey, PersistentDataType.INTEGER, 0);
        if (kills <= 0) return;

        attr.addModifier(new AttributeModifier(reaperHeartsKey, kills * 2.0, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** Potion effects applied while holding Grim last 20s longer. */
    @EventHandler
    public void onPotionExtend(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) return;
        if (!isHoldingGrim(player)) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;

        PotionEffect original = event.getNewEffect();
        event.setCancelled(true);
        player.addPotionEffect(new PotionEffect(original.getType(), original.getDuration() + 400,
                original.getAmplifier(), original.isAmbient(), original.hasParticles(), original.hasIcon()));
    }

    /** Sneaking while holding Grim shows its stored kill count. */
    @EventHandler
    public void onSneakShowKillCount(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isHoldingGrim(player)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        ItemMeta meta = held.getItemMeta();
        int kills = meta.getPersistentDataContainer().getOrDefault(killCountKey, PersistentDataType.INTEGER, 0);
        player.sendActionBar(Component.text("Grim has reaped " + kills + " souls.", NamedTextColor.DARK_GRAY));
    }

    /** Lets /churchadmin give assign a starting kill count directly onto the sword. */
    public void setKillCount(ItemStack grimItem, int count) {
        ItemMeta meta = grimItem.getItemMeta();
        meta.getPersistentDataContainer().set(killCountKey, PersistentDataType.INTEGER, count);
        grimItem.setItemMeta(meta);
    }
}
