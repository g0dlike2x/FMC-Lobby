package de.fmc.lobby.display;

import com.google.gson.JsonObject;
import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.hook.LuckPermsHook;
import de.fmc.lobby.hook.PlaceholderHook;
import de.fmc.lobby.util.NumberFormatter;
import de.fmc.lobby.util.Players;
import de.fmc.lobby.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sidebar wie im CityBuild: ein DUMMY-Objective mit NumberFormat.blank(), max. 14 Zeilen,
 * jede Zeile ist ein Team mit eindeutigem Entry, der Inhalt steht im Team-Prefix.
 * Ein globaler Task (20 Ticks), Team-Prefixe werden nur bei Änderung gesetzt.
 */
public class ScoreboardManager {

    public static final int MAX_LINES = 14;

    /** Eindeutige, unsichtbare Entries: §0§r, §1§r … §d§r */
    private static final String[] ENTRIES = new String[MAX_LINES];

    static {
        String codes = "0123456789abcd";
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + codes.charAt(i) + "§r";
        }
    }

    // ───────────────────────────── Icon-Parser ─────────────────────────────

    private static final Pattern ICON_PATTERN = Pattern.compile("\\{(icon|block|head):([^}]+)}");
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    /** Fertige Icon-Components (unveränderlich, daher wiederverwendbar) */
    private static final Map<String, Component> ICON_CACHE = new ConcurrentHashMap<>();
    private static final Component BEDROCK_ICON = Component.text(" ");

    /**
     * Wandelt eine (bereits übersetzte) Zeile mit Icon-Syntax in eine Component um.
     * <ul>
     *     <li>{icon:item/xyz} → Atlas minecraft:items</li>
     *     <li>{block:block/xyz} → Atlas minecraft:blocks</li>
     *     <li>{head:Name} / {head:Auto} → Spielerkopf</li>
     * </ul>
     * Icons werden als Object-Text-Component über den GsonComponentSerializer erzeugt.
     * Bedrock-Spieler bekommen statt der Icons ein Leerzeichen.
     *
     * @param line       §-übersetzte Zeile
     * @param viewerName Name für {head:Auto}
     * @param bedrock    true = Icons durch Leerzeichen ersetzen
     */
    public static Component parseLine(String line, String viewerName, boolean bedrock) {
        if (line.indexOf('{') < 0) {
            return Text.component(line);
        }
        Matcher matcher = ICON_PATTERN.matcher(line);
        TextComponent.Builder builder = Component.text();
        int last = 0;
        String carry = "";
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String before = line.substring(last, matcher.start());
            if (!before.isEmpty()) {
                String segment = carry + before;
                builder.append(Text.component(segment));
                carry = Text.lastColors(segment);
            }
            builder.append(bedrock ? BEDROCK_ICON : icon(matcher.group(1), matcher.group(2).trim(), viewerName));
            last = matcher.end();
        }
        if (!found) {
            return Text.component(line);
        }
        if (last < line.length()) {
            builder.append(Text.component(carry + line.substring(last)));
        }
        return builder.build();
    }

    private static Component icon(String type, String value, String viewerName) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "object");
        switch (type) {
            case "icon" -> {
                json.addProperty("object", "atlas");
                json.addProperty("atlas", "minecraft:items");
                json.addProperty("sprite", value);
            }
            case "block" -> {
                json.addProperty("object", "atlas");
                json.addProperty("atlas", "minecraft:blocks");
                json.addProperty("sprite", value);
            }
            default -> {
                String name = value.equalsIgnoreCase("auto") ? viewerName : value;
                JsonObject profile = new JsonObject();
                profile.addProperty("name", name);
                json.addProperty("object", "player");
                json.add("player", profile);
                json.addProperty("hat", true);
            }
        }
        String raw = json.toString();
        Component cached = ICON_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        Component component;
        try {
            component = GSON.deserialize(raw);
        } catch (RuntimeException e) {
            component = Component.text("?");
        }
        if (ICON_CACHE.size() < 2048) {
            ICON_CACHE.put(raw, component);
        }
        return component;
    }

    // ───────────────────────────── Sidebar ─────────────────────────────

    private final FMCLobby plugin;
    /** Pro Spieler (nur Main-Thread) */
    private final Map<UUID, Sidebar> boards = new HashMap<>();

    private boolean enabled;
    private String titleTemplate;
    private String rankFallback;
    private String serverName;
    private List<String> lines = List.of();
    /** true = Zeile enthält Platzhalter und muss pro Spieler aufgelöst werden */
    private boolean[] dynamic = new boolean[0];

    private int titleOnline = -1;
    private Component titleJava;
    private Component titleBedrock;

    private BukkitTask task;

    public ScoreboardManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration sb = plugin.getConfigManager().getScoreboards();
        enabled = sb.getBoolean("scoreboard.enabled", true);
        titleTemplate = Text.color(sb.getString("scoreboard.title", "&5&lFARMMC.DE &8(&c%online%&8)"));
        rankFallback = Text.color(sb.getString("scoreboard.rank-fallback", "Spieler"));
        serverName = plugin.getConfigManager().getConfig().getString("server-name", "Lobby");

        List<String> raw = sb.getStringList("scoreboard.lines");
        if (raw.size() > MAX_LINES) {
            plugin.getLogger().warning("scoreboards.yml: " + raw.size() + " Zeilen, es werden nur die ersten "
                    + MAX_LINES + " angezeigt.");
            raw = raw.subList(0, MAX_LINES);
        }
        List<String> translated = new ArrayList<>(raw.size());
        boolean[] dyn = new boolean[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            String line = Text.color(raw.get(i));
            translated.add(line);
            dyn[i] = line.indexOf('%') >= 0;
        }
        lines = translated;
        dynamic = dyn;
        titleOnline = -1;
        ICON_CACHE.clear();

        // Neue Zeilenanzahl → Boards beim nächsten Update neu anlegen
        resetAll();

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
        }
    }

    /** Spieler zurück auf das Haupt-Scoreboard setzen und Cache leeren. */
    private void resetAll() {
        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        resetAll();
    }

    public void remove(UUID uuid) {
        boards.remove(uuid);
    }

    private void update() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online != titleOnline) {
            // Titel nur neu bauen, wenn sich die Zahl ändert
            String title = titleTemplate.replace("%online%", NumberFormatter.format(online));
            titleJava = parseLine(title, "", false);
            titleBedrock = parseLine(title, "", true);
            titleOnline = online;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, online);
        }
    }

    private void update(Player player, int online) {
        boolean bedrock = Players.isBedrock(player);
        Sidebar sidebar = boards.get(player.getUniqueId());
        if (sidebar == null) {
            sidebar = create(player, bedrock);
            boards.put(player.getUniqueId(), sidebar);
        }
        if (sidebar.titleOnline != online) {
            sidebar.objective.displayName(bedrock ? titleBedrock : titleJava);
            sidebar.titleOnline = online;
        }
        String name = player.getName();
        for (int i = 0; i < lines.size(); i++) {
            String value = dynamic[i] ? resolve(lines.get(i), player, online) : lines.get(i);
            if (!value.equals(sidebar.last[i])) {
                sidebar.teams[i].prefix(parseLine(value, name, bedrock));
                sidebar.last[i] = value;
            }
        }
    }

    /** Beim ersten Update anlegen. */
    private Sidebar create(Player player, boolean bedrock) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("fmclobby", Criteria.DUMMY,
                bedrock ? titleBedrock : titleJava);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        int size = lines.size();
        Team[] teams = new Team[size];
        for (int i = 0; i < size; i++) {
            Team team = scoreboard.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(size - i);
            teams[i] = team;
        }
        player.setScoreboard(scoreboard);
        Sidebar sidebar = new Sidebar(objective, teams, new String[size]);
        sidebar.titleOnline = titleOnline;
        return sidebar;
    }

    private String resolve(String line, Player player, int online) {
        String result = line;
        if (result.indexOf('%') >= 0) {
            result = result
                    .replace("%player%", player.getName())
                    .replace("%server%", serverName)
                    .replace("%online%", NumberFormatter.format(online))
                    .replace("%max%", NumberFormatter.format(Bukkit.getMaxPlayers()))
                    .replace("%ping%", NumberFormatter.format(player.getPing()));
            if (result.contains("%rank%")) {
                String rank = LuckPermsHook.primaryGroup(player);
                result = result.replace("%rank%", rank == null ? rankFallback : Text.color(rank));
            }
            if (result.contains("%network_online%")) {
                int network = plugin.getMessenger().getNetworkOnline();
                result = result.replace("%network_online%", NumberFormatter.format(network < 0 ? online : network));
            }
            String papi = PlaceholderHook.apply(player, result);
            if (!papi.equals(result)) {
                // PAPI-Ergebnisse können &-Codes enthalten
                result = Text.color(papi);
            }
        }
        return result;
    }

    /** Zustand pro Spieler: letzte Werte für den Änderungsvergleich. */
    private static final class Sidebar {
        final Objective objective;
        final Team[] teams;
        final String[] last;
        int titleOnline;

        Sidebar(Objective objective, Team[] teams, String[] last) {
            this.objective = objective;
            this.teams = teams;
            this.last = last;
        }
    }
}
