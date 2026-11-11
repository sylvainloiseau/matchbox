package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import java.util.List;

/**
 * A check of the application on the objects of a text, by capability (never by plugin): its
 * findings are {@linkplain Diagnostic.Category#SEMANTIC semantic} diagnostics whose grammar id is the
 * check's {@link #id()}, listed with the parse diagnostics (margin, Semantic issues tab, views).
 * Pure and thread-safe.
 */
public interface CorpusCheck {

    /** The id given to its diagnostics, also shown to the user ("IGT structure"). */
    String id();

    List<Diagnostic> check(Text text, CheckInputs inputs);

    /** A semantic warning of this check on a span of the text. */
    static Diagnostic warning(Text text, String checkId, int start, int stop, int line, String message, String code) {
        String content = text.content();
        int i = Math.min(start, content.length()) - 1;
        while (i >= 0 && content.charAt(i) != '\n' && content.charAt(i) != '\r') i--;
        return Diagnostic.semantic(text.id(), checkId, line, start - (i + 1), message, start, stop,
            Diagnostic.Severity.WARNING).withCode(code);
    }
}
