package dev.txadmin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Server Log untuk tab TXADMIN: join/leave, chat, kematian, aksi menu, ledakan, perintah, dan event sistem. */
public final class ServerLog implements Listener {
    private record Entry(String cat, long ts, String actor, String text) { }

    private final Deque<Entry> entries = new ArrayDeque<>();

    private static String clean(String s) { return s.replaceAll("[|\\u0001\\u0002\\n]", " "); }

    public void add(String cat, String actor, String text) {
        synchronized (entries) {
            entries.addLast(new Entry(cat, System.currentTimeMillis(), clean(actor == null ? "" : actor), clean(text)));
            while (entries.size() > 500) entries.removeFirst();
        }
    }

    public void clear() { synchronized (entries) { entries.clear(); } }

    /** Baris terbaru (maks ~26000 karakter) dalam format cat\u0002waktu\u0002pelaku\u0002teks dipisah \u0001. */
    public String encode() {
        SimpleDateFormat f = new SimpleDateFormat("hh:mm:ss a", Locale.US);
        List<String> out = new ArrayList<>();
        int size = 0;
        synchronized (entries) {
            var it = entries.descendingIterator();
            while (it.hasNext()) {
                Entry e = it.next();
                String line = e.cat() + "\u0002" + f.format(new Date(e.ts())) + "\u0002" + e.actor() + "\u0002" + e.text();
                if (size + line.length() > 26000) break;
                size += line.length() + 1;
                out.add(line);
            }
        }
        Collections.reverse(out);
        return String.join("\u0001", out);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { add("join", e.getPlayer().getName(), "joined the server"); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { add("join", e.getPlayer().getName(), "left the server"); }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        add("chat", e.getPlayer().getName(), "said: \"" + PlainTextComponentSerializer.plainText().serialize(e.message()) + "\"");
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) { add("death", e.getPlayer().getName(), "died"); }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) { add("command", e.getPlayer().getName(), "executed: " + e.getMessage()); }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        var l = e.getLocation();
        add("explosion", "", e.getEntityType().name().toLowerCase() + " exploded at " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
    }
}
