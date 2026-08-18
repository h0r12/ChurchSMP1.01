package com.churchsmp;

import com.churchsmp.alignment.AlignmentManager;
import com.churchsmp.commands.AlignmentCommand;
import com.churchsmp.commands.ChurchAdminCommand;
import com.churchsmp.commands.DonateCommand;
import com.churchsmp.listeners.DeedListener;
import com.churchsmp.listeners.EffectListener;
import com.churchsmp.sermon.SermonCommand;
import com.churchsmp.sermon.SermonManager;
import com.churchsmp.shrine.ShrineListener;
import com.churchsmp.shrine.ShrineManager;
import com.churchsmp.util.ChurchRegionManager;
import com.churchsmp.util.RegionWandListener;
import com.churchsmp.weapons.CraftingGuardListener;
import com.churchsmp.weapons.NullifiedZoneManager;
import com.churchsmp.weapons.WeaponAbilities;
import com.churchsmp.weapons.WeaponListener;
import com.churchsmp.weapons.WeaponManager;
import com.churchsmp.weapons.WeaponRecipeManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChurchSMP extends JavaPlugin {

    private AlignmentManager alignmentManager;
    private WeaponManager weaponManager;
    private WeaponAbilities weaponAbilities;
    private NullifiedZoneManager nullifiedZoneManager;
    private ChurchRegionManager churchRegionManager;
    private ShrineManager shrineManager;
    private SermonManager sermonManager;
    private RegionWandListener regionWandListener;
    private EffectListener effectListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        // Managers
        this.alignmentManager = new AlignmentManager(this);
        this.weaponManager = new WeaponManager(this);
        this.weaponAbilities = new WeaponAbilities(this);
        this.nullifiedZoneManager = new NullifiedZoneManager();
        this.churchRegionManager = new ChurchRegionManager(this);
        this.shrineManager = new ShrineManager(this);
        this.sermonManager = new SermonManager(this);
        this.regionWandListener = new RegionWandListener(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new DeedListener(this), this);
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new ShrineListener(this), this);
        getServer().getPluginManager().registerEvents(regionWandListener, this);
        getServer().getPluginManager().registerEvents(new CraftingGuardListener(this), this);
        this.effectListener = new EffectListener(this);
        getServer().getPluginManager().registerEvents(effectListener, this);
        effectListener.startTicking();

        // Recipes
        new WeaponRecipeManager(this).registerAll();

        // Commands
        getCommand("alignment").setExecutor(new AlignmentCommand(this));
        getCommand("donate").setExecutor(new DonateCommand(this));
        getCommand("churchadmin").setExecutor(new ChurchAdminCommand(this));
        getCommand("sermon").setExecutor(new SermonCommand(this));

        // Daily alignment decay — approximated as a real-time interval here;
        // hook this to a proper in-game day counter if your server changes day length.
        int decayIntervalMinutes = 20;
        getServer().getScheduler().runTaskTimer(this,
                alignmentManager::tickDailyDecay, 20L * 60 * decayIntervalMinutes, 20L * 60 * decayIntervalMinutes);

        getLogger().info("Church SMP enabled — " + com.churchsmp.weapons.WeaponType.values().length + " weapons registered.");
    }

    @Override
    public void onDisable() {
        if (alignmentManager != null) {
            alignmentManager.save();
        }
    }

    public AlignmentManager getAlignmentManager() { return alignmentManager; }
    public WeaponManager getWeaponManager() { return weaponManager; }
    public WeaponAbilities getWeaponAbilities() { return weaponAbilities; }
    public NullifiedZoneManager getNullifiedZoneManager() { return nullifiedZoneManager; }
    public ChurchRegionManager getChurchRegionManager() { return churchRegionManager; }
    public ShrineManager getShrineManager() { return shrineManager; }
    public SermonManager getSermonManager() { return sermonManager; }
    public RegionWandListener getRegionWandListener() { return regionWandListener; }
}
