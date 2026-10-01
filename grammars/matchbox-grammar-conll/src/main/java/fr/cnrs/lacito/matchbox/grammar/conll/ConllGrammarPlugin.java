package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.grammar.conll.ConllLexer;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleDependencyTree;
import fr.cnrs.lacito.matchbox.grammarapi.Spans;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public class ConllGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "conll-grammar";
    public static final String KIND_SENTENCE = ID + ":sentence";

    public ConllGrammarPlugin() { }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    /** CoNLL-U columns after ID. */
    static final List<String> FIELDS = List.of(
        "FORM", "LEMMA", "UPOS", "XPOS", "FEATS", "HEAD", "DEPREL", "DEPS", "MISC"
    );

    /** Builds a tree from the terminals of a sentence rule (header, comments, token lines). */
    static SimpleDependencyTree toTree(Path filePath, ConllParser.SentenceContext ctx) {
        Map<String, String> metadata = new LinkedHashMap<>();
        List<Map<String, String>> tokens = new ArrayList<>();
        List<Integer> heads = new ArrayList<>();

        String metadataKey = null;
        boolean afterEquals = false;
        Map<String, String> token = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode node)) {
                continue;
            }
            Token t = node.getSymbol();
            switch (t.getType()) {
                case ConllLexer.OTHER_HEADER -> { metadataKey = null; afterEquals = false; }
                case ConllLexer.TXT_OTHER_HEADER -> {
                    if (!afterEquals) {
                        metadataKey = t.getText().strip();
                    } else if (metadataKey != null) {
                        metadata.put(metadataKey, t.getText().strip());
                    }
                }
                case ConllLexer.EQ_OTHER_HEADER -> afterEquals = true;
                case ConllLexer.TOKEN_ID -> {
                    token = new LinkedHashMap<>();
                    token.put("ID", t.getText());
                }
                case ConllLexer.TOKEN_FIELD -> {
                    if (token != null && token.size() <= FIELDS.size()) {
                        token.put(FIELDS.get(token.size() - 1), t.getText());
                    }
                }
                case ConllLexer.NL_TOKEN -> {
                    if (token != null) {
                        tokens.add(token);
                        heads.add(parseHead(token.get("HEAD")));
                        token = null;
                    }
                }
                default -> { }
            }
        }
        return new SimpleDependencyTree(
            KIND_SENTENCE,
            Spans.of(filePath, ctx),
            ctx.SENT_ID().getText().strip(),
            metadata,
            tokens,
            heads
        );
    }

    private static int parseHead(String head) {
        try {
            return head == null ? -1 : Integer.parseInt(head.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
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
        List<CorpusObject> objects = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
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

            for (var x : listener.getSentences()) {
                if (x.exception != null || x.SENT_ID() == null) {
                    continue;
                }
                objects.add(toTree(filePath, x));
            }

            errors.addAll(errorListener.getErrors());
        } catch (RuntimeException e) {
            errors.add(new ParseError(filePath, getId(), 0, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, errors);
    }
}
