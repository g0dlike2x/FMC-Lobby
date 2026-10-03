package de.fmc.lobby.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI-Anbindung. Die Klasse wird nur geladen, wenn PlaceholderAPI aktiv ist
 * (Aufrufer prüft vorher {@link #isAvailable()}).
 */
public final class PlaceholderHook {

    private static boolean available;

    private PlaceholderHook() {
    }

    /** Wird beim Start und bei /lobby reload aufgerufen. */
    public static void refresh() {
        available = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static boolean isAvailable() {
        return available;
    }

    /** Ersetzt PAPI-Platzhalter, nur wenn PAPI da ist und der Text überhaupt % enthält. */
    public static String apply(Player player, String text) {
        if (!available || text.indexOf('%') < 0) {
            return text;
        }
        return Access.set(player, text);
    }

    /**
     * TPS über spark (%spark_tps_1m%). Liefert null, wenn PAPI oder spark fehlt.
     */
    public static String sparkTps() {
        if (!available) {
            return null;
        }
        String result = Access.set(null, "%spark_tps_1m%");
        if (result == null || result.isBlank() || result.contains("%spark")) {
            return null;
        }
        return result.trim();
    }

    /** Getrennte Klasse, damit PlaceholderAPI-Klassen nur bei Bedarf geladen werden. */
    private static final class Access {
        private static String set(OfflinePlayer player, String text) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
