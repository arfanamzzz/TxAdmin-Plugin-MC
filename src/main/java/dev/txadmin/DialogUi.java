package dev.txadmin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;

/** Menu txAdmin berbasis Dialog bawaan Minecraft/Paper (layar di tengah, kolom ketik, tombol). */
public final class DialogUi {
    private static final ClickCallback.Options OPTS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(15)).build();

    private static final String[] KEY_ACTIONS = {"noclip", "exit-noclip", "menu", "players", "god", "superjump", "heal", "back", "ids", "names"};
    private static final String[] TRIGGERS = {"DEFAULT", "NONE", "F", "SHIFT+F", "Q", "SHIFT+Q", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "DOUBLE_SHIFT", "DOUBLE_CTRL", "DOUBLE_SPACE", "DOUBLE_W", "DOUBLE_A", "DOUBLE_S", "DOUBLE_D", "SHIFT+SPACE", "SHIFT+W",
            "CTRL+SPACE", "LEFT_CLICK", "RIGHT_CLICK", "SHIFT_LEFT_CLICK", "SHIFT_RIGHT_CLICK"};

    private final TxAdmin p;
    private final Map<UUID, String> search = new HashMap<>();
    private final Map<UUID, String> filter = new HashMap<>();
    private final Map<UUID, String> healTarget = new HashMap<>();

    public DialogUi(TxAdmin p) { this.p = p; }

    // ================= helper =================
    private Component c(String s) { return p.mm(s); }

    private DialogBody msg(String s) { return DialogBody.plainMessage(c(s), 300); }

    private DialogInput text(String key, String label, String initial, int max) {
        return DialogInput.text(key, c(label)).width(300).initial(initial == null ? "" : initial).maxLength(max).build();
    }

    private DialogInput choice(String key, String label, String[][] opts, String selected) {
        List<SingleOptionDialogInput.OptionEntry> list = new ArrayList<>();
        for (String[] o : opts) list.add(SingleOptionDialogInput.OptionEntry.create(o[0], c(o[1]), o[0].equals(selected)));
        return DialogInput.singleOption(key, c(label), list).width(300).build();
    }

    private ActionButton btn(String label, String tip, int width, List<String> keys, BiConsumer<Player, Map<String, String>> act) {
        return ActionButton.create(c(label), tip == null ? null : c(tip), width, DialogAction.customClick((view, aud) -> {
            if (!(aud instanceof Player pl) || !p.isAdmin(pl)) return;
            Map<String, String> vals = new HashMap<>();
            for (String k : keys) {
                String s = view.getText(k);
                if (s != null) vals.put(k, s);
            }
            Bukkit.getScheduler().runTask(p, () -> act.accept(pl, vals));
        }, OPTS));
    }

    private ActionButton btn(String label, int width, BiConsumer<Player, Map<String, String>> act) {
        return btn(label, null, width, List.of(), act);
    }

    private Dialog build(String title, List<DialogBody> body, List<DialogInput> inputs, List<ActionButton> buttons, int columns) {
        return Dialog.create(f -> f.empty()
                .base(DialogBase.builder(c(title)).canCloseWithEscape(true).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons).columns(columns).build()));
    }

    private void show(Player a, Dialog d) { a.showDialog(d); }

    /** Kotak panjang di atas layar: biru = info, hijau = berhasil, merah = gagal. */
    public void toast(Player a, String text, String kind) {
        BossBar.Color col = switch (kind) { case "success" -> BossBar.Color.GREEN; case "error" -> BossBar.Color.RED; default -> BossBar.Color.BLUE; };
        String icon = switch (kind) { case "success" -> "<green><bold>✔</bold></green>"; case "error" -> "<red><bold>✖</bold></red>"; default -> "<aqua><bold>(!)</bold></aqua>"; };
        p.actions.banner(List.of(a), c(icon + " <white>" + p.esc(text)), col, 3);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000), h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m " + (s % 60) + "s";
    }

    private boolean gone(Player a, Player t) {
        if (t.isOnline()) return false;
        toast(a, "Player left the server", "error");
        openPlayers(a);
        return true;
    }

    // ================= MAIN =================
    public void openMain(Player a) {
        UUID id = a.getUniqueId();
        List<DialogBody> body = new ArrayList<>();
        body.add(msg("<gray>v1.0.0   <white><bold>MAIN</bold> <dark_gray>|</dark_gray> <gray>PLAYERS <dark_gray>|</dark_gray> TXADMIN"));
        List<DialogInput> in = new ArrayList<>();
        in.add(choice("mode", "Player Mode", new String[][]{{"normal", "Normal"}, {"noclip", "Noclip"}, {"god", "God Mode"}, {"jump", "Super Jump"}},
                p.modes.mode(a)));
        in.add(choice("heal", "Heal", new String[][]{{"myself", "Myself"}, {"player", "Player"}, {"everyone", "Everyone"}},
                healTarget.getOrDefault(id, "myself")));
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<white>Apply Player Mode", "Pilih mode di atas lalu klik", 300, List.of("mode", "heal"), (pl, v) -> {
            healTarget.put(pl.getUniqueId(), v.getOrDefault("heal", "myself"));
            String m = v.getOrDefault("mode", "normal");
            p.actions.setMode(pl, m);
            toast(pl, switch (m) { case "noclip" -> "Noclip Enabled"; case "god" -> "God Mode Enabled"; case "jump" -> "Super Jump Enabled"; default -> "Normal Mode"; }, "info");
            openMain(pl);
        }));
        b.add(btn("<white>Teleport", 300, (pl, v) -> openTeleport(pl)));
        b.add(btn("<white>Heal", "Sesuai pilihan Heal di atas", 300, List.of("heal"), (pl, v) -> {
            String h = v.getOrDefault("heal", "myself");
            healTarget.put(pl.getUniqueId(), h);
            switch (h) {
                case "everyone" -> { p.actions.healAll(pl); toast(pl, "Healed everyone", "success"); openMain(pl); }
                case "player" -> openHealPlayer(pl);
                default -> { p.actions.heal(pl, pl); toast(pl, "Healed yourself", "success"); openMain(pl); }
            }
        }));
        b.add(btn("<white>Send Announcement", 300, (pl, v) -> openAnnouncement(pl)));
        b.add(btn("<white>Toggle Player IDs", 300, (pl, v) -> {
            boolean on = p.ids.toggle(pl);
            toast(pl, on ? "Player IDs Enabled" : "Player IDs Disabled", "info");
            openMain(pl);
        }));
        b.add(btn("<yellow>PLAYERS", 300, (pl, v) -> openPlayers(pl)));
        b.add(btn("<yellow>TXADMIN", 300, (pl, v) -> openConsole(pl)));
        b.add(btn("<gray>Keybind Settings", 300, (pl, v) -> openKeys(pl)));
        show(a, build("<white><bold>txAdmin</bold> <gray>v1.0.0", body, in, b, 1));
    }

    private void openTeleport(Player a) {
        List<DialogBody> body = List.of(msg("<white><bold>Teleport</bold>\n<gray>Type the player ID (or name)"));
        List<DialogInput> in = List.of(text("id", "Player ID", "", 32));
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<green>Teleport", null, 140, List.of("id"), (pl, v) -> {
            Player t = v.getOrDefault("id", "").isBlank() ? null : p.ids.find(v.get("id").trim());
            if (t == null) { toast(pl, "Player not found", "error"); openTeleport(pl); return; }
            p.actions.tp(pl, t);
            toast(pl, "Teleported to [" + p.ids.idOf(t) + "] " + t.getName(), "success");
        }));
        b.add(btn("<gray>Back", 140, (pl, v) -> openMain(pl)));
        show(a, build("<white>Teleport", new ArrayList<>(body), new ArrayList<>(in), b, 2));
    }

    private void openHealPlayer(Player a) {
        List<DialogBody> body = List.of(msg("<white><bold>Heal</bold>\n<gray>Type the player ID (or name)"));
        List<DialogInput> in = List.of(text("id", "Player ID", "", 32));
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<green>Heal", null, 140, List.of("id"), (pl, v) -> {
            Player t = v.getOrDefault("id", "").isBlank() ? null : p.ids.find(v.get("id").trim());
            if (t == null) { toast(pl, "Player not found", "error"); openHealPlayer(pl); return; }
            p.actions.heal(pl, t);
            toast(pl, "Healed [" + p.ids.idOf(t) + "] " + t.getName(), "success");
        }));
        b.add(btn("<gray>Back", 140, (pl, v) -> openMain(pl)));
        show(a, build("<white>Heal", new ArrayList<>(body), new ArrayList<>(in), b, 2));
    }

    private void openAnnouncement(Player a) {
        List<DialogBody> body = List.of(msg("<white><bold>Send Announcement</bold>\n<gray>Sent to every player on the server"));
        List<DialogInput> in = List.of(text("message", "Message", "", 256));
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<green>Send", null, 140, List.of("message"), (pl, v) -> {
            String m = v.getOrDefault("message", "").trim();
            if (m.isEmpty()) { toast(pl, "Write a message first", "error"); openAnnouncement(pl); return; }
            p.actions.announce(pl, m);
            toast(pl, "Announcement sent", "success");
        }));
        b.add(btn("<gray>Back", 140, (pl, v) -> openMain(pl)));
        show(a, build("<white>Announcement", new ArrayList<>(body), new ArrayList<>(in), b, 2));
    }

    // ================= PLAYERS =================
    public void openPlayers(Player a) {
        UUID id = a.getUniqueId();
        String q = search.getOrDefault(id, ""), f = filter.getOrDefault(id, "all");
        List<Player> list = Bukkit.getOnlinePlayers().stream()
                .filter(x -> f.equals("all") || p.isAdmin(x))
                .filter(x -> q.isBlank() || x.getName().toLowerCase().contains(q.toLowerCase()) || String.valueOf(p.ids.idOf(x)).equals(q.trim()))
                .sorted(Comparator.comparingInt(p.ids::idOf)).limit(60).map(x -> (Player) x).toList();

        List<DialogBody> body = new ArrayList<>();
        body.add(msg("<gray>v1.0.0   PLAYERS\n<white>" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + " Players <gray>- OneSync (on)"));
        List<DialogInput> in = new ArrayList<>();
        in.add(text("search", "Search (Minecraft name / ID)", q, 32));
        in.add(choice("filter", "Filter by", new String[][]{{"all", "No Filter"}, {"admin", "Is Admin"}}, f));
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<yellow>Search / Apply Filter", null, 300, List.of("search", "filter"), (pl, v) -> {
            search.put(pl.getUniqueId(), v.getOrDefault("search", ""));
            filter.put(pl.getUniqueId(), v.getOrDefault("filter", "all"));
            openPlayers(pl);
        }));
        for (Player x : list) {
            String dist = x.getWorld().equals(a.getWorld()) ? (int) a.getLocation().distance(x.getLocation()) + "m" : "-";
            String label = "<white>" + p.ids.idOf(x) + " <dark_gray>|</dark_gray> " + (p.isAdmin(x) ? "<aqua>⛨ </aqua>" : "")
                    + p.esc(x.getName()) + "  <gray>" + dist + "  <red>❤ <white>" + (int) Math.ceil(x.getHealth()) + "/20";
            b.add(btn(label, null, 300, List.of(), (pl, v) -> openPlayer(pl, x, "actions")));
        }
        b.add(btn("<gray>Back", 300, (pl, v) -> openMain(pl)));
        show(a, build("<white>Online Players", body, in, b, 1));
    }

    // ================= PLAYER (Actions / Info / Ban) =================
    private List<ActionButton> tabs(Player t, String tab) {
        List<ActionButton> l = new ArrayList<>();
        for (String[] x : new String[][]{{"actions", "Actions"}, {"info", "Info"}, {"ban", "Ban"}}) {
            l.add(btn((x[0].equals(tab) ? "<yellow><bold>" : "<gray>") + x[1], 100, (pl, v) -> { if (!gone(pl, t)) openPlayer(pl, t, x[0]); }));
        }
        return l;
    }

    public void openPlayer(Player a, Player t, String tab) {
        if (gone(a, t)) return;
        String title = "<white>[" + p.ids.idOf(t) + "] " + p.esc(t.getName());
        switch (tab) {
            case "info" -> openInfo(a, t, title);
            case "ban" -> openBan(a, t, title);
            default -> openActions(a, t, title);
        }
    }

    private void openActions(Player a, Player t, String title) {
        List<DialogBody> body = List.of(msg("<yellow>Moderation: DM, WARN, KICK, GIVE ADMIN\n<green>Interaction: HEAL, GO TO, BRING, SPECTATE, FREEZE\n<red>Troll: DRUNK, SET FIRE, WILD ATTACK"));
        List<DialogInput> in = List.of(text("text", "Message / Reason (for DM, WARN, KICK)", "", 200));
        List<ActionButton> b = new ArrayList<>(tabs(t, "actions"));
        List<String> k = List.of("text");
        b.add(btn("<yellow>DM", null, 100, k, (pl, v) -> {
            if (gone(pl, t)) return;
            String m = v.getOrDefault("text", "").trim();
            if (m.isEmpty()) { toast(pl, "Write a message first", "error"); openPlayer(pl, t, "actions"); return; }
            p.actions.dm(pl, t, m); toast(pl, "DM sent", "success"); openPlayer(pl, t, "actions");
        }));
        b.add(btn("<yellow>WARN", null, 100, k, (pl, v) -> {
            if (gone(pl, t)) return;
            String m = v.getOrDefault("text", "").trim();
            p.actions.warn(pl, t, m.isEmpty() ? p.getConfig().getString("default-reasons.warn", "-") : m);
            toast(pl, "Player warned", "success"); openPlayer(pl, t, "actions");
        }));
        b.add(btn("<yellow>KICK", null, 100, k, (pl, v) -> {
            if (gone(pl, t)) return;
            String m = v.getOrDefault("text", "").trim();
            p.actions.kick(pl, t, m.isEmpty() ? p.getConfig().getString("default-reasons.kick", "-") : m);
            toast(pl, "Player kicked", "success"); openPlayers(pl);
        }));
        b.add(btn("<yellow>GIVE ADMIN", 100, (pl, v) -> {
            if (gone(pl, t)) return;
            boolean added = p.actions.giveAdmin(pl, t);
            toast(pl, added ? "Admin permission given" : "Already an admin", added ? "success" : "info"); openPlayer(pl, t, "actions");
        }));
        b.add(btn("<green>HEAL", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.heal(pl, t); toast(pl, "Healed " + t.getName(), "success"); openPlayer(pl, t, "actions"); }));
        b.add(btn("<green>GO TO", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.tp(pl, t); toast(pl, "Teleported to " + t.getName(), "success"); }));
        b.add(btn("<green>BRING", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.bring(pl, t); toast(pl, "Brought " + t.getName(), "success"); openPlayer(pl, t, "actions"); }));
        b.add(btn("<green>SPECTATE", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.spectate(pl, t); toast(pl, "Spectating " + t.getName(), "info"); }));
        b.add(btn("<green>TOGGLE FREEZE", 100, (pl, v) -> {
            if (gone(pl, t)) return;
            boolean on = p.modes.toggleFrozen(t);
            p.actions.log(pl.getName() + (on ? " froze " : " unfroze ") + t.getName());
            toast(pl, on ? "Player frozen" : "Player unfrozen", "success"); openPlayer(pl, t, "actions");
        }));
        b.add(btn("<red>MAKE DRUNK", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.drunk(t); toast(pl, t.getName() + " is drunk", "success"); openPlayer(pl, t, "actions"); }));
        b.add(btn("<red>SET FIRE", 100, (pl, v) -> { if (gone(pl, t)) return; t.setFireTicks(200); p.actions.log(pl.getName() + " set fire to " + t.getName()); toast(pl, t.getName() + " is on fire", "success"); openPlayer(pl, t, "actions"); }));
        b.add(btn("<red>WILD ATTACK", 100, (pl, v) -> { if (gone(pl, t)) return; p.actions.wildAttack(t); p.actions.log(pl.getName() + " sent wolves at " + t.getName()); toast(pl, "Wolves released", "success"); openPlayer(pl, t, "actions"); }));
        b.add(btn("<gray>Back", 100, (pl, v) -> openPlayers(pl)));
        show(a, build(title, new ArrayList<>(body), new ArrayList<>(in), b, 3));
    }

    private void openInfo(Player a, Player t, String title) {
        long session = System.currentTimeMillis() - p.ids.joinedAt(t);
        long played = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L * 1000L;
        String joined = t.getFirstPlayed() > 0 ? new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(t.getFirstPlayed())) : "-";
        List<DialogBody> body = List.of(msg("<white><bold>Player info</bold>\n<gray>Session Time: <white>" + fmt(session)
                + "\n<gray>Play time: <white>" + fmt(played) + "\n<gray>Joined: <white>" + joined));
        List<DialogInput> in = List.of(text("note", "Note about this player", p.store.note(t.getUniqueId()), 256));
        List<ActionButton> b = new ArrayList<>(tabs(t, "info"));
        b.add(btn("<green>SAVE NOTE", null, 150, List.of("note"), (pl, v) -> {
            p.store.setNote(t.getUniqueId(), v.getOrDefault("note", "").trim());
            toast(pl, "Note saved", "success"); openPlayer(pl, t, "info");
        }));
        b.add(btn("<gray>Back", 100, (pl, v) -> openPlayers(pl)));
        show(a, build(title, new ArrayList<>(body), new ArrayList<>(in), b, 3));
    }

    private void openBan(Player a, Player t, String title) {
        List<String> durs = p.getConfig().getStringList("ban-durations");
        if (durs.isEmpty()) durs = List.of("1d", "perm");
        String[][] opts = new String[durs.size()][];
        for (int i = 0; i < durs.size(); i++) opts[i] = new String[]{durs.get(i), Actions.durationLabel(durs.get(i))};
        String init = durs.contains("1d") ? "1d" : durs.get(0);
        List<DialogBody> body = List.of(msg("<white><bold>Ban Player</bold>"));
        List<DialogInput> in = new ArrayList<>();
        in.add(text("reason", "Reason", p.getConfig().getString("default-reasons.ban", ""), 200));
        in.add(choice("duration", "Duration", opts, init));
        in.add(text("custom", "Custom duration (optional, e.g. 45m / 3d / perm)", "", 8));
        List<ActionButton> b = new ArrayList<>(tabs(t, "ban"));
        b.add(btn("<red><bold>BAN PLAYER", null, 150, List.of("reason", "duration", "custom"), (pl, v) -> {
            if (gone(pl, t)) return;
            String custom = v.getOrDefault("custom", "").trim();
            long ms = Actions.parseDuration(custom.isEmpty() ? v.getOrDefault("duration", "1d") : custom);
            if (ms < 0) { toast(pl, "Invalid duration", "error"); openPlayer(pl, t, "ban"); return; }
            String r = v.getOrDefault("reason", "").trim();
            p.actions.ban(pl, t, t.getName(), ms, r.isEmpty() ? p.getConfig().getString("default-reasons.ban", "-") : r);
            toast(pl, "Player banned", "success"); openPlayers(pl);
        }));
        b.add(btn("<gray>Back", 100, (pl, v) -> openPlayers(pl)));
        show(a, build(title, new ArrayList<>(body), in, b, 3));
    }

    // ================= TXADMIN (console) =================
    public void openConsole(Player a) {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        StringBuilder sb = new StringBuilder("<gray>v1.0.0   TXADMIN\n<white>TPS: <green>" + String.format("%.1f", tps)
                + " <gray>| <white>Players: <green>" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()
                + (p.restart.running() ? "\n<red>Restart countdown running" : "") + "\n\n<white><bold>Console</bold>");
        if (p.actions.console.isEmpty()) sb.append("\n<dark_gray>(no activity yet)");
        int n = 0;
        for (String line : p.actions.console) { if (n++ >= 14) break; sb.append("\n<gray>").append(p.esc(line)); }
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<yellow>Restart (5 min)", 140, (pl, v) -> { p.actions.restart(pl, 300); toast(pl, "Restart countdown started", "success"); openConsole(pl); }));
        b.add(btn("<yellow>Cancel Restart", 140, (pl, v) -> { p.actions.cancelRestart(pl); toast(pl, "Restart cancelled", "success"); openConsole(pl); }));
        b.add(btn("<yellow>Toggle Whitelist", 140, (pl, v) -> { p.actions.toggleWhitelist(pl); toast(pl, Bukkit.hasWhitelist() ? "Whitelist Enabled" : "Whitelist Disabled", "info"); openConsole(pl); }));
        b.add(btn("<yellow>Reload Config", 140, (pl, v) -> { p.actions.reload(pl); toast(pl, "Config reloaded", "success"); openConsole(pl); }));
        b.add(btn("<gray>Refresh", 140, (pl, v) -> openConsole(pl)));
        b.add(btn("<gray>Back", 140, (pl, v) -> openMain(pl)));
        show(a, build("<white>TXADMIN", new ArrayList<>(List.of(msg(sb.toString()))), new ArrayList<>(), b, 2));
    }

    // ================= KEYBIND per admin =================
    public void openKeys(Player a) {
        String[][] opts = new String[TRIGGERS.length][];
        for (int i = 0; i < TRIGGERS.length; i++) opts[i] = new String[]{TRIGGERS[i], TRIGGERS[i].equals("DEFAULT") ? "Server default" : TRIGGERS[i]};
        List<DialogBody> body = List.of(msg("<white><bold>Keybind Settings</bold>\n<gray>Pengaturan di bawah hanya untuk akunmu.\n<yellow>Tombol bebas (Home, 0, ', F1, huruf, dll):</yellow> <gray>buka Options > Controls > Key Binds, cari <white>Quick Actions</white> (bawaan G), lalu pilih tombolnya. Tombol itu membuka panel TxAdmin."));
        List<DialogInput> in = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String act : KEY_ACTIONS) {
            String cur = p.store.key(a.getUniqueId(), act);
            in.add(choice(act, act, opts, cur == null ? "DEFAULT" : cur));
            keys.add(act);
        }
        List<ActionButton> b = new ArrayList<>();
        b.add(btn("<green>Save", null, 140, keys, (pl, v) -> {
            Map<String, String> m = new HashMap<>();
            for (String act : KEY_ACTIONS) { String s = v.getOrDefault(act, "DEFAULT"); m.put(act, s.equals("DEFAULT") ? null : s); }
            p.store.setKeys(pl.getUniqueId(), m);
            toast(pl, "Keybinds saved", "success"); openMain(pl);
        }));
        b.add(btn("<yellow>Reset to default", 140, (pl, v) -> {
            Map<String, String> m = new HashMap<>();
            for (String act : KEY_ACTIONS) m.put(act, null);
            p.store.setKeys(pl.getUniqueId(), m);
            toast(pl, "Keybinds reset", "info"); openKeys(pl);
        }));
        b.add(btn("<gray>Back", 140, (pl, v) -> openMain(pl)));
        show(a, build("<white>Keybinds", body, in, b, 2));
    }
}
