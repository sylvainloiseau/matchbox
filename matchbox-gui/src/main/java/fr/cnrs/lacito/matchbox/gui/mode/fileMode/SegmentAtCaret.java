package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The media segment to play for a caret position: the segment on the caret's line, otherwise the
 * nearest segment above the caret within the same innermost section. No JavaFX.
 */
public final class SegmentAtCaret {

    private SegmentAtCaret() {}

    public static Optional<MediaSegment> find(Text text, int offset) {
        String content = text.content();
        int caret = Math.max(0, Math.min(offset, content.length()));
        int lineStart = caret;
        while (lineStart > 0 && content.charAt(lineStart - 1) != '\n' && content.charAt(lineStart - 1) != '\r') lineStart--;
        int lineEnd = caret;
        while (lineEnd < content.length() && content.charAt(lineEnd) != '\n' && content.charAt(lineEnd) != '\r') lineEnd++;
        List<MediaSegment> segments = text.objectsOf(MediaSegment.class).stream()
            .sorted(Comparator.comparingInt(s -> s.getTextCoordinate().startIndex()))
            .toList();
        int from = lineStart;
        int to = lineEnd;
        Optional<MediaSegment> onLine = segments.stream()
            .filter(s -> s.getTextCoordinate().startIndex() >= from && s.getTextCoordinate().startIndex() <= to)
            .findFirst();
        if (onLine.isPresent()) return onLine;
        Optional<Section> section = innermostSectionAt(text, caret);
        MediaSegment found = null;
        for (MediaSegment s : segments) {
            if (s.getTextCoordinate().startIndex() >= caret) break;
            if (innermostSectionAt(text, s.getTextCoordinate().startIndex()).equals(section)) {
                found = s;
            }
        }
        return Optional.ofNullable(found);
    }

    /** The innermost section (of any grammar) containing an offset. */
    private static Optional<Section> innermostSectionAt(Text text, int offset) {
        return text.sections().stream()
            .filter(s -> s.getTextCoordinate().contains(offset))
            .min(Comparator.comparingInt(s -> s.getTextCoordinate().length()));
    }
}
