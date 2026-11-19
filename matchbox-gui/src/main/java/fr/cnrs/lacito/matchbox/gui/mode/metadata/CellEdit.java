package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.MetadataEntry;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata;
import java.util.Optional;

/**
 * What typing a new text in a cell of the Metadata table means: set the value of an entry, add an
 * entry (creating the block when the object has none), remove it (the text cleared), give a file a
 * header, nothing, or nothing possible (why). No JavaFX.
 */
public sealed interface CellEdit {

    /** The undo label ("Set speaker of “Session 1”"), the file name is added by the edit service. */
    String description();

    record Set(HasMetadata owner, String key, String value) implements CellEdit {
        public String description() { return "Set " + key + " of " + name(owner); }
    }

    record Add(HasMetadata owner, String key, String value) implements CellEdit {
        public String description() { return "Add " + key + " to " + name(owner); }
    }

    record Remove(HasMetadata owner, String key) implements CellEdit {
        public String description() { return "Remove " + key + " from " + name(owner); }
    }

    record AddHeader(TextId text, String key, String value) implements CellEdit {
        public String description() { return "Add a header with " + key; }
    }

    record None() implements CellEdit {
        public String description() { return ""; }
    }

    record ReadOnly(String reason) implements CellEdit {
        public String description() { return ""; }
    }

    /**
     * The edit of a cell.
     *
     * @param newText    the text typed; empty: the entry is removed
     * @param emptyValue true to write an empty value (Set empty value) instead of removing
     */
    static CellEdit of(MetadataRow row, String key, String newText, boolean emptyValue) {
        Optional<String> why = readOnly(row, key);
        if (why.isPresent()) return new ReadOnly(why.get());
        String value = emptyValue ? "" : newText.strip();
        if (row.isHeaderCandidate()) {
            return value.isEmpty() && !emptyValue ? new None() : new AddHeader(row.text(), key, value);
        }
        HasMetadata owner = row.owner().orElseThrow();
        Optional<MetadataEntry> entry = row.block().flatMap(b -> b.entry(key));
        if (entry.isEmpty()) {
            return value.isEmpty() && !emptyValue ? new None() : new Add(owner, key, value);
        }
        if (entry.get().value().equals(value) && !(emptyValue && !entry.get().value().isEmpty())) return new None();
        if (value.isEmpty() && !emptyValue) return new Remove(owner, key);
        return new Set(owner, key, value);
    }

    /** Why a cell cannot be edited, if it cannot. */
    static Optional<String> readOnly(MetadataRow row, String key) {
        if (row.block().map(b -> b.hasDuplicate(key)).orElse(false)) {
            return Optional.of("The key " + key + " is written more than once in this block: edit it in the file");
        }
        Optional<MetadataEntry> entry = row.block().flatMap(b -> b.entry(key));
        if (entry.isPresent() && entry.get().valueSpan().isEmpty()) {
            return Optional.of("The plugin gives no position for this value: edit it in the file");
        }
        return Optional.empty();
    }

    private static String name(HasMetadata owner) {
        String type = MetadataTypes.of(owner);
        return type.equals(MetadataTypes.HEADER) ? "the header" : type + " “" + owner.getLabel() + "”";
    }
}
