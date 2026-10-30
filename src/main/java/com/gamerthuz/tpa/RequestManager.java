package com.gamerthuz.tpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gamerthuz.tpa.Messages.ph;

public final class RequestManager {
    public enum Type { TO, HERE }

    public record Request(UUID sender, UUID target, Type type, long expiresAt) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TpaPlugin plugin;
    private final Map<UUID, Request> bySender = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();
    private BukkitTask expiryTask;

    public RequestManager(TpaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    public void shutdown() {
        if (expiryTask != null) expiryTask.cancel();
        for (BukkitTask t : warmups.values()) t.cancel();
        warmups.clear();
        bySender.clear();
    }

    // ---------- queries ----------

    public List<Request> incoming(UUID target) {
        long now = System.currentTimeMillis();
        return bySender.values().stream()
                .filter(r -> r.target().equals(target) && r.expiresAt() > now)
                .sorted(Comparator.comparingLong(Request::expiresAt))
                .toList();
    }

    public Request outgoing(UUID sender) {
        Request r = bySender.get(sender);
        return (r != null && r.expiresAt() > System.currentTimeMillis()) ? r : null;
    }

    public boolean isWarming(UUID id) {
        return warmups.containsKey(id);
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : "player";
    }

    /** True if both players may use TPA together; otherwise tells `notify` why. */
    private boolean worldsOk(Player a, Player b, Player notify) {
        WorldGroups g = plugin.groups();
        if (!g.allowed(a.getWorld()) || !g.allowed(b.getWorld())) {
            plugin.messages().send(notify, "world-disabled");
            return false;
        }
        if (!g.sameGroup(a.getWorld(), b.getWorld())) {
            plugin.messages().send(notify, "cross-world-blocked");
            return false;
        }
        return true;
    }

    private double cost(Player payer) {
        double amount = plugin.getConfig().getDouble("cost.amount", 0);
        if (amount <= 0 || payer.hasPermission("gamerthuz.tpa.bypass.cost") || !plugin.economy().available()) {
            return 0;
        }
        return amount;
    }

    // ---------- sending ----------

    public void send(Player from, Player to, Type type) {
        Messages m = plugin.messages();
        if (from.equals(to)) {
            m.send(from, "cannot-self");
            return;
        }
        if (!from.hasPermission("gamerthuz.tpa.use")) {
            m.send(from, "no-permission");
            return;
        }
        if (!worldsOk(from, to, from)) return;
        PlayerSettings s = plugin.settings();
        if (!s.enabled(to.getUniqueId()) || s.isBlocked(to.getUniqueId(), from.getUniqueId())) {
            m.send(from, "requests-disabled", ph("player", to.getName()));
            return;
        }
        long now = System.currentTimeMillis();
        if (!from.hasPermission("gamerthuz.tpa.bypass.cooldown")) {
            long left = cooldowns.getOrDefault(from.getUniqueId(), 0L) - now;
            if (left > 0) {
                m.send(from, "cooldown", ph("seconds", String.valueOf((left + 999) / 1000)));
                return;
            }
        }
        double cost = cost(from);
        if (cost > 0 && !plugin.economy().has(from, cost)) {
            m.send(from, "not-enough-money", ph("cost", plugin.economy().format(cost)));
            return;
        }

        Request old = bySender.remove(from.getUniqueId());
        if (old != null) {
            Player oldTarget = Bukkit.getPlayer(old.target());
            if (oldTarget != null) {
                m.send(oldTarget, "request-expired-target", ph("player", from.getName()));
            }
        }

        long timeout = Math.max(5, plugin.getConfig().getLong("settings.request-timeout", 60));
        Request r = new Request(from.getUniqueId(), to.getUniqueId(), type, now + timeout * 1000);
        bySender.put(from.getUniqueId(), r);
        cooldowns.put(from.getUniqueId(),
                now + Math.max(0, plugin.getConfig().getLong("settings.cooldown", 10)) * 1000);

        String secs = String.valueOf(timeout);
        m.send(from, type == Type.TO ? "request-sent-to" : "request-sent-here",
                ph("player", to.getName()), ph("seconds", secs));
        Gui.sound(from, "entity.experience_orb.pickup", 0.8f, 1.4f);

        notifyTarget(from, to, r);
    }

    private void notifyTarget(Player from, Player to, Request r) {
        Messages m = plugin.messages();
        Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept " + from.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept", NamedTextColor.GREEN)));
        Component deny = Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpdeny " + from.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny", NamedTextColor.RED)));

        to.sendMessage(m.get(r.type() == Type.TO ? "request-received-to" : "request-received-here",
                ph("player", from.getName())));
        to.sendMessage(Component.text("   ").append(accept).append(Component.text("  ")).append(deny));
        Gui.sound(to, "block.note_block.bell", 1f, 1.2f);

        if (plugin.settings().popup(to.getUniqueId())
                && to.getOpenInventory().getType() == InventoryType.CRAFTING) {
            plugin.sync(() -> {
                if (to.isOnline() && outgoing(from.getUniqueId()) != null) Menus.confirm(plugin, to, r);
            });
        }
    }

    // ---------- accept / deny / cancel ----------

    private Request resolve(Player target, UUID senderId) {
        if (senderId != null) {
            Request r = bySender.get(senderId);
            return (r != null && r.target().equals(target.getUniqueId())
                    && r.expiresAt() > System.currentTimeMillis()) ? r : null;
        }
        List<Request> list = incoming(target.getUniqueId());
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public void accept(Player target, UUID senderId) {
        Messages m = plugin.messages();
        Request r = resolve(target, senderId);
        if (r == null) {
            m.send(target, "no-pending");
            return;
        }
        bySender.remove(r.sender());
        Player sender = Bukkit.getPlayer(r.sender());
        if (sender == null) {
            m.send(target, "sender-offline");
            return;
        }
        if (!worldsOk(sender, target, target)) {
            m.send(sender, "request-denied-sender", ph("player", target.getName()));
            return;
        }
        Player traveler = r.type() == Type.TO ? sender : target;
        Player dest = r.type() == Type.TO ? target : sender;

        m.send(sender, "request-accepted-sender", ph("player", target.getName()));
        m.send(target, "request-accepted-target", ph("player", sender.getName()));
        beginTeleport(traveler, dest, sender);
    }

    public void deny(Player target, UUID senderId) {
        Messages m = plugin.messages();
        Request r = resolve(target, senderId);
        if (r == null) {
            m.send(target, "no-pending");
            return;
        }
        bySender.remove(r.sender());
        Player sender = Bukkit.getPlayer(r.sender());
        if (sender != null) m.send(sender, "request-denied-sender", ph("player", target.getName()));
        m.send(target, "request-denied-target", ph("player", name(r.sender())));
    }

    public void denyAll(Player target) {
        for (Request r : incoming(target.getUniqueId())) deny(target, r.sender());
    }

    public void cancel(Player sender) {
        Messages m = plugin.messages();
        Request r = outgoing(sender.getUniqueId());
        if (r == null) {
            m.send(sender, "no-outgoing");
            return;
        }
        bySender.remove(sender.getUniqueId());
        m.send(sender, "request-cancelled", ph("player", name(r.target())));
    }

    // ---------- teleport ----------

    private void beginTeleport(Player traveler, Player dest, Player payer) {
        cancelWarmup(traveler, false);
        int warmup = traveler.hasPermission("gamerthuz.tpa.bypass.warmup")
                ? 0 : Math.max(0, plugin.getConfig().getInt("settings.warmup", 3));
        UUID destId = dest.getUniqueId();
        UUID payerId = payer.getUniqueId();

        if (warmup <= 0) {
            teleportNow(traveler, destId, payerId);
            return;
        }
        plugin.messages().send(traveler, "warmup-start", ph("seconds", String.valueOf(warmup)));

        BukkitTask task = new BukkitRunnable() {
            int left = warmup;

            @Override
            public void run() {
                if (!traveler.isOnline()) {
                    warmups.remove(traveler.getUniqueId());
                    cancel();
                    return;
                }
                if (left <= 0) {
                    warmups.remove(traveler.getUniqueId());
                    cancel();
                    teleportNow(traveler, destId, payerId);
                    return;
                }
                traveler.showTitle(Title.title(
                        MM.deserialize("<gradient:#ff9933:#138808><bold>" + left + "</bold></gradient>"),
                        MM.deserialize("<gray>Don't move!"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(100))));
                Gui.sound(traveler, "block.note_block.hat", 1f, 1.5f);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        warmups.put(traveler.getUniqueId(), task);
    }

    public void cancelWarmup(Player p, boolean notify) {
        BukkitTask t = warmups.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            if (notify) {
                plugin.messages().send(p, "warmup-cancelled");
                p.clearTitle();
            }
        }
    }

    private void teleportNow(Player traveler, UUID destId, UUID payerId) {
        Messages m = plugin.messages();
        Player dest = Bukkit.getPlayer(destId);
        Player payer = Bukkit.getPlayer(payerId);
        if (dest == null || !dest.isOnline()) {
            m.send(traveler, "sender-offline");
            return;
        }
        if (!worldsOk(traveler, dest, traveler)) return;
        if (payer != null) {
            double cost = cost(payer);
            if (cost > 0) {
                if (!plugin.economy().withdraw(payer, cost)) {
                    m.send(payer, "not-enough-money", ph("cost", plugin.economy().format(cost)));
                    if (!payer.equals(traveler)) m.send(traveler, "teleport-failed");
                    return;
                }
                m.send(payer, "cost-charged", ph("cost", plugin.economy().format(cost)));
            }
        }

        Location from = traveler.getLocation().clone();
        Location to = dest.getLocation();
        traveler.teleportAsync(to).thenAccept(ok -> plugin.sync(() -> {
            if (!Boolean.TRUE.equals(ok)) {
                m.send(traveler, "teleport-failed");
                return;
            }
            from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
            traveler.getWorld().spawnParticle(Particle.PORTAL, traveler.getLocation().add(0, 1, 0),
                    40, 0.4, 0.8, 0.4, 0.3);
            Gui.sound(traveler, "entity.enderman.teleport", 1f, 1f);
            traveler.showTitle(Title.title(
                    MM.deserialize("<gradient:#ff9933:#138808><bold>Teleported!</bold></gradient>"),
                    MM.deserialize("<gray>to <white>" + dest.getName()),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
        }));
    }

    // ---------- housekeeping ----------

    private void expire() {
        long now = System.currentTimeMillis();
        for (Request r : new ArrayList<>(bySender.values())) {
            if (r.expiresAt() > now) continue;
            bySender.remove(r.sender());
            Player sender = Bukkit.getPlayer(r.sender());
            Player target = Bukkit.getPlayer(r.target());
            if (sender != null) plugin.messages().send(sender, "request-expired", ph("player", name(r.target())));
            if (target != null) plugin.messages().send(target, "request-expired-target", ph("player", name(r.sender())));
        }
    }

    public void cleanup(UUID id) {
        bySender.remove(id);
        bySender.values().removeIf(r -> r.target().equals(id));
        BukkitTask t = warmups.remove(id);
        if (t != null) t.cancel();
    }
}
