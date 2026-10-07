package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;
import java.util.Objects;

/**
 * An action a plugin offers on a parsed text (see {@link TextActionProvider}), shown by the
 * application in the editor margin, never written in the text.
 *
 * @param label        short text of the button
 * @param description  tooltip
 * @param offset       where the action is shown (its line), in the parsed text
 * @param edits        edits relative to the parsed text ({@code Text.content()}), applied as one
 *                     undoable change of the document
 * @param selectOffset offset of the object to select once the text is parsed again; -1 for none
 */
public record TextAction(String label, String description, int offset, List<TextEdit> edits, int selectOffset) {

    public TextAction {
        Objects.requireNonNull(label, "label");
        description = description == null ? "" : description;
        edits = List.copyOf(edits);
    }
}
