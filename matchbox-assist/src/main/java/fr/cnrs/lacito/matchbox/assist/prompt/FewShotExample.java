package fr.cnrs.lacito.matchbox.assist.prompt;

import java.util.Objects;

/**
 * A well-formed passage of the user's own files, shown to the model as an example of their
 * conventions.
 *
 * @param kind kind of passage (e.g. "interlinear sentence", "lexical entry")
 * @param text the passage
 */
public record FewShotExample(String kind, String text) {

    public FewShotExample {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }
}
