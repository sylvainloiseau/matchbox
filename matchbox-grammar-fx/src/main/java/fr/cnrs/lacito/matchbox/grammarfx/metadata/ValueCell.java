package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a value editor needs to show and change one metadata value.
 *
 * @param owner    the object holding the metadata (section, header)
 * @param key      the key
 * @param value    the raw value
 * @param editable false for inherited values, or when the object cannot be edited
 * @param commit   writes a new value (one undoable change)
 * @param context  services of the application
 */
public record ValueCell(CorpusObject owner, String key, String value, boolean editable,
                        Consumer<String> commit, ViewContext context) {

    public MetadataSchema schema() {
        return context.metadataSchema();
    }

    public Optional<Path> projectDirectory() {
        return context.projectDirectory();
    }

    /** Directory of the text holding the metadata. */
    public Optional<Path> textDirectory() {
        Path p = owner.getTextCoordinate().text().path().toAbsolutePath().getParent();
        return Optional.ofNullable(p);
    }
}
