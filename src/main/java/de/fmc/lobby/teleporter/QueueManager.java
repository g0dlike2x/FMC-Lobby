package de.fmc.lobby.teleporter;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.config.ConfigManager;
import de.fmc.lobby.util.NumberFormatter;
import de.fmc.lobby.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warteschlange und BossBar für den Teleporter.
 * <ul>
 *     <li>Wer auf einen offline Server klickt, kommt in die Warteschlange.</li>
 *     <li>Meldet der Ping wieder "online", werden alle gestaffelt verbunden (connects-per-second).</li>
 *     <li>Solange ein Server offline ist, sehen alle eine gemeinsame BossBar mit der Offline-Zeit.</li>
 * </ul>
 * Alles läuft auf dem Main-Thread und liest nur den Status-Cache des {@link TeleporterService}.
 */
public class QueueManager {

    private final FMCLobby plugin;
    private final TeleporterService service;

    /** Zustand pro Teleporter-Eintrag (Key = Eintrag-ID) */
    private final Map<String, State> states = new HashMap<>();
    /** Spieler → Eintrag-ID, in dessen Warteschlange er steht */
    private final Map<UUID, String> queuedIn = new HashMap<>();

    private BukkitTask secondTask;
    private BukkitTask connectTask;

    private boolean queueEnabled;
    private boolean bossbarEnabled;
    /** Verbindungen pro Tick (connects-per-second / 20) */
    private double perTick;
    private double budget;
    private long connectDelayMs;

    private String barOffline;
    private String barOnline;
    private String actionBar;
    private String msgConnecting;
    private String msgBackOnline;

    public QueueManager(FMCLobby plugin, TeleporterService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ───────────────────────────── Laden ─────────────────────────────

    public void reload() {
        ConfigManager cm = plugin.getConfigManager();
        YamlConfiguration config = cm.getConfig();
        queueEnabled = config.getBoolean("teleporter.queue.enabled", true);
        perTick = Math.max(1, Math.min(40, config.getInt("teleporter.queue.connects-per-second", 5))) / 20.0;
        connectDelayMs = Math.max(0, config.getLong("teleporter.queue.connect-delay-seconds", 5)) * 1000L;
        bossbarEnabled = config.getBoolean("teleporter.bossbar.enabled", true);

        barOffline = cm.getMessageOrDefault("teleporter.bossbar-offline",
                "§c✖ §a%server% §7startet neu§8… §7offline seit §e%time%");
        barOnline = cm.getMessageOrDefault("teleporter.bossbar-online",
                "§a✔ %server% §7ist wieder online§8, §7du wirst verbunden§8… §8(§e%queue% §7wartend§8)");
        actionBar = cm.getMessageOrDefault("teleporter.queue-actionbar",
                "§7Warteschlange §a%server%§8: §7Platz §e%position%§8/§e%size%");
        msgConnecting = cm.getMessageOrDefault("teleporter.queue-connecting",
                "%prefix%§a%server% §7ist wieder online. Du wirst verbunden§8...");
        msgBackOnline = cm.getMessageOrDefault("teleporter.back-online",
                "%prefix%§a%server% §7ist wieder online. Nutze den §eTeleporter§7, um beizutreten.");

        // Zustände an neue Einträge anpassen, bestehende Warteschlangen bleiben erhalten
        Set<String> ids = new HashSet<>();
        for (ServerEntry entry : service.getEntries()) {
            ids.add(entry.id());
            State state = states.computeIfAbsent(entry.id(), k -> new State());
            state.entry = entry;
            state.lastText = null;
        }
        Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, State> e = it.next();
            if (!ids.contains(e.getKey())) {
                State removed = e.getValue();
                setViewers(removed, List.of());
                removed.queue.forEach(queuedIn::remove);
                it.remove();
            }
        }
        if (!queueEnabled) {
            clearQueues();
        }

        cancelTasks();
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::secondTick, 20L, 20L);
        connectTask = Bukkit.getScheduler().runTaskTimer(plugin, this::connectTick, 1L, 1L);
    }

    private void clearQueues() {
        for (State state : states.values()) {
            state.queue.clear();
        }
        queuedIn.clear();
    }

    private void cancelTasks() {
        if (secondTask != null) {
            secondTask.cancel();
            secondTask = null;
        }
        if (connectTask != null) {
            connectTask.cancel();
            connectTask = null;
        }
    }

    public void shutdown() {
        cancelTasks();
        for (State state : states.values()) {
            setViewers(state, List.of());
        }
        states.clear();
        queuedIn.clear();
    }

    // ───────────────────────────── Spieler ─────────────────────────────

    /** Beim Join: laufende Offline-BossBars sofort zeigen (nicht erst im nächsten Durchgang). */
    public void onJoin(Player player) {
        if (!bossbarEnabled) {
            return;
        }
        for (State state : states.values()) {
            if (state.known && !state.online && state.entry.bossbar() && state.bar != null
                    && state.viewers.add(player.getUniqueId())) {
                player.showBossBar(state.bar);
            }
        }
    }

    /** Beim Quit: aus Warteschlange und BossBars entfernen. */
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        leave(uuid);
        for (State state : states.values()) {
            if (state.viewers.remove(uuid) && state.bar != null) {
                player.hideBossBar(state.bar);
            }
        }
    }

    // ───────────────────────────── Warteschlange ─────────────────────────────

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public boolean isQueued(Player player, ServerEntry entry) {
        return entry.id().equals(queuedIn.get(player.getUniqueId()));
    }

    /**
     * true, wenn ein Klick in die Warteschlange führt statt direkt zu verbinden:
     * Server offline, Server gerade erst online (Startpuffer) oder es warten bereits Spieler.
     */
    public boolean shouldQueue(ServerEntry entry) {
        if (!queueEnabled) {
            return false;
        }
        if (!service.getStatus(entry).online()) {
            return true;
        }
        State state = states.get(entry.id());
        if (state == null) {
            return false;
        }
        return !state.queue.isEmpty()
                || (state.known && state.online && System.currentTimeMillis() - state.changedAt < connectDelayMs);
    }

    /** Spieler einreihen (verlässt dabei eine andere Warteschlange). @return Position ab 1 */
    public int join(Player player, ServerEntry entry) {
        UUID uuid = player.getUniqueId();
        leave(uuid);
        State state = states.computeIfAbsent(entry.id(), k -> {
            State s = new State();
            s.entry = entry;
            return s;
        });
        state.queue.add(uuid);
        state.initialSize = Math.max(state.initialSize, state.queue.size());
        queuedIn.put(uuid, entry.id());
        return state.queue.size();
    }

    public void leave(Player player) {
        leave(player.getUniqueId());
    }

    private void leave(UUID uuid) {
        String id = queuedIn.remove(uuid);
        if (id == null) {
            return;
        }
        State state = states.get(id);
        if (state != null) {
            state.queue.remove(uuid);
        }
    }

    public int queueSize(ServerEntry entry) {
        State state = states.get(entry.id());
        return state == null ? 0 : state.queue.size();
    }

    // ───────────────────────────── Tasks ─────────────────────────────

    /** Jeden Tick: Warteschlangen gestaffelt abarbeiten. */
    private void connectTick() {
        if (queuedIn.isEmpty()) {
            budget = 0;
            return;
        }
        // Budget wird nur bei Bedarf aufgebaut und gedeckelt, damit kein Schwall entsteht
        budget = Math.min(budget + perTick, Math.max(1.0, perTick * 20));
        long now = System.currentTimeMillis();
        boolean progress = true;
        while (budget >= 1 && progress) {
            progress = false;
            for (State state : states.values()) {
                if (budget < 1) {
                    break;
                }
                if (!state.known || !state.online || state.queue.isEmpty()
                        || now - state.changedAt < connectDelayMs
                        || !service.getStatus(state.entry).online()) {
                    continue;
                }
                Iterator<UUID> it = state.queue.iterator();
                UUID uuid = it.next();
                it.remove();
                queuedIn.remove(uuid);
                progress = true;

                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue; // offline – kostet kein Budget
                }
                budget -= 1;
                player.sendMessage(Text.component(msgConnecting.replace("%server%", state.entry.plainName())));
                plugin.getMessenger().connect(player, state.entry.proxyName());
            }
        }
    }

    /** Jede Sekunde: Statuswechsel erkennen, BossBars und ActionBars aktualisieren. */
    private void secondTick() {
        long now = System.currentTimeMillis();
        boolean menusDirty = false;
        for (ServerEntry entry : service.getEntries()) {
            State state = states.get(entry.id());
            if (state == null || !service.hasStatus(entry)) {
                continue; // noch kein Ping, sonst gäbe es beim Start ein falsches "wieder online"
            }
            state.entry = entry;
            boolean online = service.getStatus(entry).online();
            if (!state.known) {
                state.known = true;
                state.online = online;
                state.changedAt = now;
            } else if (online != state.online) {
                state.online = online;
                state.changedAt = now;
                if (online) {
                    state.initialSize = state.queue.size();
                    announceOnline(state);
                }
            }
            updateBar(state, now);
            sendActionBars(state);

            if (state.queue.size() != state.lastQueueSize) {
                state.lastQueueSize = state.queue.size();
                menusDirty = true;
            }
        }
        if (menusDirty) {
            service.refreshOpenMenus();
        }
    }

    /** Wer nicht in der Warteschlange steht, bekommt einen Hinweis im Chat. */
    private void announceOnline(State state) {
        Component message = Text.component(msgBackOnline.replace("%server%", state.entry.plainName()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!state.queue.contains(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
    }

    private void updateBar(State state, long now) {
        if (!bossbarEnabled || !state.entry.bossbar()) {
            setViewers(state, List.of());
            return;
        }
        if (state.bar == null) {
            state.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        }
        String name = state.entry.plainName();
        Collection<? extends Player> desired;
        String text;
        if (!state.online) {
            desired = Bukkit.getOnlinePlayers();
            text = barOffline.replace("%server%", name).replace("%time%", duration(now - state.changedAt));
            state.bar.color(BossBar.Color.RED);
            state.bar.progress(1f);
        } else if (!state.queue.isEmpty()) {
            // Nur noch die Wartenden sehen die Leiste, sie läuft mit der Warteschlange leer
            desired = queuedPlayers(state);
            text = barOnline.replace("%server%", name)
                    .replace("%queue%", NumberFormatter.format(state.queue.size()));
            state.initialSize = Math.max(state.initialSize, state.queue.size());
            state.bar.color(BossBar.Color.GREEN);
            state.bar.progress(Math.max(0f, Math.min(1f, (float) state.queue.size() / state.initialSize)));
        } else {
            setViewers(state, List.of());
            return;
        }
        if (!text.equals(state.lastText)) {
            state.bar.name(Text.component(text));
            state.lastText = text;
        }
        setViewers(state, desired);
    }

    private List<Player> queuedPlayers(State state) {
        List<Player> players = new ArrayList<>(state.queue.size());
        for (UUID uuid : state.queue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** BossBar nur an Spieler senden, die sie noch nicht sehen, und bei den anderen ausblenden. */
    private void setViewers(State state, Collection<? extends Player> desired) {
        if (state.bar == null) {
            state.viewers.clear();
            return;
        }
        Set<UUID> wanted = new HashSet<>(desired.size() * 2);
        for (Player player : desired) {
            UUID uuid = player.getUniqueId();
            wanted.add(uuid);
            if (state.viewers.add(uuid)) {
                player.showBossBar(state.bar);
            }
        }
        Iterator<UUID> it = state.viewers.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            if (!wanted.contains(uuid)) {
                it.remove();
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.hideBossBar(state.bar);
                }
            }
        }
    }

    private void sendActionBars(State state) {
        if (state.queue.isEmpty() || actionBar.isEmpty()) {
            return;
        }
        String size = NumberFormatter.format(state.queue.size());
        String base = actionBar.replace("%server%", state.entry.plainName()).replace("%size%", size);
        int position = 0;
        for (UUID uuid : state.queue) {
            position++;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendActionBar(Text.component(base.replace("%position%", NumberFormatter.format(position))));
            }
        }
    }

    /** 133 000 ms → "2:13", ab einer Stunde "1:02:13" */
    private static String duration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%d:%02d", minutes, secs);
    }

    /** Zustand eines Eintrags (nur Main-Thread). */
    private static final class State {
        final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
        final Set<UUID> viewers = new HashSet<>();
        ServerEntry entry;
        BossBar bar;
        boolean known;
        boolean online;
        long changedAt;
        int initialSize;
        int lastQueueSize;
        String lastText;
    }
}
