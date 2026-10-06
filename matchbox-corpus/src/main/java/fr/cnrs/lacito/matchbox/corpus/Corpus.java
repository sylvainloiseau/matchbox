package fr.cnrs.lacito.matchbox.corpus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * The texts of a project, one immutable {@link Text} snapshot per text.
 *
 * <p>Writes ({@link #put}, {@link #remove}) come from one thread (the JavaFX thread in the GUI);
 * reads are consistent from any thread, since the map is replaced whole and texts are immutable.
 */
public final class Corpus implements CorpusView {

    private volatile Map<TextId, Text> texts = Map.of();
    private final List<CorpusListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public List<Text> texts() {
        return texts.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    @Override
    public Optional<Text> text(TextId id) {
        return Optional.ofNullable(texts.get(id));
    }

    /** Adds a text, or replaces the text with the same id. */
    public void put(Text text) {
        Text before;
        synchronized (this) {
            Map<TextId, Text> copy = new HashMap<>(texts);
            before = copy.put(text.id(), text);
            texts = Map.copyOf(copy);
        }
        fire(text.id(), Optional.ofNullable(before), Optional.of(text));
    }

    /** Removes a text; nothing happens if there is none. */
    public void remove(TextId id) {
        Text before;
        synchronized (this) {
            if (!texts.containsKey(id)) return;
            Map<TextId, Text> copy = new HashMap<>(texts);
            before = copy.remove(id);
            texts = Map.copyOf(copy);
        }
        fire(id, Optional.of(before), Optional.empty());
    }

    public void addListener(CorpusListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CorpusListener listener) {
        listeners.remove(listener);
    }

    private void fire(TextId id, Optional<Text> before, Optional<Text> after) {
        for (CorpusListener l : listeners) {
            l.textChanged(id, before, after);
        }
    }

    /** An object followed by all its descendants, depth-first. */
    public static Stream<CorpusObject> selfAndDescendants(CorpusObject o) {
        return Stream.concat(
            Stream.of(o),
            o.getChildren().stream().flatMap(Corpus::selfAndDescendants)
        );
    }
}
