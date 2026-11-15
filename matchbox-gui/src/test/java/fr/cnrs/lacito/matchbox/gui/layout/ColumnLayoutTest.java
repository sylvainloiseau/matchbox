package fr.cnrs.lacito.matchbox.gui.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnLayoutTest {

    private static final double E = 1e-9;
    private final ColumnLayout three = ColumnLayout.of(0.2, 0.5, 0.3);

    @Test
    void dividersOfShownColumns() {
        assertArrayEquals(new double[] {0.2, 0.7}, three.dividerPositions(), E);
        // the middle one hidden: the outer ones share the room 2:3
        assertArrayEquals(new double[] {0.4}, three.withShown(1, false).dividerPositions(), E);
        // one column: no divider
        assertArrayEquals(new double[0], three.withShown(0, false).withShown(1, false).dividerPositions(), E);
    }

    @Test
    void hideThenShowRestoresTheWidths() {
        ColumnLayout back = three.withShown(0, false).withShown(0, true);
        assertEquals(three, back);
        assertArrayEquals(new double[] {0.2, 0.7}, back.dividerPositions(), E);
    }

    @Test
    void theLastShownColumnCannotBeHidden() {
        ColumnLayout one = three.withShown(0, false).withShown(2, false);
        assertEquals(List.of(1), one.shownPanes());
        assertFalse(one.canHide(1));
        assertSame(one, one.withShown(1, false));
        assertSame(one, one.withOnly(List.of()));
        assertTrue(three.canHide(1));
    }

    @Test
    void sizesAreNormalised() {
        ColumnLayout l = ColumnLayout.of(1, 1, 2);
        assertEquals(List.of(0.25, 0.25, 0.5), l.sizes());
        // invalid sizes become the minimum
        assertTrue(ColumnLayout.of(-1, 1).sizes().get(0) >= ColumnLayout.MIN_SIZE - E);
    }

    @Test
    void draggingChangesOnlyTheShownColumns() {
        ColumnLayout hidden = three.withShown(0, false);
        // editor and objects take 0.8 together; the user splits them 50/50
        ColumnLayout dragged = hidden.dragged(new double[] {0.5});
        assertEquals(0.2, dragged.sizes().get(0), E);
        assertEquals(0.4, dragged.sizes().get(1), E);
        assertEquals(0.4, dragged.sizes().get(2), E);
        assertArrayEquals(new double[] {0.5}, dragged.dividerPositions(), E);
        // all shown: the dividers are where the user put them
        assertArrayEquals(new double[] {0.1, 0.6}, three.dragged(new double[] {0.1, 0.6}).dividerPositions(), E);
        // a wrong number of positions changes nothing
        assertSame(three, three.dragged(new double[] {0.5}));
    }

    @Test
    void doubleClickCollapsesTheNarrowerNeighbourThenRestoresIt() {
        // divider 0, between 0.2 and 0.5: the first column is hidden
        ColumnLayout collapsed = three.dividerDoubleClicked(0);
        assertEquals(List.of(1, 2), collapsed.shownPanes());
        // the first divider of the remaining columns brings it back
        assertEquals(three, collapsed.dividerDoubleClicked(0));
        // the last column collapsed and restored from the last divider
        ColumnLayout right = ColumnLayout.of(0.2, 0.2, 0.6).withShown(0, false);
        ColumnLayout noLeft = ColumnLayout.of(0.4, 0.4, 0.2);
        assertEquals(List.of(0, 1), noLeft.dividerDoubleClicked(1).shownPanes());
        assertEquals(noLeft, noLeft.dividerDoubleClicked(1).dividerDoubleClicked(0).withShown(0, true));
        // a column hidden between two shown ones comes back first
        ColumnLayout middle = three.withShown(1, false);
        assertEquals(three, middle.dividerDoubleClicked(0));
        // no divider: nothing
        ColumnLayout single = right.withShown(1, false);
        assertSame(single, single.dividerDoubleClicked(0));
    }
}
