package fr.cnrs.lacito.matchbox.gui.problems;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rows of a problem table as CSV (Export…) or tab-separated text (Copy all rows). Pure.
 * Columns: File, Line, Position, Severity, Category, Plugin, Code, Object, Message.
 */
public final class ProblemExport {

    static final List<String> HEADER =
        List.of("File", "Line", "Position", "Severity", "Category", "Plugin", "Code", "Object", "Message");

    private ProblemExport() {}

    /** CSV (RFC 4180: comma, double quotes, CRLF line endings). */
    public static String csv(List<ProblemRow> rows, Optional<Path> projectDirectory, Function<String, String> pluginName) {
        StringBuilder out = new StringBuilder(String.join(",", HEADER)).append("\r\n");
        for (ProblemRow r : rows) {
            out.append(cells(r, projectDirectory, pluginName).stream().map(ProblemExport::csvCell)
                .collect(Collectors.joining(","))).append("\r\n");
        }
        return out.toString();
    }

    /** Tab-separated, one line per row, with a header; tabs and line breaks in values become spaces. */
    public static String tsv(List<ProblemRow> rows, Optional<Path> projectDirectory, Function<String, String> pluginName) {
        StringBuilder out = new StringBuilder(String.join("\t", HEADER)).append('\n');
        for (ProblemRow r : rows) {
            out.append(cells(r, projectDirectory, pluginName).stream().map(c -> c.replaceAll("[\\t\\r\\n]+", " "))
                .collect(Collectors.joining("\t"))).append('\n');
        }
        return out.toString();
    }

    /** One line describing a row (Copy message). */
    public static String line(ProblemRow r) {
        return r.fileName() + ":" + r.line() + ": " + r.diagnostic().message();
    }

    static List<String> cells(ProblemRow r, Optional<Path> projectDirectory, Function<String, String> pluginName) {
        var d = r.diagnostic();
        return List.of(relativePath(r.file().path(), projectDirectory), String.valueOf(d.line()),
            String.valueOf(d.column()), d.isWarning() ? "Warning" : "Error",
            d.category().name().toLowerCase(java.util.Locale.ROOT), pluginName.apply(d.grammarId()), d.code(),
            r.object().isPresent() ? r.objectLabel() : "", d.message());
    }

    /** The path relative to the project directory when inside it, with "/" separators. */
    static String relativePath(Path file, Optional<Path> projectDirectory) {
        Path shown = file;
        if (projectDirectory.isPresent()) {
            Path dir = projectDirectory.get().toAbsolutePath().normalize();
            Path abs = file.toAbsolutePath().normalize();
            if (abs.startsWith(dir)) shown = dir.relativize(abs);
        }
        StringBuilder s = new StringBuilder();
        if (shown.isAbsolute()) return shown.toString();
        for (Path element : shown) {
            if (!s.isEmpty()) s.append('/');
            s.append(element);
        }
        return s.toString();
    }

    private static String csvCell(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
