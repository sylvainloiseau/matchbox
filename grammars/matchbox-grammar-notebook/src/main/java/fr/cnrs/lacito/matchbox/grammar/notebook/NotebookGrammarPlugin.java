package fr.cnrs.lacito.matchbox.grammar.notebook;

import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.ExampleContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.SenseContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookLexer;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Example;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public class NotebookGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "notebook-grammar";
    public static final String KIND_ENTRY = ID + ":lexical-entry";
    public static final String KIND_SENSE = ID + ":sense";
    public static final String KIND_EXAMPLE = ID + ":example";

    public NotebookGrammarPlugin() {
        // Constructor can be used for initialization if needed
    }

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
        return List.of(KIND_ENTRY, KIND_SENSE, KIND_EXAMPLE);
    }

    /** Interlinear glossed texts and lexical entries keep their former highlight colours. */
    @Override
    public DisplayStyle getDefaultDisplayStyle(String kind) {
        if (KIND_ENTRY.equals(kind)) return DisplayStyle.background("#dfe7fd");
        return DisplayStyle.EMPTY;
    }

    @Override
    public String getDisplayName() {
        return "Notebook Grammar";
    }

    @Override
    public String getDisplayDescription() {
        return "Grammar for parsing notebook files";
    }

    @Override
    public String getFormatDocumentation() {
        return """
This grammar looks for lexical entries.

Entries start at the beginning of a line. Every other line (free text, Markdown headings, `---` lines, metadata) is ignored.

- IGTs (interlinear glossed text between ::: lines) are read by the IGT grammar (igt-grammar), not by this one.

- The pattern is a lexical entry definition. It contains a headword between brackets, followed by a definition between curly braces.

""";

    }

    // TODO: add canonical snippets, each parsing without errors
    @Override
    public List<GrammarExample> getExamples() {
        return List.of();
    }

    // TODO: return the .g4 sources
    @Override
    public String getGrammarSource() {
        return "";
    }

    @Override
    public ParseResult getResult(TextId filePath, String content) {
        List<CorpusObject> objects = new ArrayList<>();
        List<Diagnostic> errors = new ArrayList<>();
        try {
            // Create lexer and parser
            var inputStream = CharStreams.fromString(content);
            var lexer = new NotebookLexer(inputStream);
            // if (morphemeSeparators != null) {
            //     lexer.setMorphemeSeparators(morphemeSeparators);
            // }
            var tokens = new CommonTokenStream(lexer);
            var parser = new NotebookParser(tokens);

            // Create listener
            var listener = new NotebookListener(content);
            var errorListener = new CollectingErrorListener(filePath, getId());

            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            // Parse and walk tree
            var tree = parser.document();
            org.antlr.v4.runtime.tree.ParseTreeWalker walker =
                new org.antlr.v4.runtime.tree.ParseTreeWalker();
            walker.walk(listener, tree);

            // Rules produced by error recovery may lack tokens: such objects are skipped
            // (the syntax error is reported anyway), the others are kept.
            for (var x : listener.getLexicalDefinitions()) {
                if (x.exception != null || x.TXT() == null) {
                    continue;
                }
                List<SimpleSense> senses = new ArrayList<>();
                for (SenseContext sense : x.sense()) {
                    if (sense.exception != null || sense.TXT_DEF() == null) {
                        continue;
                    }
                    List<SimpleExample> examples = new ArrayList<>();
                    for (ExampleContext example : sense.example()) {
                        if (example.exception != null || example.TXT_EX().size() < 2) {
                            continue;
                        }
                        examples.add(
                            new SimpleExample(
                                KIND_EXAMPLE,
                                Spans.of(filePath, example),
                                example.TXT_EX(0).getText(),
                                example.TXT_EX(1).getText(),
                                List.of(
                                    field(filePath, Example.TRANSCRIPTION, example.TXT_EX(0)),
                                    field(filePath, Example.TRANSLATION, example.TXT_EX(1))
                                )
                            )
                        );
                    }
                    senses.add(
                        new SimpleSense(
                            KIND_SENSE,
                            Spans.of(filePath, sense),
                            sense.TXT_DEF().getText(),
                            examples,
                            List.of(field(filePath, Sense.DEFINITION, sense.TXT_DEF()))
                        )
                    );
                }
                objects.add(
                    new SimpleLexicalDefinition(
                        KIND_ENTRY,
                        Spans.of(filePath, x),
                        x.TXT().getText(),
                        senses,
                        List.of(field(filePath, LexicalEntry.HEADWORD, x.TXT()))
                    )
                );
            }
            errors.addAll(errorListener.getDiagnostics());
        } catch (RuntimeException e) {
            errors.add(Diagnostic.internal(filePath, getId(), 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, errors);
    }

    private static Field field(TextId text, String name, TerminalNode node) {
        return new Field(name, node.getText(), Spans.of(text, node.getSymbol()));
    }

    /** Characters that would end each field in this notation. */
    private static final Map<String, String> FORBIDDEN = Map.of(
        LexicalEntry.HEADWORD, ">{}/",
        Sense.DEFINITION, "}«",
        Example.TRANSCRIPTION, "/«»}",
        Example.TRANSLATION, "/«»}"
    );

    /** Refuses values that would change the structure of the notation, then writes them verbatim. */
    private static final ObjectEditor EDITOR = (object, fieldName, value, content) -> {
        String forbidden = FORBIDDEN.getOrDefault(fieldName, "");
        for (char c : forbidden.toCharArray()) {
            if (value.indexOf(c) >= 0) {
                return new EditProposal.Refused("The character " + c + " is not allowed here");
            }
        }
        if (value.isBlank()) {
            return new EditProposal.Refused("The value cannot be empty");
        }
        return ObjectEditor.VERBATIM.setField(object, fieldName, value, content);
    };

    @Override
    public ObjectEditor getObjectEditor() {
        return EDITOR;
    }
}
