package de.fmc.lobby.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Alle PersistentDataContainer-Keys des Plugins.
 */
public final class Keys {

    /** Kennung der Lobby-Hotbar-Items (Wert: teleporter, particles, grapple, buildblock) */
    public static NamespacedKey ITEM;
    /** Partikel-Auswahl am Spieler */
    public static NamespacedKey PARTICLE;
    /** Markiert einen Enterhaken-Schwimmer */
    public static NamespacedKey GRAPPLE_HOOK;
    /** Markiert einen Schwimmer, der an einem Block hängt */
    public static NamespacedKey GRAPPLE_ANCHORED;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM = new NamespacedKey(plugin, "item");
        PARTICLE = new NamespacedKey(plugin, "particle");
        GRAPPLE_HOOK = new NamespacedKey(plugin, "grapple_hook");
        GRAPPLE_ANCHORED = new NamespacedKey(plugin, "grapple_anchored");
    }
}
