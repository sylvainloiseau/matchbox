// Generated from /Users/sloiseau/workspace/notebook/grammars/matchbox-grammar-notebook/src/main/antlr4/fr/cnrs/lacito/matchbox/grammar/notebook/NotebookParser.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link NotebookParser}.
 */
public interface NotebookParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link NotebookParser#document}.
	 * @param ctx the parse tree
	 */
	void enterDocument(NotebookParser.DocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#document}.
	 * @param ctx the parse tree
	 */
	void exitDocument(NotebookParser.DocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#paragraph}.
	 * @param ctx the parse tree
	 */
	void enterParagraph(NotebookParser.ParagraphContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#paragraph}.
	 * @param ctx the parse tree
	 */
	void exitParagraph(NotebookParser.ParagraphContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#lexSimpleEntry}.
	 * @param ctx the parse tree
	 */
	void enterLexSimpleEntry(NotebookParser.LexSimpleEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#lexSimpleEntry}.
	 * @param ctx the parse tree
	 */
	void exitLexSimpleEntry(NotebookParser.LexSimpleEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#singleLineEntry}.
	 * @param ctx the parse tree
	 */
	void enterSingleLineEntry(NotebookParser.SingleLineEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#singleLineEntry}.
	 * @param ctx the parse tree
	 */
	void exitSingleLineEntry(NotebookParser.SingleLineEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#sense}.
	 * @param ctx the parse tree
	 */
	void enterSense(NotebookParser.SenseContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#sense}.
	 * @param ctx the parse tree
	 */
	void exitSense(NotebookParser.SenseContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#example}.
	 * @param ctx the parse tree
	 */
	void enterExample(NotebookParser.ExampleContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#example}.
	 * @param ctx the parse tree
	 */
	void exitExample(NotebookParser.ExampleContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#igt_paragraph}.
	 * @param ctx the parse tree
	 */
	void enterIgt_paragraph(NotebookParser.Igt_paragraphContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#igt_paragraph}.
	 * @param ctx the parse tree
	 */
	void exitIgt_paragraph(NotebookParser.Igt_paragraphContext ctx);
	/**
	 * Enter a parse tree produced by {@link NotebookParser#blankLines}.
	 * @param ctx the parse tree
	 */
	void enterBlankLines(NotebookParser.BlankLinesContext ctx);
	/**
	 * Exit a parse tree produced by {@link NotebookParser#blankLines}.
	 * @param ctx the parse tree
	 */
	void exitBlankLines(NotebookParser.BlankLinesContext ctx);
}