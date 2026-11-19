package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.gui.view.Kinds;
import java.util.List;
import java.util.Map;

/**
 * The Type column of the Metadata mode: by capability (whatever plugin made the object), else the
 * title of its kind. The one place to update when a capability with metadata is added.
 */
public final class MetadataTypes {

    public static final String HEADER = "Header";

    private static final List<Map.Entry<Class<?>, String>> CAPABILITIES = List.of(
        Map.entry(TextHeader.class, HEADER),
        Map.entry(Section.class, "Section"),
        Map.entry(InterlinearGlossedText.class, "IGT"),
        Map.entry(DependencySentence.class, "Sentence")
    );

    private MetadataTypes() {
    }

    public static String of(CorpusObject object) {
        for (Map.Entry<Class<?>, String> e : CAPABILITIES) {
            if (e.getKey().isInstance(object)) return e.getValue();
        }
        return Kinds.title(object.getKind());
    }
}
