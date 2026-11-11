package fr.cnrs.lacito.matchbox.model.lexicon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The context dictionary of a project (a LIFT file), loaded in the background: when its path is set,
 * and again on {@link #reload()} or when the file changed ({@link #reloadIfModified()}). The index
 * and the state are published through the apply executor (the JavaFX thread in the application).
 */
public final class ContextDictionary implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(ContextDictionary.class.getName());

    /** Where the dictionary stands, for the user. */
    public sealed interface State {
        record None() implements State {}
        record Loading(Path file) implements State {}
        record Loaded(Path file, int entries, int problems) implements State {}
        record Failed(Path file, String message) implements State {}
    }

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "context-dictionary");
        t.setDaemon(true);
        return t;
    });
    private final Executor apply;
    private final Consumer<ContextDictionary> changed;
    private volatile Optional<Path> path = Optional.empty();
    private volatile Optional<LexiconIndex> index = Optional.empty();
    private volatile State state = new State.None();
    private volatile FileTime loadedTime;
    private long generation;

    /**
     * @param apply   where the results are published
     * @param changed called (through {@code apply}) when the index or the state changed
     */
    public ContextDictionary(Executor apply, Consumer<ContextDictionary> changed) {
        this.apply = apply;
        this.changed = changed;
    }

    public Optional<LexiconIndex> index() {
        return index;
    }

    public State state() {
        return state;
    }

    /** Sets the dictionary file (empty: none) and loads it if it changed. */
    public synchronized void setPath(Optional<Path> newPath) {
        if (newPath.equals(path)) return;
        path = newPath;
        load();
    }

    public synchronized void reload() {
        load();
    }

    /** Loads the file again if its modification time changed since it was loaded. */
    public synchronized void reloadIfModified() {
        if (path.isEmpty() || loadedTime == null) return;
        try {
            if (!Files.getLastModifiedTime(path.get()).equals(loadedTime)) load();
        } catch (Exception e) {
            // the next load reports it
        }
    }

    private void load() {
        long current = ++generation;
        Optional<Path> file = path;
        if (file.isEmpty()) {
            publish(current, Optional.empty(), new State.None(), null);
            return;
        }
        publish(current, index, new State.Loading(file.get()), loadedTime);
        loader.execute(() -> {
            try {
                FileTime time = Files.getLastModifiedTime(file.get());
                LiftLexicon lexicon = LiftLexicon.load(file.get());
                LexiconIndex i = new LexiconIndex(lexicon);
                publish(current, Optional.of(i), new State.Loaded(file.get(), i.size(), lexicon.loadDiagnostics()), time);
            } catch (Exception | LinkageError e) {
                logger.log(Level.WARNING, "Cannot load the context dictionary " + file.get(), e);
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                publish(current, Optional.empty(), new State.Failed(file.get(), message), null);
            }
        });
    }

    private void publish(long gen, Optional<LexiconIndex> newIndex, State newState, FileTime time) {
        apply.execute(() -> {
            synchronized (this) {
                if (gen != generation) return; // superseded
                index = newIndex;
                state = newState;
                loadedTime = time;
            }
            changed.accept(this);
        });
    }

    @Override
    public void close() {
        loader.shutdownNow();
    }
}
