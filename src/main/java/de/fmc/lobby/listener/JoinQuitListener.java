package de.fmc.lobby.listener;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Join/Quit ohne Nachrichten. Im Join-Pfad gibt es kein Datei-, Datenbank- oder Netzwerk-IO:
 * nur geklonte Items, ein Teleport und das Lesen des PDC.
 */
public class JoinQuitListener implements Listener {

    private final FMCLobby plugin;

    public JoinQuitListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    /** HIGHEST, damit andere Plugins die Join-Nachricht nicht wieder setzen. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        plugin.preparePlayer(player, true);
        plugin.getQueue().onJoin(player);

        // Willkommensnachricht 5 Ticks nach dem Join
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendWelcome(player);
            }
        }, 5L);
    }

    private void sendWelcome(Player player) {
        List<String> lines = plugin.getConfigManager().getMessageList("welcome", new ArrayList<>());
        String name = player.getName();
        for (String line : lines) {
            String text = Text.nl(line.replace("%player%", name));
            player.sendMessage(text.isEmpty() ? Component.empty() : Text.component(text));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        UUID uuid = event.getPlayer().getUniqueId();
        // Spielerdaten aufräumen (Baublöcke laufen in der Queue weiter)
        plugin.getScoreboard().remove(uuid);
        plugin.getTablist().remove(uuid);
        plugin.getTeleporter().remove(uuid);
        plugin.getParticles().unload(uuid);
        plugin.getGrapple().remove(uuid);
        plugin.getBuildMode().remove(uuid);
        plugin.getQueue().remove(event.getPlayer());
        plugin.getDoubleJump().remove(uuid);
    }
}
