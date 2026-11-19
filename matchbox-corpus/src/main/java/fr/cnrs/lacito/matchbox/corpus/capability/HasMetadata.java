package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.MetadataBlock;
import java.util.Map;
import java.util.Optional;

/**
 * An object that has, or could have, a block of key/value metadata written in the text (the header
 * of a file, the block of a section, of an IGT, the comments of a CoNLL sentence…). The Metadata
 * mode lists them, whatever plugin and notation made them.
 *
 * <p>The values are changed by the grammar's {@code ObjectEditor}: {@code setField},
 * {@code addField} (which creates the block when the object has none) and {@code removeField}, with
 * the field name {@link #metadataFieldName(String)}.
 */
public interface HasMetadata extends CorpusObject {

    /** The block as written; empty when the object has none (it could have one). */
    Optional<MetadataBlock> metadataBlock();

    /** The name of the field holding the value of a key (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String metadataFieldName(String key);

    /** The values by key (first value of a duplicated key); empty without block. */
    default Map<String, String> metadataValues() {
        return metadataBlock().map(MetadataBlock::values).orElse(Map.of());
    }
}
