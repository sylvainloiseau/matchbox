package fr.cnrs.lacito.matchbox.model;

import java.util.Objects;

/**
 * Who made a change of a {@link TextDocument}.
 *
 * @param kind        drives undo merging and parsing
 * @param source      the object making the change, so that a listener recognises its own changes
 *                    (the editor ignores the changes it forwarded)
 * @param description what the change does, for the undo journal (e.g. "Edit Translation");
 *                    {@code null} to describe it by its kind
 */
public record EditOrigin(Kind kind, Object source, String description) {

    public enum Kind {
        /** Typing in the editor (merged into one undo step when consecutive). */
        TYPING,
        /** A validated change in an object view. */
        VIEW,
        /** An AI suggestion applied. */
        ASSIST,
        /** Search and replace. */
        SEARCH,
        UNDO,
        REDO
    }

    public EditOrigin {
        Objects.requireNonNull(kind, "kind");
    }

    public static EditOrigin of(Kind kind, Object source) {
        return new EditOrigin(kind, source, null);
    }

    public static EditOrigin of(Kind kind, Object source, String description) {
        return new EditOrigin(kind, source, description);
    }

    /** The description, or a default one for the kind ("Typing", "Edit"…). */
    public String describe() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return switch (kind) {
            case TYPING -> "Typing";
            case VIEW -> "Edit";
            case ASSIST -> "Apply suggestion";
            case SEARCH -> "Replace";
            case UNDO -> "Undo";
            case REDO -> "Redo";
        };
    }

    /** True for the changes made by the user's typing. */
    public boolean isTyping() {
        return kind == Kind.TYPING;
    }
}
