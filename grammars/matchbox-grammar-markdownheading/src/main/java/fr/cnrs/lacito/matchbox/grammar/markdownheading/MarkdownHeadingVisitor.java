package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.AtxHeadingContext;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.DocumentContext;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.KeyValueContext;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.MetadataContext;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.SetextHeadingContext;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingParser.UnsupportedContext;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Collects the header and the headings of the (flat) parse tree, then nests the sections by level.
 * Keeps no ANTLR object: texts and offsets are copied out.
 */
final class MarkdownHeadingVisitor extends MarkdownHeadingParserBaseVisitor<Void> {

    /** A heading as read, before nesting. */
    private record Heading(int level, MarkdownSection.Style style, String title, TextCoordinate titleSpan,
                           TextCoordinate headingSpan, Block block) {}

    /** A metadata block. */
    private record Block(Map<String, String> values, List<Field> fields, TextCoordinate span) {}

    private final TextId text;
    private final String content;
    private final LineIndex lines;
    private final List<Heading> headings = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private SimpleTextHeader header;

    MarkdownHeadingVisitor(TextId text, String content) {
        this.text = text;
        this.content = content;
        this.lines = new LineIndex(content);
    }

    List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /** The header (if any) then the top-level sections, in text order. */
    List<CorpusObject> objects() {
        List<CorpusObject> objects = new ArrayList<>();
        if (header != null) objects.add(header);
        objects.addAll(nest());
        return objects;
    }

    // ------------------------------------------------------------------
    // Reading the tree
    // ------------------------------------------------------------------

    @Override
    public Void visitDocument(DocumentContext ctx) {
        for (ParseTree child : ctx.children == null ? List.<ParseTree>of() : ctx.children) {
            if (child instanceof MetadataContext m) {
                Block b = block(m, TextHeader::propertyField);
                if (b != null) {
                    header = new SimpleTextHeader(MarkdownHeadingGrammarPlugin.KIND_HEADER, b.span(), b.values(), b.fields());
                }
            } else {
                child.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitAtxHeading(AtxHeadingContext ctx) {
        if (ctx.exception != null || ctx.ATX_HEADING() == null) return null;
        Token token = ctx.ATX_HEADING().getSymbol();
        String line = stripEol(token.getText());
        int start = token.getStartIndex();
        int i = 0;
        while (i < line.length() && i < 3 && line.charAt(i) == ' ') i++;
        int marks = 0;
        while (i + marks < line.length() && line.charAt(i + marks) == '#') marks++;
        int markEnd = i + marks;
        String rest = line.substring(markEnd);
        int end = rest.length();
        while (end > 0 && isSpace(rest.charAt(end - 1))) end--;
        int closing = end;
        while (closing > 0 && rest.charAt(closing - 1) == '#') closing--;
        if (closing < end && (closing == 0 || isSpace(rest.charAt(closing - 1)))) {
            end = closing;
            while (end > 0 && isSpace(rest.charAt(end - 1))) end--;
        }
        int begin = 0;
        while (begin < end && isSpace(rest.charAt(begin))) begin++;
        String title = begin < end ? rest.substring(begin, end) : "";
        TextCoordinate titleSpan = title.isEmpty()
            ? span(start + markEnd, start + markEnd - 1)
            : span(start + markEnd + begin, start + markEnd + end - 1);
        headings.add(new Heading(marks, MarkdownSection.Style.ATX, title, titleSpan, Spans.of(text, token),
            ctx.metadata() == null ? null : block(ctx.metadata(), Section::metadataField)));
        return null;
    }

    @Override
    public Void visitSetextHeading(SetextHeadingContext ctx) {
        if (ctx.exception != null || ctx.TEXT_LINE() == null || ctx.SETEXT_UNDERLINE() == null) return null;
        Token titleToken = ctx.TEXT_LINE().getSymbol();
        Token underline = ctx.SETEXT_UNDERLINE().getSymbol();
        String line = stripEol(titleToken.getText());
        String title = line.strip();
        int lead = line.length() - line.stripLeading().length();
        int start = titleToken.getStartIndex();
        int level = underline.getText().strip().charAt(0) == '=' ? 1 : 2;
        TextCoordinate headingSpan = new TextCoordinate(text, start, underline.getStopIndex(),
            titleToken.getLine(), underline.getLine());
        headings.add(new Heading(level, MarkdownSection.Style.SETEXT, title,
            span(start + lead, start + lead + title.length() - 1), headingSpan,
            ctx.metadata() == null ? null : block(ctx.metadata(), Section::metadataField)));
        return null;
    }

    /** A metadata block; null if the parser recovered from an error in it. */
    private Block block(MetadataContext ctx, Function<String, String> fieldName) {
        if (ctx.exception != null || ctx.META_CLOSE() == null) return null;
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Field> fields = new LinkedHashMap<>();
        for (var entry : ctx.entry()) {
            if (entry instanceof KeyValueContext kv && kv.META_ENTRY() != null) {
                Token t = kv.META_ENTRY().getSymbol();
                YamlEntry e = YamlEntry.parse(stripEol(t.getText()), t.getStartIndex());
                if (e.problem() != null) {
                    report(t, e.problem());
                    continue;
                }
                if (values.containsKey(e.key())) {
                    report(t, "Duplicate key '" + e.key() + "': the last value is used");
                }
                values.remove(e.key());
                values.put(e.key(), e.value());
                fields.remove(e.key());
                fields.put(e.key(), new Field(fieldName.apply(e.key()), e.value(), span(e.valueStart(), e.valueStop())));
            } else if (entry instanceof UnsupportedContext u && u.META_INVALID() != null) {
                Token t = u.META_INVALID().getSymbol();
                report(t, YamlEntry.unsupported(stripEol(t.getText())));
            }
        }
        return new Block(values, List.copyOf(fields.values()), Spans.of(text, ctx));
    }

    private void report(Token t, String message) {
        int stop = t.getStartIndex() + stripEol(t.getText()).length() - 1;
        diagnostics.add(new Diagnostic(text, MarkdownHeadingGrammarPlugin.ID, t.getLine(), 0, message,
            t.getStartIndex(), Math.max(stop, t.getStartIndex() - 1), List.of()));
    }

    // ------------------------------------------------------------------
    // Nesting
    // ------------------------------------------------------------------

    /** An open section: its heading and its subsections closed so far. */
    private static final class Open {
        final Heading heading;
        final List<MarkdownSection> children = new ArrayList<>();

        Open(Heading heading) {
            this.heading = heading;
        }
    }

    /**
     * A heading closes the open sections of the same or a deeper level (they end before it); at
     * the end of the text, all open sections end at its last character.
     */
    private List<MarkdownSection> nest() {
        List<MarkdownSection> top = new ArrayList<>();
        Deque<Open> stack = new ArrayDeque<>();
        for (Heading h : headings) {
            int start = h.headingSpan().startIndex();
            while (!stack.isEmpty() && stack.peek().heading.level() >= h.level()) {
                close(stack, top, start - 1);
            }
            stack.push(new Open(h));
        }
        while (!stack.isEmpty()) {
            close(stack, top, content.length() - 1);
        }
        return top;
    }

    private void close(Deque<Open> stack, List<MarkdownSection> top, int stop) {
        Open open = stack.pop();
        Heading h = open.heading;
        List<Field> fields = new ArrayList<>();
        fields.add(new Field(Section.TITLE, h.title(), h.titleSpan()));
        Map<String, String> metadata = Map.of();
        TextCoordinate metadataSpan = null;
        if (h.block() != null) {
            fields.addAll(h.block().fields());
            metadata = h.block().values();
            metadataSpan = h.block().span();
        }
        MarkdownSection section = new MarkdownSection(MarkdownHeadingGrammarPlugin.KIND_SECTION,
            span(h.headingSpan().startIndex(), stop), h.level(), h.style(), h.title(), metadata,
            open.children, h.headingSpan(), metadataSpan, fields);
        if (stack.isEmpty()) {
            top.add(section);
        } else {
            stack.peek().children.add(section);
        }
    }

    // ------------------------------------------------------------------

    private TextCoordinate span(int start, int stop) {
        int endLine = stop < start ? lines.lineOf(start) : lines.lineOf(stop);
        return new TextCoordinate(text, start, stop, lines.lineOf(start), endLine);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t';
    }

    static String stripEol(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
    }
}
