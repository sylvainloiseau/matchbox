package fr.cnrs.lacito.matchbox.grammarfx;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayers;
import fr.cnrs.lacito.matchbox.grammarfx.media.SharedMediaPlayers;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Services the application offers to object views.
 *
 * <p>A view never changes a corpus object (objects are immutable): to change what it shows, it
 * asks for text edits ({@link #setField}, {@link #applyEdits}). The application applies them to
 * the object's document, as one undoable change, whether or not the document is shown in the
 * editor, and parses it again; the view is then refreshed with the new object.
 */
public interface ViewContext {

    /** Opens the file of the span if needed, and selects the span in the editor. */
    void selectInEditor(TextCoordinate span);

    /** Selects the object in the application (table, detail pane and editor). */
    void select(CorpusObject object);

    /** Highlight colour used in the editor for this object, as a CSS colour (e.g. {@code "#ffeeaa"}). */
    String highlightColor(CorpusObject object);

    /** The texts of the project: the text of an object, its sections and metadata, other objects. */
    CorpusView corpus();

    /**
     * Whether edits of this object can be applied now: its text is in the project, the file can
     * be written and the object has editable fields. Views use it to disable editing controls.
     */
    default boolean canEdit(CorpusObject object) {
        return false;
    }

    /**
     * Sets a field of the object (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}), written in
     * the notation of the object's grammar.
     */
    default EditOutcome setField(CorpusObject object, String fieldName, String value) {
        return new EditOutcome.NotEditable("Editing is not available");
    }

    /**
     * Applies edits computed against the content the object was parsed from
     * ({@code corpus().textOf(object).content()}), as one undoable change of the object's
     * document, then parses it again. For views building their own edits.
     */
    default EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits) {
        return new EditOutcome.NotEditable("Editing is not available");
    }

    /**
     * Adds a field to the object (e.g. a metadata entry), written in the notation of the object's
     * grammar ({@code ObjectEditor.addField}).
     */
    default EditOutcome addField(CorpusObject object, String fieldName, String value) {
        return new EditOutcome.NotEditable("Editing is not available");
    }

    /** Removes a field of the object (e.g. a metadata entry). */
    default EditOutcome removeField(CorpusObject object, String fieldName) {
        return new EditOutcome.NotEditable("Editing is not available");
    }

    /**
     * Applies the edits of a text action (see {@link TextActionProvider}) to the displayed
     * document, as one undoable change, and selects the object at {@link TextAction#selectOffset()}
     * once the text is parsed again.
     */
    default EditOutcome runTextAction(TextAction action) {
        return new EditOutcome.NotEditable("Editing is not available");
    }

    /** Directory of the saved project file, if any (used to resolve relative paths). */
    default Optional<Path> projectDirectory() {
        return Optional.empty();
    }

    /** Opens a URI (e.g. an http link) in the system browser or the default application. */
    default void showDocument(String uri) {
    }

    /**
     * Players of media files shared by all views (each file opened once, one plays at a time).
     * The default is an instance without managed lifetime, for views used outside the application.
     */
    default MediaPlayers mediaPlayers() {
        return SharedMediaPlayers.fallback();
    }

    /** Types of metadata values by key and vocabularies of list values. */
    default MetadataSchema metadataSchema() {
        return MetadataSchema.DEFAULT;
    }

    /** Undoes the last change of the object's document. */
    default boolean undo(CorpusObject object) {
        return false;
    }

    /** Applies again the last undone change of the object's document. */
    default boolean redo(CorpusObject object) {
        return false;
    }
}
