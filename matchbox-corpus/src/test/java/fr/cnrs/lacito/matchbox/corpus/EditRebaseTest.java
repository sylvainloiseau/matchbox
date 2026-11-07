package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.EditRebase.Bias;
import fr.cnrs.lacito.matchbox.corpus.EditRebase.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link EditRebase}: edits and offsets moved through changes by position. */
@SuppressWarnings("unchecked")
class EditRebaseTest {

    private static final String TEXT = "abcdefghij";

    /** The field "def" (3..6) replaced by "XYZ". */
    private static final TextEdit FIELD = TextEdit.replace(TEXT, 3, 6, "XYZ");

    @SafeVarargs
    private static List<TextEdit> moved(TextEdit edit, List<TextEdit>... changes) {
        Result r = EditRebase.rebase(List.of(edit), List.of(changes));
        return assertInstanceOf(Result.Moved.class, r).edits();
    }

    @SafeVarargs
    private static Result.Conflict conflict(TextEdit edit, List<TextEdit>... changes) {
        return assertInstanceOf(Result.Conflict.class, EditRebase.rebase(List.of(edit), List.of(changes)));
    }

    /** Applies the changes to TEXT in order. */
    @SafeVarargs
    private static String after(List<TextEdit>... changes) {
        String t = TEXT;
        for (List<TextEdit> c : changes) t = TextEdit.applyAll(t, c);
        return t;
    }

    @Test
    void a_change_before_the_edit_shifts_it() {
        List<TextEdit> typed = List.of(TextEdit.insert(1, "1234"));
        TextEdit e = moved(FIELD, typed).get(0);
        assertEquals(7, e.start());
        assertEquals(10, e.end());
        assertTrue(e.matches(after(typed)));
        // a deletion ending at the start
        List<TextEdit> deleted = List.of(TextEdit.replace(TEXT, 0, 3, ""));
        assertEquals(0, moved(FIELD, deleted).get(0).start());
    }

    @Test
    void an_insertion_at_the_start_shifts_and_at_the_end_keeps() {
        assertEquals(5, moved(FIELD, List.of(TextEdit.insert(3, "!!"))).get(0).start());
        assertSame(FIELD, moved(FIELD, List.of(TextEdit.insert(6, "!!"))).get(0));
    }

    @Test
    void a_change_after_the_edit_keeps_it() {
        assertSame(FIELD, moved(FIELD, List.of(TextEdit.replace(TEXT, 7, 9, "long text"))).get(0));
    }

    @Test
    void a_change_of_its_characters_is_a_conflict() {
        TextEdit inside = TextEdit.replace(TEXT, 4, 5, "E");
        Result.Conflict c = conflict(FIELD, List.of(inside));
        assertSame(FIELD, c.edit());
        assertSame(inside, c.change());
        conflict(FIELD, List.of(TextEdit.insert(4, "x")));
        conflict(FIELD, List.of(TextEdit.replace(TEXT, 2, 4, "")));
        conflict(FIELD, List.of(TextEdit.replace(TEXT, 5, 8, "")));
        conflict(FIELD, List.of(TextEdit.replace(TEXT, 0, 10, "")));
    }

    @Test
    void two_insertions_at_one_offset_the_edit_goes_after() {
        TextEdit insertion = TextEdit.insert(3, "N");
        TextEdit e = moved(insertion, List.of(TextEdit.insert(3, "typed"))).get(0);
        assertEquals(8, e.start());
        // a replacement starting at the insertion point does not move it
        assertSame(insertion, moved(insertion, List.of(TextEdit.replace(TEXT, 3, 5, "zz"))).get(0));
    }

    @Test
    void several_changes_in_a_row_and_a_change_of_several_edits() {
        List<TextEdit> c1 = List.of(TextEdit.insert(0, ">>"), TextEdit.replace(TEXT, 8, 10, ""));
        String t1 = after(c1);
        List<TextEdit> c2 = List.of(TextEdit.replace(t1, 0, 1, ""), TextEdit.insert(t1.length(), "end"));
        TextEdit e = moved(FIELD, c1, c2).get(0);
        assertEquals(4, e.start());
        assertTrue(e.matches(after(c1, c2)));
    }

    @Test
    void a_batch_stays_non_overlapping() {
        List<TextEdit> batch = List.of(TextEdit.replace(TEXT, 1, 2, "B"), TextEdit.replace(TEXT, 2, 3, "C"),
            TextEdit.insert(10, "!"));
        List<TextEdit> change = List.of(TextEdit.insert(2, "--"), TextEdit.insert(0, "++"));
        List<TextEdit> change2 = List.of(TextEdit.insert(0, "++"), TextEdit.insert(2, "--"));
        Result r = EditRebase.rebase(batch, List.of(change2));
        List<TextEdit> m = assertInstanceOf(Result.Moved.class, r).edits();
        String t = after(change2);
        assertEquals("++aB--Cdefghij!", TextEdit.applyAll(t, m));
        assertTrue(change.size() == 2);
    }

    @Test
    void no_change_keeps_the_edits() {
        assertSame(FIELD, moved(FIELD).get(0));
    }

    @Test
    void offsets_with_their_bias() {
        List<List<TextEdit>> ins = List.of(List.of(TextEdit.insert(3, "xx")));
        assertEquals(3, EditRebase.offset(3, Bias.BEFORE, ins));
        assertEquals(5, EditRebase.offset(3, Bias.AFTER, ins));
        assertEquals(6, EditRebase.offset(4, Bias.BEFORE, ins));
        assertEquals(2, EditRebase.offset(2, Bias.AFTER, ins));
        // inside a replaced range
        List<List<TextEdit>> rep = List.of(List.of(TextEdit.replace(TEXT, 2, 6, "Q")));
        assertEquals(2, EditRebase.offset(4, Bias.BEFORE, rep));
        assertEquals(3, EditRebase.offset(4, Bias.AFTER, rep));
        // at the edges of a replaced range
        assertEquals(2, EditRebase.offset(2, Bias.AFTER, rep));
        assertEquals(3, EditRebase.offset(6, Bias.BEFORE, rep));
        assertEquals(5, EditRebase.offset(8, Bias.BEFORE, rep));
    }

    @Test
    void compose_drops_typing_then_deleting() {
        // type "x" inside the field, then delete it
        List<TextEdit> c1 = List.of(TextEdit.insert(4, "x"));
        List<TextEdit> c2 = List.of(TextEdit.replace(after(c1), 4, 5, ""));
        assertTrue(EditRebase.compose(TEXT, List.of(c1, c2)).isEmpty());
        // and the field can be edited
        Result r = EditRebase.rebase(List.of(FIELD), List.of(EditRebase.compose(TEXT, List.of(c1, c2))));
        assertSame(FIELD, assertInstanceOf(Result.Moved.class, r).edits().get(0));
    }

    @Test
    void compose_merges_touching_typing() {
        String t = TEXT;
        List<List<TextEdit>> changes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<TextEdit> c = List.of(TextEdit.insert(1 + i, "k"));
            changes.add(c);
            t = TextEdit.applyAll(t, c);
        }
        List<TextEdit> net = EditRebase.compose(TEXT, changes);
        assertEquals(List.of(TextEdit.insert(1, "kkkk")), net);
        assertEquals(t, TextEdit.applyAll(TEXT, net));
    }

    @Test
    void compose_trims_a_retyped_character() {
        List<TextEdit> c1 = List.of(TextEdit.replace(TEXT, 3, 6, ""));
        List<TextEdit> c2 = List.of(TextEdit.insert(3, "dEf"));
        assertEquals(List.of(TextEdit.replace(TEXT, 4, 5, "E")), EditRebase.compose(TEXT, List.of(c1, c2)));
    }

    @Test
    void compose_refuses_a_change_that_does_not_match() {
        assertThrows(IllegalArgumentException.class,
            () -> EditRebase.compose(TEXT, List.of(List.of(new TextEdit(0, 1, "z", "")))));
    }

    @Test
    void compose_gives_the_text_after_the_changes_random() {
        Random random = new Random(42);
        for (int round = 0; round < 500; round++) {
            String original = randomText(random, 1 + random.nextInt(30));
            String current = original;
            List<List<TextEdit>> changes = new ArrayList<>();
            for (int k = 0, n = random.nextInt(12); k < n; k++) {
                List<TextEdit> change = randomChange(random, current);
                changes.add(change);
                current = TextEdit.applyAll(current, change);
            }
            List<TextEdit> net = EditRebase.compose(original, changes);
            assertEquals(current, TextEdit.applyAll(original, net), "round " + round);
            for (int i = 1; i < net.size(); i++) {
                assertTrue(net.get(i - 1).end() <= net.get(i).start());
            }
            // rebase through the net change agrees with rebase through the changes when both move
            TextEdit probe = TextEdit.replace(original, original.length() / 2, original.length() / 2, "P");
            Result viaChanges = EditRebase.rebase(List.of(probe), changes);
            Result viaNet = EditRebase.rebase(List.of(probe), List.of(net));
            if (viaChanges instanceof Result.Moved m) {
                assertTrue(m.edits().get(0).matches(current));
            }
            if (viaNet instanceof Result.Moved m) {
                assertTrue(m.edits().get(0).matches(current));
            }
        }
    }

    private static String randomText(Random random, int length) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) b.append((char) ('a' + random.nextInt(3)));
        return b.toString();
    }

    private static List<TextEdit> randomChange(Random random, String text) {
        List<TextEdit> edits = new ArrayList<>();
        int pos = 0;
        for (int k = 0, n = 1 + random.nextInt(3); k < n && pos <= text.length(); k++) {
            int start = pos + random.nextInt(text.length() - pos + 1);
            int end = start + random.nextInt(Math.min(3, text.length() - start) + 1);
            edits.add(TextEdit.replace(text, start, end, randomText(random, random.nextInt(3))));
            pos = end + 1;
        }
        return edits;
    }
}
