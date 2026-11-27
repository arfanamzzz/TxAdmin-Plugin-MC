package dev.txadmin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * ID pemain bertambah sesuai urutan join (1, 2, 3, ...) dan tidak dipakai ulang.
 * ID TIDAK tampil di tab / nametag pemain biasa. Admin yang mengaktifkan "Player IDs" melihat
 * tulisan seperti [1]NamaPemain di atas kepala SEMUA pemain (termasuk dirinya sendiri) lewat
 * TextDisplay yang hanya terlihat oleh admin tersebut.
 * Admin juga bisa menyembunyikan nametag bawaan pemain lain (hanya di layarnya) lewat toggle "names".
 */
public final class Ids implements Listener {
    private final TxAdmin p;
    private final Map<UUID, Integer> ids = new HashMap<>();
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private final Set<UUID> on = new HashSet<>();
    private final Map<UUID, Scoreboard> hiders = new HashMap<>();
    private final Map<UUID, Long> joined = new HashMap<>();

    public Ids(TxAdmin p) {
        this.p = p;
        // bersihkan team "txN" sisa versi lama
        for (Team t : new ArrayList<>(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
            if (t.getName().matches("tx\\d+")) t.unregister();
        }
        Bukkit.getOnlinePlayers().forEach(this::assign);
        Bukkit.getScheduler().runTaskTimer(p, this::refresh, 20L, 20L);
    }

    public int idOf(Player pl) { return ids.getOrDefault(pl.getUniqueId(), 0); }

    /** Nomor kosong terkecil: pemain yang keluar lalu masuk lagi di server kosong tetap mendapat 1. */
    private void assign(Player pl) {
        int id = 1;
        while (ids.containsValue(id)) id++;
        ids.put(pl.getUniqueId(), id);
        joined.put(pl.getUniqueId(), System.currentTimeMillis());
    }

    public long joinedAt(Player pl) { return joined.getOrDefault(pl.getUniqueId(), System.currentTimeMillis()); }

    // ---------- Player IDs ----------
    public boolean isOn(Player a) { return on.contains(a.getUniqueId()); }

    public boolean toggle(Player a) {
        boolean nowOn = on.add(a.getUniqueId());
        if (!nowOn) on.remove(a.getUniqueId());
        refresh();
        return nowOn;
    }

    // ---------- sembunyikan nametag bawaan (hanya untuk admin ini) ----------
    public boolean isHidingNames(Player a) { return hiders.containsKey(a.getUniqueId()); }

    public boolean toggleNames(Player a) {
        boolean now = !isHidingNames(a);
        setHidden(a, now);
        return now;
    }

    public void setHidden(Player a, boolean hide) {
        UUID id = a.getUniqueId();
        if (!hide) {
            if (hiders.remove(id) != null) a.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        if (hiders.containsKey(id)) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Team t = sb.registerNewTeam("hide");
        t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        hiders.put(id, sb);
        a.setScoreboard(sb);
        syncHidden();
    }

    private void syncHidden() {
        for (var en : hiders.entrySet()) {
            Team t = en.getValue().getTeam("hide");
            if (t == null) continue;
            for (Player x : Bukkit.getOnlinePlayers()) {
                if (!t.hasEntry(x.getName())) t.addEntry(x.getName());
            }
        }
    }

    public void shutdown() {
        tags.values().forEach(d -> { if (d.isValid()) d.remove(); });
        tags.clear();
        for (UUID id : new ArrayList<>(hiders.keySet())) {
            Player a = Bukkit.getPlayer(id);
            if (a != null) a.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        hiders.clear();
    }

    // ---------- tulisan [ID]Nama di atas kepala ----------
    private TextDisplay spawn(Player t) {
        float off = (float) p.getConfig().getDouble("ids.height-offset", 0.35);
        String fmt = p.getConfig().getString("ids.format", "<yellow>[{id}]</yellow><white>{name}");
        Component text = p.mm(fmt.replace("{id}", String.valueOf(idOf(t))).replace("{name}", p.esc(t.getName())));
        TextDisplay d = t.getWorld().spawn(t.getLocation(), TextDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.text(text);
            e.setTransformation(new Transformation(new Vector3f(0f, off, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
        });
        t.addPassenger(d);
        return d;
    }

    private void refresh() {
        hiders.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
        syncHidden();
        on.removeIf(u -> Bukkit.getPlayer(u) == null);
        if (on.isEmpty()) {
            tags.values().forEach(d -> { if (d.isValid()) d.remove(); });
            tags.clear();
            return;
        }
        boolean showSelf = p.getConfig().getBoolean("ids.show-self", true);
        for (Player t : Bukkit.getOnlinePlayers()) {
            TextDisplay d = tags.get(t.getUniqueId());
            if (d == null || !d.isValid() || !d.getWorld().equals(t.getWorld()) || !t.getPassengers().contains(d)) {
                if (d != null && d.isValid()) d.remove();
                d = spawn(t);
                tags.put(t.getUniqueId(), d);
            }
            for (Player v : Bukkit.getOnlinePlayers()) {
                boolean self = v.equals(t);
                if (on.contains(v.getUniqueId()) && (!self || showSelf)) v.showEntity(p, d);
                else v.hideEntity(p, d);
            }
        }
    }

    /** Cari pemain berdasarkan ID, nama persis, atau awalan nama. */
    public Player find(String s) {
        if (s.matches("\\d+")) {
            int id = Integer.parseInt(s);
            for (Player pl : Bukkit.getOnlinePlayers()) if (idOf(pl) == id) return pl;
        }
        Player exact = Bukkit.getPlayerExact(s);
        if (exact != null) return exact;
        for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getName().toLowerCase().startsWith(s.toLowerCase())) return pl;
        return null;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { assign(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        TextDisplay d = tags.remove(id);
        if (d != null && d.isValid()) d.remove();
        ids.remove(id);
        joined.remove(id);
        on.remove(id);
        hiders.remove(id);
    }
}
