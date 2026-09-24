package com.churchsmp.item;

import com.churchsmp.ChurchSMP;
import com.churchsmp.util.TextUtil;
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

public class LiminalCoreItem {

    private static final String CORE_ID = "liminal_core";

    public static ItemStack createCore(ChurchSMP plugin) {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component name = MiniMessage.miniMessage().deserialize(
                    "<!italic><gradient:#FFD700:#FF4500:#9400D3><bold>" + TextUtil.toSmallCaps("Liminal Event Core") + "</bold></gradient>"
            );
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("A pulsating cosmic anomaly forged", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            lore.add(Component.text("during celestial and unholy events.", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Event Catalyst", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(Component.text("  Required to forge Liminal Path Items:", NamedTextColor.YELLOW));
            lore.add(Component.text("  • Iniquity (Sins Path)", NamedTextColor.RED));
            lore.add(Component.text("  • Impiety (God Path)", NamedTextColor.AQUA));
            lore.add(Component.text("  • Obscura (Voided Path)", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_liminal_core"), PersistentDataType.BOOLEAN, true);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isLiminalCore(ItemStack item, ChurchSMP plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "is_liminal_core");
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}
