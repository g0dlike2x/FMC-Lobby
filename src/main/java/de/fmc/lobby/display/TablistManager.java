package de.fmc.lobby.display;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.hook.PlaceholderHook;
import de.fmc.lobby.util.NumberFormatter;
import de.fmc.lobby.util.Players;
import de.fmc.lobby.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tablist wie im CityBuild: Header/Footer aus der Config, jede Sekunde aktualisiert.
 * Globale Teile werden einmal pro Sekunde für alle gebaut; nur spielerbezogene Vorlagen
 * (%player%, {head:Auto}) werden pro Spieler geparst. Gesendet wird nur bei Änderung.
 */
public class TablistManager {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    private final FMCLobby plugin;
    /** Zuletzt gesendete Version pro Spieler */
    private final Map<UUID, Integer> sentVersion = new HashMap<>();

    private boolean enabled;
    private String headerTemplate = "";
    private String footerTemplate = "";
    private boolean headerPerPlayer;
    private boolean footerPerPlayer;

    private String lastHeader;
    private String lastFooter;
    private int version;
    private Component headerJava;
    private Component headerBedrock;
    private Component footerJava;
    private Component footerBedrock;

    private BukkitTask task;

    public TablistManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration config = plugin.getConfigManager().getConfig();
        enabled = config.getBoolean("tablist.enabled", true);
        headerTemplate = Text.color(String.join("", config.getStringList("tablist.header")));
        footerTemplate = Text.color(String.join("", config.getStringList("tablist.footer")));
        headerPerPlayer = isPerPlayer(headerTemplate);
        footerPerPlayer = isPerPlayer(footerTemplate);
        lastHeader = null;
        lastFooter = null;
        version++;
        sentVersion.clear();

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
    }

    private static boolean isPerPlayer(String template) {
        return template.contains("%player%") || template.toLowerCase(Locale.ROOT).contains("{head:auto}");
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sentVersion.clear();
    }

    public void remove(UUID uuid) {
        sentVersion.remove(uuid);
    }

    private void update() {
        String time = ZonedDateTime.now(BERLIN).format(TIME);
        String tps = tps();
        String online = NumberFormatter.format(Bukkit.getOnlinePlayers().size());

        String header = Text.nl(fill(headerTemplate, time, tps, online));
        String footer = Text.nl(fill(footerTemplate, time, tps, online));
        if (!header.equals(lastHeader) || !footer.equals(lastFooter)) {
            lastHeader = header;
            lastFooter = footer;
            version++;
            // Globale Components nur bauen, wenn sie gebraucht werden
            headerJava = headerPerPlayer ? null : ScoreboardManager.parseLine(header, "", false);
            headerBedrock = headerPerPlayer ? null : ScoreboardManager.parseLine(header, "", true);
            footerJava = footerPerPlayer ? null : ScoreboardManager.parseLine(footer, "", false);
            footerBedrock = footerPerPlayer ? null : ScoreboardManager.parseLine(footer, "", true);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Integer sent = sentVersion.get(player.getUniqueId());
            if (sent != null && sent == version) {
                continue;
            }
            boolean bedrock = Players.isBedrock(player);
            String name = player.getName();
            Component h = headerPerPlayer
                    ? ScoreboardManager.parseLine(header.replace("%player%", name), name, bedrock)
                    : (bedrock ? headerBedrock : headerJava);
            Component f = footerPerPlayer
                    ? ScoreboardManager.parseLine(footer.replace("%player%", name), name, bedrock)
                    : (bedrock ? footerBedrock : footerJava);
            player.sendPlayerListHeaderAndFooter(h, f);
            sentVersion.put(player.getUniqueId(), version);
        }
    }

    private static String fill(String template, String time, String tps, String online) {
        if (template.indexOf('%') < 0) {
            return template;
        }
        return template.replace("%time%", time).replace("%tps%", tps).replace("%online%", online);
    }

    /** %spark_tps_1m% über PAPI, sonst Bukkit.getTPS()[0] mit Farbe. */
    private static String tps() {
        String spark = PlaceholderHook.sparkTps();
        if (spark != null) {
            return Text.color(spark);
        }
        double value = Math.min(20.0, Bukkit.getTPS()[0]);
        String color = value >= 18 ? "§a" : value >= 15 ? "§e" : "§c";
        return color + NumberFormatter.decimal(value, 1);
    }
}
