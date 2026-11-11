package fr.cnrs.lacito.matchbox.model.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import fr.cnrs.lacito.matchbox.model.lexicon.ContextDictionary;
import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import fr.cnrs.lacito.matchbox.model.lexicon.LiftLexicon;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The checks of the application on IGTs: structure, gloss labels, dictionary; and the LIFT lexicon. */
class GlossChecksTest {

    private static final TextId ID = new TextId(Path.of("notes.md"));

    static final String FRENCH = """
        :::{olang="fra-x-ipa", mlang="en"}
        je crois pas qu'elle est assez curieuse
        ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
        1SG=believe not COMPL=3SG.F be\\3SG.PRES enough curious-FEM
        'I don't think she is curious enough'
        :::
        """;

    private static Text parse(String content) {
        return TextParser.parse(ID, content, List.of(new IgtGrammarPlugin()));
    }

    private static LexiconIndex dictionary() throws Exception {
        return new LexiconIndex(LiftLexicon.load(Path.of(GlossChecksTest.class.getResource("/small.lift").toURI())));
    }

    private static List<String> codes(List<Diagnostic> ds) {
        return ds.stream().map(Diagnostic::code).toList();
    }

    @Test
    void the_lift_lexicon_and_its_index() throws Exception {
        LexiconIndex index = dictionary();
        assertEquals(2, index.size());
        assertEquals(Set.of("fra-x-ipa"), index.objectLanguages());
        assertEquals(Set.of("en", "de"), index.metaLanguages());
        var known = (LexiconIndex.Lookup.Known) index.lookup("KʁWA", Optional.empty(), Optional.of("en"));
        assertTrue(known.hasGloss("think"));
        assertTrue(known.hasGloss("Suppose"));
        assertTrue(known.hasGloss("reckon"), "subsenses");
        assertFalse(known.hasGloss("believe"));
        assertTrue(index.lookup("asse", Optional.of("fra-x-ipa"), Optional.empty()) instanceof LexiconIndex.Lookup.Known, "variants");
        assertTrue(index.lookup("pa", Optional.empty(), Optional.empty()) instanceof LexiconIndex.Lookup.FormUnknown);
    }

    @Test
    void the_structure_check_reports_the_issues_of_the_readings() {
        Text text = parse(":::\nʒə=kʁwa pa kyRjø-z\n1SG=believe not curious\n:::\n");
        List<Diagnostic> ds = new IgtStructureCheck().check(text, CheckInputs.defaults());
        assertEquals(List.of("igt.morpheme-count"), codes(ds));
        assertTrue(ds.get(0).isSemantic());
        assertEquals(IgtStructureCheck.ID, ds.get(0).grammarId());
        assertEquals(3, ds.get(0).line());
        assertEquals(16, ds.get(0).column());
        assertTrue(new IgtStructureCheck().check(parse(FRENCH), CheckInputs.defaults()).isEmpty());
    }

    @Test
    void unknown_labels_against_the_chosen_list() {
        Text text = parse(":::\nʒə=kʁwa pa\n1SG=believe COMPL2.NEG\n:::\n");
        CheckInputs leipzig = CheckInputs.defaults();
        List<Diagnostic> ds = new GlossLabelCheck().check(text, leipzig);
        assertEquals(1, ds.size(), ds.toString());
        assertTrue(ds.get(0).message().startsWith("COMPL2 is not in the Leipzig list"));
        var own = new CheckInputs(new GlossCheckSettings(GlossLabelList.LEIPZIG, Set.of("compl2"), true), Optional.empty());
        assertTrue(new GlossLabelCheck().check(text, own).isEmpty(), "the project's labels, any case");
        var none = new CheckInputs(new GlossCheckSettings(GlossLabelList.NONE, Set.of(), true), Optional.empty());
        assertTrue(new GlossLabelCheck().check(text, none).isEmpty());
    }

    @Test
    void form_and_gloss_pairs_against_the_dictionary() throws Exception {
        Text text = parse(FRENCH);
        CheckInputs inputs = new CheckInputs(GlossCheckSettings.defaults(), Optional.of(dictionary()));
        List<Diagnostic> ds = new LexiconCheck().check(text, inputs);
        List<String> messages = ds.stream().map(Diagnostic::message).toList();
        // ʒə=1SG and -z=FEM are grammatical; ase/enough is known
        assertTrue(messages.get(0).startsWith("kʁwa / believe: kʁwa is in small.lift, but no sense is glossed 'believe' (glosses: think, suppose, reckon)"),
            messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("pa / not: pa is not in small.lift")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("ɛ / be:")), "be\\\\3SG.PRES is looked up as 'be'");
        assertTrue(messages.stream().noneMatch(m -> m.startsWith("ase")));
        assertTrue(messages.stream().noneMatch(m -> m.startsWith("ʒə")));
        assertEquals(List.of(LexiconCheck.CODE_GLOSS), codes(ds.subList(0, 1)));

        Diagnostic.Fix declare = ds.get(0).fixes().get(0);
        assertEquals("Declare kʁwa / believe", declare.label());
        String declared = TextEdit.applyAll(FRENCH, declare.edits());
        assertTrue(declared.startsWith("p /kʁwa/believe\n:::{"), declared);
        // once declared, the fix is not offered again (the warning stays until the p notation is read)
        List<Diagnostic> again = new LexiconCheck().check(parse(declared), inputs);
        assertTrue(again.get(0).fixes().isEmpty());
        assertFalse(new LexiconCheck().check(text, new CheckInputs(
            new GlossCheckSettings(GlossLabelList.LEIPZIG, Set.of(), false), Optional.of(dictionary()))).size() > 0,
            "dictionary check off");
    }

    @Test
    void declarations_follow_the_p_lines_already_there_and_keep_line_endings() {
        String content = "p /a/b\r\n:::\r\nx\r\n:::\r\n";
        int igt = content.indexOf(":::");
        assertTrue(Declarations.declared(content, igt, "a", "b"));
        assertFalse(Declarations.declared(content, igt, "c", "d"));
        assertFalse(Declarations.declared("p /a/b\n\n:::\n", "p /a/b\n\n".length(), "a", "b"), "a blank line ends the run");
        assertEquals("p /a/b\r\np /c/d\r\n:::\r\nx\r\n:::\r\n",
            TextEdit.applyAll(content, List.of(Declarations.insert(content, igt, List.<String[]>of(new String[] {"c", "d"})))));
    }

    @Test
    void project_checks_are_cached_per_text_and_dropped_when_the_inputs_change() throws Exception {
        ProjectChecks checks = new ProjectChecks();
        Text text = parse(FRENCH);
        List<Diagnostic> first = checks.of(text);
        assertTrue(first == checks.of(text), "cached");
        int[] told = {0};
        checks.addListener(() -> told[0]++);
        checks.setInputs(new CheckInputs(GlossCheckSettings.defaults(), Optional.of(dictionary())));
        assertEquals(1, told[0]);
        assertTrue(checks.of(text).size() > first.size());
    }

    @Test
    void the_context_dictionary_is_loaded_in_the_background() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        try (ContextDictionary d = new ContextDictionary(Runnable::run, c -> {
            if (c.state() instanceof ContextDictionary.State.Loaded) loaded.countDown();
        })) {
            d.setPath(Optional.of(Path.of(GlossChecksTest.class.getResource("/small.lift").toURI())));
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            assertEquals(2, d.index().orElseThrow().size());
            d.setPath(Optional.empty());
            assertTrue(d.index().isEmpty());
        }
        CountDownLatch failed = new CountDownLatch(1);
        try (ContextDictionary d = new ContextDictionary(Runnable::run, c -> {
            if (c.state() instanceof ContextDictionary.State.Failed) failed.countDown();
        })) {
            d.setPath(Optional.of(Path.of("does-not-exist.lift")));
            assertTrue(failed.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void the_gloss_check_is_saved_in_the_project_file(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        var project = fr.cnrs.lacito.matchbox.model.project.Project.createDefaultProject(List.of());
        assertEquals(GlossCheckSettings.defaults(), project.getGlossCheck());
        GlossCheckSettings settings = new GlossCheckSettings(GlossLabelList.LEHMANN, Set.of("COMPL2", "TAM1"), false);
        project.setGlossCheck(settings);
        assertTrue(project.definitionChangedProperty().get());
        project.setFile(dir.resolve("p.mbx").toFile());
        fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer.save(project);
        var loaded = fr.cnrs.lacito.matchbox.model.project.Project.loadProject(List.of(), dir.resolve("p.mbx").toFile());
        assertEquals(settings, loaded.getGlossCheck());
    }
}
