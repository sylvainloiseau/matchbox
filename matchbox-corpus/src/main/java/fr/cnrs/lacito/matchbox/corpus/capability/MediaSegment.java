package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Optional;

/**
 * A media file (audio or video), or a segment of it, referred to by a text.
 *
 * <p>The file may be given by the object ({@link #getMediaReference()}) or, when absent, by the
 * {@code media} metadata of the sections or header enclosing it; the application resolves it after
 * parsing. An object has a file reference or a range, or both.
 */
public interface MediaSegment extends CorpusObject {

    /** Field names (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String MEDIA = "media";
    String RANGE = "range";
    String START = "start";
    String END = "end";
    String CAPTION = "caption";

    /** The media file as written; empty when it is given by the "media" metadata of the enclosing sections or header. */
    Optional<String> getMediaReference();

    /** The part of the media; empty for the whole media. */
    Optional<TimeRange> getRange();

    /** Free text describing the segment (e.g. "greeting"); empty if none. Not {@link #getLabel()}, which every object has. */
    default String getCaption() {
        return "";
    }

    default boolean isWholeMedia() {
        return getRange().isEmpty();
    }
}
