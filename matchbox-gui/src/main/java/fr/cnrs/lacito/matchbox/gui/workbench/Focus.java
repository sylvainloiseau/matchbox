package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the user is in a mode: a text, a range in it (a caret when empty) and possibly the object
 * selected there. The window reads the focus of the active mode to target its commands (Save
 * current file, Close file, Play…); navigation (Back/Forward, show in editor) asks a mode to
 * {@linkplain fr.cnrs.lacito.matchbox.gui.mode.Mode#reveal reveal} a focus.
 *
 * @param text   the text
 * @param start  first offset of the range (the caret)
 * @param end    end of the range, exclusive ({@code end == start} for a caret)
 * @param object the selected object, if any
 */
public record Focus(TextId text, int start, int end, Optional<CorpusObject> object) {

    public Focus {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(object, "object");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range " + start + "-" + end);
        }
    }

    /** An object: its text and span. */
    public static Focus of(CorpusObject object) {
        TextCoordinate span = object.getTextCoordinate();
        return new Focus(span.text(), span.startIndex(), Math.max(span.startIndex(), span.stopIndex() + 1),
            Optional.of(object));
    }

    /** A span, without object. */
    public static Focus of(TextCoordinate span) {
        return new Focus(span.text(), span.startIndex(), Math.max(span.startIndex(), span.stopIndex() + 1),
            Optional.empty());
    }

    /** A caret position. */
    public static Focus at(TextId text, int offset) {
        return new Focus(text, offset, offset, Optional.empty());
    }

    public Focus withObject(Optional<CorpusObject> o) {
        return new Focus(text, start, end, o);
    }
}
