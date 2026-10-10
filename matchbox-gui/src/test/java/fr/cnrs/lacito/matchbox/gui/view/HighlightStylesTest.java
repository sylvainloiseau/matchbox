package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTag;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.gui.workbench.ProjectSession;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles.Key;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HighlightStylesTest {

    private static final TextId ID = TextId.of(Path.of("a.txt"));
    private static final String TAG_KIND = "test-grammar:tag";

    /** A grammar giving tags a pink background and every object bold. */
    private static final class TestGrammar implements GrammarPlugin {
        public String getId() { return "test-grammar"; }
        public String getDisplayName() { return "Test"; }
        public String getDisplayDescription() { return ""; }
        public String getFormatDocumentation() { return ""; }
        public List<GrammarExample> getExamples() { return List.of(); }
        public String getGrammarSource() { return ""; }
        public ParseResult getResult(TextId text, String content) { return new ParseResult(List.of(), List.of()); }
        public int getApiVersion() { return API_VERSION; }

        @Override
        public DisplayStyle getDefaultDisplayStyle(String kind) {
            return kind == null ? DisplayStyle.EMPTY.withBold(true) : DisplayStyle.background("#ffd6e8");
        }
    }

    private final SimpleTag tag = new SimpleTag(TAG_KIND, new TextCoordinate(ID, 0, 3, 1, 1), "x");
    private final Text text = Text.builder(ID, "#\"x\" text").add("test-grammar", List.of(tag), List.of()).build();
    private final ProjectSession session = new ProjectSession(Runnable::run);
    private final HighlightStyles styles = new HighlightStyles(session,
        id -> id.equals("test-grammar") ? new TestGrammar() : null);

    private Project project() throws Exception {
        Project p = Project.createDefaultProject(PluginManager.loadPlugins());
        session.setProject(p);
        return p;
    }

    @Test
    void theGrammarDefaultsApplyWhenTheProjectSetsNothing() throws Exception {
        project();
        assertEquals(DisplayStyle.background("#ffd6e8").withBold(true), styles.styleFor(text, tag));
    }

    @Test
    void theProjectStylesComeFirstKindThenGrammar() throws Exception {
        Project p = project();
        p.setDisplayStyles(DisplayStyles.NONE
            .with(Key.grammar("test-grammar"), DisplayStyle.background("#eeeeee").withItalic(true))
            .with(new Key("test-grammar", TAG_KIND), DisplayStyle.EMPTY.withItalic(false)));
        assertEquals(new DisplayStyle(null, true, false, null, null, null, "#eeeeee"), styles.styleFor(text, tag));
    }

    @Test
    void withoutProjectOrGrammarThePaletteColourIsUsed() throws Exception {
        assertEquals(DisplayStyle.background("#ffd6e8").withBold(true), styles.styleFor(text, tag), "no project");
        SimpleTag other = new SimpleTag("other-grammar:tag", new TextCoordinate(ID, 0, 3, 1, 1), "x");
        Text otherText = Text.builder(ID, "#\"x\"").add("other-grammar", List.of(other), List.of()).build();
        assertEquals(DisplayStyle.background(HighlightStyles.paletteColor("other-grammar:tag")),
            styles.styleFor(otherText, other));
    }

    @Test
    void aSenseHasNoBackgroundOfItsOwn() throws Exception {
        SimpleSense sense = new SimpleSense("other-grammar:sense", new TextCoordinate(ID, 0, 3, 1, 1), "def", List.of());
        Text t = Text.builder(ID, "abcd").add("other-grammar", List.of(sense), List.of()).build();
        assertNull(styles.styleFor(t, sense).backgroundColor());
    }

    @Test
    void listenersHearProjectAndStyleChanges() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        styles.addListener(changes::incrementAndGet);
        Project p = project();
        assertEquals(1, changes.get());
        p.setDisplayStyles(DisplayStyles.NONE.with(Key.grammar("test-grammar"), DisplayStyle.EMPTY.withBold(false)));
        assertEquals(2, changes.get());
        session.closeProject();
        assertEquals(3, changes.get());
        p.setDisplayStyles(DisplayStyles.NONE);
        assertEquals(3, changes.get(), "the closed project is no longer followed");
    }

    @Test
    void theKindCatalogListsDeclaredFoundAndStyledKinds() throws Exception {
        Corpus corpus = new Corpus();
        corpus.put(text);
        DisplayStyles styled = DisplayStyles.NONE.with(new Key("test-grammar", "test-grammar:old"), DisplayStyle.EMPTY.withBold(true));
        assertEquals(List.of("test-grammar:entry", "test-grammar:old", TAG_KIND),
            KindCatalog.kinds("test-grammar", List.of("test-grammar:entry"), corpus, styled));
        assertEquals("tag", KindCatalog.shortName(TAG_KIND));
    }
}
