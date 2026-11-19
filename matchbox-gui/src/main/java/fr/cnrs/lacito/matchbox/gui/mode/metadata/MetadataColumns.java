package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The key columns of the Metadata mode: the union of the keys of the blocks (exact keys: {@code Media}
 * and {@code media} are two columns), with the number of blocks having each, in an order. No JavaFX.
 */
public final class MetadataColumns {

    public enum Order { FIRST_APPEARANCE, FREQUENCY, ALPHABETIC }

    private final Map<String, Integer> counts;

    private MetadataColumns(Map<String, Integer> counts) {
        this.counts = counts;
    }

    /** The keys of the rows, in order of first appearance (rows in file order, then text order). */
    public static MetadataColumns of(List<MetadataRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MetadataRow row : rows) {
            row.block().ifPresent(b -> new LinkedHashSet<>(b.values().keySet()).forEach(k -> counts.merge(k, 1, Integer::sum)));
        }
        return new MetadataColumns(counts);
    }

    /** The number of blocks having the key. */
    public int count(String key) {
        return counts.getOrDefault(key, 0);
    }

    public Set<String> keys() {
        return counts.keySet();
    }

    /** The other keys equal to this one ignoring case ({@code Media} for {@code media}). */
    public List<String> caseVariants(String key) {
        return counts.keySet().stream().filter(k -> !k.equals(key) && k.equalsIgnoreCase(key)).toList();
    }

    /**
     * The keys in order: those of {@code userOrder} first (the columns the user moved), in that
     * order, then the others in the given order.
     */
    public List<String> ordered(Order order, List<String> userOrder) {
        List<String> keys = new ArrayList<>(counts.keySet());
        switch (order) {
            case FREQUENCY -> keys.sort(Comparator.comparingInt((String k) -> -counts.get(k)));
            case ALPHABETIC -> keys.sort(Comparator.comparing((String k) -> k.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()));
            case FIRST_APPEARANCE -> { }
        }
        List<String> result = new ArrayList<>();
        for (String k : userOrder) if (counts.containsKey(k) && !result.contains(k)) result.add(k);
        for (String k : keys) if (!result.contains(k)) result.add(k);
        return result;
    }
}
