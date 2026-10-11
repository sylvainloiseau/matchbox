package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The palette of special characters (IPA, diacritics, tones…) offered by "Insert special
 * character…": groups read from {@code special-characters.txt}, names from Unicode. Pure.
 */
public final class SpecialCharacters {

    /** A character of the palette. */
    public record Entry(String character, String name, String group) {
        /** What the palette shows: a combining mark on a dotted circle. */
        public String display() {
            return isCombining(character) ? "◌" + character : character;
        }
    }

    /** A group of characters. */
    public record Group(String name, List<Entry> entries) {}

    private final List<Group> groups;

    private SpecialCharacters(List<Group> groups) {
        this.groups = List.copyOf(groups);
    }

    /** The palette of the application. */
    public static SpecialCharacters load() {
        try (InputStream in = SpecialCharacters.class.getResourceAsStream("special-characters.txt")) {
            if (in == null) throw new IllegalStateException("special-characters.txt not found");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SpecialCharacters parse(String content) {
        List<Group> groups = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new java.io.StringReader(content))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon).strip();
                Set<String> seen = new LinkedHashSet<>();
                for (String c : line.substring(colon + 1).strip().split(" +")) {
                    if (!c.isEmpty()) seen.add(c);
                }
                groups.add(new Group(name, seen.stream().map(c -> new Entry(c, nameOf(c), name)).toList()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SpecialCharacters(groups);
    }

    public List<Group> groups() {
        return groups;
    }

    /** The entries whose character or name contains the query (ignoring case), in palette order, without duplicates. */
    public List<Entry> search(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        List<Entry> found = new ArrayList<>();
        for (Group g : groups) {
            for (Entry e : g.entries()) {
                if ((q.isEmpty() || e.character().equals(query.strip()) || e.name().contains(q)) && seen.add(e.character())) {
                    found.add(e);
                }
            }
        }
        return found;
    }

    /** The Unicode name of a character (of each code point if there are several), lower case. */
    public static String nameOf(String character) {
        StringBuilder s = new StringBuilder();
        character.codePoints().forEach(cp -> {
            String n = Character.getName(cp);
            if (s.length() > 0) s.append(" + ");
            s.append(n == null ? String.format("U+%04X", cp) : n.toLowerCase(Locale.ROOT));
        });
        return s.toString();
    }

    static boolean isCombining(String character) {
        int type = Character.getType(character.codePointAt(0));
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
    }
}
