package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.EditRebase;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.gui.edit.ViewEditService;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemIndex;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemRow;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.check.CheckInputs;
import fr.cnrs.lacito.matchbox.model.check.GlossCheckSettings;
import fr.cnrs.lacito.matchbox.model.check.ProjectChecks;
import fr.cnrs.lacito.matchbox.model.lexicon.ContextDictionary;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The problems of the project for the window: the {@link ProblemIndex} kept up to date with the
 * corpus, going to a problem (its offsets moved to the current text of the document) and applying
 * a fix. One per window, in {@link Workbench}; used on the JavaFX thread.
 */
public final class Problems {

    private final ProjectSession session;
    private final Navigator navigator;
    private final ViewEditService edits;
    private final StatusLine status;
    private final Supplier<Optional<Focus>> focus;
    private final ProblemIndex index = new ProblemIndex();
    private final ProjectChecks checks = new ProjectChecks();
    private final ContextDictionary dictionary;
    private final java.util.List<Runnable> checksListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private javafx.beans.value.ChangeListener<Number> definitionListener;

    Problems(ProjectSession session, Navigator navigator, ViewEditService edits, StatusLine status,
             Supplier<Optional<Focus>> focus, java.util.concurrent.Executor apply) {
        this.session = session;
        this.navigator = navigator;
        this.edits = edits;
        this.status = status;
        this.focus = focus;
        this.dictionary = new ContextDictionary(apply, this::dictionaryChanged);
        session.addCorpusListener((id, before, after) -> index.update(id, after, this::diagnosticsOf));
        session.projectProperty().addListener((obs, o, n) -> projectChanged(o, n));
        checks.addListener(() -> {
            rebuild();
            checksListeners.forEach(Runnable::run);
        });
        projectChanged(null, session.project());
    }

    private void projectChanged(Project old, Project project) {
        if (old != null && definitionListener != null) old.definitionRevisionProperty().removeListener(definitionListener);
        if (old != null) old.directoryProperty().removeListener(directoryListener);
        definitionListener = (obs, o, n) -> definitionChanged();
        if (project != null) {
            project.definitionRevisionProperty().addListener(definitionListener);
            // Save as…: media references are looked for in another project directory
            project.directoryProperty().addListener(directoryListener);
        }
        definitionChanged();
        rebuild();
    }

    private final javafx.beans.InvalidationListener directoryListener = o -> updateInputs();

    /** The project's dictionary path or gloss settings may have changed. */
    private void definitionChanged() {
        Project project = session.project();
        dictionary.setPath(project == null ? Optional.empty() : project.getContextDictionary());
        updateInputs();
    }

    private Optional<fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult> history = Optional.empty();
    private Optional<fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex> patched = Optional.empty();

    /**
     * The last history of the dictionary: its problems become findings of the "Dictionary history"
     * check, and when it is clean the gloss check compares with the patched dictionary.
     */
    void setHistory(Optional<fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult> result,
                    Optional<fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex> patchedIndex) {
        // a history without command changes no finding (and the patched dictionary is the context one)
        var relevant = result.filter(r -> r.commandCount() > 0 || !r.diagnostics().isEmpty());
        var newPatched = relevant.isPresent() && relevant.get().isClean() ? patchedIndex : Optional.<fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex>empty();
        // what the checks read: the problems of the history, and the patched dictionary (its entries)
        boolean same = relevant.isPresent() == history.isPresent()
            && newPatched.isPresent() == patched.isPresent()
            && (relevant.isEmpty() || relevant.get().diagnostics().equals(history.get().diagnostics())
                && (newPatched.isEmpty() || relevant.get().entries().equals(history.get().entries())));
        if (same) return;   // the findings would be the same: the editor is not refreshed for nothing
        this.history = relevant;
        this.patched = newPatched;
        updateInputs();
    }

    private void updateInputs() {
        Project project = session.project();
        // the patched dictionary when the history is clean (the commands of the notes count), else the context one
        var index = patched.isPresent() ? patched : dictionary.index();
        checks.setInputs(new CheckInputs(project == null ? GlossCheckSettings.defaults() : project.getGlossCheck(),
            index, history, project == null ? Optional.empty() : project.directory()));
    }

    private void dictionaryChanged(ContextDictionary d) {
        if (d.state() instanceof ContextDictionary.State.Failed f) {
            status.warn(f.file().getFileName() + " could not be loaded: " + f.message());
        } else if (d.state() instanceof ContextDictionary.State.Loaded l && l.problems() > 0) {
            status.info(l.file().getFileName() + " loaded (" + l.entries() + " entries; " + l.problems()
                + " parts not read, see the log)");
        }
        updateInputs();
    }

    private void rebuild() {
        index.clear();
        for (Text t : session.corpus().texts()) {
            index.update(t.id(), Optional.of(t), this::diagnosticsOf);
        }
    }

    /** The diagnostics of a text: those of its parse and the findings of the checks of the application. */
    public java.util.List<Diagnostic> diagnosticsOf(Text text) {
        java.util.List<Diagnostic> found = checks.of(text);
        if (found.isEmpty()) return text.diagnostics();
        java.util.List<Diagnostic> all = new java.util.ArrayList<>(text.diagnostics());
        all.addAll(found);
        return all;
    }

    /** The checks of the application (IGT structure, gloss labels, dictionary). */
    public ProjectChecks checks() {
        return checks;
    }

    public ContextDictionary dictionary() {
        return dictionary;
    }

    /** Called when the findings of the checks changed for every text (settings, dictionary). */
    public void addChecksListener(Runnable listener) {
        checksListeners.add(listener);
    }

    /** Stops the loading of the dictionary (the window closes). */
    void close() {
        dictionary.close();
    }

    /** The findings of the checks about an object of the corpus (its span), with their ids, for its view. */
    public java.util.List<fr.cnrs.lacito.matchbox.grammarfx.ViewFinding> findings(fr.cnrs.lacito.matchbox.corpus.CorpusObject object) {
        Optional<Text> text = session.corpus().textOf(object);
        if (text.isEmpty()) return java.util.List.of();
        var span = object.getTextCoordinate();
        java.util.List<fr.cnrs.lacito.matchbox.grammarfx.ViewFinding> result = new java.util.ArrayList<>();
        java.util.List<Diagnostic> found = checks.of(text.get());
        for (int i = 0; i < found.size(); i++) {
            Diagnostic d = found.get(i);
            if (d.startIndex() < span.startIndex() || d.startIndex() > span.stopIndex()) continue;
            result.add(new fr.cnrs.lacito.matchbox.grammarfx.ViewFinding(String.valueOf(i), d.span().orElse(span),
                d.severity(), d.grammarId(), d.message(), d.fixes().stream().map(Diagnostic.Fix::label).toList()));
        }
        return result;
    }

    /** Applies fix {@code index} of a finding given by {@link #findings}. */
    public EditOutcome applyFix(fr.cnrs.lacito.matchbox.corpus.CorpusObject object,
                                fr.cnrs.lacito.matchbox.grammarfx.ViewFinding finding, int index) {
        Optional<ProblemRow> row = rowOf(object, finding);
        if (row.isEmpty() || index >= row.get().diagnostic().fixes().size()) {
            return new EditOutcome.Stale("The text changed: the finding is no longer there");
        }
        return applyFix(row.get(), row.get().diagnostic().fixes().get(index));
    }

    /**
     * The first fix of each finding, as one change: insertions at the same offset (e.g. lines declared
     * above an IGT) are joined in order.
     */
    public EditOutcome applyFixes(fr.cnrs.lacito.matchbox.corpus.CorpusObject object,
                                  java.util.List<fr.cnrs.lacito.matchbox.grammarfx.ViewFinding> findings, String description) {
        Optional<Text> text = session.corpus().textOf(object);
        if (text.isEmpty()) return new EditOutcome.Stale("The text changed");
        java.util.Map<Integer, StringBuilder> inserts = new java.util.TreeMap<>();
        java.util.List<fr.cnrs.lacito.matchbox.corpus.TextEdit> others = new java.util.ArrayList<>();
        for (var f : findings) {
            Optional<ProblemRow> row = rowOf(object, f);
            if (row.isEmpty() || row.get().diagnostic().fixes().isEmpty()) continue;
            for (var e : row.get().diagnostic().fixes().get(0).edits()) {
                if (e.start() == e.end()) {
                    inserts.computeIfAbsent(e.start(), k -> new StringBuilder()).append(e.replacement());
                } else {
                    others.add(e);
                }
            }
        }
        java.util.List<fr.cnrs.lacito.matchbox.corpus.TextEdit> all = new java.util.ArrayList<>(others);
        inserts.forEach((at, s) -> all.add(fr.cnrs.lacito.matchbox.corpus.TextEdit.insert(at, s.toString())));
        if (all.isEmpty()) return new EditOutcome.Applied();
        int offset = all.stream().mapToInt(fr.cnrs.lacito.matchbox.corpus.TextEdit::start).min().orElse(0);
        return edits.runTextAction(text.get().id(), new TextAction(description, description, offset, all, -1));
    }

    private Optional<ProblemRow> rowOf(fr.cnrs.lacito.matchbox.corpus.CorpusObject object,
                                       fr.cnrs.lacito.matchbox.grammarfx.ViewFinding finding) {
        Optional<Text> text = session.corpus().textOf(object);
        if (text.isEmpty()) return Optional.empty();
        java.util.List<Diagnostic> found = checks.of(text.get());
        int i;
        try {
            i = Integer.parseInt(finding.id());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (i < 0 || i >= found.size() || !found.get(i).message().equals(finding.message())) return Optional.empty();
        return ProblemRow.of(text.get(), java.util.List.of(found.get(i))).stream().findFirst();
    }

    /** Loads the context dictionary again (Current project menu). */
    public void reloadDictionary() {
        dictionary.reload();
    }

    public ProblemIndex index() {
        return index;
    }

    /**
     * Where a problem is now: its range in the parsed text moved to the current text of its document
     * (typing not yet parsed, edits made elsewhere); empty if those characters were changed, in which
     * case {@link #go} goes to the start of the line.
     */
    public Optional<Focus> target(ProblemRow row) {
        Diagnostic d = row.diagnostic();
        Optional<Text> text = session.corpus().text(row.file());
        int start;
        int end;
        if (d.startIndex() >= 0) {
            start = d.startIndex();
            end = Math.max(start, d.stopIndex() + 1);
        } else {
            start = text.map(t -> lineStart(t.content(), d.line()) + Math.max(0, d.column())).orElse(0);
            end = start;
        }
        Project project = session.project();
        Optional<TextDocument> document = project == null ? Optional.empty() : project.document(row.file());
        if (document.isEmpty() || row.version() == Text.UNKNOWN_VERSION
            || document.get().getVersion() == row.version()) {
            return Optional.of(new Focus(row.file(), start, end, Optional.empty()));
        }
        long now = document.get().getVersion();
        OptionalInt s = document.get().moveOffset(start, EditRebase.Bias.AFTER, row.version(), now);
        OptionalInt e = document.get().moveOffset(end, EditRebase.Bias.BEFORE, row.version(), now);
        if (s.isEmpty() || e.isEmpty()) return Optional.empty();
        return Optional.of(new Focus(row.file(), s.getAsInt(), Math.max(s.getAsInt(), e.getAsInt()), Optional.empty()));
    }

    /** Shows the problem in the editor (File Mode), as a jump recorded for Back. */
    public void go(ProblemRow row) {
        Optional<Focus> target = target(row);
        if (target.isPresent()) {
            navigator.showInEditor(target.get());
            status.info(row.fileName() + ":" + row.line() + ": " + row.diagnostic().message());
            return;
        }
        // the characters of the problem were changed since the parse: the start of its line, if known
        Project project = session.project();
        int offset = project == null ? 0 : project.document(row.file())
            .map(d -> lineStart(d.getCurrentText(), row.line())).orElse(0);
        navigator.showInEditor(Focus.at(row.file(), offset));
        status.warn("Line " + row.line() + " of " + row.fileName() + " changed since it was parsed");
    }

    /**
     * Applies a fix of a problem to its document, as one undoable change (rebased like any view
     * edit; refused if the text of the problem changed since the parse).
     */
    public EditOutcome applyFix(ProblemRow row, Diagnostic.Fix fix) {
        int offset = fix.edits().stream().mapToInt(fr.cnrs.lacito.matchbox.corpus.TextEdit::start).min()
            .orElse(Math.max(0, row.diagnostic().startIndex()));
        EditOutcome outcome = edits.runTextAction(row.file(),
            new TextAction("Fix: " + lowerFirst(fix.description()), fix.label(), offset, fix.edits(), -1));
        boolean elsewhere = focus.get().map(f -> !f.text().equals(row.file())).orElse(true);
        if (!outcome.isApplied()) {
            status.warn("Fix not applied: " + outcome.message());
        } else if (elsewhere) {
            status.info("Fixed in " + row.fileName(), "Show", () -> go(row));
        }
        return outcome;
    }

    private static String lowerFirst(String s) {
        if (s.isEmpty() || (s.length() > 1 && Character.isUpperCase(s.charAt(1)))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Offset of the start of a 1-based line (line 0 or beyond the end: clamped). */
    static int lineStart(String content, int line) {
        int offset = 0;
        for (int l = 1; l < line; l++) {
            int nl = nextLineStart(content, offset);
            if (nl < 0) return offset;
            offset = nl;
        }
        return offset;
    }

    private static int nextLineStart(String content, int from) {
        for (int i = from; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') return i + 1;
            if (c == '\r') return i + 1 < content.length() && content.charAt(i + 1) == '\n' ? i + 2 : i + 1;
        }
        return -1;
    }

    /** True if the text of a file has changes not saved. */
    public boolean isUnsaved(TextId file) {
        Project project = session.project();
        return project != null && project.document(file).map(TextDocument::getCouldBeWritten).orElse(false);
    }
}
