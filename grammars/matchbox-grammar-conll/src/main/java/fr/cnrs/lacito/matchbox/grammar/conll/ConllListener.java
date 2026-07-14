package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.grammar.conll.ConllParser;
import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR listener for extracting conll dependency tree
 */
public class ConllListener extends ConllParserBaseListener {

    private final List<ConllParser.SentenceContext> sentences;

    public ConllListener(String content) {
        this.sentences = new ArrayList<>();
    }

    @Override
    public void enterSentence(ConllParser.SentenceContext ctx) {
        try {
            sentences.add(ctx);
        } catch (Exception e) {
            System.err.println("Error parsing sentence: " + e.getMessage());
        }
    }

    public List<ConllParser.SentenceContext> getSentences() {
        return sentences;
    }
}
