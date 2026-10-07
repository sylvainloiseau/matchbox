/*
 * Lines of a Markdown file relevant to its structure: ATX headings, Setext headings, YAML metadata
 * blocks (file header, section metadata) and fenced code blocks. Every token is a whole line; all
 * other lines are TEXT_LINE or BLANK_LINE and are ignored by the parser.
 *
 * A few decisions depend on the context and are made by the actions below:
 * - a line of '-' is a Setext underline after a single text line, the opening of a metadata block
 *   on line 1 or right after a heading (if a closing '---' follows before the next heading: inside
 *   a block, a '#' line is a YAML comment unless a blank line precedes it), and a separator
 *   otherwise;
 * - inside a fenced code block nothing is recognised.
 */
lexer grammar MarkdownHeadingLexer;

tokens { SETEXT_UNDERLINE, META_OPEN, RULE_LINE, FENCE_CLOSE }

@members {
    /** Kind of the previous line, for Setext underlines. */
    private enum Previous { START, BLANK, SINGLE_TEXT, MULTI_TEXT, IGT_DELIMITER, STRUCTURE }

    private Previous previous = Previous.START;
    /** True right after a heading (blank lines allowed): a metadata block may open. */
    private boolean metadataAllowed = false;
    private char fenceChar;
    private int fenceLength;

    private static String stripEol(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
    }

    private static boolean isAtxHeading(String line) {
        return line.matches(" {0,3}#{1,6}([ \t].*)?");
    }

    private void heading() {
        previous = Previous.STRUCTURE;
        metadataAllowed = true;
    }

    private void blank() {
        // keeps metadataAllowed: the block may follow its heading after blank lines
        previous = Previous.BLANK;
    }

    private void text() {
        String line = stripEol(getText()).strip();
        metadataAllowed = false;
        if (line.equals(":::")) {
            previous = Previous.IGT_DELIMITER;
        } else if (previous == Previous.START || previous == Previous.BLANK || previous == Previous.STRUCTURE) {
            previous = Previous.SINGLE_TEXT;
        } else {
            previous = Previous.MULTI_TEXT;
        }
    }

    private void dashLine() {
        if (previous == Previous.SINGLE_TEXT) {
            setType(SETEXT_UNDERLINE);
            heading();
            return;
        }
        boolean header = _tokenStartCharIndex == 0;
        if (stripEol(getText()).stripTrailing().equals("---") && (header || metadataAllowed) && closedAhead()) {
            setType(META_OPEN);
            pushMode(META);
            return;
        }
        setType(RULE_LINE);
        previous = Previous.STRUCTURE;
        metadataAllowed = false;
    }

    private void equalsLine() {
        if (previous == Previous.SINGLE_TEXT) {
            setType(SETEXT_UNDERLINE);
            heading();
        } else {
            setType(TEXT_LINE);
            text();
        }
    }

    /**
     * True if a "---" line follows before the next heading. Inside a block a "#" line is a YAML
     * comment, unless a blank line precedes it: then it is a heading, and the block is not closed.
     */
    private boolean closedAhead() {
        StringBuilder line = new StringBuilder();
        boolean previousBlank = false;
        for (int i = 1; ; i++) {
            int c = _input.LA(i);
            if (c == org.antlr.v4.runtime.IntStream.EOF || c == '\n' || c == '\r') {
                String l = line.toString();
                if (l.stripTrailing().equals("---")) return true;
                if (previousBlank && isAtxHeading(l)) return false;
                if (c == org.antlr.v4.runtime.IntStream.EOF) return false;
                if (c == '\r' && _input.LA(i + 1) == '\n') i++;
                previousBlank = l.isBlank();
                line.setLength(0);
            } else {
                line.append((char) c);
            }
        }
    }

    private void metaClosed() {
        previous = Previous.STRUCTURE;
        metadataAllowed = false;
    }

    private void fenceOpen() {
        String line = stripEol(getText()).stripLeading();
        fenceChar = line.charAt(0);
        int n = 0;
        while (n < line.length() && line.charAt(n) == fenceChar) n++;
        fenceLength = n;
        metadataAllowed = false;
    }

    private void fenceLine() {
        String line = stripEol(getText());
        String trimmed = line.strip();
        int indent = line.length() - line.stripLeading().length();
        boolean closes = indent <= 3 && trimmed.length() >= fenceLength
            && trimmed.chars().allMatch(c -> c == fenceChar);
        if (closes) {
            setType(FENCE_CLOSE);
            popMode();
            previous = Previous.STRUCTURE;
        } else {
            skip();
        }
    }
}

ATX_HEADING : INDENT? HASHES ([ \t] ~[\r\n]*)? EOL? { heading(); } ;
FENCE_OPEN  : INDENT? ('```' '`'* | '~~~' '~'*) ~[\r\n]* EOL? { fenceOpen(); } -> pushMode(FENCE) ;
DASH_LINE   : INDENT? '-'+ [ \t]* EOL? { dashLine(); } ;
EQUALS_LINE : INDENT? '='+ [ \t]* EOL? { equalsLine(); } ;
BLANK_LINE  : [ \t]* EOL { blank(); } ;
TEXT_LINE   : ~[\r\n]+ EOL? { text(); } ;

mode META;

META_CLOSE   : '---' [ \t]* EOL? { metaClosed(); } -> popMode ;
META_IGNORED : [ \t]* ('#' ~[\r\n]*)? EOL -> skip ;
META_ENTRY   : KEY ':' ~[\r\n]* EOL? ;
META_INVALID : ~[\r\n]+ EOL? ;

mode FENCE;

FENCE_LINE : (~[\r\n]+ EOL? | EOL) { fenceLine(); } ;

fragment INDENT : ' ' ' '? ' '? ;
fragment HASHES : '#' '#'? '#'? '#'? '#'? '#'? ;
fragment KEY    : ~[ \t#\-:\r\n] ~[:\r\n]* ;
fragment EOL    : '\r'? '\n' | '\r' ;
