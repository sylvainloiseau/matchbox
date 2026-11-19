package fr.cnrs.lacito.matchbox.gui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.grammar.tag.TagGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchEditPlannerTest {

    @TempDir
    Path dir;
    private final GrammarPlugin grammar = new TagGrammarPlugin();
    private final Corpus corpus = new Corpus();
    private final Map<fr.cnrs.lacito.matchbox.corpus.TextId, TextDocument> documents = new HashMap<>();
    private TextDocument a;
    private TextDocument b;
    private Set<TextDocument> readOnly = Set.of();

    @BeforeEach
    void setUp() throws Exception {
        a = add("a.txt", "x #\"pos/verb\" y #\"pos/verb\"\n");
        b = add("b.txt", "z #\"pos/verb\"\n");
    }

    private TextDocument add(String name, String content) throws Exception {
        TextDocument d = new TextDocument(Files.writeString(dir.resolve(name), content));
        ParseResult r = grammar.getResult(d.getId(), content);
        corpus.put(Text.builder(d.getId(), content).add(grammar.getId(), r.objects(), r.diagnostics()).build());
        documents.put(d.getId(), d);
        return d;
    }

    private BatchEditPlanner planner() {
        ViewEditPlanner single = new ViewEditPlanner(corpus, id -> Optional.ofNullable(documents.get(id)), d -> List.of(grammar), null);
        return new BatchEditPlanner(corpus, single, (text, object) -> Optional.of(grammar), d -> !readOnly.contains(d));
    }

    private List<FieldChange> renameAll(String value) {
        return corpus.texts().stream().flatMap(t -> t.objectsOf(Tag.class).stream())
            .map(t -> new FieldChange(t, Tag.TAG, value)).toList();
    }

    @Test
    void severalTagsOfSeveralFilesAreOneChangePerFile() {
        var ready = assertInstanceOf(BatchEditPlanner.Ready.class, planner().plan(renameAll("pos/action")));
        assertEquals(2, ready.documents().size());
        ViewEditPlanner.Ready first = ready.documents().stream().filter(r -> r.document() == a).findFirst().orElseThrow();
        assertEquals(2, first.edits().size());
        assertEquals("x #\"pos/action\" y #\"pos/action\"\n", TextEdit.applyAll(a.getCurrentText(), first.edits()));
    }

    @Test
    void aReadOnlyFileOrARefusedValueFailsTheWholeBatch() {
        readOnly = Set.of(b);
        var failed = assertInstanceOf(BatchEditPlanner.Failed.class, planner().plan(renameAll("pos/action")));
        assertEquals(List.of("b.txt: the file cannot be written"), failed.problems());
        readOnly = Set.of();
        failed = assertInstanceOf(BatchEditPlanner.Failed.class, planner().plan(renameAll("bad\"quote")));
        assertTrue(failed.problems().get(0).startsWith("a.txt: "), failed.problems().toString());
    }

    @Test
    void overlappingChangesAreRefused() {
        assertTrue(BatchEditPlanner.overlap(List.of(TextEdit.insert(3, "a"), new TextEdit(2, 5, "abc", ""))));
        assertTrue(!BatchEditPlanner.overlap(List.of(new TextEdit(0, 2, "ab", ""), new TextEdit(2, 4, "cd", ""))));
    }

    @Test
    void addRemoveAndRenameKindsAndTwoAdditionsToAnObjectWithoutBlock() throws Exception {
        GrammarPlugin md = new fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin();
        String content = "---\nspeaker: Ann\ndate: x\n---\n\n# S\n\nprose\n";
        TextDocument d = new TextDocument(Files.writeString(dir.resolve("m.md"), content));
        ParseResult r = md.getResult(d.getId(), content);
        corpus.put(Text.builder(d.getId(), content).add(md.getId(), r.objects(), r.diagnostics()).build());
        documents.put(d.getId(), d);
        ViewEditPlanner single = new ViewEditPlanner(corpus, id -> Optional.ofNullable(documents.get(id)), x -> List.of(md), null);
        BatchEditPlanner planner = new BatchEditPlanner(corpus, single, (text, object) -> Optional.of(md), x -> true);
        var header = (fr.cnrs.lacito.matchbox.corpus.capability.TextHeader) r.objects().get(0);
        var section = (fr.cnrs.lacito.matchbox.corpus.capability.Section) r.objects().get(1);
        var p = fr.cnrs.lacito.matchbox.corpus.capability.TextHeader.class;
        var ready = assertInstanceOf(BatchEditPlanner.Ready.class, planner.plan(List.of(
            FieldChange.remove(header, header.metadataFieldName("date")),
            FieldChange.rename(header, header.metadataFieldName("speaker"), header.metadataFieldName("talker")),
            FieldChange.add(section, section.metadataFieldName("place"), "here"))));
        assertEquals("---\ntalker: Ann\n---\n\n# S\n---\nplace: here\n---\n\nprose\n",
            TextEdit.applyAll(content, ready.documents().get(0).edits()));
        var failed = assertInstanceOf(BatchEditPlanner.Failed.class, planner.plan(List.of(
            FieldChange.add(section, section.metadataFieldName("a"), "1"),
            FieldChange.add(section, section.metadataFieldName("b"), "2"))));
        assertTrue(failed.problems().get(0).contains("no metadata block yet"), failed.problems().toString());
        assertTrue(p.isInstance(header));
    }
}
