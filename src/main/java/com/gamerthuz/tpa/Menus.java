package com.gamerthuz.tpa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.gamerthuz.tpa.Messages.ph;

/** Every GUI screen of the plugin. */
public final class Menus {
    private static final String TITLE = "<gradient:#ff9933:#138808><bold>TPA</bold></gradient> <dark_gray>» <white>";

    private Menus() {}

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    private static String distance(Player a, Player b) {
        if (a.getWorld().equals(b.getWorld())) {
            return "<gray>Distance: <white>" + (int) a.getLocation().distance(b.getLocation()) + " blocks";
        }
        return "<gray>Distance: <red>different world";
    }

    // ===================== PLAYERS =====================

    public static void players(TpaPlugin plugin, Player p, int page) {
        if (!plugin.groups().allowed(p.getWorld())) {
            plugin.messages().send(p, "world-disabled");
            return;
        }
        List<Player> list = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        list.remove(p);
        list.removeIf(t -> !plugin.groups().sameGroup(p.getWorld(), t.getWorld()));
        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        List<Integer> slots = Gui.innerSlots(6);
        int per = slots.size();
        int pages = Math.max(1, (list.size() + per - 1) / per);
        final int pg = Math.max(0, Math.min(page, pages - 1));

        MenuHolder h = Gui.create(TITLE + "Players <gray>(" + (pg + 1) + "/" + pages + ")", 6);
        Gui.frame(plugin, h, 6);

        if (list.isEmpty()) {
            h.set(22, Gui.item(Material.BARRIER, "<red><bold>No other players online",
                    List.of("<gray>Invite a friend to the server!"), false), null);
        }
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= list.size()) break;
            Player t = list.get(idx);
            boolean open = plugin.settings().enabled(t.getUniqueId());
            List<String> lore = List.of(
                    "",
                    "<gray>World: <white>" + t.getWorld().getName(),
                    distance(p, t),
                    "<gray>Ping: <white>" + t.getPing() + "ms",
                    open ? "<green>● Accepting requests" : "<red>● Requests disabled",
                    "",
                    "<yellow>Left-click <gray>» <white>teleport to them",
                    "<yellow>Right-click <gray>» <white>bring them to you");
            h.set(slots.get(i), Gui.head(t, "<white><bold>" + t.getName(), lore), e -> {
                if (!t.isOnline()) {
                    plugin.sync(() -> players(plugin, p, pg));
                    return;
                }
                p.closeInventory();
                plugin.requests().send(p, t,
                        e.isRightClick() ? RequestManager.Type.HERE : RequestManager.Type.TO);
            });
        }

        // bottom controls
        int incoming = plugin.requests().incoming(p.getUniqueId()).size();
        h.set(46, Gui.item(Material.WRITABLE_BOOK, "<gold><bold>Incoming requests <white>(" + incoming + ")",
                List.of("<gray>Accept or deny requests.", "", "<yellow>Click to open"), incoming > 0),
                e -> plugin.sync(() -> requests(plugin, p)));
        h.set(47, Gui.item(Material.COMPARATOR, "<aqua><bold>Settings",
                List.of("<gray>Toggle requests, popup, blocklist.", "", "<yellow>Click to open"), false),
                e -> plugin.sync(() -> settings(plugin, p)));
        if (pg > 0) {
            h.set(48, Gui.item(Material.ARROW, "<yellow><bold>« Previous page", List.of(), false),
                    e -> plugin.sync(() -> players(plugin, p, pg - 1)));
        }
        if (pg < pages - 1) {
            h.set(50, Gui.item(Material.ARROW, "<yellow><bold>Next page »", List.of(), false),
                    e -> plugin.sync(() -> players(plugin, p, pg + 1)));
        }
        RequestManager.Request out = plugin.requests().outgoing(p.getUniqueId());
        if (out != null) {
            h.set(51, Gui.item(Material.BARRIER, "<red><bold>Cancel request",
                    List.of("<gray>Pending request to <white>" + name(out.target()), "", "<yellow>Click to cancel"), false),
                    e -> {
                        plugin.requests().cancel(p);
                        plugin.sync(() -> players(plugin, p, pg));
                    });
        }
        h.set(52, Gui.item(Material.OAK_DOOR, "<red><bold>Close", List.of(), false), e -> p.closeInventory());

        p.openInventory(h.getInventory());
        Gui.sound(p, "block.ender_chest.open", 0.6f, 1.3f);
    }

    // ===================== INCOMING REQUESTS =====================

    public static void requests(TpaPlugin plugin, Player p) {
        List<RequestManager.Request> list = plugin.requests().incoming(p.getUniqueId());
        List<Integer> slots = Gui.innerSlots(6);

        MenuHolder h = Gui.create(TITLE + "Incoming Requests <gray>(" + list.size() + ")", 6);
        Gui.frame(plugin, h, 6);

        if (list.isEmpty()) {
            h.set(22, Gui.item(Material.BARRIER, "<red><bold>No pending requests",
                    List.of("<gray>Nobody has asked to teleport."), false), null);
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(list.size(), slots.size()); i++) {
            RequestManager.Request r = list.get(i);
            OfflinePlayer from = Bukkit.getOfflinePlayer(r.sender());
            Player online = Bukkit.getPlayer(r.sender());
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(r.type() == RequestManager.Type.TO
                    ? "<gray>Wants to teleport <gold>to you"
                    : "<gray>Wants you to teleport <gold>to them");
            lore.add("<gray>Expires in <white>" + Math.max(0, (r.expiresAt() - now) / 1000) + "s");
            if (online != null) lore.add(distance(p, online));
            lore.add("");
            lore.add("<yellow>Left-click <gray>» <green>accept");
            lore.add("<yellow>Right-click <gray>» <red>deny");
            h.set(slots.get(i), Gui.head(from, "<white><bold>" + name(r.sender()), lore), e -> {
                if (e.isRightClick()) {
                    plugin.requests().deny(p, r.sender());
                    plugin.sync(() -> requests(plugin, p));
                } else {
                    p.closeInventory();
                    plugin.requests().accept(p, r.sender());
                }
            });
        }

        h.set(48, Gui.item(Material.ARROW, "<yellow><bold>« Back", List.of(), false),
                e -> plugin.sync(() -> players(plugin, p, 0)));
        h.set(50, Gui.item(Material.RED_CONCRETE, "<red><bold>Deny all",
                List.of("<gray>Deny every pending request.", "", "<yellow>Click"), false), e -> {
                    plugin.requests().denyAll(p);
                    plugin.sync(() -> requests(plugin, p));
                });

        p.openInventory(h.getInventory());
    }

    // ===================== SETTINGS =====================

    public static void settings(TpaPlugin plugin, Player p) {
        PlayerSettings s = plugin.settings();
        UUID id = p.getUniqueId();
        boolean on = s.enabled(id);
        boolean popup = s.popup(id);
        int blockedCount = s.blocked(id).size();

        MenuHolder h = Gui.create(TITLE + "Settings", 3);
        Gui.frame(plugin, h, 3);

        h.set(11, Gui.item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                "<white><bold>Accept requests: " + (on ? "<green>ON" : "<red>OFF"),
                List.of("", "<gray>When OFF nobody can send you", "<gray>teleport requests.", "", "<yellow>Click to toggle"), on),
                e -> {
                    s.setEnabled(id, !on);
                    plugin.messages().send(p, !on ? "toggle-on" : "toggle-off");
                    plugin.sync(() -> settings(plugin, p));
                });
        h.set(13, Gui.item(Material.REPEATER,
                "<white><bold>Popup on request: " + (popup ? "<green>ON" : "<red>OFF"),
                List.of("", "<gray>Opens an Accept/Deny menu when", "<gray>someone sends you a request.", "", "<yellow>Click to toggle"), popup),
                e -> {
                    s.setPopup(id, !popup);
                    plugin.sync(() -> settings(plugin, p));
                });
        h.set(15, Gui.item(Material.SKELETON_SKULL, "<red><bold>Blocked players <white>(" + blockedCount + ")",
                List.of("", "<gray>Block with <white>/tpa block <player>", "", "<yellow>Click to manage"), false),
                e -> plugin.sync(() -> blocked(plugin, p)));
        h.set(21, Gui.item(Material.ARROW, "<yellow><bold>« Back", List.of(), false),
                e -> plugin.sync(() -> players(plugin, p, 0)));
        h.set(23, Gui.item(Material.OAK_DOOR, "<red><bold>Close", List.of(), false), e -> p.closeInventory());

        p.openInventory(h.getInventory());
    }

    // ===================== BLOCKED =====================

    public static void blocked(TpaPlugin plugin, Player p) {
        Set<UUID> set = plugin.settings().blocked(p.getUniqueId());
        List<UUID> list = new ArrayList<>(set);
        List<Integer> slots = Gui.innerSlots(5);

        MenuHolder h = Gui.create(TITLE + "Blocked Players", 5);
        Gui.frame(plugin, h, 5);

        if (list.isEmpty()) {
            h.set(22, Gui.item(Material.LIME_DYE, "<green><bold>Nobody is blocked", List.of(), false), null);
        }
        for (int i = 0; i < Math.min(list.size(), slots.size()); i++) {
            UUID other = list.get(i);
            h.set(slots.get(i), Gui.head(Bukkit.getOfflinePlayer(other), "<white><bold>" + name(other),
                    List.of("", "<yellow>Click <gray>» <green>unblock")), e -> {
                        plugin.settings().unblock(p.getUniqueId(), other);
                        plugin.messages().send(p, "unblocked", ph("player", name(other)));
                        plugin.sync(() -> blocked(plugin, p));
                    });
        }
        h.set(39, Gui.item(Material.ARROW, "<yellow><bold>« Back", List.of(), false),
                e -> plugin.sync(() -> settings(plugin, p)));

        p.openInventory(h.getInventory());
    }

    // ===================== POPUP CONFIRM =====================

    public static void confirm(TpaPlugin plugin, Player target, RequestManager.Request r) {
        OfflinePlayer from = Bukkit.getOfflinePlayer(r.sender());
        String fromName = name(r.sender());

        MenuHolder h = Gui.create(TITLE + "Request from " + fromName, 3);
        Gui.frame(plugin, h, 3);

        h.set(13, Gui.head(from, "<white><bold>" + fromName, List.of("",
                r.type() == RequestManager.Type.TO
                        ? "<gray>Wants to teleport <gold>to you"
                        : "<gray>Wants you to teleport <gold>to them")), null);
        h.set(11, Gui.item(Material.LIME_CONCRETE, "<green><bold>ACCEPT",
                List.of("", "<yellow>Click to accept"), false), e -> {
                    target.closeInventory();
                    plugin.requests().accept(target, r.sender());
                });
        h.set(15, Gui.item(Material.RED_CONCRETE, "<red><bold>DENY",
                List.of("", "<yellow>Click to deny"), false), e -> {
                    target.closeInventory();
                    plugin.requests().deny(target, r.sender());
                });
        h.set(21, Gui.item(Material.BARRIER, "<dark_red><bold>DENY & BLOCK",
                List.of("", "<gray>Deny and block <white>" + fromName), false), e -> {
                    target.closeInventory();
                    plugin.settings().block(target.getUniqueId(), r.sender());
                    plugin.requests().deny(target, r.sender());
                    plugin.messages().send(target, "blocked", ph("player", fromName));
                });
        h.set(23, Gui.item(Material.WRITABLE_BOOK, "<gold><bold>All requests",
                List.of("", "<yellow>Click to open"), false),
                e -> plugin.sync(() -> requests(plugin, target)));

        target.openInventory(h.getInventory());
    }
}
