package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.grammar.conll.ConllLexer;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.language.corpusObject.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class ConllGrammarPlugin extends AbstractGrammarPlugin {

    private final List<CorpusObject> objects = new ArrayList<>();
    private final List<ParseError> errors = new ArrayList<>();

    public ConllGrammarPlugin() { }

    @Override
    public String getId() {
        return "conll-grammar";
    }

    @Override
    public String getDisplayName() {
        return "Conll Grammar";
    }

    @Override
    public String getDisplayDescription() {
        return "Grammar for parsing Conll files";
    }

    @Override
    public ParseResult getResult(Path filePath, String content) {
        try {
            // Create lexer and parser
            var inputStream = CharStreams.fromString(content);
            var lexer = new ConllLexer(inputStream);
            var tokens = new CommonTokenStream(lexer);
            var parser = new ConllParser(tokens);

            // Create listener
            var listener = new ConllListener(content);
            var errorListener = new CollectingErrorListener(filePath, getId());

            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            // Parse and walk tree
            var tree = parser.sample();
            org.antlr.v4.runtime.tree.ParseTreeWalker walker =
                new org.antlr.v4.runtime.tree.ParseTreeWalker();
            walker.walk(listener, tree);

            objects.clear();
            objects.addAll(
                listener
                    .getSentences()
                    .stream()
                    .map(x ->
                        new SimpleDependencyTree(
                            new TextCoordinate(
                                filePath,
                                x.getStart().getStartIndex(),
                                x.getStop().getStopIndex(),
                                x.getStart().getLine(),
                                x.getStop().getLine()
                            ),
                            x.SENT_ID().getText(),
                            null,
                            null,
                            null
                        )
                    )
                    .toList()
            );

            errors.clear();
            errors.addAll(errorListener.getErrors());
        } catch (Exception e) {
            System.err.println("Error parsing text: " + e.getMessage());
        }
        return new ParseResult(objects, errors);
    }
}
