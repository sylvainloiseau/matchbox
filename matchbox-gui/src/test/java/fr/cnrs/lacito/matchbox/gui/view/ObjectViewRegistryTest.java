package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import java.nio.file.Path;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleIGT;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSense;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

class ObjectViewRegistryTest {

    /** A grammar-specific type, unknown to the application. */
    static final class CustomObject implements CorpusObject {
        public TextCoordinate getTextCoordinate() { return new TextCoordinate(new TextId(Path.of("x")), 0, 0, 1, 1); }
        public String getKind() { return "custom:thing"; }
    }

    static class TestFactory<T extends CorpusObject> implements ObjectViewFactory<T> {
        private final Class<T> type;
        private final int priority;
        TestFactory(Class<T> type, int priority) { this.type = type; this.priority = priority; }
        public Class<T> getType() { return type; }
        public Node createView(T object, ViewContext context) { return null; }
        public int getPriority() { return priority; }
    }

    /** Same as TestFactory, but considered as coming from a plugin jar. */
    static class PluginFactory<T extends CorpusObject> extends TestFactory<T> {
        PluginFactory(Class<T> type, int priority) { super(type, priority); }
    }

    private static ObjectViewRegistry registry(ObjectViewFactory<?>... factories) {
        return new ObjectViewRegistry(List.of(factories), c -> PluginFactory.class.isAssignableFrom(c));
    }

    @Test
    void capabilityFactoryMatchesAnyImplementation() {
        var generic = new GenericObjectViewFactory();
        var igt = new InterlinearSentenceViewFactory();
        var r = registry(generic, igt);
        assertSame(igt, r.factoryFor(SimpleIGT.class));
        assertSame(generic, r.factoryFor(SimpleSense.class));
        assertSame(generic, r.factoryFor(CustomObject.class));
    }

    @Test
    void textHeadersGetThePluginView() {
        var generic = new GenericObjectViewFactory();
        var header = new fr.cnrs.lacito.matchbox.grammar.markdownheading.TextHeaderViewFactory();
        assertSame(header, registry(generic, header).factoryFor(fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader.class));
    }

    @Test
    void mediaSegmentsOfAnyGrammarGetTheSegmentView() {
        var generic = new GenericObjectViewFactory();
        var segments = new MediaSegmentViewFactory();
        assertSame(segments, registry(generic, segments).factoryFor(fr.cnrs.lacito.matchbox.grammar.mediaref.MediaMarker.class));
        assertSame(segments, registry(generic, segments).factoryFor(fr.cnrs.lacito.matchbox.gui.mode.fileMode.SegmentAtCaretTest.Segment.class));
    }

    @Test
    void sectionsGetTheSectionView() {
        var generic = new GenericObjectViewFactory();
        var section = new SectionViewFactory();
        assertSame(section, registry(generic, section).factoryFor(SimpleSection.class));
    }

    @Test
    void closestTypeWins() {
        var igt = new InterlinearSentenceViewFactory();
        var exact = new TestFactory<>(SimpleIGT.class, -10);
        assertSame(exact, registry(igt, exact).factoryFor(SimpleIGT.class));
    }

    @Test
    void priorityThenPluginBreakTies() {
        var builtIn = new TestFactory<>(InterlinearSentence.class, 0);
        var plugin = new PluginFactory<>(InterlinearSentence.class, 0);
        var preferred = new TestFactory<>(InterlinearSentence.class, 5);
        assertSame(plugin, registry(builtIn, plugin).factoryFor(SimpleIGT.class));
        assertSame(preferred, registry(builtIn, plugin, preferred).factoryFor(SimpleIGT.class));
    }

    @Test
    void fallsBackToGenericWithoutFactories() {
        assertInstanceOf(GenericObjectViewFactory.class, registry().factoryFor(CustomObject.class));
    }

    @Test
    void supertypeDistances() {
        Map<Class<?>, Integer> d = ObjectViewRegistry.supertypeDistances(SimpleIGT.class);
        assertEquals(0, d.get(SimpleIGT.class));
        assertEquals(1, d.get(InterlinearSentence.class));
        assertEquals(2, d.get(CorpusObject.class));
    }

    @Test
    void kindTitles() {
        assertEquals("Lexical entry", Kinds.title("notebook-grammar:lexical-entry"));
        assertEquals("Igt", Kinds.title("notebook-grammar:igt"));
    }
}
