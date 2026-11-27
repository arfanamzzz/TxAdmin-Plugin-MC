package dev.txadmin;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Menangani tombol-tombol pada dialog Quick Actions (dibuka lewat tombol Quick Actions di Minecraft). */
public final class QuickActions implements Listener {
    private final TxAdmin p;

    public QuickActions(TxAdmin p) { this.p = p; }

    @EventHandler
    public void onClick(PlayerCustomClickEvent e) {
        Key k = e.getIdentifier();
        if (!k.namespace().equals("txadmin")) return;
        if (!(e.getCommonConnection() instanceof PlayerGameConnection conn)) return;
        Player a = conn.getPlayer();
        String id = k.value();
        Bukkit.getScheduler().runTask(p, () -> {
            if (!p.isAdmin(a)) { a.sendMessage(p.msg("no-permission")); return; }
            switch (id) {
                case "menu" -> p.openMain(a);
                case "players" -> p.openPlayers(a);
                case "noclip" -> p.actions.noclip(a);
                case "god" -> p.actions.god(a, a);
                case "heal" -> p.actions.heal(a, a);
                case "keys" -> p.ui.openKeys(a);
                default -> { }
            }
        });
    }
}
