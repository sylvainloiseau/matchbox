package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NavigationHistoryTest {

    private final NavigationHistory<String> history = new NavigationHistory<>();

    @Test
    void backAndForwardLikeABrowser() {
        history.jumped("a");
        history.jumped("b");
        assertEquals(Optional.of("b"), history.back("c"));
        assertEquals(Optional.of("a"), history.back("b"));
        assertFalse(history.canGoBackProperty().get());
        assertEquals(Optional.of("b"), history.forward("a"));
        assertEquals(Optional.of("c"), history.forward("b"));
        assertFalse(history.canGoForwardProperty().get());
    }

    @Test
    void aNewJumpClearsForward() {
        history.jumped("a");
        history.back("b");
        assertTrue(history.canGoForwardProperty().get());
        history.jumped("a");
        assertFalse(history.canGoForwardProperty().get());
    }

    @Test
    void placesAreUpdatedOrDropped() {
        history.jumped("keep");
        history.jumped("drop");
        history.replaceAll(p -> p.equals("drop") ? Optional.empty() : Optional.of(p.toUpperCase()));
        assertEquals(Optional.of("KEEP"), history.back("here"));
        assertFalse(history.canGoBackProperty().get());
    }

    @Test
    void theHistoryIsBounded() {
        for (int i = 0; i < NavigationHistory.MAX + 10; i++) {
            history.jumped("p" + i);
        }
        assertEquals(NavigationHistory.MAX, history.backSize());
    }
}
