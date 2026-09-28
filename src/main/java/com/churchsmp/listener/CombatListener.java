package com.churchsmp.listener;

import com.churchsmp.ChurchSMP;
import com.churchsmp.weapon.Excalibur;
import com.churchsmp.weapon.Grim;
import com.churchsmp.weapon.Judas;
import com.churchsmp.weapon.LegendaryWeapon;
import com.churchsmp.weapon.LuminescenceSpear;
import com.churchsmp.weapon.Mayim;
import com.churchsmp.weapon.Sorrowess;
import com.churchsmp.weapon.VoidBreaker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        // Juggernaut knockback resistance perk
        if (target instanceof Player targetPlayer && plugin.getFinaleManager() != null && plugin.getFinaleManager().isJuggernaut(targetPlayer.getUniqueId())) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (targetPlayer.isOnline()) {
                    targetPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }, 1L);
        }

        // Mirror of Shame: reflected outgoing damage
        if (plugin.getGemAbilityExecutor().isInsideShameChamber(attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.damage(event.getDamage());
            attacker.sendMessage(Component.text("✦ Your attack was reflected back onto you by Mirror of Shame!", NamedTextColor.RED));
            return;
        }

        // Narcissus clone hit intercept
        if (plugin.getGemAbilityExecutor().handleCloneDamage(target, event.getDamage())) {
            event.setCancelled(true);
            return;
        }

        // Intercept attacks on Sorrowess illusion clones
        LegendaryWeapon sw = plugin.getWeaponManager().getWeapon("sorrowess");
        if (sw instanceof Sorrowess sorrowess) {
            if (target.getPersistentDataContainer().has(sorrowess.getCloneKey(), PersistentDataType.STRING)) {
                event.setCancelled(true);
                sorrowess.multiplyClone(target, attacker);
                return;
            }
        }

        // Gloom on target: accelerates armor durability drain
        if (sw instanceof Sorrowess sorrowess && sorrowess.hasGloom(target)) {
            if (target instanceof Player victimPlayer) {
                ItemStack[] armor = victimPlayer.getInventory().getArmorContents();
                boolean drained = false;
                for (ItemStack piece : armor) {
                    if (piece != null && piece.getType() != Material.AIR) {
                        if (piece.getItemMeta() instanceof Damageable dmgMeta && !dmgMeta.isUnbreakable()) {
                            dmgMeta.setDamage(dmgMeta.getDamage() + 3);
                            piece.setItemMeta(dmgMeta);
                            drained = true;
                        }
                    }
                }
                if (drained) {
                    target.getWorld().playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 0.6f);
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

            // Trident damage equivalent to Netherite Sword Sharpness 7 (minimum 13.0 damage)
            if (weapon instanceof Sorrowess || weapon instanceof LuminescenceSpear) {
                if (event.getDamage() < 13.0) {
                    event.setDamage(13.0);
                }
            }

            // Voidbreaker (Mace) damage boost: ensure it hits hard (minimum 12.0 base damage + 3.0 bonus damage)
            if (weapon instanceof VoidBreaker) {
                if (event.getDamage() < 12.0) {
                    event.setDamage(12.0);
                }
                event.setDamage(event.getDamage() + 3.0);
            }

            // Sorrowess Gloom Crit Tracking: every 5 crit hits put enemy on gloom
            if (weapon instanceof Sorrowess sorrowess) {
                boolean isCrit = attacker.getFallDistance() > 0.0F
                        && !attacker.isOnGround()
                        && !attacker.isClimbing()
                        && !attacker.isInWater()
                        && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS)
                        && attacker.getVehicle() == null
                        && attacker.getAttackCooldown() > 0.9F;
                if (isCrit) {
                    sorrowess.handleCritHit(attacker, target);
                }
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

        // Alignment Evil: Unholy melee bonus & Soul Leech
        if (plugin.getAlignmentManager().getAlignmentScore(attacker) < 0) {
            double unholyBonus = plugin.getAlignmentManager().getUnholyBonusDamage(attacker);
            if (unholyBonus > 0) {
                event.setDamage(event.getDamage() + unholyBonus);
            }
            if (plugin.getAlignmentManager().hasSoulLeech(attacker) && Math.random() < 0.20) {
                double healAmount = Math.min(attacker.getMaxHealth(), attacker.getHealth() + 2.0);
                attacker.setHealth(healAmount);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                attacker.getWorld().spawnParticle(Particle.SOUL, attacker.getLocation().add(0, 1.0, 0), 6, 0.2, 0.3, 0.2, 0.02);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.8f);
            }
        }

        // Judas passives apply to attacker's attacks if they possess Judas
        LegendaryWeapon judasW = plugin.getWeaponManager().getWeapon("judas");
        if (judasW instanceof Judas judas && plugin.getWeaponManager().hasWeapon(attacker, "judas")) {
            judas.triggerJudasPassives(attacker, target);
        }

        // Gem hit passives & multipliers
        plugin.getGemAbilityExecutor().onPlayerHitEntity(attacker, target, event);
        if (!attacker.isSneaking()) {
            plugin.getGemAbilityExecutor().handleLMBAttack(attacker, target, event);
        }
        plugin.getGemAbilityExecutor().checkTetherDamage(attacker, event.getDamage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(mainHand);
        if (weapon != null) {
            weapon.onDamaged(player, event);
        }

        plugin.getGemAbilityExecutor().onPlayerDamaged(player, event);
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

        plugin.getGemAbilityExecutor().onPlayerKill(killer, event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        String deathMsg = null;

        if (killer != null) {
            // Finale Purge & Bounty Purge kill handling
            if (plugin.getFinaleManager() != null) {
                plugin.getFinaleManager().onPlayerKill(killer, victim);
            }

            ItemStack weaponItem = killer.getInventory().getItemInMainHand();
            LegendaryWeapon weapon = plugin.getWeaponManager().getWeapon(weaponItem);

            if (plugin.getFinaleManager() != null && plugin.getFinaleManager().isJuggernaut(killer.getUniqueId())) {
                deathMsg = "<dark_red>☠</dark_red> <white>" + victim.getName() + "</white> <gray>was purified into a</gray> <white><bold>Lost Soul</bold></white> <gray>by the colossal might of the Juggernaut</gray> <red><bold>" + killer.getName() + "</bold></red>";
            } else if (weapon instanceof Sorrowess sorrowess) {
                if (sorrowess.hasGloom(victim)) {
                    deathMsg = "<dark_purple>☠</dark_purple> <white>" + victim.getName() + "</white> <gray>was shattered in the abyss by</gray> <gradient:#FFFFFF:#FF7F7F:#8B0000><bold>" + killer.getName() + "'s Sorrowess</bold></gradient> <dark_purple>[Gloom Shatter]</dark_purple>";
                } else if (sorrowess.isBleedingOut(victim)) {
                    deathMsg = "<dark_red>☠</dark_red> <white>" + victim.getName() + "</white> <gray>bled to death from</gray> <gradient:#FFFFFF:#FF7F7F:#8B0000><bold>" + killer.getName() + "'s Sorrowess</bold></gradient> <red>[Bleedout]</red>";
                } else {
                    deathMsg = "<dark_purple>☠</dark_purple> <white>" + victim.getName() + "</white> <gray>was pierced by</gray> <gradient:#FFFFFF:#FF7F7F:#8B0000><bold>" + killer.getName() + "'s Sorrowess</bold></gradient>";
                }
            } else if (weapon instanceof Excalibur) {
                deathMsg = "<gold>☠</gold> <white>" + victim.getName() + "</white> <gray>was smitten by the celestial fury of</gray> <gradient:#FFF8DC:#FFD700><bold>" + killer.getName() + "'s Excalibur</bold></gradient>";
            } else if (weapon instanceof Grim) {
                deathMsg = "<dark_gray>☠</dark_gray> <white>" + victim.getName() + "</white>'s <gray>soul was severed into eternity by</gray> <gradient:#8B0000:#2F4F4F><bold>" + killer.getName() + "'s Grim</bold></gradient>";
            } else if (weapon instanceof VoidBreaker) {
                deathMsg = "<dark_purple>☠</dark_purple> <white>" + victim.getName() + "</white> <gray>was fractured across the void by</gray> <gradient:#9400D3:#8A2BE2><bold>" + killer.getName() + "'s VoidBreaker</bold></gradient>";
            } else if (weapon instanceof Mayim) {
                deathMsg = "<aqua>☠</aqua> <white>" + victim.getName() + "</white> <gray>was frozen to the core by</gray> <gradient:#00FFFF:#1E90FF><bold>" + killer.getName() + "'s Mayim</bold></gradient>";
            } else if (weapon instanceof Judas) {
                deathMsg = "<red>☠</red> <white>" + victim.getName() + "</white> <gray>was betrayed and slaughtered by</gray> <gradient:#8B0000:#FF0000><bold>" + killer.getName() + "'s Judas</bold></gradient>";
            } else if (weapon instanceof LuminescenceSpear) {
                deathMsg = "<gold>☠</gold> <white>" + victim.getName() + "</white> <gray>was impaled by blinding celestial light from</gray> <gradient:#FFD700:#FFF8DC><bold>" + killer.getName() + "'s Luminescence Spear</bold></gradient>";
            } else {
                com.churchsmp.gem.SinGemType gem = plugin.getSinGemManager().getHeldGem(killer);
                if (gem != null) {
                    deathMsg = "<dark_red>☠</dark_red> <white>" + victim.getName() + "</white> <gray>succumbed to the unholy sin of</gray> <red><bold>" + gem.getDisplayName() + "</bold></red> <gray>invoked by</gray> <white>" + killer.getName() + "</white>";
                }
            }
        } else {
            // Environmental/ability death without direct killer
            if (plugin.getFallenManager().isFallen(victim)) {
                deathMsg = "<dark_purple>☠</dark_purple> <white>" + victim.getName() + "</white> <gray>collapsed under the terminal weight of the</gray> <dark_purple><bold>Fallen Debuff</bold></dark_purple>";
            }
        }

        if (deathMsg != null) {
            event.deathMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(deathMsg));
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.7f);
        }
    }
}
