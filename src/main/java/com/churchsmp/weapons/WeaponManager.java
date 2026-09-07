package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the 7 custom weapon items (tagged via PersistentDataContainer so
 * they survive serialization) and tracks per-player ability cooldowns.
 */
public class WeaponManager {

    public static final String NBT_KEY = "weapon_id";

    private final ChurchSMP plugin;
    private final NamespacedKey weaponKey;

    // playerUUID -> weaponId -> abilityNumber -> unix millis when usable again
    private final Map<UUID, Map<String, Map<Integer, Long>>> cooldowns = new HashMap<>();

    public WeaponManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, NBT_KEY);
    }

    public NamespacedKey getWeaponKey() {
        return weaponKey;
    }

    public ItemStack createWeapon(WeaponType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        NamedTextColor nameColor = switch (type.getCategory()) {
            case GOOD -> NamedTextColor.GOLD;
            case EVIL -> NamedTextColor.DARK_RED;
            case NULLIFIED -> NamedTextColor.GRAY;
        };

        meta.displayName(Component.text(type.getDisplayName(), nameColor)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.getSubtitle(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());

        if (type.getPassives().length > 0) {
            lore.add(Component.text("Passives", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
            for (String passive : type.getPassives()) {
                lore.add(Component.text("\u25B8 " + passive, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }

        lore.add(Component.text(type.getAbility1Desc(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(type.getAbility2Desc(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Requires: " + type.getCategory().name() + " alignment",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Run /church guide while holding it for details.",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, type.getId());
        meta.setItemModel(new NamespacedKey("churchsmp", type.getId()));
        item.setItemMeta(meta);

        if (type == WeaponType.VOIDBREAKER) {
            ItemMeta voidMeta = item.getItemMeta();
            voidMeta.setUnbreakable(true);
            item.setItemMeta(voidMeta);
            // Default mode: Density. Spaced Bound toggles this to Breach and back.
            item.addUnsafeEnchantment(Enchantment.DENSITY, 6);
            item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            item.addUnsafeEnchantment(Enchantment.WIND_BURST, 1);
        }

        if (type == WeaponType.SORROWESS) {
            item.addUnsafeEnchantment(Enchantment.RIPTIDE, 4);
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 7);
        }

        return item;
    }

    /** Returns the WeaponType of an item, or null if it isn't a Church SMP weapon. */
    public WeaponType getWeaponType(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(weaponKey, PersistentDataType.STRING);
        return id == null ? null : WeaponType.fromId(id);
    }

    public boolean isOnCooldown(Player player, WeaponType type, int ability) {
        Long readyAt = cooldowns
                .getOrDefault(player.getUniqueId(), Map.of())
                .getOrDefault(type.getId(), Map.of())
                .get(ability);
        return readyAt != null && readyAt > System.currentTimeMillis();
    }

    public long getRemainingCooldownSeconds(Player player, WeaponType type, int ability) {
        Long readyAt = cooldowns
                .getOrDefault(player.getUniqueId(), Map.of())
                .getOrDefault(type.getId(), Map.of())
                .get(ability);
        if (readyAt == null) return 0;
        return Math.max(0, (readyAt - System.currentTimeMillis()) / 1000);
    }

    public void putOnCooldown(Player player, WeaponType type, int ability, int seconds) {
        cooldowns
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .computeIfAbsent(type.getId(), k -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Clears both ability cooldowns for one weapon on one player. */
    public void clearCooldown(Player player, WeaponType type) {
        Map<String, Map<Integer, Long>> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(type.getId());
        }
    }

    /** Clears every weapon's cooldowns for one player. */
    public void clearAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public int getConfiguredCooldown(int ability) {
        String path = ability == 1 ? "weapons.cooldown-seconds.ability-1"
                                    : "weapons.cooldown-seconds.ability-2";
        return plugin.getConfig().getInt(path, ability == 1 ? 20 : 90);
    }
}
