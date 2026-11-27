package dev.txadmin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Menu /tx ala txAdmin: Mode Pemain, Efek, Daftar Pemain, Server. */
public final class Gui implements Listener {
    private final TxAdmin p;

    // id efek vanilla, ikon, label
    private static final String[][] EFFECTS = {
            {"speed", "SUGAR", "Speed"},
            {"haste", "GOLDEN_PICKAXE", "Haste"},
            {"strength", "IRON_SWORD", "Strength"},
            {"night_vision", "GOLDEN_CARROT", "Night Vision"},
            {"water_breathing", "PUFFERFISH", "Water Breathing"},
            {"fire_resistance", "MAGMA_CREAM", "Fire Resistance"},
            {"invisibility", "FERMENTED_SPIDER_EYE", "Invisibility"},
            {"slow_falling", "PHANTOM_MEMBRANE", "Slow Falling"},
            {"regeneration", "GHAST_TEAR", "Regeneration"},
            {"glowing", "GLOWSTONE_DUST", "Glowing"},
            {"saturation", "BREAD", "Saturation"},
            {"resistance", "IRON_CHESTPLATE", "Resistance"},
    };

    public Gui(TxAdmin p) { this.p = p; }

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Consumer<InventoryClickEvent>> acts = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private Menu menu(int rows, String title) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, rows * 9, p.mm(title));
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", false);
        for (int i = 0; i < m.inv.getSize(); i++) m.inv.setItem(i, pane);
        return m;
    }

    private ItemStack item(Material mat, String name, boolean glow, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta mt = i.getItemMeta();
        mt.displayName(p.mm("<!italic>" + name));
        if (lore.length > 0) mt.lore(Arrays.stream(lore).map(x -> p.mm("<!italic><gray>" + x)).toList());
        if (glow) mt.setEnchantmentGlintOverride(true);
        i.setItemMeta(mt);
        return i;
    }

    private void set(Menu m, int slot, ItemStack it, Consumer<InventoryClickEvent> a) {
        m.inv.setItem(slot, it);
        if (a != null) m.acts.put(slot, a);
    }

    private void later(Runnable r) { Bukkit.getScheduler().runTask(p, r); }

    private void back(Menu m, Player a, int slot, Runnable to) {
        set(m, slot, item(Material.ARROW, "<yellow>Kembali", false), e -> later(to));
    }

    private boolean online(Player a, Player t) {
        if (t.isOnline()) return true;
        a.closeInventory();
        a.sendMessage(p.msg("player-not-found", "player", t.getName()));
        return false;
    }

    private String reason(String k) { return p.getConfig().getString("default-reasons." + k, "-"); }

    // ---------- Menu utama ----------
    public void openMain(Player a) {
        Menu m = menu(3, "<dark_gray><bold>TxAdmin");
        set(m, 10, item(Material.DIAMOND_SWORD, "<aqua><bold>Mode Pemain", false, "Noclip, godmode, super jump", "heal, feed"), e -> later(() -> openMode(a)));
        set(m, 12, item(Material.PLAYER_HEAD, "<green><bold>Daftar Pemain", false, "Aksi ke pemain online", "tp, bring, freeze, warn, kick, ban"), e -> later(() -> openPlayers(a)));
        set(m, 14, item(Material.POTION, "<light_purple><bold>Efek", false, "Toggle efek untuk dirimu"), e -> later(() -> openEffects(a)));
        set(m, 16, item(Material.COMMAND_BLOCK, "<gold><bold>Server", false, "Restart, whitelist, ID pemain"), e -> later(() -> openServer(a)));
        set(m, 22, item(Material.BARRIER, "<red>Tutup", false), e -> a.closeInventory());
        a.openInventory(m.inv);
    }

    // ---------- Mode pemain ----------
    public void openMode(Player a) {
        var id = a.getUniqueId();
        Menu m = menu(3, "<dark_gray><bold>Mode Pemain");
        set(m, 10, item(Material.FEATHER, "<aqua>Noclip", p.modes.noclip.containsKey(id), "Tembus blok (mode spectator)", "<yellow>Klik untuk toggle"), e -> later(() -> {
            p.actions.noclip(a);
            if (p.modes.noclip.containsKey(id)) a.closeInventory(); else openMode(a);
        }));
        set(m, 11, item(Material.TOTEM_OF_UNDYING, "<gold>Godmode", p.modes.god.contains(id), "Kebal damage dan lapar", "<yellow>Klik untuk toggle"), e -> later(() -> { p.actions.god(a, a); openMode(a); }));
        set(m, 12, item(Material.RABBIT_FOOT, "<green>Super Jump", p.modes.jump.contains(id), "Lompat tinggi", "<yellow>Klik untuk toggle"), e -> later(() -> { p.actions.superjump(a, a); openMode(a); }));
        set(m, 14, item(Material.GOLDEN_APPLE, "<red>Heal", false, "Isi darah dan lapar"), e -> later(() -> p.actions.heal(a, a)));
        set(m, 15, item(Material.COOKED_BEEF, "<yellow>Feed", false, "Isi lapar"), e -> later(() -> p.actions.feed(a, a)));
        set(m, 16, item(Material.MILK_BUCKET, "<white>Normal", false, "Matikan semua mode dan efek"), e -> later(() -> { p.modes.clearAll(a); openMode(a); }));
        back(m, a, 22, () -> openMain(a));
        a.openInventory(m.inv);
    }

    // ---------- Efek ----------
    public void openEffects(Player a) {
        Menu m = menu(4, "<dark_gray><bold>Efek");
        for (int i = 0; i < EFFECTS.length; i++) {
            String[] fx = EFFECTS[i];
            int slot = 10 + i + (i >= 7 ? 2 : 0);
            boolean on = p.modes.hasEffect(a, fx[0]);
            set(m, slot, item(Material.valueOf(fx[1]), (on ? "<green>" : "<white>") + fx[2], on, on ? "<green>Aktif" : "<red>Nonaktif", "<yellow>Klik untuk toggle"),
                    e -> later(() -> { p.modes.toggleEffect(a, fx[0]); openEffects(a); }));
        }
        set(m, 30, item(Material.MILK_BUCKET, "<white>Hapus semua efek", false), e -> later(() -> { p.actions.clearEffects(a, a); openEffects(a); }));
        back(m, a, 31, () -> openMain(a));
        a.openInventory(m.inv);
    }

    // ---------- Daftar pemain ----------
    public void openPlayers(Player a) {
        Menu m = menu(6, "<dark_gray><bold>Pemain Online");
        int i = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (i >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(t);
            sm.displayName(p.mm("<!italic><white>[" + p.ids.idOf(t) + "] " + p.esc(t.getName())));
            sm.lore(List.of(p.mm("<!italic><yellow>Klik untuk membuka aksi")));
            head.setItemMeta(sm);
            set(m, i++, head, e -> later(() -> openPlayer(a, t)));
        }
        back(m, a, 49, () -> openMain(a));
        a.openInventory(m.inv);
    }

    // ---------- Aksi ke satu pemain ----------
    public void openPlayer(Player a, Player t) {
        if (!online(a, t)) return;
        Menu m = menu(4, "<dark_gray><bold>[" + p.ids.idOf(t) + "] " + p.esc(t.getName()));
        set(m, 10, item(Material.ENDER_PEARL, "<aqua>Teleport ke dia", false), e -> later(() -> { if (online(a, t)) p.actions.tp(a, t); }));
        set(m, 11, item(Material.ENDER_EYE, "<aqua>Tarik ke sini", false), e -> later(() -> { if (online(a, t)) p.actions.bring(a, t); }));
        set(m, 12, item(Material.GOLDEN_APPLE, "<red>Heal", false), e -> later(() -> { if (online(a, t)) p.actions.heal(a, t); }));
        set(m, 13, item(Material.ICE, "<aqua>Freeze", p.modes.frozen.contains(t.getUniqueId()), "<yellow>Klik untuk toggle"), e -> later(() -> { if (online(a, t)) { p.actions.freeze(a, t); openPlayer(a, t); } }));
        set(m, 14, item(Material.SPYGLASS, "<green>Spectate", false), e -> later(() -> { if (online(a, t)) { a.closeInventory(); p.actions.spectate(a, t); } }));
        set(m, 15, item(Material.CHEST, "<gold>Lihat inventory", false), e -> later(() -> { if (online(a, t)) a.openInventory(t.getInventory()); }));
        set(m, 16, item(Material.MILK_BUCKET, "<white>Hapus efek", false), e -> later(() -> { if (online(a, t)) p.actions.clearEffects(a, t); }));
        set(m, 19, item(Material.PAPER, "<yellow>Warn", false, "Alasan: " + reason("warn")), e -> later(() -> { if (online(a, t)) p.actions.warn(a, t, reason("warn")); }));
        set(m, 20, item(Material.IRON_BOOTS, "<gold>Kick", false, "Alasan: " + reason("kick")), e -> later(() -> { if (online(a, t)) { a.closeInventory(); p.actions.kick(a, t, reason("kick")); } }));
        set(m, 21, item(Material.TNT, "<dark_red><bold>Ban...", false, "Pilih durasi dan alasan"), e -> later(() -> { if (online(a, t)) { drafts.put(a.getUniqueId(), newDraft(t)); openBan(a); } }));
        back(m, a, 31, () -> openPlayers(a));
        a.openInventory(m.inv);
    }

    // ---------- Server ----------
    public void openServer(Player a) {
        Menu m = menu(3, "<dark_gray><bold>Server");
        set(m, 10, item(Material.BELL, "<gold>Pengumuman", false, "Pakai: <white>/tx announce <pesan>"), null);
        set(m, 11, item(Material.CLOCK, "<yellow>Restart 5 menit", p.restart.running(), "Mulai hitung mundur restart"), e -> later(() -> { p.actions.restart(a, 300); openServer(a); }));
        set(m, 12, item(Material.REDSTONE_BLOCK, "<red>Batalkan restart", false), e -> later(() -> { p.actions.cancelRestart(a); openServer(a); }));
        set(m, 13, item(Material.PAPER, "<white>Whitelist", Bukkit.hasWhitelist(), "<yellow>Klik untuk toggle"), e -> later(() -> { p.actions.toggleWhitelist(a); openServer(a); }));
        set(m, 14, item(Material.NAME_TAG, "<aqua>Player IDs", p.ids.isOn(a), "Hanya kamu yang melihat", "<yellow>Klik untuk toggle"), e -> later(() -> { p.actions.toggleIds(a); openServer(a); }));
        set(m, 15, item(Material.COMPARATOR, "<green>Reload config", false), e -> later(() -> p.actions.reload(a)));
        set(m, 17, item(Material.PLAYER_HEAD, "<aqua>Sembunyikan nama asli", p.ids.isHidingNames(a), "Nametag pemain lain disembunyikan", "Hanya untukmu. <yellow>Klik untuk toggle"), e -> later(() -> { p.actions.toggleNames(a); openServer(a); }));
        set(m, 16, item(Material.TRIPWIRE_HOOK, "<white>Keybind", p.keys.enabled(a), "Noclip: " + p.keys.bind("noclip") + " | Menu: " + p.keys.bind("menu"), "<yellow>Klik untuk toggle"), e -> later(() -> { p.actions.toggleKeys(a); openServer(a); }));
        back(m, a, 22, () -> openMain(a));
        a.openInventory(m.inv);
    }


    // ---------- Ban (durasi dan alasan dari config) ----------
    static final class Draft {
        UUID target;
        String name;
        String duration;
        String reason;
    }

    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    private Draft newDraft(Player t) {
        Draft d = new Draft();
        d.target = t.getUniqueId();
        d.name = t.getName();
        d.reason = reason("ban");
        return d;
    }

    private void openBan(Player a) {
        Draft d = drafts.get(a.getUniqueId());
        if (d == null) { openMain(a); return; }
        Menu m = menu(5, "<dark_gray><bold>Ban: " + p.esc(d.name));

        List<String> durs = p.getConfig().getStringList("ban-durations");
        int shown = 0;
        for (String du : durs) {
            if (shown >= 14) break;
            long ms = Actions.parseDuration(du);
            if (ms < 0) continue;
            int slot = shown < 7 ? 10 + shown : 19 + (shown - 7);
            shown++;
            boolean sel = du.equalsIgnoreCase(d.duration);
            set(m, slot, item(ms == 0 ? Material.TNT : Material.CLOCK, (sel ? "<green>" : "<white>") + Actions.durationLabel(du), sel,
                    sel ? "<green>Dipilih" : "<yellow>Klik untuk memilih"),
                    e -> later(() -> { d.duration = du; openBan(a); }));
        }

        List<String> reasons = p.getConfig().getStringList("ban-reasons");
        for (int i = 0; i < Math.min(7, reasons.size()); i++) {
            String r = reasons.get(i);
            boolean sel = r.equals(d.reason);
            set(m, 28 + i, item(Material.PAPER, (sel ? "<green>" : "<white>") + p.esc(r), sel, sel ? "<green>Dipilih" : "<yellow>Klik untuk memilih"),
                    e -> later(() -> { d.reason = r; openBan(a); }));
        }
        set(m, 35, item(Material.WRITABLE_BOOK, "<aqua>Alasan sendiri", false, "Saat ini: <white>" + p.esc(d.reason), "<yellow>Klik lalu ketik alasan di chat"),
                e -> { awaiting.add(a.getUniqueId()); later(() -> { a.closeInventory(); a.sendMessage(p.msg("ban-type-reason")); }); });

        back(m, a, 36, () -> {
            drafts.remove(a.getUniqueId());
            Player t = Bukkit.getPlayer(d.target);
            if (t != null) openPlayer(a, t); else openPlayers(a);
        });
        set(m, 40, item(Material.TNT, "<red><bold>Ban sekarang", false,
                        "Pemain: <white>" + p.esc(d.name),
                        "Durasi: " + (d.duration == null ? "<red>belum dipilih" : "<white>" + Actions.durationLabel(d.duration)),
                        "Alasan: <white>" + p.esc(d.reason)),
                e -> later(() -> {
                    if (d.duration == null) { a.sendMessage(p.msg("ban-pick-duration")); return; }
                    long ms = Actions.parseDuration(d.duration);
                    if (ms < 0) return;
                    OfflinePlayer op = Bukkit.getOfflinePlayer(d.target);
                    drafts.remove(a.getUniqueId());
                    a.closeInventory();
                    p.actions.ban(a, op, d.name, ms, d.reason);
                }));
        a.openInventory(m.inv);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player a = e.getPlayer();
        if (!awaiting.remove(a.getUniqueId())) return;
        e.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        final String text = raw.length() > 100 ? raw.substring(0, 100) : raw;
        later(() -> {
            Draft d = drafts.get(a.getUniqueId());
            if (d != null && !text.isEmpty() && !text.equalsIgnoreCase("batal")) d.reason = text;
            if (d != null) openBan(a);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        if (!(e.getWhoClicked() instanceof Player pl) || !p.isAdmin(pl)) return;
        var act = m.acts.get(e.getSlot());
        if (act != null) act.accept(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
