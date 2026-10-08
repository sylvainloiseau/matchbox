package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The tags of the corpus, kept per text so that a re-parse updates only its text. Occurrences are
 * listed by text, then by position.
 */
public final class TagIndex {

    private final Map<TextId, List<TagOccurrence>> byText = new TreeMap<>();

    /** Builds the index of every text of a corpus. */
    public static TagIndex of(CorpusView corpus) {
        TagIndex index = new TagIndex();
        for (Text t : corpus.texts()) {
            index.update(t.id(), Optional.of(t));
        }
        return index;
    }

    /** A text was parsed again ({@code text} present) or removed (empty). */
    public void update(TextId id, Optional<Text> text) {
        if (text.isEmpty()) {
            byText.remove(id);
            return;
        }
        List<TagOccurrence> occurrences = text.get().objectsOf(Tag.class).stream()
            .sorted(Comparator.comparingInt(t -> t.getTextCoordinate().startIndex()))
            .map(t -> TagOccurrence.of(text.get(), t))
            .toList();
        if (occurrences.isEmpty()) {
            byText.remove(id);
        } else {
            byText.put(id, occurrences);
        }
    }

    public void clear() {
        byText.clear();
    }

    /** All occurrences, by text then position. */
    public List<TagOccurrence> all() {
        return byText.values().stream().flatMap(List::stream).toList();
    }

    /** The occurrences of exactly {@code path}, or of its whole subtree. */
    public List<TagOccurrence> at(List<String> path, boolean includeSubTags) {
        return all().stream()
            .filter(o -> includeSubTags ? o.isUnder(path) : o.path().equals(path))
            .toList();
    }

    public int size() {
        return byText.values().stream().mapToInt(List::size).sum();
    }
}
