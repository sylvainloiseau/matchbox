package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearIssue;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import java.util.ArrayList;
import java.util.List;

/**
 * The problems of the way the IGTs of a text are written, once read in their context (alignment of
 * the words and morphemes, order of the lines, setting, form= / gloss=): see
 * {@code corpus.igt.InterlinearReading#issues()}.
 */
public final class IgtStructureCheck implements CorpusCheck {

    public static final String ID = "IGT structure";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Diagnostic> check(Text text, CheckInputs inputs) {
        List<Diagnostic> result = new ArrayList<>();
        for (InterlinearReading r : InterlinearReadings.of(text).all()) {
            for (InterlinearIssue issue : r.issues()) {
                TextCoordinate span = issue.span() == null ? r.igt().getTextCoordinate() : issue.span();
                result.add(CorpusCheck.warning(text, ID, span.startIndex(), Math.max(span.startIndex(), span.stopIndex()),
                    span.startLine(), issue.message(), issue.kind().code()));
            }
        }
        return result;
    }
}
