package de.fmc.lobby.config;

import de.fmc.lobby.FMCLobby;
import de.fmc.lobby.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Lädt settings/config.yml, settings/scoreboards.yml und settings/messages.yml.
 * Bestehende Dateien werden nie überschrieben (saveResource(…, false) nur wenn die Datei fehlt).
 * Nachrichten werden einmal übersetzt und gecacht.
 */
public class ConfigManager {

    private static final String FOLDER = "settings";

    private final FMCLobby plugin;
    private File folder;
    private YamlConfiguration config;
    private YamlConfiguration scoreboards;
    private YamlConfiguration messages;

    /** Übersetzte Einzel-Nachrichten (Key ohne "messages.") */
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    /** Übersetzte Listen-Nachrichten */
    private final Map<String, List<String>> listCache = new ConcurrentHashMap<>();
    /** Bereits gewarnte Keys, damit das Log nicht vollläuft */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private String prefix = "";
    private Location spawn;

    public ConfigManager(FMCLobby plugin) {
        this.plugin = plugin;
    }

    /** Lädt (oder lädt neu) alle Dateien. */
    public void load() {
        folder = new File(plugin.getDataFolder(), FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Konnte den Ordner " + folder.getPath() + " nicht anlegen.");
        }
        config = loadFile("config.yml");
        scoreboards = loadFile("scoreboards.yml");
        messages = loadFile("messages.yml");

        messageCache.clear();
        listCache.clear();
        warned.clear();
        prefix = Text.color(messages.getString("messages.prefix", "&8┃ &x&A&2&3&5&F&F&lLOBBY &8» &7"));
        spawn = null;
    }

    private YamlConfiguration loadFile(String name) {
        File file = new File(folder, name);
        if (!file.exists()) {
            plugin.saveResource(FOLDER + "/" + name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public YamlConfiguration getScoreboards() {
        return scoreboards;
    }

    public YamlConfiguration getMessages() {
        return messages;
    }

    public String getPrefix() {
        return prefix;
    }

    // ───────────────────────────── Nachrichten ─────────────────────────────

    /**
     * Übersetzte Nachricht. Fehlt der Key, wird einmalig gewarnt und [MSG:key] geliefert.
     */
    public String getMessage(String key) {
        String cached = messageCache.get(key);
        if (cached != null) {
            return cached;
        }
        String raw = messages.getString("messages." + key);
        if (raw == null) {
            if (warned.add(key)) {
                plugin.getLogger().warning("Fehlender Nachrichten-Key in messages.yml: messages." + key);
            }
            return "[MSG:" + key + "]";
        }
        String translated = Text.color(raw).replace("%prefix%", prefix);
        messageCache.put(key, translated);
        return translated;
    }

    /**
     * Übersetzte Nachricht mit Fallback für neue Keys, die in alten messages.yml noch fehlen.
     */
    public String getMessageOrDefault(String key, String fallback) {
        String cached = messageCache.get(key);
        if (cached != null) {
            return cached;
        }
        String raw = messages.getString("messages." + key, fallback);
        String translated = Text.color(raw).replace("%prefix%", prefix);
        messageCache.put(key, translated);
        return translated;
    }

    /** Übersetzte Liste (z. B. welcome, Lore-Vorlagen). Fehlt der Key, wird gewarnt und fallback genutzt. */
    public List<String> getMessageList(String key, List<String> fallback) {
        List<String> cached = listCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> raw = messages.getStringList("messages." + key);
        if (raw.isEmpty() && !messages.isList("messages." + key)) {
            if (warned.add(key)) {
                plugin.getLogger().warning("Fehlende Nachrichten-Liste in messages.yml: messages." + key);
            }
            raw = fallback;
        }
        List<String> translated = new ArrayList<>(raw.size());
        for (String line : raw) {
            translated.add(Text.color(line).replace("%prefix%", prefix));
        }
        List<String> result = Collections.unmodifiableList(translated);
        listCache.put(key, result);
        return result;
    }

    /** Nachricht mit Platzhaltern senden: send(player, "key", "%player%", name, …) */
    public void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(Text.component(replace(getMessage(key), replacements)));
    }

    /** Wie send, aber mit Fallback-Text für neue Keys */
    public void sendOrDefault(CommandSender sender, String key, String fallback, String... replacements) {
        sender.sendMessage(Text.component(replace(getMessageOrDefault(key, fallback), replacements)));
    }

    /** Nachricht in die ActionBar */
    public void actionBar(Player player, String key, String... replacements) {
        player.sendActionBar(Text.component(replace(getMessage(key), replacements)));
    }

    public static String replace(String text, String... replacements) {
        String result = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    // ───────────────────────────── Spawn ─────────────────────────────

    /** Gecachter Spawn. Fallback: Spawn der Lobby-Welt. */
    public Location getSpawn() {
        if (spawn != null && spawn.getWorld() != null) {
            return spawn;
        }
        String worldName = config.getString("spawn.world", config.getString("world.name", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (config.isConfigurationSection("spawn")) {
            spawn = new Location(world,
                    config.getDouble("spawn.x"),
                    config.getDouble("spawn.y"),
                    config.getDouble("spawn.z"),
                    (float) config.getDouble("spawn.yaw"),
                    (float) config.getDouble("spawn.pitch"));
        } else {
            spawn = world.getSpawnLocation();
        }
        return spawn;
    }

    /** Spawn setzen und config.yml speichern (Kommentare bleiben erhalten). */
    public void setSpawn(Location location) {
        config.set("spawn.world", location.getWorld().getName());
        config.set("spawn.x", round(location.getX()));
        config.set("spawn.y", round(location.getY()));
        config.set("spawn.z", round(location.getZ()));
        config.set("spawn.yaw", round(location.getYaw()));
        config.set("spawn.pitch", round(location.getPitch()));
        try {
            config.save(new File(folder, "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte config.yml nicht speichern", e);
        }
        spawn = location.clone();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
