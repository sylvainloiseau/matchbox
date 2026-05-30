// Generated from /Users/sloiseau/Recherche/Corpus/workspace/notebook/src/main/antlr4/fr/lacito/notebook/grammar/conllParser.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link conllParser}.
 */
public interface conllParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link conllParser#sample}.
	 * @param ctx the parse tree
	 */
	void enterSample(conllParser.SampleContext ctx);
	/**
	 * Exit a parse tree produced by {@link conllParser#sample}.
	 * @param ctx the parse tree
	 */
	void exitSample(conllParser.SampleContext ctx);
	/**
	 * Enter a parse tree produced by {@link conllParser#blankLines}.
	 * @param ctx the parse tree
	 */
	void enterBlankLines(conllParser.BlankLinesContext ctx);
	/**
	 * Exit a parse tree produced by {@link conllParser#blankLines}.
	 * @param ctx the parse tree
	 */
	void exitBlankLines(conllParser.BlankLinesContext ctx);
	/**
	 * Enter a parse tree produced by {@link conllParser#sentence}.
	 * @param ctx the parse tree
	 */
	void enterSentence(conllParser.SentenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link conllParser#sentence}.
	 * @param ctx the parse tree
	 */
	void exitSentence(conllParser.SentenceContext ctx);
}