package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.MetadataEntry;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The problems of the cells of a row: the diagnostics (parse and checks) whose span lies in the
 * value of a key, and those of the block not in a value. No JavaFX.
 *
 * @param byKey the problems of each key's value
 * @param block the other problems inside the block (known only when the grammar gives its span)
 */
public record CellProblems(Map<String, List<Diagnostic>> byKey, List<Diagnostic> block) {

    public static final CellProblems NONE = new CellProblems(Map.of(), List.of());

    public static CellProblems of(MetadataRow row, List<Diagnostic> diagnostics) {
        if (row.block().isEmpty() || diagnostics.isEmpty()) return NONE;
        Map<String, List<Diagnostic>> byKey = new LinkedHashMap<>();
        List<Diagnostic> rest = new ArrayList<>();
        var block = row.block().get();
        for (Diagnostic d : diagnostics) {
            boolean placed = false;
            for (MetadataEntry e : block.entries()) {
                if (e.valueSpan().map(s -> inside(d, s)).orElse(false)) {
                    byKey.computeIfAbsent(e.key(), k -> new ArrayList<>()).add(d);
                    placed = true;
                    break;
                }
            }
            if (!placed && block.span().map(s -> inside(d, s)).orElse(false)) rest.add(d);
        }
        return byKey.isEmpty() && rest.isEmpty() ? NONE : new CellProblems(Map.copyOf(byKey), List.copyOf(rest));
    }

    public List<Diagnostic> of(String key) {
        return byKey.getOrDefault(key, List.of());
    }

    /** A diagnostic inside a span (an empty diagnostic at its start included). */
    private static boolean inside(Diagnostic d, TextCoordinate span) {
        int start = d.startIndex();
        int stop = Math.max(d.startIndex(), d.stopIndex());
        int end = Math.max(span.startIndex(), span.stopIndex());
        return start >= span.startIndex() && stop <= end;
    }
}
