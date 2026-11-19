package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The filters of the Metadata table, combined (AND): one per column (fixed columns by their name,
 * key columns by key), the type choice, the global search on every shown cell, and whether rows
 * without block are shown. Immutable.
 *
 * @param columns              filters by column id ({@link #FILE}, {@link #OBJECT}, or a key)
 * @param type                 the Type shown, empty for all
 * @param search               the global search (any cell)
 * @param showWithoutMetadata  rows without block are shown
 */
public record MetadataFilters(Map<String, ColumnFilter> columns, Optional<String> type, ColumnFilter search,
                              boolean showWithoutMetadata) {

    /** Column id of the File column (keys cannot contain a NUL character). */
    public static final String FILE = "\0file";
    public static final String OBJECT = "\0object";

    public static final MetadataFilters NONE = new MetadataFilters(Map.of(), Optional.empty(), ColumnFilter.NONE, true);

    public MetadataFilters {
        columns = Map.copyOf(columns);
    }

    public MetadataFilters withColumn(String id, ColumnFilter f) {
        Map<String, ColumnFilter> m = new LinkedHashMap<>(columns);
        if (f.equals(ColumnFilter.NONE)) m.remove(id); else m.put(id, f);
        return new MetadataFilters(m, type, search, showWithoutMetadata);
    }

    public MetadataFilters withType(Optional<String> t) {
        return new MetadataFilters(columns, t, search, showWithoutMetadata);
    }

    public MetadataFilters withSearch(ColumnFilter s) {
        return new MetadataFilters(columns, type, s, showWithoutMetadata);
    }

    public MetadataFilters withShowWithoutMetadata(boolean show) {
        return new MetadataFilters(columns, type, search, show);
    }

    /** The filters cleared (the toggle of rows without block kept). */
    public MetadataFilters cleared() {
        return new MetadataFilters(Map.of(), Optional.empty(), ColumnFilter.NONE, showWithoutMetadata);
    }

    public boolean isActive() {
        return type.isPresent() || search.isActive() || columns.values().stream().anyMatch(ColumnFilter::isActive);
    }

    public ColumnFilter column(String id) {
        return columns.getOrDefault(id, ColumnFilter.NONE);
    }

    /**
     * The predicate on rows.
     *
     * @param file      the File column text of a row
     * @param value     the displayed value of a key in a row (own, or inherited when shown)
     * @param shownKeys the key columns shown (the global search looks in them)
     */
    public Predicate<MetadataRow> predicate(Function<MetadataRow, String> file,
                                            BiFunction<MetadataRow, String, Optional<String>> value,
                                            java.util.Collection<String> shownKeys) {
        return row -> {
            if (!showWithoutMetadata && row.withoutBlock()) return false;
            if (type.isPresent() && !type.get().equals(row.type())) return false;
            for (Map.Entry<String, ColumnFilter> e : columns.entrySet()) {
                Optional<String> v = switch (e.getKey()) {
                    case FILE -> Optional.of(file.apply(row));
                    case OBJECT -> Optional.of(row.label());
                    default -> value.apply(row, e.getKey());
                };
                if (!e.getValue().test(v)) return false;
            }
            if (search.isActive()) {
                ColumnFilter any = search.withNegate(false);
                boolean found = any.test(Optional.of(file.apply(row))) || any.test(Optional.of(row.label()))
                    || any.test(Optional.of(row.type()))
                    || shownKeys.stream().anyMatch(k -> value.apply(row, k).map(v -> any.test(Optional.of(v))).orElse(false));
                if (found == search.negate()) return false;
            }
            return true;
        };
    }
}
