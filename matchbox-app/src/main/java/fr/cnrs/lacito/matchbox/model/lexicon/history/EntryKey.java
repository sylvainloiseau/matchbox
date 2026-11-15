package fr.cnrs.lacito.matchbox.model.lexicon.history;

/**
 * An entry, across recomputes of the history: by its LIFT id when it comes from the context
 * dictionary (ids are stable there), else by its form (an entry created by commands gets a new
 * generated id at each recompute).
 *
 * @param id      the LIFT id in the history computed last
 * @param form    its first form, to find a created entry again
 * @param stable  whether the id is stable (the entry is in the context dictionary)
 */
public record EntryKey(String id, String form, boolean stable) {

    /** Whether this key designates the same entry as another key, possibly of another recompute. */
    public boolean matches(EntryKey other) {
        if (other == null) return false;
        if (stable && other.stable) return id.equals(other.id);
        return !stable && !other.stable && form.equals(other.form);
    }
}
