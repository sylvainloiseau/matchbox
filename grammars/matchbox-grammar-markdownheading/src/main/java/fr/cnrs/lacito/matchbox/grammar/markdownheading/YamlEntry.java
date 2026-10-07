package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import java.util.regex.Pattern;

/**
 * One {@code key: value} line of a metadata block (a YAML subset): double-quoted values
 * ({@code \"}, {@code \\} unescaped), single-quoted values ({@code ''} unescaped), plain values
 * where {@code " #"} starts a comment.
 *
 * @param key        the key, trimmed
 * @param value      the value, unquoted
 * @param valueStart offset of the raw value in the text (inside the quotes)
 * @param valueStop  offset of its last character (inclusive); {@code valueStart - 1} if empty
 * @param problem    why the line is not supported, or {@code null}
 */
record YamlEntry(String key, String value, int valueStart, int valueStop, String problem) {

    private static final Pattern BLOCK_SCALAR = Pattern.compile("[|>][-+0-9]*(\\s+#.*)?");

    /**
     * @param line      the line, without its line ending, containing a ':'
     * @param lineStart offset of the line in the text
     */
    static YamlEntry parse(String line, int lineStart) {
        int colon = line.indexOf(':');
        String key = line.substring(0, colon).strip();
        int i = colon + 1;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        String rest = line.substring(i);
        int at = lineStart + i;
        if (rest.isBlank() || (i > colon + 1 && rest.startsWith("#"))) {
            int empty = lineStart + colon + 1 + (colon + 1 < line.length() && line.charAt(colon + 1) == ' ' ? 1 : 0);
            return new YamlEntry(key, "", empty, empty - 1, null);
        }
        if (BLOCK_SCALAR.matcher(rest).matches()) {
            return new YamlEntry(key, "", at, at - 1, "Multi-line values (| or >) are not supported");
        }
        char q = rest.charAt(0);
        if (q == '"' || q == '\'') {
            StringBuilder value = new StringBuilder();
            int j = 1;
            int end = -1;
            while (j < rest.length()) {
                char c = rest.charAt(j);
                if (q == '"' && c == '\\' && j + 1 < rest.length()) {
                    value.append(rest.charAt(j + 1));
                    j += 2;
                } else if (q == '\'' && c == '\'' && j + 1 < rest.length() && rest.charAt(j + 1) == '\'') {
                    value.append('\'');
                    j += 2;
                } else if (c == q) {
                    end = j;
                    break;
                } else {
                    value.append(c);
                    j++;
                }
            }
            if (end > 0) {
                String after = rest.substring(end + 1).strip();
                if (after.isEmpty() || after.startsWith("#")) {
                    return new YamlEntry(key, value.toString(), at + 1, at + end - 1, null);
                }
            }
            // not a quoted value: read as plain text
        }
        int comment = -1;
        for (int k = 1; k < rest.length(); k++) {
            if (rest.charAt(k) == '#' && (rest.charAt(k - 1) == ' ' || rest.charAt(k - 1) == '\t')) {
                comment = k;
                break;
            }
        }
        String raw = (comment < 0 ? rest : rest.substring(0, comment)).stripTrailing();
        return new YamlEntry(key, raw, at, at + raw.length() - 1, null);
    }

    /** Why a line of a block that is not {@code key: value} is not supported. */
    static String unsupported(String line) {
        if (line.startsWith(" ") || line.startsWith("\t")) return "Nested values are not supported";
        if (line.startsWith("-")) return "Lists are not supported (write the values separated by commas)";
        return "Not a 'key: value' line";
    }
}
