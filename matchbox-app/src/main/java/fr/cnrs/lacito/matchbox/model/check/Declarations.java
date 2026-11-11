package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;

/**
 * The lines {@code p /form/gloss} ("upsert the gloss on the form", a notation to be read later)
 * inserted above an IGT to declare (form, gloss) pairs missing from the dictionary: after the
 * {@code p} lines already there, with the line ending of the text; a pair already declared there is
 * not inserted again. Pure.
 */
public final class Declarations {

    private Declarations() {}

    public static String line(String form, String gloss) {
        return "p /" + form + "/" + gloss;
    }

    /** True if {@code p /form/gloss} is in the run of non-blank lines right above {@code offset}. */
    public static boolean declared(String content, int offset, String form, String gloss) {
        String wanted = line(form, gloss);
        int pos = offset; // the start of the IGT's line
        while (pos > 0) {
            // the line before pos, without its line ending
            int end = pos;
            if (end > 0 && content.charAt(end - 1) == '\n') end--;
            if (end > 0 && content.charAt(end - 1) == '\r') end--;
            int start = end;
            while (start > 0 && content.charAt(start - 1) != '\n' && content.charAt(start - 1) != '\r') start--;
            String text = content.substring(start, end).strip();
            if (!text.startsWith("p ")) return false; // a blank line or another line ends the run
            if (text.equals(wanted)) return true;
            pos = start;
        }
        return false;
    }

    /** The insertion of the lines of these pairs (form, gloss, form, gloss…) at the start of the IGT's line. */
    public static TextEdit insert(String content, int igtStart, List<String[]> pairs) {
        String eol = content.contains("\r\n") ? "\r\n" : content.contains("\r") && !content.contains("\n") ? "\r" : "\n";
        StringBuilder s = new StringBuilder();
        for (String[] p : pairs) s.append(line(p[0], p[1])).append(eol);
        return TextEdit.insert(igtStart, s.toString());
    }
}
