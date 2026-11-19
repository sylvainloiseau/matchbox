package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The values offered while typing in a column: those already used, most frequent first. No JavaFX. */
public final class Completion {

    public static final int MAX = 10;

    private Completion() {
    }

    /**
     * @param values the values of the column (one per block)
     * @param typed  the text typed so far
     */
    public static List<String> of(List<String> values, String typed) {
        String prefix = typed.strip().toLowerCase(Locale.ROOT);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String v : values) {
            if (v.isBlank()) continue;
            counts.merge(v, 1, Integer::sum);
        }
        return counts.entrySet().stream()
            .filter(e -> e.getKey().toLowerCase(Locale.ROOT).startsWith(prefix) && !e.getKey().equals(typed.strip()))
            .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> -e.getValue())
                .thenComparing(Map.Entry::getKey, NaturalOrder.INSTANCE))
            .limit(MAX)
            .map(Map.Entry::getKey)
            .toList();
    }
}
