package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import java.util.List;
import java.util.Optional;

/** The occurrences as plain text (to copy) or CSV (to export). */
public final class OccurrenceExport {

    static final List<String> CSV_HEADER = List.of("keyword", "file", "line", "applies to", "target", "context");

    private OccurrenceExport() {
    }

    /** One line per occurrence: {@code file:line  keyword  line of the tag}. */
    public static String text(List<TagOccurrence> occurrences, CorpusView corpus) {
        StringBuilder sb = new StringBuilder();
        for (TagOccurrence o : occurrences) {
            sb.append(o.text().displayName()).append(':').append(line(o)).append('\t')
                .append(o.keyword()).append('\t').append(lineOfTag(o, corpus)).append('\n');
        }
        return sb.toString();
    }

    /** CSV (RFC 4180): a header, then one record per occurrence; the file is the full path. */
    public static String csv(List<TagOccurrence> occurrences, CorpusView corpus) {
        StringBuilder sb = new StringBuilder();
        record(sb, CSV_HEADER);
        for (TagOccurrence o : occurrences) {
            record(sb, List.of(o.keyword(), o.text().path().toString(), Integer.toString(line(o)), o.targetType(),
                o.target().map(t -> t.getLabel() == null ? "" : t.getLabel()).orElse(""), lineOfTag(o, corpus)));
        }
        return sb.toString();
    }

    private static int line(TagOccurrence o) {
        return o.tag().getTextCoordinate().startLine();
    }

    /** The line of the text holding the tag. */
    static String lineOfTag(TagOccurrence o, CorpusView corpus) {
        Optional<Text> text = corpus.text(o.text());
        if (text.isEmpty()) return "";
        int start = o.tag().getTextCoordinate().startIndex();
        return ContextExcerpt.of(text.get().content(), start, start + 1, 0, 0).lines().get(0).text().strip();
    }

    private static void record(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields.get(i)));
        }
        sb.append("\r\n");
    }

    static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
