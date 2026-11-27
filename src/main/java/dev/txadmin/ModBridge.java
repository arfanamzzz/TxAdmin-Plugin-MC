package dev.txadmin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jembatan ke mod klien TxAdmin (channel txadmin:main). Format pesan: satu string UTF-8 "perintah|argumen".
 * Semua aksi dari mod tetap diperiksa di sini (harus admin).
 */
public final class ModBridge implements PluginMessageListener, Listener {
    public static final String CHANNEL = "txadmin:main";
    private static final Set<String> RIDEABLE = Set.of("horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "pig", "strider", "camel");

    private final TxAdmin p;
    private final Set<UUID> users = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastAnnounce = new HashMap<>();

    public ModBridge(TxAdmin p) { this.p = p; }

    public void register() {
        var m = p.getServer().getMessenger();
        m.registerIncomingPluginChannel(p, CHANNEL, this);
        m.registerOutgoingPluginChannel(p, CHANNEL);
    }

    public boolean hasMod(Player a) { return users.contains(a.getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { users.remove(e.getPlayer().getUniqueId()); }

    // ---------- kirim ----------
    public void send(Player a, String msg) { a.sendPluginMessage(p, CHANNEL, encode(msg)); }

    public void toast(Player a, String kind, String text) { send(a, "toast|" + kind + "|" + text); }

    public void sendState(Player a) {
        send(a, "state|mode=" + p.modes.mode(a) + ";ids=" + (p.ids.isOn(a) ? 1 : 0)
                + ";durs=" + String.join(",", p.getConfig().getStringList("ban-durations")));
    }

    private static String clean(String s) { return s.replaceAll("[|;,\\n\\u0001\\u0002]", "_"); }

    private void sendPlayers(Player a) {
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.sort(Comparator.comparingInt(p.ids::idOf));
        StringBuilder rows = new StringBuilder();
        for (Player t : list) {
            int dist = t.getWorld().equals(a.getWorld()) ? (int) a.getLocation().distance(t.getLocation()) : -1;
            if (rows.length() > 0) rows.append(';');
            rows.append(p.ids.idOf(t)).append(',').append(clean(t.getName())).append(',').append(dist).append(',')
                    .append((int) Math.ceil(t.getHealth())).append(',').append(p.isAdmin(t) ? 1 : 0);
        }
        send(a, "players|" + list.size() + "|" + Bukkit.getMaxPlayers() + "|" + rows);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m " + (s % 60) + "s";
    }

    private void sendInfo(Player a, Player t) {
        long session = System.currentTimeMillis() - p.ids.joinedAt(t);
        long played = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L * 1000L;
        String joined = t.getFirstPlayed() > 0 ? new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(t.getFirstPlayed())) : "-";
        String ip = t.getAddress() == null ? "-" : t.getAddress().getHostString();
        send(a, "info|" + p.ids.idOf(t) + "|" + clean(t.getName()) + "|" + fmt(session) + "|" + fmt(played) + "|" + joined
                + "|" + t.getUniqueId() + "|" + ip + "|" + p.store.note(t.getUniqueId()).replace('\n', ' '));
    }

    private void sendHistory(Player a, Player t) {
        List<String> lines = p.store.historyPlain(t.getUniqueId());
        send(a, "hist|" + p.ids.idOf(t) + "|" + String.join("\u0001", lines.stream().map(s -> s.replace('\u0001', ' ').replace('|', '/')).toList()));
    }

    private void sendConsole(Player a) {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        send(a, "console|" + String.format(Locale.US, "%.1f", tps) + "|" + Bukkit.getOnlinePlayers().size() + "|"
                + Bukkit.getMaxPlayers() + "|" + (p.restart.running() ? 1 : 0) + "|" + (Bukkit.hasWhitelist() ? 1 : 0)
                + "|" + p.slog.encode());
    }

    // ---------- terima ----------
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;
        final String msg;
        try { msg = decode(message); } catch (Exception ex) { return; }
        Bukkit.getScheduler().runTask(p, () -> handle(player, msg));
    }

    private Player find(Player a, String id) {
        Player t = p.ids.find(id.trim());
        if (t == null) toast(a, "error", "Player not found");
        return t;
    }

    private void handle(Player a, String msg) {
        int i = msg.indexOf('|');
        String cmd = i < 0 ? msg : msg.substring(0, i);
        String rest = i < 0 ? "" : msg.substring(i + 1);

        if (cmd.equals("hello")) { users.add(a.getUniqueId()); sendState(a); return; }
        if (!p.isAdmin(a)) { toast(a, "error", "You are not an admin"); return; }

        switch (cmd) {
            case "state" -> sendState(a);
            case "players" -> sendPlayers(a);
            case "console" -> sendConsole(a);
            case "mode" -> {
                if (!rest.matches("normal|noclip|god|jump")) return;
                p.actions.setMode(a, rest);
                if (rest.equals("normal")) toast(a, "success", "Returned to default player mode");
                sendState(a);
            }
            case "heal" -> {
                String[] s = rest.split("\\|", 2);
                switch (s[0]) {
                    case "everyone" -> { p.actions.healAll(a); toast(a, "success", "Healed all players"); }
                    case "player" -> {
                        Player t = s.length > 1 ? find(a, s[1]) : null;
                        if (t == null) return;
                        p.actions.heal(a, t);
                        toast(a, "success", "Healed " + t.getName());
                    }
                    default -> { p.actions.heal(a, a); toast(a, "success", "Healed yourself"); }
                }
            }
            case "tp" -> {
                Player t = find(a, rest);
                if (t == null) return;
                p.actions.tp(a, t);
                toast(a, "success", "Teleported to " + t.getName());
            }
            case "tpc" -> teleportCoords(a, rest);
            case "tpx" -> {
                Location l = p.modes.back.remove(a.getUniqueId());
                if (l == null) { toast(a, "error", "No previous location"); return; }
                p.modes.back.put(a.getUniqueId(), a.getLocation());
                a.teleport(l);
                toast(a, "success", "Teleported back");
            }
            case "copycoords" -> {
                Location l = a.getLocation();
                send(a, "clip|" + String.format(Locale.US, "%.2f, %.2f, %.2f", l.getX(), l.getY(), l.getZ()));
            }
            case "announce" -> {
                if (rest.isBlank()) { toast(a, "error", "Write a message first"); return; }
                long now = System.currentTimeMillis();
                Long last = lastAnnounce.put(a.getUniqueId() + ":" + rest.trim(), now);
                if (last != null && now - last < 3000) return;
                p.actions.announce(a, rest.trim());
            }
            case "ids" -> {
                boolean on = p.ids.toggle(a);
                toast(a, "info", on ? "Showing nearby player IDs." : "Hiding nearby player IDs.");
                sendState(a);
            }
            case "info" -> {
                Player t = find(a, rest);
                if (t != null) sendInfo(a, t);
            }
            case "hist" -> {
                Player t = find(a, rest);
                if (t != null) sendHistory(a, t);
            }
            case "note" -> {
                String[] s = rest.split("\\|", 2);
                Player t = find(a, s[0]);
                if (t == null) return;
                p.store.setNote(t.getUniqueId(), s.length > 1 ? s[1].trim() : "");
                toast(a, "success", "Note saved");
            }
            case "act" -> act(a, rest);
            case "ban" -> {
                String[] s = rest.split("\\|", 3);
                Player t = find(a, s[0]);
                if (t == null || s.length < 2) return;
                long ms = Actions.parseDuration(s[1].trim());
                if (ms < 0) { toast(a, "error", "Invalid duration"); return; }
                String reason = s.length > 2 && !s[2].isBlank() ? s[2].trim() : p.getConfig().getString("default-reasons.ban", "-");
                p.actions.ban(a, t, t.getName(), ms, reason);
                toast(a, "success", "Player banned");
            }
            case "veh" -> vehicle(a, rest);
            case "resetarea" -> {
                int r;
                try { r = Integer.parseInt(rest.trim()); } catch (NumberFormatException ex) { toast(a, "error", "Invalid radius"); return; }
                r = Math.max(0, Math.min(300, r));
                int n = 0;
                for (Entity e : a.getNearbyEntities(r, r, r)) {
                    if (e instanceof Player) continue;
                    if (e instanceof Item || e instanceof Projectile || e instanceof Monster || e instanceof ExperienceOrb) { e.remove(); n++; }
                }
                p.actions.log(a.getName() + " cleared an area with " + r + "m radius (" + n + " entities)");
                toast(a, "success", "Cleaning area with radius of " + r + "m.");
            }
            case "srv" -> {
                String[] s = rest.split("\\|", 2);
                switch (s[0]) {
                    case "restart" -> {
                        int m = 5;
                        try { if (s.length > 1) m = Integer.parseInt(s[1].trim()); } catch (NumberFormatException ex) { toast(a, "error", "Invalid number of minutes"); return; }
                        m = Math.max(1, Math.min(60, m));
                        p.actions.restart(a, m * 60);
                        toast(a, "success", "Server restart scheduled in " + m + " minute(s)");
                    }
                    case "cancel" -> { p.actions.cancelRestart(a); toast(a, "success", "Restart cancelled"); }
                    case "whitelist" -> {
                        p.actions.toggleWhitelist(a);
                        toast(a, "info", Bukkit.hasWhitelist() ? "Whitelist enabled" : "Whitelist disabled");
                    }
                    case "clearlog" -> { p.slog.clear(); toast(a, "success", "Console cleared"); }
                    default -> { }
                }
                sendConsole(a);
            }
            default -> { }
        }
    }

    private void teleportCoords(Player a, String text) {
        String[] parts = text.trim().split("[,\\s]+");
        if (parts.length < 3) { toast(a, "error", "Use the x, y, z format"); return; }
        try {
            double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
            p.modes.back.put(a.getUniqueId(), a.getLocation());
            a.teleport(new Location(a.getWorld(), x, y, z, a.getLocation().getYaw(), a.getLocation().getPitch()));
            toast(a, "success", "Teleported to the provided coordinates");
        } catch (NumberFormatException ex) {
            toast(a, "error", "Invalid coordinates");
        }
    }

    private void vehicle(Player a, String rest) {
        String[] s = rest.split("\\|", 2);
        switch (s[0]) {
            case "spawn" -> {
                String name = (s.length > 1 ? s[1] : "").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (name.equals("boat")) name = "oak_boat";
                if (name.isEmpty() || !name.matches("[a-z_]+")) { toast(a, "error", "Invalid vehicle name"); return; }
                EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(name));
                Class<?> c = type == null ? null : type.getEntityClass();
                boolean ok = c != null && (Vehicle.class.isAssignableFrom(c) || RIDEABLE.contains(name));
                if (!ok) { toast(a, "error", "Unknown vehicle model: " + name); return; }
                Entity v = a.getWorld().spawnEntity(a.getLocation(), type);
                if (v instanceof AbstractHorse h) {
                    h.setTamed(true);
                    h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                } else if (v instanceof Steerable st) {
                    st.setSaddle(true);
                }
                v.addPassenger(a);
                p.actions.log(a.getName() + " spawned a vehicle (" + name + ")");
                toast(a, "success", "Vehicle spawned: " + name);
            }
            case "fix" -> {
                Entity v = a.getVehicle();
                if (v == null) { toast(a, "error", "You are not in a vehicle"); return; }
                if (v instanceof LivingEntity le && le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                    le.setHealth(le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                }
                toast(a, "success", "Vehicle repaired");
            }
            case "delete" -> {
                Entity v = a.getVehicle();
                if (v == null) { toast(a, "error", "You are not in a vehicle"); return; }
                v.remove();
                p.actions.log(a.getName() + " deleted a vehicle");
                toast(a, "success", "Vehicle deleted");
            }
            case "boost" -> {
                Entity v = a.getVehicle();
                if (v == null) { toast(a, "error", "You are not in a vehicle"); return; }
                Vector dir = a.getLocation().getDirection().setY(0).normalize().multiply(1.6);
                v.setVelocity(v.getVelocity().add(dir));
                toast(a, "success", "Vehicle boosted");
            }
            default -> { }
        }
    }

    /** act|aksi|id|teks */
    private void act(Player a, String rest) {
        String[] s = rest.split("\\|", 3);
        if (s.length < 2) return;
        Player t = find(a, s[1]);
        if (t == null) return;
        String text = s.length > 2 ? s[2].trim() : "";
        switch (s[0]) {
            case "dm" -> {
                if (text.isEmpty()) { toast(a, "error", "Write a message first"); return; }
                p.actions.dm(a, t, text);
                toast(a, "success", "DM sent");
            }
            case "warn" -> {
                p.actions.warn(a, t, text.isEmpty() ? p.getConfig().getString("default-reasons.warn", "-") : text);
                toast(a, "success", "Player warned");
            }
            case "kick" -> {
                p.actions.kick(a, t, text.isEmpty() ? p.getConfig().getString("default-reasons.kick", "-") : text);
                toast(a, "success", "Player kicked");
            }
            case "giveadmin" -> {
                boolean added = p.actions.giveAdmin(a, t);
                toast(a, added ? "success" : "info", added ? "Admin permission given" : "Already an admin");
            }
            case "heal" -> { p.actions.heal(a, t); toast(a, "success", "Healed " + t.getName()); }
            case "goto" -> { p.actions.tp(a, t); toast(a, "success", "Teleported to " + t.getName()); }
            case "bring" -> { p.actions.bring(a, t); toast(a, "success", "Brought " + t.getName()); }
            case "spectate" -> { p.actions.spectate(a, t); toast(a, "info", "Spectating " + t.getName()); }
            case "freeze" -> {
                boolean on = p.modes.toggleFrozen(t);
                p.actions.log(a.getName() + (on ? " froze " : " unfroze ") + t.getName());
                toast(a, "success", on ? "Player frozen" : "Player unfrozen");
            }
            case "drunk" -> { p.actions.drunk(t); toast(a, "success", t.getName() + " is drunk"); }
            case "fire" -> {
                t.setFireTicks(200);
                p.actions.log(a.getName() + " set fire to " + t.getName());
                toast(a, "success", t.getName() + " is on fire");
            }
            case "wolves" -> {
                p.actions.wildAttack(t);
                p.actions.log(a.getName() + " sent wolves at " + t.getName());
                toast(a, "success", "Wolves released");
            }
            default -> { }
        }
    }

    // ---------- format string vanilla (VarInt panjang + UTF-8) ----------
    private static byte[] encode(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + 5);
        int v = b.length;
        while ((v & -128) != 0) { out.write(v & 127 | 128); v >>>= 7; }
        out.write(v);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    private static String decode(byte[] m) {
        int pos = 0, len = 0, shift = 0;
        while (true) {
            int b = m[pos++];
            len |= (b & 127) << shift;
            if ((b & 128) == 0) break;
            shift += 7;
        }
        return new String(m, pos, len, StandardCharsets.UTF_8);
    }
}
