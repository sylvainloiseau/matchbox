package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import fr.cnrs.lacito.matchbox.model.lexicon.history.DictionaryHistory;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntrySnapshot;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryInputs;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult;
import fr.cnrs.lacito.matchbox.model.lexicon.history.ScriptSource;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder;
import fr.cnrs.lacito.matchbox.settings.DictionaryHistorySettingsData;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

/**
 * The history of the project's dictionary for the window: it feeds a {@link DictionaryHistory}
 * (one per project, on its own worker thread) with the mutation commands of the corpus in the
 * project's file order and the context dictionary, publishes each new {@link HistoryResult} on the
 * JavaFX thread, gives it to the checks ({@link Problems}: the dynamic errors, and the patched
 * dictionary for the gloss check when the history is clean) and summarises it on the status line.
 *
 * <p>A text is sent again only when its commands or their errors change (typing prose does not
 * recompute the history), and on changes of the project definition (context dictionary, file
 * order), after a save (the order by modification date), and of the languages of an empty
 * dictionary. Used on the JavaFX thread.</p>
 */
public final class DictionaryHistoryService {

    private final ProjectSession session;
    private final Problems problems;
    private final UserSettings settings;
    private final StatusLine status;
    private final Executor apply;
    private final ReadOnlyObjectWrapper<HistoryResult> result = new ReadOnlyObjectWrapper<>(HistoryResult.computing());
    private final ReadOnlyBooleanWrapper computing = new ReadOnlyBooleanWrapper(false);
    private final Map<TextId, String> fingerprints = new HashMap<>();
    private DictionaryHistory history;
    private ChangeListener<Number> definitionListener;
    private ChangeListener<Boolean> dirtyListener;
    private Project listened;

    DictionaryHistoryService(ProjectSession session, Problems problems, UserSettings settings, StatusLine status,
                             Executor apply) {
        this.session = session;
        this.problems = problems;
        this.settings = settings;
        this.status = status;
        this.apply = apply;
        session.addCorpusListener((id, before, after) -> textChanged(id, after));
        session.projectProperty().addListener((obs, o, n) -> projectChanged(n));
        if (settings != null) {
            settings.dictionaryHistoryProperty().addListener((obs, o, n) -> {
                if (o == null || n == null || !o.emptyObjectLanguage().equals(n.emptyObjectLanguage())
                    || !o.emptyMetaLanguage().equals(n.emptyMetaLanguage())) {
                    submit();
                }
            });
        }
        projectChanged(session.project());
    }

    /** The history last computed (a "computing" result before the first one). */
    public ReadOnlyObjectProperty<HistoryResult> resultProperty() {
        return result.getReadOnlyProperty();
    }

    public HistoryResult result() {
        return result.get();
    }

    /** True from a change until its history is published. */
    public ReadOnlyBooleanProperty computingProperty() {
        return computing.getReadOnlyProperty();
    }

    /** An entry as it was at a position of a history (detached copy), completed on the JavaFX thread. */
    public CompletableFuture<Optional<EntrySnapshot>> entryAt(HistoryResult of, String entryId, int position) {
        if (history == null) return CompletableFuture.completedFuture(Optional.empty());
        CompletableFuture<Optional<EntrySnapshot>> onFx = new CompletableFuture<>();
        history.entryAt(of, entryId, position).whenComplete((v, e) -> apply.execute(() -> {
            if (e != null) onFx.completeExceptionally(e);
            else onFx.complete(v);
        }));
        return onFx;
    }

    /** Writes the patched dictionary of a clean history to a new file; completed on the JavaFX thread. */
    public CompletableFuture<Void> export(HistoryResult of, Path file) {
        if (history == null) return CompletableFuture.failedFuture(new IllegalStateException("no project"));
        CompletableFuture<Void> onFx = new CompletableFuture<>();
        history.export(of, file).whenComplete((v, e) -> apply.execute(() -> {
            if (e != null) onFx.completeExceptionally(e);
            else onFx.complete(null);
        }));
        return onFx;
    }

    /** Applies every command again (Dictionary History › Recompute history). */
    public void recompute() {
        if (history != null) {
            computing.set(true);
            history.recompute();
        }
    }

    /** The texts holding commands, in the order of the history. */
    public List<TextId> orderedTexts() {
        return inputs().map(in -> in.scripts().stream().map(ScriptSource::text).toList()).orElse(List.of());
    }

    // ------------------------------------------------------------------

    private void projectChanged(Project project) {
        if (listened != null) {
            if (definitionListener != null) listened.definitionRevisionProperty().removeListener(definitionListener);
            if (dirtyListener != null) listened.anyDocumentDirtyProperty().removeListener(dirtyListener);
        }
        listened = project;
        if (history != null) {
            history.close();
            history = null;
        }
        fingerprints.clear();
        result.set(HistoryResult.computing());
        if (project == null) {
            computing.set(false);
            problems.setHistory(Optional.empty(), Optional.empty());
            return;
        }
        history = new DictionaryHistory(apply, this::published);
        definitionListener = (obs, o, n) -> submit();
        dirtyListener = (obs, o, n) -> {
            if (!n && project.getFileOrder() == ProjectFileOrder.MODIFIED) submit();   // saved: times changed
        };
        project.definitionRevisionProperty().addListener(definitionListener);
        project.anyDocumentDirtyProperty().addListener(dirtyListener);
        for (Text t : session.corpus().texts()) {
            ScriptSource s = ScriptSource.of(t);
            if (!s.isEmpty()) fingerprints.put(t.id(), s.fingerprint());
        }
        submit();
    }

    private void textChanged(TextId id, Optional<Text> after) {
        if (history == null) return;
        String fp = after.map(ScriptSource::of).filter(s -> !s.isEmpty()).map(ScriptSource::fingerprint).orElse(null);
        if (Objects.equals(fp, fingerprints.get(id))) return;
        if (fp == null) fingerprints.remove(id);
        else fingerprints.put(id, fp);
        submit();
    }

    private void submit() {
        if (history == null) return;
        Optional<HistoryInputs> in = inputs();
        if (in.isEmpty()) return;
        computing.set(true);
        history.submit(in.get());
    }

    private Optional<HistoryInputs> inputs() {
        Project project = session.project();
        if (project == null) return Optional.empty();
        List<ScriptSource> scripts = new ArrayList<>();
        Map<Path, Text> byPath = new HashMap<>();
        for (Text t : session.corpus().texts()) {
            ScriptSource s = ScriptSource.of(t);
            if (!s.isEmpty()) {
                scripts.add(s);
                byPath.put(t.id().path(), t);
            }
        }
        Function<Path, Optional<Instant>> date = date(project.getFileOrder(), byPath);
        List<Path> order = project.getFileOrder().order(byPath.keySet(), date);
        Map<Path, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        scripts.sort(java.util.Comparator.comparingInt(s -> rank.getOrDefault(s.text().path(), Integer.MAX_VALUE)));
        Optional<Path> dictionary = project.getContextDictionary();
        long stamp = dictionary.map(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (Exception e) {
                return -2L;
            }
        }).orElse(-1L);
        DictionaryHistorySettingsData s = settings == null ? DictionaryHistorySettingsData.defaults()
            : Optional.ofNullable(settings.dictionaryHistoryProperty().get()).orElse(DictionaryHistorySettingsData.defaults());
        return Optional.of(new HistoryInputs(dictionary, stamp, scripts, s.emptyObjectLanguage(), s.emptyMetaLanguage()));
    }

    /** The date of a file for an order by date: on disk, or the date of its header (cached for one sort). */
    private static Function<Path, Optional<Instant>> date(ProjectFileOrder order, Map<Path, Text> texts) {
        Map<Path, Optional<Instant>> cache = new HashMap<>();
        return switch (order) {
            case PATH -> p -> Optional.empty();
            case MODIFIED -> p -> cache.computeIfAbsent(p, q -> {
                try {
                    return Optional.of(Files.getLastModifiedTime(q).toInstant());
                } catch (Exception e) {
                    return Optional.empty();
                }
            });
            case HEADER_DATE -> p -> cache.computeIfAbsent(p, q -> {
                Text t = texts.get(q);
                return t == null ? Optional.empty() : ProjectFileOrder.parseHeaderDate(t.properties().get("date"));
            });
        };
    }

    private void published(HistoryResult r) {
        computing.set(false);
        result.set(r);
        Optional<LexiconIndex> patched = history == null ? Optional.empty() : history.patchedIndex();
        problems.setHistory(Optional.of(r), patched);
        summarize(r);
    }

    /** One line on the status bar after each recompute (S9). */
    private void summarize(HistoryResult r) {
        if (r.commandCount() == 0) return;
        switch (r.status()) {
            case HistoryResult.Status.Clean c -> {
                long created = r.entries().stream().filter(e -> e.created()).count();
                long changed = r.entries().stream().filter(e -> e.changed() && !e.created()).count();
                status.info("Dictionary: " + r.entries().size() + " entries (" + created + " created, " + changed
                    + " changed, " + r.deleted().size() + " deleted) by " + r.commandCount() + " commands in "
                    + r.fileCount() + " file" + (r.fileCount() == 1 ? "" : "s"));
            }
            case HistoryResult.Status.NotTried n -> status.warn("Dictionary commands not applied: " + n.errorCount()
                + " syntax or static error" + (n.errorCount() == 1 ? "" : "s") + " (first: "
                + n.firstText().path().getFileName() + ":" + n.firstLine() + ")");
            case HistoryResult.Status.StoppedAt s -> status.warn("Dictionary commands stopped at "
                + s.text().path().getFileName() + ":" + s.line() + ": " + s.message());
            case HistoryResult.Status.Failed f -> status.warn("Dictionary history: " + f.message());
            case HistoryResult.Status.Computing c -> {
            }
        }
    }

    void close() {
        if (history != null) {
            history.close();
            history = null;
        }
    }
}
