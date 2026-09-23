package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.Grim;
import com.churchsmp.weapon.Judas;
import com.churchsmp.weapon.LegendaryWeapon;
import com.churchsmp.weapon.LuminescenceSpear;
import com.churchsmp.weapon.Sorrowess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class CombatListener implements Listener {

    private final ChurchSMP plugin;
    private final Random random = new Random();

    public CombatListener(ChurchSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Intercept attacks on Sorrowess illusion Phantom clones
        if (event.getEntity() instanceof Phantom phantom) {
            LegendaryWeapon sw = plugin.getWeaponManager().getWeapon("sorrowess");
            if (sw instanceof Sorrowess sorrowess) {
                // Check if this phantom is a sorrowess clone using the exposed key
                if (phantom.getPersistentDataContainer().has(sorrowess.getCloneKey(), PersistentDataType.STRING)) {
                    event.setCancelled(true);
                    sorrowess.multiplyClone(phantom, attacker);
                    return;
                }
            }
        }

        // Fallen effect check on attacker
        if (plugin.getFallenManager().isFallen(attacker)) {
            attacker.sendMessage(Component.text("✦ Your weapon powers and gems are suppressed by Fallen!", NamedTextColor.DARK_PURPLE));
            event.setDamage(event.getDamage() * 0.5);
            return;
        }

        // Fallen effect check on victim: armor weakened by 60%
        if (plugin.getFallenManager().isFallen(target)) {
            event.setDamage(event.getDamage() * 1.6);
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(item);

        if (weapon != null) {
            // Alignment check
            if (!plugin.getAlignmentManager().canWield(attacker, weapon.getRequiredAlignment())) {
                event.setCancelled(true);
                attacker.sendMessage(Component.text("✦ Your alignment clashes with this holy/unholy relic! Attack nullified.", NamedTextColor.RED));
                return;
            }

            // Weapon onHit passive
            weapon.onHit(attacker, target, event.getDamage());
        }

        // Alignment Smite scaling: Smite deals more damage than Sharpness for positive alignment
        if (plugin.getAlignmentManager().getAlignmentScore(attacker) > 0) {
            if (item != null && item.containsEnchantment(org.bukkit.enchantments.Enchantment.SMITE)) {
                double smiteBonus = plugin.getAlignmentManager().getSmiteBonusDamage(attacker);
                event.setDamage(event.getDamage() + smiteBonus);
            }
        }

        // Judas passives apply to EVERYTHING (any weapon, projectiles, melee)
        LegendaryWeapon judasW = plugin.getWeaponManager().getWeapon("judas");
        if (judasW instanceof Judas judas) {
            judas.triggerJudasPassives(attacker, target);
        }

        // Gem hit passives & multipliers
        plugin.getGemAbilityExecutor().onPlayerHitEntity(attacker, target);
        double multiplier = plugin.getGemAbilityExecutor().getDamageMultiplier(attacker);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            weapon.onDamaged(player, event);
        }

        plugin.getGemAbilityExecutor().onPlayerDamaged(player);
    }

    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Judas Bloodlust: cannot regenerate health while holding Judas
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon instanceof Judas) {
            event.setCancelled(true);
            return;
        }

        // Fallen debuff: cannot regenerate
        if (plugin.getFallenManager().isFallen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player shooter) {
            if (event.getHitEntity() instanceof LivingEntity victim) {
                LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(shooter.getInventory().getItemInMainHand());
                if (weapon instanceof LuminescenceSpear spear) {
                    spear.onTridentThrowHit(shooter, victim);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weaponItem = killer.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponItem);

        if (weapon instanceof Grim grim) {
            grim.addKill(killer, weaponItem);
        }
    }
}
