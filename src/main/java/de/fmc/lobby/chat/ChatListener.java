package de.fmc.lobby.chat;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.hook.LuckPermsHook;
import de.fmc.lobby.util.Text;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Chat-Format der Lobby und Globalmute.
 * Läuft asynchron: es werden nur gecachte Werte gelesen (Format, LuckPerms-Meta-Cache), kein IO.
 */
public class ChatListener implements Listener {

    public static final String PERM_BYPASS = "lobby.globalmute.bypass";
    public static final String PERM_COLOR = "lobby.chat.color";

    private final FMCLobby plugin;

    /** Wird vom Main-Thread gesetzt und im Async-Chat gelesen */
    private volatile boolean muted;
    private volatile boolean formatEnabled = true;
    /** Format vor und nach %message% (bereits übersetzt) */
    private volatile String formatBefore = "";
    private volatile String formatAfter = "";
    private volatile String defaultPrefix = "";

    public ChatListener(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        formatEnabled = config.getBoolean("chat.enabled", true);
        String format = Text.color(config.getString("chat.format", "%prefix%&7%player% &8» &f%message%"));
        int index = format.indexOf("%message%");
        if (index < 0) {
            plugin.getLogger().warning("chat.format enthält kein %message% – Nachricht wird hinten angehängt.");
            formatBefore = format;
            formatAfter = "";
        } else {
            formatBefore = format.substring(0, index);
            formatAfter = format.substring(index + "%message%".length());
        }
        defaultPrefix = Text.color(config.getString("chat.default-prefix", ""));
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ConfigManager cm = plugin.getConfigManager();

        if (muted && !player.hasPermission(PERM_BYPASS)) {
            event.setCancelled(true);
            cm.sendOrDefault(player, "chat.muted", "%prefix%&cDer Chat ist aktuell deaktiviert.");
            return;
        }
        if (!formatEnabled) {
            return;
        }

        // § kann der Client nicht senden, zur Sicherheit trotzdem entfernen
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).replace("§", "");
        String message = player.hasPermission(PERM_COLOR) ? Text.color(raw) : raw;

        String prefix = LuckPermsHook.prefix(player);
        String before = formatBefore
                .replace("%prefix%", prefix == null ? defaultPrefix : Text.color(prefix))
                .replace("%player%", player.getName());
        String after = formatAfter.replace("%player%", player.getName());

        // Die Nachricht übernimmt die zuletzt aktive Farbe des Formats (z. B. &f vor %message%)
        Component line = Text.component(before)
                .append(Text.component(Text.lastColors(before) + message))
                .append(Text.component(after));

        event.renderer(ChatRenderer.viewerUnaware((source, displayName, msg) -> line));
    }
}
