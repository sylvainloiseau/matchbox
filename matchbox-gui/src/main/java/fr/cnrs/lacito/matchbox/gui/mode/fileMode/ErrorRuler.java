package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.Map;
import java.util.function.IntConsumer;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

/**
 * A narrow strip beside the editor's scroll bar marking where the parse errors (red) and the
 * warnings (amber) are in the whole file; a click on a mark goes to its line.
 */
public final class ErrorRuler extends Pane {

    static final double WIDTH = 8;
    static final String ERROR_COLOR = "#d32f2f";
    static final String WARNING_COLOR = "#f0a000";
    private Map<Integer, GutterLayout.Markers> markers = Map.of();
    private int lineCount = 1;
    private final IntConsumer goToLine;

    public ErrorRuler(IntConsumer goToLine) {
        this.goToLine = goToLine;
        setId("errorRuler");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        setStyle("-fx-background-color: #fafafa; -fx-border-color: transparent transparent transparent #e0e0e0;");
        heightProperty().addListener((obs, o, n) -> draw());
    }

    public void setMarkers(Map<Integer, GutterLayout.Markers> markers, int lineCount) {
        this.markers = markers;
        this.lineCount = Math.max(1, lineCount);
        draw();
    }

    /** The number of marks (for tests). */
    public int markCount() {
        return getChildren().size();
    }

    private void draw() {
        getChildren().clear();
        double h = getHeight();
        markers.forEach((line, m) -> {
            if (m.errors().isEmpty() && m.warnings().isEmpty()) return;
            Rectangle mark = new Rectangle(1, Math.max(0, GutterLayout.rulerPosition(line, lineCount) * h - 2), WIDTH - 2, 4);
            mark.setFill(javafx.scene.paint.Color.web(m.warningOnly() ? WARNING_COLOR : ERROR_COLOR));
            Tooltip.install(mark, new Tooltip("Line " + (line + 1) + ": " + String.join("\n", m.messages())));
            mark.setOnMouseClicked(e -> goToLine.accept(line));
            mark.setCursor(javafx.scene.Cursor.HAND);
            getChildren().add(mark);
        });
    }
}
