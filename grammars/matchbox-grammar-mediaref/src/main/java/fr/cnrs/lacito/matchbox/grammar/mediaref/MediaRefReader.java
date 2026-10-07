package fr.cnrs.lacito.matchbox.grammar.mediaref;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefParser.MarkerContext;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;
import fr.cnrs.lacito.matchbox.grammarapi.TimeCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.Token;

/** Builds the markers from the parse tree (no ANTLR object kept). */
final class MediaRefReader extends MediaRefParserBaseVisitor<Void> {

    /** The two times of a RANGE token and what separates them. */
    static final Pattern RANGE_PARTS = Pattern.compile("(\\S+?)([ \\t]*[-\u2013][ \\t]*)(\\S+)");

    private final TextId text;
    private final String content;
    private final List<CorpusObject> markers = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    MediaRefReader(TextId text, String content) {
        this.text = text;
        this.content = content;
    }

    List<CorpusObject> markers() {
        return markers;
    }

    List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    @Override
    public Void visitMarker(MarkerContext ctx) {
        if (ctx.exception != null || ctx.MEDIA_START() == null || ctx.MARKER_END() == null) return null;
        Token start = ctx.MEDIA_START().getSymbol();
        int line = start.getLine();
        List<Field> fields = new ArrayList<>();

        // file
        String reference = null;
        int afterFile;
        Token file = ctx.FILE() != null ? ctx.FILE().getSymbol() : ctx.QUOTED_FILE() != null ? ctx.QUOTED_FILE().getSymbol() : null;
        if (file != null) {
            boolean quoted = ctx.QUOTED_FILE() != null;
            reference = quoted ? unquote(file.getText()) : file.getText();
            TextCoordinate span = quoted
                ? span(file.getStartIndex() + 1, file.getStopIndex() - 1, line)
                : Spans.of(text, file);
            fields.add(new Field(MediaSegment.MEDIA, reference, span));
            afterFile = file.getStopIndex() + 1;
        } else {
            int at = start.getStopIndex() + 1;
            fields.add(new Field(MediaSegment.MEDIA, "", span(at, at - 1, line)));
            afterFile = at;
        }

        // range
        TimeRange range = null;
        TimeCodes.Format startFormat = TimeCodes.Format.MILLISECONDS;
        TimeCodes.Format endFormat = TimeCodes.Format.MILLISECONDS;
        if (ctx.RANGE() != null) {
            Token r = ctx.RANGE().getSymbol();
            Matcher m = RANGE_PARTS.matcher(r.getText());
            OptionalLong s = m.matches() ? TimeCodes.parse(m.group(1)) : OptionalLong.empty();
            OptionalLong e = m.matches() ? TimeCodes.parse(m.group(3)) : OptionalLong.empty();
            if (s.isEmpty() || e.isEmpty()) {
                report(r, "Invalid time in " + r.getText() + " (seconds and minutes must be below 60)");
                return null;
            }
            if (e.getAsLong() <= s.getAsLong()) {
                report(r, "The end of the range must be after its start: " + r.getText());
                return null;
            }
            range = new TimeRange(s.getAsLong(), e.getAsLong());
            startFormat = TimeCodes.formatOf(m.group(1));
            endFormat = TimeCodes.formatOf(m.group(3));
            int from = r.getStartIndex();
            fields.add(new Field(MediaSegment.RANGE, r.getText(), Spans.of(text, r)));
            fields.add(new Field(MediaSegment.START, m.group(1), span(from, from + m.group(1).length() - 1, line)));
            int endAt = from + m.group(1).length() + m.group(2).length();
            fields.add(new Field(MediaSegment.END, m.group(3), span(endAt, endAt + m.group(3).length() - 1, line)));
        } else {
            fields.add(new Field(MediaSegment.RANGE, "", span(afterFile, afterFile - 1, line)));
        }

        // caption
        String caption = "";
        if (ctx.CAPTION() != null) {
            Token c = ctx.CAPTION().getSymbol();
            caption = c.getText().stripTrailing();
            fields.add(new Field(MediaSegment.CAPTION, caption,
                span(c.getStartIndex(), c.getStartIndex() + caption.length() - 1, line)));
        } else {
            int end = lineEnd(start.getStartIndex());
            fields.add(new Field(MediaSegment.CAPTION, "", span(end, end - 1, line)));
        }

        Token stop = ctx.MARKER_END().getSymbol();
        int markerEnd = Math.max(stop.getStopIndex(), lineEnd(start.getStartIndex()) - 1);
        markers.add(new MediaMarker(MediaRefGrammarPlugin.KIND_MARKER, span(start.getStartIndex(), markerEnd, line),
            reference, range, caption, startFormat, endFormat, fields));
        return null;
    }

    private int lineEnd(int offset) {
        int i = offset;
        while (i < content.length() && content.charAt(i) != '\n' && content.charAt(i) != '\r') i++;
        return i;
    }

    private void report(Token t, String message) {
        diagnostics.add(new Diagnostic(text, MediaRefGrammarPlugin.ID, t.getLine(), t.getCharPositionInLine(), message,
            t.getStartIndex(), t.getStopIndex(), List.of()));
    }

    private TextCoordinate span(int start, int stop, int line) {
        return new TextCoordinate(text, start, stop, line, line);
    }

    static String unquote(String quoted) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < quoted.length() - 1; i++) {
            char c = quoted.charAt(i);
            if (c == '\\' && i + 1 < quoted.length() - 1) {
                b.append(quoted.charAt(++i));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
