package fr.cnrs.lacito.matchbox.grammarapi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DisplayStyleTest {

    @Test
    void overTakesTheUnsetPropertiesFromTheBase() {
        DisplayStyle base = new DisplayStyle("Serif", true, false, null, null, "#111111", "#eeeeee");
        DisplayStyle child = DisplayStyle.EMPTY.withItalic(true).withBackgroundColor("#ff0000");
        assertEquals(new DisplayStyle("Serif", true, true, null, null, "#111111", "#ff0000"), child.over(base));
        assertSame(child, child.over(null));
    }

    @Test
    void falseIsAValueNotAnAbsence() {
        DisplayStyle notBold = DisplayStyle.EMPTY.withBold(false);
        assertFalse(notBold.over(DisplayStyle.EMPTY.withBold(true)).bold());
        assertFalse(notBold.isEmpty());
    }

    @Test
    void blankValuesAreNotSetAndInvalidColoursAreDropped() {
        DisplayStyle s = new DisplayStyle(" ", null, null, null, null, "red", "#abc");
        assertNull(s.fontFamily());
        DisplayStyle valid = s.validated();
        assertNull(valid.textColor());
        assertEquals("#abc", valid.backgroundColor());
        assertTrue(DisplayStyle.isColor("#A0b1C2"));
        assertFalse(DisplayStyle.isColor("#12"));
        assertTrue(DisplayStyle.EMPTY.isEmpty());
    }
}
