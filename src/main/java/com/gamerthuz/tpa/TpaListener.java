package com.gamerthuz.tpa;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TpaListener implements Listener {
    private final TpaPlugin plugin;

    public TpaListener(TpaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        holder.click(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.requests().isWarming(p.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("settings.cancel-on-move", true)) return;
        Location a = e.getFrom();
        Location b = e.getTo();
        if (b == null) return;
        if (a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ()) {
            return;
        }
        plugin.requests().cancelWarmup(p, true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.requests().isWarming(p.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("settings.cancel-on-damage", true)) return;
        plugin.requests().cancelWarmup(p, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.requests().cleanup(e.getPlayer().getUniqueId());
    }
}
