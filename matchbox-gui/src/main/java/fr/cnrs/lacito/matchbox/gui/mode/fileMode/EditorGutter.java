package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * The margin of the editor, the same width on every line so that the text always starts at the
 * same position: the line number (optional), then a column of markers — ▶ to play the media
 * segment of the line, ✚ for the text actions of plugins (a menu), ● for parse errors and ◆ for
 * warnings (tooltip; hovering ◆ outlines the reading that was not kept). A thin band left of the
 * markers shows the lines of the section containing the caret.
 */
final class EditorGutter {

    /** Width of the marker column: room for ▶, ✚ and ●. */
    static final double MARKER_WIDTH = 46;
    /** Width of the section band. */
    static final double BAND_WIDTH = 3;
    private static final String BAND_STYLE = "-fx-background-color: #9db7d5;";
    private static final String GUTTER_STYLE = "-fx-background-color: #f4f4f4; -fx-border-color: transparent #dddddd transparent transparent;";

    private EditorGutter() {
    }

    /**
     * @param lineNumbers show the numbers
     * @param digits      digits of the last line number
     * @param font        font of the editor (numbers use its size)
     * @param markers     markers by line
     * @param playButton  the ▶ button of a segment
     * @param runAction   runs a text action
     * @param band        lines of the section at the caret (empty: none); the band follows it
     *                    without rebuilding the margin
     * @param hover       called with a warning while its marker is hovered, then with null
     */
    static IntFunction<Node> factory(boolean lineNumbers, int digits, Font font, Map<Integer, GutterLayout.Markers> markers,
                                     Function<MediaSegment, Button> playButton, Consumer<TextAction> runAction,
                                     ObservableValue<Optional<SectionBand.Lines>> band, Consumer<Diagnostic> hover) {
        double numberWidth = lineNumbers ? digitWidth(font) * digits + 10 : 0;
        String numberStyle = "-fx-text-fill: #999999; -fx-font-family: \"" + font.getFamily() + "\"; -fx-font-size: "
            + font.getSize() + "px;";
        return line -> {
            HBox box = new HBox();
            box.setStyle(GUTTER_STYLE);
            box.setAlignment(Pos.TOP_LEFT);
            if (lineNumbers) {
                Label number = new Label(Integer.toString(line + 1));
                number.setStyle(numberStyle);
                number.setAlignment(Pos.TOP_RIGHT);
                fix(number, numberWidth);
                box.getChildren().add(number);
            }
            Region bandCell = new Region();
            fix(bandCell, BAND_WIDTH);
            bandCell.setStyle(BAND_STYLE);
            // weak: the binding does not keep the lines of a discarded margin alive
            bandCell.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> band.getValue().map(l -> l.contains(line)).orElse(false), band));
            box.getChildren().add(bandCell);
            HBox column = new HBox(1);
            column.setPadding(new Insets(0, 2, 0, 4));
            column.setAlignment(Pos.CENTER_LEFT);
            fix(column, MARKER_WIDTH);
            GutterLayout.Markers m = markers.get(line);
            if (m != null) {
                if (!m.segments().isEmpty()) {
                    Button play = playButton.apply(m.segments().get(0));
                    column.getChildren().add(play);
                }
                if (!m.actions().isEmpty()) column.getChildren().add(actions(m, runAction));
                if (!m.errors().isEmpty()) {
                    column.getChildren().add(error(m));
                } else if (!m.warnings().isEmpty()) {
                    column.getChildren().add(warning(m, hover));
                }
            }
            box.getChildren().add(column);
            return box;
        };
    }

    private static MenuButton actions(GutterLayout.Markers m, Consumer<TextAction> runAction) {
        MenuButton plus = new MenuButton("✚");
        plus.setStyle("-fx-font-size: 0.75em; -fx-padding: 0 1 0 1; -fx-mark-color: transparent;");
        plus.setFocusTraversable(false);
        plus.setAccessibleText("Actions of this line");
        StringBuilder tip = new StringBuilder();
        for (TextAction action : m.actions()) {
            MenuItem item = new MenuItem(action.label());
            item.setOnAction(e -> runAction.accept(action));
            plus.getItems().add(item);
            if (tip.length() > 0) tip.append('\n');
            tip.append(action.label()).append(action.description().isBlank() ? "" : " — " + action.description());
        }
        plus.setTooltip(new Tooltip(tip.toString()));
        return plus;
    }

    private static Label error(GutterLayout.Markers m) {
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + ErrorRuler.ERROR_COLOR + "; -fx-font-size: 0.8em;");
        dot.setTooltip(new Tooltip(String.join("\n", m.messages())));
        dot.setAccessibleText("Parse error: " + String.join("; ", m.messages()));
        return dot;
    }

    private static Label warning(GutterLayout.Markers m, Consumer<Diagnostic> hover) {
        Label diamond = new Label("◆");
        diamond.setId("warningMarker");
        diamond.setStyle("-fx-text-fill: " + ErrorRuler.WARNING_COLOR + "; -fx-font-size: 0.8em;");
        Tooltip tip = new Tooltip(String.join("\n", m.messages()));
        tip.setWrapText(true);
        tip.setMaxWidth(520);
        diamond.setTooltip(tip);
        diamond.setAccessibleText("Warning: " + String.join("; ", m.messages()));
        Diagnostic first = m.warnings().get(0);
        diamond.setOnMouseEntered(e -> hover.accept(first));
        diamond.setOnMouseExited(e -> hover.accept(null));
        return diamond;
    }

    private static void fix(Region r, double width) {
        r.setMinWidth(width);
        r.setPrefWidth(width);
        r.setMaxWidth(width);
    }

    static double digitWidth(Font font) {
        Text t = new Text("0");
        t.setFont(font);
        return Math.ceil(t.getLayoutBounds().getWidth());
    }
}
