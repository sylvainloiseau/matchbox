package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Structure of Markdown files: sections from ATX ({@code ## Title}) and Setext
 * ({@code Title / ===}) headings, YAML metadata of the file (header on line 1) and of the sections
 * (block right after the heading). Every other line is ignored, so the grammar can run next to
 * another one (e.g. the notebook grammar) on the same files.
 */
public class MarkdownHeadingGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "markdown-heading-grammar";
    public static final String KIND_SECTION = ID + ":section";
    public static final String KIND_HEADER = ID + ":header";

    private static final ObjectEditor EDITOR = new MarkdownHeadingObjectEditor();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public List<String> getObjectKinds() {
        return List.of(KIND_SECTION, KIND_HEADER);
    }

    @Override
    public String getDisplayName() {
        return "Markdown headings and metadata";
    }

    @Override
    public String getDisplayDescription() {
        return "Sections (headings) and YAML metadata of Markdown files";
    }

    @Override
    public String getFormatDocumentation() {
        return """
            Structure of Markdown notes. Only headings and metadata blocks are read; every other line is ignored.

            - **Headings** make nested sections; a section runs until the next heading of the same or a higher level.
              - ATX: one to six `#`, a space, the title (`## Verbs`); closing `#`s are ignored (`## Verbs ##`).
                `#hashtag` (no space) and lines indented by four spaces are not headings.
              - Setext: a single text line underlined by `===` (level 1) or `---` (level 2). The line above the
                underline must be one line, preceded by a blank line or a heading, and must not be `:::`.
            - **Metadata blocks**: lines `key: value` between two `---` lines (a subset of YAML).
              - On the first line of the file, the block is the header of the whole text (e.g. language, speaker).
              - On the first non-blank line after a heading, it is the metadata of that section (e.g. date, media).
              - Values may be quoted ("..." or '...'); ` #` starts a comment; lists, nested values and
                multi-line values are not supported. A `---` elsewhere is a separator.
              - Conventional keys: `date` (ISO 8601: 2024-05-12, 2024-05-12T14:30), `media` (path of an audio or
                video file, relative to the project or the note, or an http(s) URL), `speakers` (names separated by
                commas).
            - Nothing is recognised inside fenced code blocks (``` or ~~~).
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("Header of the text, sections and section metadata", """
                ---
                language: Tuwari
                speaker: Elaisa
                ---

                # Session 1

                ---
                date: 2024-05-12T14:30
                media: recordings/session1.wav
                ---

                ## Nouns
                """),
            new GrammarExample("Setext headings", """
                Session 2
                =========

                Verbs
                -----
                """),
            new GrammarExample("A fenced code block is not read", """
                # Notes

                ```
                # not a heading
                ```
                """));
    }

    @Override
    public String getGrammarSource() {
        StringBuilder source = new StringBuilder();
        for (String name : List.of("MarkdownHeadingLexer.g4", "MarkdownHeadingParser.g4")) {
            try (InputStream in = getClass().getResourceAsStream(name)) {
                if (in != null) {
                    source.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                }
            } catch (IOException e) {
                // the source is optional
            }
        }
        return source.toString();
    }

    @Override
    public ObjectEditor getObjectEditor() {
        return EDITOR;
    }

    @Override
    public ParseResult getResult(TextId text, String content) {
        List<CorpusObject> objects = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            var lexer = new MarkdownHeadingLexer(CharStreams.fromString(content));
            var parser = new MarkdownHeadingParser(new CommonTokenStream(lexer));
            var errors = new CollectingErrorListener(text, ID);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.removeErrorListeners();
            parser.addErrorListener(errors);
            var visitor = new MarkdownHeadingVisitor(text, content);
            parser.document().accept(visitor);
            objects.addAll(visitor.objects());
            diagnostics.addAll(errors.getDiagnostics());
            diagnostics.addAll(visitor.diagnostics());
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.at(text, ID, 0, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, diagnostics);
    }
}
