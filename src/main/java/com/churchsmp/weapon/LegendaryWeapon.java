package com.churchsmp.weapon;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.Alignment;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class LegendaryWeapon {

    protected final ChurchSMP plugin;
    protected final String id;
    protected final String[] legacyIds;
    protected final Component displayName;
    protected final Material baseMaterial;
    protected final Alignment requiredAlignment;
    protected final String primaryAbilityName;
    protected final String secondaryAbilityName;

    public LegendaryWeapon(ChurchSMP plugin, String id, String[] legacyIds, Component displayName,
                           Material baseMaterial, Alignment requiredAlignment,
                           String primaryAbilityName, String secondaryAbilityName) {
        this.plugin = plugin;
        this.id = id;
        this.legacyIds = legacyIds;
        this.displayName = displayName;
        this.baseMaterial = baseMaterial;
        this.requiredAlignment = requiredAlignment;
        this.primaryAbilityName = primaryAbilityName;
        this.secondaryAbilityName = secondaryAbilityName;
    }

    public String getId() {
        return id;
    }

    public String[] getLegacyIds() {
        return legacyIds;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public Alignment getRequiredAlignment() {
        return requiredAlignment;
    }

    public String getPrimaryAbilityName() {
        return primaryAbilityName;
    }

    public String getSecondaryAbilityName() {
        return secondaryAbilityName;
    }

    public net.kyori.adventure.bossbar.BossBar.Color getThemeBossBarColor() {
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "excalibur" -> net.kyori.adventure.bossbar.BossBar.Color.YELLOW;
            case "luminescence_spear" -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            case "mayim" -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            case "judas" -> net.kyori.adventure.bossbar.BossBar.Color.RED;
            case "sorrowess" -> net.kyori.adventure.bossbar.BossBar.Color.PURPLE;
            case "voidbreaker" -> net.kyori.adventure.bossbar.BossBar.Color.PURPLE;
            case "grim" -> net.kyori.adventure.bossbar.BossBar.Color.GREEN;
            default -> net.kyori.adventure.bossbar.BossBar.Color.WHITE;
        };
    }

    public String getThemeGradientTag() {
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "excalibur" -> "<gradient:#FFFFFF:#FFD700:#55FFFF>";
            case "luminescence_spear" -> "<gradient:#FFFFFF:#0055FF:#FFFFFF>";
            case "mayim" -> "<gradient:#FFFFFF:#00DFFF:#FFFFFF>";
            case "judas" -> "<gradient:#FF4444:#8B0000>";
            case "sorrowess" -> "<gradient:#FF7F7F:#8B0000>";
            case "voidbreaker" -> "<gradient:#8A2BE2:#D3D3D3:#4B0082>";
            case "grim" -> "<gradient:#2E8B57:#556B2F:#004d00>";
            default -> "<gradient:#FFFFFF:#FFD700>";
        };
    }

    public abstract ItemStack createItem();

    public abstract boolean executePrimary(Player player);

    public abstract boolean executeSecondary(Player player);

    /**
     * Optional custom active/charge status string for action bar (e.g. "1/3 5s(per charge)").
     */
    public String getCustomActiveStatus(Player player, boolean secondary) {
        return null;
    }

    public void onHit(Player attacker, LivingEntity target, double damage) {}

    public void onDamaged(Player victim, EntityDamageEvent event) {}

    public void onCrouch(Player player, boolean isSneaking) {}

    protected java.util.List<Component> buildCleanLore(java.util.List<String> passives, String primary, String secondary) {
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("---------------------------------", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        lore.add(Component.text("✦ Alignment: ", net.kyori.adventure.text.format.NamedTextColor.GRAY).append(requiredAlignment.getFormattedComponent()));
        lore.add(Component.empty());
        lore.add(Component.text("Passives:", net.kyori.adventure.text.format.NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        for (String p : passives) {
            lore.add(Component.text(" • " + p, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Abilities:", net.kyori.adventure.text.format.NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        lore.add(Component.text(" [Primary] " + primary, net.kyori.adventure.text.format.NamedTextColor.GOLD));
        lore.add(Component.text(" [Secondary] " + secondary, net.kyori.adventure.text.format.NamedTextColor.GOLD));
        lore.add(Component.text("---------------------------------", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        return lore;
    }

    protected void applyStandardEnchants(org.bukkit.inventory.meta.ItemMeta meta) {
        meta.setUnbreakable(true);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 7, true);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 3, true);
        if (requiredAlignment == Alignment.GOOD) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.SMITE, 7, true);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
    }
}
