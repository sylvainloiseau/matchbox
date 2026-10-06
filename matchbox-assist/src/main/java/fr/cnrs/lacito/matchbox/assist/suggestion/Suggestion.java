package fr.cnrs.lacito.matchbox.assist.suggestion;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A proposed correction (or a problem without a correction) of the edited file. Immutable: the
 * {@code with…} methods return modified copies.
 *
 * @param id          stable identifier, kept by the copies
 * @param source      where it comes from
 * @param start       offset where it applies (for sorting and "go to")
 * @param end         end offset (exclusive) of the concerned range, {@code start} if empty
 * @param title       one-line summary
 * @param detail      longer text (explanation of the AI, expected tokens…), may be empty
 * @param edits       the changes it proposes; empty for a problem without correction
 * @param error       the parse error it is about, if any
 * @param status      current state
 * @param note        remark shown with the suggestion (e.g. why a fix was rejected), may be empty
 */
public record Suggestion(String id, Source source, int start, int end, String title, String detail,
                         List<TextEdit> edits, Optional<Diagnostic> error, Status status, String note) {

    public enum Source {
        PARSE_ERROR("Error"),
        GRAMMAR_FIX("Fix"),
        AI("AI");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        ACTIVE,
        /** The text changed and the suggestion could not be relocated. */
        OUTDATED
    }

    public Suggestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(title, "title");
        detail = detail == null ? "" : detail;
        edits = List.copyOf(edits);
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(status, "status");
        note = note == null ? "" : note;
    }

    public boolean hasEdits() {
        return !edits.isEmpty();
    }

    public Suggestion withDetail(String newDetail) {
        return new Suggestion(id, source, start, end, title, newDetail, edits, error, status, note);
    }

    public Suggestion withEdits(List<TextEdit> newEdits) {
        return new Suggestion(id, source, start, end, title, detail, newEdits, error, status, note);
    }

    public Suggestion withNote(String newNote) {
        return new Suggestion(id, source, start, end, title, detail, edits, error, status, newNote);
    }

    public Suggestion withRange(int newStart, int newEnd) {
        return new Suggestion(id, source, newStart, newEnd, title, detail, edits, error, status, note);
    }

    public Suggestion withStatus(Status newStatus) {
        return new Suggestion(id, source, start, end, title, detail, edits, error, newStatus, note);
    }

    /**
     * This suggestion after a change of the text: its range and edits are relocated, or it is
     * marked {@link Status#OUTDATED} if an edit cannot be found any more.
     */
    public Suggestion relocated(int position, int removed, int inserted, String newText) {
        if (status == Status.OUTDATED) {
            return this;
        }
        int delta = inserted - removed;
        int newStart = start;
        int newEnd = end;
        if (position + removed <= start) {
            newStart += delta;
            newEnd += delta;
        } else if (position < end) {
            newEnd = Math.max(newStart, newEnd + delta);
        }
        List<TextEdit> moved = new java.util.ArrayList<>();
        for (TextEdit e : edits) {
            Optional<TextEdit> r = Relocation.relocate(e, position, removed, inserted, newText);
            if (r.isEmpty()) {
                return withStatus(Status.OUTDATED);
            }
            moved.add(r.get());
        }
        if (!moved.isEmpty()) {
            newStart = moved.stream().mapToInt(TextEdit::start).min().orElse(newStart);
            newEnd = moved.stream().mapToInt(TextEdit::end).max().orElse(newEnd);
        }
        newStart = Math.min(Math.max(0, newStart), newText.length());
        newEnd = Math.min(Math.max(newStart, newEnd), newText.length());
        return new Suggestion(id, source, newStart, newEnd, title, detail, moved, error, status, note);
    }
}
