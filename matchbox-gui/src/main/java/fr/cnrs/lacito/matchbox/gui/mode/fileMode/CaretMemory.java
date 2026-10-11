package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Where the user was in each file: caret, selection anchor, first visible line; for the time the project is open. */
public final class CaretMemory {

    /** A place in a file. */
    public record Place(int caret, int anchor, int firstVisibleLine) {}

    private final Map<TextId, Place> places = new HashMap<>();

    public void remember(TextId text, Place place) {
        places.put(text, place);
    }

    /** The place of a file, clamped to its current length. */
    public Optional<Place> recall(TextId text, int length) {
        return Optional.ofNullable(places.get(text)).map(p ->
            new Place(Math.min(p.caret(), length), Math.min(p.anchor(), length), Math.max(0, p.firstVisibleLine())));
    }

    public void clear() {
        places.clear();
    }
}
