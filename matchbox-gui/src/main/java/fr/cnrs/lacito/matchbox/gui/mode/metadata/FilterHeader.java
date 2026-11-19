package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import java.util.function.Consumer;
import javafx.event.Event;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The header of a column of the Metadata table: its title (a click on it sorts, as on any header),
 * and below, the filter of the column: a text field and a ⚙ menu (contains / equals / regular
 * expression, match case, empty or non-empty cells only, invert). The filter controls keep the
 * mouse to themselves, so typing in them neither sorts nor drags the column.
 */
public final class FilterHeader extends VBox {

    public static final String STYLE_CLASS = "metadata-filter";

    private final Label title = new Label();
    private final TextField field = new TextField();
    private final MenuButton options = new MenuButton("⚙");
    private final Consumer<ColumnFilter> changed;
    private ColumnFilter filter = ColumnFilter.NONE;
    private boolean updating;

    private final RadioMenuItem contains = new RadioMenuItem("Contains");
    private final RadioMenuItem equals = new RadioMenuItem("Equals");
    private final RadioMenuItem regex = new RadioMenuItem("Regular expression");
    private final CheckMenuItem matchCase = new CheckMenuItem("Match case");
    private final RadioMenuItem anyCells = new RadioMenuItem("All cells");
    private final RadioMenuItem emptyCells = new RadioMenuItem("Empty cells only");
    private final RadioMenuItem nonEmptyCells = new RadioMenuItem("Non-empty cells only");
    private final CheckMenuItem negate = new CheckMenuItem("Not (invert the filter)");

    /** @param id the column id, kept as user data (GUI tests find a filter by it) */
    public FilterHeader(String id, Consumer<ColumnFilter> changed) {
        this.changed = changed;
        getStyleClass().add(STYLE_CLASS);
        setUserData(id);
        setSpacing(2);
        title.setStyle("-fx-font-weight: bold;");
        title.setMaxWidth(Double.MAX_VALUE);
        field.setPromptText("filter");
        field.setPrefColumnCount(6);
        field.getStyleClass().add("metadata-filter-field");
        field.setTooltip(new Tooltip("Filter this column (Escape clears it)"));
        options.setTooltip(new Tooltip("Filter options"));
        options.getStyleClass().add("metadata-filter-options");
        options.setFocusTraversable(false);
        ToggleGroup match = new ToggleGroup();
        contains.setToggleGroup(match);
        equals.setToggleGroup(match);
        regex.setToggleGroup(match);
        ToggleGroup cells = new ToggleGroup();
        anyCells.setToggleGroup(cells);
        emptyCells.setToggleGroup(cells);
        nonEmptyCells.setToggleGroup(cells);
        MenuItem clear = new MenuItem("Clear the filter");
        clear.setOnAction(e -> set(ColumnFilter.NONE));
        options.getItems().addAll(contains, equals, regex, new SeparatorMenuItem(), matchCase, new SeparatorMenuItem(),
            anyCells, emptyCells, nonEmptyCells, new SeparatorMenuItem(), negate, new SeparatorMenuItem(), clear);
        contains.setOnAction(e -> update(filter.withMatch(ColumnFilter.Match.CONTAINS)));
        equals.setOnAction(e -> update(filter.withMatch(ColumnFilter.Match.EQUALS)));
        regex.setOnAction(e -> update(filter.withMatch(ColumnFilter.Match.REGEX)));
        matchCase.setOnAction(e -> update(filter.withCaseSensitive(matchCase.isSelected())));
        anyCells.setOnAction(e -> update(filter.withCells(ColumnFilter.Cells.ANY)));
        emptyCells.setOnAction(e -> update(filter.withCells(ColumnFilter.Cells.EMPTY)));
        nonEmptyCells.setOnAction(e -> update(filter.withCells(ColumnFilter.Cells.NON_EMPTY)));
        negate.setOnAction(e -> update(filter.withNegate(negate.isSelected())));
        field.textProperty().addListener((obs, o, n) -> {
            if (!updating) update(filter.withText(n));
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                set(ColumnFilter.NONE);
                e.consume();
            }
        });
        HBox controls = new HBox(2, field, options);
        HBox.setHgrow(field, Priority.ALWAYS);
        // the header sorts on click and drags the column: not from the filter controls
        controls.addEventHandler(MouseEvent.ANY, Event::consume);
        getChildren().addAll(title, controls);
        show();
    }

    public void setTitle(String text, String tooltip) {
        title.setText(text);
        title.setTooltip(tooltip == null ? null : new Tooltip(tooltip));
    }

    public ColumnFilter filter() {
        return filter;
    }

    public TextField field() {
        return field;
    }

    /** Sets the filter (from code: Clear filters) and tells the table. */
    public void set(ColumnFilter f) {
        update(f);
    }

    private void update(ColumnFilter f) {
        if (f.equals(filter)) {
            show();
            return;
        }
        filter = f;
        show();
        changed.accept(f);
    }

    private void show() {
        updating = true;
        try {
            if (!field.getText().equals(filter.text())) field.setText(filter.text());
            contains.setSelected(filter.match() == ColumnFilter.Match.CONTAINS);
            equals.setSelected(filter.match() == ColumnFilter.Match.EQUALS);
            regex.setSelected(filter.match() == ColumnFilter.Match.REGEX);
            matchCase.setSelected(filter.caseSensitive());
            anyCells.setSelected(filter.cells() == ColumnFilter.Cells.ANY);
            emptyCells.setSelected(filter.cells() == ColumnFilter.Cells.EMPTY);
            nonEmptyCells.setSelected(filter.cells() == ColumnFilter.Cells.NON_EMPTY);
            negate.setSelected(filter.negate());
        } finally {
            updating = false;
        }
        options.setText(filter.hasOptions() ? "⚙●" : "⚙");
        var error = filter.error();
        field.setStyle(error.isPresent() ? "-fx-border-color: #c33;" : "");
        field.setTooltip(new Tooltip(error.map(e -> "Invalid regular expression: " + e)
            .orElse("Filter this column (Escape clears it)")));
        setStyle(filter.isActive() ? "-fx-background-color: rgba(255, 200, 0, 0.18);" : "");
    }
}
