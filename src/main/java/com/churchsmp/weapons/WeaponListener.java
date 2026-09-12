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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Activation model:
 *  - Pressing the swap-hands key (default F) while holding a Church SMP
 *    weapon in the main hand triggers Ability 1.
 *  - Holding sneak (shift) while pressing swap-hands triggers Ability 2.
 *  - Right-clicking (the "Use Item" button) does the same thing, so
 *    Bedrock/console/controller players — who have no swap-hands
 *    equivalent Geyser can map cleanly — can still use every weapon.
 *    Right-click on an actually-interactable block (a door, chest,
 *    button, crafting table, etc.) is left alone so players can still
 *    open things normally while holding the weapon; sneak-right-click
 *    always triggers Ability 2 regardless, matching vanilla's own
 *    sneak-to-bypass-interaction convention. On top of that, right-click
 *    only hijacks the click at all when the relevant ability is actually
 *    ready to fire (correct alignment, not nullified, off cooldown) — if
 *    it isn't, the click is left alone entirely and falls through to
 *    normal vanilla behavior (axe strips a log, trident throws/riptides,
 *    etc.) instead of eating the input and showing a cooldown message.
 *    The swap-hands key, by contrast, is a dedicated ability button with
 *    no vanilla behavior worth preserving, so it always consumes the
 *    press and always reports why nothing happened.
 * The vanilla item swap (and, for right-click triggers, the weapon's own
 * vanilla use — throwing a trident, stripping a log with an axe, etc.) is
 * cancelled only when the ability actually fires.
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
        int ability = player.isSneaking() ? 2 : 1;
        activate(player, type, mainHand, ability, true);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // ignore the paired off-hand firing of this event
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        WeaponType type = weaponManager.getWeaponType(mainHand);
        if (type == null) return; // not a Church SMP weapon, allow normal interaction

        // Let a genuine door/chest/button/etc. still open normally when not
        // sneaking — only hijack the click when there's nothing block-side
        // to interact with, or the player is deliberately sneaking through it.
        boolean clickedInteractableBlock = event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable();
        if (clickedInteractableBlock && !player.isSneaking()) return;

        int ability = player.isSneaking() ? 2 : 1;
        if (!isReady(player, type, mainHand, ability)) {
            return; // not usable right now — leave the click alone, let vanilla behavior happen
        }

        event.setCancelled(true);
        activate(player, type, mainHand, ability, false);
    }

    /** True if this ability could actually fire right now — correct alignment, not nullified, off cooldown. */
    private boolean isReady(Player player, WeaponType type, ItemStack mainHand, int ability) {
        if (plugin.getNullifiedZoneManager().isNullified(player)) return false;
        if (!type.isUsableBy(alignmentManager.getTier(player))) return false;
        int cooldownBucket = resolveCooldownBucket(type, ability, mainHand);
        return !weaponManager.isOnCooldown(player, type, cooldownBucket);
    }

    /**
     * @param announceFailures whether to send a reason to the player when
     *                          the ability can't fire — true for the
     *                          swap-hands trigger (a dedicated button that
     *                          always deserves feedback), false for
     *                          right-click (isReady() already filtered
     *                          those cases out before the click was ever
     *                          hijacked, so this is just defense in depth).
     */
    private void activate(Player player, WeaponType type, ItemStack mainHand, int ability, boolean announceFailures) {
        if (plugin.getNullifiedZoneManager().isNullified(player)) {
            if (announceFailures) actionBar(player, "Your powers are nullified right now.");
            return;
        }

        AlignmentTier tier = alignmentManager.getTier(player);
        if (!type.isUsableBy(tier)) {
            if (announceFailures) actionBar(player, "This weapon rejects your unworthy hand.");
            return;
        }

        int cooldownBucket = resolveCooldownBucket(type, ability, mainHand);

        if (weaponManager.isOnCooldown(player, type, cooldownBucket)) {
            if (announceFailures) {
                long remaining = weaponManager.getRemainingCooldownSeconds(player, type, cooldownBucket);
                actionBar(player, "On cooldown: " + remaining + "s");
            }
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
