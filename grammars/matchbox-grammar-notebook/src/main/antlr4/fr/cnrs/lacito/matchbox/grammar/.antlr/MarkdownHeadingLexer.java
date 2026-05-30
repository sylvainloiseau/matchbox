// Generated from /Users/sloiseau/workspace/notebook/src/main/antlr4/fr/lacito/notebook/grammar/MarkdownHeadingLexer.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class MarkdownHeadingLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		HEADING=1, HEADING_CONTENT=2, YAML_START=3, YAML_END=4, YAML_SEP=5, YAML_KEY=6, 
		YAML_VALUE=7, OTHER_LINE=8, NL=9, WS=10;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"HEADING", "HEADING_CONTENT", "YAML_START", "YAML_END", "YAML_SEP", "YAML_KEY", 
			"YAML_VALUE", "OTHER_LINE", "NL", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, "':'", null, null, null, null, "' '"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "HEADING", "HEADING_CONTENT", "YAML_START", "YAML_END", "YAML_SEP", 
			"YAML_KEY", "YAML_VALUE", "OTHER_LINE", "NL", "WS"
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


	public MarkdownHeadingLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "MarkdownHeadingLexer.g4"; }

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
			return HEADING_sempred((RuleContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean HEADING_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return getCharPositionInLine() == 1;
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0000\nB\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0003\u0000\u0019\b\u0000\u0001\u0001\u0004\u0001\u001c\b\u0001"+
		"\u000b\u0001\f\u0001\u001d\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0004\u0005"+
		"/\b\u0005\u000b\u0005\f\u00050\u0001\u0006\u0004\u00064\b\u0006\u000b"+
		"\u0006\f\u00065\u0001\u0007\u0004\u00079\b\u0007\u000b\u0007\f\u0007:"+
		"\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0000\u0000\n"+
		"\u0001\u0001\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r"+
		"\u0007\u000f\b\u0011\t\u0013\n\u0001\u0000\u0002\u0002\u0000\n\n\r\r\u0003"+
		"\u0000\n\n\r\r::F\u0000\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001"+
		"\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001"+
		"\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000"+
		"\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000"+
		"\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000"+
		"\u0000\u0001\u0015\u0001\u0000\u0000\u0000\u0003\u001b\u0001\u0000\u0000"+
		"\u0000\u0005\u001f\u0001\u0000\u0000\u0000\u0007%\u0001\u0000\u0000\u0000"+
		"\t+\u0001\u0000\u0000\u0000\u000b.\u0001\u0000\u0000\u0000\r3\u0001\u0000"+
		"\u0000\u0000\u000f8\u0001\u0000\u0000\u0000\u0011>\u0001\u0000\u0000\u0000"+
		"\u0013@\u0001\u0000\u0000\u0000\u0015\u0016\u0005#\u0000\u0000\u0016\u0018"+
		"\u0004\u0000\u0000\u0000\u0017\u0019\u0005#\u0000\u0000\u0018\u0017\u0001"+
		"\u0000\u0000\u0000\u0018\u0019\u0001\u0000\u0000\u0000\u0019\u0002\u0001"+
		"\u0000\u0000\u0000\u001a\u001c\b\u0000\u0000\u0000\u001b\u001a\u0001\u0000"+
		"\u0000\u0000\u001c\u001d\u0001\u0000\u0000\u0000\u001d\u001b\u0001\u0000"+
		"\u0000\u0000\u001d\u001e\u0001\u0000\u0000\u0000\u001e\u0004\u0001\u0000"+
		"\u0000\u0000\u001f \u0005-\u0000\u0000 !\u0005-\u0000\u0000!\"\u0005-"+
		"\u0000\u0000\"#\u0001\u0000\u0000\u0000#$\u0007\u0000\u0000\u0000$\u0006"+
		"\u0001\u0000\u0000\u0000%&\u0005-\u0000\u0000&\'\u0005-\u0000\u0000\'"+
		"(\u0005-\u0000\u0000()\u0001\u0000\u0000\u0000)*\u0007\u0000\u0000\u0000"+
		"*\b\u0001\u0000\u0000\u0000+,\u0005:\u0000\u0000,\n\u0001\u0000\u0000"+
		"\u0000-/\b\u0001\u0000\u0000.-\u0001\u0000\u0000\u0000/0\u0001\u0000\u0000"+
		"\u00000.\u0001\u0000\u0000\u000001\u0001\u0000\u0000\u00001\f\u0001\u0000"+
		"\u0000\u000024\b\u0001\u0000\u000032\u0001\u0000\u0000\u000045\u0001\u0000"+
		"\u0000\u000053\u0001\u0000\u0000\u000056\u0001\u0000\u0000\u00006\u000e"+
		"\u0001\u0000\u0000\u000079\b\u0000\u0000\u000087\u0001\u0000\u0000\u0000"+
		"9:\u0001\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000\u0000"+
		"\u0000;<\u0001\u0000\u0000\u0000<=\u0007\u0000\u0000\u0000=\u0010\u0001"+
		"\u0000\u0000\u0000>?\u0007\u0000\u0000\u0000?\u0012\u0001\u0000\u0000"+
		"\u0000@A\u0005 \u0000\u0000A\u0014\u0001\u0000\u0000\u0000\u0006\u0000"+
		"\u0018\u001d05:\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}