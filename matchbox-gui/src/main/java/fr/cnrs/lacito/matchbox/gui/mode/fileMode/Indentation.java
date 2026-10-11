package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

/** Auto-indentation and smart Home, on the text of a line. */
public final class Indentation {

    private Indentation() {
    }

    /** The leading spaces and tabs of a line. */
    public static String indentationOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    /**
     * What Enter inserts at {@code column} of a line: a newline and the indentation of the line
     * (not more than the text before the caret holds, so that Enter in the indentation does not
     * add any).
     */
    public static String newline(String line, int column) {
        String indent = indentationOf(line);
        return "\n" + indent.substring(0, Math.min(indent.length(), Math.max(0, column)));
    }

    /**
     * The column smart Home goes to: the first non-blank character, or column 0 if the caret is
     * already there (or the line is blank).
     */
    public static int smartHome(String line, int column) {
        int first = indentationOf(line).length();
        return column == first || first == line.length() ? 0 : first;
    }
}
