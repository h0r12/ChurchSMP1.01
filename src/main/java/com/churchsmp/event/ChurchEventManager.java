package com.churchsmp.event;

import com.churchsmp.ChurchSMP;
import com.churchsmp.item.RelicItem;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChurchEventManager implements Listener {

    public enum EventType {
        NONE,
        BLOOD_MOON,
        HOLY_INQUISITION
    }

    private final ChurchSMP plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey totemKey;
    private final NamespacedKey celestialMobKey;

    private EventType currentEvent = EventType.NONE;
    private int eventTicksRemaining = 0;
    private BukkitTask loopTask = null;

    private final List<ArmorStand> activeTotems = new ArrayList<>();
    private final Random random = new Random();

    public ChurchEventManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.totemKey = new NamespacedKey(plugin, "is_corrupted_totem");
        this.celestialMobKey = new NamespacedKey(plugin, "is_celestial_mob");

        startAutoScheduler();
    }

    private void startAutoScheduler() {
        // Runs every 4 hours (288,000 ticks). First check in 4 hours.
        long intervalTicks = plugin.getConfig().getLong("events.interval_ticks", 288000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentEvent == EventType.NONE && !Bukkit.getOnlinePlayers().isEmpty()) {
                    // Alternate between Blood Moon and Holy Inquisition
                    if (random.nextBoolean()) {
                        startEvent(EventType.BLOOD_MOON, 20 * 60 * 15); // 15 mins
                    } else {
                        startEvent(EventType.HOLY_INQUISITION, 20 * 60 * 15);
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void startEvent(EventType type, int durationTicks) {
        stopCurrentEvent();
        this.currentEvent = type;
        this.eventTicksRemaining = durationTicks;

        if (type == EventType.BLOOD_MOON) {
            broadcastEventAnnouncement(
                    "<gradient:#8B0000:#FF0000><bold>✦ ᴛʜᴇ ʙʟᴏᴏᴅ ᴍᴏᴏɴ ʜᴀꜱ ᴀᴡᴀᴋᴇɴᴇᴅ ✦</bold></gradient>",
                    "The skies run crimson. Evil souls surge with speed and life-steal. Corrupted Totems emerge!"
            );
            playGlobalSound(Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        } else if (type == EventType.HOLY_INQUISITION) {
            broadcastEventAnnouncement(
                    "<gradient:#FFFFFF:#FFD700><bold>✦ ᴛʜᴇ ʜᴏʟʏ ɪɴǫᴜɪꜱɪᴛɪᴏɴ ʜᴀꜱ ʙᴇɢᴜɴ ✦</bold></gradient>",
                    "Divine pillars descend from the heavens. Smite is empowered. Celestial beasts appear!"
            );
            playGlobalSound(Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.0f);
            playGlobalSound(Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.2f);
        }

        loopTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (eventTicksRemaining <= 0) {
                    stopCurrentEvent();
                    cancel();
                    return;
                }

                eventTicksRemaining -= 10;
                tickEvent();
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void stopCurrentEvent() {
        if (loopTask != null) {
            loopTask.cancel();
            loopTask = null;
        }

        // Clean up spawned totems
        for (ArmorStand totem : activeTotems) {
            if (totem.isValid()) totem.remove();
        }
        activeTotems.clear();

        if (currentEvent != EventType.NONE) {
            broadcastEventAnnouncement(
                    "<gold>✦ <white><bold>" + TextUtil.toSmallCaps("The Celestial Event has subsided.") + "</bold></white></gold>",
                    "The atmospheric equilibrium has been restored."
            );
        }
        this.currentEvent = EventType.NONE;
    }

    private void tickEvent() {
        if (currentEvent == EventType.BLOOD_MOON) {
            // Crimson ash particles and Speed buff for Evil players
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location loc = p.getLocation().add(0, 1.0, 0);
                p.getWorld().spawnParticle(Particle.DUST, loc, 12, 1.5, 1.0, 1.5, 0,
                        new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.6f));

                if (plugin.getAlignmentManager().getAlignmentScore(p) < 0) {
                    // Evil players gain +20% movement speed (Speed I)
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                }
            }

            // Spawn Corrupted Totems periodically (cap at 6 active totems)
            if (activeTotems.size() < 6 && random.nextInt(40) == 0 && !Bukkit.getOnlinePlayers().isEmpty()) {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                Player randomP = players.get(random.nextInt(players.size()));
                spawnCorruptedTotemNear(randomP);
            }

        } else if (currentEvent == EventType.HOLY_INQUISITION) {
            // Holy light pillars and glowing celestial mobs
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (random.nextInt(15) == 0) {
                    Location beamLoc = p.getLocation().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                    Location ground = p.getWorld().getHighestBlockAt(beamLoc).getLocation();
                    // Pillar of holy light
                    for (double y = 0; y <= 25.0; y += 1.0) {
                        ground.getWorld().spawnParticle(Particle.DUST, ground.clone().add(0, y, 0), 2, 0.2, 0.1, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 255, 200), 1.5f));
                        ground.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, ground.clone().add(0, y, 0), 1, 0.1, 0.1, 0.1, 0.02);
                    }
                }

                // Tag nearby hostile mobs as Celestial Mobs
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 20, 10, 20)) {
                    if (e instanceof Monster m && !m.getPersistentDataContainer().has(celestialMobKey, PersistentDataType.BOOLEAN)) {
                        m.getPersistentDataContainer().set(celestialMobKey, PersistentDataType.BOOLEAN, true);
                        m.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 6000, 0, false, false));
                        m.customName(miniMessage.deserialize("<gradient:#FFFFFF:#FFD700><bold>✦ ᴄᴇʟᴇꜱᴛɪᴀʟ ʙᴇᴀꜱᴛ ✦</bold></gradient>"));
                        m.setCustomNameVisible(true);
                    }
                }
            }
        }
    }

    private void spawnCorruptedTotemNear(Player player) {
        Location spawnLoc = player.getLocation().add(random.nextInt(30) - 15, 0, random.nextInt(30) - 15);
        Location highest = player.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0.5, 0, 0.5);

        ArmorStand totem = (ArmorStand) highest.getWorld().spawnEntity(highest, EntityType.ARMOR_STAND);
        totem.setVisible(false);
        totem.setGravity(false);
        totem.setInvulnerable(false);
        totem.setCustomNameVisible(true);
        totem.customName(miniMessage.deserialize("<gradient:#8B0000:#FF0000><bold>✦ ᴄᴏʀʀᴜᴘᴛᴇᴅ ᴛᴏᴛᴇᴍ ✦ (ᴄʟɪᴄᴋ ᴛᴏ ᴄʟᴀɪᴍ)</bold></gradient>"));
        totem.getPersistentDataContainer().set(totemKey, PersistentDataType.BOOLEAN, true);

        // Put crying obsidian on head
        totem.getEquipment().setHelmet(new ItemStack(Material.CRYING_OBSIDIAN));

        activeTotems.add(totem);
        highest.getWorld().playSound(highest, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        player.sendMessage(miniMessage.deserialize("<gradient:#FF4500:#8B0000><bold>✦ A Corrupted Totem has manifested nearby!</bold></gradient>"));
    }

    @EventHandler
    public void onTotemInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand totem)) return;
        if (!totem.getPersistentDataContainer().has(totemKey, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc = totem.getLocation();

        activeTotems.remove(totem);
        totem.remove();

        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.5f, 1.2f);
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.add(0, 1, 0), 1);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 50, 0.5, 0.8, 0.5, 0.1);

        // Drop unholy relic materials (Netherite Scrap & Crying Obsidian)
        loc.getWorld().dropItem(loc, new ItemStack(Material.NETHERITE_SCRAP, 1));
        loc.getWorld().dropItem(loc, new ItemStack(Material.CRYING_OBSIDIAN, 2));

        // 35% chance to drop a Liminal Path Item (Iniquity, Impiety, Obscura)
        if (random.nextDouble() < 0.35) {
            RelicItem.RelicType[] relics = RelicItem.RelicType.values();
            RelicItem.RelicType dropRelic = relics[random.nextInt(relics.length)];
            loc.getWorld().dropItem(loc, RelicItem.createRelic(plugin, dropRelic));
            player.sendMessage(miniMessage.deserialize("<gold>✦ <yellow><bold>The shattered totem released a " + dropRelic.getDisplayName() + "<yellow><bold>!</bold></yellow></gold>"));
        }

        player.sendMessage(miniMessage.deserialize("<gradient:#FF4500:#8B0000><bold>✦ You shattered the Corrupted Totem and purged its dark energies!</bold></gradient>"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLifestealAndSmite(EntityDamageByEntityEvent event) {
        if (currentEvent == EventType.BLOOD_MOON) {
            // Evil players gain 15% life-steal
            if (event.getDamager() instanceof Player attacker) {
                if (plugin.getAlignmentManager().getAlignmentScore(attacker) < 0) {
                    double heal = Math.min(6.0, event.getFinalDamage() * 0.15);
                    if (heal > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
                        attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
                        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.2, 0), 2, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        } else if (currentEvent == EventType.HOLY_INQUISITION) {
            // Smite damage is amplified (+50%)
            if (event.getDamager() instanceof Player attacker) {
                ItemStack hand = attacker.getInventory().getItemInMainHand();
                if (hand.containsEnchantment(org.bukkit.enchantments.Enchantment.SMITE)) {
                    event.setDamage(event.getDamage() * 1.5);
                }
            }
        }
    }

    @EventHandler
    public void onCelestialMobDeath(EntityDeathEvent event) {
        if (currentEvent != EventType.HOLY_INQUISITION) return;
        LivingEntity entity = event.getEntity();
        if (!entity.getPersistentDataContainer().has(celestialMobKey, PersistentDataType.BOOLEAN)) return;

        Location loc = entity.getLocation();
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.6f);
        loc.getWorld().spawnParticle(Particle.FLASH, loc.add(0, 1, 0), 1, Color.WHITE);

        // Drops celestial materials (Gold Block and Amethyst Shards)
        event.getDrops().add(new ItemStack(Material.GOLD_BLOCK, 1));
        event.getDrops().add(new ItemStack(Material.AMETHYST_SHARD, 3));

        // 25% chance to drop a Liminal Path Item (Iniquity, Impiety, Obscura)
        if (random.nextDouble() < 0.25) {
            RelicItem.RelicType[] relics = RelicItem.RelicType.values();
            RelicItem.RelicType dropRelic = relics[random.nextInt(relics.length)];
            event.getDrops().add(RelicItem.createRelic(plugin, dropRelic));
            if (entity.getKiller() != null) {
                entity.getKiller().sendMessage(miniMessage.deserialize("<gold>✦ <yellow><bold>The Celestial Beast dropped a " + dropRelic.getDisplayName() + "<yellow><bold>!</bold></yellow></gold>"));
            }
        }
    }

    private void broadcastEventAnnouncement(String header, String detail) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(miniMessage.deserialize(header));
            p.sendMessage(Component.text("  " + detail, NamedTextColor.GRAY));
        }
    }

    private void playGlobalSound(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public EventType getCurrentEvent() { return currentEvent; }
}
