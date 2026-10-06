package fr.cnrs.lacito.matchbox.corpus;

import java.util.Objects;

/**
 * A named value of a corpus object and the span of its raw text, delimiters excluded (e.g. the
 * text between the quotes of a translation). An empty value has an empty span
 * ({@link TextCoordinate#isEmpty()}): its start is the offset where the value would be inserted.
 *
 * @param name  field name, e.g. {@link fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence#FREE_TRANSLATION}
 * @param value the value, as the object exposes it
 * @param span  span of the raw text of the value, inside the object's span
 */
public record Field(String name, String value, TextCoordinate span) {

    public Field {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}
