package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The value of the {@code setting} metadata of an IGT: the roles of its text lines, in order, each
 * a letter — {@code o} orthographic line, {@code t} transcription, {@code g} gloss — optionally
 * followed by a level ({@code :w} or {@code :m}, not for {@code o}), a name between single quotes
 * and a language after {@code @}: {@code o@fra,t'IPA'@fra:m,g'morpheme'@en}. Pure.
 */
public final class SettingSpec {

    /** One text line of the setting. */
    public record Entry(LineRole role, Optional<Level> level, Optional<String> name, Optional<String> language) {}

    /** The entries, or the first error ("column 7: …"). */
    public record Result(List<Entry> entries, Optional<String> error) {
        public boolean isValid() {
            return error.isEmpty();
        }
    }

    private SettingSpec() {}

    public static Result parse(String value) {
        List<Entry> entries = new ArrayList<>();
        int i = 0;
        String v = value;
        while (true) {
            while (i < v.length() && v.charAt(i) == ' ') i++;
            if (i >= v.length()) return error(entries, i, "a line specification (o, t or g) is missing");
            char letter = v.charAt(i);
            LineRole role = switch (letter) {
                case 'o', 'O' -> LineRole.ORTHOGRAPHIC;
                case 't', 'T' -> LineRole.TRANSCRIPTION;
                case 'g', 'G' -> LineRole.GLOSS;
                default -> null;
            };
            if (role == null) return error(entries, i, "'" + letter + "' is not o, t or g");
            i++;
            Optional<Level> level = Optional.empty();
            Optional<String> name = Optional.empty();
            Optional<String> language = Optional.empty();
            while (i < v.length() && v.charAt(i) != ',') {
                char c = v.charAt(i);
                if (c == ':' && i + 1 < v.length() && (v.charAt(i + 1) == 'w' || v.charAt(i + 1) == 'm')) {
                    if (role == LineRole.ORTHOGRAPHIC) return error(entries, i, "an orthographic line has no level");
                    level = Optional.of(v.charAt(i + 1) == 'w' ? Level.WORD : Level.MORPHEME);
                    i += 2;
                } else if (c == '\'') {
                    int close = v.indexOf('\'', i + 1);
                    if (close < 0) return error(entries, i, "the name is not closed by '");
                    name = Optional.of(v.substring(i + 1, close));
                    i = close + 1;
                } else if (c == '@') {
                    int from = ++i;
                    while (i < v.length() && v.charAt(i) != ',' && v.charAt(i) != ':' && v.charAt(i) != '\'') i++;
                    String lang = v.substring(from, i).strip();
                    if (lang.isEmpty()) return error(entries, from, "a language is expected after @");
                    language = Optional.of(lang);
                } else if (c == ' ') {
                    i++;
                } else {
                    return error(entries, i, "unexpected '" + c + "'");
                }
            }
            entries.add(new Entry(role, level, name, language));
            if (i >= v.length()) return new Result(List.copyOf(entries), Optional.empty());
            i++; // ','
        }
    }

    private static Result error(List<Entry> entries, int column, String message) {
        return new Result(List.copyOf(entries), Optional.of("setting, column " + (column + 1) + ": " + message));
    }

    /** The setting written back ({@code o@fra,t'IPA'@fra:m,g@en}). */
    public static String write(List<Entry> entries) {
        StringBuilder s = new StringBuilder();
        for (Entry e : entries) {
            if (!s.isEmpty()) s.append(',');
            s.append(switch (e.role()) {
                case ORTHOGRAPHIC -> 'o';
                case GLOSS -> 'g';
                default -> 't';
            });
            e.level().ifPresent(l -> s.append(l == Level.WORD ? ":w" : ":m"));
            e.name().ifPresent(n -> s.append('\'').append(n).append('\''));
            e.language().ifPresent(l -> s.append('@').append(l));
        }
        return s.toString();
    }
}
