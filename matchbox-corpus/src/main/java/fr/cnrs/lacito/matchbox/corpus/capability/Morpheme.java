package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.Objects;

/**
 * A morpheme of a {@link Word}.
 *
 * @param form     its text, without the boundary symbol (and without the angle brackets of an infix)
 * @param boundary the boundary before it ({@link Boundary#NONE} for the first morpheme of a word)
 * @param span     its characters, boundary symbol excluded (empty span for an empty form)
 */
public record Morpheme(String form, Boundary boundary, TextCoordinate span) {

    /** Boundary symbols of the Leipzig Glossing Rules. */
    public enum Boundary {
        /** First morpheme of a word. */
        NONE(""),
        /** {@code -}: affix (rule 2). */
        AFFIX("-"),
        /** {@code =}: clitic (rule 2). */
        CLITIC("="),
        /** {@code ~}: reduplication (rule 10). */
        REDUPLICATION("~"),
        /** {@code <…>}: infix (rule 9). */
        INFIX("<>");

        private final String symbol;

        Boundary(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    public Morpheme {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(span, "span");
    }

    /** The morpheme as written, with its boundary symbol ({@code =kʁwa}, {@code <um>}). */
    public String written() {
        return switch (boundary) {
            case NONE -> form;
            case INFIX -> "<" + form + ">";
            default -> boundary.symbol() + form;
        };
    }
}
