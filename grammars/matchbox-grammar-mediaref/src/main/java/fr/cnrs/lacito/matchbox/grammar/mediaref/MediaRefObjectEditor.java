package fr.cnrs.lacito.matchbox.grammar.mediaref;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.TimeCodes;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Writes the fields of a media marker: start and end (in the format of the time they replace),
 * the range (added, replaced or removed), the file (quoted when needed) and the caption. A marker
 * keeps a file or a range.
 */
final class MediaRefObjectEditor implements ObjectEditor {

    /** A value that would be read as a time range at the start of the rest of the line. */
    private static final Pattern RANGE_START = Pattern.compile(
        "(?:\\d+|(?:\\d+:)?\\d+:\\d{2}(?:\\.\\d{1,3})?)[ \\t]*[-\u2013][ \\t]*(?:\\d+|(?:\\d+:)?\\d+:\\d{2}(?:\\.\\d{1,3})?)(?:[ \\t].*)?");

    @Override
    public EditProposal setField(CorpusObject object, String fieldName, String value, String content) {
        if (!(object instanceof MediaMarker marker)) {
            return new EditProposal.Refused(object.getKind() + " is not a media marker");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return new EditProposal.Refused("Line breaks are not allowed here");
        }
        Optional<Field> field = marker.field(fieldName);
        if (field.isEmpty()) {
            return new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        }
        TextCoordinate span = field.get().span();
        if (!span.isValidFor(content.length())) {
            return new EditProposal.Refused("The text changed");
        }
        return switch (fieldName) {
            case MediaSegment.START, MediaSegment.END -> setTime(marker, fieldName, span, value.strip(), content);
            case MediaSegment.RANGE -> setRange(marker, span, value.strip(), content);
            case MediaSegment.MEDIA -> setMedia(marker, span, value.strip(), content);
            case MediaSegment.CAPTION -> setCaption(marker, span, value.strip(), content);
            default -> new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        };
    }

    private static EditProposal setTime(MediaMarker marker, String fieldName, TextCoordinate span, String value, String content) {
        TimeRange range = marker.getRange().orElseThrow();
        OptionalLong ms = TimeCodes.parse(value);
        if (ms.isEmpty()) {
            return new EditProposal.Refused("Not a time: write milliseconds (12000) or a clock time (0:12.5)");
        }
        boolean start = fieldName.equals(MediaSegment.START);
        long s = start ? ms.getAsLong() : range.startMs();
        long e = start ? range.endMs() : ms.getAsLong();
        if (e <= s) {
            return new EditProposal.Refused("The end must be after the start");
        }
        String written = TimeCodes.write(ms.getAsLong(), start ? marker.getStartFormat() : marker.getEndFormat());
        return edits(TextEdit.replace(content, span, written));
    }

    private static EditProposal setRange(MediaMarker marker, TextCoordinate span, String value, String content) {
        if (value.isEmpty()) {
            if (marker.getRange().isEmpty()) return edits();
            if (marker.getMediaReference().isEmpty()) {
                return new EditProposal.Refused("A marker without file needs a range");
            }
            // remove the range and the spaces before it
            int from = span.startIndex();
            while (from > 0 && isSpace(content.charAt(from - 1))) from--;
            return edits(TextEdit.replace(content, from, span.stopIndex() + 1, ""));
        }
        Optional<TimeRange> range = TimeCodes.parseRange(value);
        if (range.isEmpty()) {
            return new EditProposal.Refused("Not a time range: write start-end, e.g. 0:12.000-0:15.500");
        }
        String written = TimeCodes.write(range.get());
        if (span.isEmpty()) {
            return edits(TextEdit.insert(span.startIndex(), " " + written));
        }
        return edits(TextEdit.replace(content, span, written));
    }

    private static EditProposal setMedia(MediaMarker marker, TextCoordinate span, String value, String content) {
        if (value.isEmpty()) {
            if (marker.getMediaReference().isEmpty()) return edits();
            if (marker.getRange().isEmpty()) {
                return new EditProposal.Refused("A marker without range needs a file");
            }
            // remove the file (and its quotes) and the spaces after it
            int from = span.startIndex();
            int to = span.isEmpty() ? from : span.stopIndex() + 1;
            if (from > 0 && content.charAt(from - 1) == '"') { from--; to++; }
            while (to < content.length() && isSpace(content.charAt(to))) to++;
            return edits(TextEdit.replace(content, from, to, ""));
        }
        String written = writeFile(value);
        if (marker.getMediaReference().isEmpty()) {
            return edits(TextEdit.insert(span.startIndex(), written + " "));
        }
        int from = span.startIndex();
        int to = span.isEmpty() ? from : span.stopIndex() + 1;
        if (from > 0 && content.charAt(from - 1) == '"' && to < content.length() && content.charAt(to) == '"') {
            from--;
            to++;
        }
        return edits(TextEdit.replace(content, from, to, written));
    }

    private static EditProposal setCaption(MediaMarker marker, TextCoordinate span, String value, String content) {
        if (value.isEmpty()) {
            if (marker.getCaption().isEmpty()) return edits();
            int from = span.startIndex();
            while (from > 0 && isSpace(content.charAt(from - 1))) from--;
            return edits(TextEdit.replace(content, from, span.stopIndex() + 1, ""));
        }
        if (marker.getRange().isEmpty() && RANGE_START.matcher(value).matches()) {
            return new EditProposal.Refused("Without a range, a caption cannot start with a time range (it would be read as the range)");
        }
        if (span.isEmpty()) {
            return edits(TextEdit.insert(span.startIndex(), " " + value));
        }
        return edits(TextEdit.replace(content, span, value));
    }

    /** A file name, double-quoted when it contains spaces or quotes or looks like a range. */
    static String writeFile(String file) {
        boolean quote = file.chars().anyMatch(c -> c == ' ' || c == '\t' || c == '"')
            || RANGE_START.matcher(file).matches();
        return quote ? "\"" + file.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : file;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t';
    }

    private static EditProposal edits(TextEdit... edits) {
        return new EditProposal.Edits(List.of(edits));
    }
}
