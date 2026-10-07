package fr.cnrs.lacito.matchbox.grammar.mediaref;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import fr.cnrs.lacito.matchbox.grammarapi.TimeCodes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * An {@code @media} line: a media file and/or a time range, and an optional caption. Remembers how
 * each time was written (milliseconds or clock time), so that edits keep it.
 */
public final class MediaMarker extends AbstractCorpusObject implements MediaSegment, HasFields {

    private final String mediaReference;
    private final TimeRange range;
    private final String caption;
    private final TimeCodes.Format startFormat;
    private final TimeCodes.Format endFormat;
    private final List<Field> fields;

    /**
     * @param mediaReference the file as written (unquoted), or {@code null}
     * @param range          the range, or {@code null} for the whole file
     * @param fields         {@link #MEDIA}, {@link #RANGE}, {@link #CAPTION} and, with a range,
     *                       {@link #START} and {@link #END}
     */
    public MediaMarker(String kind, TextCoordinate coordinate, String mediaReference, TimeRange range, String caption,
                       TimeCodes.Format startFormat, TimeCodes.Format endFormat, List<Field> fields) {
        super(kind, coordinate);
        if (mediaReference == null && range == null) {
            throw new IllegalArgumentException("A media marker needs a file or a range");
        }
        this.mediaReference = mediaReference;
        this.range = range;
        this.caption = caption == null ? "" : caption;
        this.startFormat = startFormat;
        this.endFormat = endFormat;
        this.fields = List.copyOf(fields);
    }

    @Override
    public Optional<String> getMediaReference() {
        return Optional.ofNullable(mediaReference);
    }

    @Override
    public Optional<TimeRange> getRange() {
        return Optional.ofNullable(range);
    }

    @Override
    public String getCaption() {
        return caption;
    }

    /** How the start time is written; milliseconds for a whole-file marker. */
    public TimeCodes.Format getStartFormat() {
        return startFormat;
    }

    public TimeCodes.Format getEndFormat() {
        return endFormat;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }

    /** The caption, else the range or "whole file"; then the file name when it is written. */
    @Override
    public String getLabel() {
        String what = !caption.isEmpty() ? caption
            : range != null ? TimeCodes.display(range.startMs()) + "–" + TimeCodes.display(range.endMs())
            : "whole file";
        if (mediaReference == null) return what;
        Path name = Path.of(mediaReference.replace('\\', '/')).getFileName();
        return what + " — " + (name == null ? mediaReference : name);
    }
}
