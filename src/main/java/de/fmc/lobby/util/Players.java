package de.fmc.lobby.util;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Kleine Spieler-Hilfen.
 */
public final class Players {

    private Players() {
    }

    /** Bedrock-Spieler (Floodgate-Prefix ".") */
    public static boolean isBedrock(Player player) {
        return player.getName().startsWith(".");
    }

    /** Vanish über das übliche Metadata-Flag "vanished" (SuperVanish, PremiumVanish, Essentials u. a.). */
    public static boolean isVanished(Player player) {
        if (!player.hasMetadata("vanished")) {
            return false;
        }
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
