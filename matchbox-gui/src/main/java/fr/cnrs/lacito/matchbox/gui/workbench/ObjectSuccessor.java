package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Finds, after a re-parse, the object that replaces an object of the previous parse. */
public final class ObjectSuccessor {

    private ObjectSuccessor() {
    }

    /**
     * After a re-parse all objects are new instances: the object of {@code text} that replaces
     * {@code previous}. After a view edit at {@code lastEditOffset}, the object of the same kind
     * containing (or ending just before) the edit; otherwise preferably an object of the same kind
     * and label (the closest one to the previous position, which may have moved if text was
     * inserted before it); otherwise the object of the same kind containing the previous start
     * offset.
     */
    public static Optional<CorpusObject> find(Text text, CorpusObject previous, OptionalInt lastEditOffset) {
        if (text.owns(previous)) {
            return Optional.of(previous);
        }
        int start = previous.getTextCoordinate().startIndex();
        List<CorpusObject> sameKind = text.flatten()
            .filter(o -> o.getKind().equals(previous.getKind()))
            .toList();
        Optional<CorpusObject> edited = Optional.empty();
        if (lastEditOffset.isPresent()) {
            int offset = lastEditOffset.getAsInt();
            edited = sameKind.stream()
                .filter(o -> o.getTextCoordinate().contains(offset) || o.getTextCoordinate().stopIndex() + 1 == offset)
                .reduce((a, b) -> b);
        }
        return edited
            .or(() -> sameKind.stream()
                .filter(o -> Objects.equals(o.getLabel(), previous.getLabel()))
                .min(Comparator.comparingInt(o -> Math.abs(o.getTextCoordinate().startIndex() - start))))
            .or(() -> sameKind.stream().filter(o -> o.getTextCoordinate().contains(start)).findFirst());
    }
}
