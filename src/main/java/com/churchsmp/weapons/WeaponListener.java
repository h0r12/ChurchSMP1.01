package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Activation model:
 *  - Pressing the swap-hands key (default F) while holding a Church SMP
 *    weapon in the main hand triggers Ability 1.
 *  - Holding sneak (shift) while pressing swap-hands triggers Ability 2.
 * The vanilla item swap is always cancelled — the key is fully repurposed
 * as the weapon's activation button.
 *
 * VoidBreaker is special-cased: its Ability 1 trigger fires one of two
 * completely different named abilities depending on Density/Breach mode,
 * each with its own cooldown, so it needs its own cooldown bucket (3)
 * separate from the shared "ability 1" bucket (1) everyone else uses —
 * otherwise using one would block the other on a shared timer.
 */
public class WeaponListener implements Listener {

    private static final int VOIDBREAKER_LIGHTLESS_PHOS_BUCKET = 3;

    private final ChurchSMP plugin;
    private final WeaponManager weaponManager;
    private final WeaponAbilities abilities;
    private final AlignmentManager alignmentManager;

    public WeaponListener(ChurchSMP plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getWeaponManager();
        this.abilities = plugin.getWeaponAbilities();
        this.alignmentManager = plugin.getAlignmentManager();
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        WeaponType type = weaponManager.getWeaponType(mainHand);

        if (type == null) {
            return; // not a Church SMP weapon, allow normal offhand swap
        }

        // Weapon activation always consumes the key press; never actually swap items.
        event.setCancelled(true);

        if (plugin.getNullifiedZoneManager().isNullified(player)) {
            actionBar(player, "Your powers are nullified right now.");
            return;
        }

        AlignmentTier tier = alignmentManager.getTier(player);
        if (!type.isUsableBy(tier)) {
            actionBar(player, "This weapon rejects your unworthy hand.");
            return;
        }

        int ability = player.isSneaking() ? 2 : 1;
        int cooldownBucket = resolveCooldownBucket(type, ability, mainHand);

        if (weaponManager.isOnCooldown(player, type, cooldownBucket)) {
            long remaining = weaponManager.getRemainingCooldownSeconds(player, type, cooldownBucket);
            actionBar(player, "On cooldown: " + remaining + "s");
            return;
        }

        int cooldownSeconds = abilities.execute(type, ability, player);
        if (cooldownSeconds > 0) {
            weaponManager.putOnCooldown(player, type, cooldownBucket, cooldownSeconds);
            CooldownBarDisplay.show(plugin, player, type.getDisplayName(), cooldownSeconds);
        }
    }

    /** Which cooldown bucket to check/set for this trigger — usually just the ability number, except VoidBreaker's split Ability 1. */
    private int resolveCooldownBucket(WeaponType type, int ability, ItemStack mainHand) {
        if (type == WeaponType.VOIDBREAKER && ability == 1) {
            return mainHand.containsEnchantment(Enchantment.BREACH) ? VOIDBREAKER_LIGHTLESS_PHOS_BUCKET : 1;
        }
        return ability;
    }

    private void actionBar(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.RED));
    }
}
