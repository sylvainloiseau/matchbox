package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import java.util.Map;

/**
 * A titled division of a text (chapter, session, story…) with metadata and its own content.
 *
 * <p>The span of a section covers its heading, its metadata and its content. The content holds the
 * objects the same grammar found in the section, in text order, subsections included; objects of
 * other grammars lying in the section are found with {@code Text.enclosingSections}.
 */
public interface Section extends CorpusObject {

    /** Name of the title field (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String TITLE = "title";

    /** Name of the field holding the value of a metadata key. */
    static String metadataField(String key) {
        return "metadata:" + key;
    }

    /** Title; empty string for an untitled section, never {@code null}. */
    String getTitle();

    /**
     * Metadata declared by the section itself (e.g. {@code speaker}, {@code date}), in declaration
     * order; unmodifiable, never {@code null}. Values inherited from enclosing sections or the
     * text header are not included: see {@code Text.metadataOf}.
     */
    Map<String, String> getMetadata();

    /** Content in text order: corpus objects and subsections; unmodifiable, never {@code null}. */
    List<? extends CorpusObject> getContent();

    /** Direct subsections, in text order. */
    default List<Section> getSubsections() {
        return getContent().stream()
            .filter(Section.class::isInstance)
            .map(Section.class::cast)
            .toList();
    }

    @Override
    default String getLabel() {
        return getTitle().isBlank() ? getKind() : getTitle();
    }

    @Override
    default List<? extends CorpusObject> getChildren() {
        return getContent();
    }
}
