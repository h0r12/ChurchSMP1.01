package com.churchsmp.item;

import com.churchsmp.ChurchSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ImpietyItem {

    private static final Material BASE_MATERIAL = Material.RECOVERY_COMPASS;

    public static ItemStack create(ChurchSMP plugin) {
        ItemStack item = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component name = MiniMessage.miniMessage().deserialize("<!italic><gradient:#9400D3:#4B0082><bold>Impiety</bold></gradient>");
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("A relic defying mortal alignment.", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Right-Click", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(" to select a Legendary Weapon", NamedTextColor.GRAY)));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            meta.lore(lore);

            NamespacedKey key = new NamespacedKey(plugin, "is_impiety");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);

            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isImpiety(ChurchSMP plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "is_impiety");
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}
