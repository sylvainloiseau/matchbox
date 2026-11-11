package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearTimeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the IGTs of a text written with ::: blocks (a simplified reader for the tests of this module). */
final class IgtFixture {

    private static final Pattern ENTRY = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private IgtFixture() {}

    /** The IGTs of {@code content}, "\n" line endings. */
    static List<SimpleInterlinearGlossedText> igts(TextId id, String content) {
        List<SimpleInterlinearGlossedText> result = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int offset = 0;
        int[] starts = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            starts[i] = offset;
            offset += lines[i].length() + 1;
        }
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].startsWith(":::")) continue;
            int end = i + 1;
            while (end < lines.length && !lines[end].strip().equals(":::")) end++;
            Map<String, String> metadata = new LinkedHashMap<>();
            Matcher m = ENTRY.matcher(lines[i]);
            while (m.find()) metadata.put(m.group(1), m.group(2));
            List<InterlinearLine> body = new ArrayList<>();
            int number = 0;
            for (int j = i + 1; j < end; j++) {
                String text = lines[j];
                if (text.isBlank()) continue;
                number++;
                TextCoordinate span = new TextCoordinate(id, starts[j], starts[j] + text.length() - 1, j + 1, j + 1);
                InterlinearLine.Content c = Segmenter.contentOf(text);
                if (c == InterlinearLine.Content.TIMELINE) {
                    body.add(new SimpleInterlinearTimeline("igt:timeline", span, number, text, starts[j],
                        Optional.ofNullable(metadata.get("media")), Optional.empty()));
                } else if (c == InterlinearLine.Content.TRANSLATION) {
                    String inner = text.strip();
                    body.add(new SimpleInterlinearLine("igt:translation", span, c, number,
                        inner.substring(1, inner.length() - 1), starts[j] + text.indexOf(inner) + 1));
                } else {
                    body.add(new SimpleInterlinearLine("igt:line", span, c, number, text, starts[j]));
                }
            }
            int stop = end < lines.length ? starts[end] + lines[end].length() - 1 : content.length() - 1;
            result.add(new SimpleInterlinearGlossedText("igt:igt",
                new TextCoordinate(id, starts[i], stop, i + 1, Math.min(end, lines.length - 1) + 1),
                metadata, body, List.of(), List.of()));
            i = end;
        }
        return result;
    }
}
