package fr.cnrs.lacito.matchbox.gui.problems;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The problems (diagnostics) of every text of the project, kept up to date text by text: each
 * corpus change replaces the rows of that one text. Rows are in project order
 * ({@link ProblemRow#ORDER}). Pure; used on the JavaFX thread.
 */
public final class ProblemIndex {

    private final Map<TextId, List<ProblemRow>> rows = new TreeMap<>();
    private final List<Consumer<TextId>> listeners = new CopyOnWriteArrayList<>();
    private List<ProblemRow> all;

    /** Replaces the rows of a text; an absent text (removed from the project) has none. */
    public void update(TextId id, Optional<Text> text) {
        update(id, text, t -> t.diagnostics());
    }

    /** Replaces the rows of a text with the diagnostics {@code diagnostics} gives for it. */
    public void update(TextId id, Optional<Text> text,
                       java.util.function.Function<Text, List<fr.cnrs.lacito.matchbox.corpus.Diagnostic>> diagnostics) {
        List<ProblemRow> newRows = text.map(t -> ProblemRow.of(t, diagnostics.apply(t))).orElse(List.of());
        List<ProblemRow> old = newRows.isEmpty() ? rows.remove(id) : rows.put(id, newRows);
        if ((old == null || old.isEmpty()) && newRows.isEmpty()) return;
        all = null;
        listeners.forEach(l -> l.accept(id));
    }

    /** Forgets every text (project closed or replaced); listeners get a {@code null} id. */
    public void clear() {
        if (rows.isEmpty()) return;
        rows.clear();
        all = null;
        listeners.forEach(l -> l.accept(null));
    }

    /** All the rows, in project order; unmodifiable. */
    public List<ProblemRow> rows() {
        if (all == null) {
            List<ProblemRow> list = new ArrayList<>();
            rows.values().forEach(list::addAll);
            all = List.copyOf(list);
        }
        return all;
    }

    /** The rows of one text, in text order. */
    public List<ProblemRow> rows(TextId id) {
        return rows.getOrDefault(id, List.of());
    }

    /** Called with the id of the text whose rows changed ({@code null}: all of them). */
    public void addListener(Consumer<TextId> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<TextId> listener) {
        listeners.remove(listener);
    }

    /**
     * The next (or previous) problem accepted by {@code filter} after (before) the line
     * {@code line} of {@code file}, in project order, wrapping at the end (start); empty if none.
     * A problem on {@code line} itself is skipped: the caller is already there.
     *
     * @param file the current file; null for the start of the project
     */
    public Optional<ProblemRow> next(TextId file, int line, boolean forward, Predicate<ProblemRow> filter) {
        List<ProblemRow> list = rows().stream().filter(filter).toList();
        if (list.isEmpty()) return Optional.empty();
        if (file == null) return Optional.of(forward ? list.get(0) : list.get(list.size() - 1));
        if (forward) {
            for (ProblemRow r : list) {
                int c = r.file().compareTo(file);
                if (c > 0 || (c == 0 && r.line() > line)) return Optional.of(r);
            }
            return Optional.of(list.get(0));
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            ProblemRow r = list.get(i);
            int c = r.file().compareTo(file);
            if (c < 0 || (c == 0 && r.line() < line)) return Optional.of(r);
        }
        return Optional.of(list.get(list.size() - 1));
    }
}
