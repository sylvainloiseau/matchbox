package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.grammar.conll.ConllLexer;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
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

    // TODO: describe the notation for users and AI prompts
    @Override
    public String getFormatDocumentation() {
        return """
The CoNLL-U format has three types of lines:

- Word lines containing the annotation of a word/token/node in 10 fields separated by single tab characters; see below.
- Blank lines marking sentence boundaries. The last line of each sentence is a blank line.
- Sentence-level comments starting with hash (#). Comment lines occur at the beginning of sentences, before word lines. The comment lines contain, after the # symbol,
        a field name and a field value, separated by an equal sign: "#<field_name>=<field_value>". Very common comment lines are : "sent_id" for the sentence ID, "text" for the sentence text, and "text_en" for its translation in english

Sentences consist of one or more word lines, and word lines contain the following fields:

ID: Word index, integer starting at 1 for each new sentence; may be a range for multiword tokens; may be a decimal number for empty nodes (decimal numbers can be lower than 1 but must be greater than 0).
FORM: Word form or punctuation symbol.
LEMMA: Lemma or stem of word form, which include typically : ADJ ADP PUNCT ADV AUX SYM INTJ CCONJ X NOUN DET PROPN NUM VERB PART PRON SCONJ
UPOS: Universal part-of-speech tag.
XPOS: Optional language-specific (or treebank-specific) part-of-speech / morphological tag; underscore if not available.
FEATS: List of morphological features from the universal feature inventory or from a defined language-specific extension; underscore if not available.
HEAD: Head of the current word, which is either a value of ID or zero (0).
DEPREL: Universal dependency relation to the HEAD (root iff HEAD = 0) or a defined language-specific subtype of one.
DEPS: Enhanced dependency graph in the form of a list of head-deprel pairs.
MISC: Any other annotation.

Note that the number of fields may vary depending on the specific CoNLL-U format used.

The fields must additionally meet the following constraints:

- Fields must not be empty.
- Fields other than FORM, LEMMA, and MISC must not contain space characters.
- Underscore (_) is used to denote unspecified values in all fields except ID.
""";
    }

    // TODO: add canonical snippets, each parsing without errors
    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample(
                "Typical sentence in conll format",
                """# sent_id = 1
# text = They buy and sell books.
1   They     they    PRON    PRP    Case=Nom|Number=Plur               2   nsubj   2:nsubj|4:nsubj   _
2   buy      buy     VERB    VBP    Number=Plur|Person=3|Tense=Pres    0   root    0:root            _
3   and      and     CCONJ   CC     _                                  4   cc      4:cc              _
4   sell     sell    VERB    VBP    Number=Plur|Person=3|Tense=Pres    2   conj    0:root|2:conj     _
5   books    book    NOUN    NNS    Number=Plur                        2   obj     2:obj|4:obj       SpaceAfter=No
6   .        .       PUNCT   .      _                                  2   punct   2:punct           _"""),
            new GrammarExample(
                "Typical sentence in conll format",
                """# newdoc id = mf920901-001
# newpar id = mf920901-001-p1
# sent_id = mf920901-001-p1s1A
# text = Slovenská ústava: pro i proti
# text_en = Slovak constitution: pros and cons
1   Slovenská   slovenský   ADJ     AAFS1----1A---- Case=Nom|Degree=Pos|Gender=Fem|Number=Sing|Polarity=Pos 2 amod _ _
2   ústava      ústava      NOUN    NNFS1-----A---- Case=Nom|Gender=Fem|Number=Sing|Polarity=Pos 0 root _ SpaceAfter=No
3   :           :           PUNCT   Z:------------- _          2       punct   _       _
4   pro         pro         ADP     RR--4---------- Case=Acc   2       appos   _       LId=pro-1
5   i           i           CCONJ   J^------------- _          6       cc      _       LId=i-1
6   proti       proti       ADP     RR--3---------- Case=Dat   4       conj    _       LId=proti-1""")
        );
    }

    // TODO: return the .g4 sources
    @Override
    public String getGrammarSource() {
        return "not provided";
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
