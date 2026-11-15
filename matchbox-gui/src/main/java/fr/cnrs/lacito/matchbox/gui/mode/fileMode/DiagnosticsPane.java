package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemExport;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemIndex;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemRow;
import fr.cnrs.lacito.matchbox.gui.view.DiagnosticFilter;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Problems;
import fr.cnrs.lacito.matchbox.gui.workbench.StatusLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A table of problems of File Mode (Parsing errors, Semantic issues): the diagnostics of one
 * category set, of the displayed file or of every file of the project ("All files"), with a plugin
 * filter, "Show warnings", fixes, copy and export. Rows come from the window's {@link ProblemIndex}.
 *
 * <p>Selecting a row of the displayed file selects its span; Enter or a double-click goes to it
 * (opening its file: a jump recorded for Back). A row of another file is not opened on mere
 * selection, so that moving in the table does not switch files.
 */
public final class DiagnosticsPane extends VBox {

    /** What a pane shows and where its settings are. */
    public record Config(
        String title,
        String idPrefix,
        Predicate<Diagnostic> category,
        String showWarningsTooltip,
        String emptyFile,
        String emptyProject,
        BooleanProperty showWarnings,
        BooleanProperty allFiles,
        BooleanProperty showCode) {}

    private final Config config;
    private final Tab tab;
    private final Problems problems;
    private final StatusLine status;
    private final Supplier<Optional<TextId>> displayedFile;
    private final Consumer<Focus> selectInDisplayed;
    private final Function<String, String> pluginName;
    private final Supplier<Optional<Path>> projectDirectory;
    private Function<String, Optional<Path>> exportChooser = name -> Optional.empty();

    private final ObservableList<ProblemRow> rows = FXCollections.observableArrayList();
    private final FilteredList<ProblemRow> filtered = new FilteredList<>(rows);
    private final TableView<ProblemRow> table = new TableView<>();
    private final ChoiceBox<DiagnosticFilter.Choice> pluginChoice = new ChoiceBox<>();
    private final CheckBox showWarningsCheck = new CheckBox("Show warnings");
    private final CheckBox allFilesCheck = new CheckBox("All files");
    private final Label position = new Label();
    private final Label placeholder = new Label();
    private final TableColumn<ProblemRow, String> fileColumn;
    private final TableColumn<ProblemRow, String> codeColumn;
    private boolean updatingChoices;

    /**
     * @param tab               the tab showing the pane (its title counts the problems)
     * @param displayedFile     the file shown in the editor
     * @param selectInDisplayed selects a range of the displayed file in the editor
     * @param pluginName        name shown for a grammar id
     * @param extraMenuItems    items added to the context menu (they read {@link #selected()})
     */
    public DiagnosticsPane(Config config, Tab tab, Problems problems, StatusLine status,
                           Supplier<Optional<TextId>> displayedFile, Consumer<Focus> selectInDisplayed,
                           Function<String, String> pluginName, Supplier<Optional<Path>> projectDirectory,
                           List<MenuItem> extraMenuItems) {
        this.config = config;
        this.tab = tab;
        this.problems = problems;
        this.status = status;
        this.displayedFile = displayedFile;
        this.selectInDisplayed = selectInDisplayed;
        this.pluginName = pluginName;
        this.projectDirectory = projectDirectory;
        setSpacing(4);
        setId(config.idPrefix() + "Pane");

        // filter bar
        pluginChoice.setId(config.idPrefix() + "PluginChoice");
        pluginChoice.setPrefWidth(320);
        pluginChoice.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (!updatingChoices) applyFilter();
        });
        showWarningsCheck.setId(config.idPrefix() + "ShowWarningsCheck");
        showWarningsCheck.setTooltip(new Tooltip(config.showWarningsTooltip()));
        showWarningsCheck.selectedProperty().bindBidirectional(config.showWarnings());
        showWarningsCheck.selectedProperty().addListener((obs, o, n) -> applyFilter());
        allFilesCheck.setId(config.idPrefix() + "AllFilesCheck");
        allFilesCheck.setTooltip(new Tooltip("List the problems of every file of the project, not only of the file shown"));
        allFilesCheck.selectedProperty().bindBidirectional(config.allFiles());
        allFilesCheck.selectedProperty().addListener((obs, o, n) -> refresh());
        Button export = new Button("Export…");
        export.setId(config.idPrefix() + "Export");
        export.setTooltip(new Tooltip("Write the problems listed as a CSV file"));
        export.setOnAction(e -> export());
        Button previous = new Button("◀");
        previous.setId(config.idPrefix() + "Previous");
        previous.setTooltip(new Tooltip("Previous problem of the list, and go to it"));
        previous.setOnAction(e -> step(false));
        Button next = new Button("▶");
        next.setId(config.idPrefix() + "Next");
        next.setTooltip(new Tooltip("Next problem of the list, and go to it"));
        next.setOnAction(e -> step(true));
        position.setMinWidth(Region.USE_PREF_SIZE);
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        HBox bar = new HBox(6, new Label("Plugin:"), pluginChoice, showWarningsCheck, allFilesCheck, export,
            spring, previous, next, position);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-padding: 4 4 0 4;");

        // table
        table.setId(config.idPrefix() + "TableView");
        TableColumn<ProblemRow, String> severity = new TableColumn<>("Severity");
        severity.setPrefWidth(90);
        severity.setCellValueFactory(c -> new SimpleStringProperty(DiagnosticFilter.severityLabel(c.getValue().diagnostic())));
        severity.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String label, boolean empty) {
                super.updateItem(label, empty);
                if (empty || label == null) {
                    setText(null);
                    setStyle("");
                    setAccessibleText(null);
                    return;
                }
                boolean warning = "Warning".equals(label);
                setText((warning ? "◆ " : "● ") + label);
                setStyle("-fx-text-fill: " + (warning ? "#b07000" : "#d32f2f") + ";");
                ProblemRow r = getTableRow() == null ? null : getTableRow().getItem();
                setAccessibleText(r == null ? label : accessibleText(r));
            }
        });
        fileColumn = textColumn("File", 140, ProblemRow::fileName,
            r -> r.file().path() + (problems.isUnsaved(r.file()) ? " (not saved)" : ""));
        fileColumn.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().fileName() + (problems.isUnsaved(c.getValue().file()) ? " •" : "")));
        TableColumn<ProblemRow, Integer> line = new TableColumn<>("Line");
        line.setPrefWidth(60);
        line.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().line()).asObject());
        TableColumn<ProblemRow, Integer> column = new TableColumn<>("Position");
        column.setPrefWidth(70);
        column.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().diagnostic().column()).asObject());
        TableColumn<ProblemRow, String> plugin = textColumn("Plugin", 220,
            r -> pluginName.apply(r.diagnostic().grammarId()), r -> pluginName.apply(r.diagnostic().grammarId()));
        TableColumn<ProblemRow, String> object = textColumn("Object", 140, ProblemRow::objectLabel,
            r -> r.object().map(o -> o.getKind() + ": " + o.getLabel()).orElse("No object at this place"));
        TableColumn<ProblemRow, String> message = textColumn("Message", 520, r -> r.diagnostic().message(),
            r -> r.diagnostic().message() + (r.diagnostic().code().isEmpty() ? "" : "\nCode: " + r.diagnostic().code())
                + "\nLine " + r.line() + ", position " + r.diagnostic().column()
                + " · " + pluginName.apply(r.diagnostic().grammarId())
                + r.object().map(o -> " · " + o.getLabel()).orElse(""));
        codeColumn = textColumn("Code", 160, r -> r.diagnostic().code(), r -> r.diagnostic().code());
        TableColumn<ProblemRow, ProblemRow> fix = new TableColumn<>("Fix");
        fix.setPrefWidth(60);
        fix.setSortable(false);
        fix.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        fix.setCellFactory(c -> new FixCell());
        table.getColumns().addAll(List.of(severity, fileColumn, line, column, plugin, object, message, codeColumn, fix));
        fileColumn.visibleProperty().bind(config.allFiles());
        codeColumn.visibleProperty().bind(config.showCode());
        // a narrow panel shows the essential columns only
        var compact = javafx.beans.binding.Bindings.createBooleanBinding(
            () -> fr.cnrs.lacito.matchbox.gui.problems.ProblemColumns.isCompact(table.getWidth()), table.widthProperty());
        for (TableColumn<ProblemRow, ?> c : List.of(column, plugin, object)) c.visibleProperty().bind(compact.not());

        SortedList<ProblemRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
            () -> table.getComparator() == null ? ProblemRow.ORDER : table.getComparator(),
            table.comparatorProperty()));
        table.setItems(sorted);
        placeholder.setWrapText(true);
        table.setPlaceholder(placeholder);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            updatePosition();
            if (n != null && displayedFile.get().map(n.file()::equals).orElse(false)) {
                problems.target(n).ifPresent(selectInDisplayed);
            }
        });
        table.setRowFactory(t -> {
            TableRow<ProblemRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) problems.go(row.getItem());
            });
            return row;
        });
        KeyCombination copy = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        table.setOnKeyPressed(e -> {
            ProblemRow selected = table.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && selected != null) {
                problems.go(selected);
                e.consume();
            } else if (copy.match(e) && selected != null) {
                copy(ProblemExport.line(selected));
                e.consume();
            }
        });
        table.setContextMenu(contextMenu(extraMenuItems));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(bar, table);

        problems.index().addListener(this::indexChanged);
        config.showCode().addListener((obs, o, n) -> table.refresh());
        refresh();
    }

    private TableColumn<ProblemRow, String> textColumn(String title, double width, Function<ProblemRow, String> value,
                                                      Function<ProblemRow, String> tooltip) {
        TableColumn<ProblemRow, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                ProblemRow r = empty || getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(r == null ? null : new Tooltip(tooltip.apply(r)));
            }
        });
        return c;
    }

    /** The button of the fixes of a row: one fix is applied, several are offered in a menu. */
    private final class FixCell extends TableCell<ProblemRow, ProblemRow> {
        @Override
        protected void updateItem(ProblemRow r, boolean empty) {
            super.updateItem(r, empty);
            setText(null);
            if (empty || r == null || !r.hasFixes()) {
                setGraphic(null);
                return;
            }
            List<Diagnostic.Fix> fixes = r.diagnostic().fixes();
            Button button = new Button(fixes.size() == 1 ? "Fix" : "Fix ▾");
            button.getStyleClass().add("fix-button");
            button.setTooltip(new Tooltip(fixes.size() == 1 ? fixes.get(0).label()
                : fixes.stream().map(Diagnostic.Fix::label).reduce((a, b) -> a + "\n" + b).orElse("")));
            button.setAccessibleText("Fix: " + fixes.get(0).label());
            button.setOnAction(e -> {
                if (fixes.size() == 1) {
                    problems.applyFix(r, fixes.get(0));
                } else {
                    ContextMenu menu = new ContextMenu();
                    for (Diagnostic.Fix f : fixes) {
                        menu.getItems().add(item(f.label(), () -> problems.applyFix(r, f)));
                    }
                    menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            });
            setGraphic(button);
        }
    }

    private ContextMenu contextMenu(List<MenuItem> extraMenuItems) {
        MenuItem go = item("Go to", () -> selected().ifPresent(problems::go));
        Menu fix = new Menu("Apply fix");
        MenuItem copyMessage = item("Copy message", () -> selected().ifPresent(r -> copy(ProblemExport.line(r))));
        copyMessage.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        MenuItem copyAll = item("Copy all rows", () -> copy(ProblemExport.tsv(shownRows(), projectDirectory.get(), pluginName)));
        MenuItem copyCode = item("Copy code", () -> selected().ifPresent(r -> copy(r.diagnostic().code())));
        MenuItem selectObject = item("Select object", () -> selected().flatMap(ProblemRow::object)
            .ifPresent(o -> {
                selected().ifPresent(problems::go);
                selectInDisplayed.accept(Focus.of(o));
            }));
        javafx.scene.control.CheckMenuItem codeColumnItem = new javafx.scene.control.CheckMenuItem("Show the Code column");
        codeColumnItem.selectedProperty().bindBidirectional(config.showCode());
        MenuItem exportItem = item("Export…", this::export);
        ContextMenu menu = new ContextMenu(go, fix, new SeparatorMenuItem(), copyMessage, copyAll, copyCode, selectObject,
            new SeparatorMenuItem(), codeColumnItem, exportItem);
        if (!extraMenuItems.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
            menu.getItems().addAll(extraMenuItems);
        }
        menu.setOnShowing(e -> {
            Optional<ProblemRow> r = selected();
            go.setDisable(r.isEmpty());
            copyMessage.setDisable(r.isEmpty());
            copyCode.setDisable(r.map(x -> x.diagnostic().code().isEmpty()).orElse(true));
            selectObject.setDisable(r.flatMap(ProblemRow::object).isEmpty());
            fix.getItems().clear();
            r.ifPresent(row -> row.diagnostic().fixes().forEach(f ->
                fix.getItems().add(item(f.label(), () -> problems.applyFix(row, f)))));
            fix.setDisable(fix.getItems().isEmpty());
            copyAll.setDisable(filtered.isEmpty());
            exportItem.setDisable(filtered.isEmpty());
        });
        return menu;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    private void indexChanged(TextId changed) {
        if (allFilesCheck.isSelected() || changed == null || displayedFile.get().map(changed::equals).orElse(false)) {
            refresh();
        }
    }

    /** Reads the rows again (the displayed file changed, or its scope); keeps the selected problem if it is still there. */
    public void refresh() {
        Optional<ProblemRow> previous = selected();
        ProblemIndex index = problems.index();
        List<ProblemRow> source = allFilesCheck.isSelected() ? index.rows()
            : displayedFile.get().map(index::rows).orElse(List.of());
        rows.setAll(source.stream().filter(r -> config.category().test(r.diagnostic())).toList());
        refreshChoices();
        previous.flatMap(p -> rows.stream().filter(r -> sameProblem(r, p)).findFirst())
            .ifPresent(r -> table.getSelectionModel().select(r));
        placeholder.setText(allFilesCheck.isSelected() || displayedFile.get().isPresent()
            ? (allFilesCheck.isSelected() ? config.emptyProject() : config.emptyFile())
            : "Open a file to see its problems, or check \"All files\".");
    }

    private static boolean sameProblem(ProblemRow a, ProblemRow b) {
        return a.file().equals(b.file()) && a.line() == b.line()
            && Objects.equals(a.diagnostic().grammarId(), b.diagnostic().grammarId())
            && a.diagnostic().message().equals(b.diagnostic().message());
    }

    private void refreshChoices() {
        DiagnosticFilter.Choice previous = pluginChoice.getSelectionModel().getSelectedItem();
        List<Diagnostic> diagnostics = rows.stream().map(ProblemRow::diagnostic).toList();
        var choices = DiagnosticFilter.choices(diagnostics,
            fr.cnrs.lacito.matchbox.PluginManager.getAvailableGrammars());
        updatingChoices = true;
        try {
            pluginChoice.getItems().setAll(choices);
            pluginChoice.getSelectionModel().select(DiagnosticFilter.keep(choices, previous == null ? null : previous.grammarId()));
        } finally {
            updatingChoices = false;
        }
        applyFilter();
    }

    private void applyFilter() {
        DiagnosticFilter.Choice choice = pluginChoice.getSelectionModel().getSelectedItem();
        boolean warnings = showWarningsCheck.isSelected();
        filtered.setPredicate(r -> (warnings || r.diagnostic().isError()) && (choice == null || choice.accepts(r.diagnostic())));
        List<Diagnostic> all = rows.stream().map(ProblemRow::diagnostic).toList();
        boolean isFiltered = (choice != null && choice.grammarId() != null)
            || (!warnings && all.stream().anyMatch(Diagnostic::isWarning));
        int files = allFilesCheck.isSelected() ? (int) rows.stream().map(ProblemRow::file).distinct().count() : 0;
        tab.setText(DiagnosticFilter.tabTitle(config.title(), filtered.size(), all, isFiltered, files));
        updatePosition();
    }

    private void updatePosition() {
        int n = table.getItems().size();
        int i = table.getSelectionModel().getSelectedIndex();
        position.setText(n == 0 ? "" : (i < 0 ? "–" : String.valueOf(i + 1)) + " / " + n);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /** The selected row. */
    public Optional<ProblemRow> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    /** Selects the previous / next row of the table (wrapping) and goes to it. */
    public void step(boolean forward) {
        int n = table.getItems().size();
        if (n == 0) {
            status.info("No problem in the list");
            return;
        }
        int i = table.getSelectionModel().getSelectedIndex();
        int next = i < 0 ? (forward ? 0 : n - 1) : Math.floorMod(i + (forward ? 1 : -1), n);
        table.getSelectionModel().clearAndSelect(next);
        table.scrollTo(next);
        problems.go(table.getItems().get(next));
    }

    /** The rows shown (filters, sort and scope applied). */
    public List<ProblemRow> shownRows() {
        return new ArrayList<>(table.getItems());
    }

    private void export() {
        List<ProblemRow> shown = shownRows();
        if (shown.isEmpty()) return;
        String name = "matchbox-" + config.title().toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + ".csv";
        Optional<Path> target = exportChooser.apply(name);
        if (target.isEmpty()) return;
        try {
            Files.writeString(target.get(), ProblemExport.csv(shown, projectDirectory.get(), pluginName), StandardCharsets.UTF_8);
            status.info("Exported " + shown.size() + " problem" + (shown.size() == 1 ? "" : "s") + " to " + target.get());
        } catch (IOException e) {
            status.warn("Could not write " + target.get() + ": " + e.getMessage());
        }
    }

    /** How the target file of Export… is chosen (a file chooser; replaced in tests). */
    public void setExportChooser(Function<String, Optional<Path>> chooser) {
        this.exportChooser = Objects.requireNonNull(chooser);
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String accessibleText(ProblemRow r) {
        Diagnostic d = r.diagnostic();
        return DiagnosticFilter.severityLabel(d) + ", " + r.fileName() + " line " + r.line() + ", "
            + pluginName.apply(d.grammarId()) + ": " + d.message()
            + (d.fixes().isEmpty() ? "" : "; fix available: " + d.fixes().get(0).label());
    }

    public TableView<ProblemRow> table() {
        return table;
    }

    public Tab tab() {
        return tab;
    }

    /** Focuses the table (Show parsing errors / semantic issues). */
    public void focusTable() {
        table.requestFocus();
        if (table.getSelectionModel().isEmpty() && !table.getItems().isEmpty()) {
            table.getSelectionModel().select(0);
        }
    }

    /** A boolean property kept in sync with a getter and a setter (a setting). */
    public static BooleanProperty setting(boolean initial, Consumer<Boolean> write) {
        SimpleBooleanProperty p = new SimpleBooleanProperty(initial);
        p.addListener((obs, o, n) -> write.accept(n));
        return p;
    }
}
