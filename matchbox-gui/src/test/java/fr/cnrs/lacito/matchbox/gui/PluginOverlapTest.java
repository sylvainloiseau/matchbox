package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.MatchConflict;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.tag.TagGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The rule of {@code MatchRole} with the real plugins, on the sample files. */
class PluginOverlapTest {

    private static final Path DATA = Path.of("src/test/data");

    static Stream<Path> samples() throws IOException {
        try (Stream<Path> files = Files.list(DATA)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList().stream();
        }
    }

    private static List<GrammarPlugin> plugins(boolean markdownFirst) {
        List<GrammarPlugin> list = new ArrayList<>(List.of(new NotebookGrammarPlugin(), new TagGrammarPlugin(),
            new MediaRefGrammarPlugin()));
        list.add(markdownFirst ? 0 : list.size(), new MarkdownHeadingGrammarPlugin());
        return list;
    }

    /** "kind@start-stop" of every object, in text order. */
    private static List<String> signature(Text text) {
        return text.flatten()
            .map(o -> o.getKind() + "@" + o.getTextCoordinate().startIndex() + "-" + o.getTextCoordinate().stopIndex())
            .sorted().toList();
    }

    @ParameterizedTest
    @MethodSource("samples")
    void the_plugins_of_a_markdown_notebook_do_not_conflict_whatever_their_order(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        TextId id = TextId.of(file);
        Text first = TextParser.parse(id, content, plugins(true));
        Text last = TextParser.parse(id, content, plugins(false));
        assertTrue(first.conflicts().isEmpty(), () -> file + ": " + first.conflicts().stream().map(MatchConflict::message).toList());
        assertTrue(last.conflicts().isEmpty(), () -> file + ": " + last.conflicts().stream().map(MatchConflict::message).toList());
        assertEquals(signature(first), signature(last), file.toString());
    }

    @Test
    void sections_contain_the_igts_and_a_click_selects_the_igt() throws IOException {
        Path file = DATA.resolve("tuwari.md");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        for (boolean markdownFirst : new boolean[] {true, false}) {
            Text text = TextParser.parse(TextId.of(file), content, plugins(markdownFirst));
            List<InterlinearSentence> igts = text.objectsOf(InterlinearSentence.class);
            assertTrue(!igts.isEmpty() && !text.sections().isEmpty());
            InterlinearSentence inSection = igts.stream()
                .filter(i -> !text.enclosingSections(i).isEmpty()).findFirst().orElseThrow();
            int offset = inSection.getTextCoordinate().startIndex() + 1;
            CorpusObject clicked = text.objectAt(offset).orElseThrow();
            assertTrue(clicked == inSection || text.parentOf(clicked).isPresent()
                && inSection.getTextCoordinate().encloses(clicked.getTextCoordinate()), clicked.toString());
            // drawing order: sections first, tags last
            List<CorpusObject> layered = text.layered();
            assertTrue(layered.get(0) instanceof Section);
            assertTrue(layered.get(layered.size() - 1) instanceof Tag);
        }
    }

    @Test
    void a_second_reading_of_the_same_igts_is_not_kept_and_reported() throws IOException {
        Path file = DATA.resolve("tuwari.md");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        DuplicateIgtGrammar second = new DuplicateIgtGrammar();
        List<GrammarPlugin> grammars = new ArrayList<>(plugins(true));
        grammars.add(second);
        Text text = TextParser.parse(TextId.of(file), content, grammars);
        int igts = (int) text.objects("notebook-grammar").stream().filter(InterlinearSentence.class::isInstance).count();
        assertTrue(igts > 0);
        assertTrue(text.objects(DuplicateIgtGrammar.ID).isEmpty());
        assertEquals(igts, text.conflicts().stream().filter(MatchConflict::isDropped).count());
        assertEquals(igts, text.diagnostics(DuplicateIgtGrammar.ID).size());
        assertTrue(text.diagnostics(DuplicateIgtGrammar.ID).get(0).message().contains(new NotebookGrammarPlugin().getDisplayName()),
            text.diagnostics(DuplicateIgtGrammar.ID).get(0).message());
        // the plugin received what the earlier grammars own
        assertTrue(second.received.claims().stream().anyMatch(c -> c.grammarId().equals("notebook-grammar")));
        CorpusObject igt = text.objects("notebook-grammar").stream()
            .filter(InterlinearSentence.class::isInstance).findFirst().orElseThrow();
        assertTrue(second.received.isClaimed(igt.getTextCoordinate().startIndex(), igt.getTextCoordinate().startIndex()));
        assertSame(igt, text.conflicts().stream().filter(MatchConflict::isDropped).findFirst().orElseThrow().other());
    }
}
