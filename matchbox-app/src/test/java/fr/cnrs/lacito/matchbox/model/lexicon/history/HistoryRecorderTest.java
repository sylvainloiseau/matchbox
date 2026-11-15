package fr.cnrs.lacito.matchbox.model.lexicon.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammar.liftpatchbox.shortsyntax.LiftPatchShortGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The history of a dictionary over the commands of several files: the two gates (nothing tried
 * while a syntax or static error exists; stop at the first dynamic error, its file rolled back),
 * statuses, entries, snapshots, recomputing from the first changed file.
 */
class HistoryRecorderTest {

    private static final LiftPatchShortGrammarPlugin PLUGIN = new LiftPatchShortGrammarPlugin();

    @TempDir
    Path dir;
    private Path dictionaryFile;

    @BeforeEach
    void dictionary() throws Exception {
        LiftDictionary d = LiftDictionary.makeBuilder().withObjectLanguages("tww").withMetaLanguages("en").build();
        var b = d.getComponentBuilder();
        var mami = b.entry().withForm("tww", "mami").build();
        mami.setId("mami_1");
        b.sense(mami).withGloss("en", "pig").build();
        var efe = b.entry().withForm("tww", "efe").build();
        efe.setId("efe_1");
        b.sense(efe).withGloss("en", "door").build();
        dictionaryFile = dir.resolve("context.lift");
        d.save(dictionaryFile.toFile());
    }

    private static ScriptSource script(String name, String content) {
        TextId id = new TextId(Path.of("/notes/" + name));
        ParseResult r = PLUGIN.getResult(id, content);
        Text t = Text.builder(id, content).add(PLUGIN.getId(), r.objects(), r.diagnostics()).build();
        return ScriptSource.of(t);
    }

    private HistoryInputs inputs(ScriptSource... scripts) {
        return new HistoryInputs(Optional.of(dictionaryFile), 1, List.of(scripts), "und", "en");
    }

    private static Map<String, String> glosses(HistoryResult r) {
        return r.entries().stream().collect(Collectors.toMap(e -> e.form("tww"), e -> e.gloss("en"), (a, b) -> a + "|" + b));
    }

    private static CommandStatus statusOf(HistoryResult r, ScriptSource s, int command) {
        return r.statusOf(CommandKey.of(s.commands().get(command)));
    }

    @Test
    void commandsOfSeveralFilesAreAppliedInOrder() {
        ScriptSource one = script("01.txt", "Monday\nc e(\"lofi\", s(\"water\"))\np /mami/pig\n");
        ScriptSource two = script("02.txt", "s /lofi/water (d@en = \"Fresh water\")\n");
        HistoryResult r = new HistoryRecorder().update(inputs(one, two), 1);
        assertInstanceOf(HistoryResult.Status.Clean.class, r.status());
        assertTrue(r.isClean());
        assertEquals(Map.of("mami", "pig", "efe", "door", "lofi", "water"), glosses(r));
        assertEquals(CommandStatus.APPLIED, statusOf(r, one, 0));
        assertEquals(CommandStatus.NO_CHANGE, statusOf(r, one, 1), "p /mami/pig finds the sense");
        assertEquals(CommandStatus.APPLIED, statusOf(r, two, 0));
        EntryRow lofi = r.entries().stream().filter(e -> e.form("tww").equals("lofi")).findFirst().orElseThrow();
        assertTrue(lofi.created() && lofi.changed());
        assertFalse(lofi.key().stable());
        assertEquals(2, lofi.operations().size());
        EntryRow efe = r.entries().stream().filter(e -> e.form("tww").equals("efe")).findFirst().orElseThrow();
        assertFalse(efe.changed());
        assertTrue(efe.key().stable());
        assertEquals(List.of("tww"), r.objectLanguages());
        assertEquals(2, r.fileCount());
        assertEquals(3, r.commandCount());
    }

    @Test
    void gateOneASyntaxErrorAnywhereMeansNothingIsTried() {
        ScriptSource one = script("01.txt", "c e(\"lofi\", s(\"water\"))\n");
        ScriptSource two = script("02.txt", "c e(\"mafa\", s(\"sun\")\n");     // missing parenthesis
        assertFalse(two.errors().isEmpty());
        HistoryResult r = new HistoryRecorder().update(inputs(one, two), 1);
        HistoryResult.Status.NotTried s = assertInstanceOf(HistoryResult.Status.NotTried.class, r.status());
        assertEquals(two.text(), s.firstText());
        assertEquals(Map.of("mami", "pig", "efe", "door"), glosses(r), "the context dictionary, unchanged");
        assertEquals(CommandStatus.NOT_TRIED, statusOf(r, one, 0));
        assertTrue(r.operations().isEmpty());
        assertFalse(r.isClean());
    }

    @Test
    void gateOneAStaticErrorTheParseDidNotReportIsCaughtByValidation() {
        ScriptSource bad = script("01.txt", "s $nowhere (d@en = \"x\")\nc e(\"lofi\")\n");
        // the same commands with their diagnostics dropped: a plugin that missed the error
        ScriptSource unreported = new ScriptSource(bad.text(), bad.commands(), List.of());
        HistoryResult r = new HistoryRecorder().update(inputs(unreported), 1);
        assertInstanceOf(HistoryResult.Status.NotTried.class, r.status());
        assertFalse(r.diagnosticsOf(bad.text()).isEmpty());
        assertTrue(r.diagnosticsOf(bad.text()).get(0).code().startsWith("history."));
    }

    @Test
    void warningsDoNotBlock() {
        ScriptSource ok = script("01.txt", "c e(\"lofi\", s(\"water\"))\n");
        Diagnostic warning = Diagnostic.warning(ok.text(), PLUGIN.getId(), 1, 0, "doubtful", 0, 3).withCode("liftpatch.W");
        ScriptSource warned = new ScriptSource(ok.text(), ok.commands(), List.of(warning));
        assertTrue(warned.errors().isEmpty() || warned.errors().stream().allMatch(d -> d.severity() == Diagnostic.Severity.WARNING));
        HistoryResult r = new HistoryRecorder().update(inputs(ScriptSource.of(
            Text.builder(ok.text(), "c e(\"lofi\", s(\"water\"))\n").add(PLUGIN.getId(),
                PLUGIN.getResult(ok.text(), "c e(\"lofi\", s(\"water\"))\n").objects(), List.of(warning)).build())), 1);
        assertTrue(r.isClean());
    }

    @Test
    void gateTwoApplyingStopsAtTheFirstDynamicErrorAndItsFileIsRolledBack() {
        ScriptSource one = script("01.txt", "c e(\"lofi\", s(\"water\"))\n");
        // creating the sense pig of mami again fails (it exists): the third command of the file
        ScriptSource two = script("02.txt", "c e(\"mafa\", s(\"sun\"))\nc e(\"kofe\", s(\"stone\"))\nc /mami s(\"pig\")\n");
        ScriptSource three = script("03.txt", "c e(\"tapi\", s(\"bird\"))\n");
        HistoryResult r = new HistoryRecorder().update(inputs(one, two, three), 1);
        HistoryResult.Status.StoppedAt s = assertInstanceOf(HistoryResult.Status.StoppedAt.class, r.status(),
            () -> "" + r.status());
        assertEquals(two.text(), s.text());
        assertEquals(3, s.line());
        assertEquals(CommandStatus.APPLIED, statusOf(r, one, 0));
        assertEquals(CommandStatus.ROLLED_BACK, statusOf(r, two, 0));
        assertEquals(CommandStatus.ROLLED_BACK, statusOf(r, two, 1));
        assertEquals(CommandStatus.ERROR, statusOf(r, two, 2));
        assertEquals(CommandStatus.NOT_APPLIED, statusOf(r, three, 0));
        // the dictionary is the state after the last file without errors
        assertEquals(Map.of("mami", "pig", "efe", "door", "lofi", "water"), glosses(r));
        Diagnostic error = r.diagnosticsOf(two.text()).get(0);
        assertEquals(3, error.line());
        assertEquals(Diagnostic.Severity.ERROR, error.severity());
        assertEquals(HistoryRecorder.CHECK_ID, error.grammarId());
        assertFalse(r.isClean());
        // the third file never ran: no operation of it
        assertTrue(r.operations().stream().noneMatch(o -> o.text().equals(three.text())));
    }

    @Test
    void theOrderOfTheFilesMatters() {
        ScriptSource rename = script("a.txt", "u /mami/pig (g@en = \"hog\")\n");
        ScriptSource ensure = script("b.txt", "p /mami/pig\n");
        HistoryResult renameThenEnsure = new HistoryRecorder().update(inputs(rename, ensure), 1);
        HistoryResult ensureThenRename = new HistoryRecorder().update(inputs(ensure, rename), 1);
        assertTrue(renameThenEnsure.isClean() && ensureThenRename.isClean(),
            () -> renameThenEnsure.status() + " / " + ensureThenRename.status());
        assertEquals("hog|pig", glosses(renameThenEnsure).get("mami"), "pig renamed, then a homograph mami with pig created");
        assertEquals("hog", glosses(ensureThenRename).get("mami"), "pig found, then renamed");
        assertNotEquals(glosses(renameThenEnsure), glosses(ensureThenRename));
    }

    @Test
    void aRecomputeFromTheFirstChangedFileGivesWhatAFullOneGives() {
        ScriptSource one = script("01.txt", "c e(\"lofi\", s(\"water\"))\n");
        ScriptSource two = script("02.txt", "c e(\"mafa\", s(\"sun\"))\n");
        ScriptSource twoChanged = script("02.txt", "c e(\"mafa\", s(\"moon\"))\nd /efe\n");
        HistoryRecorder recorder = new HistoryRecorder();
        recorder.update(inputs(one, two), 1);
        HistoryResult resumed = recorder.update(inputs(one, twoChanged), 2);
        HistoryResult full = new HistoryRecorder().update(inputs(one, twoChanged), 1);
        assertEquals(glosses(full), glosses(resumed));
        assertEquals(full.operations().size(), resumed.operations().size());
        assertEquals(2, resumed.generation());
        // fixing a stopped series resumes it
        ScriptSource failing = script("03.txt", "c /mami s(\"pig\")\n");
        assertInstanceOf(HistoryResult.Status.StoppedAt.class, recorder.update(inputs(one, twoChanged, failing), 3).status());
        ScriptSource fixed = script("03.txt", "c e(\"tapi\")\n");
        HistoryResult again = recorder.update(inputs(one, twoChanged, fixed), 4);
        assertTrue(again.isClean());
        assertTrue(glosses(again).containsKey("tapi"));
        // and a gate-1 error in between leaves the recording to resume afterwards
        ScriptSource broken = script("03.txt", "c e(\"tapi\"\n");
        assertInstanceOf(HistoryResult.Status.NotTried.class, recorder.update(inputs(one, twoChanged, broken), 5).status());
        assertTrue(recorder.update(inputs(one, twoChanged, fixed), 6).isClean());
        // recompute applies everything again
        assertEquals(glosses(again), glosses(recorder.recompute(7)));
    }

    @Test
    void snapshotsOfAnEntryAtPositions() {
        ScriptSource one = script("01.txt", "c e(\"lofi\", s(\"water\"))\ns /lofi/water (d@en = \"Fresh\")\n");
        HistoryRecorder recorder = new HistoryRecorder();
        HistoryResult r = recorder.update(inputs(one), 1);
        EntryRow lofi = r.entries().stream().filter(e -> e.form("tww").equals("lofi")).findFirst().orElseThrow();
        int afterCreate = lofi.operations().get(0);
        EntrySnapshot created = recorder.entryAt(lofi.key().id(), afterCreate).orElseThrow();
        assertTrue(created.entry().isPresent());
        assertTrue(created.entry().get().getSenses().get(0).getDefinition().isEmpty());
        EntrySnapshot end = recorder.entryAt(lofi.key().id(), r.shownPosition()).orElseThrow();
        assertFalse(end.entry().get().getSenses().get(0).getDefinition().isEmpty());
        EntrySnapshot before = recorder.entryAt(lofi.key().id(), 0).orElseThrow();
        assertTrue(before.entry().isEmpty(), "lofi does not exist before the commands");
        assertTrue(created.dictionary() != end.dictionary());
    }

    @Test
    void withoutContextDictionaryTheHistoryStartsEmpty() {
        ScriptSource one = script("01.txt", "c e(\"lofi\", s(\"water\"))\n");
        HistoryResult r = new HistoryRecorder().update(
            new HistoryInputs(Optional.empty(), -1, List.of(one), "tww", "en"), 1);
        assertTrue(r.isClean());
        assertTrue(r.startedEmpty());
        assertEquals(Map.of("lofi", "water"), glosses(r));
    }

    @Test
    void anUnreadableDictionaryFails() throws Exception {
        java.nio.file.Files.writeString(dictionaryFile, "not a LIFT file");
        ScriptSource one = script("01.txt", "c e(\"lofi\")\n");
        HistoryResult r = new HistoryRecorder().update(inputs(one), 1);
        assertInstanceOf(HistoryResult.Status.Failed.class, r.status());
        assertFalse(r.diagnosticsOf(one.text()).isEmpty());
    }

    @Test
    void theServiceDebouncesAndPublishesTheLatest() throws Exception {
        List<HistoryResult> published = new CopyOnWriteArrayList<>();
        try (DictionaryHistory history = new DictionaryHistory(Runnable::run, published::add, 50)) {
            history.submit(inputs(script("01.txt", "c e(\"lofi\")\n")));
            history.submit(inputs(script("01.txt", "c e(\"mafa\")\n")));
            long until = System.currentTimeMillis() + 10_000;
            while (published.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(20);
            Thread.sleep(200);
            assertEquals(1, published.size(), "the first inputs were superseded during the delay");
            HistoryResult r = published.get(0);
            assertTrue(glosses(r).containsKey("mafa"));
            EntryRow mafa = r.entries().stream().filter(e -> e.form("tww").equals("mafa")).findFirst().orElseThrow();
            assertTrue(history.entryAt(r, mafa.key().id(), r.shownPosition()).get(5, TimeUnit.SECONDS).isPresent());
            HistoryResult stale = new HistoryResult(r.generation() - 1, r.status(), false, 0, 0, Map.of(), Map.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), 0);
            assertTrue(history.entryAt(stale, mafa.key().id(), 0).get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(history.patchedIndex().isPresent());
            Path out = dir.resolve("patched.lift");
            history.export(r, out).get(5, TimeUnit.SECONDS);
            assertTrue(java.nio.file.Files.readString(out).contains("mafa"));
        }
    }

    @Test
    void theEntryOfACommandIsReadFromItsModel() {
        ScriptSource s = script("01.txt", "c e(\"lofi\", s(\"water\"))\np /mami/pig\ns /efe/door (d@en = \"x\")\nd /tapi\n");
        List<String> forms = s.commands().stream().map(c -> CommandTargets.entryForm(c.item()).orElse("?")).toList();
        assertEquals(List.of("lofi", "mami", "efe", "tapi"), forms);
    }

    @SuppressWarnings("unused")
    private static List<LexiconMutationCommand> all(ScriptSource s) {
        return new ArrayList<>(s.commands());
    }
}
