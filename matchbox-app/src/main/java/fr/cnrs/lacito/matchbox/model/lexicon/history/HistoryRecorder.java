package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.liftapi.ExtractOptions;
import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftpatchbox.LiftPatchEngine;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.engine.Effect;
import fr.cnrs.lacito.liftpatchbox.engine.Operation;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import fr.cnrs.lacito.liftpatchbox.error.SourceRange;
import fr.cnrs.lacito.liftpatchbox.timeline.RecordPolicy;
import fr.cnrs.lacito.liftpatchbox.timeline.ScriptRun;
import fr.cnrs.lacito.liftpatchbox.timeline.Timeline;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelineOperation;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelinePosition;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.model.lexicon.LiftLexicon;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the mutation commands of a project to its context dictionary and records the history:
 * the dictionary at every command, what each command did. Confined to one thread (the worker of
 * {@link DictionaryHistory}): it owns the dictionary and lift-patchbox's {@link Timeline}, which
 * change the dictionary in place.
 *
 * <p>Two gates (Dictionary History plan, P7):</p>
 * <ol>
 *   <li>While any text has a syntax or static error of mutation commands (codes starting with
 *       {@code liftpatch.}), or lift-patchbox's validation finds one, nothing is tried: the
 *       dictionary stays the context dictionary.</li>
 *   <li>The scripts (one per text, in the project's file order) are applied in order, and the
 *       series stops at the first script that fails ({@link RecordPolicy#STOP_AT_FIRST_FAILURE}):
 *       that file is rolled back as a whole, later commands are not applied.</li>
 * </ol>
 *
 * <p>A recompute replays only from the first script that changed
 * ({@link Timeline#resumeFrom}); a new context dictionary is loaded again.</p>
 */
public final class HistoryRecorder {

    /** The grammar id of the diagnostics of the history: the name of the check that reports them. */
    public static final String CHECK_ID = "Dictionary history";

    private final LiftPatchEngine engine = new LiftPatchEngine();

    private HistoryInputs inputs;
    private LiftDictionary dictionary;
    private String loadFailure;
    private Set<String> originalIds = Set.of();
    private Map<String, EntryRow> originalRows = Map.of();
    private Timeline timeline;
    private List<String> recordedFingerprints = List.of();
    private List<ScriptSource> recordedScripts = List.of();

    /**
     * Brings the history to these inputs.
     *
     * @param in         what to compute from
     * @param generation the number of this recompute, carried by the result
     * @return the history
     */
    public HistoryResult update(HistoryInputs in, long generation) {
        if (!in.sameDictionary(inputs)) {
            load(in);
        }
        inputs = in;
        List<ScriptSource> scripts = in.scripts().stream().filter(s -> !s.isEmpty()).toList();
        int commandCount = scripts.stream().mapToInt(s -> s.commands().size()).sum();
        if (dictionary == null) {
            return new HistoryResult(generation, new HistoryResult.Status.Failed(loadFailure), false, commandCount,
                scripts.size(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                failureDiagnostic(scripts), 0);
        }

        // Gate 1: syntax and static errors, as the plugins report them, then lift-patchbox's validation.
        List<Diagnostic> errors = new ArrayList<>();
        Map<TextId, List<Diagnostic>> validation = new LinkedHashMap<>();
        for (ScriptSource s : scripts) {
            errors.addAll(s.errors());
        }
        if (errors.isEmpty()) {
            for (ScriptSource s : scripts) {
                ErrorCollector collector = engine.validate(s.script());
                for (LiftPatchError e : collector.errors()) {
                    Diagnostic d = diagnostic(s.text(), e.range(), e.message(), "history." + e.code().id(),
                        Diagnostic.Severity.ERROR);
                    validation.computeIfAbsent(s.text(), t -> new ArrayList<>()).add(d);
                    errors.add(d);
                }
            }
        }
        if (!errors.isEmpty()) {
            if (timeline != null && timeline.isOpen()) {
                timeline.seek(timeline.positions().get(0));
            }
            return notTried(generation, scripts, commandCount, errors, validation);
        }

        // Gate 2: apply in order, stopping at the first failed script.
        List<Script> series = scripts.stream().map(ScriptSource::script).toList();
        List<String> fingerprints = scripts.stream().map(ScriptSource::fingerprint).toList();
        if (timeline == null || !timeline.isOpen()) {
            timeline = engine.record(dictionary, series, RecordPolicy.STOP_AT_FIRST_FAILURE);
        } else {
            int k = 0;
            while (k < fingerprints.size() && k < recordedFingerprints.size()
                && fingerprints.get(k).equals(recordedFingerprints.get(k))) {
                k++;
            }
            boolean unchanged = k == fingerprints.size() && k == recordedFingerprints.size();
            if (unchanged) {
                timeline.seek(timeline.end());
            } else {
                int limit = timeline.stoppedAt().map(ScriptRun::index).orElse(timeline.scripts().size());
                int from = Math.min(k, limit);
                timeline = timeline.resumeFrom(from, series.subList(from, series.size()));
            }
        }
        recordedFingerprints = fingerprints;
        recordedScripts = scripts;
        return applied(generation, scripts, commandCount);
    }

    /** Applies every command again, from the dictionary as loaded, without reading it again. */
    public HistoryResult recompute(long generation) {
        if (timeline != null && timeline.isOpen()) {
            timeline.rewind();
        }
        timeline = null;
        recordedFingerprints = List.of();
        HistoryInputs in = inputs;
        inputs = in;
        return in == null ? HistoryResult.computing() : update(in, generation);
    }

    // ------------------------------------------------------------------ loading

    private void load(HistoryInputs in) {
        timeline = null;
        recordedFingerprints = List.of();
        recordedScripts = List.of();
        dictionary = null;
        loadFailure = null;
        try {
            if (in.dictionary().isPresent()) {
                dictionary = LiftDictionary.loadDictionaryFromFile(new File(in.dictionary().get().toString()));
            } else {
                dictionary = LiftDictionary.makeBuilder()
                    .withObjectLanguages(in.emptyObjectLanguage())
                    .withMetaLanguages(in.emptyMetaLanguage())
                    .build();
            }
        } catch (Exception | LinkageError e) {
            loadFailure = in.dictionary().map(Path::toString).orElse("the empty dictionary") + ": "
                + (e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }
        Set<String> ids = new HashSet<>();
        Map<String, EntryRow> rows = new LinkedHashMap<>();
        for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries()) {
            String id = e.getId().orElse(null);
            if (id == null) continue;
            ids.add(id);
            rows.put(id, row(e, true, false, false, List.of()));
        }
        originalIds = Set.copyOf(ids);
        originalRows = rows;
    }

    // ------------------------------------------------------------------ results

    private HistoryResult notTried(long generation, List<ScriptSource> scripts, int commandCount,
                                   List<Diagnostic> errors, Map<TextId, List<Diagnostic>> validation) {
        Map<CommandKey, CommandStatus> statuses = new HashMap<>();
        for (ScriptSource s : scripts) {
            for (LexiconMutationCommand c : flatten(s.commands())) {
                statuses.put(CommandKey.of(c), CommandStatus.NOT_TRIED);
            }
        }
        Diagnostic first = errors.stream()
            .min(Comparator.comparingInt((Diagnostic d) -> scriptIndex(scripts, d.text()))
                .thenComparingInt(Diagnostic::startIndex))
            .orElseThrow();
        Map<TextId, List<Diagnostic>> diagnostics = new LinkedHashMap<>();
        validation.forEach((t, list) -> diagnostics.put(t, List.copyOf(list)));
        return new HistoryResult(generation,
            new HistoryResult.Status.NotTried(errors.size(), first.text(), first.line(), first.message()),
            inputs.dictionary().isEmpty(), commandCount, scripts.size(), statuses, Map.of(), List.of(),
            List.copyOf(originalRows.values()), List.of(), languages(true), languages(false), diagnostics, 0);
    }

    private static int scriptIndex(List<ScriptSource> scripts, TextId text) {
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).text().equals(text)) return i;
        }
        return Integer.MAX_VALUE;
    }

    private HistoryResult applied(long generation, List<ScriptSource> scripts, int commandCount) {
        List<TimelineOperation> ops = timeline.operations();
        Optional<ScriptRun> stopped = timeline.stoppedAt();
        int failedScript = stopped.map(ScriptRun::index).orElse(Integer.MAX_VALUE);
        int failingOp = -1;
        LiftPatchError failure = null;
        if (stopped.isPresent()) {
            for (TimelineOperation op : stopped.get().operations()) {
                if (op.error() != null) {
                    failingOp = op.index();
                    failure = op.error();
                    break;
                }
            }
            if (failure == null) {
                failure = stopped.get().plan().error();
            }
        }

        // operations → their innermost command
        Map<CommandKey, List<Integer>> commandOps = new HashMap<>();
        Map<Integer, CommandKey> opCommand = new HashMap<>();
        for (TimelineOperation op : ops) {
            ScriptSource s = scripts.get(op.scriptIndex());
            SourceRange r = op.operation().range();
            LexiconMutationCommand c = innermost(s.commands(), r.startOffset());
            if (c != null) {
                CommandKey key = CommandKey.of(c);
                commandOps.computeIfAbsent(key, x -> new ArrayList<>()).add(op.index());
                opCommand.put(op.index(), key);
            }
        }

        // the status of each command
        Map<CommandKey, CommandStatus> statuses = new HashMap<>();
        for (int i = 0; i < scripts.size(); i++) {
            for (LexiconMutationCommand c : scripts.get(i).commands()) {
                status(c, i, failedScript, failingOp, failure, ops, commandOps, statuses);
            }
        }

        // operation rows
        List<OperationRow> rows = new ArrayList<>();
        for (TimelineOperation op : ops) {
            ScriptSource s = scripts.get(op.scriptIndex());
            Operation o = op.operation();
            CommandStatus st;
            if (op.index() == failingOp) st = CommandStatus.ERROR;
            else if (!op.committed()) st = CommandStatus.ROLLED_BACK;
            else st = o.allEffects().isEmpty() ? CommandStatus.NO_CHANGE : CommandStatus.APPLIED;
            rows.add(new OperationRow(op.index(), s.text(), o.range().startOffset(), Math.max(1, o.range().startLine()),
                o.verb(), o.command(), st, effects(o), o.error() == null ? null : o.error().message()));
        }

        // diagnostics: the dynamic error, and the warnings of the applied scripts
        Map<TextId, List<Diagnostic>> diagnostics = new LinkedHashMap<>();
        if (stopped.isPresent() && failure != null) {
            TextId t = scripts.get(failedScript).text();
            diagnostics.computeIfAbsent(t, x -> new ArrayList<>()).add(diagnostic(t, failure.range(),
                failure.message(), "history." + failure.code().id(), Diagnostic.Severity.ERROR));
        }
        for (ScriptRun run : timeline.scripts()) {
            TextId t = scripts.get(run.index()).text();
            for (LiftPatchWarning w : run.plan().warnings()) {
                if (!w.range().isKnown()) continue;
                diagnostics.computeIfAbsent(t, x -> new ArrayList<>()).add(diagnostic(t, w.range(), w.message(),
                    "history." + w.code().id(), Diagnostic.Severity.WARNING));
            }
        }

        // the entries at the end of what was applied
        TimelinePosition end = timeline.end();
        timeline.seek(end);
        Map<String, List<Integer>> entryOps = new HashMap<>();
        Set<String> changed = new HashSet<>();
        for (TimelineOperation op : ops) {
            for (String id : op.entryIds()) {
                entryOps.computeIfAbsent(id, x -> new ArrayList<>()).add(op.index());
                if (op.committed()) changed.add(id);
            }
        }
        List<EntryRow> entries = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries()) {
            String id = e.getId().orElse(null);
            if (id == null) continue;
            present.add(id);
            entries.add(row(e, originalIds.contains(id), changed.contains(id), !originalIds.contains(id),
                entryOps.getOrDefault(id, List.of())));
        }
        List<EntryRow> deleted = new ArrayList<>();
        originalRows.forEach((id, row) -> {
            if (!present.contains(id)) {
                deleted.add(new EntryRow(row.key(), row.forms(), row.glosses(), true, false,
                    entryOps.getOrDefault(id, List.of())));
            }
        });

        HistoryResult.Status status;
        if (stopped.isPresent()) {
            TextId t = scripts.get(failedScript).text();
            int later = 0;
            for (Map.Entry<CommandKey, CommandStatus> e : statuses.entrySet()) {
                if (e.getValue() == CommandStatus.NOT_APPLIED) later++;
            }
            status = new HistoryResult.Status.StoppedAt(t, failure == null ? 1 : Math.max(1, failure.range().startLine()),
                failure == null ? "the script failed" : failure.message(), later);
        } else {
            status = new HistoryResult.Status.Clean();
        }
        return new HistoryResult(generation, status, inputs.dictionary().isEmpty(), commandCount, scripts.size(),
            statuses, commandOps, rows, entries, deleted, languages(true), languages(false), diagnostics, end.index());
    }

    /** Status of a command (and of its children), from its operations and its script. */
    private CommandStatus status(LexiconMutationCommand c, int script, int failedScript, int failingOp,
                                 LiftPatchError failure, List<TimelineOperation> ops,
                                 Map<CommandKey, List<Integer>> commandOps, Map<CommandKey, CommandStatus> out) {
        CommandKey key = CommandKey.of(c);
        List<Integer> own = commandOps.getOrDefault(key, List.of());
        CommandStatus st;
        if (script < failedScript) {
            boolean anyEffect = own.isEmpty() || own.stream().anyMatch(i -> !ops.get(i - 1).operation().allEffects().isEmpty());
            st = anyEffect ? CommandStatus.APPLIED : CommandStatus.NO_CHANGE;
        } else if (script > failedScript) {
            st = CommandStatus.NOT_APPLIED;
        } else if (failingOp > 0 && own.contains(failingOp)) {
            st = CommandStatus.ERROR;
        } else if (failingOp > 0 && !own.isEmpty()) {
            st = own.stream().allMatch(i -> i < failingOp) ? CommandStatus.ROLLED_BACK : CommandStatus.NOT_APPLIED;
        } else {
            int at = failure == null || !failure.range().isKnown() ? Integer.MAX_VALUE : failure.range().startOffset();
            int start = c.getTextCoordinate().startIndex();
            if (failure != null && c.getTextCoordinate().contains(at)) st = CommandStatus.ERROR;
            else st = start < at ? CommandStatus.ROLLED_BACK : CommandStatus.NOT_APPLIED;
        }
        for (CorpusObject child : c.getChildren()) {
            if (child instanceof LexiconMutationCommand inner) {
                st = st.worst(status(inner, script, failedScript, failingOp, failure, ops, commandOps, out));
            }
        }
        out.put(key, st);
        return st;
    }

    private static LexiconMutationCommand innermost(List<? extends CorpusObject> objects, int offset) {
        for (CorpusObject o : objects) {
            if (o instanceof LexiconMutationCommand c && c.getTextCoordinate().contains(offset)) {
                LexiconMutationCommand inner = innermost(c.getChildren(), offset);
                return inner != null ? inner : c;
            }
        }
        return null;
    }

    private static List<LexiconMutationCommand> flatten(List<? extends CorpusObject> objects) {
        List<LexiconMutationCommand> out = new ArrayList<>();
        for (CorpusObject o : objects) {
            if (o instanceof LexiconMutationCommand c) {
                out.add(c);
                out.addAll(flatten(c.getChildren()));
            }
        }
        return out;
    }

    private static List<String> effects(Operation o) {
        List<String> out = new ArrayList<>();
        for (Effect e : o.allEffects()) {
            out.add(describe(e));
        }
        return out;
    }

    /** One effect in words: "created sense", "set gloss@en: pig"… */
    static String describe(Effect e) {
        String where = e.language() == null ? "" : "@" + e.language();
        return switch (e.kind()) {
            case componentCreated -> "created " + e.componentType();
            case componentDeleted -> "deleted " + e.componentType();
            case componentMoved -> "moved " + e.componentType();
            case propertySet -> "set " + e.property() + where + ": " + e.newValue();
            case propertyReplaced -> "changed " + e.property() + where + ": " + e.oldValue() + " → " + e.newValue();
            case propertyRemoved -> "removed " + e.property() + where + (e.oldValue() == null ? "" : ": " + e.oldValue());
            case languageCreated -> "added " + e.languageKind() + " language " + e.language();
            case languageDeleted -> "removed " + e.languageKind() + " language " + e.language();
            case declarationCreated -> "declared " + e.declarationType() + " " + e.name();
            case declarationDeleted -> "removed " + e.declarationType() + " " + e.name();
            case declarationRenamed -> "renamed " + e.declarationType() + " " + e.name() + " → " + e.newName();
        };
    }

    private List<String> languages(boolean object) {
        var manager = object ? dictionary.getObjectLanguageManager() : dictionary.getMetaLanguageManager();
        List<String> langs = new ArrayList<>(manager.getLanguages());
        // the most used first: the default of the language choices
        langs.sort(Comparator.comparingInt((String l) -> -manager.getLanguageOccurrence(l)).thenComparing(l -> l));
        return langs;
    }

    private static EntryRow row(LiftEntry e, boolean stable, boolean changed, boolean created, List<Integer> ops) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Form f : e.getForms().getForms()) {
            String text = f.toPlainText();
            if (text != null && !text.isBlank()) forms.putIfAbsent(f.getLang(), text);
        }
        Map<String, List<String>> glosses = new LinkedHashMap<>();
        for (LiftSense s : e.getSenses()) glosses(s, glosses);
        String first = forms.isEmpty() ? "" : forms.values().iterator().next();
        String id = e.getId().orElse("");
        return new EntryRow(new EntryKey(id, first, stable), forms, glosses, changed, created, ops);
    }

    private static void glosses(LiftSense sense, Map<String, List<String>> out) {
        MultiText g = sense.getGlosses();
        for (Form f : g.getForms()) {
            String text = f.toPlainText();
            if (text != null && !text.isBlank()) out.computeIfAbsent(f.getLang(), k -> new ArrayList<>()).add(text);
        }
        for (LiftSense sub : sense.getSenses()) glosses(sub, out);
    }

    private static Diagnostic diagnostic(TextId text, SourceRange range, String message, String code,
                                         Diagnostic.Severity severity) {
        if (!range.isKnown()) {
            return new Diagnostic(text, CHECK_ID, 1, 0, message, -1, -1, List.of(), severity,
                Diagnostic.Category.SEMANTIC, code, List.of());
        }
        int stop = range.isEmpty() ? range.startOffset() : range.endOffset() - 1;
        return new Diagnostic(text, CHECK_ID, range.startLine(), range.startColumn() - 1, message, range.startOffset(),
            stop, List.of(), severity, Diagnostic.Category.SEMANTIC, code, List.of());
    }

    private Map<TextId, List<Diagnostic>> failureDiagnostic(List<ScriptSource> scripts) {
        if (scripts.isEmpty() || scripts.get(0).commands().isEmpty()) return Map.of();
        LexiconMutationCommand first = scripts.get(0).commands().get(0);
        var span = first.getTextCoordinate();
        return Map.of(span.text(), List.of(new Diagnostic(span.text(), CHECK_ID, span.startLine(), 0,
            "The context dictionary cannot be read: " + loadFailure, span.startIndex(), span.stopIndex(), List.of(),
            Diagnostic.Severity.ERROR, Diagnostic.Category.SEMANTIC, "history.dictionary", List.of())));
    }

    // ------------------------------------------------------------------ entries

    /**
     * An entry as it was at a position of the history, copied out of the dictionary.
     *
     * @param entryId  the LIFT id of the entry
     * @param position the position (0: before any command); past the end: the end
     * @return the copy; empty when there is no dictionary
     */
    public Optional<EntrySnapshot> entryAt(String entryId, int position) {
        if (dictionary == null) return Optional.empty();
        int at = 0;
        if (timeline != null && timeline.isOpen()) {
            List<TimelinePosition> positions = timeline.positions();
            TimelinePosition p = position < 0 || position >= positions.size() ? timeline.end() : positions.get(position);
            timeline.seek(p);
            at = p.index();
        }
        var target = dictionary.getLiftDictionaryRegistry().getEntryOrSenseByLiftId(entryId);
        if (!(target instanceof LiftEntry entry)) {
            return Optional.of(new EntrySnapshot(at, null, Optional.empty()));
        }
        LiftDictionary copy = dictionary.extract(List.of(entry),
            ExtractOptions.DEFAULT.withReferences(ExtractOptions.ReferenceTargets.STUBS));
        LiftEntry copied = copy.getLiftDictionaryRegistry().getEntries().get(0);
        return Optional.of(new EntrySnapshot(at, copy, Optional.of(copied)));
    }

    /** The patched dictionary as a lexicon (the dictionary check of glosses); empty unless clean. */
    public Optional<LiftLexicon> lexicon(HistoryResult result) {
        if (dictionary == null || !result.isClean() || timeline == null) return Optional.empty();
        timeline.seek(timeline.end());
        return Optional.of(LiftLexicon.of("patched dictionary", dictionary));
    }

    /** Saves the dictionary at the end of a clean history to a new file. */
    public void export(Path file) throws Exception {
        if (dictionary == null) throw new IllegalStateException("no dictionary");
        if (timeline != null && timeline.isOpen()) timeline.seek(timeline.end());
        dictionary.save(file.toFile());
    }

    /** The scripts the last history was computed from. */
    List<ScriptSource> recordedScripts() {
        return recordedScripts;
    }

    /** Whether the text is one of the scripts of the history. */
    boolean holds(TextId text) {
        return recordedScripts.stream().anyMatch(s -> s.text().equals(text));
    }

    /** For tests: the set of ids of the context dictionary. */
    Set<String> originalIds() {
        return new LinkedHashSet<>(originalIds);
    }
}
