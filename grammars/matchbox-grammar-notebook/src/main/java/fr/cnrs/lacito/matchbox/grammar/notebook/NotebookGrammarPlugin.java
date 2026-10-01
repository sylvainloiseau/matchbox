package fr.cnrs.lacito.matchbox.grammar.notebook;

import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.ExampleContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.SenseContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookLexer;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleExample;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class NotebookGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "notebook-grammar";
    public static final String KIND_IGT = ID + ":igt";
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
    public String getDisplayName() {
        return "Notebook Grammar";
    }

    @Override
    public String getDisplayDescription() {
        return "Grammar for parsing notebook files";
    }

    // TODO: describe the notation for users and AI prompts
    @Override
    public String getFormatDocumentation() {
        return "";
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
    public ParseResult getResult(Path filePath, String content) {
        List<CorpusObject> objects = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
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
            for (var x : listener.getIGTs()) {
                if (x.exception != null || x.IMB_LINE().size() < 2 || x.IMB_QUOTED_LINE() == null) {
                    continue;
                }
                objects.add(
                    new SimpleIGT(
                        KIND_IGT,
                        Spans.of(filePath, x),
                        x.IMB_LINE(0).getText(),
                        x.IMB_LINE(1).getText(),
                        x.IMB_QUOTED_LINE().getText()
                    )
                );
            }

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
                                example.TXT_EX(1).getText()
                            )
                        );
                    }
                    senses.add(
                        new SimpleSense(
                            KIND_SENSE,
                            Spans.of(filePath, sense),
                            sense.TXT_DEF().getText(),
                            examples
                        )
                    );
                }
                objects.add(
                    new SimpleLexicalDefinition(
                        KIND_ENTRY,
                        Spans.of(filePath, x),
                        x.TXT().getText(),
                        senses
                    )
                );
            }
            errors.addAll(errorListener.getErrors());
        } catch (RuntimeException e) {
            errors.add(new ParseError(filePath, getId(), 0, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, errors);
    }
}
