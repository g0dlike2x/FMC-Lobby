package de.fmc.lobby.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Einheitliche GUI-Sounds wie im CityBuild.
 */
public final class Sounds {

    private Sounds() {
    }

    /** Menü öffnen */
    public static void open(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.5f, 1.4f);
    }

    /** Normaler Klick */
    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /** Ablehnen (gesperrt, offline, Limit …) */
    public static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /** Umschalten: an */
    public static void toggleOn(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 1.4f);
    }

    /** Umschalten: aus */
    public static void toggleOff(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 0.8f);
    }
}
