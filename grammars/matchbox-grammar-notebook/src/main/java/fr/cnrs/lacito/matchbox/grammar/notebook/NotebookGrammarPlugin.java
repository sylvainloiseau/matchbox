package fr.cnrs.lacito.matchbox.grammar.notebook;

import fr.cnrs.lacito.matchboxgrammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.ExampleContext;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookParser.SenseContext;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import fr.cnrs.lacito.matchboxgrammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchboxgrammarapi.ParseError;
import fr.cnrs.lacito.matchboxgrammarapi.ParseResult;

public class NotebookGrammarPlugin extends AbstractGrammarPlugin {

    private final List<CorpusObject> objects = new ArrayList<>();
    private final List<ParseError> errors = new ArrayList<>();

    public NotebookGrammarPlugin() {
        // Constructor can be used for initialization if needed
    }

    @Override
    public String getId() {
        return "notebook-grammar";
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
    public ParseResult getResult(Path filePath, String content) {
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

            objects.clear();
            objects.addAll(
                listener
                    .getIGTs()
                    .stream()
                    .map(
                        x -> new SimpleIGT(
                            new TextCoordinate(
                                filePath, x.getStart().getStartIndex(), x.getStop().getStopIndex(), x.getStart().getLine(), x.getStop().getLine()
                            ), 
                            x.IMB_LINE(0).getText(),
                            x.IMB_LINE(1).getText(),
                            x.IMB_QUOTED_LINE().getText()
                        )
                    )
                    .toList()
            );

            objects.addAll(
                listener.getLexicalDefinitions().stream().map(
                    x -> {
                        TextCoordinate coordinate = new TextCoordinate(filePath, x.getStart().getStartIndex(), x.getStop().getStopIndex(), x.getStart().getLine(), x.getStop().getLine());
                        SimpleLexicalDefinition sld = new SimpleLexicalDefinition(coordinate, 
                            x.TXT().getText());
                        
                        for (SenseContext sense : x.sense()) {
                            SimpleSense s = new SimpleSense(
                                new TextCoordinate(
                                    filePath,
                                    sense.getStart().getStartIndex(),
                                    sense.getStop().getStopIndex(),
                                    sense.getStart().getLine(),
                                    sense.getStop().getLine()
                                ),
                                sense.TXT_DEF().getText()
                            );
                            for (ExampleContext example : sense.example()) {
                                s.addExample(
                                    new SimpleExample(
                                        new TextCoordinate(
                                            filePath,
                                            example.getStart().getStartIndex(),
                                            example.getStop().getStopIndex(),
                                            example.getStart().getLine(),
                                            example.getStop().getLine()
                                        ),
                                        example.TXT_EX(0).getText(),
                                        example.TXT_EX(1).getText()
                                    )
                                );
                            }
                            sld.addSense(s);
                        }
                        return sld;
                    }
                ).toList()
            );
            errors.clear();
            errors.addAll(errorListener.getErrors());
        } catch (Exception e) {
            System.err.println("Error parsing text: " + e.getMessage());
        }
        return new ParseResult(objects, errors);
    }
}