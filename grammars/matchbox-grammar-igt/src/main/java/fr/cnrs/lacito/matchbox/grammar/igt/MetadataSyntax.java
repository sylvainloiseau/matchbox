package fr.cnrs.lacito.matchbox.grammar.igt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The metadata block of a start anchor: {@code :::{id="1.1", title="an \"example\""}}. Keys contain
 * no {@code =}, {@code ,}, {@code "}, braces nor whitespace; values are between double quotes,
 * {@code \"} and {@code \\} escape a quote and a backslash. Pure.
 */
final class MetadataSyntax {

    /**
     * One entry. Offsets are in the whole content.
     *
     * @param valueStart first character of the raw value (after the opening quote)
     * @param valueEnd   after the last character of the raw value (the closing quote)
     * @param start      first character of the key
     * @param end        after the closing quote
     */
    record Entry(String key, String value, int valueStart, int valueEnd, int start, int end) {}

    /** A problem of the block, at an offset of the content. */
    record Problem(int offset, String message) {}

    /**
     * @param open  offset of {@code {}, -1 without block
     * @param close offset of {@code }}, -1 without block or when it is not found
     */
    record Block(int open, int close, List<Entry> entries, List<Problem> problems) {

        boolean present() {
            return open >= 0;
        }

        Optional<Entry> entry(String key) {
            return entries.stream().filter(e -> e.key().equals(key)).findFirst();
        }
    }

    private MetadataSyntax() {}

    /**
     * @param line      the start anchor line, without its line ending
     * @param lineStart offset of the line in the content
     */
    static Block parse(String line, int lineStart) {
        List<Entry> entries = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        int i = 3; // after ":::"
        i = skip(line, i);
        if (i >= line.length()) return new Block(-1, -1, List.of(), List.of());
        if (line.charAt(i) != '{') {
            problems.add(new Problem(lineStart + i, "Only a {…} metadata block may follow :::"));
            return new Block(-1, -1, List.of(), List.copyOf(problems));
        }
        int open = i;
        i = skip(line, i + 1);
        if (i < line.length() && line.charAt(i) == '}') return new Block(lineStart + open, lineStart + i, List.of(), List.of());
        while (i < line.length()) {
            int keyStart = i;
            while (i < line.length() && validKeyChar(line.charAt(i))) i++;
            String key = line.substring(keyStart, i);
            if (key.isEmpty()) {
                problems.add(new Problem(lineStart + i, "A key is expected here (keys contain no = , \" { } nor spaces)"));
                break;
            }
            i = skip(line, i);
            if (i >= line.length() || line.charAt(i) != '=') {
                problems.add(new Problem(lineStart + i, "= is expected after the key " + key));
                break;
            }
            i = skip(line, i + 1);
            if (i >= line.length() || line.charAt(i) != '"') {
                problems.add(new Problem(lineStart + i, "The value of " + key + " must be between double quotes"));
                break;
            }
            int valueStart = i + 1;
            StringBuilder value = new StringBuilder();
            i = valueStart;
            boolean closed = false;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '\\' && i + 1 < line.length() && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\')) {
                    value.append(line.charAt(i + 1));
                    i += 2;
                } else if (c == '"') {
                    closed = true;
                    break;
                } else {
                    value.append(c);
                    i++;
                }
            }
            if (!closed) {
                problems.add(new Problem(lineStart + valueStart - 1, "The value of " + key + " is not closed by \""));
                break;
            }
            entries.add(new Entry(key, value.toString(), lineStart + valueStart, lineStart + i, lineStart + keyStart, lineStart + i + 1));
            i = skip(line, i + 1);
            if (i < line.length() && line.charAt(i) == ',') {
                i = skip(line, i + 1);
                continue;
            }
            if (i < line.length() && line.charAt(i) == '}') {
                return new Block(lineStart + open, lineStart + i, List.copyOf(entries), List.copyOf(problems));
            }
            problems.add(new Problem(lineStart + i, ", or } is expected after the value of " + key));
            break;
        }
        int close = line.lastIndexOf('}');
        return new Block(lineStart + open, close > open ? lineStart + close : -1, List.copyOf(entries), List.copyOf(problems));
    }

    private static int skip(String line, int i) {
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    static boolean validKeyChar(char c) {
        return c != '=' && c != ',' && c != '"' && c != '{' && c != '}' && !Character.isWhitespace(c);
    }

    static boolean validKey(String key) {
        return !key.isEmpty() && key.chars().allMatch(c -> validKeyChar((char) c));
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** {@code key="value"}, escaped. */
    static String entry(String key, String value) {
        return key + "=\"" + escape(value) + "\"";
    }
}
