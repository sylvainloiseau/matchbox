package fr.cnrs.lacito.matchbox.grammar.tag;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Inline tags {@code #"keyword"} (or with curly quotes), anywhere in a line, as {@link SimpleTag}s;
 * {@code /} separates the levels of a keyword. The object a tag applies to is computed by
 * {@code Text.targetOf}.
 */
public class TagGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "tag-grammar";
    public static final String KIND_TAG = ID + ":tag";

    private static final ObjectEditor EDITOR = new TagObjectEditor();

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
        return List.of(KIND_TAG);
    }

    @Override
    public DisplayStyle getDefaultDisplayStyle(String kind) {
        return DisplayStyle.background("#ffd6e8");
    }

    @Override
    public String getDisplayName() {
        return "Tags";
    }

    @Override
    public String getDisplayDescription() {
        return "Inline tags #\"keyword\" attached to the objects of a text";
    }

    @Override
    public String getFormatDocumentation() {
        return """
            A tag is `#` followed by a keyword between double quotes: `#"infinitive"` (curly quotes “” are
            accepted too). Tags can appear anywhere in a line, several per line; they are not read in fenced code blocks.

            - The keyword is any text without quote or line break; surrounding spaces are ignored; it cannot be empty.
            - `/` separates levels of a hierarchy: `#"verb/infinitive"` is "infinitive" under "verb".
            - A tag applies to the object (IGT, lexical entry, media marker...) on the line just above it, else just
              below it (no blank line in between; lines containing only tags are skipped), else to the object it is
              written in (e.g. a sense, a section), else to the whole text.
            - Avoid tags inside the transcription and gloss lines of an IGT (they break the alignment) and in metadata
              values (` #` starts a YAML comment).
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("Tags after a sentence", "'I went down in canoe' #\"verb/motion\" #\"to check\"\n"),
            new GrammarExample("A tag line", "#\"elicitation\"\n"));
    }

    @Override
    public String getGrammarSource() {
        StringBuilder source = new StringBuilder();
        for (String name : List.of("TagLexer.g4", "TagParser.g4")) {
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
            var lexer = new TagLexer(CharStreams.fromString(content));
            var parser = new TagParser(new CommonTokenStream(lexer));
            var errors = new CollectingErrorListener(text, ID);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.removeErrorListeners();
            parser.addErrorListener(errors);
            for (TerminalNode node : parser.document().TAG()) {
                Token t = node.getSymbol();
                String inner = t.getText().substring(2, t.getText().length() - 1);
                String name = inner.strip();
                if (name.isEmpty()) {
                    diagnostics.add(new Diagnostic(text, ID, t.getLine(), t.getCharPositionInLine(),
                        "Empty tag", t.getStartIndex(), t.getStopIndex(), List.of()));
                    continue;
                }
                int start = t.getStartIndex() + 2 + (inner.length() - inner.stripLeading().length());
                TextCoordinate keyword = new TextCoordinate(text, start, start + name.length() - 1, t.getLine(), t.getLine());
                objects.add(new SimpleTag(KIND_TAG, Spans.of(text, t), name, List.of(new Field(Tag.TAG, name, keyword))));
            }
            diagnostics.addAll(errors.getDiagnostics());
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.at(text, ID, 0, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, diagnostics);
    }
}
