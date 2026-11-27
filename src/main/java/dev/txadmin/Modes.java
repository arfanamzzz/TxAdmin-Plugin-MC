package dev.txadmin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/** Player Mode ala txAdmin: noclip, godmode, super jump, freeze, efek. */
public final class Modes implements Listener {
    private final TxAdmin p;
    public final Set<UUID> god = new HashSet<>();
    public final Set<UUID> jump = new HashSet<>();
    public final Set<UUID> frozen = new HashSet<>();
    public final Map<UUID, GameMode> noclip = new HashMap<>();
    public final Map<UUID, Location> back = new HashMap<>();
    public final Map<UUID, Set<String>> fx = new HashMap<>();

    public Modes(TxAdmin p) {
        this.p = p;
        Bukkit.getScheduler().runTaskTimer(p, this::sparks, 5L, 5L);
    }

    /** Partikel listrik di sekitar admin yang sedang noclip. */
    private void sparks() {
        if (noclip.isEmpty() || !p.getConfig().getBoolean("effects.noclip-particles", true)) return;
        for (UUID id : noclip.keySet()) {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null) continue;
            pl.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, pl.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.05);
        }
    }

    /** normal / noclip / god / jump */
    public String mode(Player a) {
        UUID id = a.getUniqueId();
        if (noclip.containsKey(id)) return "noclip";
        if (god.contains(id)) return "god";
        if (jump.contains(id)) return "jump";
        return "normal";
    }

    /** Pindah ke satu mode (mode lain dimatikan). Tidak menghapus efek toggle lain. */
    public void setMode(Player a, String mode) {
        UUID id = a.getUniqueId();
        GameMode gm = noclip.remove(id);
        if (gm != null) a.setGameMode(gm);
        god.remove(id);
        if (jump.remove(id)) cmd("effect clear " + a.getName() + " jump_boost");
        switch (mode) {
            case "noclip" -> { noclip.put(id, a.getGameMode()); a.setGameMode(GameMode.SPECTATOR); }
            case "god" -> { god.add(id); heal(a); }
            case "jump" -> { jump.add(id); cmd("effect give " + a.getName() + " jump_boost infinite 4 true"); }
            default -> { }
        }
    }

    public void cmd(String c) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c); }

    public void heal(Player t) {
        var a = t.getAttribute(Attribute.MAX_HEALTH);
        t.setHealth(a == null ? 20.0 : a.getValue());
        t.setFoodLevel(20);
        t.setSaturation(20f);
        t.setFireTicks(0);
    }

    public boolean toggleGod(Player t) {
        if (god.remove(t.getUniqueId())) return false;
        god.add(t.getUniqueId());
        heal(t);
        return true;
    }

    public boolean toggleNoclip(Player t) {
        UUID id = t.getUniqueId();
        if (noclip.containsKey(id)) { t.setGameMode(noclip.remove(id)); return false; }
        noclip.put(id, t.getGameMode());
        t.setGameMode(GameMode.SPECTATOR);
        return true;
    }

    public void enterSpectate(Player t) {
        noclip.putIfAbsent(t.getUniqueId(), t.getGameMode());
        t.setGameMode(GameMode.SPECTATOR);
    }

    public boolean toggleJump(Player t) {
        if (jump.remove(t.getUniqueId())) { cmd("effect clear " + t.getName() + " jump_boost"); return false; }
        jump.add(t.getUniqueId());
        cmd("effect give " + t.getName() + " jump_boost infinite 4 true");
        return true;
    }

    public boolean toggleFrozen(Player t) {
        if (frozen.remove(t.getUniqueId())) return false;
        frozen.add(t.getUniqueId());
        return true;
    }

    /** Toggle efek tak terbatas (id efek vanilla, mis. speed, night_vision). */
    public boolean toggleEffect(Player t, String effect) {
        Set<String> s = fx.computeIfAbsent(t.getUniqueId(), k -> new HashSet<>());
        if (s.remove(effect)) { cmd("effect clear " + t.getName() + " " + effect); return false; }
        s.add(effect);
        cmd("effect give " + t.getName() + " " + effect + " infinite 1 true");
        return true;
    }

    public boolean hasEffect(Player t, String effect) {
        return fx.getOrDefault(t.getUniqueId(), Set.of()).contains(effect);
    }

    /** Kembalikan pemain ke kondisi normal. */
    public void clearAll(Player t) {
        UUID id = t.getUniqueId();
        god.remove(id); jump.remove(id); frozen.remove(id); fx.remove(id);
        GameMode gm = noclip.remove(id);
        if (gm != null) t.setGameMode(gm);
        cmd("effect clear " + t.getName());
    }

    public void cleanup() {
        for (UUID id : new ArrayList<>(noclip.keySet())) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) pl.setGameMode(noclip.get(id));
        }
        noclip.clear();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player pl && god.contains(pl.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (god.contains(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId()) && e.hasChangedPosition()) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { clearAll(e.getPlayer()); back.remove(e.getPlayer().getUniqueId()); }
}
