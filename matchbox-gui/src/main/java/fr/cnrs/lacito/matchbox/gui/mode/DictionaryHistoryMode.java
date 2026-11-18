package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.layout.ColumnSplitPane;
import fr.cnrs.lacito.matchbox.gui.layout.PanelToggles;
import fr.cnrs.lacito.matchbox.gui.lexicon.EntrySnapshotPane;
import fr.cnrs.lacito.matchbox.gui.lexicon.StatusBadge;
import fr.cnrs.lacito.matchbox.gui.mode.history.HistoryLayout;
import fr.cnrs.lacito.matchbox.gui.workbench.DictionaryHistoryService;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandKey;
import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandTargets;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntryKey;
import fr.cnrs.lacito.matchbox.model.lexicon.history.EntryRow;
import fr.cnrs.lacito.matchbox.model.lexicon.history.HistoryResult;
import fr.cnrs.lacito.matchbox.model.lexicon.history.OperationRow;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.DictionaryHistorySettingsData;
import java.io.File;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Dictionary History: the dictionary as the mutation commands of the project's notes leave it,
 * and what shaped each entry. On the left, the entries (form and gloss, in an object and a meta
 * language to choose); on the right, the selected entry (lift-fx's editor, read-only) above the
 * commands that changed it, in history order; selecting a command shows the entry as it was after
 * it. A banner says when the commands were not applied (syntax or static errors) or where applying
 * stopped (the first dynamic error). The history itself is computed in the background
 * ({@link DictionaryHistoryService}); this mode only shows it.
 */
public final class DictionaryHistoryMode extends AbstractMode {

    public static final String ID = "history";

    private static final KeyCombination SHOW_IN_EDITOR = new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination FOCUS_FILTER = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination NEXT_CHANGED = new KeyCodeCombination(KeyCode.CLOSE_BRACKET, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination PREVIOUS_CHANGED = new KeyCodeCombination(KeyCode.OPEN_BRACKET, KeyCombination.SHORTCUT_DOWN);

    /** What the table of entries lists (S2). */
    public enum EntryFilter {
        ALL("All entries"), CHANGED("Changed by commands"), CREATED("Created by commands"), DELETED("Deleted by commands");

        private final String label;

        EntryFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final DictionaryHistoryService service;
    private final StackPane root = new StackPane();
    private final VBox content = new VBox();
    private final HBox banner = new HBox(8);
    private final Label bannerText = new Label();
    private final Hyperlink bannerLink = new Hyperlink();
    private final TextField filter = new TextField();
    private final ComboBox<String> objectLanguage = new ComboBox<>();
    private final ComboBox<String> metaLanguage = new ComboBox<>();
    private final ChoiceBox<EntryFilter> entryFilter = new ChoiceBox<>(FXCollections.observableArrayList(EntryFilter.values()));
    private final TableView<EntryRow> entries = new TableView<>();
    private final Label entriesTitle = new Label("Entries");
    private final Label entryHeader = new Label();
    private final HBox timeTravel = new HBox(8);
    private final Label timeTravelText = new Label();
    private final EntrySnapshotPane entryPane = new EntrySnapshotPane();
    private final Label commandsTitle = new Label("Commands applied to this entry");
    private final TableView<OperationRow> commands = new TableView<>();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final ObjectProperty<HistoryLayout> layout = new SimpleObjectProperty<>();
    private final List<ColumnSplitPane> splits = new ArrayList<>();
    private boolean syncingLayout;
    private PanelToggles<HistoryLayout> panelToggles;
    private final Menu menu;
    private final Menu objectLanguageMenu = new Menu("Object language");
    private final Menu metaLanguageMenu = new Menu("Meta language");
    private final MenuItem exportItem = new MenuItem("Export patched dictionary…");
    private final FilteredList<EntryRow> filtered;
    private HistoryResult shown = HistoryResult.computing();
    private EntryKey selectedKey;
    private boolean stale = true;
    private boolean updatingLanguages;

    public DictionaryHistoryMode(Workbench workbench) {
        super(workbench);
        this.service = workbench.history();
        filtered = new FilteredList<>(FXCollections.observableArrayList(), e -> true);
        SortedList<EntryRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(entries.comparatorProperty());
        entries.setItems(sorted);
        buildEntriesTable();
        buildCommandsTable();
        HistoryLayout initial = layoutOf(settings());
        layout.set(initial);
        Node entriesPane = buildEntriesPane();
        Node entryPaneNode = buildEntryPane();
        Node commandsPane = buildCommandsPane();
        arrange(entriesPane, entryPaneNode, commandsPane);
        layout.addListener((obs, o, n) -> {
            if (!syncingLayout) syncSplits(n);
            saveSettings(settings().withPanels(n.isShown(0), n.isShown(1), n.isShown(2), n.widths(), n.heights()));
        });
        panelToggles = new PanelToggles<>(List.of(
            new PanelToggles.Panel("Entries", "Show or hide the entries of the dictionary", "historyEntriesToggle",
                PanelToggles.accelerator(1)),
            new PanelToggles.Panel("Entry", "Show or hide the selected entry", "historyEntryToggle",
                PanelToggles.accelerator(2)),
            new PanelToggles.Panel("Commands", "Show or hide the commands that changed the entry", "historyCommandsToggle",
                PanelToggles.accelerator(3))),
            layout, HistoryLayout.panels());

        banner.setId("historyBanner");
        banner.setPadding(new Insets(4, 8, 4, 8));
        banner.setStyle("-fx-background-color: #fff4ce; -fx-border-color: #e0c060; -fx-border-width: 0 0 1 0;");
        bannerText.setWrapText(true);
        HBox.setHgrow(bannerText, Priority.ALWAYS);
        banner.getChildren().addAll(bannerText, bannerLink);
        progress.setMaxSize(18, 18);
        progress.setVisible(false);
        HBox top = new HBox(6, banner, progress);
        HBox.setHgrow(banner, Priority.ALWAYS);
        VBox.setVgrow(splits.get(0).node(), Priority.ALWAYS);
        content.getChildren().setAll(top, splits.get(0).node());
        root.getChildren().setAll(content);
        root.setId("dictionaryHistoryRoot");

        menu = buildMenu();
        service.resultProperty().addListener((obs, o, n) -> {
            if (isActive()) show(n);
            else stale = true;
        });
        service.computingProperty().addListener((obs, o, n) -> progress.setVisible(n));
        entryFilter.setValue(Boolean.TRUE.equals(settings().changedOnly()) ? EntryFilter.CHANGED : EntryFilter.ALL);
        entryFilter.valueProperty().addListener((obs, o, n) -> {
            if (n == EntryFilter.DELETED || o == EntryFilter.DELETED) fillEntries();
            else applyFilter();
            saveSettings(settings().withChangedOnly(n == EntryFilter.CHANGED));
        });
        filter.textProperty().addListener((obs, o, n) -> applyFilter());
    }

    // ------------------------------------------------------------------ layout

    private void buildEntriesTable() {
        entries.setId("historyEntries");
        entries.setPlaceholder(new Label("No entry"));
        TableColumn<EntryRow, String> form = new TableColumn<>("Form");
        form.setCellValueFactory(c -> new ReadOnlyStringWrapper(formOf(c.getValue())));
        form.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                EntryRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setText(null);
                    setTooltip(null);
                    setAccessibleText(null);
                    return;
                }
                String mark = row.created() ? " ✚" : row.changed() ? " ●" : "";
                setText(item.isEmpty() ? "(none)" : item + mark);
                setStyle(item.isEmpty() ? "-fx-text-fill: gray;" : "");
                setTooltip(new Tooltip(String.join(", ", row.forms().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue()).toList())
                    + (row.created() ? "\nCreated by commands" : row.changed() ? "\nChanged by commands" : "")));
                setAccessibleText(item + (row.created() ? ", created by commands" : row.changed() ? ", changed by commands" : ""));
            }
        });
        form.setComparator(collator());
        form.setPrefWidth(140);
        TableColumn<EntryRow, String> gloss = new TableColumn<>("Gloss");
        gloss.setCellValueFactory(c -> new ReadOnlyStringWrapper(glossOf(c.getValue())));
        gloss.setComparator(collator());
        gloss.setPrefWidth(150);
        entries.getColumns().setAll(List.of(form, gloss));
        entries.getSortOrder().setAll(List.of(form));
        entries.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> entrySelected(n));
    }

    private Comparator<String> collator() {
        Collator c = Collator.getInstance(Locale.ROOT);
        c.setStrength(Collator.SECONDARY);
        return (a, b) -> c.compare(a == null ? "" : a, b == null ? "" : b);
    }

    private void buildCommandsTable() {
        commands.setId("historyCommands");
        commands.setPlaceholder(new Label("No command changed this entry"));
        TableColumn<OperationRow, String> index = column("#", 44, r -> String.valueOf(r.index()));
        TableColumn<OperationRow, String> file = column("File", 130, r -> name(r.text()));
        TableColumn<OperationRow, String> line = column("Line", 50, r -> String.valueOf(r.line()));
        TableColumn<OperationRow, String> verb = column("Verb", 70, OperationRow::verb);
        TableColumn<OperationRow, String> command = column("Command", 280, OperationRow::command);
        TableColumn<OperationRow, String> status = column("Status", 110, r -> r.status().label());
        status.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                OperationRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setText(null);
                setGraphic(row == null ? null : new StatusBadge(row.status(), row.error()));
            }
        });
        commands.getColumns().setAll(List.of(index, file, line, verb, command, status));
        commands.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> commandSelected(n));
        commands.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                showInEditor();
                e.consume();
            } else if (new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN).match(e)) {
                copyCommands();
                e.consume();
            }
        });
        commands.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) showInEditor();
        });
    }

    private static <T> TableColumn<T, String> column(String title, double width, java.util.function.Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setSortable(false);
        return c;
    }

    private Node buildEntriesPane() {
        filter.setId("historyFilter");
        filter.setPromptText("Filter forms and glosses");
        objectLanguage.setId("historyObjectLanguage");
        objectLanguage.setTooltip(new Tooltip("The language of the forms"));
        metaLanguage.setId("historyMetaLanguage");
        metaLanguage.setTooltip(new Tooltip("The language of the glosses"));
        entryFilter.setId("historyEntryFilter");
        objectLanguage.valueProperty().addListener((obs, o, n) -> languagesChanged());
        metaLanguage.valueProperty().addListener((obs, o, n) -> languagesChanged());
        entriesTitle.setStyle("-fx-font-weight: bold; -fx-padding: 4 4 0 4;");
        HBox languages = new HBox(4, objectLanguage, metaLanguage);
        objectLanguage.setMaxWidth(Double.MAX_VALUE);
        metaLanguage.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(objectLanguage, Priority.ALWAYS);
        HBox.setHgrow(metaLanguage, Priority.ALWAYS);
        entryFilter.setMaxWidth(Double.MAX_VALUE);
        VBox pane = new VBox(4, entriesTitle, filter, languages, entryFilter, entries);
        pane.setPadding(new Insets(0, 2, 2, 2));
        pane.setMinWidth(160);
        VBox.setVgrow(entries, Priority.ALWAYS);
        return pane;
    }

    private Node buildEntryPane() {
        entryHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 1.15em;");
        entryHeader.setId("historyEntryHeader");
        Hyperlink showFinal = new Hyperlink("Show final");
        showFinal.setOnAction(e -> commands.getSelectionModel().clearSelection());
        timeTravel.getChildren().setAll(timeTravelText, showFinal);
        timeTravel.setStyle("-fx-background-color: #e8f0fe; -fx-padding: 2 6 2 6;");
        timeTravel.setId("historyTimeTravel");
        timeTravel.setVisible(false);
        timeTravel.setManaged(false);
        ScrollPane scroll = new ScrollPane(entryPane);
        scroll.setFitToWidth(true);
        VBox pane = new VBox(4, entryHeader, timeTravel, scroll);
        pane.setPadding(new Insets(4));
        pane.setMinHeight(80);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return pane;
    }

    private Node buildCommandsPane() {
        commandsTitle.setStyle("-fx-font-weight: bold;");
        VBox pane = new VBox(4, commandsTitle, commands);
        pane.setPadding(new Insets(4));
        pane.setMinHeight(60);
        VBox.setVgrow(commands, Priority.ALWAYS);
        return pane;
    }

    private void arrange(Node entriesPane, Node entryPaneNode, Node commandsPane) {
        HistoryLayout l = layout.get();
        ColumnSplitPane stack = new ColumnSplitPane(Orientation.VERTICAL, List.of(entryPaneNode, commandsPane), l.inner());
        stack.node().setId("historyStack");
        ColumnSplitPane outer = new ColumnSplitPane(Orientation.HORIZONTAL, List.of(entriesPane, stack.node()), l.outer());
        outer.node().setId("historySplit");
        outer.setFocusOrder(List.of(0, 1), List.of(entries, commands));
        stack.setFocusOrder(List.of(1, 0), List.of(entryPane, commands));
        splits.add(outer);
        splits.add(stack);
        stack.layoutProperty().addListener((obs, o, n) -> {
            if (syncingLayout) return;
            layout.set(layout.get().withInner(n));
        });
        outer.layoutProperty().addListener((obs, o, n) -> {
            if (syncingLayout) return;
            layout.set(layout.get().withOuter(n));
        });
    }

    private void syncSplits(HistoryLayout l) {
        syncingLayout = true;
        try {
            splits.get(0).layoutProperty().set(l.outer());
            splits.get(1).layoutProperty().set(l.inner());
        } finally {
            syncingLayout = false;
        }
    }

    /** The layout of the panels (tests, Panels menu). */
    public ObjectProperty<HistoryLayout> layoutProperty() {
        return layout;
    }

    // ------------------------------------------------------------------ menu

    private Menu buildMenu() {
        Menu panels = new Menu("Panels");
        panels.getItems().addAll(panelToggles.menuItems());
        MenuItem showAll = new MenuItem("Show all panels");
        showAll.setAccelerator(PanelToggles.accelerator(0));
        showAll.setOnAction(e -> layout.set(layout.get().withAll()));
        MenuItem reset = new MenuItem("Reset panel sizes");
        reset.setOnAction(e -> layout.set(layout.get().withSizes(DictionaryHistorySettingsData.DEFAULT_WIDTHS,
            DictionaryHistorySettingsData.DEFAULT_HEIGHTS)));
        panels.getItems().addAll(new SeparatorMenuItem(), showAll, reset);

        MenuItem showInEditor = new MenuItem("Show command in editor");
        showInEditor.setAccelerator(SHOW_IN_EDITOR);
        showInEditor.setOnAction(e -> showInEditor());
        MenuItem next = new MenuItem("Next changed entry");
        next.setAccelerator(NEXT_CHANGED);
        next.setOnAction(e -> selectChanged(1));
        MenuItem previous = new MenuItem("Previous changed entry");
        previous.setAccelerator(PREVIOUS_CHANGED);
        previous.setOnAction(e -> selectChanged(-1));
        MenuItem focusFilter = new MenuItem("Filter entries");
        focusFilter.setAccelerator(FOCUS_FILTER);
        focusFilter.setOnAction(e -> filter.requestFocus());
        Menu show = new Menu("Show");
        ToggleGroup showGroup = new ToggleGroup();
        for (EntryFilter f : EntryFilter.values()) {
            RadioMenuItem item = new RadioMenuItem(f.toString());
            item.setToggleGroup(showGroup);
            item.setOnAction(e -> entryFilter.setValue(f));
            show.getItems().add(item);
        }
        MenuItem recompute = new MenuItem("Recompute history");
        recompute.setOnAction(e -> service.recompute());
        exportItem.setOnAction(e -> export());
        Menu result = new Menu("Dictionary History", null, panels, new SeparatorMenuItem(), showInEditor, next, previous,
            focusFilter, show, objectLanguageMenu, metaLanguageMenu, new SeparatorMenuItem(), recompute, exportItem);
        result.setOnShowing(e -> {
            showInEditor.setDisable(commands.getSelectionModel().getSelectedItem() == null);
            exportItem.setDisable(!shown.isClean());
            for (var item : show.getItems()) {
                ((RadioMenuItem) item).setSelected(item.getText().equals(entryFilter.getValue().toString()));
            }
            fillLanguageMenu(objectLanguageMenu, objectLanguage);
            fillLanguageMenu(metaLanguageMenu, metaLanguage);
        });
        return result;
    }

    private static void fillLanguageMenu(Menu menu, ComboBox<String> box) {
        menu.getItems().clear();
        ToggleGroup group = new ToggleGroup();
        for (String lang : box.getItems()) {
            RadioMenuItem item = new RadioMenuItem(lang);
            item.setToggleGroup(group);
            item.setSelected(lang.equals(box.getValue()));
            item.setOnAction(e -> box.setValue(lang));
            menu.getItems().add(item);
        }
        menu.setDisable(box.getItems().isEmpty());
    }

    @Override
    public Optional<Menu> menu() {
        return Optional.of(menu);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public Optional<Node> toolBarItems() {
        return Optional.of(panelToggles.toolBarItems());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onProjectChanged() {
        selectedKey = null;
        stale = false;
        show(service.result());
    }

    @Override
    protected void onTextsChanged(Set<TextId> texts) {
        // the history service recomputes when commands change; its result is shown when it comes
    }

    @Override
    protected void onActivated() {
        if (stale) {
            stale = false;
            show(service.result());
        }
    }

    /** Shows a history: banner, languages, entries; the selection is kept by entry. */
    private void show(HistoryResult result) {
        shown = result;
        updateBanner(result);
        updatingLanguages = true;
        try {
            String[] chosen = chosenLanguages();
            setChoices(objectLanguage, result.objectLanguages(), chosen[0]);
            setChoices(metaLanguage, result.metaLanguages(), chosen[1]);
        } finally {
            updatingLanguages = false;
        }
        fillEntries();
    }

    private void fillEntries() {
        EntryKey keep = selectedKey;
        List<EntryRow> rows = entryFilter.getValue() == EntryFilter.DELETED ? shown.deleted() : shown.entries();
        ((javafx.collections.ObservableList<EntryRow>) filtered.getSource()).setAll(rows);
        applyFilter();
        entriesTitle.setText("Entries (" + filtered.size() + ")");
        entries.setPlaceholder(new Label(emptyText()));
        if (keep != null) {
            for (EntryRow row : entries.getItems()) {
                if (row.key().matches(keep)) {
                    entries.getSelectionModel().select(row);
                    entries.scrollTo(row);
                    return;
                }
            }
        }
        entrySelected(entries.getSelectionModel().getSelectedItem());
    }

    private String emptyText() {
        if (shown.status() instanceof HistoryResult.Status.Computing) return "Computing the history…";
        if (shown.isEmpty()) {
            return "No dictionary history. Choose a context dictionary in Project properties › Inherited context, "
                + "or write LiftPatch commands (e.g. p /mami/pig) in your notes.";
        }
        return "No entry";
    }

    private void applyFilter() {
        String f = normalise(filter.getText());
        EntryFilter which = entryFilter.getValue();
        filtered.setPredicate(row -> {
            if (which == EntryFilter.CHANGED && !row.changed()) return false;
            if (which == EntryFilter.CREATED && !row.created()) return false;
            if (f.isEmpty()) return true;
            return row.forms().values().stream().anyMatch(v -> normalise(v).contains(f))
                || row.glosses().values().stream().flatMap(List::stream).anyMatch(v -> normalise(v).contains(f));
        });
        entriesTitle.setText("Entries (" + filtered.size() + ")");
    }

    private static String normalise(String s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return d.toLowerCase(Locale.ROOT).strip();
    }

    private void updateBanner(HistoryResult r) {
        String text = null;
        Runnable action = null;
        String link = null;
        switch (r.status()) {
            case HistoryResult.Status.NotTried n -> {
                text = "Commands not applied: " + n.errorCount() + " syntax or static error" + (n.errorCount() == 1 ? "" : "s")
                    + " (first: " + name(n.firstText()) + ":" + n.firstLine() + "). Showing the context dictionary.";
                link = "Show first error";
                action = () -> showLine(n.firstText(), n.firstLine());
            }
            case HistoryResult.Status.StoppedAt s -> {
                text = "Applying stopped at " + name(s.text()) + ":" + s.line() + ": " + s.message() + ". " + name(s.text())
                    + " was rolled back; " + s.laterCommands() + " later command" + (s.laterCommands() == 1 ? " is" : "s are")
                    + " not applied.";
                link = "Show";
                action = () -> showLine(s.text(), s.line());
            }
            case HistoryResult.Status.Failed f -> {
                text = "The dictionary history cannot be computed: " + f.message();
                link = "Recompute";
                action = service::recompute;
            }
            case HistoryResult.Status.Computing c -> text = "Computing the history of the dictionary…";
            case HistoryResult.Status.Clean c -> {
                if (r.startedEmpty() && r.commandCount() > 0) {
                    DictionaryHistorySettingsData s = settings();
                    text = "No context dictionary: starting from an empty dictionary (object language "
                        + s.emptyObjectLanguage() + ", meta language " + s.emptyMetaLanguage() + ").";
                }
            }
        }
        boolean visible = text != null;
        banner.setVisible(visible);
        banner.setManaged(visible);
        bannerText.setText(text == null ? "" : text);
        bannerLink.setVisible(link != null);
        bannerLink.setText(link == null ? "" : link);
        Runnable a = action;
        bannerLink.setOnAction(e -> {
            if (a != null) a.run();
        });
    }

    // ------------------------------------------------------------------ languages

    private String[] chosenLanguages() {
        String saved = settings().languagesOf(projectKey());
        String o = objectLanguage.getValue();
        String m = metaLanguage.getValue();
        if (saved != null) {
            String[] parts = saved.split(" ");
            if (o == null && parts.length > 0) o = parts[0];
            if (m == null && parts.length > 1) m = parts[1];
        }
        return new String[] {o, m};
    }

    private static void setChoices(ComboBox<String> box, List<String> choices, String wanted) {
        if (!box.getItems().equals(choices)) box.getItems().setAll(choices);
        if (wanted != null && choices.contains(wanted)) box.setValue(wanted);
        else box.setValue(choices.isEmpty() ? null : choices.get(0));
    }

    private void languagesChanged() {
        if (updatingLanguages) return;
        entries.refresh();
        entrySelected(entries.getSelectionModel().getSelectedItem());
        if (objectLanguage.getValue() != null && metaLanguage.getValue() != null) {
            saveSettings(settings().withLanguages(projectKey(), objectLanguage.getValue(), metaLanguage.getValue()));
        }
    }

    private String formOf(EntryRow row) {
        String lang = objectLanguage.getValue();
        String f = lang == null ? null : row.forms().get(lang);
        return f == null ? "" : f;
    }

    private String glossOf(EntryRow row) {
        String lang = metaLanguage.getValue();
        return lang == null ? "" : row.gloss(lang);
    }

    private String projectKey() {
        Project p = workbench.session().project();
        if (p == null) return "";
        File f = p.fileProperty().get();
        return f != null ? f.getAbsolutePath() : String.valueOf(p.getName());
    }

    // ------------------------------------------------------------------ selection

    private void entrySelected(EntryRow row) {
        selectedKey = row == null ? selectedKey : row.key();
        commands.getItems().clear();
        timeTravel.setVisible(false);
        timeTravel.setManaged(false);
        if (row == null) {
            entryHeader.setText("");
            commandsTitle.setText("Commands applied to this entry");
            entryPane.showMessage(entries.getItems().isEmpty() ? emptyText() : "Select an entry.");
            setFocus(Optional.empty());
            return;
        }
        List<OperationRow> ops = row.operations().stream().map(shown::operation).flatMap(Optional::stream).toList();
        commands.getItems().setAll(ops);
        commandsTitle.setText("Commands applied to this entry (" + ops.size() + ")");
        String form = formOf(row).isEmpty() ? row.key().form() : formOf(row);
        String origin = row.created() ? "created by commands" : "from the context dictionary";
        entryHeader.setText(form + "   " + origin + " · " + ops.size() + " command" + (ops.size() == 1 ? "" : "s"));
        commands.setPlaceholder(new Label(shown.status() instanceof HistoryResult.Status.NotTried
            ? "Commands are not applied while the project has syntax or static errors."
            : "No command changed this entry"));
        showState(row, entryFilter.getValue() == EntryFilter.DELETED ? lastBefore(row) : shown.shownPosition(), null);
    }

    /** The position just before the deletion of a deleted entry: its last state. */
    private int lastBefore(EntryRow row) {
        return row.operations().isEmpty() ? 0 : row.operations().get(row.operations().size() - 1) - 1;
    }

    private void commandSelected(OperationRow op) {
        EntryRow row = entries.getSelectionModel().getSelectedItem();
        if (row == null) return;
        if (op == null) {
            timeTravel.setVisible(false);
            timeTravel.setManaged(false);
            showState(row, entryFilter.getValue() == EntryFilter.DELETED ? lastBefore(row) : shown.shownPosition(), null);
            setFocus(Optional.empty());
            return;
        }
        timeTravelText.setText("Showing the state after " + name(op.text()) + ":" + op.line()
            + (op.status() == fr.cnrs.lacito.matchbox.model.lexicon.history.CommandStatus.ROLLED_BACK
                ? " (rolled back later)" : ""));
        timeTravel.setVisible(true);
        timeTravel.setManaged(true);
        showState(row, op.index(), op);
        setFocus(Optional.of(Focus.at(op.text(), op.start())));
    }

    private void showState(EntryRow row, int position, OperationRow op) {
        HistoryResult of = shown;
        entryPane.showMessage("Loading…");
        service.entryAt(of, row.key().id(), position).thenAccept(snapshot -> {
            if (shown != of) return;
            entryPane.show(snapshot.orElse(null), op == null ? "The entry is not in the dictionary."
                : "The entry does not exist after this command (deleted).");
        }).exceptionally(e -> {
            entryPane.showMessage("Cannot show the entry: " + e.getMessage());
            return null;
        });
    }

    private void selectChanged(int direction) {
        List<EntryRow> items = entries.getItems();
        if (items.isEmpty()) return;
        int start = entries.getSelectionModel().getSelectedIndex();
        for (int k = 1; k <= items.size(); k++) {
            int i = Math.floorMod(start + direction * k, items.size());
            if (items.get(i).changed()) {
                entries.getSelectionModel().select(i);
                entries.scrollTo(i);
                return;
            }
        }
    }

    private void showInEditor() {
        OperationRow op = commands.getSelectionModel().getSelectedItem();
        if (op != null) {
            workbench.navigator().showInEditor(Focus.at(op.text(), op.start()));
        }
    }

    private void showLine(TextId text, int line) {
        int offset = workbench.session().corpus().text(text).map(t -> lineStart(t.content(), line)).orElse(0);
        workbench.navigator().showInEditor(Focus.at(text, offset));
    }

    private static int lineStart(String content, int line) {
        int offset = 0;
        for (int l = 1; l < line; l++) {
            int nl = content.indexOf('\n', offset);
            if (nl < 0) return content.length();
            offset = nl + 1;
        }
        return offset;
    }

    private void copyCommands() {
        StringBuilder sb = new StringBuilder();
        for (OperationRow r : commands.getSelectionModel().getSelectedItems()) {
            sb.append(name(r.text())).append(':').append(r.line()).append('\t').append(r.command()).append('\t')
                .append(r.status().label()).append('\n');
        }
        ClipboardContent c = new ClipboardContent();
        c.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(c);
    }

    private void export() {
        if (!shown.isClean()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export the patched dictionary");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("LIFT dictionary", "*.lift"));
        chooser.setInitialFileName("patched.lift");
        if (workbench.session().project() != null) {
            workbench.session().project().directory().map(java.nio.file.Path::toFile).filter(java.io.File::isDirectory).ifPresent(chooser::setInitialDirectory);
        }
        File file = chooser.showSaveDialog(workbench.window());
        if (file == null) return;
        HistoryResult of = shown;
        service.export(of, file.toPath()).thenRun(() -> workbench.status().info("Exported " + of.entries().size()
            + " entries to " + file.getName())).exceptionally(e -> {
                ErrorReporter.report(workbench.window(), "Cannot export the dictionary", e);
                return null;
            });
    }

    // ------------------------------------------------------------------ navigation

    /**
     * Shows the entry a command is about, or the entry whose form is the word at a place of a text
     * (Show history of entry at caret).
     */
    @Override
    public boolean reveal(Focus focus) {
        if (stale) {
            stale = false;
            show(service.result());
        }
        Optional<EntryRow> row = Optional.empty();
        Optional<CorpusObject> object = focus.object();
        if (object.isPresent() && object.get() instanceof LexiconMutationCommand c) {
            row = entryOfCommand(c);
        }
        if (row.isEmpty()) {
            Optional<String> word = workbench.session().corpus().text(focus.text())
                .map(t -> wordAt(t, focus.start()));
            row = word.filter(w -> !w.isEmpty()).flatMap(w -> shown.entries().stream()
                .filter(e -> e.forms().values().stream().anyMatch(f -> f.equalsIgnoreCase(w))).findFirst());
        }
        if (row.isEmpty()) return false;
        if (!filtered.contains(row.get())) {
            filter.clear();
            entryFilter.setValue(EntryFilter.ALL);
        }
        entries.getSelectionModel().select(row.get());
        entries.scrollTo(row.get());
        return true;
    }

    private Optional<EntryRow> entryOfCommand(LexiconMutationCommand c) {
        List<Integer> ops = shown.commandOperations().getOrDefault(CommandKey.of(c), List.of());
        for (Integer op : ops) {
            Optional<EntryRow> e = shown.entries().stream().filter(r -> r.operations().contains(op)).findFirst();
            if (e.isPresent()) return e;
        }
        Optional<String> form = CommandTargets.entryForm(c.item());
        return form.flatMap(f -> shown.entries().stream().filter(r -> r.forms().containsValue(f)).findFirst());
    }

    /** The word (letters, marks, digits, apostrophes, hyphens) around an offset. */
    static String wordAt(Text text, int offset) {
        String s = text.content();
        int start = Math.max(0, Math.min(offset, s.length()));
        int end = start;
        while (start > 0 && isWordChar(s.charAt(start - 1))) start--;
        while (end < s.length() && isWordChar(s.charAt(end))) end++;
        return s.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK || c == '\'' || c == '-';
    }

    // ------------------------------------------------------------------ settings

    private DictionaryHistorySettingsData settings() {
        var s = workbench.settings() == null ? null : workbench.settings().dictionaryHistoryProperty().get();
        return (s == null ? DictionaryHistorySettingsData.defaults() : s).validated(p -> {});
    }

    private void saveSettings(DictionaryHistorySettingsData s) {
        if (workbench.settings() != null) workbench.settings().dictionaryHistoryProperty().set(s);
    }

    private static HistoryLayout layoutOf(DictionaryHistorySettingsData s) {
        return new HistoryLayout(List.of(s.showEntries(), s.showEntry(), s.showCommands()), s.widths(), s.heights());
    }

    private static String name(TextId text) {
        return text.path().getFileName() == null ? text.toString() : text.path().getFileName().toString();
    }

    // ------------------------------------------------------------------ tests

    /** The table of entries (tests). */
    TableView<EntryRow> entriesTable() {
        return entries;
    }

    /** The commands of the selected entry (tests). */
    TableView<OperationRow> commandsTable() {
        return commands;
    }
}
