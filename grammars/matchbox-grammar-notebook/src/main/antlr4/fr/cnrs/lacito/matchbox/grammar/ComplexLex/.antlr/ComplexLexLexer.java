// Generated from /Users/sloiseau/workspace/notebook/src/main/antlr4/fr/lacito/matchbox/grammar/ComplexLex/ComplexLexLexer.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class ComplexLexLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		HEADING=1, OTHER_LINE=2, END=3, HEADWORD_START=4, HEADWORD_END=5, GRAM_START=6, 
		COLUMN=7, DEF_MARKER=8, EX_START=9, EX_END=10, EX_GLOS_SEP=11, CONTENT=12, 
		GRAM_CONTENT=13, GRAM_END=14;
	public static final int
		LEX_MODE=1, GRAM_MODE=2;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE", "LEX_MODE", "GRAM_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"HEADING", "OTHER_LINE", "END", "HEADWORD_START", "HEADWORD_END", "GRAM_START", 
			"COLUMN", "DEF_MARKER", "EX_START", "EX_END", "EX_GLOS_SEP", "CONTENT", 
			"GRAM_CONTENT", "GRAM_END"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'#LEX '", null, null, "'\\u2039'", "'\\u203A'", "'('", "':'", 
			"'\\u2018'", "'\\u00AB'", "'\\u00BB'", "'/'", null, null, "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "HEADING", "OTHER_LINE", "END", "HEADWORD_START", "HEADWORD_END", 
			"GRAM_START", "COLUMN", "DEF_MARKER", "EX_START", "EX_END", "EX_GLOS_SEP", 
			"CONTENT", "GRAM_CONTENT", "GRAM_END"
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


	public ComplexLexLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "ComplexLexLexer.g4"; }

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

	public static final String _serializedATN =
		"\u0004\u0000\u000eR\u0006\uffff\uffff\u0006\uffff\uffff\u0006\uffff\uffff"+
		"\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002\u0007\u0002"+
		"\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002\u0005\u0007\u0005"+
		"\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002\b\u0007\b\u0002"+
		"\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002\f\u0007\f\u0002"+
		"\r\u0007\r\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0004\u0001)\b\u0001"+
		"\u000b\u0001\f\u0001*\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001"+
		"\n\u0001\u000b\u0004\u000bF\b\u000b\u000b\u000b\f\u000bG\u0001\f\u0004"+
		"\fK\b\f\u000b\f\f\fL\u0001\r\u0001\r\u0001\r\u0001\r\u0000\u0000\u000e"+
		"\u0003\u0001\u0005\u0002\u0007\u0003\t\u0004\u000b\u0005\r\u0006\u000f"+
		"\u0007\u0011\b\u0013\t\u0015\n\u0017\u000b\u0019\f\u001b\r\u001d\u000e"+
		"\u0003\u0000\u0001\u0002\u0003\u0002\u0000\n\n\r\r\u0007\u0000\n\n\r\r"+
		"//\u00ab\u00ab\u00bb\u00bb\u2018\u2018\u2039\u203a\u0003\u0000\n\n\r\r"+
		"))R\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001\u0000\u0000"+
		"\u0000\u0001\u0007\u0001\u0000\u0000\u0000\u0001\t\u0001\u0000\u0000\u0000"+
		"\u0001\u000b\u0001\u0000\u0000\u0000\u0001\r\u0001\u0000\u0000\u0000\u0001"+
		"\u000f\u0001\u0000\u0000\u0000\u0001\u0011\u0001\u0000\u0000\u0000\u0001"+
		"\u0013\u0001\u0000\u0000\u0000\u0001\u0015\u0001\u0000\u0000\u0000\u0001"+
		"\u0017\u0001\u0000\u0000\u0000\u0001\u0019\u0001\u0000\u0000\u0000\u0002"+
		"\u001b\u0001\u0000\u0000\u0000\u0002\u001d\u0001\u0000\u0000\u0000\u0003"+
		"\u001f\u0001\u0000\u0000\u0000\u0005(\u0001\u0000\u0000\u0000\u0007.\u0001"+
		"\u0000\u0000\u0000\t2\u0001\u0000\u0000\u0000\u000b4\u0001\u0000\u0000"+
		"\u0000\r6\u0001\u0000\u0000\u0000\u000f:\u0001\u0000\u0000\u0000\u0011"+
		"<\u0001\u0000\u0000\u0000\u0013>\u0001\u0000\u0000\u0000\u0015@\u0001"+
		"\u0000\u0000\u0000\u0017B\u0001\u0000\u0000\u0000\u0019E\u0001\u0000\u0000"+
		"\u0000\u001bJ\u0001\u0000\u0000\u0000\u001dN\u0001\u0000\u0000\u0000\u001f"+
		" \u0005#\u0000\u0000 !\u0005L\u0000\u0000!\"\u0005E\u0000\u0000\"#\u0005"+
		"X\u0000\u0000#$\u0005 \u0000\u0000$%\u0001\u0000\u0000\u0000%&\u0006\u0000"+
		"\u0000\u0000&\u0004\u0001\u0000\u0000\u0000\')\b\u0000\u0000\u0000(\'"+
		"\u0001\u0000\u0000\u0000)*\u0001\u0000\u0000\u0000*(\u0001\u0000\u0000"+
		"\u0000*+\u0001\u0000\u0000\u0000+,\u0001\u0000\u0000\u0000,-\u0007\u0000"+
		"\u0000\u0000-\u0006\u0001\u0000\u0000\u0000./\u0007\u0000\u0000\u0000"+
		"/0\u0001\u0000\u0000\u000001\u0006\u0002\u0001\u00001\b\u0001\u0000\u0000"+
		"\u000023\u0005\u2039\u0000\u00003\n\u0001\u0000\u0000\u000045\u0005\u203a"+
		"\u0000\u00005\f\u0001\u0000\u0000\u000067\u0005(\u0000\u000078\u0001\u0000"+
		"\u0000\u000089\u0006\u0005\u0002\u00009\u000e\u0001\u0000\u0000\u0000"+
		":;\u0005:\u0000\u0000;\u0010\u0001\u0000\u0000\u0000<=\u0005\u2018\u0000"+
		"\u0000=\u0012\u0001\u0000\u0000\u0000>?\u0005\u00ab\u0000\u0000?\u0014"+
		"\u0001\u0000\u0000\u0000@A\u0005\u00bb\u0000\u0000A\u0016\u0001\u0000"+
		"\u0000\u0000BC\u0005/\u0000\u0000C\u0018\u0001\u0000\u0000\u0000DF\b\u0001"+
		"\u0000\u0000ED\u0001\u0000\u0000\u0000FG\u0001\u0000\u0000\u0000GE\u0001"+
		"\u0000\u0000\u0000GH\u0001\u0000\u0000\u0000H\u001a\u0001\u0000\u0000"+
		"\u0000IK\b\u0002\u0000\u0000JI\u0001\u0000\u0000\u0000KL\u0001\u0000\u0000"+
		"\u0000LJ\u0001\u0000\u0000\u0000LM\u0001\u0000\u0000\u0000M\u001c\u0001"+
		"\u0000\u0000\u0000NO\u0005)\u0000\u0000OP\u0001\u0000\u0000\u0000PQ\u0006"+
		"\r\u0001\u0000Q\u001e\u0001\u0000\u0000\u0000\u0006\u0000\u0001\u0002"+
		"*GL\u0003\u0005\u0001\u0000\u0004\u0000\u0000\u0005\u0002\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}