package dev.txadmin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Peringatan (warn): pemain tidak bisa bergerak selama beberapa detik, lalu diminta menekan SPASI
 * (lompat) untuk menutup peringatan, seperti txAdmin asli.
 */
public final class Warnings implements Listener {
    private final TxAdmin p;
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final Map<UUID, Boolean> wasFrozen = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public Warnings(TxAdmin p) { this.p = p; }

    public void show(Player t, String admin, String reason) {
        end(t, false);
        UUID id = t.getUniqueId();
        int wait = Math.max(1, p.getConfig().getInt("warn.dismiss-seconds", 10));
        wasFrozen.put(id, p.modes.frozen.contains(id));
        p.modes.frozen.add(id);
        readyAt.put(id, System.currentTimeMillis() + wait * 1000L);
        t.sendMessage(p.plain("warn-chat", "admin", admin, "reason", reason));
        p.actions.sound(t, "sounds.warn");

        if (p.bridge != null && p.bridge.hasMod(t)) {
            p.bridge.send(t, "warn|" + wait + "|" + admin + "|" + reason);
            return;
        }
        t.showTitle(Title.title(p.plain("warn-title"), p.plain("warn-subtitle", "reason", reason),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(300), Duration.ofMillis(500))));
        tasks.put(id, Bukkit.getScheduler().runTaskTimer(p, () -> {
            if (!t.isOnline()) { end(t, false); return; }
            long left = readyAt.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (left > 0) t.sendActionBar(p.plain("warn-wait", "seconds", String.valueOf(left / 1000 + 1)));
            else t.sendActionBar(p.plain("warn-dismiss"));
        }, 0L, 20L));
    }

    private void end(Player t, boolean notify) {
        UUID id = t.getUniqueId();
        if (readyAt.remove(id) == null) return;
        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();
        if (!wasFrozen.getOrDefault(id, false)) p.modes.frozen.remove(id);
        wasFrozen.remove(id);
        if (notify) {
            if (p.bridge != null && p.bridge.hasMod(t)) p.bridge.send(t, "warnend");
            else { t.clearTitle(); t.sendActionBar(Component.empty()); }
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player a = e.getPlayer();
        Long ready = readyAt.get(a.getUniqueId());
        if (ready == null || System.currentTimeMillis() < ready) return;
        if (e.getInput().isJump()) end(a, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { end(e.getPlayer(), false); }
}
