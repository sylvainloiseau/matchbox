package fr.cnrs.lacito.matchbox.grammarapi;

import java.util.Objects;

/**
 * A canonical snippet of the notation recognised by a grammar, used to show the notation to the
 * user or to an AI assistant.
 *
 * @param caption short description of what the snippet shows
 * @param text    the snippet; it must parse without errors with the grammar alone
 */
public record GrammarExample(String caption, String text) {

    public GrammarExample {
        Objects.requireNonNull(caption, "caption");
        Objects.requireNonNull(text, "text");
    }
}
