package com.gamerthuz.tpa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static com.gamerthuz.tpa.Messages.ph;

public final class TpaCommand implements CommandExecutor, TabCompleter {
    private final TpaPlugin plugin;

    public TpaCommand(TpaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        Messages m = plugin.messages();
        if (!(s instanceof Player p)) {
            if (cmd.getName().equalsIgnoreCase("tpa") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAll();
                m.send(s, "reloaded");
            } else {
                m.send(s, "player-only");
            }
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "tpa" -> tpa(p, args);
            case "tpahere" -> {
                if (args.length == 0) {
                    m.send(p, "usage-tpahere");
                } else {
                    Player t = find(p, args[0]);
                    if (t != null) plugin.requests().send(p, t, RequestManager.Type.HERE);
                }
            }
            case "tpaccept" -> {
                if (args.length == 0) {
                    plugin.requests().accept(p, null);
                } else {
                    Player t = find(p, args[0]);
                    if (t != null) plugin.requests().accept(p, t.getUniqueId());
                }
            }
            case "tpdeny" -> {
                if (args.length == 0) {
                    plugin.requests().deny(p, null);
                } else {
                    Player t = find(p, args[0]);
                    if (t != null) plugin.requests().deny(p, t.getUniqueId());
                }
            }
            case "tpcancel" -> plugin.requests().cancel(p);
            case "tpatoggle" -> toggle(p);
            default -> { }
        }
        return true;
    }

    private void tpa(Player p, String[] args) {
        Messages m = plugin.messages();
        if (args.length == 0) {
            Menus.players(plugin, p, 0);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "requests" -> Menus.requests(plugin, p);
            case "settings" -> Menus.settings(plugin, p);
            case "toggle" -> toggle(p);
            case "block", "unblock" -> {
                if (args.length < 2) return;
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (op == null) {
                    m.send(p, "player-not-found");
                } else if (op.getUniqueId().equals(p.getUniqueId())) {
                    m.send(p, "cannot-self");
                } else if (args[0].equalsIgnoreCase("block")) {
                    plugin.settings().block(p.getUniqueId(), op.getUniqueId());
                    m.send(p, "blocked", ph("player", String.valueOf(op.getName())));
                    plugin.requests().deny(p, op.getUniqueId());
                } else {
                    plugin.settings().unblock(p.getUniqueId(), op.getUniqueId());
                    m.send(p, "unblocked", ph("player", String.valueOf(op.getName())));
                }
            }
            case "reload" -> {
                if (!p.hasPermission("gamerthuz.tpa.admin")) {
                    m.send(p, "no-permission");
                    return;
                }
                plugin.reloadAll();
                m.send(p, "reloaded");
            }
            case "about" -> {
                p.sendMessage(Brand.prefix().append(net.kyori.adventure.text.Component.text(
                        plugin.getPluginMeta().getName() + " v" + plugin.getPluginMeta().getVersion())));
                p.sendMessage(Brand.footer());
            }
            default -> {
                Player t = find(p, args[0]);
                if (t != null) plugin.requests().send(p, t, RequestManager.Type.TO);
            }
        }
    }

    private void toggle(Player p) {
        boolean now = !plugin.settings().enabled(p.getUniqueId());
        plugin.settings().setEnabled(p.getUniqueId(), now);
        plugin.messages().send(p, now ? "toggle-on" : "toggle-off");
    }

    private Player find(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) plugin.messages().send(p, "player-not-found");
        return t;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase();
        if (args.length == 1) {
            if (name.equals("tpa")) out.addAll(List.of("requests", "settings", "toggle", "block", "unblock", "about"));
            if (name.equals("tpa") || name.equals("tpahere") || name.equals("tpaccept") || name.equals("tpdeny")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
            String prefix = args[0].toLowerCase();
            out.removeIf(x -> !x.toLowerCase().startsWith(prefix));
        } else if (args.length == 2 && name.equals("tpa")
                && (args[0].equalsIgnoreCase("block") || args[0].equalsIgnoreCase("unblock"))) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}
