package de.fmc.lobby.network;

import de.fmc.lobby.FMCLobby;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Server-Wechsel und Netzwerk-Spielerzahl über den Kanal "BungeeCord".
 * Funktioniert hinter BungeeCord und Velocity (bungee-plugin-message-channel = true).
 */
public class BungeeMessenger implements PluginMessageListener {

    public static final String CHANNEL = "BungeeCord";

    private final FMCLobby plugin;
    /** Letzte Antwort auf PlayerCount ALL, -1 = noch keine Antwort */
    private volatile int networkOnline = -1;
    private BukkitTask countTask;

    public BungeeMessenger(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Messenger messenger = Bukkit.getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        stopTask();
        Messenger messenger = Bukkit.getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /** Startet (oder startet neu) die periodische PlayerCount-Abfrage. */
    public void reload() {
        stopTask();
        long interval = Math.max(1, plugin.getConfigManager().getConfig()
                .getLong("network.player-count-interval-seconds", 5)) * 20L;
        countTask = Bukkit.getScheduler().runTaskTimer(plugin, this::requestNetworkCount, 40L, interval);
    }

    private void stopTask() {
        if (countTask != null) {
            countTask.cancel();
            countTask = null;
        }
    }

    /** Schickt den Spieler auf einen anderen Server im Proxy. */
    public void connect(Player player, String server) {
        player.sendPluginMessage(plugin, CHANNEL, write("Connect", server));
    }

    /** Fragt die Gesamtzahl im Netzwerk an (braucht einen beliebigen Spieler als Träger). */
    public void requestNetworkCount() {
        Iterator<? extends Player> it = Bukkit.getOnlinePlayers().iterator();
        if (!it.hasNext()) {
            return;
        }
        it.next().sendPluginMessage(plugin, CHANNEL, write("PlayerCount", "ALL"));
    }

    /** Netzwerk-Spielerzahl oder -1, falls noch keine Antwort vorliegt. */
    public int getNetworkOnline() {
        return networkOnline;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String sub = in.readUTF();
            if ("PlayerCount".equals(sub)) {
                String server = in.readUTF();
                int count = in.readInt();
                if ("ALL".equalsIgnoreCase(server)) {
                    networkOnline = count;
                }
            }
        } catch (IOException ignored) {
            // Unbekanntes oder kaputtes Paket – ignorieren
        }
    }

    private static byte[] write(String... values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (String value : values) {
                out.writeUTF(value);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
