package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ObjectSuccessorTest {

    private static final String TEXT = "<mami> {pig}\n<jaflo> {canoe}\n";

    private static CorpusObject entry(Text text, int i) {
        return (CorpusObject) text.objectsOf(LexicalEntry.class).get(i);
    }

    @Test
    void anObjectOfTheSameParseIsItsOwnSuccessor() {
        Text text = ParsedTexts.parse(TEXT);
        CorpusObject jaflo = entry(text, 1);
        assertSame(jaflo, ObjectSuccessor.find(text, jaflo, OptionalInt.empty()).orElseThrow());
    }

    @Test
    void anObjectMovedByAnInsertionIsFoundByItsLabel() {
        CorpusObject jaflo = entry(ParsedTexts.parse(TEXT), 1);
        Text after = ParsedTexts.parse("<new> {x}\n" + TEXT);
        CorpusObject successor = ObjectSuccessor.find(after, jaflo, OptionalInt.empty()).orElseThrow();
        assertEquals(jaflo.getLabel(), successor.getLabel());
        assertEquals(jaflo.getTextCoordinate().startIndex() + "<new> {x}\n".length(),
            successor.getTextCoordinate().startIndex());
    }

    @Test
    void afterAViewEditTheObjectContainingTheEditIsChosen() {
        CorpusObject jaflo = entry(ParsedTexts.parse(TEXT), 1);
        Text after = ParsedTexts.parse("<mami> {pig}\n<jaflo> {boat}\n");
        int editOffset = after.content().indexOf("boat");
        CorpusObject successor = ObjectSuccessor.find(after, jaflo, OptionalInt.of(editOffset)).orElseThrow();
        assertTrue(successor.getTextCoordinate().contains(editOffset));
    }

    @Test
    void aDeletedObjectHasNoSuccessor() {
        CorpusObject jaflo = entry(ParsedTexts.parse(TEXT), 1);
        Text after = ParsedTexts.parse("plain text only\n");
        assertTrue(ObjectSuccessor.find(after, jaflo, OptionalInt.empty()).isEmpty());
    }
}
