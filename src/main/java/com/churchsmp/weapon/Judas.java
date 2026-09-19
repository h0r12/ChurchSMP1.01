package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
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
            lore.add(Component.text("✦ Alignment Required: ", NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
            lore.add(Component.empty());
            lore.add(Component.text("Passives:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text(" • Bloodlust: ", NamedTextColor.GOLD).append(Component.text("Cannot regenerate health while holding Judas.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Unfree: ", NamedTextColor.GOLD).append(Component.text("You occasionally receive a random Judas curse/gift.", NamedTextColor.WHITE)));
            lore.add(Component.text(" • Bite: ", NamedTextColor.GOLD).append(Component.text("25% chance on hit to inflict Wither, Nausea & Blindness (30s CD).", NamedTextColor.WHITE)));
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

        // Unleash a horizontal crescent wave of wither skulls in a 5-block cone
        Location eye = player.getEyeLocation();
        Vector baseDir = eye.getDirection().setY(0).normalize();

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
            attacker.sendMessage(Component.text("✦ Bite triggered! Target inflicted with Wither, Nausea & Blindness.", NamedTextColor.DARK_RED));
        }

        // Unfree: Judas gift (random debuff on victim or small drawback on attacker)
        if (random.nextDouble() < 0.15) {
            PotionEffectType[] curses = new PotionEffectType[]{
                    PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS, PotionEffectType.DARKNESS
            };
            PotionEffectType curse = curses[random.nextInt(curses.length)];
            attacker.addPotionEffect(new PotionEffect(curse, 80, 0));
            attacker.sendMessage(Component.text("✦ Unfree: You received a gift from Judas...", NamedTextColor.DARK_GRAY));
        }
    }
}
