package dev.txadmin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public final class TxCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("players", "noclip", "god", "superjump", "heal", "feed", "tp", "bring",
            "back", "freeze", "spectate", "inv", "warn", "kick", "ban", "unban", "history", "announce", "dm", "effect",
            "clear", "whitelist", "restart", "ids", "names", "keys", "keybinds", "reload", "help");
    private static final Set<String> TAKES_PLAYER = Set.of("god", "superjump", "heal", "feed", "tp", "bring", "freeze",
            "spectate", "inv", "warn", "kick", "ban", "unban", "history", "dm", "effect", "clear");
    private static final List<String> EFFECTS = List.of("speed", "haste", "strength", "night_vision", "water_breathing",
            "fire_resistance", "invisibility", "slow_falling", "regeneration", "glowing", "saturation", "resistance",
            "jump_boost", "blindness", "slowness", "poison", "levitation");

    private final TxAdmin p;

    public TxCommand(TxAdmin p) { this.p = p; }

    private void usage(CommandSender s, String u) { s.sendMessage(p.msg("usage", "usage", u)); }

    /** Target wajib dari argumen. */
    private Player req(CommandSender s, String[] a, int i, String u) {
        if (a.length <= i) { usage(s, u); return null; }
        Player t = p.ids.find(a[i]);
        if (t == null) s.sendMessage(p.msg("player-not-found", "player", a[i]));
        return t;
    }

    /** Target dari argumen, atau diri sendiri bila tidak ada. */
    private Player tgt(CommandSender s, String[] a, int i) {
        if (a.length > i) return req(s, a, i, "");
        if (s instanceof Player me) return me;
        s.sendMessage(p.msg("players-only"));
        return null;
    }

    private Player me(CommandSender s) {
        if (s instanceof Player me) return me;
        s.sendMessage(p.msg("players-only"));
        return null;
    }

    private String join(String[] a, int from, String def) {
        return a.length > from ? String.join(" ", Arrays.copyOfRange(a, from, a.length)) : def;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!p.isAdmin(s)) { s.sendMessage(p.msg("no-permission")); return true; }
        if (s instanceof Player && a.length > 0 && !a[0].equalsIgnoreCase("keybinds") && !a[0].equalsIgnoreCase("players")) {
            s.sendMessage(p.msg("console-only"));
            return true;
        }
        if (a.length == 0) {
            if (s instanceof Player me) p.openMain(me); else help(s);
            return true;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "players" -> { Player me = me(s); if (me != null) p.openPlayers(me); }
            case "noclip" -> { Player me = me(s); if (me != null) p.actions.noclip(me); }
            case "god" -> { Player t = tgt(s, a, 1); if (t != null) p.actions.god(s, t); }
            case "superjump" -> { Player t = tgt(s, a, 1); if (t != null) p.actions.superjump(s, t); }
            case "heal" -> { Player t = tgt(s, a, 1); if (t != null) p.actions.heal(s, t); }
            case "feed" -> { Player t = tgt(s, a, 1); if (t != null) p.actions.feed(s, t); }
            case "freeze" -> { Player t = req(s, a, 1, "/tx freeze <pemain>"); if (t != null) p.actions.freeze(s, t); }
            case "tp" -> { Player me = me(s); Player t = me == null ? null : req(s, a, 1, "/tx tp <pemain>"); if (t != null) p.actions.tp(me, t); }
            case "bring" -> { Player me = me(s); Player t = me == null ? null : req(s, a, 1, "/tx bring <pemain>"); if (t != null) p.actions.bring(me, t); }
            case "back" -> { Player me = me(s); if (me != null) p.actions.back(me); }
            case "spectate" -> { Player me = me(s); Player t = me == null ? null : req(s, a, 1, "/tx spectate <pemain>"); if (t != null) p.actions.spectate(me, t); }
            case "inv" -> { Player me = me(s); Player t = me == null ? null : req(s, a, 1, "/tx inv <pemain>"); if (t != null) me.openInventory(t.getInventory()); }
            case "warn" -> {
                Player t = req(s, a, 1, "/tx warn <pemain> [alasan]");
                if (t != null) p.actions.warn(s, t, join(a, 2, p.getConfig().getString("default-reasons.warn", "-")));
            }
            case "kick" -> {
                Player t = req(s, a, 1, "/tx kick <pemain> [alasan]");
                if (t != null) p.actions.kick(s, t, join(a, 2, p.getConfig().getString("default-reasons.kick", "-")));
            }
            case "ban" -> {
                if (a.length < 3) { usage(s, "/tx ban <pemain> <30m|2h|7d|perm> [alasan]"); return true; }
                long dur = Actions.parseDuration(a[2]);
                if (dur < 0) { s.sendMessage(p.msg("invalid-duration")); return true; }
                Player on = p.ids.find(a[1]);
                OfflinePlayer t = on != null ? on : Bukkit.getOfflinePlayer(a[1]);
                String name = on != null ? on.getName() : a[1];
                p.actions.ban(s, t, name, dur, join(a, 3, p.getConfig().getString("default-reasons.ban", "-")));
            }
            case "unban" -> {
                if (a.length < 2) { usage(s, "/tx unban <nama>"); return true; }
                p.actions.unban(s, Bukkit.getOfflinePlayer(a[1]), a[1]);
            }
            case "history" -> {
                if (a.length < 2) { usage(s, "/tx history <nama>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                s.sendMessage(p.plain("history-header", "player", a[1]));
                List<String> h = p.store.history(t.getUniqueId());
                if (h.isEmpty()) s.sendMessage(p.plain("history-empty"));
                for (String line : h) s.sendMessage(p.mm(line));
            }
            case "announce" -> {
                if (a.length < 2) { usage(s, "/tx announce <pesan>"); return true; }
                p.actions.announce(s, join(a, 1, ""));
            }
            case "dm" -> {
                Player t = req(s, a, 1, "/tx dm <pemain> <pesan>");
                if (t == null) return true;
                if (a.length < 3) { usage(s, "/tx dm <pemain> <pesan>"); return true; }
                p.actions.dm(s, t, join(a, 2, ""));
            }
            case "effect" -> {
                Player t = req(s, a, 1, "/tx effect <pemain> <efek> [level] [detik]");
                if (t == null) return true;
                if (a.length < 3) { usage(s, "/tx effect <pemain> <efek> [level] [detik]"); return true; }
                int lvl = 1, sec = 0;
                try {
                    if (a.length > 3) lvl = Integer.parseInt(a[3]);
                    if (a.length > 4) sec = Integer.parseInt(a[4]);
                } catch (NumberFormatException ex) { usage(s, "/tx effect <pemain> <efek> [level] [detik]"); return true; }
                p.actions.effect(s, t, a[2].toLowerCase(), lvl, sec);
            }
            case "clear" -> {
                Player t = req(s, a, 1, "/tx clear <pemain>");
                if (t != null) { p.modes.clearAll(t); s.sendMessage(p.msg("effects-cleared", "player", t.getName())); }
            }
            case "whitelist" -> {
                if (a.length < 2) { usage(s, "/tx whitelist <on|off|add|remove> [nama]"); return true; }
                switch (a[1].toLowerCase()) {
                    case "on" -> { Bukkit.setWhitelist(true); s.sendMessage(p.msg("whitelist-on")); }
                    case "off" -> { Bukkit.setWhitelist(false); s.sendMessage(p.msg("whitelist-off")); }
                    case "add", "remove" -> {
                        if (a.length < 3) { usage(s, "/tx whitelist <add|remove> <nama>"); return true; }
                        boolean add = a[1].equalsIgnoreCase("add");
                        Bukkit.getOfflinePlayer(a[2]).setWhitelisted(add);
                        s.sendMessage(p.msg(add ? "whitelist-added" : "whitelist-removed", "player", a[2]));
                    }
                    default -> usage(s, "/tx whitelist <on|off|add|remove> [nama]");
                }
            }
            case "restart" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("cancel")) { p.actions.cancelRestart(s); return true; }
                int min = 5;
                try { if (a.length > 1) min = Integer.parseInt(a[1]); } catch (NumberFormatException ex) { usage(s, "/tx restart [menit|cancel]"); return true; }
                p.actions.restart(s, Math.max(1, min) * 60);
            }
            case "ids" -> { Player me = me(s); if (me != null) p.actions.toggleIds(me); }
            case "keybinds" -> { Player me = me(s); if (me != null) p.ui.openKeys(me); }
            case "names" -> { Player me = me(s); if (me != null) p.actions.toggleNames(me); }
            case "keys" -> { Player me = me(s); if (me != null) p.actions.toggleKeys(me); }
            case "reload" -> p.actions.reload(s);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(p.mm("<gradient:#00c6ff:#0072ff><bold>TxAdmin</bold></gradient> <gray>— daftar perintah"));
        String[] lines = {
                "/tx <gray>— buka menu",
                "/tx noclip | god [p] | superjump [p] | heal [p] | feed [p]",
                "/tx tp <p> | bring <p> | back | freeze <p> | spectate <p> | inv <p>",
                "/tx warn <p> [alasan] | kick <p> [alasan]",
                "/tx ban <p> <30m|2h|7d|perm> [alasan] | unban <nama> | history <nama>",
                "/tx announce <pesan> | dm <p> <pesan>",
                "/tx effect <p> <efek> [level] [detik] | clear <p>",
                "/tx whitelist <on|off|add|remove> [nama]",
                "/tx restart [menit|cancel] | ids | names | keys | reload"
        };
        for (String l : lines) s.sendMessage(p.mm("<yellow>" + l));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!p.isAdmin(s)) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(SUBS);
        } else {
            String sub = a[0].toLowerCase();
            if (a.length == 2 && TAKES_PLAYER.contains(sub)) {
                Bukkit.getOnlinePlayers().forEach(x -> out.add(x.getName()));
            } else if (a.length == 2 && sub.equals("whitelist")) {
                out.addAll(List.of("on", "off", "add", "remove"));
            } else if (a.length == 2 && sub.equals("restart")) {
                out.addAll(List.of("cancel", "1", "5", "10"));
            } else if (a.length == 3 && sub.equals("ban")) {
                out.addAll(p.getConfig().getStringList("ban-durations"));
            } else if (a.length == 3 && sub.equals("effect")) {
                out.addAll(EFFECTS);
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
