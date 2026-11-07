package fr.cnrs.lacito.matchbox.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * The recent changes of one {@link TextDocument}, to move edits computed against an earlier version
 * to the current text. Bounded: the oldest changes are forgotten. Written from the thread that
 * changes the document; read from any thread (synchronized).
 */
final class ChangeLog {

    /** Number of changes kept (typing makes one change per keystroke). */
    static final int CAPACITY = 10_000;

    private final int capacity;
    private final Deque<DocumentChange> changes = new ArrayDeque<>();

    ChangeLog() {
        this(CAPACITY);
    }

    ChangeLog(int capacity) {
        this.capacity = capacity;
    }

    synchronized void record(DocumentChange change) {
        if (!changes.isEmpty() && changes.peekLast().versionAfter() != change.versionBefore()) {
            // a gap: what came before cannot be replayed
            changes.clear();
        }
        changes.addLast(change);
        while (changes.size() > capacity) {
            changes.removeFirst();
        }
    }

    /**
     * The changes made after {@code version}, oldest first; empty list if {@code version} is the
     * current one, empty if {@code version} is unknown or its changes were forgotten.
     */
    synchronized Optional<List<DocumentChange>> since(long version, long current) {
        if (version == current) {
            return Optional.of(List.of());
        }
        List<DocumentChange> result = new ArrayList<>();
        Iterator<DocumentChange> it = changes.descendingIterator();
        while (it.hasNext()) {
            DocumentChange c = it.next();
            result.add(c);
            if (c.versionBefore() == version) {
                Collections.reverse(result);
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    synchronized int size() {
        return changes.size();
    }
}
