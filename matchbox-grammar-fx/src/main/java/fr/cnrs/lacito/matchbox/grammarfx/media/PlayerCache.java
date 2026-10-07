package fr.cnrs.lacito.matchbox.grammarfx.media;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One player per file, opened on first use and reused; at most {@code capacity} files open, the
 * least recently used one (except the one in use) closed first. Generic so that it can be tested
 * without the JavaFX media toolkit.
 *
 * @param <P> the player type
 */
public final class PlayerCache<P> {

    private final int capacity;
    private final Function<Path, P> open;
    private final Consumer<P> close;
    /** Access order: least recently used first. */
    private final LinkedHashMap<Path, P> players = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Path, String> errors = new HashMap<>();
    private Path inUse;

    /**
     * @param open  opens a player; a RuntimeException means the file cannot be played (its message
     *              is kept, see {@link #error})
     * @param close releases a player
     */
    public PlayerCache(int capacity, Function<Path, P> open, Consumer<P> close) {
        this.capacity = Math.max(1, capacity);
        this.open = open;
        this.close = close;
    }

    /** The player of a file, opened on first use; empty if it cannot be opened. */
    public Optional<P> get(Path file) {
        Path key = file.toAbsolutePath().normalize();
        P player = players.get(key);
        if (player != null) return Optional.of(player);
        if (errors.containsKey(key)) return Optional.empty();
        try {
            player = open.apply(key);
        } catch (RuntimeException e) {
            errors.put(key, e.getMessage() == null ? e.toString() : e.getMessage());
            return Optional.empty();
        }
        players.put(key, player);
        evict();
        return Optional.of(player);
    }

    private void evict() {
        Iterator<Map.Entry<Path, P>> it = players.entrySet().iterator();
        while (players.size() > capacity && it.hasNext()) {
            Map.Entry<Path, P> e = it.next();
            if (!e.getKey().equals(inUse)) {
                close.accept(e.getValue());
                it.remove();
            }
        }
    }

    /** Why a file could not be opened, if it could not. */
    public Optional<String> error(Path file) {
        return Optional.ofNullable(errors.get(file.toAbsolutePath().normalize()));
    }

    /** The file being played: never closed to make room. */
    public void setInUse(Path file) {
        this.inUse = file == null ? null : file.toAbsolutePath().normalize();
    }

    public int size() {
        return players.size();
    }

    /** Closes every player and forgets the errors (a file may have been fixed). */
    public void closeAll() {
        players.values().forEach(close);
        players.clear();
        errors.clear();
        inUse = null;
    }
}
