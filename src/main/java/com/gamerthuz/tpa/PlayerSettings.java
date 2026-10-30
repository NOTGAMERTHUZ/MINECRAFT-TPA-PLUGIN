package com.gamerthuz.tpa;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Per-player settings saved in data.yml. */
public final class PlayerSettings {
    private final TpaPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PlayerSettings(TpaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    private static String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    public boolean enabled(UUID id) {
        return yml.getBoolean(path(id, "enabled"), true);
    }

    public void setEnabled(UUID id, boolean value) {
        yml.set(path(id, "enabled"), value);
        save();
    }

    public boolean popup(UUID id) {
        return yml.getBoolean(path(id, "popup"),
                plugin.getConfig().getBoolean("settings.open-gui-on-request", false));
    }

    public void setPopup(UUID id, boolean value) {
        yml.set(path(id, "popup"), value);
        save();
    }

    public Set<UUID> blocked(UUID id) {
        Set<UUID> out = new LinkedHashSet<>();
        for (String s : yml.getStringList(path(id, "blocked"))) {
            try {
                out.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        return out;
    }

    public boolean isBlocked(UUID owner, UUID other) {
        return blocked(owner).contains(other);
    }

    public void block(UUID owner, UUID other) {
        Set<UUID> set = blocked(owner);
        set.add(other);
        saveBlocked(owner, set);
    }

    public void unblock(UUID owner, UUID other) {
        Set<UUID> set = blocked(owner);
        set.remove(other);
        saveBlocked(owner, set);
    }

    private void saveBlocked(UUID owner, Set<UUID> set) {
        yml.set(path(owner, "blocked"), set.stream().map(UUID::toString).toList());
        save();
    }

    public synchronized void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
