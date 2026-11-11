package fr.cnrs.lacito.matchbox.gui.problems;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Next / previous problem in one text (F8, Shift+F8): the first diagnostic on a line after
 * (before) the caret's line, wrapping. Problems across the project are found by
 * {@link ProblemIndex#next}. Pure.
 */
public final class ProblemNavigator {

    private ProblemNavigator() {}

    /**
     * @param diagnostics the diagnostics of the text (any order)
     * @param caretLine   the caret's line, 1-based
     */
    public static Optional<Diagnostic> next(List<Diagnostic> diagnostics, int caretLine, boolean forward) {
        List<Diagnostic> sorted = diagnostics.stream()
            .filter(d -> d.line() > 0)
            .sorted(Comparator.comparingInt(Diagnostic::line).thenComparingInt(Diagnostic::column))
            .toList();
        if (sorted.isEmpty()) return Optional.empty();
        if (forward) {
            return sorted.stream().filter(d -> d.line() > caretLine).findFirst().or(() -> Optional.of(sorted.get(0)));
        }
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (sorted.get(i).line() < caretLine) {
                // the first diagnostic of that line
                int line = sorted.get(i).line();
                return sorted.stream().filter(d -> d.line() == line).findFirst();
            }
        }
        int last = sorted.get(sorted.size() - 1).line();
        return sorted.stream().filter(d -> d.line() == last).findFirst();
    }
}
