package com.churchsmp.item;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
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

public class RelicItem {

    public enum RelicType {
        INIQUITY(
                "iniquity",
                "<gradient:#FF0000:#8B0000><bold>Iniquity</bold></gradient>",
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                Alignment.EVIL,
                -100,
                "Relic of Sins and Depravity.",
                "Shifts soul to EVIL (-100) & unlocks Evil Relics."
        ),
        IMPIETY(
                "impiety",
                "<gradient:#FFFFFF:#FFD700><bold>Impiety</bold></gradient>",
                Material.RECOVERY_COMPASS,
                Alignment.GOOD,
                100,
                "Relic of Holiness and Virtue.",
                "Shifts soul to GOOD (+100) & unlocks Good Relics."
        ),
        OBSCURA(
                "obscura",
                "<gradient:#4B0082:#00CED1><bold>Obscura</bold></gradient>",
                Material.ECHO_SHARD,
                Alignment.NULLIFIED,
                0,
                "Relic of the Void and Neutrality.",
                "Shifts soul to NULLIFIED (0) & unlocks Nullified Relics."
        );

        private final String id;
        private final String displayName;
        private final Material material;
        private final Alignment alignment;
        private final int targetScore;
        private final String loreLine1;
        private final String loreLine2;

        RelicType(String id, String displayName, Material material, Alignment alignment, int targetScore, String loreLine1, String loreLine2) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.alignment = alignment;
            this.targetScore = targetScore;
            this.loreLine1 = loreLine1;
            this.loreLine2 = loreLine2;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public Alignment getAlignment() { return alignment; }
        public int getTargetScore() { return targetScore; }
        public String getLoreLine1() { return loreLine1; }
        public String getLoreLine2() { return loreLine2; }
    }

    public static ItemStack createRelic(ChurchSMP plugin, RelicType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<!italic>" + type.getDisplayName()));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            lore.add(Component.text(type.getLoreLine1(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            lore.add(Component.empty());
            lore.add(Component.text("✦ Right-Click: ", NamedTextColor.GOLD)
                    .append(Component.text(type.getLoreLine2(), NamedTextColor.YELLOW)));
            lore.add(Component.text("  Single-use item, consumed upon activation.", NamedTextColor.DARK_RED));
            lore.add(Component.text("---------------------------------", NamedTextColor.DARK_GRAY));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "relic_type"), PersistentDataType.STRING, type.getId());
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static RelicType getRelicType(ItemStack item, ChurchSMP plugin) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "relic_type");
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            // Check legacy is_impiety tag
            if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "is_impiety"), PersistentDataType.BOOLEAN)) {
                return RelicType.IMPIETY;
            }
            return null;
        }
        for (RelicType t : RelicType.values()) {
            if (t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
