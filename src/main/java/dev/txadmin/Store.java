package dev.txadmin;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/** Menyimpan ban dan riwayat (warn/kick/ban) di data.yml */
public final class Store implements Listener {
    private final TxAdmin p;
    private final File file;
    private final YamlConfiguration data;

    public Store(TxAdmin p) {
        this.p = p;
        this.file = new File(p.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try { data.save(file); } catch (IOException e) { p.getLogger().warning("Gagal simpan data.yml: " + e.getMessage()); }
    }

    public synchronized void ban(UUID id, String name, String reason, long until, String by, String banId) {
        String k = "bans." + id;
        data.set(k + ".name", name);
        data.set(k + ".reason", reason);
        data.set(k + ".until", until);
        data.set(k + ".by", by);
        data.set(k + ".id", banId);
        addHistory(id, "BAN", by, reason + " (" + untilText(until) + ", " + banId + ")");
    }

    public synchronized boolean unban(UUID id) {
        if (!data.contains("bans." + id)) return false;
        data.set("bans." + id, null);
        save();
        return true;
    }

    public synchronized boolean isBanned(UUID id) {
        String k = "bans." + id;
        if (!data.contains(k)) return false;
        long until = data.getLong(k + ".until", 0);
        if (until > 0 && until <= System.currentTimeMillis()) { data.set(k, null); save(); return false; }
        return true;
    }

    public synchronized void addHistory(UUID id, String type, String by, String reason) {
        List<String> l = new ArrayList<>(data.getStringList("history." + id));
        l.add(type + "|" + System.currentTimeMillis() + "|" + by + "|" + reason);
        data.set("history." + id, l);
        save();
    }

    public synchronized List<String> history(UUID id) {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy HH:mm");
        for (String s : data.getStringList("history." + id)) {
            String[] x = s.split("\\|", 4);
            if (x.length < 4) continue;
            out.add("<gray>" + f.format(new Date(Long.parseLong(x[1]))) + " <yellow>" + x[0] + " <gray>oleh <white>"
                    + p.esc(x[2]) + "<gray>: <white>" + p.esc(x[3]));
        }
        return out;
    }

    public synchronized String note(UUID id) { return data.getString("notes." + id, ""); }

    public synchronized void setNote(UUID id, String note) {
        data.set("notes." + id, note == null || note.isBlank() ? null : note);
        save();
    }

    public synchronized String key(UUID admin, String action) { return data.getString("keys." + admin + "." + action); }

    /** value null = hapus override (ikut default server). */
    public synchronized void setKeys(UUID admin, Map<String, String> values) {
        for (var e : values.entrySet()) data.set("keys." + admin + "." + e.getKey(), e.getValue());
        save();
    }

    public synchronized List<String> historyPlain(UUID id) {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy HH:mm");
        for (String s : data.getStringList("history." + id)) {
            String[] x = s.split("\\|", 4);
            if (x.length < 4) continue;
            out.add(f.format(new Date(Long.parseLong(x[1]))) + "  " + x[0] + " by " + x[2] + ": " + x[3]);
        }
        Collections.reverse(out);
        return out;
    }

    public static String untilText(long until) {
        return until <= 0 ? "Permanen" : new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(until));
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID id = e.getUniqueId();
        if (!isBanned(id)) return;
        String k = "bans." + id;
        Component c;
        synchronized (this) {
            c = p.plain("ban-screen",
                    "reason", data.getString(k + ".reason", "-"),
                    "until", untilText(data.getLong(k + ".until", 0)),
                    "by", data.getString(k + ".by", "-"),
                    "id", data.getString(k + ".id", "-"));
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, c);
    }
}
