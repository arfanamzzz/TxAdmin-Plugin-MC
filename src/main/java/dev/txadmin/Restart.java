package dev.txadmin;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Restart terjadwal dengan hitung mundur, mirip scheduled restart txAdmin. */
public final class Restart {
    private static final Set<Integer> WARN = Set.of(1800, 900, 600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1);
    private final TxAdmin p;
    private int remaining = -1;

    public Restart(TxAdmin p) { this.p = p; }

    public void start() { Bukkit.getScheduler().runTaskTimer(p, this::tick, 20L, 20L); }

    public boolean running() { return remaining >= 0; }
    public void begin(int seconds) { remaining = Math.max(1, seconds); }
    public void cancel() { remaining = -1; }

    private void tick() {
        if (remaining < 0) {
            if (!p.getConfig().getBoolean("restart.enabled", true)) return;
            int cd = p.getConfig().getInt("restart.countdown-seconds", 600);
            LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
            for (String t : p.getConfig().getStringList("restart.times")) {
                try {
                    if (LocalTime.parse(t).minusSeconds(cd).equals(now)) { begin(cd); break; }
                } catch (Exception ignored) { }
            }
            if (remaining < 0) return;
        }
        if (remaining == 0) { finish(); return; }
        if (WARN.contains(remaining)) announce(remaining);
        remaining--;
    }

    private void announce(int sec) {
        String time = sec >= 60 ? (sec / 60) + " menit" : sec + " detik";
        Bukkit.broadcast(p.plain("restart-warn", "time", time));
        p.actions.banner(Bukkit.getOnlinePlayers(), p.plain("restart-banner", "time", time),
                net.kyori.adventure.bossbar.BossBar.Color.RED, 8);
        if (sec <= 10) {
            for (Player x : Bukkit.getOnlinePlayers()) {
                x.showTitle(Title.title(p.mm("<red><bold>RESTART"), p.mm("<white>" + time)));
            }
        }
    }

    private void finish() {
        remaining = -1;
        for (Player x : Bukkit.getOnlinePlayers()) x.kick(p.plain("restart-kick"));
        Bukkit.getScheduler().runTaskLater(p, Bukkit::shutdown, 20L);
    }
}
