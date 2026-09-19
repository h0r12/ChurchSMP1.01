package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
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

public class Judas extends LegendaryWeapon {

    private final Map<UUID, Integer> skullCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> biteCooldown = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Judas(ChurchSMP plugin) {
        super(plugin,
                "judas",
                new String[]{"blade_of_judas", "dagger_of_betrayal"},
                Component.text("Judas", TextColor.color(0x8B0000)).decorate(TextDecoration.BOLD),
                Material.NETHERITE_AXE,
                Alignment.EVIL,
                "Hemorrhaged",
                "Crescent Bloodmoon");
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_RED));
            lore.add(Component.text("Decayed Blood.", TextColor.color(0xB22222)).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("âœ¦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" â€¢ Bloodlust: ", NamedTextColor.GOLD).append(Component.text("Cannot regenerate health while holding Judas.", NamedTextColor.WHITE)));
            lore.add(Component.text(" â€¢ Unfree: ", NamedTextColor.GOLD).append(Component.text("You occasionally receive a random Judas curse/gift.", NamedTextColor.WHITE)));
            lore.add(Component.text(" â€¢ Bite: ", NamedTextColor.GOLD).append(Component.text("25% chance on hit to inflict Wither, Nausea & Blindness (30s CD).", NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Abilities:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" [Primary] Hemorrhaged: ", NamedTextColor.DARK_RED).append(Component.text("Launch explosive Wither Skulls (3/3 charges).", NamedTextColor.WHITE)));
            lore.add(Component.text(" [Secondary] Crescent Bloodmoon: ", NamedTextColor.DARK_RED).append(Component.text("Horizontal crescent wave of wither skulls dealing damage + Wither II for 5s in a 5-block cone.", NamedTextColor.WHITE)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_RED));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "weapon_id"), PersistentDataType.STRING, id);
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean executePrimary(Player player) {
        String key = id + "_primary";
        int currentCharges = skullCharges.getOrDefault(player.getUniqueId(), 3);

        if (currentCharges <= 0) {
            if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;
            skullCharges.put(player.getUniqueId(), 3);
            currentCharges = 3;
        }

        Vector dir = player.getEyeLocation().getDirection().normalize().multiply(1.3);
        WitherSkull skull = player.launchProjectile(WitherSkull.class, dir);
        skull.setCharged(currentCharges == 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);

        // Blood particle burst at launch
        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.6f);
        player.getWorld().spawnParticle(Particle.DUST, player.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0, bloodDust);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getEyeLocation(), 3, 0.2, 0.2, 0.2, 0.05);

        // Trail behind the skull
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!skull.isValid() || skull.isDead()) {
                    cancel();
                    return;
                }
                skull.getWorld().spawnParticle(Particle.DUST, skull.getLocation(), 4, 0.1, 0.1, 0.1, 0, bloodDust);
                skull.getWorld().spawnParticle(Particle.SMOKE, skull.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);

        currentCharges--;
        skullCharges.put(player.getUniqueId(), currentCharges);

        if (currentCharges <= 0) {
            int cd = plugin.getConfig().getInt("weapons.judas.primary_cooldown", 16);
            plugin.getCooldownManager().setCooldown(player, key, cd);
        } else {
            player.sendMessage(Component.text("✦ Hemorrhaged Skull Charges: " + currentCharges + "/3", NamedTextColor.DARK_RED));
        }

        return true;
    }

    @Override
    public boolean executeSecondary(Player player) {
        String key = id + "_secondary";
        if (plugin.getCooldownManager().isOnCooldown(player, key)) return false;

        int cd = plugin.getConfig().getInt("weapons.judas.secondary_cooldown", 40);
        plugin.getCooldownManager().setCooldown(player, key, cd);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.6f);
        player.swingMainHand();

        Location eye = player.getEyeLocation();
        Vector baseDir = eye.getDirection().setY(0).normalize();
        Particle.DustOptions crimsonDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 2.0f);

        // Horizontal crescent arc of dark red particles
        for (int angle = -45; angle <= 45; angle += 3) {
            Vector dir = baseDir.clone().rotateAroundY(Math.toRadians(angle));
            Location arcP = eye.clone().add(dir.multiply(3.5));
            arcP.getWorld().spawnParticle(Particle.DUST, arcP, 2, 0.1, 0.1, 0.1, 0, crimsonDust);
            arcP.getWorld().spawnParticle(Particle.SMOKE, arcP, 1, 0.05, 0.05, 0.05, 0.01);
        }

        // Unleash a horizontal crescent wave of wither skulls in a 5-block cone
        for (int angle = -40; angle <= 40; angle += 20) {
            Vector dir = baseDir.clone().rotateAroundY(Math.toRadians(angle)).multiply(1.4);
            WitherSkull skull = player.launchProjectile(WitherSkull.class, dir);
            skull.setCharged(false);
        }

        // Damage and Wither II for 5s to all entities in 5-block cone
        for (LivingEntity target : player.getWorld().getNearbyLivingEntities(player.getLocation(), 6.0)) {
            if (target.equals(player)) continue;
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            if (baseDir.dot(toTarget) > 0.4) {
                target.damage(10.0, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
            }
        }

        player.sendMessage(Component.text("✦ Crescent Bloodmoon unleashed!", NamedTextColor.DARK_RED));
        return true;
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        // Bite: 25% chance to trigger debuff hit (wither, nausea, blindness), 30s cooldown
        long now = System.currentTimeMillis();
        long lastBite = biteCooldown.getOrDefault(attacker.getUniqueId(), 0L);

        if (now - lastBite > 30000L && random.nextDouble() < 0.25) {
            biteCooldown.put(attacker.getUniqueId(), now);

            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));

            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FOX_BITE, 1.5f, 0.6f);

            // Bite fang visual: closing crimson V-shape
            Location targetEye = target.getLocation().add(0, 1.2, 0);
            Particle.DustOptions biteDust = new Particle.DustOptions(Color.fromRGB(200, 10, 10), 1.6f);
            for (double d = -0.6; d <= 0.6; d += 0.2) {
                targetEye.getWorld().spawnParticle(Particle.DUST, targetEye.clone().add(d, Math.abs(d) * 0.8, 0), 2, 0, 0, 0, 0, biteDust);
                targetEye.getWorld().spawnParticle(Particle.DUST, targetEye.clone().add(d, -Math.abs(d) * 0.8, 0), 2, 0, 0, 0, 0, biteDust);
            }
            targetEye.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, targetEye, 6, 0.2, 0.2, 0.2, 0.05);

            attacker.sendMessage(Component.text("✦ Bite triggered! Target inflicted with Wither, Nausea & Blindness.", NamedTextColor.DARK_RED));
        }

        // Unfree: Judas gift (random debuff on victim or small drawback on attacker)
        if (random.nextDouble() < 0.15) {
            PotionEffectType[] curses = new PotionEffectType[]{
                    PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS, PotionEffectType.DARKNESS
            };
            PotionEffectType curse = curses[random.nextInt(curses.length)];
            attacker.addPotionEffect(new PotionEffect(curse, 80, 0));
            // Dark curse swirl around attacker
            Location aLoc = attacker.getLocation().add(0, 1.0, 0);
            aLoc.getWorld().spawnParticle(Particle.SOUL, aLoc, 10, 0.3, 0.5, 0.3, 0.03);
            attacker.sendMessage(Component.text("✦ Unfree: You received a gift from Judas...", NamedTextColor.DARK_GRAY));
        }
    }
}
