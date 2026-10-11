package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

/** Caret movements and deletions bound to keys by {@link EditorKeymap}. */
public enum EditorAction {
    /** Start of the line: first non-blank character, then column 1 (smart Home). */
    LINE_START("Start of line"),
    LINE_END("End of line"),
    FILE_START("Start of file"),
    FILE_END("End of file"),
    WORD_LEFT("Previous word"),
    WORD_RIGHT("Next word"),
    DELETE_TO_LINE_START("Delete to start of line"),
    DELETE_WORD_LEFT("Delete previous word"),
    DELETE_WORD_RIGHT("Delete next word");

    private final String label;

    EditorAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True for the actions that move the caret (they have a "select" variant with Shift). */
    public boolean isMove() {
        return ordinal() <= WORD_RIGHT.ordinal();
    }
}
