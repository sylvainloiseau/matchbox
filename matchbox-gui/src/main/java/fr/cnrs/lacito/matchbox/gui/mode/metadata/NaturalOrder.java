package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.Comparator;
import java.util.Locale;

/**
 * The order of the values of a column: runs of digits compared as numbers ("2" &lt; "10", ISO dates
 * in date order), other characters ignoring case ({@link Locale#ROOT}), then exactly; the same on
 * every system.
 */
public final class NaturalOrder implements Comparator<String> {

    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(String a, String b) {
        int c = compare(a, b, true);
        return c != 0 ? c : compare(a, b, false);
    }

    private static int compare(String a, String b, boolean ignoreCase) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int ei = i;
                while (ei < a.length() && Character.isDigit(a.charAt(ei))) ei++;
                int ej = j;
                while (ej < b.length() && Character.isDigit(b.charAt(ej))) ej++;
                String ra = strip(a.substring(i, ei));
                String rb = strip(b.substring(j, ej));
                if (ra.length() != rb.length()) return Integer.compare(ra.length(), rb.length());
                int c = ra.compareTo(rb);
                if (c != 0) return c;
                i = ei;
                j = ej;
                continue;
            }
            int c = ignoreCase
                ? String.valueOf(x).toLowerCase(Locale.ROOT).compareTo(String.valueOf(y).toLowerCase(Locale.ROOT))
                : Character.compare(x, y);
            if (c != 0) return c;
            i++;
            j++;
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static String strip(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') k++;
        return digits.substring(k);
    }
}
