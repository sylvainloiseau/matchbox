package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import java.util.Comparator;
import java.util.Optional;

/**
 * The lines of the section containing the caret, shown as a band in the margin of the editor:
 * sections are painted on their heading only, the band shows how far they reach. Pure.
 */
public final class SectionBand {

    /** Lines of a section, counted from 0, both included. */
    public record Lines(int first, int last) {
        public boolean contains(int line) {
            return line >= first && line <= last;
        }
    }

    private SectionBand() {}

    /**
     * The lines of the innermost section (of any grammar) containing the caret; a caret just after
     * the last character of a section (end of file) is in it.
     */
    public static Optional<Lines> linesOf(Text text, int caretOffset) {
        return innermost(text, caretOffset).or(() -> innermost(text, caretOffset - 1))
            .map(s -> {
                TextCoordinate span = s.getTextCoordinate();
                return new Lines(span.startLine() - 1, span.endLine() - 1);
            });
    }

    private static Optional<Section> innermost(Text text, int offset) {
        if (offset < 0) return Optional.empty();
        return text.sections().stream()
            .filter(s -> s.getTextCoordinate().contains(offset))
            .min(Comparator.comparingInt(s -> s.getTextCoordinate().length()));
    }
}
