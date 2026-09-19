package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
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

    // Mirror clones: Player UUID -> List of active ArmorStand clones
    private final Map<UUID, List<ArmorStand>> activeClones = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Sorrowess(ChurchSMP plugin) {
        super(plugin,
                "sorrowess",
                new String[]{"blade_of_sorrow"},
                Component.text("Sorrowess", TextColor.color(0x4B0082)).decorate(TextDecoration.BOLD),
                Material.TRIDENT,
                Alignment.EVIL,
                "Grief Shards",
                "Bloody Rain");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("Its-a-sorrowy day...", TextColor.color(0x9370DB)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("âœ¦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" â€¢ Forming: ", NamedTextColor.GRAY).append(Component.text("Crouching summons a temporary non-flowing water pool.", NamedTextColor.WHITE)));
            lore.add(Component.text(" â€¢ Brave: ", NamedTextColor.GRAY).append(Component.text("Increases max health to 12 hearts while held; reverts when dropped.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Grief Shards: ", NamedTextColor.DARK_PURPLE).append(Component.text("Launches 5 homing white sorrow shards dealing 2.5 True Damage + Bleedout DOT. (30s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Bloody Rain: ", NamedTextColor.DARK_PURPLE).append(Component.text("8x8 sorrow cloud spawns up to 3 mirrored clones when struck that weaken attackers. (20s active, 100s CD)", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_PURPLE));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        // Target in line of sight
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

        // Summon 5 random white items (sugar, quartz, feather, snowballs, white wool)
        Material[] whiteItems = new Material[]{
                Material.SUGAR, Material.QUARTZ, Material.FEATHER, Material.SNOWBALL, Material.WHITE_WOOL
        };

        final LivingEntity finalTarget = target;
        Location spawnLoc = player.getEyeLocation();

        Particle.DustOptions sorrowDust = new Particle.DustOptions(Color.fromRGB(147, 112, 219), 1.4f);

        for (int i = 0; i < 5; i++) {
            Material whiteMat = whiteItems[random.nextInt(whiteItems.length)];
            ItemStack whiteItem = new ItemStack(whiteMat);
            Item itemEntity = player.getWorld().dropItem(spawnLoc, whiteItem);
            itemEntity.setPickupDelay(99999);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (!itemEntity.isValid() || !finalTarget.isValid() || ticks > 30) {
                        itemEntity.remove();
                        cancel();
                        return;
                    }

                    // White and purple sorrow trail behind each shard
                    Location itemLoc = itemEntity.getLocation();
                    itemLoc.getWorld().spawnParticle(Particle.END_ROD, itemLoc, 1, 0.05, 0.05, 0.05, 0.01);
                    itemLoc.getWorld().spawnParticle(Particle.DUST, itemLoc, 2, 0.05, 0.05, 0.05, 0, sorrowDust);

                    Vector dir = finalTarget.getLocation().add(0, 1.0, 0).toVector().subtract(itemEntity.getLocation().toVector()).normalize().multiply(1.2);
                    itemEntity.setVelocity(dir);

                    if (itemEntity.getLocation().distance(finalTarget.getLocation().add(0, 1.0, 0)) < 1.5) {
                        Location hitLoc = finalTarget.getLocation().add(0, 1.0, 0);
                        hitLoc.getWorld().spawnParticle(Particle.FLASH, hitLoc, 1);
                        hitLoc.getWorld().spawnParticle(Particle.SOUL, hitLoc, 8, 0.3, 0.3, 0.3, 0.05);

                        itemEntity.remove();
                        // 2.5 True Damage (5 HP)
                        double newHp = finalTarget.getHealth() - 5.0;
                        if (newHp <= 0) {
                            finalTarget.setHealth(0);
                            finalTarget.damage(1.0, player);
                        } else {
                            finalTarget.setHealth(newHp);
                            finalTarget.damage(0.01, player);
                        }

                        // Inflict Bleedout DOT (constantly lose health)
                        startBleedout(finalTarget, player);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, i * 2L, 1L);
        }

        return true;
    }

    private void startBleedout(LivingEntity target, Player attacker) {
        target.sendMessage(Component.text("⚔ You are hemorrhaging from Bleedout!", NamedTextColor.RED));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.8f);

        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.4f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || ticks > 6) {
                    cancel();
                    return;
                }
                target.damage(1.5, attacker);
                Location loc = target.getLocation().add(0, 1.0, 0);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 4);
                // Bleedout ground ring pulse
                Location feet = target.getLocation();
                for (int d = 0; d < 360; d += 30) {
                    double rad = Math.toRadians(d);
                    feet.getWorld().spawnParticle(Particle.DUST, feet.clone().add(Math.cos(rad) * 0.8, 0.1, Math.sin(rad) * 0.8), 1, 0, 0, 0, 0, bloodDust);
                }
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
        plugin.getBossBarManager().showActiveCountdown(player, "Bloody Rain", BossBar.Color.PURPLE, 20);

        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, 1.5f, 0.8f);
        Particle.DustOptions purpleRain = new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.3f);

        // 8x8 Cloud above player's head with purple-tinted rain
        new BukkitRunnable() {
            int ticks = 200; // 20s

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    cancel();
                    return;
                }

                Location cloudLoc = player.getLocation().add(0, 4.0, 0);
                for (double x = -4.0; x <= 4.0; x += 1.5) {
                    for (double z = -4.0; z <= 4.0; z += 1.5) {
                        cloudLoc.getWorld().spawnParticle(Particle.CLOUD, cloudLoc.clone().add(x, 0, z), 1, 0.2, 0.1, 0.2, 0.01);
                        cloudLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, cloudLoc.clone().add(x, 0, z), 1, 0.1, 0.1, 0.1, 0.01);
                        cloudLoc.getWorld().spawnParticle(Particle.DRIPPING_WATER, cloudLoc.clone().add(x, -0.2, z), 1);
                        cloudLoc.getWorld().spawnParticle(Particle.DUST, cloudLoc.clone().add(x, -0.4, z), 1, 0, 0, 0, 0, purpleRain);
                    }
                }

                ticks -= 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        player.sendMessage(Component.text("✦ Bloody Rain active! Up to 3 mirrored clones will manifest when attacked.", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isActive(victim, key)) {
            List<ArmorStand> clones = activeClones.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>());

            if (clones.size() < 3) {
                // Spawn mirrored clone
                Location cloneLoc = victim.getLocation().add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
                ArmorStand clone = victim.getWorld().spawn(cloneLoc, ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setCustomNameVisible(true);
                    as.customName(victim.name());
                    as.setArms(true);
                    as.setBasePlate(false);
                    as.getEquipment().setItemInMainHand(victim.getInventory().getItemInMainHand());
                    as.getEquipment().setHelmet(victim.getInventory().getHelmet());
                    as.getEquipment().setChestplate(victim.getInventory().getChestplate());
                    as.getEquipment().setLeggings(victim.getInventory().getLeggings());
                    as.getEquipment().setBoots(victim.getInventory().getBoots());
                });

                clones.add(clone);
                // Mirror shatter particles
                victim.getWorld().spawnParticle(Particle.PORTAL, cloneLoc.clone().add(0, 1.0, 0), 25, 0.4, 0.5, 0.4, 0.1);
                victim.getWorld().spawnParticle(Particle.END_ROD, cloneLoc.clone().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0.05);
                victim.playSound(cloneLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.4f, 1.0f);
                victim.playSound(cloneLoc, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);

                // Give attacker weakness if attacker is a living entity
                if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof LivingEntity attacker) {
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
                }

                // Disappear after 4 seconds
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        clone.remove();
                        clones.remove(clone);
                    }
                }.runTaskLater(plugin, 80L);
            }
        }
    }

    @Override
    public void onCrouch(Player player, boolean isSneaking) {
        if (!isSneaking) return;
        // Forming: Crouching summons non-flowing water block + ripple ring
        Block block = player.getLocation().getBlock();
        if (block.getType() == Material.AIR) {
            block.setType(Material.WATER);

            Particle.DustOptions waterRing = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.5f);
            Location loc = player.getLocation();
            for (int d = 0; d < 360; d += 25) {
                double rad = Math.toRadians(d);
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(Math.cos(rad) * 1.2, 0.1, Math.sin(rad) * 1.2), 1, 0, 0, 0, 0, waterRing);
            }

            // Revert after 3 seconds so world isn't flooded permanently
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

    public void applyBraveBuff(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(24.0); // 12 hearts
        }
    }

    public void removeBraveBuff(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null && attr.getBaseValue() > 20.0) {
            attr.setBaseValue(20.0); // 10 hearts normal
        }
    }
}
