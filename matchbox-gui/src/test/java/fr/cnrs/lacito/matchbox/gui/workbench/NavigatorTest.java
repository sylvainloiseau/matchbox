package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NavigatorTest {

    private static final String TEXT = "<mami> {pig}\n<jaflo> {canoe}\n";

    @Test
    void placesFollowReparsesAndAreDroppedWithTheirText() {
        Corpus corpus = new Corpus();
        corpus.put(ParsedTexts.parse(TEXT));
        CorpusObject jaflo = (CorpusObject) corpus.text(ParsedTexts.ID).orElseThrow()
            .objectsOf(LexicalEntry.class).get(1);
        Navigator.Place place = new Navigator.Place("file", Optional.of(Focus.of(jaflo)));

        corpus.put(ParsedTexts.parse("% a note\n" + TEXT));
        Navigator.Place updated = Navigator.update(place, corpus).orElseThrow();
        CorpusObject successor = updated.focus().orElseThrow().object().orElseThrow();
        assertNotSame(jaflo, successor);
        assertEquals(jaflo.getLabel(), successor.getLabel());

        corpus.remove(ParsedTexts.ID);
        assertTrue(Navigator.update(place, corpus).isEmpty());
        Navigator.Place nowhere = new Navigator.Place("file", Optional.empty());
        assertEquals(Optional.of(nowhere), Navigator.update(nowhere, corpus));
    }
}
