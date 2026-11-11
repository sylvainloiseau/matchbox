package fr.cnrs.lacito.matchbox.corpus.igt;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * The width of a text in a monospaced font: one column per grapheme (a letter and its combining
 * diacritics, as in IPA {@code t̠}, {@code ʊ̯}), two for East Asian wide and fullwidth characters. Pure.
 */
public final class DisplayWidth {

    private DisplayWidth() {}

    public static int of(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        int width = 0;
        int start = graphemes.first();
        for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
            int cp = text.codePointAt(start);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) continue;
            width += wide(cp) ? 2 : 1;
        }
        return width;
    }

    private static boolean wide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F) || (cp >= 0x2E80 && cp <= 0xA4CF) || (cp >= 0xAC00 && cp <= 0xD7A3)
            || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0xFE30 && cp <= 0xFE4F) || (cp >= 0xFF00 && cp <= 0xFF60)
            || (cp >= 0xFFE0 && cp <= 0xFFE6) || (cp >= 0x20000 && cp <= 0x3FFFD);
    }
}
