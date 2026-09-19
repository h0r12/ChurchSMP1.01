package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Excalibur extends LegendaryWeapon {

    // Tracks ongoing charging for Acceleration Nova: UUID -> BukkitRunnable
    private final Map<UUID, BukkitRunnable> chargingTasks = new ConcurrentHashMap<>();

    public Excalibur(ChurchSMP plugin) {
        super(plugin,
                "excalibur",
                new String[]{"blade_of_the_archangel"},
                Component.text("Excalibur", TextColor.color(0x55FFFF)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_SWORD,
                Alignment.GOOD,
                "Acceleration Nova",
                "Altar Pining");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_AQUA));
            lore.add(Component.text("Forged God's will.", TextColor.color(0xFFD700)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Hopeful: ", NamedTextColor.YELLOW).append(Component.text("Every attack inflicts glowing matching your alignment.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Wings: ", NamedTextColor.YELLOW).append(Component.text("Complete immunity to fall damage.", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Acceleration Nova: ", NamedTextColor.AQUA).append(Component.text("Charge 10s with Resistance II, unleashing a holy sonic blast (2.5 True Damage).", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Altar Pining: ", NamedTextColor.AQUA).append(Component.text("Ascend exploding nearby foes for 2 True Damage, launching 10x10 into sky then slam for 1 True Damage + 3s stun.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_AQUA));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
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

        // Check if already charging
        if (chargingTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("✦ You are already charging Acceleration Nova!", NamedTextColor.YELLOW));
            return false;
        }

        player.sendMessage(Component.text("✦ Charging Acceleration Nova (10s)... Keep steady!", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.2f, 1.5f);
        plugin.getBossBarManager().showActiveCountdown(player, "Acceleration Nova Charging", BossBar.Color.BLUE, 10);

        // Grant Resistance II while charging
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 210, 1, false, false));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getInventory().getItemInMainHand().getType() != baseMaterial) {
                    chargingTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Charging particles around player
                Location loc = player.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.4, 0.4, 0.4, 0.05);

                ticks += 5;
                if (ticks >= 200) { // 10 seconds reached
                    chargingTasks.remove(player.getUniqueId());

                    // Fire Acceleration Nova blast!
                    Location start = player.getEyeLocation();
                    Vector dir = start.getDirection().normalize();
                    player.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);

                    for (int i = 1; i <= 18; i++) {
                        Location p = start.clone().add(dir.clone().multiply(i));
                        // Gold, white, and blue particles
                        p.getWorld().spawnParticle(Particle.CRIT, p, 5, 0.2, 0.2, 0.2, 0.05); // Gold
                        p.getWorld().spawnParticle(Particle.END_ROD, p, 5, 0.2, 0.2, 0.2, 0.05); // White
                        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p, 5, 0.2, 0.2, 0.2, 0.05); // Blue

                        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p, 2.5)) {
                            if (e.equals(player)) continue;
                            // 2.5 true damage (5 HP)
                            applyTrueDamage(e, 5.0, player);
                            e.setVelocity(dir.clone().multiply(1.5).setY(0.4));
                        }
                    }

                    // 45s CD
                    int cd = plugin.getConfig().getInt("weapons.excalibur.primary_cooldown", 45);
                    plugin.getCooldownManager().setCooldown(player, key, cd);
                    cancel();
                }
            }
        };

        chargingTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L);
        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.excalibur.secondary_cooldown", 80);
        plugin.getCooldownManager().setCooldown(player, key, cd);
        plugin.getBossBarManager().showActiveCountdown(player, "Altar Pining", BossBar.Color.YELLOW, 4);

        // Altar Pining: particles around feet, Excalibur ascends exploding nearby entities (2 true damage = 4 HP)
        Location feet = player.getLocation();
        feet.getWorld().spawnParticle(Particle.GOLD, feet, 40, 0.8, 0.1, 0.8, 0.05);
        feet.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.6f);

        // Stage 1: Ascends, 2 true damage, launch 10x10 into the sky
        player.setVelocity(new Vector(0, 1.8, 0));

        for (LivingEntity e : feet.getWorld().getNearbyLivingEntities(feet, 10.0, 5.0, 10.0)) {
            if (e.equals(player)) continue;
            applyTrueDamage(e, 4.0, player); // 2 true damage (4 HP)
            e.setVelocity(new Vector(0, 1.7, 0)); // Launch skyward
        }

        // Stage 2: Slam down -> 1 more true damage (2 HP) + 3s stun
        new BukkitRunnable() {
            int ticks = 0;
            boolean slammed = false;

            @Override
            public void run() {
                ticks++;
                if (ticks == 18 && !slammed) {
                    player.setVelocity(new Vector(0, -3.0, 0));
                    slammed = true;
                }

                if (slammed && (player.isOnGround() || ticks >= 38)) {
                    Location impact = player.getLocation();
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.8f);
                    impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 2);
                    impact.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, impact, 100, 3.0, 0.5, 3.0, 0.2);

                    for (LivingEntity e : impact.getWorld().getNearbyLivingEntities(impact, 8.0, 4.0, 8.0)) {
                        if (e.equals(player)) continue;
                        applyTrueDamage(e, 2.0, player); // 1 more true damage
                        // Stun for 3 seconds (Slowness 255 + Jump boost 128)
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 255, false, false));
                        e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 128, false, false));
                        e.setVelocity(new Vector(0, 0, 0));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Hopeful: Every attack gives entity glowing; color based on alignment
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
        Alignment al = plugin.getAlignmentManager().getAlignment(attacker);
        Particle particle = switch (al) {
            case GOOD -> Particle.GLOW;
            case EVIL -> Particle.SOUL_FIRE_FLAME;
            case NULLIFIED -> Particle.SMOKE;
        };
        target.getWorld().spawnParticle(particle, target.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.05);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        // Wings: Complete immunity to fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
        }
    }

    private void applyTrueDamage(LivingEntity entity, double amount, Player source) {
        double newHp = entity.getHealth() - amount;
        if (newHp <= 0) {
            entity.setHealth(0);
            entity.damage(1.0, source); // trigger kill credit
        } else {
            entity.setHealth(newHp);
            entity.damage(0.01, source);
        }
    }
}
