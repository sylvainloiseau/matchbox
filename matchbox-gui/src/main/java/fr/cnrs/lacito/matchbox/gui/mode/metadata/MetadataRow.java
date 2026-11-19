package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.MetadataBlock;
import fr.cnrs.lacito.matchbox.corpus.MetadataEntry;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata;
import java.util.Objects;
import java.util.Optional;

/**
 * A row of the Metadata mode: the metadata block of an object, or an object that could have one
 * (no block), or a file that could have a header (no object: {@link #isHeaderCandidate()}).
 *
 * @param text    the text of the block
 * @param owner   the object holding the block; empty for a header candidate
 * @param type    the Type column ({@link MetadataTypes})
 * @param label   the Object column ("—" for a header)
 * @param line    the line of the block, else of the object (1-based)
 * @param ordinal the position of the row among the rows of its text (with its type, it identifies
 *                the row across parses)
 */
public record MetadataRow(TextId text, Optional<HasMetadata> owner, String type, String label, int line, int ordinal) {

    public MetadataRow {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(owner, "owner");
    }

    /** A row for a file without header that a grammar of the file can give one. */
    public static MetadataRow headerCandidate(TextId text) {
        return new MetadataRow(text, Optional.empty(), MetadataTypes.HEADER, "—", 1, 0);
    }

    public boolean isHeaderCandidate() {
        return owner.isEmpty();
    }

    public Optional<MetadataBlock> block() {
        return owner.flatMap(HasMetadata::metadataBlock);
    }

    /** True for an object (or a file) that has no block: typing a value creates it. */
    public boolean withoutBlock() {
        return block().isEmpty();
    }

    /** The own value of a key (first entry of a duplicated key). */
    public Optional<String> value(String key) {
        return block().flatMap(b -> b.entry(key)).map(MetadataEntry::value);
    }

    /** Identity of the row across parses (the owner object changes at each parse). */
    public Key key() {
        return new Key(text, type, label, ordinal);
    }

    public record Key(TextId text, String type, String label, int ordinal) {}
}
