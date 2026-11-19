package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.List;

/**
 * The Metadata table as text: CSV (RFC 4180: fields with the separator, a quote or a line break
 * between double quotes, quotes doubled) or TSV (tabs and line breaks of values replaced by spaces).
 * No JavaFX.
 */
public final class TableExport {

    public enum Format { CSV_COMMA, CSV_SEMICOLON, TSV }

    private TableExport() {
    }

    /** @param rows the cells of each row, as shown; the first row is usually the titles */
    public static String of(List<List<String>> rows, Format format) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(separator(format));
                out.append(field(row.get(i) == null ? "" : row.get(i), format));
            }
            out.append(format == Format.TSV ? "\n" : "\r\n");
        }
        return out.toString();
    }

    private static char separator(Format format) {
        return switch (format) {
            case CSV_COMMA -> ',';
            case CSV_SEMICOLON -> ';';
            case TSV -> '\t';
        };
    }

    private static String field(String value, Format format) {
        if (format == Format.TSV) return value.replaceAll("[\\t\\r\\n]+", " ");
        char sep = separator(format);
        if (value.indexOf(sep) < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Cells of a pasted TSV text: rows by line, cells by tab (a trailing line break ignored). */
    public static List<List<String>> parseTsv(String text) {
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        if (t.endsWith("\n")) t = t.substring(0, t.length() - 1);
        if (t.isEmpty()) return List.of();
        return java.util.Arrays.stream(t.split("\n", -1)).map(line -> List.of(line.split("\t", -1))).toList();
    }
}
