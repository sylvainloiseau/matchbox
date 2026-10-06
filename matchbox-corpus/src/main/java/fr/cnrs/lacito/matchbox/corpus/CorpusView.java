package fr.cnrs.lacito.matchbox.corpus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Read-only access to the texts of a corpus. Safe to use from any thread. */
public interface CorpusView {

    /** Texts, sorted by id. */
    List<Text> texts();

    Optional<Text> text(TextId id);

    /** Text an object belongs to; empty for an object of an outdated parse. */
    default Optional<Text> textOf(CorpusObject object) {
        return text(object.getTextCoordinate().text()).filter(t -> t.owns(object));
    }

    /** Objects of the given type in all texts, nested objects included. */
    default <T> Stream<T> objectsOf(Class<T> type) {
        return texts().stream().flatMap(t -> t.objectsOf(type).stream());
    }

    /** Diagnostics of all texts. */
    default Stream<Diagnostic> diagnostics() {
        return texts().stream().flatMap(t -> t.diagnostics().stream());
    }
}
