package de.fmc.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Farbcode-Hilfen. Übersetzt &-Codes (inkl. &x&R&R&G&G&B&B) nach § und baut daraus Adventure-Components.
 * Übersetzungen werden von den Managern einmal beim Laden gecacht, nicht pro Tick.
 */
public final class Text {

    /** Legacy-Serializer für §-Codes inkl. Hex im BungeeCord-Format (§x§A§2§3§5§F§F). */
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final String CODES = "0123456789abcdefklmnorx";

    private Text() {
    }

    /** Übersetzt &-Codes nach § und normalisiert alle Codes auf Kleinbuchstaben. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            char c = chars[i];
            if (c == '&' || c == '§') {
                char code = Character.toLowerCase(chars[i + 1]);
                if (CODES.indexOf(code) >= 0) {
                    chars[i] = '§';
                    chars[i + 1] = code;
                }
            }
        }
        return new String(chars);
    }

    /** Übersetzt eine ganze Liste. */
    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    /** §-String → Component (für Chat, ActionBar, Scoreboard, Tablist). */
    public static Component component(String section) {
        if (section == null || section.isEmpty()) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(section);
    }

    /** §-String → Component ohne Kursivschrift (für Item-Namen und Lore). */
    public static Component item(String section) {
        if (section == null || section.isEmpty()) {
            return Component.empty();
        }
        return SERIALIZER.deserialize(section).decoration(TextDecoration.ITALIC, false);
    }

    /** Wandelt %nl% in echte Zeilenumbrüche um. */
    public static String nl(String input) {
        return input.replace("%nl%", "\n");
    }

    /**
     * Liefert die zuletzt aktiven Farb- und Format-Codes eines §-Strings
     * (wie ChatColor.getLastColors, inkl. Hex-Farben).
     */
    public static String lastColors(String input) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        int length = input.length();
        for (int i = 0; i < length - 1; i++) {
            if (input.charAt(i) != '§') {
                continue;
            }
            char code = Character.toLowerCase(input.charAt(i + 1));
            if (code == 'x' && i + 13 < length) {
                // §x§R§R§G§G§B§B = 14 Zeichen
                color = input.substring(i, i + 14);
                formats.setLength(0);
                i += 13;
            } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                color = "§" + code;
                formats.setLength(0);
                i++;
            } else if (code == 'r') {
                color = "";
                formats.setLength(0);
                i++;
            } else if (code >= 'k' && code <= 'o') {
                formats.append('§').append(code);
                i++;
            }
        }
        return color + formats;
    }
}
