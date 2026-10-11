package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlignmentListener implements Listener {

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Long> actionCooldowns = new ConcurrentHashMap<>();

    public AlignmentListener(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    private boolean checkCooldown(UUID playerUuid, String actionKey, long cooldownMillis) {
        String key = playerUuid.toString() + ":" + actionKey;
        long now = System.currentTimeMillis();
        Long last = actionCooldowns.get(key);
        if (last != null && (now - last) < cooldownMillis) {
            return false;
        }
        actionCooldowns.put(key, now);
        return true;
    }

    // 1. Entity Slain
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        int score = plugin.getAlignmentManager().getAlignmentScore(killer);

        // Evil actions: Slaying innocent, passive, guardian, or tamed companions
        if (entity instanceof Villager) {
            shiftAlignment(killer, score, -5, "Slain Villager", false);
        } else if (entity instanceof IronGolem) {
            shiftAlignment(killer, score, -8, "Slain Village Protector", false);
        } else if (entity instanceof Tameable t && t.isTamed()) {
            shiftAlignment(killer, score, -10, "Slain Bonded Familiar", false);
        } else if (entity instanceof Animals || entity instanceof Fish || entity instanceof Allay) {
            shiftAlignment(killer, score, -1, "Slain Peaceful Creature", false);
        }
        // Good actions: Raid invaders, Dark Cataclysms, or Hostile Monsters
        else if (entity instanceof Raider) {
            shiftAlignment(killer, score, +3, "Repelled Raid Invader", true);
        } else if (entity instanceof Wither || entity instanceof Warden || entity instanceof EnderDragon) {
            shiftAlignment(killer, score, +15, "Vanquished Dark Cataclysm", true);
        } else if (entity instanceof Monster) {
            shiftAlignment(killer, score, +1, "Slain Unholy Monster", true);
        }
    }

    // 2. Assaulting Innocents / Friendly Companions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity target = event.getEntity();

        int score = plugin.getAlignmentManager().getAlignmentScore(player);

        if (target instanceof Villager) {
            if (checkCooldown(player.getUniqueId(), "hit_villager_" + target.getEntityId(), 2500L)) {
                shiftAlignment(player, score, -2, "Assaulted Innocent", false);
            }
        } else if (target instanceof Tameable t && t.isTamed()) {
            if (checkCooldown(player.getUniqueId(), "hit_tamed_" + target.getEntityId(), 2500L)) {
                shiftAlignment(player, score, -3, "Harm to Familiar", false);
            }
        }
    }

    // 3. Breeding Animals (Nurturing Life)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            int score = plugin.getAlignmentManager().getAlignmentScore(player);
            shiftAlignment(player, score, +1, "Nurtured Life", true);
        }
    }

    // 4. Taming Wild Beasts
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            int score = plugin.getAlignmentManager().getAlignmentScore(player);
            shiftAlignment(player, score, +2, "Befriended Wild Beast", true);
        }
    }

    // 5. Curing Zombie Villagers
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCureZombieVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ZombieVillager zv)) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());

        if (hand != null && hand.getType() == Material.GOLDEN_APPLE && zv.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            if (checkCooldown(player.getUniqueId(), "cure_zv_" + zv.getEntityId(), 5000L)) {
                int score = plugin.getAlignmentManager().getAlignmentScore(player);
                shiftAlignment(player, score, +15, "Administered Sacred Cure", true);
            }
        }
    }

    // 6. Inciting Unholy Raids
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            int score = plugin.getAlignmentManager().getAlignmentScore(player);
            shiftAlignment(player, score, -10, "Incited Unholy Raid", false);
        }
    }

    // 7. Victorious Raid Defense
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaidFinish(RaidFinishEvent event) {
        for (Player winner : event.getWinners()) {
            int score = plugin.getAlignmentManager().getAlignmentScore(winner);
            shiftAlignment(winner, score, +15, "Defended Village Sanctuary", true);
        }
    }

    // 8. Desecrating Bells
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BELL) {
            Player player = event.getPlayer();
            int score = plugin.getAlignmentManager().getAlignmentScore(player);
            shiftAlignment(player, score, -5, "Desecrated Sacred Bell", false);
        }
    }

    // 9. Cultivating the Earth (Saplings, Crops, Flowers)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material mat = event.getBlockPlaced().getType();
        boolean isCultivation = mat.name().contains("SAPLING")
                || mat.name().contains("SEEDS")
                || mat == Material.CARROTS
                || mat == Material.POTATOES
                || mat.name().contains("FLOWER")
                || mat == Material.SWEET_BERRY_BUSH;

        if (isCultivation) {
            Player player = event.getPlayer();
            if (checkCooldown(player.getUniqueId(), "plant", 4000L)) {
                int score = plugin.getAlignmentManager().getAlignmentScore(player);
                shiftAlignment(player, score, +1, "Cultivated the Earth", true);
            }
        }
    }

    // 10. Sounding Sacred Chime (Ringing Village Bell)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBellRing(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.BELL) {
                Player player = event.getPlayer();
                if (checkCooldown(player.getUniqueId(), "ring_bell", 10000L)) {
                    int score = plugin.getAlignmentManager().getAlignmentScore(player);
                    shiftAlignment(player, score, +1, "Sounded Sacred Chime", true);
                }
            }
        }
    }

    // 11. Consuming Sinister & Corrupted Substances
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Material mat = item.getType();
        Player player = event.getPlayer();
        int score = plugin.getAlignmentManager().getAlignmentScore(player);

        if (mat == Material.ROTTEN_FLESH || mat == Material.SPIDER_EYE || mat == Material.POISONOUS_POTATO) {
            shiftAlignment(player, score, -2, "Consumed Corrupted Flesh", false);
        } else if (mat.name().contains("OMINOUS_BOTTLE")) {
            shiftAlignment(player, score, -5, "Consumed Ominous Curse", false);
        }
    }

    private void shiftAlignment(Player player, int currentScore, int delta, String reason, boolean isGood) {
        int newScore = Math.max(-100, Math.min(100, currentScore + delta));
        if (newScore == currentScore) return;

        plugin.getAlignmentManager().setAlignmentScore(player, newScore, false);

        String sign = delta > 0 ? "+" + delta : String.valueOf(delta);
        String label = isGood ? TextUtil.toSmallCaps("Holiness") : TextUtil.toSmallCaps("Depravity");
        String alignTag = newScore > 0
                ? "<yellow><bold>GOOD (+" + newScore + ")</bold></yellow>"
                : (newScore < 0 ? "<red><bold>EVIL (" + newScore + ")</bold></red>" : "<aqua><bold>NEUTRAL (0)</bold></aqua>");

        // Chat notification
        if (isGood) {
            player.sendMessage(miniMessage.deserialize(
                    "<gold>✦ <gradient:#FFD700:#FFFFFF><bold>" + TextUtil.toSmallCaps("Holiness Shift") + "</bold></gradient> " +
                    "<dark_gray>»</dark_gray> <green><bold>" + sign + "</bold> Alignment</green> <gray>(" + reason + ")</gray> " +
                    "<dark_gray>•</dark_gray> <gray>Soul:</gray> " + alignTag + "</gold>"
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        } else {
            player.sendMessage(miniMessage.deserialize(
                    "<dark_red>✦ <gradient:#8B0000:#FF0000><bold>" + TextUtil.toSmallCaps("Depravity Shift") + "</bold></gradient> " +
                    "<dark_gray>»</dark_gray> <red><bold>" + sign + "</bold> Alignment</red> <gray>(" + reason + ")</gray> " +
                    "<dark_gray>•</dark_gray> <gray>Soul:</gray> " + alignTag + "</dark_red>"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.7f, 0.8f);
        }

        // Real-time Action Bar Feedback
        player.sendActionBar(miniMessage.deserialize(
                (isGood ? "<green>" : "<red>") + "<bold>" + sign + " " + label + " (" + TextUtil.toSmallCaps(reason) + ") | " + TextUtil.toSmallCaps("Score") + ": " + newScore + "</bold>"
        ));

        // Milestone alerts
        if (newScore == 100 && currentScore < 100) {
            player.sendMessage(miniMessage.deserialize(
                    "<gold>══════════════════════════════════════════════════\n" +
                    "  <gradient:#FFF8DC:#FFD700><bold>✦ " + TextUtil.toSmallCaps("Pure Holiness Achieved (+100)") + " ✦</bold></gradient>\n" +
                    "  <gray>The heavens smile upon you. Smite damage, potion boosts, and EXP are maximized!</gray>\n" +
                    "══════════════════════════════════════════════════</gold>"
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
        } else if (newScore == -100 && currentScore > -100) {
            player.sendMessage(miniMessage.deserialize(
                    "<dark_red>══════════════════════════════════════════════════\n" +
                    "  <gradient:#4B0000:#FF0000><bold>✦ " + TextUtil.toSmallCaps("Pure Depravity Achieved (-100)") + " ✦</bold></gradient>\n" +
                    "  <gray>Your soul has fallen to the abyss. Dark relics unleash their utmost malice!</gray>\n" +
                    "══════════════════════════════════════════════════</dark_red>"
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        }
    }
}
