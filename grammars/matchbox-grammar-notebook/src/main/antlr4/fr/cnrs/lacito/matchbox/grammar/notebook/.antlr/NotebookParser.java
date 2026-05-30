// Generated from /Users/sloiseau/workspace/notebook/grammars/matchbox-grammar-notebook/src/main/antlr4/fr/cnrs/lacito/matchbox/grammar/notebook/NotebookParser.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class NotebookParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		START_LEX_SIMPLE=1, START_IMB=2, LBRACE=3, RBRACE=4, DASH=5, TAB=6, QUOTE=7, 
		SINGLE_QUOTE=8, START_HEADWORD=9, RANGLE=10, LPARENTHESIS=11, RPARENTHESIS=12, 
		SHARP=13, HEADER=14, NEWLINE=15, WS=16, WS_HEAD=17, TXT_HEAD=18, END_HEAD=19, 
		WS_LEX=20, TXT=21, END_HEADWORD=22, START_DEF=23, END_LEX=24, END_DEF=25, 
		TXT_DEF=26, START_EX=27, WS_EX=28, TXT_EX=29, SEP_EX=30, END_EX=31, LEX_SIMPLE_GLOSS=32, 
		LEX_SIMPLE_CAT=33, LEX_SIMPLE_FORM=34, WS_LEX_SIMPLE=35, END_LEX_SIMPLE=36, 
		END_IMB=37, IMB_QUOTED_LINE=38, IMB_LINE=39, IMB_NL=40;
	public static final int
		RULE_document = 0, RULE_paragraph = 1, RULE_lexSimpleEntry = 2, RULE_singleLineEntry = 3, 
		RULE_sense = 4, RULE_example = 5, RULE_igt_paragraph = 6, RULE_blankLines = 7;
	private static String[] makeRuleNames() {
		return new String[] {
			"document", "paragraph", "lexSimpleEntry", "singleLineEntry", "sense", 
			"example", "igt_paragraph", "blankLines"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'#LEX'", null, null, null, "'-'", "'\\t'", "'\"'", "'''", null, 
			null, "'('", "')'", "'#'", null, null, null, null, null, null, null, 
			null, null, null, null, null, null, "'\\u00AB'", null, null, "'/'", "'\\u00BB'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "START_LEX_SIMPLE", "START_IMB", "LBRACE", "RBRACE", "DASH", "TAB", 
			"QUOTE", "SINGLE_QUOTE", "START_HEADWORD", "RANGLE", "LPARENTHESIS", 
			"RPARENTHESIS", "SHARP", "HEADER", "NEWLINE", "WS", "WS_HEAD", "TXT_HEAD", 
			"END_HEAD", "WS_LEX", "TXT", "END_HEADWORD", "START_DEF", "END_LEX", 
			"END_DEF", "TXT_DEF", "START_EX", "WS_EX", "TXT_EX", "SEP_EX", "END_EX", 
			"LEX_SIMPLE_GLOSS", "LEX_SIMPLE_CAT", "LEX_SIMPLE_FORM", "WS_LEX_SIMPLE", 
			"END_LEX_SIMPLE", "END_IMB", "IMB_QUOTED_LINE", "IMB_LINE", "IMB_NL"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "NotebookParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public NotebookParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DocumentContext extends ParserRuleContext {
		public List<ParagraphContext> paragraph() {
			return getRuleContexts(ParagraphContext.class);
		}
		public ParagraphContext paragraph(int i) {
			return getRuleContext(ParagraphContext.class,i);
		}
		public TerminalNode EOF() { return getToken(NotebookParser.EOF, 0); }
		public List<BlankLinesContext> blankLines() {
			return getRuleContexts(BlankLinesContext.class);
		}
		public BlankLinesContext blankLines(int i) {
			return getRuleContext(BlankLinesContext.class,i);
		}
		public DocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_document; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitDocument(this);
		}
	}

	public final DocumentContext document() throws RecognitionException {
		DocumentContext _localctx = new DocumentContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_document);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(19);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(16);
				blankLines();
				}
				}
				setState(21);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(22);
			paragraph();
			setState(28);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(23);
					blankLines();
					setState(24);
					paragraph();
					}
					} 
				}
				setState(30);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			}
			setState(34);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(31);
				blankLines();
				}
				}
				setState(36);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(37);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParagraphContext extends ParserRuleContext {
		public SingleLineEntryContext singleLineEntry() {
			return getRuleContext(SingleLineEntryContext.class,0);
		}
		public LexSimpleEntryContext lexSimpleEntry() {
			return getRuleContext(LexSimpleEntryContext.class,0);
		}
		public Igt_paragraphContext igt_paragraph() {
			return getRuleContext(Igt_paragraphContext.class,0);
		}
		public ParagraphContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_paragraph; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterParagraph(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitParagraph(this);
		}
	}

	public final ParagraphContext paragraph() throws RecognitionException {
		ParagraphContext _localctx = new ParagraphContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_paragraph);
		try {
			setState(42);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case START_HEADWORD:
				enterOuterAlt(_localctx, 1);
				{
				setState(39);
				singleLineEntry();
				}
				break;
			case START_LEX_SIMPLE:
				enterOuterAlt(_localctx, 2);
				{
				setState(40);
				lexSimpleEntry();
				}
				break;
			case START_IMB:
				enterOuterAlt(_localctx, 3);
				{
				setState(41);
				igt_paragraph();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LexSimpleEntryContext extends ParserRuleContext {
		public TerminalNode START_LEX_SIMPLE() { return getToken(NotebookParser.START_LEX_SIMPLE, 0); }
		public TerminalNode LEX_SIMPLE_FORM() { return getToken(NotebookParser.LEX_SIMPLE_FORM, 0); }
		public TerminalNode LEX_SIMPLE_CAT() { return getToken(NotebookParser.LEX_SIMPLE_CAT, 0); }
		public TerminalNode LEX_SIMPLE_GLOSS() { return getToken(NotebookParser.LEX_SIMPLE_GLOSS, 0); }
		public TerminalNode END_LEX_SIMPLE() { return getToken(NotebookParser.END_LEX_SIMPLE, 0); }
		public LexSimpleEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lexSimpleEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterLexSimpleEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitLexSimpleEntry(this);
		}
	}

	public final LexSimpleEntryContext lexSimpleEntry() throws RecognitionException {
		LexSimpleEntryContext _localctx = new LexSimpleEntryContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_lexSimpleEntry);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(44);
			match(START_LEX_SIMPLE);
			setState(45);
			match(LEX_SIMPLE_FORM);
			setState(46);
			match(LEX_SIMPLE_CAT);
			setState(47);
			match(LEX_SIMPLE_GLOSS);
			setState(48);
			match(END_LEX_SIMPLE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SingleLineEntryContext extends ParserRuleContext {
		public TerminalNode START_HEADWORD() { return getToken(NotebookParser.START_HEADWORD, 0); }
		public TerminalNode TXT() { return getToken(NotebookParser.TXT, 0); }
		public TerminalNode END_HEADWORD() { return getToken(NotebookParser.END_HEADWORD, 0); }
		public TerminalNode END_LEX() { return getToken(NotebookParser.END_LEX, 0); }
		public List<SenseContext> sense() {
			return getRuleContexts(SenseContext.class);
		}
		public SenseContext sense(int i) {
			return getRuleContext(SenseContext.class,i);
		}
		public SingleLineEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_singleLineEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterSingleLineEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitSingleLineEntry(this);
		}
	}

	public final SingleLineEntryContext singleLineEntry() throws RecognitionException {
		SingleLineEntryContext _localctx = new SingleLineEntryContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_singleLineEntry);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(50);
			match(START_HEADWORD);
			setState(51);
			match(TXT);
			setState(52);
			match(END_HEADWORD);
			setState(56);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==START_DEF) {
				{
				{
				setState(53);
				sense();
				}
				}
				setState(58);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(59);
			match(END_LEX);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SenseContext extends ParserRuleContext {
		public TerminalNode START_DEF() { return getToken(NotebookParser.START_DEF, 0); }
		public TerminalNode TXT_DEF() { return getToken(NotebookParser.TXT_DEF, 0); }
		public TerminalNode END_DEF() { return getToken(NotebookParser.END_DEF, 0); }
		public List<ExampleContext> example() {
			return getRuleContexts(ExampleContext.class);
		}
		public ExampleContext example(int i) {
			return getRuleContext(ExampleContext.class,i);
		}
		public SenseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sense; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterSense(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitSense(this);
		}
	}

	public final SenseContext sense() throws RecognitionException {
		SenseContext _localctx = new SenseContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_sense);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(61);
			match(START_DEF);
			setState(62);
			match(TXT_DEF);
			setState(66);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==START_EX) {
				{
				{
				setState(63);
				example();
				}
				}
				setState(68);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(69);
			match(END_DEF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExampleContext extends ParserRuleContext {
		public TerminalNode START_EX() { return getToken(NotebookParser.START_EX, 0); }
		public List<TerminalNode> TXT_EX() { return getTokens(NotebookParser.TXT_EX); }
		public TerminalNode TXT_EX(int i) {
			return getToken(NotebookParser.TXT_EX, i);
		}
		public TerminalNode SEP_EX() { return getToken(NotebookParser.SEP_EX, 0); }
		public TerminalNode END_EX() { return getToken(NotebookParser.END_EX, 0); }
		public ExampleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_example; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterExample(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitExample(this);
		}
	}

	public final ExampleContext example() throws RecognitionException {
		ExampleContext _localctx = new ExampleContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_example);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(71);
			match(START_EX);
			setState(72);
			match(TXT_EX);
			setState(73);
			match(SEP_EX);
			setState(74);
			match(TXT_EX);
			setState(75);
			match(END_EX);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Igt_paragraphContext extends ParserRuleContext {
		public TerminalNode START_IMB() { return getToken(NotebookParser.START_IMB, 0); }
		public List<TerminalNode> IMB_NL() { return getTokens(NotebookParser.IMB_NL); }
		public TerminalNode IMB_NL(int i) {
			return getToken(NotebookParser.IMB_NL, i);
		}
		public List<TerminalNode> IMB_LINE() { return getTokens(NotebookParser.IMB_LINE); }
		public TerminalNode IMB_LINE(int i) {
			return getToken(NotebookParser.IMB_LINE, i);
		}
		public TerminalNode IMB_QUOTED_LINE() { return getToken(NotebookParser.IMB_QUOTED_LINE, 0); }
		public TerminalNode END_IMB() { return getToken(NotebookParser.END_IMB, 0); }
		public TerminalNode NEWLINE() { return getToken(NotebookParser.NEWLINE, 0); }
		public Igt_paragraphContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_igt_paragraph; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterIgt_paragraph(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitIgt_paragraph(this);
		}
	}

	public final Igt_paragraphContext igt_paragraph() throws RecognitionException {
		Igt_paragraphContext _localctx = new Igt_paragraphContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_igt_paragraph);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(77);
			match(START_IMB);
			setState(78);
			match(IMB_NL);
			setState(79);
			match(IMB_LINE);
			setState(80);
			match(IMB_NL);
			setState(81);
			match(IMB_LINE);
			setState(82);
			match(IMB_NL);
			setState(83);
			match(IMB_QUOTED_LINE);
			setState(84);
			match(IMB_NL);
			setState(85);
			match(END_IMB);
			setState(86);
			match(NEWLINE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlankLinesContext extends ParserRuleContext {
		public List<TerminalNode> NEWLINE() { return getTokens(NotebookParser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(NotebookParser.NEWLINE, i);
		}
		public BlankLinesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blankLines; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).enterBlankLines(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof NotebookParserListener ) ((NotebookParserListener)listener).exitBlankLines(this);
		}
	}

	public final BlankLinesContext blankLines() throws RecognitionException {
		BlankLinesContext _localctx = new BlankLinesContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_blankLines);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(89); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(88);
					match(NEWLINE);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(91); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001(^\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002"+
		"\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002\u0005"+
		"\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0001\u0000"+
		"\u0005\u0000\u0012\b\u0000\n\u0000\f\u0000\u0015\t\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0000\u0005\u0000\u001b\b\u0000\n\u0000\f\u0000"+
		"\u001e\t\u0000\u0001\u0000\u0005\u0000!\b\u0000\n\u0000\f\u0000$\t\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001"+
		"+\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003"+
		"7\b\u0003\n\u0003\f\u0003:\t\u0003\u0001\u0003\u0001\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0005\u0004A\b\u0004\n\u0004\f\u0004D\t\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0007\u0004\u0007Z\b\u0007\u000b\u0007\f\u0007[\u0001"+
		"\u0007\u0000\u0000\b\u0000\u0002\u0004\u0006\b\n\f\u000e\u0000\u0000]"+
		"\u0000\u0013\u0001\u0000\u0000\u0000\u0002*\u0001\u0000\u0000\u0000\u0004"+
		",\u0001\u0000\u0000\u0000\u00062\u0001\u0000\u0000\u0000\b=\u0001\u0000"+
		"\u0000\u0000\nG\u0001\u0000\u0000\u0000\fM\u0001\u0000\u0000\u0000\u000e"+
		"Y\u0001\u0000\u0000\u0000\u0010\u0012\u0003\u000e\u0007\u0000\u0011\u0010"+
		"\u0001\u0000\u0000\u0000\u0012\u0015\u0001\u0000\u0000\u0000\u0013\u0011"+
		"\u0001\u0000\u0000\u0000\u0013\u0014\u0001\u0000\u0000\u0000\u0014\u0016"+
		"\u0001\u0000\u0000\u0000\u0015\u0013\u0001\u0000\u0000\u0000\u0016\u001c"+
		"\u0003\u0002\u0001\u0000\u0017\u0018\u0003\u000e\u0007\u0000\u0018\u0019"+
		"\u0003\u0002\u0001\u0000\u0019\u001b\u0001\u0000\u0000\u0000\u001a\u0017"+
		"\u0001\u0000\u0000\u0000\u001b\u001e\u0001\u0000\u0000\u0000\u001c\u001a"+
		"\u0001\u0000\u0000\u0000\u001c\u001d\u0001\u0000\u0000\u0000\u001d\"\u0001"+
		"\u0000\u0000\u0000\u001e\u001c\u0001\u0000\u0000\u0000\u001f!\u0003\u000e"+
		"\u0007\u0000 \u001f\u0001\u0000\u0000\u0000!$\u0001\u0000\u0000\u0000"+
		"\" \u0001\u0000\u0000\u0000\"#\u0001\u0000\u0000\u0000#%\u0001\u0000\u0000"+
		"\u0000$\"\u0001\u0000\u0000\u0000%&\u0005\u0000\u0000\u0001&\u0001\u0001"+
		"\u0000\u0000\u0000\'+\u0003\u0006\u0003\u0000(+\u0003\u0004\u0002\u0000"+
		")+\u0003\f\u0006\u0000*\'\u0001\u0000\u0000\u0000*(\u0001\u0000\u0000"+
		"\u0000*)\u0001\u0000\u0000\u0000+\u0003\u0001\u0000\u0000\u0000,-\u0005"+
		"\u0001\u0000\u0000-.\u0005\"\u0000\u0000./\u0005!\u0000\u0000/0\u0005"+
		" \u0000\u000001\u0005$\u0000\u00001\u0005\u0001\u0000\u0000\u000023\u0005"+
		"\t\u0000\u000034\u0005\u0015\u0000\u000048\u0005\u0016\u0000\u000057\u0003"+
		"\b\u0004\u000065\u0001\u0000\u0000\u00007:\u0001\u0000\u0000\u000086\u0001"+
		"\u0000\u0000\u000089\u0001\u0000\u0000\u00009;\u0001\u0000\u0000\u0000"+
		":8\u0001\u0000\u0000\u0000;<\u0005\u0018\u0000\u0000<\u0007\u0001\u0000"+
		"\u0000\u0000=>\u0005\u0017\u0000\u0000>B\u0005\u001a\u0000\u0000?A\u0003"+
		"\n\u0005\u0000@?\u0001\u0000\u0000\u0000AD\u0001\u0000\u0000\u0000B@\u0001"+
		"\u0000\u0000\u0000BC\u0001\u0000\u0000\u0000CE\u0001\u0000\u0000\u0000"+
		"DB\u0001\u0000\u0000\u0000EF\u0005\u0019\u0000\u0000F\t\u0001\u0000\u0000"+
		"\u0000GH\u0005\u001b\u0000\u0000HI\u0005\u001d\u0000\u0000IJ\u0005\u001e"+
		"\u0000\u0000JK\u0005\u001d\u0000\u0000KL\u0005\u001f\u0000\u0000L\u000b"+
		"\u0001\u0000\u0000\u0000MN\u0005\u0002\u0000\u0000NO\u0005(\u0000\u0000"+
		"OP\u0005\'\u0000\u0000PQ\u0005(\u0000\u0000QR\u0005\'\u0000\u0000RS\u0005"+
		"(\u0000\u0000ST\u0005&\u0000\u0000TU\u0005(\u0000\u0000UV\u0005%\u0000"+
		"\u0000VW\u0005\u000f\u0000\u0000W\r\u0001\u0000\u0000\u0000XZ\u0005\u000f"+
		"\u0000\u0000YX\u0001\u0000\u0000\u0000Z[\u0001\u0000\u0000\u0000[Y\u0001"+
		"\u0000\u0000\u0000[\\\u0001\u0000\u0000\u0000\\\u000f\u0001\u0000\u0000"+
		"\u0000\u0007\u0013\u001c\"*8B[";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}