package de.fmc.lobby.teleporter;

/**
 * Gecachter Status eines Servers (unveränderlich, daher threadsicher).
 */
public record ServerStatus(boolean online, int players, int max) {

    public static final ServerStatus OFFLINE = new ServerStatus(false, 0, 0);
}
