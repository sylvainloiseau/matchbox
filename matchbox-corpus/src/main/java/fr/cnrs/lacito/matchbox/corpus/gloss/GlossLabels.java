package fr.cnrs.lacito.matchbox.corpus.gloss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The grammatical labels of glosses ({@code 1SG}, {@code COMPL}, {@code PST}): the list of the
 * Leipzig Glossing Rules and Christian Lehmann's list, loaded once from the resources of this
 * package ({@code leipzig.tsv}, {@code lehmann.tsv}). Thread-safe.
 */
public final class GlossLabels {

    private static final Pattern PERSON = Pattern.compile("[123]+");
    private static final Pattern CLASS = Pattern.compile("(CL|G)\\d+");

    private static volatile Map<String, String> leipzig;
    private static volatile Map<String, String> lehmann;

    private GlossLabels() {}

    /** Label → meaning of the Leipzig list (upper case keys); unmodifiable. */
    public static Map<String, String> leipzig() {
        if (leipzig == null) leipzig = load("leipzig.tsv");
        return leipzig;
    }

    /** Label → meaning of Lehmann's list (the first meaning of a label; aliases as "see …"); unmodifiable. */
    public static Map<String, String> lehmann() {
        if (lehmann == null) lehmann = load("lehmann.tsv");
        return lehmann;
    }

    private static Map<String, String> load(String resource) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = GlossLabels.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (cells.length < 2 || cells[0].endsWith("-")) continue;
                String meaning = cells.length > 2 && cells[2].equals("alias") ? cells[1] + " (alias)" : cells[1];
                map.putIfAbsent(cells[0].toUpperCase(Locale.ROOT), meaning);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableMap(map);
    }

    /** The meaning of a label, from the Leipzig list first, then Lehmann's ("COMPL" → "completive"). */
    public static Optional<String> meaning(String label) {
        String l = label.toUpperCase(Locale.ROOT);
        String m = leipzig().get(l);
        if (m == null) m = lehmann().get(l);
        if (m == null && PERSON.matcher(l).matches()) m = "person " + l;
        return Optional.ofNullable(m);
    }

    /**
     * True if a grammatical part of a gloss ({@code 3SG}, {@code PST}, {@code NPST}, {@code CL7}) is a
     * label of the list or of the project. Person numbers followed by a label (rule 5: {@code 3SG},
     * {@code 1PL}), {@code N} + a label ("non-") and {@code CL}/{@code G} + a number are accepted.
     * With {@link GlossLabelList#NONE}, every part is accepted.
     */
    public static boolean isLabel(String part, GlossLabelList list, Set<String> projectLabels) {
        if (list == GlossLabelList.NONE) return true;
        String p = part.toUpperCase(Locale.ROOT);
        if (p.isEmpty()) return true;
        if (projectLabels.contains(p) || known(p, list)) return true;
        if (CLASS.matcher(p).matches()) return true;
        int i = 0;
        while (i < p.length() && p.charAt(i) >= '1' && p.charAt(i) <= '3') i++;
        String rest = p.substring(i);
        if (i > 0 && (rest.isEmpty() || known(rest, list) || projectLabels.contains(rest))) return true;
        return rest.length() > 1 && rest.charAt(0) == 'N' && known(rest.substring(1), list);
    }

    private static boolean known(String label, GlossLabelList list) {
        return switch (list) {
            case LEIPZIG -> leipzig().containsKey(label);
            case LEHMANN -> lehmann().containsKey(label);
            case BOTH -> leipzig().containsKey(label) || lehmann().containsKey(label);
            case NONE -> true;
        };
    }
}
