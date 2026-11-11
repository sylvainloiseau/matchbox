package fr.cnrs.lacito.matchbox.gui.problems;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.Comparator;
import java.util.Optional;

/**
 * One row of a problem table: a diagnostic of a text and what is shown with it. Pure.
 *
 * @param file        the text of the problem
 * @param diagnostic  the problem
 * @param object      the innermost object of the diagnostic's grammar at its start, if any
 * @param version     version of the document the text was parsed from ({@link Text#version()})
 */
public record ProblemRow(TextId file, Diagnostic diagnostic, Optional<CorpusObject> object, long version) {

    /** Project order: file ({@code FileOrder.PATHS} through {@link TextId#compareTo}), line, column. */
    public static final Comparator<ProblemRow> ORDER = Comparator.comparing(ProblemRow::file)
        .thenComparingInt(ProblemRow::line)
        .thenComparingInt(r -> r.diagnostic().column());

    /** The rows of the diagnostics of a text, in text order. */
    public static java.util.List<ProblemRow> of(Text text) {
        return of(text, text.diagnostics());
    }

    /** The rows of the given diagnostics of a text (its parse diagnostics and the findings of the checks). */
    public static java.util.List<ProblemRow> of(Text text, java.util.List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .map(d -> new ProblemRow(text.id(), d, objectOf(text, d), text.version()))
            .sorted(ORDER)
            .toList();
    }

    /** The innermost object of the diagnostic's grammar containing its first character. */
    static Optional<CorpusObject> objectOf(Text text, Diagnostic d) {
        if (d.startIndex() < 0) return Optional.empty();
        int offset = d.startIndex();
        return text.flatten()
            .filter(o -> d.grammarId() != null && text.grammarIdOf(o).map(d.grammarId()::equals).orElse(false))
            .filter(o -> o.getTextCoordinate().contains(offset))
            .min(Comparator.comparingInt(o -> o.getTextCoordinate().length()));
    }

    public int line() {
        return diagnostic.line();
    }

    /** The file name shown in the File column. */
    public String fileName() {
        var name = file.path().getFileName();
        return name == null ? file.path().toString() : name.toString();
    }

    /** The label of the object column: "—" without object. */
    public String objectLabel() {
        return object.map(o -> {
            String label = o.getLabel();
            return label == null || label.isBlank() ? kindName(o.getKind()) : label;
        }).orElse("—");
    }

    /** {@code "sentence"} for {@code "conll-grammar:sentence"}. */
    static String kindName(String kind) {
        if (kind == null) return "";
        int colon = kind.indexOf(':');
        return colon < 0 ? kind : kind.substring(colon + 1);
    }

    public boolean hasFixes() {
        return !diagnostic.fixes().isEmpty();
    }
}
