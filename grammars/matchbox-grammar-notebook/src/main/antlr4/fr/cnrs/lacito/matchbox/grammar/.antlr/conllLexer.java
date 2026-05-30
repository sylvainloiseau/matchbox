// Generated from /Users/sloiseau/workspace/notebook/src/main/antlr4/fr/lacito/notebook/grammar/conllLexer.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class conllLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		SENT_HEADER=1, OTHER_HEADER=2, TOKEN_ID=3, NUM=4, NL=5, TOKEN_FIELD=6, 
		NL_TOKEN=7, TAB_TOKEN=8, WS_SENT=9, EQ_SENT=10, SENT_ID=11, WS_OTHER_HEADER=12, 
		EQ_OTHER_HEADER=13, TXT_OTHER_HEADER=14, NL_OTHER_HEADER=15;
	public static final int
		IN_TOKEN=1, IN_SENT_HEADER=2, IN_OTHER_HEADER=3;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE", "IN_TOKEN", "IN_SENT_HEADER", "IN_OTHER_HEADER"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"SENT_HEADER", "OTHER_HEADER", "TOKEN_ID", "NUM", "NL", "TOKEN_FIELD", 
			"NL_TOKEN", "TAB_TOKEN", "WS_SENT", "EQ_SENT", "SENT_ID", "WS_OTHER_HEADER", 
			"EQ_OTHER_HEADER", "TXT_OTHER_HEADER", "NL_OTHER_HEADER"
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


	public conllLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "conllLexer.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	@Override
	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 0:
			return SENT_HEADER_sempred((RuleContext)_localctx, predIndex);
		case 1:
			return OTHER_HEADER_sempred((RuleContext)_localctx, predIndex);
		case 2:
			return TOKEN_ID_sempred((RuleContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean SENT_HEADER_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return getCharPositionInLine() == 1;
		}
		return true;
	}
	private boolean OTHER_HEADER_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return getCharPositionInLine() == 1;
		}
		return true;
	}
	private boolean TOKEN_ID_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return getCharPositionInLine() == 1;
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0000\u000f~\u0006\uffff\uffff\u0006\uffff\uffff\u0006\uffff\uffff"+
		"\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0005\u00028\b\u0002\n\u0002\f\u0002;\t\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0003\u0004B\b\u0004"+
		"\u0001\u0004\u0001\u0004\u0003\u0004F\b\u0004\u0001\u0005\u0004\u0005"+
		"I\b\u0005\u000b\u0005\f\u0005J\u0001\u0006\u0003\u0006N\b\u0006\u0001"+
		"\u0006\u0001\u0006\u0003\u0006R\b\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0007\u0001\u0007\u0001\b\u0004\bY\b\b\u000b\b\f\bZ\u0001\b\u0001\b\u0001"+
		"\t\u0001\t\u0001\n\u0004\nb\b\n\u000b\n\f\nc\u0001\n\u0001\n\u0001\u000b"+
		"\u0004\u000bi\b\u000b\u000b\u000b\f\u000bj\u0001\u000b\u0001\u000b\u0001"+
		"\f\u0001\f\u0001\r\u0004\rr\b\r\u000b\r\f\rs\u0001\u000e\u0003\u000ew"+
		"\b\u000e\u0001\u000e\u0001\u000e\u0003\u000e{\b\u000e\u0001\u000e\u0001"+
		"\u000e\u0000\u0000\u000f\u0004\u0001\u0006\u0002\b\u0003\n\u0004\f\u0005"+
		"\u000e\u0006\u0010\u0007\u0012\b\u0014\t\u0016\n\u0018\u000b\u001a\f\u001c"+
		"\r\u001e\u000e \u000f\u0004\u0000\u0001\u0002\u0003\u0004\u0001\u0000"+
		"09\u0002\u0000\t\n\r\r\u0001\u0000  \u0003\u0000\n\n\r\r==\u0086\u0000"+
		"\u0004\u0001\u0000\u0000\u0000\u0000\u0006\u0001\u0000\u0000\u0000\u0000"+
		"\b\u0001\u0000\u0000\u0000\u0000\n\u0001\u0000\u0000\u0000\u0000\f\u0001"+
		"\u0000\u0000\u0000\u0001\u000e\u0001\u0000\u0000\u0000\u0001\u0010\u0001"+
		"\u0000\u0000\u0000\u0001\u0012\u0001\u0000\u0000\u0000\u0002\u0014\u0001"+
		"\u0000\u0000\u0000\u0002\u0016\u0001\u0000\u0000\u0000\u0002\u0018\u0001"+
		"\u0000\u0000\u0000\u0003\u001a\u0001\u0000\u0000\u0000\u0003\u001c\u0001"+
		"\u0000\u0000\u0000\u0003\u001e\u0001\u0000\u0000\u0000\u0003 \u0001\u0000"+
		"\u0000\u0000\u0004\"\u0001\u0000\u0000\u0000\u0006/\u0001\u0000\u0000"+
		"\u0000\b4\u0001\u0000\u0000\u0000\n>\u0001\u0000\u0000\u0000\fE\u0001"+
		"\u0000\u0000\u0000\u000eH\u0001\u0000\u0000\u0000\u0010Q\u0001\u0000\u0000"+
		"\u0000\u0012U\u0001\u0000\u0000\u0000\u0014X\u0001\u0000\u0000\u0000\u0016"+
		"^\u0001\u0000\u0000\u0000\u0018a\u0001\u0000\u0000\u0000\u001ah\u0001"+
		"\u0000\u0000\u0000\u001cn\u0001\u0000\u0000\u0000\u001eq\u0001\u0000\u0000"+
		"\u0000 z\u0001\u0000\u0000\u0000\"#\u0005#\u0000\u0000#$\u0004\u0000\u0000"+
		"\u0000$%\u0005 \u0000\u0000%&\u0005s\u0000\u0000&\'\u0005e\u0000\u0000"+
		"\'(\u0005n\u0000\u0000()\u0005t\u0000\u0000)*\u0005_\u0000\u0000*+\u0005"+
		"i\u0000\u0000+,\u0005d\u0000\u0000,-\u0001\u0000\u0000\u0000-.\u0006\u0000"+
		"\u0000\u0000.\u0005\u0001\u0000\u0000\u0000/0\u0005#\u0000\u000001\u0004"+
		"\u0001\u0001\u000012\u0001\u0000\u0000\u000023\u0006\u0001\u0001\u0000"+
		"3\u0007\u0001\u0000\u0000\u000045\u0003\n\u0003\u000059\u0004\u0002\u0002"+
		"\u000068\u0003\n\u0003\u000076\u0001\u0000\u0000\u00008;\u0001\u0000\u0000"+
		"\u000097\u0001\u0000\u0000\u00009:\u0001\u0000\u0000\u0000:<\u0001\u0000"+
		"\u0000\u0000;9\u0001\u0000\u0000\u0000<=\u0006\u0002\u0002\u0000=\t\u0001"+
		"\u0000\u0000\u0000>?\u0007\u0000\u0000\u0000?\u000b\u0001\u0000\u0000"+
		"\u0000@B\u0005\r\u0000\u0000A@\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000"+
		"\u0000BC\u0001\u0000\u0000\u0000CF\u0005\n\u0000\u0000DF\u0005\r\u0000"+
		"\u0000EA\u0001\u0000\u0000\u0000ED\u0001\u0000\u0000\u0000F\r\u0001\u0000"+
		"\u0000\u0000GI\b\u0001\u0000\u0000HG\u0001\u0000\u0000\u0000IJ\u0001\u0000"+
		"\u0000\u0000JH\u0001\u0000\u0000\u0000JK\u0001\u0000\u0000\u0000K\u000f"+
		"\u0001\u0000\u0000\u0000LN\u0005\r\u0000\u0000ML\u0001\u0000\u0000\u0000"+
		"MN\u0001\u0000\u0000\u0000NO\u0001\u0000\u0000\u0000OR\u0005\n\u0000\u0000"+
		"PR\u0005\r\u0000\u0000QM\u0001\u0000\u0000\u0000QP\u0001\u0000\u0000\u0000"+
		"RS\u0001\u0000\u0000\u0000ST\u0006\u0006\u0003\u0000T\u0011\u0001\u0000"+
		"\u0000\u0000UV\u0005\t\u0000\u0000V\u0013\u0001\u0000\u0000\u0000WY\u0007"+
		"\u0002\u0000\u0000XW\u0001\u0000\u0000\u0000YZ\u0001\u0000\u0000\u0000"+
		"ZX\u0001\u0000\u0000\u0000Z[\u0001\u0000\u0000\u0000[\\\u0001\u0000\u0000"+
		"\u0000\\]\u0006\b\u0004\u0000]\u0015\u0001\u0000\u0000\u0000^_\u0005="+
		"\u0000\u0000_\u0017\u0001\u0000\u0000\u0000`b\b\u0003\u0000\u0000a`\u0001"+
		"\u0000\u0000\u0000bc\u0001\u0000\u0000\u0000ca\u0001\u0000\u0000\u0000"+
		"cd\u0001\u0000\u0000\u0000de\u0001\u0000\u0000\u0000ef\u0006\n\u0003\u0000"+
		"f\u0019\u0001\u0000\u0000\u0000gi\u0007\u0002\u0000\u0000hg\u0001\u0000"+
		"\u0000\u0000ij\u0001\u0000\u0000\u0000jh\u0001\u0000\u0000\u0000jk\u0001"+
		"\u0000\u0000\u0000kl\u0001\u0000\u0000\u0000lm\u0006\u000b\u0004\u0000"+
		"m\u001b\u0001\u0000\u0000\u0000no\u0005=\u0000\u0000o\u001d\u0001\u0000"+
		"\u0000\u0000pr\b\u0003\u0000\u0000qp\u0001\u0000\u0000\u0000rs\u0001\u0000"+
		"\u0000\u0000sq\u0001\u0000\u0000\u0000st\u0001\u0000\u0000\u0000t\u001f"+
		"\u0001\u0000\u0000\u0000uw\u0005\r\u0000\u0000vu\u0001\u0000\u0000\u0000"+
		"vw\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000x{\u0005\n\u0000\u0000"+
		"y{\u0005\r\u0000\u0000zv\u0001\u0000\u0000\u0000zy\u0001\u0000\u0000\u0000"+
		"{|\u0001\u0000\u0000\u0000|}\u0006\u000e\u0003\u0000}!\u0001\u0000\u0000"+
		"\u0000\u0010\u0000\u0001\u0002\u00039AEJMQZcjsvz\u0005\u0005\u0002\u0000"+
		"\u0005\u0003\u0000\u0005\u0001\u0000\u0004\u0000\u0000\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}