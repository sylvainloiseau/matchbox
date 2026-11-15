package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The history of the dictionary of a project, computed in the background: one worker thread owns
 * the {@link HistoryRecorder} (the dictionary and the timeline), so that a recompute and a query
 * never overlap. Inputs are debounced; each recompute has a generation, results are published
 * through the apply executor (the JavaFX thread in the application), and a query names the
 * generation it is for (an older one gets nothing).
 */
public final class DictionaryHistory implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DictionaryHistory.class.getName());

    /** The delay after a change before the history is computed again. */
    public static final long DEBOUNCE_MS = 400;

    private static final int CACHE_SIZE = 32;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dictionary-history");
        t.setDaemon(true);
        return t;
    });
    private final Executor apply;
    private final Consumer<HistoryResult> published;
    private final HistoryRecorder recorder = new HistoryRecorder();
    private final long debounceMs;

    /** The generation of the latest inputs; written on any thread, read on the worker. */
    private volatile long generation;
    /** The generation of the history the recorder holds (worker only). */
    private long computed = -1;
    private volatile HistoryResult current = HistoryResult.computing();
    private volatile Optional<LexiconIndex> lexicon = Optional.empty();

    /** Snapshots by (generation, entry, position), most recent last (worker only). */
    private final Map<String, Optional<EntrySnapshot>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<EntrySnapshot>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * @param apply     where results are published
     * @param published told of each new history, through {@code apply}
     */
    public DictionaryHistory(Executor apply, Consumer<HistoryResult> published) {
        this(apply, published, DEBOUNCE_MS);
    }

    /** With another delay (tests). */
    public DictionaryHistory(Executor apply, Consumer<HistoryResult> published, long debounceMs) {
        this.apply = apply;
        this.published = published;
        this.debounceMs = debounceMs;
    }

    /** The history last published. */
    public HistoryResult current() {
        return current;
    }

    /** The patched dictionary as an index (the dictionary check), when the history is clean. */
    public Optional<LexiconIndex> patchedIndex() {
        return lexicon;
    }

    /** New inputs: the history is computed again after the delay, unless newer inputs come first. */
    public void submit(HistoryInputs inputs) {
        long gen = ++generation;
        worker.schedule(() -> run(gen, () -> recorder.update(inputs, gen)), debounceMs, TimeUnit.MILLISECONDS);
    }

    /** Applies every command again (Recompute history), with the inputs of the last computation. */
    public void recompute() {
        long gen = ++generation;
        worker.execute(() -> run(gen, () -> recorder.recompute(gen)));
    }

    private void run(long gen, java.util.function.Supplier<HistoryResult> computation) {
        if (gen != generation) {
            return;     // superseded during the delay
        }
        try {
            HistoryResult result = computation.get();
            computed = gen;
            cache.clear();
            Optional<LexiconIndex> index = recorder.lexicon(result).map(LexiconIndex::new);
            apply.execute(() -> {
                if (gen != generation) return;
                current = result;
                lexicon = index;
                published.accept(result);
            });
        } catch (RuntimeException | LinkageError e) {
            LOGGER.log(Level.WARNING, "Cannot compute the dictionary history", e);
            HistoryResult failed = new HistoryResult(gen, new HistoryResult.Status.Failed(
                "Internal error: " + e), false, 0, 0, Map.of(), Map.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), Map.of(), 0);
            apply.execute(() -> {
                if (gen != generation) return;
                current = failed;
                lexicon = Optional.empty();
                published.accept(failed);
            });
        }
    }

    /**
     * An entry as it was at a position of a history, copied out of the dictionary, on the worker.
     *
     * @param result   the history the position belongs to
     * @param entryId  the LIFT id of the entry in that history
     * @param position the position (0: before any command; {@code result.shownPosition()}: the end)
     * @return the copy; empty when the history is no longer the current one, or has no dictionary
     */
    public CompletableFuture<Optional<EntrySnapshot>> entryAt(HistoryResult result, String entryId, int position) {
        CompletableFuture<Optional<EntrySnapshot>> future = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                if (result.generation() != computed) {
                    future.complete(Optional.empty());
                    return;
                }
                String key = result.generation() + "\u0000" + entryId + "\u0000" + position;
                Optional<EntrySnapshot> snapshot = cache.get(key);
                if (snapshot == null) {
                    snapshot = recorder.entryAt(entryId, position);
                    cache.put(key, snapshot);
                }
                future.complete(snapshot);
            } catch (RuntimeException | LinkageError e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /** Saves the patched dictionary of a clean history to a new LIFT file, on the worker. */
    public CompletableFuture<Void> export(HistoryResult result, Path file) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                if (!result.isClean() || result.generation() != computed) {
                    throw new IllegalStateException("The history changed, or is not clean: nothing was written");
                }
                recorder.export(file);
                future.complete(null);
            } catch (Exception | LinkageError e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public void close() {
        generation++;
        worker.shutdownNow();
    }
}
