package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import com.churchsmp.util.CloneUtil;
import com.churchsmp.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Sorrowess extends LegendaryWeapon {

    // Mirror clones: Player UUID -> List of active humanoid clones
    private final Map<UUID, List<LivingEntity>> activeClones = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // PDC key to identify Sorrowess clones
    private final NamespacedKey cloneKey;

    // Gloom tracking: inflicts for 1 stack (10% armor reduction & durability drain for 30s)
    public static class GloomData {
        private final UUID victimId;
        private final int stacks = 1;
        private final double reductionPercent = 0.10;
        private long expireTime;

        public GloomData(UUID victimId) {
            this.victimId = victimId;
            this.expireTime = System.currentTimeMillis() + 30000L;
        }

        public void refresh() {
            this.expireTime = System.currentTimeMillis() + 30000L;
        }

        public int getStacks() {
            return stacks;
        }

        public double getReductionPercent() {
            return reductionPercent;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        public long getRemainingSeconds() {
            return Math.max(0, (expireTime - System.currentTimeMillis()) / 1000L);
        }
    }

    private final Map<UUID, Integer> critHitCounters = new ConcurrentHashMap<>();
    private final Map<UUID, GloomData> activeGloom = new ConcurrentHashMap<>();
    private final NamespacedKey gloomArmorKey;

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
        this.gloomArmorKey = new NamespacedKey(plugin, "sorrowess_gloom_armor");
        startGloomTicker();
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(buildCleanLore(List.of("Forming", "Brave", "Gloom"), "Grief Shards", "Bloody Rain"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            applyStandardEnchants(meta);
            meta.addEnchant(Enchantment.RIPTIDE, 7, true);

            // Netherite Sword Sharpness 7 damage (12.0 attribute bonus = 13.0 total attack damage)
            NamespacedKey dmgKey = new NamespacedKey(plugin, "sorrowess_damage");
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(dmgKey, 12.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

            // Attack speed of sword (1.6)
            NamespacedKey speedKey = new NamespacedKey(plugin, "sorrowess_speed");
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

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

                    // Dual spiral spirit particles around the flying shard
                    double spiralAngle = ticks * 0.45;
                    Vector spiralOffset = new Vector(Math.cos(spiralAngle) * 0.35, Math.sin(spiralAngle) * 0.35, 0);
                    itemLoc.getWorld().spawnParticle(Particle.DUST, itemLoc.clone().add(spiralOffset), 1, 0, 0, 0, 0, trailDust);
                    itemLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, itemLoc.clone().subtract(spiralOffset), 1, 0, 0, 0, 0.01);
                    itemLoc.getWorld().spawnParticle(Particle.END_ROD, itemLoc, 1, 0.01, 0.01, 0.01, 0.01);
                    itemLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, itemLoc, 1, 0, 0, 0, 0);

                    Vector dir = finalTarget.getLocation().add(0, 1.0, 0).toVector().subtract(itemEntity.getLocation().toVector()).normalize().multiply(1.2);
                    itemEntity.setVelocity(dir);

                    if (itemEntity.getLocation().distance(finalTarget.getLocation().add(0, 1.0, 0)) < 1.5) {
                        Location hitLoc = finalTarget.getLocation().add(0, 1.0, 0);
                        hitLoc.getWorld().spawnParticle(Particle.FLASH, hitLoc, 1, Color.WHITE);
                        hitLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, hitLoc, 1);
                        hitLoc.getWorld().spawnParticle(Particle.SOUL, hitLoc, 15, 0.3, 0.3, 0.3, 0.05);
                        hitLoc.getWorld().spawnParticle(Particle.DUST, hitLoc, 16, 0.4, 0.4, 0.4, 0, redDust);

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

        Location spawnCenter = player.getLocation().clone();
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(200, 0, 20), 1.4f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.8f);
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1.2f);

        // Spawn 4 illusion clones in 4 cardinal directions
        List<LivingEntity> clones = activeClones.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        for (LivingEntity old : clones) {
            if (old.isValid()) old.remove();
        }
        clones.clear();

        double[] angles = {0, 90, 180, 270};
        for (double deg : angles) {
            double rad = Math.toRadians(deg);
            Vector dir = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize().multiply(0.18);
            Location spawn = spawnCenter.clone().add(Math.cos(rad) * 2.0, 0, Math.sin(rad) * 2.0);
            spawnClone(player, spawn, dir, clones);
        }

        // Arena loop: 20s with rain effects and boundary FOLLOWING THE PLAYER
        new BukkitRunnable() {
            int ticks = 200;

            @Override
            public void run() {
                if (!player.isOnline() || ticks <= 0) {
                    for (LivingEntity c : clones) {
                        if (c.isValid()) c.remove();
                    }
                    clones.clear();
                    cancel();
                    return;
                }

                // Domain dynamically follows the player
                Location arenaCenter = player.getLocation();

                // White "sun" orb at top of arena
                Location sunLoc = arenaCenter.clone().add(0, 6.5, 0);
                sunLoc.getWorld().spawnParticle(Particle.END_ROD, sunLoc, 4, 0.4, 0.4, 0.4, 0.02);
                sunLoc.getWorld().spawnParticle(Particle.DUST, sunLoc, 6, 0.5, 0.5, 0.5, 0, whiteDust);

                // 12×12 boundary on ground following player
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

        player.sendMessage(Component.text("✦ Bloody Rain! 4 illusion clones summoned in mobile arena.", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /**
     * Executes Sorrowess Riptide regardless of water/rain conditions with a 15-second cooldown.
     */
    public boolean executeRiptide(Player player) {
        String cdKey = id + "_riptide";
        if (plugin.getCooldownManager().isOnCooldown(player, cdKey)) {
            int rem = (int) Math.ceil(plugin.getCooldownManager().getRemainingCooldownSeconds(player, cdKey));
            player.sendActionBar(miniMessage.deserialize("<red>✦ Sorrowess Riptide on Cooldown: " + rem + "s ✦</red>"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f);
            return false;
        }

        plugin.getCooldownManager().setCooldown(player, cdKey, 15);
        plugin.getBossBarManager().showPassiveCooldown(player, this, "Riptide", 15);
        player.sendMessage(miniMessage.deserialize("<gradient:#FFFFFF:#FF7F7F:#8B0000><bold>✦ [SORROWESS] Riptide Surge! ✦</bold></gradient> <gray>(15s cooldown)</gray>"));

        Vector dir = player.getEyeLocation().getDirection().normalize();
        Vector vel = dir.clone().multiply(2.2);
        vel.setY(Math.max(vel.getY(), 0.3));
        player.setVelocity(vel);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.6f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);

        Particle.DustOptions riptideDust = new Particle.DustOptions(Color.fromRGB(200, 50, 100), 1.4f);
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.6f);
        java.util.Set<UUID> hitTargets = new java.util.HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || ticks > 12) {
                    cancel();
                    return;
                }

                Location pLoc = player.getLocation().add(0, 1.0, 0);
                pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 6, 0.4, 0.4, 0.4, 0, riptideDust);
                pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 4, 0.3, 0.3, 0.3, 0, whiteDust);
                pLoc.getWorld().spawnParticle(Particle.NAUTILUS, pLoc, 3, 0.2, 0.2, 0.2, 0.05);

                for (LivingEntity nearby : player.getWorld().getNearbyLivingEntities(pLoc, 2.6)) {
                    if (nearby.equals(player) || hitTargets.contains(nearby.getUniqueId())) continue;
                    if (nearby.getPersistentDataContainer().has(cloneKey, PersistentDataType.STRING)) continue;

                    hitTargets.add(nearby.getUniqueId());
                    nearby.damage(13.0, player);
                    nearby.setVelocity(dir.clone().multiply(0.7).add(new Vector(0, 0.35, 0)));

                    nearby.getWorld().playSound(nearby.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 1.0f);
                    nearby.getWorld().playSound(nearby.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
                    nearby.getWorld().spawnParticle(Particle.SWEEP_ATTACK, nearby.getLocation().add(0, 1.0, 0), 1);
                    nearby.getWorld().spawnParticle(Particle.CRIT, nearby.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.1);

                    // Inflict Gloom (1 stack) on Riptide strike
                    applyGloom(player, nearby);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    // Spawns a humanoid clone that mirrors the player's appearance and behavior with realistic AI
    private void spawnClone(Player owner, Location loc, Vector dir, List<LivingEntity> cloneList) {
        if (cloneList.size() >= 16) return; // hard cap

        CloneUtil.CloneConfig cfg = new CloneUtil.CloneConfig();
        cfg.owner = owner;
        cfg.location = loc;
        cfg.displayName = owner.name();
        cfg.tagKey = cloneKey;
        cfg.tagValue = owner.getUniqueId().toString();
        cfg.durationTicks = 400;
        cfg.movementSpeed = 0.32;
        cfg.formationOffset = dir.clone().multiply(3.0);
        cfg.followOwner = true;
        cfg.syncSneak = true;
        cfg.attackRange = 3.2;
        cfg.attackDamage = 1.0;
        cfg.jumpCrit = true;
        cfg.mainHandOverride = owner.getInventory().getItemInMainHand() != null && owner.getInventory().getItemInMainHand().getType() != Material.AIR
                ? owner.getInventory().getItemInMainHand().clone()
                : new ItemStack(Material.TRIDENT);
        cfg.onTick = z -> {
            Location cLoc = z.getLocation();
            cLoc.getWorld().spawnParticle(Particle.DUST, cLoc.clone().add(0, 0.1, 0), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 0, 50), 1.0f));
        };
        cfg.onDespawn = z -> cloneList.remove(z);

        org.bukkit.entity.Zombie clone = CloneUtil.spawnRealisticClone(plugin, cfg);
        if (clone != null) {
            cloneList.add(clone);
        }
    }

    // Called from CombatListener when a clone is attacked
    public void multiplyClone(LivingEntity hitClone, LivingEntity attacker) {
        String ownerStr = hitClone.getPersistentDataContainer().get(cloneKey, PersistentDataType.STRING);
        if (ownerStr == null) return;
        UUID ownerId = UUID.fromString(ownerStr);
        Player owner = plugin.getServer().getPlayer(ownerId);
        List<LivingEntity> clones = activeClones.get(ownerId);
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
                Vector v = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize().multiply(0.18);
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

    private void startGloomTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeGloom.isEmpty()) return;
                Iterator<Map.Entry<UUID, GloomData>> it = activeGloom.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, GloomData> entry = it.next();
                    UUID victimId = entry.getKey();
                    GloomData gloom = entry.getValue();

                    Entity entity = Bukkit.getEntity(victimId);
                    if (entity == null || !entity.isValid() || entity.isDead() || gloom.isExpired()) {
                        if (entity instanceof LivingEntity le) {
                            removeGloomArmorModifier(le);
                            if (entity instanceof Player p && p.isOnline()) {
                                p.sendMessage(miniMessage.deserialize("<gray>✦ " + TextUtil.toSmallCaps("The Gloom lifts; your armor has stabilized.") + " ✦</gray>"));
                            }
                        }
                        it.remove();
                        continue;
                    }

                    if (entity instanceof LivingEntity le) {
                        Location loc = le.getLocation();
                        loc.getWorld().spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, loc.clone().add(0, 1.3, 0), 2, 0.25, 0.35, 0.25, 0.02);
                        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.8, 0), 2, 0.3, 0.4, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(48, 25, 52), 1.2f));
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public void handleCritHit(Player attacker, LivingEntity target) {
        // Visual crit feedback with Sorrowess particles
        target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1.0, 0), 8, 0.2, 0.2, 0.2, 0.05);
        applyGloom(attacker, target);
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        applyGloom(attacker, target);
    }

    public void applyGloom(Player attacker, LivingEntity target) {
        UUID victimId = target.getUniqueId();
        GloomData gloom = activeGloom.get(victimId);
        boolean isNew = (gloom == null || gloom.isExpired());
        if (isNew) {
            gloom = new GloomData(victimId);
            activeGloom.put(victimId, gloom);
        } else {
            gloom.refresh();
        }

        applyGloomArmorModifier(target, gloom.getReductionPercent());

        Location loc = target.getLocation();
        loc.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.7f);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.8f);
        loc.getWorld().spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, loc.clone().add(0, 1.5, 0), 20, 0.4, 0.5, 0.4, 0.04);
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1.0, 0), 16, 0.3, 0.4, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1.4f));

        if (isNew) {
            attacker.sendMessage(miniMessage.deserialize("<dark_purple>✦ [GLOOM] " + TextUtil.toSmallCaps("Inflicted Gloom (1 stack) on ") + "<white>" + target.getName() + "</white>! " + TextUtil.toSmallCaps("Armor reduced by 10% (30s).") + " ✦</dark_purple>"));
            if (target instanceof Player victimPlayer) {
                victimPlayer.sendMessage(miniMessage.deserialize("<dark_purple><bold>✦ [GLOOM] " + TextUtil.toSmallCaps("Your armor fractured into sorrow! Total armor reduced by 10% (30s) & durability crumbling faster!") + " ✦</bold></dark_purple>"));
            }
        }
    }

    private void applyGloomArmorModifier(LivingEntity target, double reductionPercent) {
        org.bukkit.attribute.AttributeInstance attr = target.getAttribute(Attribute.ARMOR);
        if (attr != null) {
            attr.removeModifier(gloomArmorKey);
            double totalArmor = attr.getValue();
            if (totalArmor > 0) {
                double reductionAmount = totalArmor * reductionPercent;
                org.bukkit.attribute.AttributeModifier mod = new org.bukkit.attribute.AttributeModifier(
                        gloomArmorKey,
                        -reductionAmount,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER
                );
                attr.addTransientModifier(mod);
            }
        }
    }

    public void removeGloomArmorModifier(LivingEntity target) {
        if (target == null) return;
        org.bukkit.attribute.AttributeInstance attr = target.getAttribute(Attribute.ARMOR);
        if (attr != null) {
            attr.removeModifier(gloomArmorKey);
        }
    }

    public boolean hasGloom(LivingEntity target) {
        if (target == null) return false;
        GloomData data = activeGloom.get(target.getUniqueId());
        return data != null && !data.isExpired();
    }

    public double getGloomReduction(LivingEntity target) {
        if (target == null) return 0.0;
        GloomData data = activeGloom.get(target.getUniqueId());
        if (data == null || data.isExpired()) return 0.0;
        return data.getReductionPercent();
    }
}
