package fr.cnrs.lacito.matchbox.corpus.gloss;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The parts of the gloss of one morpheme, after the Leipzig Glossing Rules: {@code .} (rule 4),
 * {@code _} (4A, inside a part: several words), {@code ;} {@code :} {@code \} {@code >} (4B–4E)
 * separate parts; a part is grammatical (no lower-case letter: {@code 3SG}, {@code PRES}), lexical
 * ({@code believe}) or non-overt ({@code [3SG]}, {@code (F)}, {@code Ø}). Pure.
 */
public final class GlossParts {

    /** What a part of a gloss is. */
    public enum Kind { GRAMMATICAL, LEXICAL, NON_OVERT }

    public record Part(String text, Kind kind) {}

    private GlossParts() {}

    public static List<Part> of(String gloss) {
        List<Part> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < gloss.length(); i++) {
            char c = gloss.charAt(i);
            if (c == '[' || c == '(') depth++;
            if (c == ']' || c == ')') depth = Math.max(0, depth - 1);
            if (depth == 0 && (c == '.' || c == ';' || c == ':' || c == '\\' || c == '>')) {
                add(parts, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        add(parts, current.toString());
        return parts;
    }

    private static void add(List<Part> parts, String text) {
        String t = text.strip();
        if (t.isEmpty()) return;
        Kind kind;
        if (t.startsWith("[") || t.startsWith("(") || t.equals("Ø") || t.equals("∅")) {
            kind = Kind.NON_OVERT;
        } else if (t.codePoints().anyMatch(Character::isLowerCase)) {
            kind = Kind.LEXICAL;
        } else if (t.codePoints().anyMatch(Character::isLetterOrDigit)) {
            kind = Kind.GRAMMATICAL;
        } else {
            return;
        }
        parts.add(new Part(t, kind));
    }

    /** The lexical parts joined by a space, {@code _} read as a space ({@code come.out} → "come out"); empty if none. */
    public static String lexicalGloss(String gloss) {
        return of(gloss).stream().filter(p -> p.kind() == Kind.LEXICAL)
            .map(p -> p.text().replace('_', ' ').strip())
            .collect(Collectors.joining(" "));
    }

    /** The grammatical parts ({@code 3SG}, {@code PRES} for {@code be\3SG.PRES}). */
    public static List<String> grammatical(String gloss) {
        return of(gloss).stream().filter(p -> p.kind() == Kind.GRAMMATICAL).map(Part::text).toList();
    }
}
