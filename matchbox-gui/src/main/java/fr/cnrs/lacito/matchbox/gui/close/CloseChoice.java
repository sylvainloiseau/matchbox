package fr.cnrs.lacito.matchbox.gui.close;

import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.util.List;

/**
 * The answer of the close dialog: what to save before closing (the rest is discarded).
 *
 * @param saveDefinition save the project definition
 * @param toSave         documents to save
 */
public record CloseChoice(boolean saveDefinition, List<TextDocument> toSave) {

    public CloseChoice {
        toSave = List.copyOf(toSave);
    }
}
