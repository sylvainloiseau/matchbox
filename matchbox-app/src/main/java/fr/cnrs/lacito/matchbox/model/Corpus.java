package fr.cnrs.lacito.matchbox.model;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The corpus objects and parse errors of a project, stored per file and per grammar.
 *
 * <p>Results are replaced file by file ({@link #setResults}): re-parsing a file replaces exactly
 * what that file produced. Objects with an invalid span are rejected and reported as errors.
 *
 * <p>Not thread-safe: in the GUI, all calls must happen on the JavaFX thread.
 */
public class Corpus {

    private static final Logger logger = Logger.getLogger(Corpus.class.getName());

    /** file → (grammar id → validated result), grammars in the order they were applied. */
    private final Map<Path, Map<String, ParseResult>> results = new HashMap<>();
    /** file → the text its results were computed from (null text is not stored). */
    private final Map<Path, String> parsedTexts = new HashMap<>();

    private final ObservableList<CorpusObject> corpus = FXCollections.observableArrayList();
    private final ObservableList<ParseError> errors = FXCollections.observableArrayList();
    private final List<Consumer<Path>> fileListeners = new CopyOnWriteArrayList<>();

    /** All top-level objects of the project (derived, read-only). */
    public ObservableList<CorpusObject> getCorpus() {
        return FXCollections.unmodifiableObservableList(corpus);
    }

    /** All parse errors of the project (derived, read-only). */
    public ObservableList<ParseError> getErrors() {
        return FXCollections.unmodifiableObservableList(errors);
    }

    /** Called with the file path each time the results of a file are replaced or removed. */
    public void addFileListener(Consumer<Path> listener) {
        fileListeners.add(listener);
    }

    public void removeFileListener(Consumer<Path> listener) {
        fileListeners.remove(listener);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Replaces all results of a file.
     *
     * @param file          the parsed file
     * @param resultsByGrammar results keyed by grammar id, in grammar rank order
     * @param text          the text that was parsed, used to validate spans
     */
    public void setResults(Path file, Map<String, ParseResult> resultsByGrammar, String text) {
        Map<String, ParseResult> validated = new LinkedHashMap<>();
        resultsByGrammar.forEach((grammarId, result) ->
            validated.put(grammarId, validate(file, grammarId, result, text))
        );
        Map<String, ParseResult> previous = results.put(file, validated);
        if (text != null) {
            parsedTexts.put(file, text);
        } else {
            parsedTexts.remove(file);
        }
        if (previous != null) {
            removeDerived(previous);
        }
        for (ParseResult r : validated.values()) {
            corpus.addAll(r.objects());
            errors.addAll(r.errors());
        }
        fireFileChanged(file);
    }

    /** Removes everything a file produced. */
    public void removeFile(Path file) {
        Map<String, ParseResult> previous = results.remove(file);
        parsedTexts.remove(file);
        if (previous != null) {
            removeDerived(previous);
            fireFileChanged(file);
        }
    }

    private void removeDerived(Map<String, ParseResult> previous) {
        for (ParseResult r : previous.values()) {
            removeByIdentity(corpus, r.objects());
            removeByIdentity(errors, r.errors());
        }
    }

    private static <T> void removeByIdentity(ObservableList<T> list, List<? extends T> toRemove) {
        if (toRemove.isEmpty()) return;
        var set = Collections.newSetFromMap(new java.util.IdentityHashMap<T, Boolean>());
        set.addAll(toRemove);
        list.removeIf(set::contains);
    }

    private void fireFileChanged(Path file) {
        for (Consumer<Path> l : fileListeners) {
            l.accept(file);
        }
    }

    /**
     * Drops objects whose span (or a descendant's span) is invalid for {@code text}, or lies
     * outside its parent's span, and reports them as errors.
     */
    static ParseResult validate(Path file, String grammarId, ParseResult result, String text) {
        List<CorpusObject> valid = new ArrayList<>();
        List<ParseError> errs = new ArrayList<>(result.errors());
        int length = text == null ? Integer.MAX_VALUE : text.length();
        for (CorpusObject o : result.objects()) {
            String problem = o == null ? "null object" : findProblem(o, null, file, length);
            if (problem == null) {
                valid.add(o);
                if (!o.getKind().startsWith(grammarId + ":")) {
                    logger.warning("Kind '" + o.getKind() + "' is not prefixed by the grammar id '" + grammarId + "'");
                }
            } else {
                int line = o == null || o.getTextCoordinate() == null ? 0 : o.getTextCoordinate().startLine();
                errs.add(new ParseError(file, grammarId, line, 0,
                    "Object ignored, the grammar returned an invalid span: " + problem));
            }
        }
        return new ParseResult(valid, errs);
    }

    private static String findProblem(CorpusObject o, CorpusObject parent, Path file, int length) {
        TextCoordinate span = o.getTextCoordinate();
        if (span == null) return o.getKind() + " has no span";
        if (o.getKind() == null) return "object without kind at " + span;
        if (!Objects.equals(span.path(), file)) return o.getKind() + " refers to another file: " + span.path();
        if (!span.isValidFor(length)) return o.getKind() + " " + span;
        if (parent != null && !parent.getTextCoordinate().encloses(span)) {
            return o.getKind() + " " + span + " is outside its parent " + parent.getTextCoordinate();
        }
        for (CorpusObject child : o.getChildren()) {
            String p = child == null ? "null child of " + o.getKind() : findProblem(child, o, file, length);
            if (p != null) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Files that have results, sorted by path. */
    public List<Path> files() {
        return results.keySet().stream().sorted().toList();
    }

    /** The text the results of a file were computed from (their spans refer to it). */
    public Optional<String> parsedText(Path file) {
        return Optional.ofNullable(parsedTexts.get(file));
    }

    /** Top-level objects of a file, grammars in rank order. */
    public List<CorpusObject> objectsFor(Path file) {
        List<CorpusObject> list = new ArrayList<>();
        for (ParseResult r : resultsOf(file).values()) {
            list.addAll(r.objects());
        }
        return list;
    }

    /** Objects of a file and all their descendants, depth-first (parents before children). */
    public Stream<CorpusObject> flatten(Path file) {
        return objectsFor(file).stream().flatMap(Corpus::selfAndDescendants);
    }

    /** Objects of the given type in a file, nested objects included. */
    public <T> List<T> objectsOf(Class<T> type, Path file) {
        return flatten(file).filter(type::isInstance).map(type::cast).toList();
    }

    /** Objects of the given type in the whole project, nested objects included. */
    public <T> List<T> objectsOf(Class<T> type) {
        return results.keySet().stream()
            .flatMap(this::flatten)
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    /** Deepest object of the file whose span contains {@code offset}. */
    public Optional<CorpusObject> objectAt(Path file, int offset) {
        return deepestAt(objectsFor(file), offset);
    }

    private static Optional<CorpusObject> deepestAt(List<? extends CorpusObject> objects, int offset) {
        for (CorpusObject o : objects) {
            if (o.getTextCoordinate().contains(offset)) {
                return deepestAt(o.getChildren(), offset).or(() -> Optional.of(o));
            }
        }
        return Optional.empty();
    }

    /** Parse errors of a file, grammars in rank order. */
    public List<ParseError> errorsFor(Path file) {
        List<ParseError> list = new ArrayList<>();
        for (ParseResult r : resultsOf(file).values()) {
            list.addAll(r.errors());
        }
        return list;
    }

    /** Id of the grammar that produced a top-level or nested object of this file, if any. */
    public Optional<String> grammarIdOf(Path file, CorpusObject object) {
        for (var e : resultsOf(file).entrySet()) {
            if (e.getValue().objects().stream().flatMap(Corpus::selfAndDescendants).anyMatch(o -> o == object)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    private Map<String, ParseResult> resultsOf(Path file) {
        return results.getOrDefault(file, Map.of());
    }

    public static Stream<CorpusObject> selfAndDescendants(CorpusObject o) {
        return Stream.concat(
            Stream.of(o),
            o.getChildren().stream().flatMap(Corpus::selfAndDescendants)
        );
    }
}
