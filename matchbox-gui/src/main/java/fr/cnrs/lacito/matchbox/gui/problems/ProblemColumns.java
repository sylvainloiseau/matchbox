package fr.cnrs.lacito.matchbox.gui.problems;

/**
 * Which columns a problem table shows for its width. Pure. Below {@link #COMPACT_WIDTH} the table
 * is compact: Position, Plugin and Object are hidden (the tooltip of the message keeps the
 * details), so that Severity, Line, Message and Fix stay readable in a narrow panel.
 */
public final class ProblemColumns {

    /** Width (pixels) below which the table is compact. */
    public static final double COMPACT_WIDTH = 640;

    private ProblemColumns() {
    }

    /** True if a table of this width shows the compact set of columns; a 0 width (not laid out yet) is not compact. */
    public static boolean isCompact(double width) {
        return width > 0 && width < COMPACT_WIDTH;
    }
}
