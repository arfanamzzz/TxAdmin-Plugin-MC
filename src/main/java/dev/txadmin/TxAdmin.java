package dev.txadmin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

public final class TxAdmin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public Store store;
    public Modes modes;
    public Ids ids;
    public Gui gui;
    public DialogUi ui;
    public ModBridge bridge;
    public Actions actions;
    public Restart restart;
    public Keys keys;
    public Warnings warnings;
    public ServerLog slog;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        slog = new ServerLog();
        store = new Store(this);
        modes = new Modes(this);
        ids = new Ids(this);
        actions = new Actions(this);
        gui = new Gui(this);
        ui = new DialogUi(this);
        restart = new Restart(this);
        keys = new Keys(this);
        warnings = new Warnings(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(store, this);
        pm.registerEvents(slog, this);
        pm.registerEvents(modes, this);
        pm.registerEvents(ids, this);
        pm.registerEvents(gui, this);
        pm.registerEvents(keys, this);
        pm.registerEvents(warnings, this);
        pm.registerEvents(new QuickActions(this), this);
        bridge = new ModBridge(this);
        bridge.register();
        pm.registerEvents(bridge, this);

        TxCommand cmd = new TxCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                event.registrar().register("tx", "Menu dan perintah TxAdmin", List.of("txadmin"), new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack stack, String[] args) {
                        cmd.onCommand(stack.getSender(), null, "tx", args);
                    }

                    @Override
                    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                        return cmd.onTabComplete(stack.getSender(), null, "tx", args.length == 0 ? new String[]{""} : args);
                    }
                });
                event.registrar().register("dv", "Delete vehicles (/dv [radius])", new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack stack, String[] args) {
                        var s = stack.getSender();
                        if (!(s instanceof Player a) || !isAdmin(a)) { s.sendMessage(msg("no-permission")); return; }
                        int r = 0;
                        try { if (args.length > 0) r = Integer.parseInt(args[0]); } catch (NumberFormatException ex) { s.sendMessage(msg("usage", "usage", "/dv [radius]")); return; }
                        s.sendMessage(msg("vehicles-deleted", "count", String.valueOf(actions.deleteVehicles(a, r))));
                    }
                });
        });
        restart.start();
        slog.add("system", "", "TxAdmin enabled");
        getLogger().info("TxAdmin aktif.");
    }

    @Override
    public void onDisable() {
        if (ids != null) ids.shutdown();
        if (modes != null) modes.cleanup();
        if (store != null) store.save();
    }

    /** Menu utama: dialog (default) atau inventory lama bila ui.type: inventory. */
    public void openMain(Player a) {
        if (bridge != null && bridge.hasMod(a)) { bridge.send(a, "open|main"); return; }
        if ("inventory".equalsIgnoreCase(getConfig().getString("ui.type", "dialog"))) gui.openMain(a); else ui.openMain(a);
    }

    public void openPlayers(Player a) {
        if ("inventory".equalsIgnoreCase(getConfig().getString("ui.type", "dialog"))) gui.openPlayers(a); else ui.openPlayers(a);
    }

    public boolean isAdmin(CommandSender s) {
        if (!(s instanceof Player pl)) return s instanceof ConsoleCommandSender;
        return getConfig().getStringList("admins").stream().anyMatch(n -> n.equalsIgnoreCase(pl.getName()));
    }

    /** Ambil pesan mentah dari config, ganti {kunci} dengan nilai (nilai di-escape). */
    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", MM.escapeTags(kv[i + 1]));
        return s;
    }

    public Component msg(String key, String... kv) { return MM.deserialize(raw("prefix") + raw(key, kv)); }
    public Component plain(String key, String... kv) { return MM.deserialize(raw(key, kv)); }
    public Component mm(String s) { return MM.deserialize(s); }
    public String esc(String s) { return MM.escapeTags(s); }
}
