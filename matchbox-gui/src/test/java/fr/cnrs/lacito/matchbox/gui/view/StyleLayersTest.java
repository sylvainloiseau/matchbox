package fr.cnrs.lacito.matchbox.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.gui.view.StyleLayers.Run;
import java.util.List;
import org.junit.jupiter.api.Test;

class StyleLayersTest {

    private static final DisplayStyle IGT = DisplayStyle.background("#fff1c1").withBold(true);
    private static final DisplayStyle TAG = DisplayStyle.background("#ffd6e8");

    @Test
    void aChildOverridesOnlyWhatItSets() {
        StyleLayers layers = new StyleLayers();
        layers.add(0, 10, IGT);
        layers.add(4, 6, TAG);
        assertEquals(List.of(
            new Run(4, IGT),
            new Run(2, DisplayStyle.background("#ffd6e8").withBold(true)),
            new Run(4, IGT)), layers.runs(10));
    }

    @Test
    void uncoveredTextIsPlainAndLayersAreClipped() {
        StyleLayers layers = new StyleLayers();
        layers.add(2, 4, TAG);
        layers.add(8, 50, IGT);
        assertEquals(List.of(
            new Run(2, DisplayStyle.EMPTY),
            new Run(2, TAG),
            new Run(4, DisplayStyle.EMPTY),
            new Run(2, IGT)), layers.runs(10));
        assertTrue(new StyleLayers().runs(0).isEmpty());
    }

    @Test
    void overlappingLayersComposeInTheOrderTheyWereAdded() {
        StyleLayers layers = new StyleLayers();
        layers.add(0, 6, DisplayStyle.EMPTY.withItalic(true));
        layers.add(3, 9, DisplayStyle.EMPTY.withItalic(false).withUnderline(true));
        assertEquals(List.of(
            new Run(3, DisplayStyle.EMPTY.withItalic(true)),
            new Run(6, DisplayStyle.EMPTY.withItalic(false).withUnderline(true))), layers.runs(9));
    }

    @Test
    void adjacentRunsOfTheSameStyleAreMerged() {
        StyleLayers layers = new StyleLayers();
        layers.add(0, 3, TAG);
        layers.add(3, 6, TAG);
        assertEquals(List.of(new Run(6, TAG)), layers.runs(6));
    }

    @Test
    void cssOfTheEditorAndOfLabels() {
        DisplayStyle s = new DisplayStyle("Fira \"Code\"", true, false, true, true, "#111111", "#eeeeee");
        assertEquals("-fx-font-family: \"Fira Code\";-fx-font-weight: bold;-fx-font-style: normal;"
            + "-fx-underline: true;-fx-strikethrough: true;-fx-fill: #111111;-rtfx-background-color: #eeeeee;",
            DisplayStyleCss.editor(s));
        assertEquals("-fx-font-family: \"Fira Code\";-fx-font-weight: bold;-fx-font-style: normal;"
            + "-fx-underline: true;-fx-text-fill: #111111;-fx-background-color: #eeeeee;", DisplayStyleCss.label(s));
        assertEquals("", DisplayStyleCss.editor(DisplayStyle.EMPTY));
    }
}
