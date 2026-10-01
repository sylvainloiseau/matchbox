package fr.cnrs.lacito.matchbox.assist.suggestion;

import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic corrections derived from a parse error, without AI: insert an expected literal
 * token, or delete the offending input. Candidates must still be checked with
 * {@link fr.cnrs.lacito.matchbox.assist.validation.EditValidator}, since a generic fix can be wrong.
 */
public final class GrammarQuickFixes {

    /** Maximum number of insertions proposed for one error. */
    static final int MAX_INSERTIONS = 3;

    private GrammarQuickFixes() {}

    /** A candidate correction: a title and its edit. */
    public record Fix(String title, TextEdit edit) {}

    public static List<Fix> candidates(String text, ParseError error) {
        List<Fix> fixes = new ArrayList<>();
        int start = error.getStartIndex();
        if (start < 0 || start > text.length()) {
            return fixes;
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        boolean missing = message.startsWith("missing ");
        boolean mismatched = message.startsWith("mismatched input");
        boolean extraneous = message.startsWith("extraneous input");
        boolean lexer = message.startsWith("token recognition error");

        if (missing || mismatched || extraneous) {
            int n = 0;
            for (String token : error.getExpected()) {
                if (n == MAX_INSERTIONS) break;
                String literal = literal(token);
                if (literal != null && !literal.isEmpty()) {
                    fixes.add(new Fix("Insert " + display(literal), TextEdit.insert(start, literal)));
                    n++;
                }
            }
        }
        int stop = error.getStopIndex();
        if ((extraneous || mismatched || lexer) && stop >= start && stop < text.length()) {
            String offending = text.substring(start, stop + 1);
            fixes.add(new Fix("Delete " + display(offending), TextEdit.replace(text, start, stop + 1, "")));
        }
        return fixes;
    }

    /** The text of a token display name if it is a quoted literal ({@code 'x'}), unescaped; else null. */
    public static String literal(String displayName) {
        if (displayName == null || displayName.length() < 2 || !displayName.startsWith("'") || !displayName.endsWith("'")) {
            return null;
        }
        return unescape(displayName.substring(1, displayName.length() - 1));
    }

    /** Decodes the escapes of ANTLR literals ({@code \n}, {@code \t}, {@code \'}, {@code \\}, {@code \}{@code uXXXX}). */
    static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 == s.length()) {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    // four hex digits after the 'u'
                    if (i + 4 < s.length() && s.substring(i + 1, i + 5).matches("\\p{XDigit}{4}")) {
                        b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    } else {
                        b.append("\\u");
                    }
                }
                default -> b.append(n);
            }
        }
        return b.toString();
    }

    /** A short, visible rendering of a text (newlines and tabs shown as escapes). */
    public static String display(String s) {
        String shown = s.replace("\n", "↵").replace("\t", "⇥");
        if (shown.length() > 30) {
            shown = shown.substring(0, 30) + "…";
        }
        return "'" + shown + "'";
    }
}
