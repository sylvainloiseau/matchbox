package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Arrays;
import java.util.List;

/**
 * A keyword attached to a place of a text (e.g. {@code #"verb/infinitive"}); {@code /} separates
 * the levels of a hierarchy. The object a tag applies to is computed by {@code Text.targetOf}.
 */
public interface Tag extends CorpusObject {

    /** Field name of the keyword (see {@link fr.cnrs.lacito.matchbox.corpus.HasFields}). */
    String TAG = "tag";

    /** The keyword as written, trimmed (e.g. "verb/infinitive"). */
    String getName();

    /** Levels of the keyword: "verb/infinitive" → [verb, infinitive]; spaces and empty levels ignored. */
    default List<String> getPath() {
        return Arrays.stream(getName().split("/")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** The last level ("infinitive"); the name if it has no level. */
    default String getLeaf() {
        List<String> path = getPath();
        return path.isEmpty() ? getName() : path.get(path.size() - 1);
    }

    @Override
    default String getLabel() {
        return getName();
    }
}
