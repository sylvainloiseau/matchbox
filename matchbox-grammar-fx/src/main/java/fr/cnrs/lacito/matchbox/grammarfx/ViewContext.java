package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;

/** Services the application offers to object views. */
public interface ViewContext {

    /** Opens the file of the span if needed, and selects the span in the editor. */
    void selectInEditor(TextCoordinate span);

    /** Selects the object in the application (table, detail pane and editor). */
    void select(CorpusObject object);

    /** Highlight colour used in the editor for this object, as a CSS colour (e.g. {@code "#ffeeaa"}). */
    String highlightColor(CorpusObject object);
}
