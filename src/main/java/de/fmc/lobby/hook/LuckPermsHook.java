package de.fmc.lobby.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * LuckPerms-Anbindung (optional). Liest nur gecachte Daten, keine Datenbankabfragen.
 */
public final class LuckPermsHook {

    private static boolean available;

    private LuckPermsHook() {
    }

    public static void refresh() {
        available = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Anzeigename der Primärgruppe oder null, wenn LuckPerms fehlt / der Nutzer nicht geladen ist.
     * Farbcodes im Display-Namen bleiben erhalten (&-Codes werden vom Aufrufer übersetzt).
     */
    public static String primaryGroup(Player player) {
        if (!available) {
            return null;
        }
        return Access.primaryGroup(player);
    }

    /**
     * Chat-Prefix aus den gecachten Meta-Daten (threadsicher, auch im AsyncChatEvent nutzbar).
     * Liefert null, wenn LuckPerms fehlt oder kein Prefix gesetzt ist.
     */
    public static String prefix(Player player) {
        if (!available) {
            return null;
        }
        return Access.prefix(player);
    }

    /** Getrennte Klasse, damit LuckPerms-Klassen nur bei Bedarf geladen werden. */
    private static final class Access {
        private static String prefix(Player player) {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }
            return user.getCachedData().getMetaData().getPrefix();
        }

        private static String primaryGroup(Player player) {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }
            String groupName = user.getPrimaryGroup();
            Group group = api.getGroupManager().getGroup(groupName);
            if (group != null && group.getDisplayName() != null) {
                return group.getDisplayName();
            }
            if (groupName.isEmpty()) {
                return null;
            }
            return Character.toUpperCase(groupName.charAt(0)) + groupName.substring(1);
        }
    }
}
