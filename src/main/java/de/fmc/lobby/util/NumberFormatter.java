package de.fmc.lobby.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Zahlen im deutschen Format mit Leerzeichen als Tausendertrenner (1 234) und Komma als Dezimaltrenner (1,5).
 */
public final class NumberFormatter {

    private NumberFormatter() {
    }

    /** 1234567 → "1 234 567" */
    public static String format(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        sb.append(digits, 0, firstGroup);
        for (int i = firstGroup; i < digits.length(); i += 3) {
            sb.append(' ').append(digits, i, i + 3);
        }
        return value < 0 ? "-" + sb : sb.toString();
    }

    /** 1234.56 mit 1 Nachkommastelle → "1 234,6" */
    public static String decimal(double value, int decimals) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
        long integerPart = rounded.longValue();
        String result = format(integerPart);
        if (rounded.signum() < 0 && integerPart == 0) {
            result = "-" + result;
        }
        if (decimals <= 0) {
            return result;
        }
        String fraction = rounded.abs().toPlainString();
        int dot = fraction.indexOf('.');
        return result + "," + (dot >= 0 ? fraction.substring(dot + 1) : "0".repeat(decimals));
    }
}
