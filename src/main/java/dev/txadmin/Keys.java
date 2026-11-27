package dev.txadmin;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

import java.util.*;

/**
 * Keybind sisi server. Plugin tidak bisa membaca sembarang tombol keyboard (F1-F12, huruf X/Z/V, dll),
 * tapi bisa membaca: F, Q, angka 1-9, W/A/S/D, Spasi, Shift, Ctrl, dan klik mouse, termasuk kombinasi
 * dan tekan 2x cepat. Lihat daftar trigger di config.yml.
 */
public final class Keys implements Listener {
    private static final List<String> ACTIONS = List.of("exit-noclip", "noclip", "menu", "players", "god",
            "superjump", "heal", "back", "ids", "names");
    private static final String[] KEYS = {"W", "A", "S", "D", "SPACE", "SHIFT", "CTRL"};

    private final TxAdmin p;
    private final Set<UUID> off = new HashSet<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Long> lastSprint = new HashMap<>();
    private final Map<String, Long> lastKey = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Input> lastInput = new HashMap<>();

    public Keys(TxAdmin p) { this.p = p; }

    // ---------- config ----------
    private static String norm(String s) {
        s = s.trim().toUpperCase().replace(" ", "");
        return switch (s) {
            case "F" -> "SWAP";
            case "SHIFT+F" -> "SHIFT_SWAP";
            case "Q" -> "DROP";
            case "SHIFT+Q" -> "SHIFT_DROP";
            case "DOUBLE_SHIFT" -> "DOUBLE_SNEAK";
            case "DOUBLE_CTRL" -> "DOUBLE_SPRINT";
            default -> {
                if (s.matches("[1-9]")) yield "HOTBAR_" + s;
                if (s.matches("SHIFT\\+[1-9]")) yield "SHIFT_HOTBAR_" + s.substring(6);
                yield s;
            }
        };
    }

    private List<String> binds(String action) {
        var cfg = p.getConfig();
        String path = "keybinds." + action;
        List<String> raw = cfg.isList(path) ? cfg.getStringList(path) : List.of(cfg.getString(path, "NONE"));
        return raw.stream().map(Keys::norm).toList();
    }

    private List<String> binds(String action, Player a) {
        String o = p.store.key(a.getUniqueId(), action);
        if (o != null && !o.isBlank()) return List.of(norm(o));
        return binds(action);
    }

    public String bind(String action) { return String.join(" / ", binds(action)); }

    public boolean enabled(Player a) {
        return p.getConfig().getBoolean("keybinds.enabled", true) && !off.contains(a.getUniqueId());
    }

    public boolean toggle(Player a) {
        if (!off.remove(a.getUniqueId())) { off.add(a.getUniqueId()); return false; }
        return true;
    }

    private boolean active(Player a) { return p.isAdmin(a) && enabled(a); }

    private void run(Runnable r) { Bukkit.getScheduler().runTask(p, r); }

    private void perform(Player a, String action) {
        switch (action) {
            case "exit-noclip", "noclip" -> p.actions.noclip(a);
            case "menu" -> p.openMain(a);
            case "players" -> p.openPlayers(a);
            case "god" -> p.actions.god(a, a);
            case "superjump" -> p.actions.superjump(a, a);
            case "heal" -> p.actions.heal(a, a);
            case "back" -> p.actions.back(a);
            case "ids" -> p.actions.toggleIds(a);
            case "names" -> p.actions.toggleNames(a);
            default -> { }
        }
    }

    /** @return true bila trigger dipakai oleh salah satu keybind. */
    private boolean handle(Player a, String trig) {
        trig = norm(trig);
        for (String act : ACTIONS) {
            if (!binds(act, a).contains(trig)) continue;
            if (act.equals("exit-noclip") && !p.modes.noclip.containsKey(a.getUniqueId())) continue;
            if (trig.endsWith("CLICK")) {
                long now = System.currentTimeMillis();
                Long l = lastClick.get(a.getUniqueId());
                if (l != null && now - l < 500) return true;
                lastClick.put(a.getUniqueId(), now);
            }
            run(() -> perform(a, act));
            return true;
        }
        return false;
    }

    private boolean doubleTap(Map<UUID, Long> map, Player a, long windowMs) {
        long now = System.currentTimeMillis();
        Long last = map.put(a.getUniqueId(), now);
        if (last != null && now - last < windowMs) { map.remove(a.getUniqueId()); return true; }
        return false;
    }

    private boolean doubleKey(Player a, String key) {
        String k = a.getUniqueId() + ":" + key;
        long now = System.currentTimeMillis();
        Long last = lastKey.put(k, now);
        if (last != null && now - last < 350) { lastKey.remove(k); return true; }
        return false;
    }

    // ---------- F / Q / hotbar ----------
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player a = e.getPlayer();
        if (active(a) && handle(a, a.isSneaking() ? "SHIFT_SWAP" : "SWAP")) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player a = e.getPlayer();
        if (active(a) && handle(a, a.isSneaking() ? "SHIFT_DROP" : "DROP")) e.setCancelled(true);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player a = e.getPlayer();
        if (!active(a)) return;
        String t = (a.isSneaking() ? "SHIFT_" : "") + "HOTBAR_" + (e.getNewSlot() + 1);
        if (handle(a, t)) e.setCancelled(true);
    }

    // ---------- Shift / Ctrl 2x ----------
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player a = e.getPlayer();
        if (!e.isSneaking() || !active(a)) return;
        if (doubleTap(lastSneak, a, 400)) handle(a, "DOUBLE_SNEAK");
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent e) {
        Player a = e.getPlayer();
        if (!e.isSprinting() || !active(a)) return;
        if (doubleTap(lastSprint, a, 600)) handle(a, "DOUBLE_SPRINT");
    }

    // ---------- W A S D / Spasi: 2x dan kombinasi ----------
    private static boolean down(Input i, String k) {
        return switch (k) {
            case "W" -> i.isForward();
            case "S" -> i.isBackward();
            case "A" -> i.isLeft();
            case "D" -> i.isRight();
            case "SPACE" -> i.isJump();
            case "SHIFT" -> i.isSneak();
            default -> i.isSprint();
        };
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player a = e.getPlayer();
        Input now = e.getInput();
        Input prev = lastInput.put(a.getUniqueId(), now);
        if (prev == null || !active(a)) return;
        for (String k : KEYS) {
            if (!down(now, k) || down(prev, k)) continue;      // hanya saat baru ditekan
            if (!k.equals("SHIFT") && !k.equals("CTRL") && doubleKey(a, k)) handle(a, "DOUBLE_" + k);
            if (!k.equals("SHIFT") && now.isSneak()) handle(a, "SHIFT+" + k);
            if (!k.equals("CTRL") && now.isSprint()) handle(a, "CTRL+" + k);
        }
    }

    // ---------- klik mouse ----------
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player a = e.getPlayer();
        if (active(a)) handle(a, a.isSneaking() ? "SHIFT_LEFT_CLICK" : "LEFT_CLICK");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player a = e.getPlayer();
        if (active(a) && handle(a, a.isSneaking() ? "SHIFT_RIGHT_CLICK" : "RIGHT_CLICK")) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastInput.remove(id);
        lastSneak.remove(id);
        lastSprint.remove(id);
        lastClick.remove(id);
        lastKey.keySet().removeIf(k -> k.startsWith(id.toString()));
    }
}
