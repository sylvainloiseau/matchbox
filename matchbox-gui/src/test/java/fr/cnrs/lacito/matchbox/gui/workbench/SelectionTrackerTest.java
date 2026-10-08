package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import org.junit.jupiter.api.Test;

class SelectionTrackerTest {

    private static final String TEXT = "<mami> {pig}\n<jaflo> {canoe}\n";
    private final Corpus corpus = new Corpus();
    private final EditHints hints = new EditHints();
    private final SelectionTracker tracker = new SelectionTracker(hints);

    private CorpusObject entry(int i) {
        return (CorpusObject) corpus.text(ParsedTexts.ID).orElseThrow().objectsOf(LexicalEntry.class).get(i);
    }

    @Test
    void theSelectionFollowsAReparse() {
        corpus.put(ParsedTexts.parse(TEXT));
        CorpusObject jaflo = entry(1);
        tracker.set(jaflo);
        corpus.put(ParsedTexts.parse("% a note\n" + TEXT));
        tracker.textChanged(ParsedTexts.ID, corpus, false);
        assertNotSame(jaflo, tracker.get());
        assertEquals(jaflo.getLabel(), tracker.get().getLabel());
    }

    @Test
    void theSelectionIsClearedWhenTheTextIsRemoved() {
        corpus.put(ParsedTexts.parse(TEXT));
        tracker.set(entry(0));
        corpus.remove(ParsedTexts.ID);
        tracker.textChanged(ParsedTexts.ID, corpus, false);
        assertNull(tracker.get());
    }

    @Test
    void aPendingSelectionIsTakenOnce() {
        corpus.put(ParsedTexts.parse(TEXT));
        hints.selectAfterParse(ParsedTexts.ID, TEXT.indexOf("jaflo"));
        tracker.textChanged(ParsedTexts.ID, corpus, false);
        assertNull(tracker.get(), "not taken when not asked");
        tracker.textChanged(ParsedTexts.ID, corpus, true);
        assertTrue(tracker.get().getTextCoordinate().contains(TEXT.indexOf("jaflo")));
        tracker.set(null);
        tracker.textChanged(ParsedTexts.ID, corpus, true);
        assertNull(tracker.get(), "the hint was consumed");
    }
}
