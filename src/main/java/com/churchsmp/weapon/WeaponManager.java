package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WeaponManager {

    private final ChurchSMP plugin;
    private final NamespacedKey weaponIdKey;
    private final Map<String, LegendaryWeapon> weapons = new HashMap<>();

    public WeaponManager(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");

        register(new Excalibur(plugin));
        register(new LuminescenceSpear(plugin));
        register(new Mayim(plugin));
        register(new Judas(plugin));
        register(new Sorrowess(plugin));
        register(new VoidBreaker(plugin));
        register(new Grim(plugin));
    }

    public void register(LegendaryWeapon weapon) {
        weapons.put(weapon.getId().toLowerCase(Locale.ROOT), weapon);
        for (String legacy : weapon.getLegacyIds()) {
            weapons.put(legacy.toLowerCase(Locale.ROOT), weapon);
        }
    }

    public LegendaryWeapon getWeapon(String id) {
        if (id == null) return null;
        return weapons.get(id.toLowerCase(Locale.ROOT));
    }

    public LegendaryWeapon getWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(weaponIdKey, PersistentDataType.STRING);
        if (id == null) return null;
        return getWeapon(id);
    }

    public Collection<LegendaryWeapon> getAllWeapons() {
        return weapons.values().stream().distinct().toList();
    }

    public boolean hasWeapon(org.bukkit.entity.Player player, String weaponId) {
        if (player == null || weaponId == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            LegendaryWeapon w = getWeapon(item);
            if (w != null && weaponId.equalsIgnoreCase(w.getId())) {
                return true;
            }
        }
        return false;
    }
}
