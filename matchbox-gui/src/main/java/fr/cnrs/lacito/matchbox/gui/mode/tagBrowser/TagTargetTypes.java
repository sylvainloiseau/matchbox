package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.corpus.capability.Example;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.gui.view.Kinds;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The type of the object a tag applies to, for the user: by capability (whatever plugin made the
 * object), else by the title of its kind, else "Whole text". The one place to update when a
 * capability is added.
 */
public final class TagTargetTypes {

    public static final String WHOLE_TEXT = "Whole text";

    /** Capabilities in the order they are tested (the first one implemented names the object). */
    private static final List<Map.Entry<Class<?>, String>> CAPABILITIES = List.of(
        Map.entry(InterlinearSentence.class, "Interlinear sentence"),
        Map.entry(LexicalEntry.class, "Lexical entry"),
        Map.entry(Sense.class, "Sense"),
        Map.entry(Example.class, "Example"),
        Map.entry(DependencySentence.class, "Dependency sentence"),
        Map.entry(MediaSegment.class, "Media segment"),
        Map.entry(Section.class, "Section"),
        Map.entry(TextHeader.class, "Text header")
    );

    private TagTargetTypes() {
    }

    public static String of(Optional<CorpusObject> target) {
        if (target.isEmpty()) return WHOLE_TEXT;
        for (Map.Entry<Class<?>, String> e : CAPABILITIES) {
            if (e.getKey().isInstance(target.get())) return e.getValue();
        }
        return Kinds.title(target.get().getKind());
    }
}
