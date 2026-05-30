// Generated from /Users/sloiseau/workspace/notebook/src/main/antlr4/fr/lacito/notebook/grammar/conllParser.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class conllParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		SENT_HEADER=1, OTHER_HEADER=2, TOKEN_ID=3, NUM=4, NL=5, TOKEN_FIELD=6, 
		NL_TOKEN=7, TAB_TOKEN=8, WS_SENT=9, EQ_SENT=10, SENT_ID=11, WS_OTHER_HEADER=12, 
		EQ_OTHER_HEADER=13, TXT_OTHER_HEADER=14, NL_OTHER_HEADER=15;
	public static final int
		RULE_sample = 0, RULE_blankLines = 1, RULE_sentence = 2;
	private static String[] makeRuleNames() {
		return new String[] {
			"sample", "blankLines", "sentence"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, "'\\t'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "SENT_HEADER", "OTHER_HEADER", "TOKEN_ID", "NUM", "NL", "TOKEN_FIELD", 
			"NL_TOKEN", "TAB_TOKEN", "WS_SENT", "EQ_SENT", "SENT_ID", "WS_OTHER_HEADER", 
			"EQ_OTHER_HEADER", "TXT_OTHER_HEADER", "NL_OTHER_HEADER"
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
	public String getGrammarFileName() { return "conllParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public conllParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SampleContext extends ParserRuleContext {
		public List<SentenceContext> sentence() {
			return getRuleContexts(SentenceContext.class);
		}
		public SentenceContext sentence(int i) {
			return getRuleContext(SentenceContext.class,i);
		}
		public TerminalNode EOF() { return getToken(conllParser.EOF, 0); }
		public List<BlankLinesContext> blankLines() {
			return getRuleContexts(BlankLinesContext.class);
		}
		public BlankLinesContext blankLines(int i) {
			return getRuleContext(BlankLinesContext.class,i);
		}
		public SampleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sample; }
	}

	public final SampleContext sample() throws RecognitionException {
		SampleContext _localctx = new SampleContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_sample);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(9);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NL) {
				{
				{
				setState(6);
				blankLines();
				}
				}
				setState(11);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(12);
			sentence();
			setState(18);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(13);
					blankLines();
					setState(14);
					sentence();
					}
					} 
				}
				setState(20);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			}
			setState(24);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NL) {
				{
				{
				setState(21);
				blankLines();
				}
				}
				setState(26);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(27);
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
	public static class BlankLinesContext extends ParserRuleContext {
		public List<TerminalNode> NL() { return getTokens(conllParser.NL); }
		public TerminalNode NL(int i) {
			return getToken(conllParser.NL, i);
		}
		public BlankLinesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blankLines; }
	}

	public final BlankLinesContext blankLines() throws RecognitionException {
		BlankLinesContext _localctx = new BlankLinesContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_blankLines);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(30); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(29);
					match(NL);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(32); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
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

	@SuppressWarnings("CheckReturnValue")
	public static class SentenceContext extends ParserRuleContext {
		public TerminalNode SENT_HEADER() { return getToken(conllParser.SENT_HEADER, 0); }
		public TerminalNode EQ_SENT() { return getToken(conllParser.EQ_SENT, 0); }
		public TerminalNode SENT_ID() { return getToken(conllParser.SENT_ID, 0); }
		public List<BlankLinesContext> blankLines() {
			return getRuleContexts(BlankLinesContext.class);
		}
		public BlankLinesContext blankLines(int i) {
			return getRuleContext(BlankLinesContext.class,i);
		}
		public List<TerminalNode> OTHER_HEADER() { return getTokens(conllParser.OTHER_HEADER); }
		public TerminalNode OTHER_HEADER(int i) {
			return getToken(conllParser.OTHER_HEADER, i);
		}
		public List<TerminalNode> TXT_OTHER_HEADER() { return getTokens(conllParser.TXT_OTHER_HEADER); }
		public TerminalNode TXT_OTHER_HEADER(int i) {
			return getToken(conllParser.TXT_OTHER_HEADER, i);
		}
		public List<TerminalNode> EQ_OTHER_HEADER() { return getTokens(conllParser.EQ_OTHER_HEADER); }
		public TerminalNode EQ_OTHER_HEADER(int i) {
			return getToken(conllParser.EQ_OTHER_HEADER, i);
		}
		public List<TerminalNode> NL_OTHER_HEADER() { return getTokens(conllParser.NL_OTHER_HEADER); }
		public TerminalNode NL_OTHER_HEADER(int i) {
			return getToken(conllParser.NL_OTHER_HEADER, i);
		}
		public List<TerminalNode> TOKEN_ID() { return getTokens(conllParser.TOKEN_ID); }
		public TerminalNode TOKEN_ID(int i) {
			return getToken(conllParser.TOKEN_ID, i);
		}
		public List<TerminalNode> NL_TOKEN() { return getTokens(conllParser.NL_TOKEN); }
		public TerminalNode NL_TOKEN(int i) {
			return getToken(conllParser.NL_TOKEN, i);
		}
		public List<TerminalNode> TAB_TOKEN() { return getTokens(conllParser.TAB_TOKEN); }
		public TerminalNode TAB_TOKEN(int i) {
			return getToken(conllParser.TAB_TOKEN, i);
		}
		public List<TerminalNode> TOKEN_FIELD() { return getTokens(conllParser.TOKEN_FIELD); }
		public TerminalNode TOKEN_FIELD(int i) {
			return getToken(conllParser.TOKEN_FIELD, i);
		}
		public SentenceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sentence; }
	}

	public final SentenceContext sentence() throws RecognitionException {
		SentenceContext _localctx = new SentenceContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_sentence);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(34);
			match(SENT_HEADER);
			setState(35);
			match(EQ_SENT);
			setState(36);
			match(SENT_ID);
			setState(38); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(37);
					blankLines();
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(40); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
			setState(55);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OTHER_HEADER) {
				{
				{
				setState(42);
				match(OTHER_HEADER);
				setState(43);
				match(TXT_OTHER_HEADER);
				setState(44);
				match(EQ_OTHER_HEADER);
				setState(45);
				match(TXT_OTHER_HEADER);
				setState(46);
				match(NL_OTHER_HEADER);
				setState(50);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,5,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(47);
						blankLines();
						}
						} 
					}
					setState(52);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,5,_ctx);
				}
				}
				}
				setState(57);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(74);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==TOKEN_ID) {
				{
				{
				setState(58);
				match(TOKEN_ID);
				setState(61); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(59);
					match(TAB_TOKEN);
					setState(60);
					match(TOKEN_FIELD);
					}
					}
					setState(63); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==TAB_TOKEN );
				setState(65);
				match(NL_TOKEN);
				setState(69);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,8,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(66);
						blankLines();
						}
						} 
					}
					setState(71);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,8,_ctx);
				}
				}
				}
				setState(76);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
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
		"\u0004\u0001\u000fN\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0001\u0000\u0005\u0000\b\b\u0000\n\u0000\f\u0000\u000b"+
		"\t\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0005\u0000\u0011"+
		"\b\u0000\n\u0000\f\u0000\u0014\t\u0000\u0001\u0000\u0005\u0000\u0017\b"+
		"\u0000\n\u0000\f\u0000\u001a\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001"+
		"\u0004\u0001\u001f\b\u0001\u000b\u0001\f\u0001 \u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0004\u0002\'\b\u0002\u000b\u0002\f\u0002(\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005"+
		"\u00021\b\u0002\n\u0002\f\u00024\t\u0002\u0005\u00026\b\u0002\n\u0002"+
		"\f\u00029\t\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0004\u0002>\b\u0002"+
		"\u000b\u0002\f\u0002?\u0001\u0002\u0001\u0002\u0005\u0002D\b\u0002\n\u0002"+
		"\f\u0002G\t\u0002\u0005\u0002I\b\u0002\n\u0002\f\u0002L\t\u0002\u0001"+
		"\u0002\u0000\u0000\u0003\u0000\u0002\u0004\u0000\u0000T\u0000\t\u0001"+
		"\u0000\u0000\u0000\u0002\u001e\u0001\u0000\u0000\u0000\u0004\"\u0001\u0000"+
		"\u0000\u0000\u0006\b\u0003\u0002\u0001\u0000\u0007\u0006\u0001\u0000\u0000"+
		"\u0000\b\u000b\u0001\u0000\u0000\u0000\t\u0007\u0001\u0000\u0000\u0000"+
		"\t\n\u0001\u0000\u0000\u0000\n\f\u0001\u0000\u0000\u0000\u000b\t\u0001"+
		"\u0000\u0000\u0000\f\u0012\u0003\u0004\u0002\u0000\r\u000e\u0003\u0002"+
		"\u0001\u0000\u000e\u000f\u0003\u0004\u0002\u0000\u000f\u0011\u0001\u0000"+
		"\u0000\u0000\u0010\r\u0001\u0000\u0000\u0000\u0011\u0014\u0001\u0000\u0000"+
		"\u0000\u0012\u0010\u0001\u0000\u0000\u0000\u0012\u0013\u0001\u0000\u0000"+
		"\u0000\u0013\u0018\u0001\u0000\u0000\u0000\u0014\u0012\u0001\u0000\u0000"+
		"\u0000\u0015\u0017\u0003\u0002\u0001\u0000\u0016\u0015\u0001\u0000\u0000"+
		"\u0000\u0017\u001a\u0001\u0000\u0000\u0000\u0018\u0016\u0001\u0000\u0000"+
		"\u0000\u0018\u0019\u0001\u0000\u0000\u0000\u0019\u001b\u0001\u0000\u0000"+
		"\u0000\u001a\u0018\u0001\u0000\u0000\u0000\u001b\u001c\u0005\u0000\u0000"+
		"\u0001\u001c\u0001\u0001\u0000\u0000\u0000\u001d\u001f\u0005\u0005\u0000"+
		"\u0000\u001e\u001d\u0001\u0000\u0000\u0000\u001f \u0001\u0000\u0000\u0000"+
		" \u001e\u0001\u0000\u0000\u0000 !\u0001\u0000\u0000\u0000!\u0003\u0001"+
		"\u0000\u0000\u0000\"#\u0005\u0001\u0000\u0000#$\u0005\n\u0000\u0000$&"+
		"\u0005\u000b\u0000\u0000%\'\u0003\u0002\u0001\u0000&%\u0001\u0000\u0000"+
		"\u0000\'(\u0001\u0000\u0000\u0000(&\u0001\u0000\u0000\u0000()\u0001\u0000"+
		"\u0000\u0000)7\u0001\u0000\u0000\u0000*+\u0005\u0002\u0000\u0000+,\u0005"+
		"\u000e\u0000\u0000,-\u0005\r\u0000\u0000-.\u0005\u000e\u0000\u0000.2\u0005"+
		"\u000f\u0000\u0000/1\u0003\u0002\u0001\u00000/\u0001\u0000\u0000\u0000"+
		"14\u0001\u0000\u0000\u000020\u0001\u0000\u0000\u000023\u0001\u0000\u0000"+
		"\u000036\u0001\u0000\u0000\u000042\u0001\u0000\u0000\u00005*\u0001\u0000"+
		"\u0000\u000069\u0001\u0000\u0000\u000075\u0001\u0000\u0000\u000078\u0001"+
		"\u0000\u0000\u00008J\u0001\u0000\u0000\u000097\u0001\u0000\u0000\u0000"+
		":=\u0005\u0003\u0000\u0000;<\u0005\b\u0000\u0000<>\u0005\u0006\u0000\u0000"+
		"=;\u0001\u0000\u0000\u0000>?\u0001\u0000\u0000\u0000?=\u0001\u0000\u0000"+
		"\u0000?@\u0001\u0000\u0000\u0000@A\u0001\u0000\u0000\u0000AE\u0005\u0007"+
		"\u0000\u0000BD\u0003\u0002\u0001\u0000CB\u0001\u0000\u0000\u0000DG\u0001"+
		"\u0000\u0000\u0000EC\u0001\u0000\u0000\u0000EF\u0001\u0000\u0000\u0000"+
		"FI\u0001\u0000\u0000\u0000GE\u0001\u0000\u0000\u0000H:\u0001\u0000\u0000"+
		"\u0000IL\u0001\u0000\u0000\u0000JH\u0001\u0000\u0000\u0000JK\u0001\u0000"+
		"\u0000\u0000K\u0005\u0001\u0000\u0000\u0000LJ\u0001\u0000\u0000\u0000"+
		"\n\t\u0012\u0018 (27?EJ";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}