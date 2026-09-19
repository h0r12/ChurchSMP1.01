package com.churchsmp;

import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.command.ChurchAdminCommand;
import com.churchsmp.command.ChurchCommand;
import com.churchsmp.cooldown.ActionBarCooldownTask;
import com.churchsmp.cooldown.BossBarManager;
import com.churchsmp.cooldown.CooldownManager;
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

    private AlignmentManager alignmentManager;
    private CooldownManager cooldownManager;
    private BossBarManager bossBarManager;
    private SinGemManager sinGemManager;
    private SinGemAbilityExecutor gemAbilityExecutor;
    private WeaponManager weaponManager;
    private ChurchRecipeManager recipeManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Configuration
            saveDefaultConfig();

            // Managers
            this.alignmentManager = new AlignmentManager(this);
            this.cooldownManager = new CooldownManager(this);
            this.bossBarManager = new BossBarManager(this);
            this.sinGemManager = new SinGemManager(this);
            this.gemAbilityExecutor = new SinGemAbilityExecutor(this);
            this.weaponManager = new WeaponManager(this);
            this.recipeManager = new ChurchRecipeManager(this);

            // Listeners
            Bukkit.getPluginManager().registerEvents(new InputListener(this), this);
            Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
            Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
            Bukkit.getPluginManager().registerEvents(this.recipeManager, this);

            // Commands
            if (getCommand("church") != null) {
                ChurchCommand churchCommand = new ChurchCommand(this);
                getCommand("church").setExecutor(churchCommand);
                getCommand("church").setTabCompleter(churchCommand);
            }
            if (getCommand("churchadmin") != null) {
                ChurchAdminCommand adminCommand = new ChurchAdminCommand(this);
                getCommand("churchadmin").setExecutor(adminCommand);
                getCommand("churchadmin").setTabCompleter(adminCommand);
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
}
