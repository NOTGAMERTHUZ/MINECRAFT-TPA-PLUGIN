package com.gamerthuz.tpa;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TpaPlugin extends JavaPlugin {
    private Messages messages;
    private PlayerSettings settings;
    private EconomyHook economy;
    private RequestManager requests;
    private WorldGroups groups;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Brand.banner(this);

        groups = WorldGroups.load(this);
        messages = new Messages(this);
        settings = new PlayerSettings(this);
        economy = new EconomyHook(this);
        requests = new RequestManager(this);

        getServer().getPluginManager().registerEvents(new TpaListener(this), this);

        TpaCommand handler = new TpaCommand(this);
        for (String name : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            }
        }
        requests.start();
    }

    @Override
    public void onDisable() {
        if (requests != null) requests.shutdown();
    }

    /** Run on the next tick (safe for opening menus from click events). */
    public void sync(Runnable task) {
        getServer().getScheduler().runTask(this, task);
    }

    public void reloadAll() {
        reloadConfig();
        groups = WorldGroups.load(this);
    }

    public WorldGroups groups() { return groups; }

    public Messages messages() { return messages; }

    public PlayerSettings settings() { return settings; }

    public EconomyHook economy() { return economy; }

    public RequestManager requests() { return requests; }
}
