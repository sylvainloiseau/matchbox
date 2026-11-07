package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.EditRebase;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.CaretMemory;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offsets kept by the window (edit hints, caret memory) follow the changes of their document. */
class VersionedOffsetsTest {

    private static final EditOrigin VIEW = EditOrigin.of(EditOrigin.Kind.VIEW, "view");

    @TempDir
    Path dir;
    private TextDocument document;

    @BeforeEach
    void setUp() throws Exception {
        document = new TextDocument(Files.writeString(dir.resolve("a.txt"), "line one\nline two\nline three\n"));
    }

    private void insert(int offset, String s) {
        assertTrue(document.applyEdits(List.of(TextEdit.insert(offset, s)), VIEW));
    }

    private Text parsed() {
        return Text.builder(document.getId(), document.getCurrentText()).version(document.getVersion()).build();
    }

    private EditHints hints() {
        return new EditHints((text, offset, from, to) ->
            document.moveOffset(offset, EditRebase.Bias.BEFORE, from, to));
    }

    @Test
    void aPendingSelectionFollowsTypingBeforeTheParse() {
        EditHints hints = hints();
        int two = document.getCurrentText().indexOf("two");
        hints.selectAfterParse(document.getId(), two, document.getVersion());
        insert(0, "new first line\n");
        assertEquals(OptionalInt.of(two + 15), hints.takePendingSelection(parsed()));
        assertTrue(hints.takePendingSelection(parsed()).isEmpty());
    }

    @Test
    void theLastEditOffsetFollowsToo() {
        EditHints hints = hints();
        hints.lastEdit(document.getId(), 9, document.getVersion());
        insert(0, ">>");
        assertEquals(OptionalInt.of(11), hints.lastEditOffset(parsed()));
        // a hint without version is kept as recorded
        hints.lastEdit(document.getId(), 9);
        assertEquals(OptionalInt.of(9), hints.lastEditOffset(parsed()));
    }

    @Test
    void theCaretComesBackWhereTheUserWas() {
        CaretMemory memory = new CaretMemory();
        int three = document.getCurrentText().indexOf("three");
        int lineThree = document.getCurrentText().indexOf("line three");
        memory.remember(document.getId(), new CaretMemory.Place(three, three - 2, 2, lineThree, document.getVersion()));
        // a view edit while the file is hidden: two lines added at the top
        insert(0, "a\nb\n");
        CaretMemory.Place place = memory.recall(document).orElseThrow();
        assertEquals(three + 4, place.caret());
        assertEquals(three + 2, place.anchor());
        assertEquals(4, place.firstVisibleLine());
    }

    @Test
    void withoutHistoryTheCaretIsClamped() {
        CaretMemory memory = new CaretMemory();
        memory.remember(document.getId(), new CaretMemory.Place(500, 400, 3));
        CaretMemory.Place place = memory.recall(document).orElseThrow();
        assertEquals(document.getCurrentText().length(), place.caret());
        assertEquals(3, place.firstVisibleLine());
    }
}
