package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.EditRebase;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where the user was in each file: caret, selection anchor, first visible line; for the time the
 * project is open. Each place keeps the version of the document it refers to, and is moved through
 * the changes made since (by a view, undo, the assistant…) when the file is shown again.
 */
public final class CaretMemory {

    /**
     * A place in a file.
     *
     * @param firstVisibleOffset offset of the start of the first visible line
     * @param version            version of the document the offsets refer to
     */
    public record Place(int caret, int anchor, int firstVisibleLine, int firstVisibleOffset, long version) {
        public Place(int caret, int anchor, int firstVisibleLine) {
            this(caret, anchor, firstVisibleLine, -1, Text.UNKNOWN_VERSION);
        }
    }

    private final Map<TextId, Place> places = new HashMap<>();

    public void remember(TextId text, Place place) {
        places.put(text, place);
    }

    /** The place of a file, clamped to its current length. */
    public Optional<Place> recall(TextId text, int length) {
        return Optional.ofNullable(places.get(text)).map(p -> clamped(p, length));
    }

    /**
     * The place of a document, moved to its current text if the changes since it was remembered
     * are known, then clamped to its length.
     */
    public Optional<Place> recall(TextDocument document) {
        Place p = places.get(document.getId());
        if (p == null) return Optional.empty();
        String text = document.getCurrentText();
        if (p.version() != Text.UNKNOWN_VERSION && p.version() != document.getVersion()) {
            var caret = document.moveOffset(p.caret(), EditRebase.Bias.BEFORE, p.version(), document.getVersion());
            var anchor = document.moveOffset(p.anchor(), EditRebase.Bias.BEFORE, p.version(), document.getVersion());
            if (caret.isPresent() && anchor.isPresent()) {
                int line = p.firstVisibleLine();
                if (p.firstVisibleOffset() >= 0) {
                    int first = document.moveOffset(p.firstVisibleOffset(), EditRebase.Bias.BEFORE, p.version(),
                        document.getVersion()).orElse(p.firstVisibleOffset());
                    line = lineIndex(text, Math.min(first, text.length()));
                }
                p = new Place(caret.getAsInt(), anchor.getAsInt(), line, -1, document.getVersion());
            }
        }
        return Optional.of(clamped(p, text.length()));
    }

    private static Place clamped(Place p, int length) {
        return new Place(Math.min(p.caret(), length), Math.min(p.anchor(), length), Math.max(0, p.firstVisibleLine()),
            p.firstVisibleOffset(), p.version());
    }

    /** 0-based line of an offset. */
    private static int lineIndex(String text, int offset) {
        int line = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    public void clear() {
        places.clear();
    }
}
