package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import java.util.List;
import java.util.Optional;

/**
 * One tag of the corpus, as the Tag Browser shows it.
 *
 * @param tag        the tag (of the current parse of its text)
 * @param text       its text
 * @param path       its levels ({@link Tag#getPath()}; the name as one level if it has none)
 * @param target     the object it applies to ({@link Text#targetOf}); empty: the whole text
 * @param targetType the type of the target ({@link TagTargetTypes})
 */
public record TagOccurrence(Tag tag, TextId text, List<String> path, Optional<CorpusObject> target, String targetType) {

    public TagOccurrence {
        path = List.copyOf(path);
    }

    public static TagOccurrence of(Text text, Tag tag) {
        Optional<CorpusObject> target = text.targetOf(tag);
        return new TagOccurrence(tag, text.id(), pathOf(tag), target, TagTargetTypes.of(target));
    }

    /** The levels of a tag; a tag without level (e.g. "/") is one level, its name. */
    public static List<String> pathOf(Tag tag) {
        List<String> path = tag.getPath();
        return path.isEmpty() ? List.of(tag.getName()) : path;
    }

    public String keyword() {
        return String.join("/", path);
    }

    /** True if the path starts with {@code prefix}. */
    public boolean isUnder(List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }
}
