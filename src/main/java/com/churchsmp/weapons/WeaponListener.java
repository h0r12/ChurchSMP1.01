package com.churchsmp.weapons;

import com.churchsmp.ChurchSMP;
import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.alignment.AlignmentTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 */
public class WeaponListener implements Listener {

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

        if (weaponManager.isOnCooldown(player, type, ability)) {
            long remaining = weaponManager.getRemainingCooldownSeconds(player, type, ability);
            actionBar(player, "Ability " + ability + " on cooldown: " + remaining + "s");
            return;
        }

        abilities.execute(type, ability, player);
        weaponManager.putOnCooldown(player, type, ability, weaponManager.getConfiguredCooldown(ability));
    }

    private void actionBar(Player player, String text) {
        player.sendActionBar(Component.text(text, NamedTextColor.RED));
    }
}
