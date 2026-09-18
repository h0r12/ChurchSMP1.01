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

    public abstract ItemStack createItem();

    public abstract boolean executePrimary(Player player);

    public abstract boolean executeSecondary(Player player);

    public void onHit(Player attacker, LivingEntity target, double damage) {}

    public void onDamaged(Player victim, EntityDamageEvent event) {}

    public void onCrouch(Player player, boolean isSneaking) {}
}
