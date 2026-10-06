package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

/**
 * Assertions shared by grammar tests (published in the matchbox-corpus test-jar).
 */
public final class CorpusAssertions {

    private CorpusAssertions() {}

    /** Checks that every object's span, children included, is a valid range of {@code text}. */
    public static void assertSpansValid(List<? extends CorpusObject> objects, String text) {
        for (CorpusObject o : objects) {
            assertSpanValid(o.getTextCoordinate(), text);
            assertSpansValid(o.getChildren(), text);
        }
    }

    /** Checks spans are valid and every child's span lies inside its parent's span. */
    public static void assertSpansNested(List<? extends CorpusObject> objects, String text) {
        assertSpansValid(objects, text);
        for (CorpusObject parent : objects) {
            for (CorpusObject child : parent.getChildren()) {
                assertTrue(
                    parent.getTextCoordinate().encloses(child.getTextCoordinate()),
                    () -> child + " is not inside " + parent
                );
            }
            assertSpansNested(parent.getChildren(), text);
        }
    }

    /** Checks that every object's kind, children included, starts with {@code grammarId + ":"}. */
    public static void assertKindsPrefixed(List<? extends CorpusObject> objects, String grammarId) {
        for (CorpusObject o : objects) {
            assertTrue(o.getKind().startsWith(grammarId + ":"), () -> "bad kind: " + o.getKind());
            assertKindsPrefixed(o.getChildren(), grammarId);
        }
    }

    /** Checks that every object (top-level or nested) of {@code objects} belongs to {@code text}. */
    public static void assertOwnedBy(Text text, List<? extends CorpusObject> objects) {
        for (CorpusObject o : objects) {
            assertTrue(text.owns(o), () -> o + " does not belong to " + text);
            assertOwnedBy(text, o.getChildren());
        }
    }

    public static void assertSpanValid(TextCoordinate span, String text) {
        assertTrue(span.startIndex() >= 0, "negative start: " + span);
        assertTrue(span.stopIndex() >= span.startIndex(), "inverted span: " + span);
        assertTrue(span.stopIndex() < text.length(), "span beyond text: " + span);
        assertTrue(span.startLine() <= span.endLine(), "inverted lines: " + span);
    }

    /** Text covered by a span (stop index is inclusive). */
    public static String covered(TextCoordinate span, String text) {
        return text.substring(span.startIndex(), span.stopIndex() + 1);
    }

    public static void assertCovers(String expected, TextCoordinate span, String text) {
        assertEquals(expected, covered(span, text));
    }
}
