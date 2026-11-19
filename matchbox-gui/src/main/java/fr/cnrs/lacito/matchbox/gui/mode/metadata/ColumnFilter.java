package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The filter of a column: a text matched by containment, equality or a regular expression, with or
 * without case; or only the empty, or only the non-empty cells; and the whole inverted. An invalid
 * regular expression filters nothing ({@link #error()} says why). Immutable.
 */
public record ColumnFilter(String text, Match match, boolean caseSensitive, Cells cells, boolean negate) {

    public enum Match { CONTAINS, EQUALS, REGEX }

    public enum Cells { ANY, EMPTY, NON_EMPTY }

    public static final ColumnFilter NONE = new ColumnFilter("", Match.CONTAINS, false, Cells.ANY, false);

    public ColumnFilter {
        text = text == null ? "" : text;
        match = match == null ? Match.CONTAINS : match;
        cells = cells == null ? Cells.ANY : cells;
    }

    public ColumnFilter withText(String t) {
        return new ColumnFilter(t, match, caseSensitive, cells, negate);
    }

    public ColumnFilter withMatch(Match m) {
        return new ColumnFilter(text, m, caseSensitive, cells, negate);
    }

    public ColumnFilter withCaseSensitive(boolean c) {
        return new ColumnFilter(text, match, c, cells, negate);
    }

    public ColumnFilter withCells(Cells c) {
        return new ColumnFilter(text, match, caseSensitive, c, negate);
    }

    public ColumnFilter withNegate(boolean n) {
        return new ColumnFilter(text, match, caseSensitive, cells, n);
    }

    /** True if the filter hides something. */
    public boolean isActive() {
        return error().isEmpty() && (!text.isEmpty() || cells != Cells.ANY);
    }

    /** True if an option differs from the defaults (the ⚙ mark). */
    public boolean hasOptions() {
        return match != Match.CONTAINS || caseSensitive || cells != Cells.ANY || negate;
    }

    /** The error of an invalid regular expression. */
    public Optional<String> error() {
        if (match != Match.REGEX || text.isEmpty()) return Optional.empty();
        try {
            Pattern.compile(text);
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            return Optional.of(e.getDescription());
        }
    }

    /** True if a cell (empty: no value) passes the filter. */
    public boolean test(Optional<String> value) {
        if (!isActive()) return true;
        String v = value.orElse("");
        boolean ok = switch (cells) {
            case ANY -> true;
            case EMPTY -> v.isEmpty();
            case NON_EMPTY -> !v.isEmpty();
        };
        if (ok && !text.isEmpty()) ok = matches(v);
        return negate != ok;
    }

    private boolean matches(String v) {
        return switch (match) {
            case CONTAINS -> caseSensitive ? v.contains(text) : v.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
            case EQUALS -> caseSensitive ? v.equals(text) : v.equalsIgnoreCase(text);
            case REGEX -> pattern(text, caseSensitive).matcher(v).find();
        };
    }

    /** Compiled patterns of the filters in use (a filter is tested once per row). */
    private static final java.util.Map<String, Pattern> PATTERNS = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Pattern> eldest) {
                return size() > 64;
            }
        });

    private static Pattern pattern(String text, boolean caseSensitive) {
        return PATTERNS.computeIfAbsent((caseSensitive ? "1" : "0") + text,
            k -> Pattern.compile(text, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }
}
