package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a line of an IGT into words (on runs of whitespace) and words into morphemes, after the
 * Leipzig Glossing Rules: {@code -} affix, {@code =} clitic (rule 2), {@code ~} reduplication (rule
 * 10), {@code <…>} infix (rule 9: {@code s<um>ulat} is the host {@code sulat} and the infix
 * {@code um}). {@code .}, {@code _}, {@code \}, {@code :}, {@code ;}, {@code >}, {@code [ ]} and
 * {@code ( )} are not boundaries: they belong to the morpheme (its gloss). Pure.
 */
public final class Segmenter {

    private static final Pattern INFIX = Pattern.compile("<[^<>\\s]*>");

    private Segmenter() {}

    private static final Pattern TIMELINE = Pattern.compile("[0-9\\s=\\-~]*[0-9][0-9\\s=\\-~]*");

    private static final Pattern TRAILING_TAGS =
        Pattern.compile("(?:^|\\s+)(?:#[\"\u201C][^\"\u201D\\r\\n]*[\"\u201D]\\s*)+$");

    /**
     * The end of the content of a line: tags written after it ({@code 'I went' #"verb"}), or a line
     * made only of tags, are not part of the IGT line (they are read by a tag grammar and apply to
     * the IGT).
     */
    public static int contentEnd(String line) {
        var m = TRAILING_TAGS.matcher(line);
        return m.find() ? m.start() : line.length();
    }

    /**
     * What the characters of a line make it: a translation between single quotes ({@code '…'},
     * {@code ‘…’}), a timeline (digits, whitespace and boundary symbols only), else a text line.
     */
    public static fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content contentOf(String line) {
        String t = line.strip();
        if (t.length() >= 2 && (t.charAt(0) == '\'' || t.charAt(0) == '\u2018')
            && (t.charAt(t.length() - 1) == '\'' || t.charAt(t.length() - 1) == '\u2019')) {
            return fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TRANSLATION;
        }
        if (TIMELINE.matcher(t).matches()) {
            return fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TIMELINE;
        }
        return fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TEXT;
    }

    /** True if the text has a morpheme boundary, i.e. would be segmented at the morpheme level. */
    public static boolean hasBoundary(String text) {
        for (String word : text.strip().split("\\s+")) {
            if (word.indexOf('-') >= 0 || word.indexOf('=') >= 0 || word.indexOf('~') >= 0
                || INFIX.matcher(word).find()) {
                return true;
            }
        }
        return false;
    }

    /** The segmentation the characters suggest. */
    public static Level levelOf(String text) {
        return hasBoundary(text) ? Level.MORPHEME : Level.WORD;
    }

    /**
     * @param text  the line, without line ending
     * @param start offset of the first character of {@code text} in the whole content
     * @param line  1-based line number (for the spans)
     */
    public static List<Word> words(TextId id, String text, int start, int line, Level level) {
        List<Word> words = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            int from = i;
            while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
            if (i > from) {
                String w = text.substring(from, i);
                TextCoordinate span = span(id, start + from, start + i - 1, line);
                List<Morpheme> morphemes = level == Level.WORD
                    ? List.of(new Morpheme(w, Morpheme.Boundary.NONE, span))
                    : morphemes(id, w, start + from, line);
                words.add(new Word(w, morphemes, span));
            }
        }
        return words;
    }

    private static List<Morpheme> morphemes(TextId id, String word, int start, int line) {
        List<Morpheme> result = new ArrayList<>();
        Morpheme.Boundary boundary = Morpheme.Boundary.NONE;
        int pieceStart = 0;
        int depth = 0;
        for (int i = 0; i <= word.length(); i++) {
            char c = i < word.length() ? word.charAt(i) : 0;
            if (c == '<') depth++;
            if (c == '>') depth = Math.max(0, depth - 1);
            boolean end = i == word.length();
            Morpheme.Boundary next = end || depth > 0 ? null : switch (c) {
                case '-' -> Morpheme.Boundary.AFFIX;
                case '=' -> Morpheme.Boundary.CLITIC;
                case '~' -> Morpheme.Boundary.REDUPLICATION;
                default -> null;
            };
            if (end || next != null) {
                piece(id, word.substring(pieceStart, i), start + pieceStart, line, boundary, result);
                boundary = next;
                pieceStart = i + 1;
            }
        }
        return result;
    }

    /** A piece between boundaries: its host, then its infixes. */
    private static void piece(TextId id, String piece, int start, int line, Morpheme.Boundary boundary,
                              List<Morpheme> out) {
        var m = INFIX.matcher(piece);
        StringBuilder host = new StringBuilder();
        List<Morpheme> infixes = new ArrayList<>();
        int last = 0;
        while (m.find()) {
            host.append(piece, last, m.start());
            infixes.add(new Morpheme(piece.substring(m.start() + 1, m.end() - 1), Morpheme.Boundary.INFIX,
                span(id, start + m.start() + 1, start + m.end() - 2, line)));
            last = m.end();
        }
        host.append(piece.substring(last));
        out.add(new Morpheme(host.toString(), boundary, span(id, start, start + piece.length() - 1, line)));
        out.addAll(infixes);
    }

    private static TextCoordinate span(TextId id, int start, int stop, int line) {
        return new TextCoordinate(id, start, Math.max(start - 1, stop), line, line);
    }
}
