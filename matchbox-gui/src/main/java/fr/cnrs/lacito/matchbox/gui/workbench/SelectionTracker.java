package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The object selected in a mode, kept valid across re-parses: when the text of the selected object
 * is parsed again, the selection moves to the object that replaces it ({@link ObjectSuccessor},
 * helped by the {@link EditHints}); it is cleared when the text leaves the corpus. Each mode has
 * its own tracker: selections are not shared between modes.
 */
public final class SelectionTracker {

    private final ObjectProperty<CorpusObject> selected = new SimpleObjectProperty<>();
    private final EditHints hints;

    public SelectionTracker(EditHints hints) {
        this.hints = hints;
    }

    public ObjectProperty<CorpusObject> selectedProperty() {
        return selected;
    }

    public CorpusObject get() {
        return selected.get();
    }

    public void set(CorpusObject object) {
        selected.set(object);
    }

    /**
     * A text of {@code corpus} changed: replaces the selected object if it belonged to the
     * previous parse of that text.
     *
     * @param takePending also select the object a text action asked to select in this text
     */
    public void textChanged(TextId id, CorpusView corpus, boolean takePending) {
        CorpusObject previous = selected.get();
        Optional<Text> text = corpus.text(id);
        if (previous != null && previous.getTextCoordinate().text().equals(id) && corpus.textOf(previous).isEmpty()) {
            selected.set(text.flatMap(t -> ObjectSuccessor.find(t, previous, hints.lastEditOffset(id))).orElse(null));
        }
        if (takePending) {
            hints.takePendingSelection(id).ifPresent(offset ->
                text.flatMap(t -> t.objectAt(offset)).ifPresent(selected::set));
        }
    }
}
