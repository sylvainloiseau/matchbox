package fr.cnrs.lacito.matchbox.grammar.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearResolver;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.corpus.igt.Segmenter;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearTimeline;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Builds an IGT, as written, from a block of the parse tree: its metadata, its lines with their
 * content kind, its fields; reports the syntax errors (metadata, block not closed) and the keys given
 * twice. The roles of the lines are not decided here (they depend on the context, see
 * {@code corpus.igt}); only the editor kind of an orthographic line, from the block's own
 * {@code setting} or the word-count rule.
 */
final class IgtBuilder {

    /** The span of a metadata block, drawn in its own style. */
    static final class MetadataBlock extends AbstractCorpusObject {
        MetadataBlock(TextCoordinate span) {
            super(IgtGrammarPlugin.KIND_METADATA, span);
        }

        @Override
        public String getLabel() {
            return "metadata";
        }
    }

    private final TextId text;
    private final String content;
    private final List<Diagnostic> diagnostics;

    IgtBuilder(TextId text, String content, List<Diagnostic> diagnostics) {
        this.text = text;
        this.content = content;
        this.diagnostics = diagnostics;
    }

    /** A line of a block: its text without line ending, its offset, its line number. */
    private record Raw(String text, int start, int line) {}

    static String withoutEol(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
    }

    Optional<InterlinearGlossedText> build(IgtParser.BlockContext block) {
        Token start = block.START().getSymbol();
        String anchor = withoutEol(start.getText());
        if (block.END() == null) {
            diagnostics.add(new Diagnostic(text, IgtGrammarPlugin.ID, start.getLine(), 0,
                "IGT not closed: add a ::: line after its last line", start.getStartIndex(),
                start.getStartIndex() + 2, List.of()).withCode(IgtGrammarPlugin.CODE_UNCLOSED));
            return Optional.empty();
        }
        Token end = block.END().getSymbol();
        MetadataSyntax.Block meta = MetadataSyntax.parse(anchor, start.getStartIndex());
        for (MetadataSyntax.Problem p : meta.problems()) {
            diagnostics.add(new Diagnostic(text, IgtGrammarPlugin.ID, start.getLine(), p.offset() - start.getStartIndex(),
                p.message(), p.offset(), Math.min(p.offset(), start.getStartIndex() + anchor.length() - 1), List.of())
                .withCode(IgtGrammarPlugin.CODE_METADATA));
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        List<Field> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        MetadataSyntax.Entry previous = null;
        for (MetadataSyntax.Entry e : meta.entries()) {
            if (!seen.add(e.key())) {
                int from = previous == null ? e.start() : previous.end();
                diagnostics.add(Diagnostic.semantic(text, IgtGrammarPlugin.ID, start.getLine(), e.start() - start.getStartIndex(),
                        "Duplicate key '" + e.key() + "': the first value is used", e.start(), e.end() - 1,
                        Diagnostic.Severity.WARNING)
                    .withCode(IgtGrammarPlugin.CODE_DUPLICATE_KEY)
                    .withFixes(List.of(new Diagnostic.Fix("Remove this '" + e.key() + "' entry",
                        List.of(TextEdit.replace(content, from, e.end(), ""))))));
            } else {
                metadata.put(e.key(), e.value());
                fields.add(new Field(InterlinearGlossedText.metadataField(e.key()), e.value(),
                    span(e.valueStart(), e.valueEnd() - 1, start.getLine())));
            }
            previous = e;
        }

        List<Raw> raws = new ArrayList<>();
        for (int i = 1; i < block.getChildCount(); i++) {
            if (block.getChild(i) instanceof TerminalNode node && node.getSymbol().getType() == IgtLexer.BODY) {
                Token t = node.getSymbol();
                String full = withoutEol(t.getText());
                String line = full.substring(0, Segmenter.contentEnd(full)).stripTrailing();
                if (!line.isBlank()) raws.add(new Raw(line, t.getStartIndex(), t.getLine()));
            }
        }

        int stop = end.getStartIndex() + withoutEol(end.getText()).length() - 1;
        TextCoordinate igtSpan = new TextCoordinate(text, start.getStartIndex(), stop, start.getLine(), end.getLine());
        List<CorpusObject> other = meta.present() && meta.close() > meta.open()
            ? List.of(new MetadataBlock(span(meta.open(), meta.close(), start.getLine())))
            : List.of();

        // first build: generic kinds, to read the IGT alone; second build: kinds and ranges from that reading
        SimpleInterlinearGlossedText first = igt(igtSpan, metadata, lines(raws, metadata, Set.of(), Map.of()), other, fields);
        InterlinearReading alone = InterlinearResolver.withoutContext(first);
        Set<Integer> orthographic = new HashSet<>();
        alone.lines(LineRole.ORTHOGRAPHIC).forEach(l -> orthographic.add(l.index()));
        Map<Level, Optional<TimeRange>> ranges = new LinkedHashMap<>();
        for (Level level : Level.values()) {
            ranges.put(level, alone.timing(level).flatMap(IgtBuilder::range));
        }
        List<InterlinearLine> lines = lines(raws, metadata, orthographic, ranges);
        fields.addAll(lineFields(lines));
        // the fields of InterlinearSentence (table columns of the built-in view of sentences)
        SimpleInterlinearGlossedText second = igt(igtSpan, metadata, lines, other, fields);
        InterlinearReading reading = InterlinearResolver.withoutContext(second);
        sentenceField(fields, InterlinearSentence.TRANSCRIPTION, reading.first(LineRole.TRANSCRIPTION, Level.MORPHEME)
            .or(() -> reading.first(LineRole.TRANSCRIPTION, Level.WORD)), lines);
        sentenceField(fields, InterlinearSentence.MORPHEMES, reading.first(LineRole.GLOSS, Level.MORPHEME)
            .or(() -> reading.first(LineRole.GLOSS, Level.WORD)), lines);
        sentenceField(fields, InterlinearSentence.FREE_TRANSLATION, reading.lines(LineRole.TRANSLATION).stream().findFirst(), lines);
        return Optional.of(igt(igtSpan, metadata, lines, other, fields));
    }

    private SimpleInterlinearGlossedText igt(TextCoordinate span, Map<String, String> metadata, List<InterlinearLine> lines,
                                             List<CorpusObject> other, List<Field> fields) {
        return new SimpleInterlinearGlossedText(IgtGrammarPlugin.KIND_IGT, span, metadata, lines, other, fields);
    }

    /** The range of the whole IGT: from the first start to the last known end. */
    private static Optional<TimeRange> range(List<WordTiming> timings) {
        long from = Long.MAX_VALUE;
        long to = -1;
        for (WordTiming t : timings) {
            if (t.range().isPresent()) {
                from = Math.min(from, t.range().get().startMs());
                to = Math.max(to, t.range().get().endMs());
            }
        }
        return to > from ? Optional.of(new TimeRange(from, to)) : Optional.empty();
    }

    private List<InterlinearLine> lines(List<Raw> raws, Map<String, String> metadata, Set<Integer> orthographic,
                                        Map<Level, Optional<TimeRange>> ranges) {
        List<InterlinearLine> lines = new ArrayList<>();
        for (int i = 0; i < raws.size(); i++) {
            Raw r = raws.get(i);
            TextCoordinate span = span(r.start(), r.start() + r.text().length() - 1, r.line());
            InterlinearLine.Content c = Segmenter.contentOf(r.text());
            switch (c) {
                case TIMELINE -> lines.add(new SimpleInterlinearTimeline(IgtGrammarPlugin.KIND_TIMELINE, span, i + 1, r.text(),
                    r.start(), Optional.ofNullable(metadata.get("media")),
                    ranges.getOrDefault(Segmenter.levelOf(r.text()), Optional.empty())));
                case TRANSLATION -> {
                    String inner = r.text().strip();
                    int innerStart = r.start() + r.text().indexOf(inner) + 1;
                    lines.add(new SimpleInterlinearLine(IgtGrammarPlugin.KIND_TRANSLATION, span, c, i + 1,
                        inner.substring(1, inner.length() - 1), innerStart));
                }
                case TEXT -> lines.add(new SimpleInterlinearLine(orthographic.contains(i)
                    ? IgtGrammarPlugin.KIND_ORTHOGRAPHIC : IgtGrammarPlugin.KIND_LINE, span, c, i + 1, r.text(), r.start()));
            }
        }
        return lines;
    }

    /** line/i (the text of the line; between the quotes for a translation) and line/i/w (its words). */
    private List<Field> lineFields(List<InterlinearLine> lines) {
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            InterlinearLine l = lines.get(i);
            fields.add(new Field(InterlinearGlossedText.lineField(i), l.text(), textSpan(l)));
        }
        for (int i = 0; i < lines.size(); i++) {
            List<Word> words = lines.get(i).words();
            for (int w = 0; w < words.size(); w++) {
                fields.add(new Field(InterlinearGlossedText.wordField(i, w), words.get(w).text(), words.get(w).span()));
            }
        }
        return fields;
    }

    private void sentenceField(List<Field> fields, String name, Optional<LineReading> line, List<InterlinearLine> lines) {
        line.ifPresent(l -> fields.add(new Field(name, l.line().text(), textSpan(lines.get(l.index())))));
    }

    /** The span of the text of a line: the whole line, or between the quotes of a translation. */
    TextCoordinate textSpan(InterlinearLine l) {
        TextCoordinate s = l.getTextCoordinate();
        if (l.content() != InterlinearLine.Content.TRANSLATION) return s;
        String raw = content.substring(s.startIndex(), s.stopIndex() + 1);
        int open = s.startIndex() + raw.indexOf(raw.strip()) + 1;
        return span(open, open + l.text().length() - 1, s.startLine());
    }

    private TextCoordinate span(int start, int stop, int line) {
        return new TextCoordinate(text, start, Math.max(start - 1, stop), line, line);
    }
}
