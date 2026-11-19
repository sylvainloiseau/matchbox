package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.MetadataEntry;
import fr.cnrs.lacito.matchbox.corpus.ProjectPaths;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataValueEditor;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.ValueCell;
import fr.cnrs.lacito.matchbox.gui.edit.FieldChange;
import fr.cnrs.lacito.matchbox.gui.edit.ViewEditService;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.BulkEdit;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.CellEdit;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.CellProblems;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.ColumnFilter;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.Completion;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.FilterHeader;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.InheritedValues;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.KeyRename;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.MetadataColumns;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.MetadataFilters;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.MetadataIndex;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.MetadataRow;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.NaturalOrder;
import fr.cnrs.lacito.matchbox.gui.mode.metadata.TableExport;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemRow;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.MetadataModeSettingsData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Metadata mode: every metadata block of the corpus ({@link HasMetadata}, whatever plugin and
 * notation made it) in one table, one row per block, one column per key. Each column has a filter;
 * a click on a title sorts; key columns are reordered by dragging their title or from the keyboard.
 * Cells are edited: an edit is a text edit in the block's notation (the grammar's
 * {@code ObjectEditor}), then the file is parsed again. See {@code .claude/plans/METADATA_MODE_PLAN.md}.
 */
public final class MetadataMode extends AbstractMode {

    public static final String ID = "metadata";

    /** Column ids of the fixed columns (keys cannot contain a NUL character). */
    static final String FILE = MetadataFilters.FILE;
    static final String TYPE = "\0type";
    static final String OBJECT = MetadataFilters.OBJECT;
    static final String LINE = "\0line";
    private static final List<String> FIXED = List.of(FILE, TYPE, OBJECT, LINE);
    /** More cells than this, or more than one file: a batch is previewed before it is applied. */
    static final int PREVIEW_CELLS = 20;

    /** A row of the tree: a block, or a group of blocks. */
    public sealed interface Item permits RowItem, GroupItem {}

    public record RowItem(MetadataRow row) implements Item {}

    public record GroupItem(String id, String title, int count) implements Item {}

    /** The questions the mode asks (replaced in the GUI tests). */
    public interface Dialogs {
        /** A text (Set value…, Rename key…); empty to cancel. */
        Optional<String> ask(String title, String header, String initial);

        /** A confirmation of a change; false to cancel. */
        boolean confirm(String title, String header, String content);

        /** Tells why nothing was changed. */
        void inform(String title, String header, String content);

        /** A file to export to; empty to cancel. */
        Optional<Path> exportFile(Optional<Path> directory);
    }

    private final StackPane root = new StackPane();
    private final TreeTableView<Item> table = new TreeTableView<>();
    private final TreeItem<Item> treeRoot = new TreeItem<>();
    private final Label empty = new Label();
    private final TextField search = new TextField();
    private final CheckBox withoutBlock = new CheckBox();
    private final CheckBox inherited = new CheckBox("Inherited values");
    private final ChoiceBox<String> group = new ChoiceBox<>();
    private final MenuButton columnsButton = new MenuButton("Columns");
    private final Label counts = new Label();
    private final HBox toolbar;
    private final Menu menu;
    private final ChoiceBox<String> typeChoice = new ChoiceBox<>();

    private final MetadataIndex index;
    private MetadataColumns columns = MetadataColumns.of(List.of());
    private MetadataFilters filters = MetadataFilters.NONE;
    private final Map<String, TreeTableColumn<Item, String>> keyColumns = new LinkedHashMap<>();
    private final Map<String, FilterHeader> headers = new HashMap<>();
    private final Map<String, TreeTableColumn<Item, String>> fixedColumns = new LinkedHashMap<>();
    private final Map<MetadataRow.Key, CellProblems> problemCache = new HashMap<>();
    private final Set<String> collapsedGroups = new HashSet<>();
    /** The key columns in the order the user gave (moved columns), for this project. */
    private List<String> userOrder = new ArrayList<>();
    private Set<String> hidden = new LinkedHashSet<>();
    /** The cell to select after the next rebuild (after an edit: the row is a new object). */
    private Optional<PendingCell> pending = Optional.empty();
    private boolean rebuilding;
    private Dialogs dialogs;

    private record PendingCell(MetadataRow.Key row, String column) {}

    public MetadataMode(Workbench workbench) {
        super(workbench);
        index = new MetadataIndex(text -> workbench.edits().canAddTextProperty(text));
        dialogs = defaultDialogs();
        table.setId("metadataTable");
        search.setId("metadataSearch");
        withoutBlock.setId("metadataWithout");
        inherited.setId("metadataInherited");
        group.setId("metadataGroup");
        columnsButton.setId("metadataColumns");
        counts.setId("metadataCounts");
        empty.setId("metadataEmpty");

        MetadataModeSettingsData s = settings();
        filters = filters.withShowWithoutMetadata(s.showWithoutMetadata());
        withoutBlock.setSelected(s.showWithoutMetadata());
        inherited.setSelected(s.showInherited());
        group.getItems().addAll("No groups", "Group by file", "Group by type");
        group.setValue(switch (s.groupBy()) {
            case "FILE" -> "Group by file";
            case "TYPE" -> "Group by type";
            default -> "No groups";
        });

        search.setPromptText("Search all cells…");
        search.setPrefColumnCount(14);
        search.setTooltip(new Tooltip("Rows with a cell containing this text (⌘F)"));
        search.textProperty().addListener((obs, o, n) -> {
            filters = filters.withSearch(ColumnFilter.NONE.withText(n));
            rebuild();
        });
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) search.clear();
        });
        withoutBlock.setTooltip(new Tooltip("Also list the objects that could have a metadata block but have none:"
            + " typing a value in one of their cells creates the block"));
        withoutBlock.selectedProperty().addListener((obs, o, n) -> {
            filters = filters.withShowWithoutMetadata(n);
            saveSettings(settings().withShowWithoutMetadata(n));
            rebuild();
        });
        inherited.setTooltip(new Tooltip("Show, greyed, the values inherited from sections, the header or a previous IGT"));
        inherited.selectedProperty().addListener((obs, o, n) -> {
            saveSettings(settings().withShowInherited(n));
            rebuild();
        });
        group.setTooltip(new Tooltip("Group the blocks by file or by type"));
        group.valueProperty().addListener((obs, o, n) -> {
            saveSettings(settings().withGroupBy(groupBy()));
            rebuild();
        });
        columnsButton.setTooltip(new Tooltip("Show or hide key columns, order them"));
        columnsButton.setOnShowing(e -> columnsButton.getItems().setAll(columnsMenuItems()));
        counts.setStyle("-fx-text-fill: #666666;");
        toolbar = new HBox(8, search, withoutBlock, inherited, group, columnsButton, counts);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setUpTable();
        empty.setWrapText(true);
        empty.setMaxWidth(520);
        empty.setStyle("-fx-text-fill: #888888; -fx-font-size: 1.1em;");
        empty.setMouseTransparent(true);
        root.getChildren().addAll(table, empty);
        menu = buildMenu();
        workbench.problems().addChecksListener(() -> {
            problemCache.clear();
            table.refresh();
        });
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    @Override
    public Node root() {
        return root;
    }

    @Override
    public Optional<Menu> menu() {
        return Optional.of(menu);
    }

    @Override
    public Optional<Node> toolBarItems() {
        return Optional.of(toolbar);
    }

    @Override
    protected void onProjectChanged() {
        index.clear();
        for (Text t : workbench.session().corpus().texts()) index.update(t.id(), Optional.of(t));
        MetadataModeSettingsData.ProjectColumns c = settings().columnsOf(projectKey());
        userOrder = new ArrayList<>(c.order());
        hidden = new LinkedHashSet<>(c.hidden());
        collapsedGroups.clear();
        keyColumns.clear();
        table.getSortOrder().clear();
        rebuild();
        restoreSort(c);
    }

    @Override
    protected void onTextsChanged(Set<TextId> texts) {
        for (TextId id : texts) index.update(id, workbench.session().corpus().text(id));
        problemCache.clear();
        rebuild();
    }

    @Override
    protected void onActivated() {
        table.requestFocus();
    }

    /** Selects the row of an object with metadata, or the innermost one around a place. */
    @Override
    public boolean reveal(Focus focus) {
        Optional<MetadataRow> row = rowAt(focus);
        if (row.isEmpty()) return false;
        if (!filters.predicate(this::fileOf, this::displayed, shownKeys()).test(row.get())) {
            clearFilters();
        }
        select(row.get().key(), FILE);
        return find(row.get().key()).isPresent();
    }

    private Optional<MetadataRow> rowAt(Focus focus) {
        List<MetadataRow> rows = index.of(focus.text());
        if (focus.object().isPresent() && focus.object().get() instanceof HasMetadata m) {
            for (MetadataRow r : rows) if (r.owner().map(o -> o == m || same(o, m)).orElse(false)) return Optional.of(r);
        }
        return rows.stream().filter(r -> r.owner().isPresent())
            .filter(r -> {
                TextCoordinate c = r.owner().get().getTextCoordinate();
                return c.startIndex() <= focus.start() && focus.start() <= Math.max(c.startIndex(), c.stopIndex());
            })
            .min(Comparator.comparingInt(r -> r.owner().get().getTextCoordinate().length()))
            .or(() -> rows.stream().filter(MetadataRow::isHeaderCandidate).findFirst());
    }

    private static boolean same(CorpusObject a, CorpusObject b) {
        return a.getKind().equals(b.getKind()) && a.getTextCoordinate().equals(b.getTextCoordinate());
    }

    /** Replaces the dialogs (GUI tests). */
    public void setDialogs(Dialogs dialogs) {
        this.dialogs = dialogs;
    }

    public TreeTableView<Item> table() {
        return table;
    }

    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------

    private void setUpTable() {
        table.setRoot(treeRoot);
        table.setShowRoot(false);
        table.setEditable(true);
        table.setTableMenuButtonVisible(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setPlaceholder(new Label(""));
        fixedColumns.put(FILE, fixedColumn(FILE, "File", 180, item -> switch (item) {
            case RowItem r -> fileOf(r.row());
            case GroupItem g -> g.title() + " — " + g.count() + (g.count() == 1 ? " block" : " blocks");
        }));
        fixedColumns.put(TYPE, fixedColumn(TYPE, "Type", 90, item -> item instanceof RowItem r ? r.row().type() : ""));
        fixedColumns.put(OBJECT, fixedColumn(OBJECT, "Object", 140, item -> item instanceof RowItem r ? r.row().label() : ""));
        fixedColumns.put(LINE, fixedColumn(LINE, "Line", 50, item -> item instanceof RowItem r ? String.valueOf(r.row().line()) : ""));
        table.getColumns().setAll(fixedColumns.values());

        typeChoice.setTooltip(new Tooltip("Show one type of block"));
        typeChoice.valueProperty().addListener((obs, o, n) -> {
            if (rebuilding) return;
            filters = filters.withType(n == null || n.startsWith("All") ? Optional.empty() : Optional.of(n));
            rebuild();
        });

        // the fixed columns stay first; key columns moved by the user are remembered
        table.getColumns().addListener((ListChangeListener<TreeTableColumn<Item, ?>>) c -> {
            if (rebuilding) return;
            Platform.runLater(this::columnsMoved);
        });
        table.getSortOrder().addListener((ListChangeListener<TreeTableColumn<Item, ?>>) c -> {
            if (!rebuilding) saveColumns();
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateFocus());
        table.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        // a filter: the table's own key handling would take the character first
        table.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (table.getEditingCell() != null || e.getTarget() instanceof TextField
                || e.getCharacter().isEmpty() || e.isShortcutDown() || e.isControlDown()) return;
            char ch = e.getCharacter().charAt(0);
            if (Character.isISOControl(ch)) return;
            focusedKeyCell().ifPresent(cell -> {
                startEdit(cell, e.getCharacter());
                e.consume();
            });
        });
        ContextMenu context = new ContextMenu();
        context.setOnShowing(e -> context.getItems().setAll(contextMenuItems()));
        table.setContextMenu(context);
    }

    private TreeTableColumn<Item, String> fixedColumn(String id, String title, double width,
                                                     java.util.function.Function<Item, String> value) {
        TreeTableColumn<Item, String> column = new TreeTableColumn<>();
        column.setUserData(id);
        column.setReorderable(false);
        column.setEditable(false);
        column.setPrefWidth(width);
        column.setCellValueFactory(p -> new SimpleStringProperty(value.apply(p.getValue().getValue())));
        column.setComparator(NaturalOrder.INSTANCE);
        column.setCellFactory(c -> new FixedCell(id));
        if (id.equals(TYPE)) {
            Label t = new Label(title);
            t.setStyle("-fx-font-weight: bold;");
            VBox header = new VBox(2, t, typeChoice);
            typeChoice.addEventHandler(javafx.scene.input.MouseEvent.ANY, javafx.event.Event::consume);
            column.setGraphic(header);
        } else if (id.equals(LINE)) {
            Label t = new Label(title);
            t.setStyle("-fx-font-weight: bold;");
            column.setGraphic(t);
        } else {
            FilterHeader header = new FilterHeader(id, f -> {
                filters = filters.withColumn(id, f);
                rebuild();
            });
            header.setTitle(title, id.equals(FILE) ? "The file, relative to the project directory" : "The object holding the block");
            headers.put(id, header);
            column.setGraphic(header);
        }
        column.widthProperty().addListener((obs, o, n) -> {
            if (!rebuilding) saveColumns();
        });
        return column;
    }

    private TreeTableColumn<Item, String> keyColumn(String key) {
        TreeTableColumn<Item, String> column = new TreeTableColumn<>();
        column.setUserData(key);
        column.setPrefWidth(settings().columnsOf(projectKey()).widths().getOrDefault(key, 120.0));
        column.setCellValueFactory(p -> new SimpleStringProperty(
            p.getValue().getValue() instanceof RowItem r ? displayed(r.row(), key).orElse("") : ""));
        column.setComparator(EMPTY_LAST);
        column.setCellFactory(c -> new KeyCell(key));
        column.setEditable(true);
        FilterHeader header = new FilterHeader(key, f -> {
            filters = filters.withColumn(key, f);
            rebuild();
        });
        headers.put(key, header);
        column.setGraphic(header);
        column.widthProperty().addListener((obs, o, n) -> {
            if (!rebuilding) saveColumns();
        });
        return column;
    }

    private static final Comparator<String> EMPTY_LAST = (a, b) -> {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty() ? 0 : a.isEmpty() ? 1 : -1;
        return NaturalOrder.INSTANCE.compare(a, b);
    };

    /** The value shown in a cell: the own value, else the inherited one while they are shown. */
    private Optional<String> displayed(MetadataRow row, String key) {
        Optional<String> own = row.value(key);
        if (own.isPresent() || !inherited.isSelected()) return own;
        return inheritedOf(row, key).map(InheritedValues.Inherited::value);
    }

    private Optional<InheritedValues.Inherited> inheritedOf(MetadataRow row, String key) {
        return workbench.session().corpus().text(row.text()).flatMap(t -> InheritedValues.of(t, row, key));
    }

    private String fileOf(MetadataRow row) {
        Project p = workbench.session().project();
        Optional<Path> base = p == null ? Optional.empty() : p.directory();
        return ProjectPaths.display(row.text().path(), base);
    }

    private List<String> shownKeys() {
        return keyColumns.keySet().stream().filter(k -> !hidden.contains(k)).toList();
    }

    /** Rebuilds the columns and the rows from the index (selection, sort and scroll kept). */
    void rebuild() {
        if (rebuilding) return;
        Optional<PendingCell> keep = pending.isPresent() ? pending : selectedCell();
        pending = Optional.empty();
        rebuilding = true;
        try {
            List<MetadataRow> rows = index.all();
            columns = MetadataColumns.of(rows);
            syncColumns();
            syncTypeChoice(rows);
            var predicate = filters.predicate(this::fileOf, this::displayed, shownKeys());
            List<MetadataRow> shown = rows.stream().filter(predicate).toList();
            List<TreeItem<Item>> items = new ArrayList<>();
            String by = groupBy();
            if (by.equals("NONE")) {
                for (MetadataRow r : shown) items.add(new TreeItem<>(new RowItem(r)));
            } else {
                Map<String, List<MetadataRow>> groups = new LinkedHashMap<>();
                for (MetadataRow r : shown) groups.computeIfAbsent(by.equals("FILE") ? fileOf(r) : r.type(), k -> new ArrayList<>()).add(r);
                groups.forEach((title, members) -> {
                    String id = by + ":" + title;
                    TreeItem<Item> g = new TreeItem<>(new GroupItem(id, title, members.size()));
                    for (MetadataRow r : members) g.getChildren().add(new TreeItem<>(new RowItem(r)));
                    g.setExpanded(!collapsedGroups.contains(id));
                    g.expandedProperty().addListener((obs, o, n) -> {
                        if (n) collapsedGroups.remove(id); else collapsedGroups.add(id);
                    });
                    items.add(g);
                });
            }
            treeRoot.getChildren().setAll(items);
            treeRoot.setExpanded(true);
            if (!table.getSortOrder().isEmpty()) table.sort();
            long blocks = rows.stream().filter(r -> !r.withoutBlock()).count();
            long without = rows.size() - blocks;
            withoutBlock.setText("Objects without metadata (" + without + ")");
            counts.setText(shown.size() == rows.size() || rows.isEmpty()
                ? blocks + (blocks == 1 ? " block" : " blocks") + " · " + columns.keys().size() + (columns.keys().size() == 1 ? " key" : " keys")
                : shown.size() + " of " + rows.size() + " rows · " + columns.keys().size() + " keys");
            counts.setTooltip(new Tooltip(blocks + " metadata blocks, " + without + " objects without block, "
                + columns.keys().size() + " different keys"));
            if (rows.isEmpty()) {
                empty.setText("No metadata in the project. Write a YAML block (--- key: value ---) at the start of a file"
                    + " or after a heading, or {key=\"value\"} after ::: in an IGT.");
            } else if (shown.isEmpty()) {
                empty.setText("No block matches the filters — Metadata › Clear filters (⇧⌘F)");
            }
            empty.setVisible(shown.isEmpty());
        } finally {
            rebuilding = false;
        }
        keep.ifPresent(c -> select(c.row(), c.column()));
    }

    private String groupBy() {
        String g = group.getValue();
        return g == null || g.equals("No groups") ? "NONE" : g.equals("Group by file") ? "FILE" : "TYPE";
    }

    private void syncTypeChoice(List<MetadataRow> rows) {
        List<String> types = new ArrayList<>(List.of("All types"));
        rows.stream().map(MetadataRow::type).distinct().sorted(NaturalOrder.INSTANCE).forEach(types::add);
        String current = typeChoice.getValue();
        if (!typeChoice.getItems().equals(types)) typeChoice.getItems().setAll(types);
        typeChoice.setValue(current != null && types.contains(current) ? current : "All types");
    }

    /** Key columns: one per key of the union, in order, hidden ones left out; the fixed ones first. */
    private void syncColumns() {
        MetadataModeSettingsData s = settings();
        List<String> ordered = columns.ordered(MetadataColumns.Order.valueOf(s.columnOrder()), userOrder);
        keyColumns.keySet().retainAll(new HashSet<>(ordered));
        for (String key : ordered) keyColumns.computeIfAbsent(key, this::keyColumn);
        for (String key : ordered) {
            FilterHeader h = headers.get(key);
            List<String> variants = columns.caseVariants(key);
            String n = String.valueOf(columns.count(key));
            h.setTitle((variants.isEmpty() ? "" : "⚠ ") + key + " · " + n,
                columns.count(key) + " blocks have " + key
                    + (variants.isEmpty() ? "" : "; also written " + String.join(", ", variants) + " (another column)"));
        }
        List<TreeTableColumn<Item, ?>> wanted = new ArrayList<>(fixedColumns.values());
        for (String key : ordered) if (!hidden.contains(key)) wanted.add(keyColumns.get(key));
        if (!table.getColumns().equals(wanted)) {
            List<TreeTableColumn<Item, ?>> sort = new ArrayList<>(table.getSortOrder());
            table.getColumns().setAll(wanted);
            sort.retainAll(wanted);
            table.getSortOrder().setAll(sort);
        }
    }

    /** After a drag of a column title: the fixed columns back in front, the new order remembered. */
    private void columnsMoved() {
        List<TreeTableColumn<Item, ?>> current = new ArrayList<>(table.getColumns());
        List<TreeTableColumn<Item, ?>> fixed = new ArrayList<>(fixedColumns.values());
        List<TreeTableColumn<Item, ?>> keys = current.stream().filter(c -> !FIXED.contains((String) c.getUserData())).toList();
        List<TreeTableColumn<Item, ?>> wanted = new ArrayList<>(fixed);
        wanted.addAll(keys);
        if (!current.equals(wanted)) {
            rebuilding = true;
            try {
                table.getColumns().setAll(wanted);
            } finally {
                rebuilding = false;
            }
        }
        List<String> order = keys.stream().map(c -> (String) c.getUserData()).toList();
        if (!order.equals(columns.ordered(MetadataColumns.Order.valueOf(settings().columnOrder()), userOrder).stream()
                .filter(k -> !hidden.contains(k)).toList())) {
            userOrder = new ArrayList<>(order);
            saveColumns();
        }
    }

    /** Moves the focused key column by one place (keyboard alternative to dragging). */
    void moveColumn(int by) {
        Optional<String> key = focusedColumnId().filter(id -> !FIXED.contains(id));
        if (key.isEmpty()) {
            workbench.status().info("Select a cell of a key column to move it");
            return;
        }
        List<String> order = new ArrayList<>(shownKeys());
        int i = order.indexOf(key.get());
        int j = i + by;
        if (i < 0 || j < 0 || j >= order.size()) return;
        order.remove(i);
        order.add(j, key.get());
        userOrder = order;
        saveColumns();
        rebuild();
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    private Optional<PendingCell> selectedCell() {
        TreeTablePosition<Item, ?> pos = table.getFocusModel().getFocusedCell();
        if (pos == null || pos.getTreeItem() == null || !(pos.getTreeItem().getValue() instanceof RowItem r)) return Optional.empty();
        String column = pos.getTableColumn() == null ? FILE : (String) pos.getTableColumn().getUserData();
        return Optional.of(new PendingCell(r.row().key(), column));
    }

    private Optional<TreeItem<Item>> find(MetadataRow.Key key) {
        for (TreeItem<Item> item : treeRoot.getChildren()) {
            if (item.getValue() instanceof RowItem r && r.row().key().equals(key)) return Optional.of(item);
            for (TreeItem<Item> child : item.getChildren()) {
                if (child.getValue() instanceof RowItem r && r.row().key().equals(key)) return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /** Selects a cell (the row's File cell if the column is gone), expanding its group, and scrolls to it. */
    void select(MetadataRow.Key key, String columnId) {
        find(key).ifPresent(item -> {
            if (item.getParent() != null && item.getParent() != treeRoot) item.getParent().setExpanded(true);
            int row = table.getRow(item);
            TreeTableColumn<Item, ?> column = table.getColumns().stream()
                .filter(c -> columnId.equals(c.getUserData())).findFirst().orElse(fixedColumns.get(FILE));
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(row, column);
            table.getFocusModel().focus(row, column);
            table.scrollTo(Math.max(0, row - 3));
        });
    }

    private Optional<MetadataRow> focusedRow() {
        TreeTablePosition<Item, ?> pos = table.getFocusModel().getFocusedCell();
        if (pos == null || pos.getTreeItem() == null) return Optional.empty();
        return pos.getTreeItem().getValue() instanceof RowItem r ? Optional.of(r.row()) : Optional.empty();
    }

    private Optional<String> focusedColumnId() {
        TreeTablePosition<Item, ?> pos = table.getFocusModel().getFocusedCell();
        if (pos == null || pos.getTableColumn() == null) return Optional.empty();
        return Optional.of((String) pos.getTableColumn().getUserData());
    }

    private Optional<TreeTablePosition<Item, ?>> focusedKeyCell() {
        TreeTablePosition<Item, ?> pos = table.getFocusModel().getFocusedCell();
        if (pos == null || pos.getTableColumn() == null || pos.getTreeItem() == null) return Optional.empty();
        if (FIXED.contains((String) pos.getTableColumn().getUserData())) return Optional.empty();
        if (!(pos.getTreeItem().getValue() instanceof RowItem)) return Optional.empty();
        return Optional.of(pos);
    }

    /** The selected cells of key columns, in the order of the rows then of the columns shown. */
    List<BulkEdit.Cell> selectedKeyCells() {
        List<BulkEdit.Cell> cells = new ArrayList<>();
        List<TreeTablePosition<Item, ?>> positions = new ArrayList<>(table.getSelectionModel().getSelectedCells());
        positions.sort(Comparator.<TreeTablePosition<Item, ?>>comparingInt(TreeTablePosition::getRow)
            .thenComparingInt(p -> table.getVisibleLeafIndex(p.getTableColumn())));
        for (TreeTablePosition<Item, ?> p : positions) {
            if (p.getTableColumn() == null || p.getTreeItem() == null) continue;
            String id = (String) p.getTableColumn().getUserData();
            if (FIXED.contains(id) || !(p.getTreeItem().getValue() instanceof RowItem r)) continue;
            cells.add(new BulkEdit.Cell(r.row(), id));
        }
        return cells;
    }

    private void updateFocus() {
        setFocus(focusedRow().map(r -> r.owner().<Focus>map(Focus::of).orElse(Focus.at(r.text(), 0))));
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    private void keyPressed(KeyEvent e) {
        if (table.getEditingCell() != null) return;
        if ((e.getCode() == KeyCode.F2 || e.getCode() == KeyCode.ENTER) && !e.isShortcutDown()) {
            focusedKeyCell().ifPresent(cell -> {
                startEdit(cell, null);
                e.consume();
            });
        } else if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && !e.isShortcutDown()) {
            removeSelected();
            e.consume();
        } else if (e.getCode() == KeyCode.LEFT && e.isAltDown() && e.isShiftDown()) {
            moveColumn(-1);
            e.consume();
        } else if (e.getCode() == KeyCode.RIGHT && e.isAltDown() && e.isShiftDown()) {
            moveColumn(1);
            e.consume();
        }
    }

    /** Starts editing a key cell, with a first character typed or the current value. */
    private void startEdit(TreeTablePosition<Item, ?> pos, String typed) {
        initialText = typed;
        @SuppressWarnings("unchecked")
        TreeTableColumn<Item, String> column = (TreeTableColumn<Item, String>) pos.getTableColumn();
        table.edit(pos.getRow(), column);
    }

    /** The first character typed that started the edit, if any (consumed by the cell). */
    private String initialText;

    // ------------------------------------------------------------------
    // Edits
    // ------------------------------------------------------------------

    /**
     * Writes the new text of a cell: set, add (creating the block), remove (empty text), or a header
     * for a file without one; refusals and read-only cells are said on the status line.
     */
    public EditOutcome write(MetadataRow row, String key, String text, boolean emptyValue) {
        CellEdit edit = CellEdit.of(row, key, text, emptyValue);
        ViewEditService edits = workbench.edits();
        EditOutcome outcome = switch (edit) {
            case CellEdit.Set s -> edits.setField(s.owner(), s.owner().metadataFieldName(s.key()), s.value(), s.description());
            case CellEdit.Add a -> edits.addField(a.owner(), a.owner().metadataFieldName(a.key()), a.value(), a.description());
            case CellEdit.Remove r -> edits.removeField(r.owner(), r.owner().metadataFieldName(r.key()), r.description());
            case CellEdit.AddHeader h -> edits.addTextProperty(h.text(), h.key(), h.value(), h.description());
            case CellEdit.ReadOnly ro -> new EditOutcome.NotEditable(ro.reason());
            case CellEdit.None n -> new EditOutcome.Applied();
        };
        if (outcome.isApplied()) {
            if (!(edit instanceof CellEdit.None)) {
                pending = Optional.of(new PendingCell(row.key(), key));
                workbench.status().info(edit.description() + " (" + row.text().displayName() + ")");
            }
        } else {
            workbench.status().warn("Not changed: " + outcome.message());
        }
        return outcome;
    }

    private void removeSelected() {
        List<BulkEdit.Cell> cells = selectedKeyCells().stream().filter(c -> c.row().value(c.key()).isPresent()).toList();
        if (cells.isEmpty()) return;
        if (cells.size() == 1) {
            write(cells.get(0).row(), cells.get(0).key(), "", false);
            return;
        }
        applyBulk(BulkEdit.set(cells, ""), "Remove " + describeKeys(cells), "Removes " + describeKeys(cells));
    }

    public void fillDown() {
        List<BulkEdit.Cell> cells = selectedKeyCells();
        if (cells.size() < 2 || cells.stream().map(BulkEdit.Cell::key).distinct().count() > 1) {
            workbench.status().info("Fill down: select cells of one column, the first holding the value");
            return;
        }
        String key = cells.get(0).key();
        applyBulk(BulkEdit.fillDown(cells), "Set " + key, "Sets " + key);
    }

    public void setValue() {
        List<BulkEdit.Cell> cells = selectedKeyCells();
        if (cells.isEmpty()) {
            workbench.status().info("Select the cells to set");
            return;
        }
        String keys = describeKeys(cells);
        dialogs.ask("Set value", "Value of " + keys + " in " + cells.size() + (cells.size() == 1 ? " cell" : " cells"),
            cells.get(0).row().value(cells.get(0).key()).orElse(""))
            .ifPresent(v -> applyBulk(BulkEdit.set(cells, v), "Set " + keys, "Sets " + keys));
    }

    void paste() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isEmpty()) return;
        pasteText(text);
    }

    /** Pastes TSV at the focused cell: to the right in the shown key columns, below in the shown rows. */
    public void pasteText(String text) {
        Optional<TreeTablePosition<Item, ?>> pos = focusedKeyCell();
        if (pos.isEmpty()) {
            workbench.status().info("Select the key cell where to paste");
            return;
        }
        List<List<String>> grid = TableExport.parseTsv(text);
        List<MetadataRow> rows = shownRows();
        List<String> keys = shownKeys().stream().filter(k -> table.getColumns().contains(keyColumns.get(k))).toList();
        List<String> visibleKeys = table.getVisibleLeafColumns().stream().map(c -> (String) c.getUserData())
            .filter(id -> !FIXED.contains(id)).toList();
        MetadataRow row = ((RowItem) pos.get().getTreeItem().getValue()).row();
        Optional<BulkEdit> bulk = BulkEdit.paste(grid, rows, rows.indexOf(row), visibleKeys,
            visibleKeys.indexOf((String) pos.get().getTableColumn().getUserData()));
        if (bulk.isEmpty() || keys.isEmpty()) {
            workbench.status().warn("Not pasted: " + grid.size() + " × " + (grid.isEmpty() ? 0 : grid.get(0).size())
                + " cells do not fit from this cell");
            return;
        }
        if (bulk.get().changes().size() == 1 && bulk.get().problems().isEmpty()) {
            FieldChange c = bulk.get().changes().get(0);
            write(row, (String) pos.get().getTableColumn().getUserData(), grid.get(0).get(0), false);
            return;
        }
        applyBulk(bulk.get(), "Paste", "Pastes");
    }

    private List<MetadataRow> shownRows() {
        List<MetadataRow> rows = new ArrayList<>();
        for (int i = 0; i < table.getExpandedItemCount(); i++) {
            TreeItem<Item> item = table.getTreeItem(i);
            if (item != null && item.getValue() instanceof RowItem r) rows.add(r.row());
        }
        return rows;
    }

    private static String describeKeys(List<BulkEdit.Cell> cells) {
        List<String> keys = cells.stream().map(BulkEdit.Cell::key).distinct().toList();
        return keys.size() == 1 ? keys.get(0) : keys.size() + " keys";
    }

    /** A change of several cells: previewed when large, all or nothing, one undo entry. */
    void applyBulk(BulkEdit bulk, String description, String verb) {
        if (!bulk.problems().isEmpty()) {
            dialogs.inform("Not changed", "Nothing was changed: " + bulk.problems().size()
                + (bulk.problems().size() == 1 ? " cell cannot be" : " cells cannot be") + " changed", String.join("\n", bulk.problems()));
            return;
        }
        if (bulk.changes().isEmpty()) return;
        apply(bulk.changes(), description + " in " + bulk.cells() + (bulk.cells() == 1 ? " block" : " blocks"),
            bulk.preview(verb), bulk.files().size(), bulk.cells());
    }

    private void apply(List<FieldChange> changes, String description, String preview, int files, int cells) {
        ViewEditService edits = workbench.edits();
        List<String> problems = edits.checkFields(changes);
        if (!problems.isEmpty()) {
            dialogs.inform("Not changed", "Nothing was changed", String.join("\n", problems));
            return;
        }
        if ((files > 1 || cells > PREVIEW_CELLS) && !dialogs.confirm("Change several blocks", preview + "?",
                "One change, undone with Edit › Undo. Nothing is saved.")) {
            return;
        }
        var result = edits.setFields(changes, description);
        if (!result.applied()) {
            dialogs.inform("Not changed", "Nothing was changed", String.join("\n", result.problems()));
            return;
        }
        String text = description + " (" + result.files() + (result.files() == 1 ? " file)" : " files)");
        if (result.files() > 1) {
            workbench.status().info(text, "Save all", workbench::saveAll);
        } else {
            workbench.status().info(text);
        }
    }

    void renameKey() {
        Optional<String> key = focusedColumnId().filter(id -> !FIXED.contains(id));
        if (key.isEmpty()) {
            workbench.status().info("Select a cell of the key column to rename");
            return;
        }
        renameKey(key.get());
    }

    public void renameKey(String key) {
        dialogs.ask("Rename key", "New name of " + key + " in every block", key).ifPresent(newKey -> {
            String k = newKey.strip();
            if (k.isEmpty() || k.equals(key)) return;
            KeyRename rename = KeyRename.of(index.all(), key, k);
            if (rename.changes().isEmpty()) {
                dialogs.inform("Not renamed", "No block can be renamed", rename.preview(key, k));
                return;
            }
            List<String> problems = workbench.edits().checkFields(rename.changes());
            if (!problems.isEmpty()) {
                dialogs.inform("Not renamed", "Nothing was renamed", String.join("\n", problems));
                return;
            }
            if (!dialogs.confirm("Rename key", rename.preview(key, k) + "?", "One change, undone with Edit › Undo. Nothing is saved.")) {
                return;
            }
            var result = workbench.edits().setFields(rename.changes(), "Rename " + key + " to " + k);
            if (!result.applied()) {
                dialogs.inform("Not renamed", "Nothing was renamed", String.join("\n", result.problems()));
                return;
            }
            // the column keeps its place under its new name
            userOrder = userOrder.stream().map(x -> x.equals(key) ? k : x).toList();
            if (!userOrder.contains(k)) {
                List<String> order = new ArrayList<>(shownKeys());
                order.replaceAll(x -> x.equals(key) ? k : x);
                userOrder = order;
            }
            ColumnFilter f = filters.column(key);
            filters = filters.withColumn(key, ColumnFilter.NONE).withColumn(k, f);
            saveColumns();
            workbench.status().info("Renamed " + key + " to " + k + " in " + rename.changes().size() + " blocks", "Save all",
                workbench::saveAll);
        });
    }

    // ------------------------------------------------------------------
    // Navigation, copy, export, fixes
    // ------------------------------------------------------------------

    /** Shows the block (or the value of the focused key) in File Mode; Back returns here. */
    void showInEditor() {
        Optional<MetadataRow> row = focusedRow();
        if (row.isEmpty()) return;
        Optional<String> key = focusedColumnId().filter(id -> !FIXED.contains(id));
        Optional<TextCoordinate> span = key.flatMap(k -> row.get().block().flatMap(b -> b.entry(k)))
            .flatMap(MetadataEntry::valueSpan)
            .or(() -> row.get().block().flatMap(b -> b.span()))
            .or(() -> row.get().owner().map(CorpusObject::getTextCoordinate));
        Focus focus = span.map(Focus::of).orElse(Focus.at(row.get().text(), 0));
        workbench.navigator().showInEditor(focus);
    }

    void copy() {
        List<TreeTablePosition<Item, ?>> positions = new ArrayList<>(table.getSelectionModel().getSelectedCells());
        if (positions.isEmpty()) return;
        positions.sort(Comparator.<TreeTablePosition<Item, ?>>comparingInt(TreeTablePosition::getRow)
            .thenComparingInt(p -> table.getVisibleLeafIndex(p.getTableColumn())));
        List<List<String>> rows = new ArrayList<>();
        int lastRow = -1;
        for (TreeTablePosition<Item, ?> p : positions) {
            if (p.getRow() != lastRow) {
                rows.add(new ArrayList<>());
                lastRow = p.getRow();
            }
            Object v = p.getTableColumn() == null ? "" : p.getTableColumn().getCellData(p.getRow());
            rows.get(rows.size() - 1).add(v == null ? "" : v.toString());
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(TableExport.of(rows, TableExport.Format.TSV));
        Clipboard.getSystemClipboard().setContent(content);
        workbench.status().info("Copied " + positions.size() + (positions.size() == 1 ? " cell" : " cells"));
    }

    /** The shown table as text: the titles, then the shown rows (group rows left out). */
    public String exportText(TableExport.Format format) {
        List<TreeTableColumn<Item, ?>> visible = table.getVisibleLeafColumns();
        List<List<String>> rows = new ArrayList<>();
        rows.add(visible.stream().map(c -> {
            String id = (String) c.getUserData();
            return switch (id) {
                case FILE -> "File";
                case TYPE -> "Type";
                case OBJECT -> "Object";
                case LINE -> "Line";
                default -> id;
            };
        }).toList());
        for (int i = 0; i < table.getExpandedItemCount(); i++) {
            TreeItem<Item> item = table.getTreeItem(i);
            if (item == null || !(item.getValue() instanceof RowItem)) continue;
            int row = i;
            rows.add(visible.stream().map(c -> {
                Object v = c.getCellData(row);
                return v == null ? "" : v.toString();
            }).toList());
        }
        return TableExport.of(rows, format);
    }

    void export() {
        Project p = workbench.session().project();
        Optional<Path> file = dialogs.exportFile(p == null ? Optional.empty() : p.directory());
        if (file.isEmpty()) return;
        String name = file.get().getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        TableExport.Format format = name.endsWith(".tsv") || name.endsWith(".txt") ? TableExport.Format.TSV : TableExport.Format.CSV_COMMA;
        try {
            Files.writeString(file.get(), exportText(format), StandardCharsets.UTF_8);
            workbench.status().info("Exported " + (table.getExpandedItemCount()) + " rows to " + file.get().getFileName());
        } catch (IOException e) {
            fr.cnrs.lacito.matchbox.gui.ErrorReporter.report(workbench.window(), "Error exporting the metadata", e);
        }
    }

    private CellProblems problemsOf(MetadataRow row) {
        return problemCache.computeIfAbsent(row.key(), k -> workbench.session().corpus().text(row.text())
            .map(t -> CellProblems.of(row, workbench.problems().diagnosticsOf(t))).orElse(CellProblems.NONE));
    }

    /** Applies the first fix of a problem of the focused cell. */
    void applyFix() {
        Optional<MetadataRow> row = focusedRow();
        Optional<String> key = focusedColumnId().filter(id -> !FIXED.contains(id));
        if (row.isEmpty()) return;
        List<Diagnostic> problems = key.map(k -> problemsOf(row.get()).of(k)).orElse(problemsOf(row.get()).block());
        Optional<Diagnostic> withFix = problems.stream().filter(d -> !d.fixes().isEmpty()).findFirst();
        Optional<Text> text = workbench.session().corpus().text(row.get().text());
        if (withFix.isEmpty() || text.isEmpty()) {
            workbench.status().info("No fix here");
            return;
        }
        EditOutcome outcome = workbench.problems().applyFix(new ProblemRow(row.get().text(), withFix.get(), Optional.empty(),
            text.get().version()), withFix.get().fixes().get(0));
        if (!outcome.isApplied()) workbench.status().warn("Not changed: " + outcome.message());
    }

    void clearFilters() {
        rebuilding = true;
        try {
            headers.values().forEach(h -> h.set(ColumnFilter.NONE));
            search.clear();
            typeChoice.setValue("All types");
        } finally {
            rebuilding = false;
        }
        filters = filters.cleared();
        rebuild();
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    private Menu buildMenu() {
        Menu m = new Menu("Metadata");
        m.setOnShowing(e -> m.getItems().setAll(menuItems()));
        m.getItems().setAll(menuItems());
        return m;
    }

    private List<MenuItem> menuItems() {
        MenuItem show = item("Show in editor", this::showInEditor, shortcut(KeyCode.E));
        MenuItem edit = item("Edit cell", () -> focusedKeyCell().ifPresent(c -> startEdit(c, null)), new KeyCodeCombination(KeyCode.F2));
        MenuItem remove = item("Remove entry", this::removeSelected, null);
        MenuItem empty = item("Set empty value", () -> focusedRow().ifPresent(r -> focusedColumnId()
            .filter(id -> !FIXED.contains(id)).ifPresent(k -> write(r, k, "", true))), null);
        MenuItem fill = item("Fill down", this::fillDown, shortcut(KeyCode.D));
        MenuItem set = item("Set value…", this::setValue, null);
        MenuItem paste = item("Paste", this::paste, shortcut(KeyCode.V));
        MenuItem copy = item("Copy", this::copy, shortcut(KeyCode.C));
        MenuItem rename = item("Rename key…", this::renameKey, new KeyCodeCombination(KeyCode.F6));
        MenuItem find = item("Find", () -> search.requestFocus(), shortcut(KeyCode.F));
        MenuItem clear = item("Clear filters", this::clearFilters, shortcut(KeyCode.F, KeyCombination.SHIFT_DOWN));
        Menu cols = new Menu("Columns");
        cols.getItems().setAll(columnsMenuItems());
        cols.setOnShowing(e -> cols.getItems().setAll(columnsMenuItems()));
        Menu groups = new Menu("Group by");
        ToggleGroup tg = new ToggleGroup();
        for (String g : group.getItems()) {
            RadioMenuItem r = new RadioMenuItem(g);
            r.setToggleGroup(tg);
            r.setSelected(g.equals(group.getValue()));
            r.setOnAction(e -> group.setValue(g));
            groups.getItems().add(r);
        }
        CheckMenuItem without = new CheckMenuItem("Objects without metadata");
        without.setSelected(withoutBlock.isSelected());
        without.setOnAction(e -> withoutBlock.setSelected(without.isSelected()));
        CheckMenuItem inh = new CheckMenuItem("Inherited values");
        inh.setSelected(inherited.isSelected());
        inh.setOnAction(e -> inherited.setSelected(inh.isSelected()));
        MenuItem fix = item("Apply fix", this::applyFix, shortcut(KeyCode.PERIOD));
        MenuItem export = item("Export CSV…", this::export, null);
        return List.of(show, new SeparatorMenuItem(), edit, remove, empty, fill, set, paste, copy, rename,
            new SeparatorMenuItem(), find, clear, cols, groups, without, inh, new SeparatorMenuItem(), fix, export);
    }

    private List<MenuItem> columnsMenuItems() {
        List<MenuItem> items = new ArrayList<>();
        MenuItem left = item("Move left", () -> moveColumn(-1), new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN));
        MenuItem right = item("Move right", () -> moveColumn(1), new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN));
        items.add(left);
        items.add(right);
        items.add(new SeparatorMenuItem());
        ToggleGroup order = new ToggleGroup();
        for (var o : List.of(Map.entry("FIRST_APPEARANCE", "Order by first appearance"), Map.entry("FREQUENCY", "Order by frequency"),
                Map.entry("ALPHABETIC", "Order A–Z"))) {
            RadioMenuItem r = new RadioMenuItem(o.getValue());
            r.setToggleGroup(order);
            r.setSelected(settings().columnOrder().equals(o.getKey()));
            r.setOnAction(e -> {
                userOrder = new ArrayList<>();
                saveSettings(settings().withColumnOrder(o.getKey()));
                saveColumns();
                rebuild();
            });
            items.add(r);
        }
        items.add(new SeparatorMenuItem());
        MenuItem all = item("Show all", () -> {
            hidden.clear();
            saveColumns();
            rebuild();
        }, null);
        MenuItem reset = item("Reset columns", () -> {
            hidden.clear();
            userOrder = new ArrayList<>();
            saveSettings(settings().withColumns(projectKey(), MetadataModeSettingsData.ProjectColumns.EMPTY));
            keyColumns.clear();
            table.getSortOrder().clear();
            rebuild();
        }, null);
        items.add(all);
        items.add(reset);
        items.add(new SeparatorMenuItem());
        for (String key : columns.ordered(MetadataColumns.Order.valueOf(settings().columnOrder()), userOrder)) {
            CheckMenuItem c = new CheckMenuItem(key + " (" + columns.count(key) + ")");
            c.setSelected(!hidden.contains(key));
            c.setOnAction(e -> {
                if (c.isSelected()) hidden.remove(key); else hidden.add(key);
                saveColumns();
                rebuild();
            });
            items.add(c);
        }
        return items;
    }

    private List<MenuItem> contextMenuItems() {
        List<MenuItem> items = new ArrayList<>();
        items.add(item("Show in editor", this::showInEditor, null));
        Optional<String> key = focusedColumnId().filter(id -> !FIXED.contains(id));
        Optional<MetadataRow> row = focusedRow();
        if (key.isPresent() && row.isPresent()) {
            items.add(item("Remove entry", this::removeSelected, null));
            items.add(item("Set empty value", () -> write(row.get(), key.get(), "", true), null));
            inheritedOf(row.get(), key.get()).flatMap(InheritedValues.Inherited::origin).ifPresent(o ->
                items.add(item("Show source of the inherited value", () -> workbench.navigator().showInEditor(Focus.of(o)), null)));
            items.add(item("Rename key " + key.get() + "…", () -> renameKey(key.get()), null));
        }
        items.add(item("Fill down", this::fillDown, null));
        items.add(item("Set value…", this::setValue, null));
        items.add(item("Copy", this::copy, null));
        return items;
    }

    private static MenuItem item(String text, Runnable action, KeyCombination accelerator) {
        MenuItem i = new MenuItem(text);
        i.setOnAction(e -> action.run());
        if (accelerator != null) i.setAccelerator(accelerator);
        return i;
    }

    private static KeyCombination shortcut(KeyCode code, KeyCombination.Modifier... more) {
        KeyCombination.Modifier[] m = new KeyCombination.Modifier[more.length + 1];
        m[0] = KeyCombination.SHORTCUT_DOWN;
        System.arraycopy(more, 0, m, 1, more.length);
        return new KeyCodeCombination(code, m);
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private MetadataModeSettingsData settings() {
        MetadataModeSettingsData s = workbench.settings().metadataModeProperty().get();
        return s == null ? MetadataModeSettingsData.defaults() : s;
    }

    private void saveSettings(MetadataModeSettingsData s) {
        workbench.settings().metadataModeProperty().set(s);
    }

    /** The settings key of the project: its file; empty while unsaved (nothing kept). */
    private String projectKey() {
        Project p = workbench.session().project();
        return p == null || p.fileProperty().get() == null ? "" : p.fileProperty().get().getAbsolutePath();
    }

    private void saveColumns() {
        String project = projectKey();
        if (project.isEmpty()) return;
        Map<String, Double> widths = new LinkedHashMap<>();
        fixedColumns.forEach((id, c) -> widths.put(settingsId(id), c.getWidth()));
        keyColumns.forEach((k, c) -> {
            if (c.getWidth() > 0) widths.put(k, c.getWidth());
        });
        List<String> sort = table.getSortOrder().stream()
            .map(c -> settingsId((String) c.getUserData()) + (c.getSortType() == TreeTableColumn.SortType.ASCENDING ? ":asc" : ":desc"))
            .toList();
        saveSettings(settings().withColumns(project, new MetadataModeSettingsData.ProjectColumns(
            List.copyOf(userOrder), List.copyOf(hidden), widths, sort)));
    }

    private static String settingsId(String id) {
        return switch (id) {
            case FILE -> "@file";
            case TYPE -> "@type";
            case OBJECT -> "@object";
            case LINE -> "@line";
            default -> id;
        };
    }

    private void restoreSort(MetadataModeSettingsData.ProjectColumns c) {
        rebuilding = true;
        try {
            fixedColumns.forEach((id, column) -> {
                Double w = c.widths().get(settingsId(id));
                if (w != null) column.setPrefWidth(w);
            });
            List<TreeTableColumn<Item, ?>> sort = new ArrayList<>();
            for (String s : c.sort()) {
                int colon = s.lastIndexOf(':');
                String id = s.substring(0, colon);
                TreeTableColumn<Item, String> column = switch (id) {
                    case "@file" -> fixedColumns.get(FILE);
                    case "@type" -> fixedColumns.get(TYPE);
                    case "@object" -> fixedColumns.get(OBJECT);
                    case "@line" -> fixedColumns.get(LINE);
                    default -> keyColumns.get(id);
                };
                if (column == null || !table.getColumns().contains(column)) continue;
                column.setSortType(s.endsWith(":asc") ? TreeTableColumn.SortType.ASCENDING : TreeTableColumn.SortType.DESCENDING);
                sort.add(column);
            }
            table.getSortOrder().setAll(sort);
        } finally {
            rebuilding = false;
        }
        if (!table.getSortOrder().isEmpty()) table.sort();
    }

    private Dialogs defaultDialogs() {
        return new Dialogs() {
            @Override
            public Optional<String> ask(String title, String header, String initial) {
                TextInputDialog d = new TextInputDialog(initial);
                if (workbench.window() != null) d.initOwner(workbench.window());
                d.setTitle(title);
                d.setHeaderText(header);
                return d.showAndWait();
            }

            @Override
            public boolean confirm(String title, String header, String content) {
                ButtonType yes = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, content, yes, ButtonType.CANCEL);
                if (workbench.window() != null) a.initOwner(workbench.window());
                a.setTitle(title);
                a.setHeaderText(header);
                return a.showAndWait().orElse(ButtonType.CANCEL) == yes;
            }

            @Override
            public void inform(String title, String header, String content) {
                Alert a = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
                if (workbench.window() != null) a.initOwner(workbench.window());
                a.setTitle(title);
                a.setHeaderText(header);
                a.showAndWait();
            }

            @Override
            public Optional<Path> exportFile(Optional<Path> directory) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Export the metadata");
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"),
                    new FileChooser.ExtensionFilter("TSV", "*.tsv"));
                directory.map(Path::toFile).filter(java.io.File::isDirectory).ifPresent(chooser::setInitialDirectory);
                chooser.setInitialFileName("metadata.csv");
                return Optional.ofNullable(chooser.showSaveDialog(workbench.window())).map(java.io.File::toPath);
            }
        };
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    /** A cell of a fixed column; double-click shows the block in File Mode. */
    private final class FixedCell extends TreeTableCell<Item, String> {
        private final String id;

        FixedCell(String id) {
            this.id = id;
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && getTableRow() != null && getTableRow().getItem() instanceof RowItem) {
                    showInEditor();
                    e.consume();
                }
            });
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            setTooltip(null);
            setStyle("");
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                return;
            }
            Item item = getTableRow().getItem();
            if (item instanceof GroupItem) {
                setText(id.equals(FILE) ? value : "");
                setStyle("-fx-font-weight: bold;");
                return;
            }
            MetadataRow row = ((RowItem) item).row();
            if (id.equals(TYPE)) {
                CellProblems p = problemsOf(row);
                setText(value + (row.withoutBlock() ? " ＋" : "") + (p.block().isEmpty() ? "" : " ●"));
                String tip = row.isHeaderCandidate() ? "This file has no header: type a value to create one"
                    : row.withoutBlock() ? "No metadata block: type a value to create one" : null;
                if (!p.block().isEmpty()) {
                    tip = (tip == null ? "" : tip + "\n") + String.join("\n", p.block().stream().map(Diagnostic::message).toList());
                }
                if (tip != null) setTooltip(new Tooltip(tip));
                if (row.withoutBlock()) setStyle("-fx-text-fill: #888888;");
            } else {
                setText(value);
                if (id.equals(FILE)) setTooltip(new Tooltip(row.text().path().toString()));
            }
            setAccessibleText(value);
        }
    }

    /**
     * The text field of a cell editor: a selection of the whole text made when it gains the focus
     * is dropped before the first character it receives (that character must not replace the one
     * that started the edit); Select all (⌘A) works as usual.
     */
    private static final class EditorField extends TextField {
        private boolean first = true;

        EditorField(String text) {
            super(text);
            addEventFilter(KeyEvent.KEY_TYPED, e -> {
                if (first) {
                    first = false;
                    if (getSelection().getLength() == getLength() && getLength() > 0) {
                        deselect();
                        end();
                    }
                }
            });
            addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.A && e.isShortcutDown()) first = false;
            });
        }
    }

    /** A cell of a key column: its value (own, empty "∅", inherited greyed), problems, and its editor. */
    private final class KeyCell extends TreeTableCell<Item, String> {
        private final String key;

        KeyCell(String key) {
            this.key = key;
        }

        private Optional<MetadataRow> row() {
            return getTableRow() != null && getTableRow().getItem() instanceof RowItem r ? Optional.of(r.row()) : Optional.empty();
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            // while editing, the editor stays (a layout pass updates the cell too)
            if (isEditing()) return;
            setTooltip(null);
            setStyle("");
            setGraphic(null);
            Optional<MetadataRow> row = empty ? Optional.empty() : row();
            if (row.isEmpty()) {
                setText(null);
                return;
            }
            MetadataRow r = row.get();
            Optional<String> own = r.value(key);
            List<String> tips = new ArrayList<>();
            String style = "";
            if (own.isPresent()) {
                setText(own.get().isEmpty() ? "∅" : own.get());
                if (own.get().isEmpty()) {
                    style = "-fx-text-fill: #999999;";
                    tips.add("Empty value");
                }
            } else {
                Optional<InheritedValues.Inherited> inh = inherited.isSelected() ? inheritedOf(r, key) : Optional.empty();
                if (inh.isPresent()) {
                    setText(inh.get().value());
                    style = "-fx-text-fill: #999999; -fx-font-style: italic;";
                    tips.add("Inherited " + inh.get().source() + "; type to write an own value");
                } else {
                    setText("");
                    if (r.withoutBlock()) tips.add("No metadata block: type a value to create one");
                }
            }
            CellEdit.readOnly(r, key).ifPresent(tips::add);
            List<Diagnostic> problems = problemsOf(r).of(key);
            if (!problems.isEmpty()) {
                boolean error = problems.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
                style += error ? "-fx-border-color: #c33; -fx-border-width: 1.5;" : "-fx-border-color: #e0a000; -fx-border-width: 1.5;";
                problems.forEach(d -> tips.add(d.message() + (d.fixes().isEmpty() ? "" : " (⌘. applies: " + d.fixes().get(0).label() + ")")));
            }
            setStyle(style);
            if (!tips.isEmpty()) setTooltip(new Tooltip(String.join("\n", tips)));
            setAccessibleText(key + ": " + (getText() == null || getText().isEmpty() ? "empty" : getText())
                + (CellEdit.readOnly(r, key).isPresent() ? ", read-only" : ", editable"));
        }

        @Override
        public void startEdit() {
            Optional<MetadataRow> row = row();
            if (row.isEmpty()) return;
            Optional<String> readOnly = CellEdit.readOnly(row.get(), key);
            if (readOnly.isPresent()) {
                workbench.status().info(readOnly.get());
                initialText = null;
                return;
            }
            super.startEdit();
            if (!isEditing()) return;
            MetadataRow r = row.get();
            boolean typed = initialText != null;
            EditorField field = new EditorField(typed ? initialText : r.value(key).orElse(""));
            initialText = null;
            // the suggestions do not take the keyboard: ↑ ↓ choose one, Enter takes it
            javafx.scene.control.ListView<String> list = new javafx.scene.control.ListView<>();
            list.setId("metadataCompletions");
            list.setPrefHeight(150);
            list.setFocusTraversable(false);
            javafx.stage.Popup completions = new javafx.stage.Popup();
            completions.getContent().add(list);
            completions.setAutoHide(true);
            completions.setHideOnEscape(false);
            list.setOnMouseClicked(e -> {
                String v = list.getSelectionModel().getSelectedItem();
                if (v != null) {
                    field.setText(v);
                    field.end();
                    completions.hide();
                }
            });
            field.textProperty().addListener((obs, o, n) -> showCompletions(field, completions, list, n));
            field.setOnAction(e -> {
                completions.hide();
                finish(r, field.getText(), 0);
            });
            field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                boolean offering = completions.isShowing() && !list.getItems().isEmpty();
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (offering) {
                        completions.hide();
                    } else {
                        cancelEdit();
                    }
                    e.consume();
                } else if (e.getCode() == KeyCode.TAB) {
                    completions.hide();
                    finish(r, field.getText(), e.isShiftDown() ? -1 : 1);
                    e.consume();
                } else if (offering && (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP)) {
                    int i = list.getSelectionModel().getSelectedIndex() + (e.getCode() == KeyCode.DOWN ? 1 : -1);
                    list.getSelectionModel().select(Math.max(0, Math.min(list.getItems().size() - 1, i)));
                    list.scrollTo(list.getSelectionModel().getSelectedIndex());
                    e.consume();
                } else if (offering && e.getCode() == KeyCode.ENTER && list.getSelectionModel().getSelectedItem() != null) {
                    field.setText(list.getSelectionModel().getSelectedItem());
                    field.end();
                    completions.hide();
                    e.consume();
                }
            });
            HBox box = new HBox(2, field);
            HBox.setHgrow(field, Priority.ALWAYS);
            r.owner().ifPresent(owner -> {
                ValueCell cell = new ValueCell(owner, key, field.getText(), true, v -> field.setText(v), workbench.viewContext());
                MetadataValueEditor.of(workbench.viewContext().metadataSchema().typeOf(key))
                    .chooser(cell, field.getText(), field::setText).ifPresent(box.getChildren()::add);
            });
            setText(null);
            setGraphic(box);
            field.requestFocus();
            field.end();
            // the character that started the edit already suggests values
            if (typed) Platform.runLater(() -> showCompletions(field, completions, list, field.getText()));
        }

        private void showCompletions(TextField field, javafx.stage.Popup popup, javafx.scene.control.ListView<String> list,
                                     String typed) {
            List<String> values = new ArrayList<>();
            for (MetadataRow r : index.all()) r.value(key).ifPresent(values::add);
            List<String> offered = Completion.of(values, typed);
            if (offered.isEmpty() || typed.isEmpty() || field.getScene() == null) {
                popup.hide();
                return;
            }
            list.getItems().setAll(offered);
            list.getSelectionModel().clearSelection();
            list.setPrefWidth(Math.max(field.getWidth(), 120));
            if (!popup.isShowing()) {
                var p = field.localToScreen(0, field.getHeight());
                if (p != null) popup.show(field, p.getX(), p.getY());
            }
        }

        private void finish(MetadataRow row, String text, int move) {
            String column = key;
            cancelEdit();
            write(row, key, text, false);
            if (move != 0) {
                List<String> keys = table.getVisibleLeafColumns().stream().map(c -> (String) c.getUserData())
                    .filter(id -> !FIXED.contains(id)).toList();
                int i = keys.indexOf(column) + move;
                if (i >= 0 && i < keys.size()) pending = Optional.of(new PendingCell(row.key(), keys.get(i)));
                if (pending.isPresent() && pending.get().row().equals(row.key()) && !pending.get().column().equals(column)) {
                    PendingCell p = pending.get();
                    // no parse when nothing changed: select now
                    Platform.runLater(() -> {
                        if (pending.isPresent()) {
                            pending = Optional.empty();
                            select(p.row(), p.column());
                        }
                    });
                }
            }
            table.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            updateItem(getItem(), getItem() == null && row().isEmpty());
        }
    }
}
