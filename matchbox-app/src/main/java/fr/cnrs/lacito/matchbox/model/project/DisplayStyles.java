package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The display styles of a project: one per grammar (all its objects) and per kind of object,
 * keyed by grammar id. Styles of grammars that are unchecked in every file type, or not
 * installed, are kept. Immutable.
 */
public final class DisplayStyles {

    /**
     * Key of a style.
     *
     * @param grammarId id of the grammar
     * @param kind      kind of object ({@code "<grammar id>:<name>"}), null for all the objects of the grammar
     */
    public record Key(String grammarId, String kind) {
        public Key {
            Objects.requireNonNull(grammarId, "grammarId");
        }

        public static Key grammar(String grammarId) {
            return new Key(grammarId, null);
        }
    }

    public static final DisplayStyles NONE = new DisplayStyles(Map.of());

    private final Map<Key, DisplayStyle> styles;

    private DisplayStyles(Map<Key, DisplayStyle> styles) {
        this.styles = Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    }

    /** The styles of the map; empty styles are left out. */
    public static DisplayStyles of(Map<Key, DisplayStyle> styles) {
        Map<Key, DisplayStyle> kept = new LinkedHashMap<>();
        styles.forEach((k, s) -> {
            if (s != null && !s.isEmpty()) kept.put(k, s);
        });
        return new DisplayStyles(kept);
    }

    /** All the styles, in insertion order. */
    public Map<Key, DisplayStyle> asMap() {
        return styles;
    }

    /** The style stored for a key, {@link DisplayStyle#EMPTY} if none. */
    public DisplayStyle get(Key key) {
        return styles.getOrDefault(key, DisplayStyle.EMPTY);
    }

    /** A copy where {@code key} has {@code style} (an empty or null style removes it). */
    public DisplayStyles with(Key key, DisplayStyle style) {
        Map<Key, DisplayStyle> copy = new LinkedHashMap<>(styles);
        if (style == null || style.isEmpty()) {
            copy.remove(key);
        } else {
            copy.put(key, style);
        }
        return new DisplayStyles(copy);
    }

    /** The project's style of an object: its kind's style over its grammar's style. */
    public DisplayStyle resolve(String grammarId, String kind) {
        DisplayStyle grammar = get(Key.grammar(grammarId));
        return kind == null ? grammar : get(new Key(grammarId, kind)).over(grammar);
    }

    public boolean isEmpty() {
        return styles.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DisplayStyles other && styles.equals(other.styles);
    }

    @Override
    public int hashCode() {
        return styles.hashCode();
    }

    @Override
    public String toString() {
        return "DisplayStyles" + styles;
    }
}
