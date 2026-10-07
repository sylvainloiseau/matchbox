package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma-separated list values ({@code Elaisa, John Smith}); an item containing a comma is
 * double-quoted ({@code "Smith, John"}); the YAML flow style {@code [a, b]} is read too. No JavaFX.
 */
public final class ValueLists {

    private ValueLists() {}

    /** True if the value is written in the flow style {@code [a, b]}. */
    public static boolean isFlow(String value) {
        String v = value == null ? "" : value.strip();
        return v.startsWith("[") && v.endsWith("]");
    }

    /** The items of a value, trimmed, unquoted, empty items dropped. */
    public static List<String> split(String value) {
        List<String> items = new ArrayList<>();
        if (value == null) return items;
        String v = value.strip();
        if (isFlow(v)) {
            v = v.substring(1, v.length() - 1);
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < v.length()) {
                    current.append(v.charAt(++i));
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' && current.toString().isBlank()) {
                current.setLength(0);
                quoted = true;
                wasQuoted = true;
            } else if (c == ',') {
                add(items, current, wasQuoted);
                current.setLength(0);
                wasQuoted = false;
            } else {
                current.append(c);
            }
        }
        add(items, current, wasQuoted);
        return items;
    }

    private static void add(List<String> items, StringBuilder item, boolean quoted) {
        String s = quoted ? item.toString() : item.toString().strip();
        if (!s.isBlank()) items.add(s);
    }

    /** The value of a list of items, in the flow style or not. */
    public static String join(List<String> items, boolean flow) {
        List<String> written = items.stream().map(ValueLists::quote).toList();
        String joined = String.join(", ", written);
        return flow ? "[" + joined + "]" : joined;
    }

    private static String quote(String item) {
        boolean needs = item.contains(",") || item.contains("\"") || item.contains("[") || item.contains("]")
            || !item.equals(item.strip());
        return needs ? "\"" + item.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : item;
    }
}
