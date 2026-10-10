package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.view.DisplayStyleCss;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.gui.view.KindCatalog;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Window;

/**
 * Edits the display styles of a grammar: the style of all its objects, and the style of each kind,
 * which overrides it property by property. A property not set (check box grey, colour unchecked,
 * "Default" font) comes from the more general style. Returns the project's styles with the edits;
 * nothing changes on Cancel.
 */
public final class DisplayStyleDialog {

    private static final String SAMPLE = "a mio-jaflo-m-wi-j  #\"verb\"";
    private static final String NOT_SET = "(not set)";

    private final GrammarPlugin plugin;
    private final List<String> kinds;
    private DisplayStyles styles;
    private Key current;
    /** True while the controls are filled from a style (their listeners must not write back). */
    private boolean loading;

    private final ComboBox<String> family = new ComboBox<>();
    private final CheckBox bold = tristate("Bold");
    private final CheckBox italic = tristate("Italic");
    private final CheckBox underline = tristate("Underline");
    private final CheckBox strikethrough = tristate("Strikethrough");
    private final CheckBox setText = new CheckBox();
    private final ColorPicker textColor = new ColorPicker();
    private final CheckBox setBackground = new CheckBox();
    private final ColorPicker background = new ColorPicker();
    private final Label preview = new Label(SAMPLE);
    private final Label inheritHelp = new Label();
    private ListView<Key> entries;

    /**
     * @param kinds the kinds offered (see {@link KindCatalog})
     */
    public DisplayStyleDialog(GrammarPlugin plugin, List<String> kinds, DisplayStyles styles) {
        this.plugin = plugin;
        this.kinds = kinds;
        this.styles = styles;
    }

    /** The project's styles with the edits of this grammar; empty on Cancel. */
    public Optional<DisplayStyles> showAndWait(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Plugin display");
        dialog.setHeaderText("Display of the \"" + plugin.getDisplayName() + "\" objects in the editor");

        List<Key> keys = new ArrayList<>();
        keys.add(Key.grammar(plugin.getId()));
        kinds.forEach(k -> keys.add(new Key(plugin.getId(), k)));
        entries = new ListView<>(FXCollections.observableArrayList(keys));
        entries.setId("displayStyleEntries");
        entries.setPrefWidth(220);
        entries.setPrefHeight(260);
        entries.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Key key, boolean empty) {
                super.updateItem(key, empty);
                if (empty || key == null) {
                    setText(null);
                    return;
                }
                String name = key.kind() == null ? "All objects of the plugin" : "    " + KindCatalog.shortName(key.kind());
                setText(name + (styles.get(key).isEmpty() ? "" : "   (styled)"));
                setStyle(key.kind() == null ? "-fx-font-weight: bold;" : "");
            }
        });
        entries.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> show(n));

        family.getItems().add(NOT_SET);
        family.getItems().addAll(Font.getFamilies());
        family.setTooltip(new Tooltip("A proportional font breaks the alignment of interlinear glosses"));
        family.setMaxWidth(Double.MAX_VALUE);
        family.valueProperty().addListener((obs, o, n) -> edited());
        for (CheckBox c : List.of(bold, italic, underline, strikethrough)) {
            c.selectedProperty().addListener((obs, o, n) -> edited());
            c.indeterminateProperty().addListener((obs, o, n) -> edited());
        }
        setText.selectedProperty().addListener((obs, o, n) -> edited());
        setBackground.selectedProperty().addListener((obs, o, n) -> edited());
        textColor.valueProperty().addListener((obs, o, n) -> {
            if (!loading) setText.setSelected(true);
            edited();
        });
        background.valueProperty().addListener((obs, o, n) -> {
            if (!loading) setBackground.setSelected(true);
            edited();
        });
        textColor.disableProperty().bind(setText.selectedProperty().not());
        background.disableProperty().bind(setBackground.selectedProperty().not());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Font family"), family);
        grid.addRow(1, new Label("Style"), new HBox(10, bold, italic));
        grid.addRow(2, new Label("Decoration"), new HBox(10, underline, strikethrough));
        grid.addRow(3, new Label("Text colour"), new HBox(6, setText, textColor));
        grid.addRow(4, new Label("Background"), new HBox(6, setBackground, background));
        preview.setId("displayStylePreview");
        preview.setPadding(new Insets(6));
        preview.setMinHeight(32);
        preview.setFont(Font.font("Monospaced"));
        grid.addRow(5, new Label("Preview"), preview);
        inheritHelp.setWrapText(true);
        inheritHelp.setMaxWidth(340);
        inheritHelp.setStyle("-fx-text-fill: #666666;");
        Button reset = new Button("Reset to default");
        reset.setOnAction(e -> {
            if (current != null) {
                styles = styles.with(current, DisplayStyle.EMPTY);
                show(current);
                entries.refresh();
            }
        });
        VBox right = new VBox(10, grid, inheritHelp, reset);
        HBox content = new HBox(14, entries, right);
        content.setAlignment(Pos.TOP_LEFT);
        dialog.getDialogPane().setContent(content);

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
        entries.getSelectionModel().selectFirst();
        Optional<ButtonType> answer = dialog.showAndWait();
        return answer.isPresent() && answer.get() == ok ? Optional.of(styles) : Optional.empty();
    }

    private static CheckBox tristate(String text) {
        CheckBox c = new CheckBox(text);
        c.setAllowIndeterminate(true);
        c.setIndeterminate(true);
        return c;
    }

    /** Fills the controls with the style of an entry. */
    private void show(Key key) {
        current = key;
        if (key == null) return;
        DisplayStyle s = styles.get(key);
        loading = true;
        try {
            family.setValue(s.fontFamily() == null ? NOT_SET : s.fontFamily());
            setTristate(bold, s.bold());
            setTristate(italic, s.italic());
            setTristate(underline, s.underline());
            setTristate(strikethrough, s.strikethrough());
            DisplayStyle shown = s.over(base(key));
            setText.setSelected(s.textColor() != null);
            textColor.setValue(color(shown.textColor(), Color.BLACK));
            setBackground.setSelected(s.backgroundColor() != null);
            background.setValue(color(shown.backgroundColor(), Color.WHITE));
        } finally {
            loading = false;
        }
        inheritHelp.setText(key.kind() == null
            ? "Grey check boxes, unchecked colours and \"" + NOT_SET + "\" use the plugin's defaults and the editor font."
            : "Grey check boxes, unchecked colours and \"" + NOT_SET + "\" use the style of all the objects of the plugin.");
        updatePreview();
    }

    private static void setTristate(CheckBox c, Boolean value) {
        c.setIndeterminate(value == null);
        c.setSelected(Boolean.TRUE.equals(value));
    }

    private static Boolean tristate(CheckBox c) {
        return c.isIndeterminate() ? null : c.isSelected();
    }

    /** The controls changed: store the style of the current entry. */
    private void edited() {
        if (loading || current == null) return;
        String f = family.getValue();
        DisplayStyle s = new DisplayStyle(
            f == null || f.equals(NOT_SET) ? null : f,
            tristate(bold), tristate(italic), tristate(underline), tristate(strikethrough),
            setText.isSelected() ? hex(textColor.getValue()) : null,
            setBackground.isSelected() ? hex(background.getValue()) : null);
        styles = styles.with(current, s);
        entries.refresh();
        updatePreview();
    }

    /** What the properties not set by the entry come from. */
    private DisplayStyle base(Key key) {
        DisplayStyle defaults = HighlightStyles.defaultStyle(plugin, key.kind());
        return key.kind() == null ? defaults : styles.get(Key.grammar(key.grammarId())).over(defaults);
    }

    private void updatePreview() {
        if (current == null) return;
        DisplayStyle resolved = styles.get(current).over(base(current));
        preview.setStyle("-fx-font-family: \"Monospaced\";" + DisplayStyleCss.label(resolved));
        // labels cannot strike through: said in the text
        preview.setText(Boolean.TRUE.equals(resolved.strikethrough()) ? SAMPLE + "   (struck through)" : SAMPLE);
    }

    private static Color color(String css, Color fallback) {
        try {
            return css == null ? fallback : Color.web(css);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x",
            (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
