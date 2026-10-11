package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The matches of a query in a text, with the options of the find bar: match case, whole word,
 * regular expression. An invalid regular expression gives no match and a message, never an
 * exception. Pure.
 */
public final class FindModel {

    /** A match: {@code [start, end)}. */
    public record Match(int start, int end) {}

    /** The matches of a query, or why there are none. */
    public record Result(List<Match> matches, String problem) {
        public Result {
            matches = List.copyOf(matches);
        }

        /** Index of the first match at or after {@code offset} (wrapping to 0); -1 if none. */
        public int nextFrom(int offset) {
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i).start() >= offset) return i;
            }
            return matches.isEmpty() ? -1 : 0;
        }

        /** Index of the last match before {@code offset} (wrapping to the last); -1 if none. */
        public int previousFrom(int offset) {
            for (int i = matches.size() - 1; i >= 0; i--) {
                if (matches.get(i).start() < offset) return i;
            }
            return matches.isEmpty() ? -1 : matches.size() - 1;
        }
    }

    /** The options of a search. */
    public record Query(String text, boolean matchCase, boolean wholeWord, boolean regex) {}

    private FindModel() {
    }

    /** The pattern of a query; throws {@link PatternSyntaxException} for an invalid regular expression. */
    static Pattern pattern(Query q) {
        String body = q.regex() ? q.text() : Pattern.quote(q.text());
        if (q.wholeWord()) body = "(?<![\\p{L}\\p{N}_])(?:" + body + ")(?![\\p{L}\\p{N}_])";
        int flags = Pattern.UNICODE_CASE | (q.matchCase() ? 0 : Pattern.CASE_INSENSITIVE);
        return Pattern.compile(body, flags);
    }

    public static Result find(String text, Query q) {
        if (q.text() == null || q.text().isEmpty()) return new Result(List.of(), null);
        Pattern p;
        try {
            p = pattern(q);
        } catch (PatternSyntaxException e) {
            return new Result(List.of(), "Invalid regular expression: " + e.getDescription());
        }
        List<Match> matches = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            // empty matches (e.g. "^") cannot be shown or replaced usefully
            if (m.end() > m.start()) matches.add(new Match(m.start(), m.end()));
        }
        return new Result(matches, null);
    }

    /**
     * The replacement of a match: with a regular expression, {@code $1}… refer to its groups.
     *
     * @throws IllegalArgumentException for an invalid group reference
     */
    public static TextEdit replace(String text, Query q, Match match, String replacement) {
        String value = replacement;
        if (q.regex()) {
            Matcher m = pattern(q).matcher(text);
            m.region(match.start(), text.length());
            if (m.lookingAt() && m.end() == match.end()) {
                StringBuilder sb = new StringBuilder();
                m.appendReplacement(sb, replacement);
                value = sb.substring(match.start());
            }
        }
        return new TextEdit(match.start(), match.end(), text.substring(match.start(), match.end()), value);
    }

    /** The replacements of every match. */
    public static List<TextEdit> replaceAll(String text, Query q, String replacement) {
        List<TextEdit> edits = new ArrayList<>();
        for (Match m : find(text, q).matches()) edits.add(replace(text, q, m, replacement));
        return edits;
    }
}
