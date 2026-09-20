package com.churchsmp;

import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.command.ChurchAdminCommand;
import com.churchsmp.command.ChurchCommand;
import com.churchsmp.cooldown.ActionBarCooldownTask;
import com.churchsmp.cooldown.BossBarManager;
import com.churchsmp.cooldown.CooldownManager;
import com.churchsmp.effect.FallenEffectManager;
import com.churchsmp.gem.SinGemAbilityExecutor;
import com.churchsmp.gem.SinGemManager;
import com.churchsmp.listener.CombatListener;
import com.churchsmp.listener.InputListener;
import com.churchsmp.listener.PlayerListener;
import com.churchsmp.recipe.ChurchRecipeManager;
import com.churchsmp.weapon.WeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ChurchSMP extends JavaPlugin {

    private static ChurchSMP instance;

    private FallenEffectManager fallenManager;
    private AlignmentManager alignmentManager;
    private CooldownManager cooldownManager;
    private BossBarManager bossBarManager;
    private SinGemManager sinGemManager;
    private SinGemAbilityExecutor gemAbilityExecutor;
    private WeaponManager weaponManager;
    private ChurchRecipeManager recipeManager;
    private com.churchsmp.menu.WeaponMenuManager weaponMenuManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Configuration
            saveDefaultConfig();

            // Managers
            this.fallenManager = new com.churchsmp.effect.FallenEffectManager(this);
            this.alignmentManager = new AlignmentManager(this);
            this.cooldownManager = new CooldownManager(this);
            this.bossBarManager = new BossBarManager(this);
            this.sinGemManager = new SinGemManager(this);
            this.gemAbilityExecutor = new SinGemAbilityExecutor(this);
            this.weaponManager = new WeaponManager(this);
            this.recipeManager = new ChurchRecipeManager(this);
            this.weaponMenuManager = new com.churchsmp.menu.WeaponMenuManager(this);

            // Listeners
            Bukkit.getPluginManager().registerEvents(new InputListener(this), this);
            Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
            Bukkit.getPluginManager().registerEvents(this.recipeManager, this);
            Bukkit.getPluginManager().registerEvents(this.weaponMenuManager, this);

            // Commands: Register directly via CommandMap first (universal for Paper, Purpur, Spigot)
            ChurchCommand churchCommand = new ChurchCommand(this);
            ChurchAdminCommand adminCommand = new ChurchAdminCommand(this);

            try {
                org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();

                org.bukkit.command.Command fallbackChurch = new org.bukkit.command.Command("church", "ChurchSMP guide and information", "/church [guide|reroll]", java.util.List.of("csmp")) {
                    @Override
                    public boolean execute(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
                        return churchCommand.onCommand(sender, this, label, args);
                    }

                    @Override
                    public @org.jetbrains.annotations.NotNull java.util.List<String> tabComplete(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull String alias, @org.jetbrains.annotations.NotNull String[] args) {
                        java.util.List<String> list = churchCommand.onTabComplete(sender, this, alias, args);
                        return list != null ? list : super.tabComplete(sender, alias, args);
                    }
                };
                fallbackChurch.setPermission("churchsmp.use");
                commandMap.register("churchsmp", fallbackChurch);

                org.bukkit.command.Command fallbackAdmin = new org.bukkit.command.Command("churchadmin", "ChurchSMP Admin Commands", "/churchadmin [help|...]", java.util.List.of("ca", "csmpadmin")) {
                    @Override
                    public boolean execute(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
                        return adminCommand.onCommand(sender, this, label, args);
                    }

                    @Override
                    public @org.jetbrains.annotations.NotNull java.util.List<String> tabComplete(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull String alias, @org.jetbrains.annotations.NotNull String[] args) {
                        java.util.List<String> list = adminCommand.onTabComplete(sender, this, alias, args);
                        return list != null ? list : super.tabComplete(sender, alias, args);
                    }
                };
                fallbackAdmin.setPermission("churchsmp.admin");
                commandMap.register("churchsmp", fallbackAdmin);
            } catch (Throwable t) {
                getLogger().warning("Direct CommandMap registration notice: " + t.getMessage());
            }

            // Also bind to plugin.yml commands if available (safely catching Paper's UnsupportedOperationException)
            try {
                var c = getCommand("church");
                if (c != null) {
                    c.setExecutor(churchCommand);
                    c.setTabCompleter(churchCommand);
                }
                var ca = getCommand("churchadmin");
                if (ca != null) {
                    ca.setExecutor(adminCommand);
                    ca.setTabCompleter(adminCommand);
                }
            } catch (Throwable ignored) {
                // Paper throws an exception on getCommand() during startup if loaded as a paper-plugin — safely ignored
            }

            // Action bar real-time cooldown/active display task (runs every 2 ticks = 100ms)
            new ActionBarCooldownTask(this).runTaskTimer(this, 0L, 2L);

            getLogger().info("ChurchSMP v" + getDescription().getVersion() + " has been successfully enabled!");
        } catch (Throwable t) {
            getLogger().severe("Error while enabling ChurchSMP: " + t.getMessage());
            t.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("ChurchSMP has been disabled.");
    }

    public static ChurchSMP getInstance() {
        return instance;
    }

    public FallenEffectManager getFallenManager() {
        return fallenManager;
    }

    public AlignmentManager getAlignmentManager() {
        return alignmentManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public SinGemManager getSinGemManager() {
        return sinGemManager;
    }

    public SinGemAbilityExecutor getGemAbilityExecutor() {
        return gemAbilityExecutor;
    }

    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    public ChurchRecipeManager getRecipeManager() {
        return recipeManager;
    }

    public com.churchsmp.menu.WeaponMenuManager getWeaponMenuManager() {
        return weaponMenuManager;
    }
}
