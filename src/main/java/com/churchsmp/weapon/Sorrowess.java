package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Sorrowess extends LegendaryWeapon {

    // Mirror clones: Player UUID -> List of active Phantom clones
    private final Map<UUID, List<Phantom>> activeClones = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // PDC key to identify Sorrowess clones
    private final NamespacedKey cloneKey;

    public Sorrowess(ChurchSMP plugin) {
        super(plugin,
                "sorrowess",
                new String[]{"blade_of_sorrow"},
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><gradient:#FFFFFF:#FF7F7F:#8B0000><bold>Sorrowess</bold></gradient>"),
                Material.TRIDENT,
                Alignment.EVIL,
                "Grief Shards",
                "Bloody Rain");
        this.cloneKey = new NamespacedKey(plugin, "sorrowess_clone");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Forming", "Brave"), "Grief Shards", "Bloody Rain"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            meta.addEnchant(Enchantment.RIPTIDE, 7, true);
            meta.setCustomModelData(1005);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        LivingEntity target = null;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), 16.0)) {
            if (e.equals(player)) continue;
            Vector toE = e.getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            if (player.getEyeLocation().getDirection().dot(toE) > 0.8) {
                target = e;
                break;
            }
        }

        if (target == null) {
            player.sendMessage(Component.text("No entity in sight for Grief Shards!", NamedTextColor.RED));
            return false;
        }

        int cd = plugin.getConfig().getInt("weapons.sorrowess.primary_cooldown", 30);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_HURT, 1.2f, 0.5f);

        Material[] whiteItems = new Material[]{
                Material.SUGAR, Material.QUARTZ, Material.FEATHER, Material.SNOWBALL, Material.WHITE_WOOL
        };

        final LivingEntity finalTarget = target;
        Location spawnLoc = player.getEyeLocation();

        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(147, 50, 180), 1.3f);
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.3f);

        for (int i = 0; i < 5; i++) {
            Material whiteMat = whiteItems[random.nextInt(whiteItems.length)];
            ItemStack whiteItem = new ItemStack(whiteMat);
            org.bukkit.entity.Item itemEntity = player.getWorld().dropItem(spawnLoc, whiteItem);
            itemEntity.setPickupDelay(99999);
            final int index = i;

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (!itemEntity.isValid() || !finalTarget.isValid() || ticks > 35) {
                        itemEntity.remove();
                        cancel();
                        return;
                    }

                    Location itemLoc = itemEntity.getLocation();
                    Particle.DustOptions trailDust = (ticks % 2 == 0) ? purpleDust : redDust;
                    itemLoc.getWorld().spawnParticle(Particle.DUST, itemLoc, 2, 0.05, 0.05, 0.05, 0, trailDust);
                    itemLoc.getWorld().spawnParticle(Particle.END_ROD, itemLoc, 1, 0.02, 0.02, 0.02, 0.01);

                    Vector dir = finalTarget.getLocation().add(0, 1.0, 0).toVector().subtract(itemEntity.getLocation().toVector()).normalize().multiply(1.2);
                    itemEntity.setVelocity(dir);

                    if (itemEntity.getLocation().distance(finalTarget.getLocation().add(0, 1.0, 0)) < 1.5) {
                        Location hitLoc = finalTarget.getLocation().add(0, 1.0, 0);
                        hitLoc.getWorld().spawnParticle(Particle.FLASH, hitLoc, 1, Color.WHITE);
                        hitLoc.getWorld().spawnParticle(Particle.DUST, hitLoc, 8, 0.3, 0.3, 0.3, 0, redDust);

                        itemEntity.remove();
                        // Total damage capped to 2.5 hearts (5.0 HP) -> 1.0 HP per shard
                        double newHp = finalTarget.getHealth() - 1.0;
                        if (newHp <= 0) {
                            finalTarget.setHealth(0);
                            finalTarget.damage(1.0, player);
                        } else {
                            finalTarget.setHealth(newHp);
                            finalTarget.damage(0.01, player);
                        }

                        if (index == 4) {
                            startBleedout(finalTarget, player);
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, i * 2L, 1L);
        }

        return true;
    }

    private void startBleedout(LivingEntity target, Player attacker) {
        if (target instanceof Player tp) {
            tp.sendMessage(Component.text("⚔ You are hemorrhaging from Bleedout!", NamedTextColor.RED));
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.8f);

        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.2f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || ticks > 4) {
                    cancel();
                    return;
                }
                target.damage(0.5, attacker);
                Location loc = target.getLocation().add(0, 1.0, 0);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 2);
            }
        }.runTaskTimer(plugin, 15L, 15L);
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.sorrowess.secondary_cooldown", 100);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getCooldownManager().setActiveDuration(player, key, 20);
        plugin.getBossBarManager().showActiveCountdown(player, "Bloody Rain Arena", BossBar.Color.PURPLE, 20);

        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, 1.5f, 0.8f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.2f, 1.0f);

        Location arenaCenter = player.getLocation().clone();
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(200, 0, 20), 1.4f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f);
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.2f);

        // Spawn 4 illusion clones in 4 cardinal directions
        List<Phantom> clones = activeClones.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        for (Phantom old : clones) {
            if (old.isValid()) old.remove();
        }
        clones.clear();

        double[] angles = {0, 90, 180, 270};
        for (double deg : angles) {
            double rad = Math.toRadians(deg);
            Vector dir = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize().multiply(0.16);
            Location spawn = arenaCenter.clone().add(Math.cos(rad) * 2.0, 0, Math.sin(rad) * 2.0);
            spawnClone(player, spawn, dir, clones);
        }

        // Arena loop: 20s with rain effects and boundary
        new BukkitRunnable() {
            int ticks = 200;

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    for (Phantom c : clones) {
                        if (c.isValid()) c.remove();
                    }
                    clones.clear();
                    cancel();
                    return;
                }

                // White "sun" orb at top of arena
                Location sunLoc = arenaCenter.clone().add(0, 6.5, 0);
                sunLoc.getWorld().spawnParticle(Particle.END_ROD, sunLoc, 4, 0.4, 0.4, 0.4, 0.02);
                sunLoc.getWorld().spawnParticle(Particle.DUST, sunLoc, 6, 0.5, 0.5, 0.5, 0, whiteDust);

                // 12×12 boundary on ground
                for (int d = -6; d <= 6; d += 3) {
                    arenaCenter.getWorld().spawnParticle(Particle.DUST, arenaCenter.clone().add(d, 0.1, 6), 1, 0, 0, 0, 0, purpleDust);
                    arenaCenter.getWorld().spawnParticle(Particle.DUST, arenaCenter.clone().add(d, 0.1, -6), 1, 0, 0, 0, 0, purpleDust);
                    arenaCenter.getWorld().spawnParticle(Particle.DUST, arenaCenter.clone().add(6, 0.1, d), 1, 0, 0, 0, 0, purpleDust);
                    arenaCenter.getWorld().spawnParticle(Particle.DUST, arenaCenter.clone().add(-6, 0.1, d), 1, 0, 0, 0, 0, purpleDust);
                }

                // Raining red particles
                for (int r = 0; r < 8; r++) {
                    double rx = (random.nextDouble() - 0.5) * 12.0;
                    double rz = (random.nextDouble() - 0.5) * 12.0;
                    Location rainLoc = arenaCenter.clone().add(rx, 5.5, rz);
                    rainLoc.getWorld().spawnParticle(Particle.DUST, rainLoc, 2, 0.2, 0.2, 0.2, 0, redDust);
                    rainLoc.getWorld().spawnParticle(Particle.DRIPPING_WATER, rainLoc, 1);
                }

                ticks -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        player.sendMessage(Component.text("✦ Bloody Rain! 4 illusion clones summoned in 12×12 arena.", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    // Spawns a Phantom clone that walks along the given direction path
    private void spawnClone(Player owner, Location loc, Vector dir, List<Phantom> cloneList) {
        if (cloneList.size() >= 16) return; // hard cap

        Phantom clone = loc.getWorld().spawn(loc, Phantom.class, ph -> {
            ph.setSize(0); // smallest phantom
            ph.setCustomNameVisible(true);
            ph.customName(owner.name());
            ph.setSilent(true);
            ph.getPersistentDataContainer().set(cloneKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            ph.setRemoveWhenFarAway(false);
            ph.setAI(false); // we control movement manually
        });
        cloneList.add(clone);

        // Movement task: walks in direction, bounces off walls
        new BukkitRunnable() {
            int lifeTicks = 0;
            Vector currentDir = dir.clone();

            @Override
            public void run() {
                lifeTicks += 2;
                if (!clone.isValid() || clone.isDead() || lifeTicks > 400) {
                    if (clone.isValid()) clone.remove();
                    cloneList.remove(clone);
                    cancel();
                    return;
                }

                Location cLoc = clone.getLocation();
                Location next = cLoc.clone().add(currentDir);

                if (next.getBlock().getType().isSolid()) {
                    currentDir.multiply(-1).rotateAroundY(Math.toRadians(45));
                } else {
                    clone.teleport(next);
                }

                // Footprint trail particles
                cLoc.getWorld().spawnParticle(Particle.DUST, cLoc.clone().add(0, 0.1, 0), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 50), 1.0f));
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    // Called from CombatListener when a clone Phantom is attacked
    public void multiplyClone(Phantom hitClone, LivingEntity attacker) {
        String ownerStr = hitClone.getPersistentDataContainer().get(cloneKey, PersistentDataType.STRING);
        if (ownerStr == null) return;
        UUID ownerId = UUID.fromString(ownerStr);
        Player owner = plugin.getServer().getPlayer(ownerId);
        List<Phantom> clones = activeClones.get(ownerId);
        if (clones == null) return;

        Location hitLoc = hitClone.getLocation();
        hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 1.2f);
        hitLoc.getWorld().spawnParticle(Particle.FLASH, hitLoc.clone().add(0, 1, 0), 1, Color.WHITE);
        hitLoc.getWorld().spawnParticle(Particle.DUST, hitLoc.clone().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.5f));

        hitClone.remove();
        clones.remove(hitClone);

        // Weakness on attacker
        if (attacker != null) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
        }

        // Multiply: spawn 4 new clones in cardinal directions if under limit (max 16)
        if (owner != null && owner.isOnline() && clones.size() < 16) {
            double[] dirs = {0, 90, 180, 270};
            for (double deg : dirs) {
                double rad = Math.toRadians(deg);
                Vector v = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize().multiply(0.16);
                Location spawn = hitLoc.clone().add(Math.cos(rad) * 0.8, 0, Math.sin(rad) * 0.8);
                spawnClone(owner, spawn, v, clones);
            }
        }
    }

    // Returns the cloneKey for use in CombatListener
    public NamespacedKey getCloneKey() {
        return cloneKey;
    }

    public static void checkInventoryHearts(Player player, ChurchSMP plugin) {
        boolean hasSorrowess = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                String wid = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING);
                if ("sorrowess".equals(wid)) {
                    hasSorrowess = true;
                    break;
                }
            }
        }

        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            if (hasSorrowess) {
                if (attr.getBaseValue() < 24.0) {
                    attr.setBaseValue(24.0); // +2 hearts (12 hearts total)
                }
            } else {
                if (attr.getBaseValue() == 24.0) {
                    attr.setBaseValue(20.0);
                }
            }
        }
    }

    @Override
    public void onCrouch(Player player, boolean isSneaking) {
        if (!isSneaking) return;
        // Forming: Crouching summons non-flowing water + ripple ring (20s revert)
        Block block = player.getLocation().getBlock();
        if (block.getType() == Material.AIR) {
            block.setType(Material.WATER);

            Particle.DustOptions waterRing = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.5f);
            Location loc = player.getLocation();
            for (int d = 0; d < 360; d += 25) {
                double rad = Math.toRadians(d);
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(rad) * 1.2, 0.1, Math.sin(rad) * 1.2), 1, 0, 0, 0, 0, waterRing);
            }

            // Revert after 3s (was 60L = 3s, kept the same)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType() == Material.WATER) {
                        block.setType(Material.AIR);
                    }
                }
            }.runTaskLater(plugin, 60L);
        }
    }
}
