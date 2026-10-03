package de.fmc.lobby.protection;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spieler im Baumodus (/lobby build): Für sie ist der Lobby-Schutz aus.
 */
public class BuildModeManager {

    private final Set<UUID> builders = new HashSet<>();

    public boolean isBuilder(Player player) {
        return !builders.isEmpty() && builders.contains(player.getUniqueId());
    }

    /** @return neuer Zustand (true = Baumodus an) */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (builders.remove(uuid)) {
            return false;
        }
        builders.add(uuid);
        return true;
    }

    public void remove(UUID uuid) {
        builders.remove(uuid);
    }
}
