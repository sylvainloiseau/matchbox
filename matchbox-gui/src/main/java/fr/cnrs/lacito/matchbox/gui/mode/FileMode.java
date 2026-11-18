package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.assist.GhostTextController;
import fr.cnrs.lacito.matchbox.gui.assist.SuggestionPanelController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.ErrorRuler;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FindBar;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.ShortcutsWindow;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.SpecialCharacterPopup;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.OverlapRows;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.OverlapsPane;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.DiagnosticsPane;
import fr.cnrs.lacito.matchbox.gui.view.DiagnosticFilter;
import fr.cnrs.lacito.matchbox.gui.view.GrammarNames;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.view.Kinds;
import fr.cnrs.lacito.matchbox.gui.view.ObjectViewRegistry;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import fr.cnrs.lacito.matchbox.gui.layout.ColumnSplitPane;
import fr.cnrs.lacito.matchbox.gui.layout.PanelToggles;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileModeLayout;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * File Mode: the files of the project, the text editor, the objects of the displayed file (tables
 * by kind, detail pane) and its parse errors, the assistant's suggestions. The mode that "show in
 * editor" switches to. Its own functions (search, assistant at the caret, insert) are in its menu.
 */
public class FileMode implements Mode {

    @FXML
    private TreeView<FileTree.Node> fileTreeView;
    @FXML
    private Label filesTitle;
    /** The position in the dictionary history of the files holding commands (1, 2, 3…). */
    private Map<java.nio.file.Path, Integer> historyRank = Map.of();
    /** The files are listed flat, in the order of a date (the project's file order). */
    private boolean flatList;
    private Map<java.nio.file.Path, java.util.Optional<java.time.Instant>> fileDates = Map.of();

    @FXML
    private TextField filterFileTextField;

    @FXML
    private InlineCssTextArea fileContentCodeArea;

    @FXML
    private javafx.scene.control.ToolBar editorToolBar;

    private FindBar findBar;
    private ErrorRuler errorRuler;
    /** "Ln 12, Col 5 (34 selected)"; a click opens Go to line. */
    private final javafx.scene.control.Hyperlink positionLink = new javafx.scene.control.Hyperlink();

    @FXML
    private TabPane objectKindTabPane;

    @FXML
    private ScrollPane objectDetailPane;

    @FXML
    private Button saveButton;

    @FXML
    private Button parseButton;

    @FXML
    private Button closeButton;

    @FXML
    private Tab parsingErrorPane;

    @FXML
    private SplitPane editorSplitPane;

    @FXML
    private ToggleButton suggestionsToggle;

    @FXML
    private TabPane objectTabPane;

    /** The three panels, arranged by {@link #layout}. */
    @FXML
    private javafx.scene.layout.StackPane layoutRoot;
    @FXML
    private javafx.scene.layout.VBox filesPane;
    @FXML
    private javafx.scene.layout.VBox editorPane;
    @FXML
    private javafx.scene.layout.VBox objectsPane;
    @FXML
    private SplitPane objectsSplitPane;

    /** Which panels are shown, their arrangement and sizes; saved in the settings. */
    private final ObjectProperty<FileModeLayout> layout = new SimpleObjectProperty<>();
    /** The splits of the current arrangement (rebuilt when it changes). */
    private final List<ColumnSplitPane> splits = new ArrayList<>();
    /** True while the splits are set from {@link #layout}. */
    private boolean syncingLayout;
    private PanelToggles<FileModeLayout> panelToggles;

    /** Overlapping readings of the whole project. */
    private OverlapsPane overlapsPane;
    private Tab overlapsTab;
    /** True when the corpus changed while the Overlaps tab was not shown. */
    private boolean overlapsStale = true;

    /** The Parsing errors and Semantic issues tables. */
    private DiagnosticsPane parsingErrors;
    private DiagnosticsPane semanticIssues;
    private Tab semanticIssuesTab;

    private final Workbench workbench;
    private final UserSettings settings;
    private final AssistController assist;
    private final ViewContext viewContext;
    private final ObjectViewRegistry viewRegistry;
    private final Node root;
    private Project project;
    private EditorController editorController;
    private GhostTextController ghostText;
    private SuggestionPanelController suggestionPanel;
    /** Expanded state of the directory nodes, by path (top directories expanded at first). */
    private final Map<java.nio.file.Path, Boolean> expanded = new HashMap<>();
    /** The dirty state of the documents shown changes the look of their directories. */
    private final List<javafx.beans.property.ReadOnlyBooleanProperty> watchedDirty = new ArrayList<>();
    private final javafx.beans.InvalidationListener dirtyRefresh = o -> fileTreeView.refresh();
    private final javafx.beans.InvalidationListener definitionListener = o -> rebuildTree();
    /** Saving the project to another directory changes the marks and the relative names. */
    private final javafx.beans.InvalidationListener directoryListener = o -> rebuildTree();
    /** True while the tree is rebuilt (its selection changes are not the user's). */
    private boolean rebuilding;
    /** True while a directory node, or a node inside one, is selected. */
    private final javafx.beans.property.BooleanProperty directorySelected = new javafx.beans.property.SimpleBooleanProperty();
    /** True while the displayed file was added alone (it can be removed from the project). */
    private final javafx.beans.property.BooleanProperty displayedRemovable = new javafx.beans.property.SimpleBooleanProperty();
    private Menu menu;
    /** What the window's commands target: the displayed file, the caret and the selected object. */
    private final ObjectProperty<Optional<Focus>> focus = new SimpleObjectProperty<>(Optional.empty());
    /** Rebuilds the tree; shows the first file when files are added while none is displayed. */
    private final ListChangeListener<TextDocument> filesAdded = change -> {
        rebuildTree();
        if (editorController.getSelectedDocument().isEmpty()) {
            Platform.runLater(() -> {
                if (editorController.getSelectedDocument().isEmpty()) {
                    firstFile(fileTreeView.getRoot()).ifPresent(this::selectItem);
                }
            });
        }
    };

    private static final KeyCombination NEXT_SUGGESTION = new KeyCodeCombination(KeyCode.F8);
    private static final KeyCombination PREVIOUS_SUGGESTION = new KeyCodeCombination(KeyCode.F8, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination APPLY_SUGGESTION = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.ALT_DOWN);
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination REDO_ALT = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

    /** One tab per kind of top-level object of the current file, in order of appearance. */
    private final Map<String, Tab> tabsByKind = new LinkedHashMap<>();
    private javafx.beans.InvalidationListener historyRefresh;
    private javafx.beans.value.ChangeListener<Object> historyTree;
    /** Object shown in the detail pane. */
    private CorpusObject displayedObject;
    /** True while the selection is propagated, to avoid feedback between table and editor. */
    private boolean propagatingSelection;

    public FileMode(Workbench workbench) {
        this.workbench = workbench;
        this.settings = workbench.settings();
        this.assist = workbench.assist();
        this.viewContext = workbench.viewContext();
        this.viewRegistry = workbench.viewRegistry();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/cnrs/lacito/matchbox/gui/mode/FileMode.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            // the window cannot work without it
            throw new UncheckedIOException("Cannot load the file mode view", e);
        }
        workbench.session().projectProperty().addListener((obs, o, n) -> setProject(n));
        if (workbench.session().project() != null) {
            setProject(workbench.session().project());
        }
    }

    @FXML
    public void initialize() {
        this.editorController = new EditorController(fileContentCodeArea, workbench);
        editorController.setTextActions(PluginManager.loadServices(TextActionProvider.class));
        setupEditorArea();
        setupFileListView();
        setupObjectsTab();
        setupProblemTabs();
        setupOverlapsTab();
        setupAssistant();
        setupFocus();
        setupLayout();
        this.menu = buildMenu();
        saveButton.disableProperty().bind(editorController.getCurrentTextCouldBeWrittenDownProperty().not());
        // a file of a directory is excluded in the directory settings, not removed alone
        closeButton.disableProperty().bind(editorController.getClosableProperty().not().or(displayedRemovable.not()));
        closeButton.setTooltip(new Tooltip("Remove the displayed file from the project (files added alone; "
            + "exclude a file of a directory in its settings)"));
        parseButton.disableProperty().bind(editorController.getCurrentTextCouldBeParsedAgainProperty().not());
    }

    /**
     * The editor in a scroll pane, the error ruler beside it, the find bar above, an empty state
     * when no file is displayed, and the caret position in the toolbar.
     */
    private void setupEditorArea() {
        findBar = new FindBar(editorController, workbench.status()::info);
        var scroll = new org.fxmisc.flowless.VirtualizedScrollPane<>(fileContentCodeArea);
        scroll.setId("editorScrollPane");
        errorRuler = new ErrorRuler(line -> goToLine(line + 1));
        javafx.scene.layout.HBox.setHgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        var withRuler = new javafx.scene.layout.HBox(scroll, errorRuler);
        Label empty = new Label("Choose a file in the list, or add files with Current project › Add file…");
        empty.setId("editorEmptyState");
        empty.setStyle("-fx-text-fill: #888888; -fx-font-size: 1.1em;");
        empty.setMouseTransparent(true);
        empty.visibleProperty().bind(editorController.getClosableProperty().not());
        var stack = new javafx.scene.layout.StackPane(withRuler, empty);
        javafx.scene.layout.VBox.setVgrow(stack, javafx.scene.layout.Priority.ALWAYS);
        var editorBox = new javafx.scene.layout.VBox(findBar, stack);
        int index = editorSplitPane.getItems().indexOf(fileContentCodeArea);
        editorSplitPane.getItems().set(index, editorBox);
        editorController.markersProperty().addListener((obs, o, n) -> errorRuler.setMarkers(n, editorController.lineCount()));

        positionLink.setId("caretPosition");
        positionLink.setTooltip(new Tooltip("Go to line… (" + shortcut(KeyCode.L).getDisplayText() + ")"));
        positionLink.setOnAction(e -> {
            positionLink.setVisited(false);
            goToLine();
        });
        positionLink.visibleProperty().bind(editorController.getClosableProperty());
        var spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        editorToolBar.getItems().addAll(spacer, positionLink);
        fileContentCodeArea.caretPositionProperty().addListener((obs, o, n) -> updatePosition());
        fileContentCodeArea.selectionProperty().addListener((obs, o, n) -> updatePosition());
    }

    private void updatePosition() {
        int selected = fileContentCodeArea.getSelection().getLength();
        positionLink.setText("Ln " + (fileContentCodeArea.getCurrentParagraph() + 1) + ", Col "
            + (fileContentCodeArea.getCaretColumn() + 1) + (selected > 0 ? " (" + selected + " selected)" : ""));
    }

    /** Asks for a line number and goes there (recorded for Back). */
    public void goToLine() {
        if (editorController.getSelectedDocument().isEmpty()) return;
        int count = editorController.lineCount();
        var dialog = new javafx.scene.control.TextInputDialog(String.valueOf(fileContentCodeArea.getCurrentParagraph() + 1));
        dialog.initOwner(window());
        dialog.setTitle("Go to line");
        dialog.setHeaderText(null);
        dialog.setContentText("Line (1–" + count + "):");
        dialog.showAndWait().ifPresent(text -> {
            try {
                goToLine(Integer.parseInt(text.strip()));
            } catch (NumberFormatException e) {
                workbench.status().warn("Not a line number: " + text);
            }
        });
    }

    /** Goes to the start of a line (1-based, clamped), recorded for Back. */
    public void goToLine(int line) {
        editorController.getSelectedDocument().ifPresent(d ->
            workbench.navigator().showInEditor(Focus.at(d.getId(), editorController.offsetOfLine(line))));
    }

    /** Opens the special character palette at the caret. */
    public void insertSpecialCharacter() {
        if (editorController.getSelectedDocument().isEmpty()) return;
        var bounds = fileContentCodeArea.getCaretBounds();
        double x = bounds.map(javafx.geometry.Bounds::getMinX).orElseGet(() -> fileContentCodeArea.localToScreen(0, 0).getX() + 40);
        double y = bounds.map(javafx.geometry.Bounds::getMaxY).orElseGet(() -> fileContentCodeArea.localToScreen(0, 0).getY() + 40);
        SpecialCharacterPopup.show(window(), x, y, settings.editorProperty().get().recentCharacters(), c -> {
            editorController.insertText(c);
            settings.editorProperty().set(settings.editorProperty().get().withRecentCharacter(c));
        });
    }

    private void showShortcuts() {
        javafx.scene.control.MenuBar bar = fileContentCodeArea.getScene() == null ? null
            : (javafx.scene.control.MenuBar) fileContentCodeArea.getScene().lookup(".menu-bar");
        ShortcutsWindow.show(window(), bar);
    }

    /** The platform's shortcut key with a key (and other modifiers). */
    private static KeyCombination shortcut(KeyCode code, KeyCombination.Modifier... others) {
        KeyCombination.Modifier[] modifiers = java.util.Arrays.copyOf(others, others.length + 1);
        modifiers[others.length] = KeyCombination.SHORTCUT_DOWN;
        return new KeyCodeCombination(code, modifiers);
    }

    private void changeOptions(java.util.function.UnaryOperator<fr.cnrs.lacito.matchbox.settings.EditorSettingsData> change) {
        settings.editorProperty().set(change.apply(settings.editorProperty().get()));
    }

    /** A check item showing and changing a boolean option of the editor. */
    private CheckMenuItem option(String text,
                                 java.util.function.Predicate<fr.cnrs.lacito.matchbox.settings.EditorSettingsData> value,
                                 java.util.function.BiFunction<fr.cnrs.lacito.matchbox.settings.EditorSettingsData, Boolean,
                                     fr.cnrs.lacito.matchbox.settings.EditorSettingsData> with) {
        CheckMenuItem item = new CheckMenuItem(text);
        item.setSelected(value.test(settings.editorProperty().get()));
        settings.editorProperty().addListener((obs, o, n) -> item.setSelected(value.test(n)));
        item.setOnAction(e -> changeOptions(opts -> with.apply(opts, item.isSelected())));
        return item;
    }

    /** Shows the files of a new project (none if null); nothing is displayed until a file is chosen. */
    private void setProject(Project project) {
        if (this.project != null) {
            this.project.getDerivedFileList().removeListener(filesAdded);
            this.project.definitionRevisionProperty().removeListener(definitionListener);
            this.project.directoryProperty().removeListener(directoryListener);
        }
        this.project = project;
        editorController.setProject(project);
        expanded.clear();
        if (project != null) {
            project.getDerivedFileList().addListener(filesAdded);
            project.definitionRevisionProperty().addListener(definitionListener);
            project.directoryProperty().addListener(directoryListener);
        }
        // no project: an empty tree (the start page covers the mode)
        rebuildTree();
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    @Override
    public Node root() {
        return root;
    }

    @Override
    public ObservableValue<Optional<Focus>> focusProperty() {
        return focus;
    }

    @Override
    public Optional<Menu> menu() {
        return Optional.of(menu);
    }

    /** The toggles of the three panels, at the right end of the main toolbar. */
    @Override
    public Optional<Node> toolBarItems() {
        return Optional.of(panelToggles.toolBarItems());
    }

    /** Opens the file of the focus, then selects its object or range. */
    @Override
    public boolean reveal(Focus target) {
        if (project == null) return false;
        // a place is shown in the editor; an object also in the Objects panel if the editor is hidden
        if (target.object().isEmpty() || !layout.get().isShown(FileModeLayout.Panel.OBJECTS)) {
            ensureShown(FileModeLayout.Panel.EDITOR);
        }
        if (!isDisplayed(target)) {
            project.document(target.text()).ifPresent(d -> {
                // the file may be hidden by the filter of the tree
                if (find(fileTreeView.getRoot(), d.getFilePath()).isEmpty()) filterFileTextField.clear();
                find(fileTreeView.getRoot(), d.getFilePath()).ifPresent(this::selectItem);
            });
        }
        if (!isDisplayed(target)) return false;
        if (target.object().isPresent()) {
            editorController.selectInEditor(target.object().get().getTextCoordinate());
            editorController.selectedObjectProperty().set(target.object().get());
        } else {
            editorController.selectRange(target.start(), target.end());
        }
        return true;
    }

    private boolean isDisplayed(Focus target) {
        return editorController.getSelectedDocument().map(d -> d.getId().equals(target.text())).orElse(false);
    }

    /** The focus follows the displayed file, the caret, the selection of the editor and the selected object. */
    private void setupFocus() {
        fileContentCodeArea.caretPositionProperty().addListener((obs, o, n) -> updateFocus());
        fileContentCodeArea.selectionProperty().addListener((obs, o, n) -> updateFocus());
        editorController.selectedObjectProperty().addListener((obs, o, n) -> updateFocus());
        editorController.getClosableProperty().addListener((obs, o, n) -> updateFocus());
    }

    /**
     * The Dictionary History of the entry at the caret: the entry of the command at the caret, or the
     * entry whose form is the word at the caret (a word of an IGT, of a note…).
     */
    private void showHistoryAtCaret() {
        updateFocus();
        focus.get().ifPresent(f -> {
            if (!workbench.navigator().browseIn(DictionaryHistoryMode.ID, f)) {
                workbench.status().info("No entry of the dictionary history at the caret");
            }
        });
    }

    private void updateFocus() {
        Optional<TextDocument> document = editorController.getSelectedDocument();
        if (document.isEmpty()) {
            focus.set(Optional.empty());
            return;
        }
        var selection = fileContentCodeArea.getSelection();
        int start = selection.getLength() > 0 ? selection.getStart() : fileContentCodeArea.getCaretPosition();
        int end = selection.getLength() > 0 ? selection.getEnd() : start;
        CorpusObject object = editorController.selectedObjectProperty().get();
        Optional<CorpusObject> selected = object != null && object.getTextCoordinate().text().equals(document.get().getId())
            ? Optional.of(object) : Optional.empty();
        focus.set(Optional.of(new Focus(document.get().getId(), start, end, selected)));
    }

    // ------------------------------------------------------------------
    // Layout: the three panels (plan FILE_MODE_LAYOUT_PLAN.md)
    // ------------------------------------------------------------------

    private static final List<PanelToggles.Panel> PANELS = List.of(
        new PanelToggles.Panel("Files", "Show or hide the list of files", "fileListToggle", panelKey(1)),
        new PanelToggles.Panel("Editor", "Show or hide the editor", "fileEditorToggle", panelKey(2)),
        new PanelToggles.Panel("Objects", "Show or hide the objects and problems of the file", "fileObjectsToggle",
            panelKey(3)));

    private static KeyCombination panelKey(int n) {
        return PanelToggles.accelerator(n);
    }

    private void setupLayout() {
        layout.set(FileModeLayout.of(settings.fileModeProperty().get()));
        javafx.scene.control.SplitPane.setResizableWithParent(filesPane, false);
        arrange(layout.get());
        layout.addListener((obs, o, n) -> {
            if (n.arrangement() != o.arrangement()) {
                arrange(n);
            } else {
                syncSplits(n);
            }
            settings.fileModeProperty().set(n.into(settings.fileModeProperty().get()));
        });
        panelToggles = new PanelToggles<>(PANELS, layout, FileModeLayout.panels());

        var fileMode = settings.fileModeProperty().get();
        objectsSplitPane.setDividerPositions(fileMode == null ? 0.55 : fileMode.validated(p -> {}).objectsSplit());
        rememberDivider(objectsSplitPane, v -> settings.fileModeProperty().set(
            settings.fileModeProperty().get().withObjectsSplit(v)));
        rememberDivider(editorSplitPane, v -> {
            if (editorSplitPane.getItems().size() == 2) {
                settings.fileModeProperty().set(settings.fileModeProperty().get().withSuggestionsSplit(v));
            }
        });
    }

    /** Puts the panels in the splits of the layout's arrangement. */
    private void arrange(FileModeLayout l) {
        for (ColumnSplitPane split : splits) split.node().getItems().clear();
        splits.clear();
        javafx.geometry.Orientation horizontal = javafx.geometry.Orientation.HORIZONTAL;
        ColumnSplitPane outer;
        if (l.arrangement() == FileModeLayout.Arrangement.COLUMNS) {
            outer = new ColumnSplitPane(horizontal, List.of(filesPane, editorPane, objectsPane), l.outer());
            outer.setFocusOrder(List.of(1, 2, 0), List.of(fileTreeView, fileContentCodeArea, objectTabPane));
            splits.add(outer);
        } else {
            ColumnSplitPane stack = new ColumnSplitPane(javafx.geometry.Orientation.VERTICAL,
                List.of(editorPane, objectsPane), l.inner());
            stack.setFocusOrder(List.of(0, 1), List.of(fileContentCodeArea, objectTabPane));
            stack.node().setId("fileModeStack");
            outer = new ColumnSplitPane(horizontal, List.of(filesPane, stack.node()), l.outer());
            outer.setFocusOrder(List.of(1, 0), List.of(fileTreeView, fileContentCodeArea));
            splits.add(outer);
            splits.add(stack);
            stack.layoutProperty().addListener((obs, o, n) -> {
                if (syncingLayout) return;
                layout.set(layout.get().withInner(n));
                syncSplits(layout.get());
            });
        }
        outer.node().setId("fileModeSplit");
        outer.layoutProperty().addListener((obs, o, n) -> {
            if (syncingLayout) return;
            layout.set(layout.get().withOuter(n));
            syncSplits(layout.get());
        });
        layoutRoot.getChildren().setAll(outer.node());
    }

    /** Sets the splits from the layout (same arrangement). */
    private void syncSplits(FileModeLayout l) {
        syncingLayout = true;
        try {
            splits.get(0).layoutProperty().set(l.outer());
            if (splits.size() > 1) splits.get(1).layoutProperty().set(l.inner());
        } finally {
            syncingLayout = false;
        }
    }

    /** Shows a panel if it is hidden (a command or a navigation needs it). */
    public void ensureShown(FileModeLayout.Panel panel) {
        layout.set(layout.get().withShown(panel, true));
    }

    /** The layout of the panels (for tests and the Panels menu). */
    public ObjectProperty<FileModeLayout> layoutProperty() {
        return layout;
    }

    /** Calls {@code save} with the first divider position when the user releases it. */
    private static void rememberDivider(SplitPane split, java.util.function.DoubleConsumer save) {
        split.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            Node n = e.getTarget() instanceof Node t ? t : null;
            while (n != null && n != split) {
                if (n.getStyleClass().contains("split-pane-divider") && n.getParent() == split) {
                    double[] positions = split.getDividerPositions();
                    if (positions.length > 0) save.accept(positions[0]);
                    return;
                }
                n = n.getParent();
            }
        });
    }

    /** File Mode › Panels: the panels, presets, arrangement and sizes. */
    private Menu buildPanelsMenu() {
        Menu panels = new Menu("Panels");
        panels.getItems().addAll(panelToggles.menuItems());
        MenuItem all = item("Show all panels", () -> layout.set(layout.get().withPreset(FileModeLayout.Preset.ALL)));
        all.setAccelerator(panelKey(0));
        MenuItem writing = item("Writing (editor only)",
            () -> layout.set(layout.get().withPreset(FileModeLayout.Preset.WRITING)));
        MenuItem reviewing = item("Reviewing (editor and objects)",
            () -> layout.set(layout.get().withPreset(FileModeLayout.Preset.REVIEWING)));
        MenuItem browsing = item("Browsing (files and objects)",
            () -> layout.set(layout.get().withPreset(FileModeLayout.Preset.BROWSING)));
        javafx.scene.control.ToggleGroup arrangement = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioMenuItem beside = new javafx.scene.control.RadioMenuItem("Objects beside the editor");
        beside.setId("arrangementColumns");
        javafx.scene.control.RadioMenuItem under = new javafx.scene.control.RadioMenuItem("Objects under the editor");
        under.setId("arrangementStacked");
        beside.setToggleGroup(arrangement);
        under.setToggleGroup(arrangement);
        Runnable syncArrangement = () -> {
            boolean columns = layout.get().arrangement() == FileModeLayout.Arrangement.COLUMNS;
            beside.setSelected(columns);
            under.setSelected(!columns);
        };
        syncArrangement.run();
        layout.addListener((obs, o, n) -> syncArrangement.run());
        beside.setOnAction(e -> layout.set(layout.get().withArrangement(FileModeLayout.Arrangement.COLUMNS)));
        under.setOnAction(e -> layout.set(layout.get().withArrangement(FileModeLayout.Arrangement.STACKED)));
        MenuItem reset = item("Reset panel sizes", () -> {
            layout.set(layout.get().withDefaultSizes());
            FileModeLayout fresh = layout.get();
            // same layout value when the sizes were already the defaults: apply it anyway
            syncSplits(fresh);
            var d = fr.cnrs.lacito.matchbox.settings.FileModeSettingsData.defaults();
            objectsSplitPane.setDividerPositions(d.objectsSplit());
            settings.fileModeProperty().set(settings.fileModeProperty().get()
                .withObjectsSplit(d.objectsSplit()).withSuggestionsSplit(d.suggestionsSplit()));
            if (editorSplitPane.getItems().size() == 2) editorSplitPane.setDividerPositions(d.suggestionsSplit());
        });
        reset.setId("resetPanelSizes");
        panels.getItems().addAll(new SeparatorMenuItem(), all, writing, reviewing, browsing,
            new SeparatorMenuItem(), beside, under, new SeparatorMenuItem(), reset);
        return panels;
    }

    // ------------------------------------------------------------------
    // The File Mode menu
    // ------------------------------------------------------------------

    private Menu buildMenu() {
        MenuItem search = item("Find…", this::openSearch);
        search.setAccelerator(shortcut(KeyCode.F));
        MenuItem findNext = item("Find next", () -> findBar.next());
        findNext.setAccelerator(shortcut(KeyCode.G));
        MenuItem findPrevious = item("Find previous", () -> findBar.previous());
        findPrevious.setAccelerator(shortcut(KeyCode.G, KeyCombination.SHIFT_DOWN));
        MenuItem replace = item("Replace…", () -> findBar.open(true));
        replace.setAccelerator(EditorKeymap.Platform.current() == EditorKeymap.Platform.MAC
            ? shortcut(KeyCode.F, KeyCombination.ALT_DOWN) : shortcut(KeyCode.H));
        MenuItem goToLine = item("Go to line…", this::goToLine);
        goToLine.setAccelerator(shortcut(KeyCode.L));
        MenuItem historyAtCaret = item("Show history of entry at caret", this::showHistoryAtCaret);
        historyAtCaret.setAccelerator(shortcut(KeyCode.H, KeyCombination.SHIFT_DOWN));
        MenuItem specialCharacter = item("Insert special character…", this::insertSpecialCharacter);
        specialCharacter.setAccelerator(shortcut(KeyCode.I, KeyCombination.SHIFT_DOWN));

        MenuItem duplicate = item("Duplicate line", () -> editorController.duplicateLines());
        duplicate.setAccelerator(shortcut(KeyCode.D));
        MenuItem deleteLine = item("Delete line", () -> editorController.deleteLines());
        deleteLine.setAccelerator(shortcut(KeyCode.K, KeyCombination.SHIFT_DOWN));
        MenuItem moveUp = item("Move line up", () -> editorController.moveLinesUp());
        moveUp.setAccelerator(new KeyCodeCombination(KeyCode.UP, KeyCombination.ALT_DOWN));
        MenuItem moveDown = item("Move line down", () -> editorController.moveLinesDown());
        moveDown.setAccelerator(new KeyCodeCombination(KeyCode.DOWN, KeyCombination.ALT_DOWN));
        Menu lines = new Menu("Lines", null, duplicate, deleteLine, moveUp, moveDown);

        MenuItem zoomIn = item("Zoom in", () -> changeOptions(o -> o.withFontSize(o.fontSize() + 1)));
        zoomIn.setAccelerator(shortcut(KeyCode.EQUALS));
        MenuItem zoomOut = item("Zoom out", () -> changeOptions(o -> o.withFontSize(o.fontSize() - 1)));
        zoomOut.setAccelerator(shortcut(KeyCode.MINUS));
        MenuItem actualSize = item("Actual size",
            () -> changeOptions(o -> o.withFontSize(fr.cnrs.lacito.matchbox.settings.EditorSettingsData.DEFAULT_FONT_SIZE)));
        actualSize.setAccelerator(shortcut(KeyCode.DIGIT0));
        Menu editorOptions = new Menu("Editor", null,
            option("Wrap lines", o -> o.wrapLines(), (o, v) -> o.withWrapLines(v)),
            option("Show line numbers", o -> o.lineNumbers(), (o, v) -> o.withLineNumbers(v)),
            option("Highlight current line", o -> o.highlightCurrentLine(), (o, v) -> o.withHighlightCurrentLine(v)),
            option("Show invisible characters", o -> o.showInvisibles(), (o, v) -> o.withShowInvisibles(v)),
            option("Auto-indent", o -> o.autoIndent(), (o, v) -> o.withAutoIndent(v)),
            new SeparatorMenuItem(), zoomIn, zoomOut, actualSize);
        MenuItem shortcuts = item("Keyboard shortcuts", this::showShortcuts);
        MenuItem parse = item("Parse file", this::handleParseText);
        MenuItem showOverlaps = item("Show overlaps", this::showOverlaps);
        MenuItem showParsingErrors = item("Show parsing errors", this::showParsingErrors);
        MenuItem showSemanticIssues = item("Show semantic issues", this::showSemanticIssues);
        MenuItem nextProblem = item("Next problem", () -> nextProblemInFile(true));
        nextProblem.setAccelerator(new KeyCodeCombination(KeyCode.F8));
        MenuItem previousProblem = item("Previous problem", () -> nextProblemInFile(false));
        previousProblem.setAccelerator(new KeyCodeCombination(KeyCode.F8, KeyCombination.SHIFT_DOWN));
        MenuItem nextInProject = item("Next problem in project", () -> nextProblemInProject(true));
        nextInProject.setAccelerator(new KeyCodeCombination(KeyCode.F8, KeyCombination.ALT_DOWN));
        MenuItem previousInProject = item("Previous problem in project", () -> nextProblemInProject(false));
        previousInProject.setAccelerator(new KeyCodeCombination(KeyCode.F8, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN));
        MenuItem fixAtCaret = item("Apply fix at caret", this::applyFixAtCaret);
        fixAtCaret.setAccelerator(shortcut(KeyCode.PERIOD));
        Menu problemsMenu = new Menu("Problems", null, showParsingErrors, showSemanticIssues, showOverlaps,
            new SeparatorMenuItem(), nextProblem, previousProblem, nextInProject, previousInProject,
            new SeparatorMenuItem(), fixAtCaret);
        MenuItem suggest = item("Suggest completion (Ctrl+Space)", this::requestCompletion);
        CheckMenuItem inline = new CheckMenuItem("Inline suggestions");
        inline.setSelected(assist.data().inlineEnabled());
        settings.assistProperty().addListener((obs, o, n) -> inline.setSelected(n.inlineEnabled()));
        inline.setOnAction(e -> settings.assistProperty().set(
            settings.assistProperty().get().withInlineEnabled(inline.isSelected())));
        MenuItem explain = item("Explain error at cursor", this::explainErrorAtCaret);
        MenuItem reviewBlock = item("Review current block", () -> review("Current block"));
        MenuItem reviewSelection = item("Review selection", () -> review("Selection"));
        MenuItem reviewFile = item("Review whole file", () -> review("Whole file"));
        CheckMenuItem panel = new CheckMenuItem("Suggestions panel");
        panel.selectedProperty().bindBidirectional(suggestionsToggle.selectedProperty());

        MenuItem directorySettings = item("Directory settings…",
            () -> selectedDirectory().ifPresent(workbench.files()::editSettings));
        MenuItem removeDirectory = item("Remove directory from project",
            () -> selectedDirectory().ifPresent(workbench.files()::removeDirectory));
        directorySettings.disableProperty().bind(directorySelected.not());
        removeDirectory.disableProperty().bind(directorySelected.not());

        // commands acting at the caret show the editor if it is hidden
        for (MenuItem i : List.of(search, findNext, findPrevious, replace, goToLine, specialCharacter, duplicate,
                deleteLine, moveUp, moveDown, nextProblem, previousProblem, fixAtCaret, suggest, explain, reviewBlock,
                reviewSelection, reviewFile)) {
            var action = i.getOnAction();
            i.setOnAction(e -> {
                ensureShown(FileModeLayout.Panel.EDITOR);
                action.handle(e);
            });
        }
        Menu result = new Menu("File Mode", null,
            buildPanelsMenu(),
            new SeparatorMenuItem(),
            search, findNext, findPrevious, replace, goToLine, specialCharacter, parse, historyAtCaret,
            new SeparatorMenuItem(),
            problemsMenu,
            new SeparatorMenuItem(),
            lines, editorOptions,
            new SeparatorMenuItem(),
            directorySettings, removeDirectory,
            new SeparatorMenuItem(),
            suggest, inline, explain, reviewBlock, reviewSelection, reviewFile, panel);
        // everything acts on the displayed file
        var noFile = editorController.getClosableProperty().not();
        result.getItems().addAll(new SeparatorMenuItem(), shortcuts);
        for (MenuItem i : List.of(search, findNext, findPrevious, replace, goToLine, specialCharacter, lines,
                suggest, explain, reviewBlock, reviewFile)) {
            i.disableProperty().bind(noFile);
        }
        parse.disableProperty().bind(editorController.getCurrentTextCouldBeParsedAgainProperty().not());
        result.setOnShowing(e -> reviewSelection.setDisable(editorController.getSelectedDocument().isEmpty()
            || fileContentCodeArea.getSelection().getLength() == 0));
        return result;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** Find (and replace) in the displayed file, in the find bar above the editor. */
    public void openSearch() {
        if (editorController.getSelectedDocument().isPresent()) findBar.open(false);
    }

    // ------------------------------------------------------------------
    // Assistant
    // ------------------------------------------------------------------

    private void setupAssistant() {
        ghostText = new GhostTextController(editorController, assist, workbench.status()::info);
        suggestionPanel = new SuggestionPanelController(editorController, assist);

        // the panel's visibility is a setting
        suggestionsToggle.setSelected(assist.data().suggestionPanelVisible());
        showSuggestionPanel(suggestionsToggle.isSelected());
        suggestionsToggle.selectedProperty().addListener((obs, o, visible) -> {
            showSuggestionPanel(visible);
            if (visible != assist.data().suggestionPanelVisible()) {
                settings.assistProperty().set(assist.data().withSuggestionPanelVisible(visible));
            }
        });
        settings.assistProperty().addListener((obs, o, n) -> suggestionsToggle.setSelected(n.suggestionPanelVisible()));

        fileContentCodeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (NEXT_SUGGESTION.match(e) || PREVIOUS_SUGGESTION.match(e)) {
                e.consume();
                setSuggestionPanelVisible(true);
                suggestionPanel.next(!e.isShiftDown());
            } else if (APPLY_SUGGESTION.match(e)) {
                e.consume();
                suggestionPanel.applySelected();
            }
        });

        MenuItem suggest = new MenuItem("Suggest completion (Ctrl+Space)");
        suggest.setOnAction(e -> requestCompletion());
        MenuItem explain = new MenuItem("Explain error here");
        explain.setOnAction(e -> explainErrorAtCaret());
        MenuItem reviewSelection = new MenuItem("Review selection with AI");
        reviewSelection.setOnAction(e -> review("Selection"));
        MenuItem reviewBlock = new MenuItem("Review current block with AI");
        reviewBlock.setOnAction(e -> review("Current block"));
        MenuItem play = new MenuItem("Play media segment at caret (⇧⌘Space)");
        play.setOnAction(e -> editorController.playSegmentAtCaret());
        ContextMenu contextMenu = new ContextMenu(suggest, explain, new SeparatorMenuItem(), reviewBlock, reviewSelection,
            new SeparatorMenuItem(), play);
        contextMenu.setOnShowing(e -> {
            explain.setDisable(editorController.errorAt(fileContentCodeArea.getCaretPosition()).isEmpty());
            reviewSelection.setDisable(fileContentCodeArea.getSelection().getLength() == 0);
        });
        fileContentCodeArea.setContextMenu(contextMenu);
    }

    private void showSuggestionPanel(boolean visible) {
        var items = editorSplitPane.getItems();
        if (visible && !items.contains(suggestionPanel.getRoot())) {
            items.add(suggestionPanel.getRoot());
            var fileMode = settings.fileModeProperty().get();
            editorSplitPane.setDividerPositions(fileMode == null ? 0.72 : fileMode.validated(p -> {}).suggestionsSplit());
        } else if (!visible) {
            items.remove(suggestionPanel.getRoot());
        }
    }

    public void setSuggestionPanelVisible(boolean visible) {
        suggestionsToggle.setSelected(visible);
    }

    /** Asks for an inline suggestion at the cursor. */
    public void requestCompletion() {
        fileContentCodeArea.requestFocus();
        ghostText.request(true);
    }

    public void explainErrorAtCaret() {
        setSuggestionPanelVisible(true);
        suggestionPanel.explainErrorAtCaret();
    }

    /** Reviews "Current block", "Selection" or "Whole file". */
    public void review(String what) {
        setSuggestionPanelVisible(true);
        suggestionPanel.review(what);
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    private void setupFileListView() {
        fileTreeView.setRoot(new TreeItem<>());
        fileTreeView.setShowRoot(false);
        filterFileTextField.textProperty().addListener((obs, oldValue, newValue) -> rebuildTree());
        fileTreeView.setCellFactory(tree -> new FileTreeCell());
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateSelectionState();
            if (rebuilding) return;
            if (newVal != null && newVal.getValue() instanceof FileTree.FileNode f) {
                editorController.selectEditedFile(f.path());
            } else if (newVal == null) {
                editorController.clearEditor();
            }
            // a directory selected: the editor keeps its file
            updateFocus();
        });
    }

    /** Builds the tree again from the project, keeping the expanded nodes and the selection. */
    private void rebuildTree() {
        if (fileTreeView == null) return;
        watchedDirty.forEach(p -> p.removeListener(dirtyRefresh));
        watchedDirty.clear();
        String filter = filterFileTextField.getText();
        boolean filtering = filter != null && !filter.isBlank();
        TreeItem<FileTree.Node> selected = fileTreeView.getSelectionModel().getSelectedItem();
        java.nio.file.Path selectedPath = selected == null || selected.getValue() == null ? null : selected.getValue().path();
        List<TreeItem<FileTree.Node>> items = new ArrayList<>();
        updateHistoryRank();
        flatList = project != null && project.getFileOrder() != fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder.PATH;
        if (filesTitle != null) {
            filesTitle.setText(!flatList ? "Files" : project.getFileOrder()
                == fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder.MODIFIED
                ? "Files — ordered by modification date" : "Files — ordered by header date");
            filesTitle.setTooltip(project == null ? null : new Tooltip(project.directory()
                .map(d -> "Project directory: " + d + "\nFiles outside it are marked with !")
                .orElse("Save the project to give it a project directory (the directory of the project file)")));
        }
        if (project != null && flatList) {
            // the files in the project's order, flat: a tree cannot show an order by date
            List<FileTree.FileNode> files = new ArrayList<>();
            collectFiles(FileTree.build(project, filter), files);
            Map<java.nio.file.Path, java.util.Optional<java.time.Instant>> dates = new java.util.HashMap<>();
            var order = project.getFileOrder();
            for (FileTree.FileNode f : files) dates.put(f.path(), dateOf(order, f.path()));
            fileDates = dates;
            var comparator = order.comparator(dates::get);
            files.sort((a, b) -> comparator.compare(a.path(), b.path()));
            for (FileTree.FileNode f : files) items.add(new TreeItem<>(f));
            project.getDerivedFileList().forEach(d -> {
                watchedDirty.add(d.getCouldBeWrittenBooleanProperty());
                d.getCouldBeWrittenBooleanProperty().addListener(dirtyRefresh);
            });
        } else if (project != null) {
            for (FileTree.Entry e : FileTree.build(project, filter)) items.add(item(e, filtering));
            project.getDerivedFileList().forEach(d -> {
                watchedDirty.add(d.getCouldBeWrittenBooleanProperty());
                d.getCouldBeWrittenBooleanProperty().addListener(dirtyRefresh);
            });
        }
        rebuilding = true;
        try {
            fileTreeView.getRoot().getChildren().setAll(items);
            if (selectedPath != null) {
                find(fileTreeView.getRoot(), selectedPath).ifPresentOrElse(this::selectItem,
                    () -> fileTreeView.getSelectionModel().clearSelection());
            }
        } finally {
            rebuilding = false;
        }
        updateSelectionState();
    }

    /** The position of each file holding commands in the history of the dictionary. */
    private void updateHistoryRank() {
        Map<java.nio.file.Path, Integer> rank = new java.util.HashMap<>();
        List<fr.cnrs.lacito.matchbox.corpus.TextId> ordered = workbench.history().orderedTexts();
        for (int i = 0; i < ordered.size(); i++) rank.put(ordered.get(i).path(), i + 1);
        historyRank = rank;
    }

    private static void collectFiles(List<FileTree.Entry> entries, List<FileTree.FileNode> out) {
        for (FileTree.Entry e : entries) {
            if (e.node() instanceof FileTree.FileNode f) out.add(f);
            collectFiles(e.children(), out);
        }
    }

    /** The date of a file for the project's order: on disk, or the date of its header. */
    private java.util.Optional<java.time.Instant> dateOf(fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder order,
                                                       java.nio.file.Path path) {
        return switch (order) {
            case PATH -> java.util.Optional.empty();
            case MODIFIED -> {
                try {
                    yield java.util.Optional.of(java.nio.file.Files.getLastModifiedTime(path).toInstant());
                } catch (Exception e) {
                    yield java.util.Optional.empty();
                }
            }
            case HEADER_DATE -> workbench.session().corpus().text(fr.cnrs.lacito.matchbox.corpus.TextId.of(path))
                .flatMap(t -> fr.cnrs.lacito.matchbox.model.project.ProjectFileOrder.parseHeaderDate(t.properties().get("date")));
        };
    }

    private TreeItem<FileTree.Node> item(FileTree.Entry entry, boolean filtering) {
        TreeItem<FileTree.Node> item = new TreeItem<>(entry.node());
        for (FileTree.Entry child : entry.children()) item.getChildren().add(item(child, filtering));
        if (!(entry.node() instanceof FileTree.FileNode)) {
            java.nio.file.Path path = entry.node().path();
            if (filtering) {
                item.setExpanded(true);
            } else {
                item.setExpanded(expanded.computeIfAbsent(path, p -> entry.node() instanceof FileTree.TopDirectory));
                item.expandedProperty().addListener((obs, o, n) -> {
                    if (!rebuilding && (filterFileTextField.getText() == null || filterFileTextField.getText().isBlank())) {
                        expanded.put(path, n);
                    }
                });
            }
        }
        return item;
    }

    /**
     * Shows the file list and selects the node of a file or directory of the project (e.g. an entry
     * outside the project directory); false if it is not in the list (filtered out).
     */
    public boolean selectInFileList(java.nio.file.Path path) {
        ensureShown(FileModeLayout.Panel.FILES);
        Optional<TreeItem<FileTree.Node>> item = find(fileTreeView.getRoot(), path);
        item.ifPresent(i -> {
            selectItem(i);
            fileTreeView.requestFocus();
        });
        return item.isPresent();
    }

    /** Selects a node, expanding its ancestors, and scrolls to it. */
    private void selectItem(TreeItem<FileTree.Node> item) {
        for (TreeItem<FileTree.Node> p = item.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
        fileTreeView.getSelectionModel().select(item);
        int row = fileTreeView.getRow(item);
        if (row >= 0) fileTreeView.scrollTo(row);
    }

    private static Optional<TreeItem<FileTree.Node>> find(TreeItem<FileTree.Node> item, java.nio.file.Path path) {
        if (item == null) return Optional.empty();
        if (item.getValue() != null && item.getValue().path().equals(path)) return Optional.of(item);
        for (TreeItem<FileTree.Node> child : item.getChildren()) {
            Optional<TreeItem<FileTree.Node>> found = find(child, path);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static Optional<TreeItem<FileTree.Node>> firstFile(TreeItem<FileTree.Node> item) {
        if (item == null) return Optional.empty();
        if (item.getValue() instanceof FileTree.FileNode) return Optional.of(item);
        for (TreeItem<FileTree.Node> child : item.getChildren()) {
            Optional<TreeItem<FileTree.Node>> found = firstFile(child);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** The directory of the project the selected node belongs to (itself, or its top directory). */
    public Optional<java.nio.file.Path> selectedDirectory() {
        TreeItem<FileTree.Node> item = fileTreeView.getSelectionModel().getSelectedItem();
        if (item == null || item.getValue() == null) return Optional.empty();
        return switch (item.getValue()) {
            case FileTree.TopDirectory d -> Optional.of(d.path());
            case FileTree.SubDirectory d -> Optional.of(d.top());
            case FileTree.FileNode f -> f.directory();
        };
    }

    private void updateSelectionState() {
        directorySelected.set(selectedDirectory().isPresent());
        displayedRemovable.set(editorController != null
            && editorController.getSelectedDocument().map(workbench.files()::isRemovable).orElse(false));
    }

    /**
     * A node of the file tree: a file (its name, "* " and bold italic when unsaved; a file added
     * alone also shows its directory) or a directory (name, number of files, bold when a file
     * below is unsaved), with its context menu.
     */
    private final class FileTreeCell extends TreeCell<FileTree.Node> {
        @Override
        protected void updateItem(FileTree.Node node, boolean empty) {
            super.updateItem(node, empty);
            textProperty().unbind();
            styleProperty().unbind();
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            if (empty || node == null) {
                setText(null);
                setStyle("");
                return;
            }
            switch (node) {
                case FileTree.FileNode f -> {
                    var dirty = f.document().getCouldBeWrittenBooleanProperty();
                    Integer rank = historyRank.get(f.path());
                    String name = (rank == null ? "" : rank + " · ") + f.name();
                    textProperty().bind(Bindings.when(dirty).then("* " + name).otherwise(name));
                    styleProperty().bind(Bindings.when(dirty)
                        .then("-fx-font-weight: bold; -fx-font-style: italic;").otherwise(""));
                    if (flatList) {
                        String folder = f.path().getParent() == null ? "" : where(f.path().getParent());
                        String date = fileDates.getOrDefault(f.path(), java.util.Optional.empty())
                            .map(i -> java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                                .format(i.atZone(java.time.ZoneId.systemDefault())))
                            .orElse("no date");
                        Label where = new Label(folder + " · " + date);
                        where.setStyle("-fx-text-fill: #888888; -fx-font-size: 0.9em;");
                        setGraphic(where);
                        setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
                    } else if (f.directory().isEmpty() && f.path().getParent() != null
                            && !isProjectDirectory(f.path().getParent())) {
                        Label where = new Label(where(f.path().getParent()));
                        where.setStyle("-fx-text-fill: #888888; -fx-font-size: 0.9em;");
                        setGraphic(where);
                        setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
                    }
                    boolean outside = FileTree.outside(f, projectDirectory(), flatList);
                    setTooltip(new Tooltip(f.path().toString()
                        + (rank == null ? "" : "\nPosition " + rank + " in the history of the dictionary")
                        + (outside ? "\n" + OUTSIDE_FILE : "")));
                    if (outside) markOutside(OUTSIDE_FILE, f.name());
                    setContextMenu(fileMenu(f));
                }
                case FileTree.TopDirectory d -> {
                    directoryLook(where(d.path()));
                    String settings = project == null ? "" : project.directorySettings(d.path()).map(FileMode::describe).orElse("");
                    boolean outside = FileTree.outside(d, projectDirectory(), flatList);
                    setTooltip(new Tooltip(d.path() + (settings.isEmpty() ? "" : "\n" + settings)
                        + (outside ? "\n" + OUTSIDE_DIRECTORY : "")));
                    if (outside) markOutside(OUTSIDE_DIRECTORY, d.path().getFileName() + "");
                    setContextMenu(topDirectoryMenu(d));
                }
                case FileTree.SubDirectory d -> {
                    directoryLook(d.name());
                    setTooltip(new Tooltip(d.path().toString()));
                    setContextMenu(subDirectoryMenu(d));
                }
            }
        }

        /** A "!" before the cell's other graphic: the entry is not in the project directory. */
        private void markOutside(String message, String name) {
            Label mark = new Label("!");
            mark.getStyleClass().add("outside-mark");
            mark.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
            mark.setTooltip(new Tooltip(message));
            mark.setAccessibleText(name + ", not in the project directory");
            Node other = getGraphic();
            setGraphic(other == null ? mark : new javafx.scene.layout.HBox(6, mark, other));
            setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        }

        private void directoryLook(String name) {
            setText("📁 " + name);
            List<TextDocument> below = getTreeItem() == null ? List.of() : documentsBelow(getTreeItem()).toList();
            Label count = new Label("(" + below.size() + ")");
            count.setStyle("-fx-text-fill: #888888;");
            setGraphic(count);
            setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            setStyle(below.stream().anyMatch(TextDocument::getCouldBeWritten) ? "-fx-font-weight: bold;" : "");
        }
    }

    private static java.util.stream.Stream<TextDocument> documentsBelow(TreeItem<FileTree.Node> item) {
        if (item.getValue() instanceof FileTree.FileNode f) return java.util.stream.Stream.of(f.document());
        return item.getChildren().stream().flatMap(FileMode::documentsBelow);
    }

    /** "*.txt, *.md · excludes drafts/** · recursive". */
    static String describe(fr.cnrs.lacito.matchbox.model.project.DirectorySettings s) {
        String in = s.includes().isEmpty() ? "every file" : String.join(", ", s.includes());
        String out = s.excludes().isEmpty() ? "" : " · excludes " + String.join(", ", s.excludes());
        return in + out + (s.recursive() ? " · with sub-directories" : " · without sub-directories");
    }

    static final String OUTSIDE_FILE = "This file is not in the project directory";
    static final String OUTSIDE_DIRECTORY = "This directory is not in the project directory";

    /**
     * How a directory is named in the tree: relative to the project directory when inside it, else
     * its absolute path with "~" for the home directory.
     */
    private String where(java.nio.file.Path dir) {
        Optional<java.nio.file.Path> base = projectDirectory();
        if (fr.cnrs.lacito.matchbox.corpus.ProjectPaths.isInside(dir, base)) {
            return fr.cnrs.lacito.matchbox.corpus.ProjectPaths.display(dir, base);
        }
        return abbreviate(dir);
    }

    private boolean isProjectDirectory(java.nio.file.Path dir) {
        return projectDirectory().map(d -> d.equals(dir.toAbsolutePath().normalize())).orElse(false);
    }

    private static String abbreviate(java.nio.file.Path dir) {
        String home = System.getProperty("user.home");
        String s = dir.toString();
        return home != null && !home.isEmpty() && s.startsWith(home) ? "~" + s.substring(home.length()) : s;
    }

    private ContextMenu topDirectoryMenu(FileTree.TopDirectory d) {
        return new ContextMenu(
            item("Edit directory settings…", () -> workbench.files().editSettings(d.path())),
            item("Rescan directory", () -> workbench.files().rescan(d.path())),
            new SeparatorMenuItem(),
            item("Remove this directory from the project", () -> workbench.files().removeDirectory(d.path())));
    }

    private ContextMenu subDirectoryMenu(FileTree.SubDirectory d) {
        return new ContextMenu(
            item("Exclude this sub-directory", () -> workbench.files().excludeSubdirectory(d.top(), d.path())));
    }

    private ContextMenu fileMenu(FileTree.FileNode f) {
        MenuItem remove = item("Remove file from project", () -> workbench.files().removeFile(f.document()));
        if (f.directory().isPresent()) {
            remove.setDisable(true);
            return new ContextMenu(remove,
                item("Exclude this file", () -> workbench.files().excludeFile(f.document())));
        }
        return new ContextMenu(remove);
    }

    // ------------------------------------------------------------------
    // Errors tab
    // ------------------------------------------------------------------

    private void setupProblemTabs() {
        java.util.function.Function<String, String> pluginName =
            id -> GrammarNames.label(id, PluginManager.getAvailableGrammars());
        var editorSettings = settings.editorProperty();
        DiagnosticsPane.Config parsing = new DiagnosticsPane.Config(
            DiagnosticFilter.PARSING_ERRORS, "error", d -> !d.isSemantic(),
            "Also list the warnings, e.g. a reading not kept because a grammar listed earlier reads the same text",
            "No parsing errors in this file.", "No parsing errors in the project.",
            DiagnosticsPane.setting(!Boolean.FALSE.equals(editorSettings.get().showWarnings()),
                v -> editorSettings.set(editorSettings.get().withShowWarnings(v))),
            DiagnosticsPane.setting(editorSettings.get().problemsOrDefaults().parsingErrorsAllFiles(),
                v -> changeProblems(p -> p.withParsingErrorsAllFiles(v))),
            DiagnosticsPane.setting(editorSettings.get().problemsOrDefaults().showCode(),
                v -> changeProblems(p -> p.withShowCode(v))));
        DiagnosticsPane.Config semantic = new DiagnosticsPane.Config(
            DiagnosticFilter.SEMANTIC_ISSUES, "semantic", Diagnostic::isSemantic,
            "Also list the warnings, e.g. a metadata key given twice",
            "No semantic issues in this file.", "No semantic issues in the project.",
            DiagnosticsPane.setting(editorSettings.get().problemsOrDefaults().showSemanticWarnings(),
                v -> changeProblems(p -> p.withShowSemanticWarnings(v))),
            DiagnosticsPane.setting(editorSettings.get().problemsOrDefaults().semanticIssuesAllFiles(),
                v -> changeProblems(p -> p.withSemanticIssuesAllFiles(v))),
            parsing.showCode());
        // the settings may change elsewhere (another window, a reset)
        editorSettings.addListener((obs, o, n) -> {
            if (n == null) return;
            if (n.showWarnings() != null) parsing.showWarnings().set(n.showWarnings());
            var p = n.problemsOrDefaults();
            parsing.allFiles().set(p.parsingErrorsAllFiles());
            parsing.showCode().set(p.showCode());
            semantic.showWarnings().set(p.showSemanticWarnings());
            semantic.allFiles().set(p.semanticIssuesAllFiles());
        });

        MenuItem explain = new MenuItem("Explain");
        MenuItem show = new MenuItem("Show in suggestions panel");
        parsingErrors = new DiagnosticsPane(parsing, parsingErrorPane, workbench.problems(), workbench.status(),
            this::displayedFile, this::selectInDisplayed, pluginName, this::projectDirectory, List.of(explain, show));
        explain.setOnAction(e -> parsingErrors.selected().ifPresent(r -> {
            setSuggestionPanelVisible(true);
            suggestionPanel.showError(r.diagnostic(), true);
        }));
        show.setOnAction(e -> parsingErrors.selected().ifPresent(r -> {
            setSuggestionPanelVisible(true);
            suggestionPanel.showError(r.diagnostic(), false);
        }));
        parsingErrorPane.setContent(parsingErrors);

        semanticIssuesTab = new Tab(DiagnosticFilter.SEMANTIC_ISSUES);
        semanticIssuesTab.setId("semanticIssuesTab");
        semanticIssuesTab.setTooltip(new Tooltip("The text follows the notation, but what it says is wrong or doubtful"));
        semanticIssues = new DiagnosticsPane(semantic, semanticIssuesTab, workbench.problems(), workbench.status(),
            this::displayedFile, this::selectInDisplayed, pluginName, this::projectDirectory, List.of());
        semanticIssuesTab.setContent(semanticIssues);
        objectTabPane.getTabs().add(objectTabPane.getTabs().indexOf(parsingErrorPane) + 1, semanticIssuesTab);
        for (DiagnosticsPane pane : List.of(parsingErrors, semanticIssues)) {
            pane.setExportChooser(this::chooseExportFile);
        }
        // the displayed file changed, or was parsed again
        editorController.getCurrentFileErrors().addListener((ListChangeListener<Diagnostic>) change -> {
            parsingErrors.refresh();
            semanticIssues.refresh();
        });
    }

    private void changeProblems(java.util.function.UnaryOperator<fr.cnrs.lacito.matchbox.settings.ProblemsSettingsData> change) {
        var current = settings.editorProperty().get();
        var updated = change.apply(current.problemsOrDefaults());
        if (!updated.equals(current.problemsOrDefaults())) {
            settings.editorProperty().set(current.withProblems(updated));
        }
    }

    private Optional<fr.cnrs.lacito.matchbox.corpus.TextId> displayedFile() {
        return editorController.getSelectedDocument().map(d -> d.getId());
    }

    private void selectInDisplayed(Focus focus) {
        editorController.selectRange(focus.start(), focus.end());
    }

    private Optional<java.nio.file.Path> projectDirectory() {
        return workbench.viewContext().projectDirectory();
    }

    private Optional<java.nio.file.Path> chooseExportFile(String name) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export the problems");
        chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV files", "*.csv"));
        projectDirectory().map(java.nio.file.Path::toFile).filter(java.io.File::isDirectory)
            .ifPresent(chooser::setInitialDirectory);
        return Optional.ofNullable(chooser.showSaveDialog(workbench.window())).map(java.io.File::toPath);
    }

    /** Shows the Parsing errors tab and focuses its table (File Mode menu). */
    public void showParsingErrors() {
        ensureShown(FileModeLayout.Panel.OBJECTS);
        objectTabPane.getSelectionModel().select(parsingErrorPane);
        parsingErrors.focusTable();
    }

    /** Shows the Semantic issues tab and focuses its table (File Mode menu). */
    public void showSemanticIssues() {
        ensureShown(FileModeLayout.Panel.OBJECTS);
        objectTabPane.getSelectionModel().select(semanticIssuesTab);
        semanticIssues.focusTable();
    }

    /** The two problem tables (for tests). */
    public DiagnosticsPane parsingErrorsPane() {
        return parsingErrors;
    }

    public DiagnosticsPane semanticIssuesPane() {
        return semanticIssues;
    }

    private int caretLine() {
        return fileContentCodeArea.getCurrentParagraph() + 1;
    }

    /** F8 / Shift+F8: the next / previous line with a problem in the displayed file, wrapping. */
    public void nextProblemInFile(boolean forward) {
        Optional<fr.cnrs.lacito.matchbox.corpus.TextId> file = displayedFile();
        if (file.isEmpty()) return;
        var rows = workbench.problems().index().rows(file.get());
        var next = fr.cnrs.lacito.matchbox.gui.problems.ProblemNavigator.next(
            rows.stream().map(fr.cnrs.lacito.matchbox.gui.problems.ProblemRow::diagnostic).toList(), caretLine(), forward);
        if (next.isEmpty()) {
            workbench.status().info("No problem in this file");
            return;
        }
        rows.stream().filter(r -> r.diagnostic() == next.get()).findFirst().ifPresent(r -> {
            workbench.problems().target(r).ifPresent(this::selectInDisplayed);
            workbench.status().info("Line " + r.line() + ": " + r.diagnostic().message());
        });
    }

    /** Alt+F8 / Alt+Shift+F8: the next / previous problem of the project, opening its file. */
    public void nextProblemInProject(boolean forward) {
        var next = workbench.problems().index().next(displayedFile().orElse(null), caretLine(), forward, r -> true);
        if (next.isEmpty()) {
            workbench.status().info("No problem in the project");
            return;
        }
        workbench.problems().go(next.get());
    }

    /** Shortcut+.: the fixes of the problems of the caret's line (one is applied, several are offered). */
    public void applyFixAtCaret() {
        Optional<fr.cnrs.lacito.matchbox.corpus.TextId> file = displayedFile();
        int line = caretLine();
        List<javafx.util.Pair<fr.cnrs.lacito.matchbox.gui.problems.ProblemRow, Diagnostic.Fix>> fixes = file
            .map(f -> workbench.problems().index().rows(f)).orElse(List.of()).stream()
            .filter(r -> r.line() == line)
            .flatMap(r -> r.diagnostic().fixes().stream().map(fix -> new javafx.util.Pair<>(r, fix)))
            .toList();
        if (fixes.isEmpty()) {
            workbench.status().info("No fix for line " + line);
            return;
        }
        if (fixes.size() == 1) {
            workbench.problems().applyFix(fixes.get(0).getKey(), fixes.get(0).getValue());
            return;
        }
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        for (var p : fixes) {
            menu.getItems().add(item(p.getValue().label(), () -> workbench.problems().applyFix(p.getKey(), p.getValue())));
        }
        fileContentCodeArea.getCaretBounds().ifPresentOrElse(
            b -> menu.show(fileContentCodeArea, b.getMinX(), b.getMaxY()),
            () -> menu.show(fileContentCodeArea, javafx.geometry.Side.TOP, 0, 0));
    }

    // ------------------------------------------------------------------
    // Overlaps tab
    // ------------------------------------------------------------------

    private void setupOverlapsTab() {
        overlapsPane = new OverlapsPane(this::grammarLabel, this::showBoth);
        overlapsTab = new Tab(OverlapRows.tabTitle(0), overlapsPane);
        overlapsTab.setId("overlapsTab");
        overlapsTab.setTooltip(new Tooltip("Readings of the same text by several grammars, in the whole project"));
        int errorsIndex = objectTabPane.getTabs().indexOf(parsingErrorPane);
        objectTabPane.getTabs().add(errorsIndex + 1, overlapsTab);
        overlapsTab.selectedProperty().addListener((obs, o, n) -> {
            if (n && overlapsStale) refreshOverlaps();
        });
        workbench.session().addCorpusListener((id, before, after) -> {
            boolean changed = before.map(t -> !t.conflicts().isEmpty()).orElse(false)
                || after.map(t -> !t.conflicts().isEmpty()).orElse(false);
            if (changed) overlapsChanged();
        });
        workbench.session().projectProperty().addListener((obs, o, n) -> overlapsChanged());
        overlapsChanged();
    }

    /** "Display name (id)" of a grammar of the project, or installed; the id if unknown. */
    private String grammarLabel(String grammarId) {
        List<GrammarPlugin> known = new ArrayList<>();
        if (project != null) {
            project.getFileTypes().forEach(t -> known.addAll(t.getGrammars()));
        }
        known.addAll(PluginManager.getAvailableGrammars());
        return GrammarNames.label(grammarId, known);
    }

    /** The count of the tab follows the corpus; the rows are rebuilt only while the tab is shown. */
    private void overlapsChanged() {
        overlapsTab.setText(OverlapRows.tabTitle(OverlapRows.counts(workbench.session().corpus())[0]));
        overlapsStale = true;
        if (overlapsTab.isSelected()) refreshOverlaps();
    }

    private void refreshOverlaps() {
        overlapsPane.refresh(workbench.session().corpus());
        overlapsStale = false;
    }

    /** Shows the Overlaps tab (File Mode menu). */
    public void showOverlaps() {
        ensureShown(FileModeLayout.Panel.OBJECTS);
        objectTabPane.getSelectionModel().select(overlapsTab);
        if (overlapsStale) refreshOverlaps();
        overlapsPane.table().requestFocus();
        if (overlapsPane.table().getSelectionModel().isEmpty() && !overlapsPane.table().getItems().isEmpty()) {
            overlapsPane.table().getSelectionModel().select(0);
        }
    }

    /** Shows both readings of an overlap: selects the one kept, outlines the other. */
    void showBoth(OverlapRows.Row row) {
        ensureShown(FileModeLayout.Panel.EDITOR);
        workbench.navigator().showInEditor(Focus.of(row.selected()));
        var outlined = row.outlined();
        editorController.outline(new int[] {outlined.startIndex(), outlined.stopIndex() + 1});
    }

    /** The Overlaps view (for tests). */
    public OverlapsPane overlapsPane() {
        return overlapsPane;
    }

    // ------------------------------------------------------------------
    // Objects tab
    // ------------------------------------------------------------------

    private void setupObjectsTab() {
        editorController.getCurrentFileObjects().addListener(
            (ListChangeListener<CorpusObject>) change -> refreshObjectTabs()
        );
        editorController.selectedObjectProperty().addListener(
            (obs, oldObject, newObject) -> onObjectSelected(newObject)
        );
        // the statuses of dictionary commands follow the history of the dictionary
        // only the tables listing dictionary commands show what the history says
        historyRefresh = o -> tabsByKind.values().forEach(t -> {
            TableView<?> table = (TableView<?>) t.getContent();
            if (table.getItems().stream().anyMatch(
                    i -> i instanceof fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand)) {
                table.refresh();
            }
        });
        // the positions in the history: the cells are drawn again; a list ordered by date may change order (saves)
        historyTree = (obs, o, n) -> {
            Map<java.nio.file.Path, Integer> before = historyRank;
            if (flatList) rebuildTree();
            else {
                updateHistoryRank();
                if (!historyRank.equals(before)) fileTreeView.refresh();
            }
        };
        workbench.history().resultProperty().addListener(new javafx.beans.value.WeakChangeListener<>(historyTree));
        workbench.history().resultProperty().addListener(new javafx.beans.WeakInvalidationListener(historyRefresh));
        workbench.history().computingProperty().addListener(new javafx.beans.WeakInvalidationListener(historyRefresh));
    }

    /** Updates the per-kind tables, keeping existing tabs (and their selection state) when possible. */
    private void refreshObjectTabs() {
        Map<String, List<CorpusObject>> byKind = new LinkedHashMap<>();
        for (CorpusObject o : editorController.getCurrentFileObjects()) {
            byKind.computeIfAbsent(o.getKind(), k -> new ArrayList<>()).add(o);
        }
        tabsByKind.keySet().removeIf(kind -> {
            if (!byKind.containsKey(kind)) {
                objectKindTabPane.getTabs().remove(tabsByKind.get(kind));
                return true;
            }
            return false;
        });
        byKind.forEach((kind, objects) -> {
            Tab tab = tabsByKind.computeIfAbsent(kind, this::createKindTab);
            @SuppressWarnings("unchecked")
            TableView<CorpusObject> table = (TableView<CorpusObject>) tab.getContent();
            Class<?> type = objects.get(0).getClass();
            if (!type.equals(table.getUserData())) {
                table.getColumns().setAll(viewRegistry.columnsFor(type, viewContext, workbench.status()::warn));
                table.setUserData(type);
            }
            table.getItems().setAll(objects);
            tab.setText(Kinds.title(kind) + " (" + objects.size() + ")");
            if (!objectKindTabPane.getTabs().contains(tab)) {
                objectKindTabPane.getTabs().add(tab);
            }
        });
        if (byKind.isEmpty()) {
            showDetail(null);
        }
    }

    private Tab createKindTab(String kind) {
        TableView<CorpusObject> table = new TableView<>();
        table.setPlaceholder(new Label("No objects"));
        // columns showing a field can be edited (Enter writes the value in the text)
        table.setEditable(true);
        table.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            CorpusObject selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || table.getEditingCell() != null) return;
            if (UNDO.match(e)) {
                viewContext.undo(selected);
                e.consume();
            } else if (REDO.match(e) || REDO_ALT.match(e)) {
                viewContext.redo(selected);
                e.consume();
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldObject, newObject) -> {
            if (newObject != null && !propagatingSelection) {
                editorController.selectedObjectProperty().set(newObject);
                editorController.selectInEditor(newObject.getTextCoordinate());
            }
        });
        return new Tab(Kinds.title(kind), table);
    }

    /** Shows the selected object and selects its top-level ancestor in the tables. */
    private void onObjectSelected(CorpusObject object) {
        showDetail(object);
        if (object == null) return;
        CorpusObject topLevel = editorController.getCurrentFileObjects().stream()
            .filter(o -> Corpus.selfAndDescendants(o).anyMatch(d -> d == object))
            .findFirst()
            .orElse(null);
        if (topLevel == null) return;
        Tab tab = tabsByKind.get(topLevel.getKind());
        if (tab == null) return;
        propagatingSelection = true;
        try {
            objectKindTabPane.getSelectionModel().select(tab);
            @SuppressWarnings("unchecked")
            TableView<CorpusObject> table = (TableView<CorpusObject>) tab.getContent();
            table.getSelectionModel().select(topLevel);
            table.scrollTo(topLevel);
        } finally {
            propagatingSelection = false;
        }
    }

    /**
     * Shows an object in the detail pane. The view is not rebuilt if the object is a re-parsed
     * copy of the displayed one (same kind, label and span), to avoid flicker.
     */
    private void showDetail(CorpusObject object) {
        if (object == displayedObject || (object != null && sameContent(object, displayedObject))) {
            displayedObject = object;
            return;
        }
        CorpusObject previous = displayedObject;
        displayedObject = object;
        // a re-parsed version of the displayed object: let its view update itself (keeps focus, edits)
        if (object != null && viewRegistry.updateView(objectDetailPane.getContent(), previous, object, viewContext)) {
            return;
        }
        objectDetailPane.setContent(object == null ? null : viewRegistry.createView(object, viewContext));
    }

    private static boolean sameContent(CorpusObject a, CorpusObject b) {
        return b != null
            && a.getKind().equals(b.getKind())
            && a.getTextCoordinate().equals(b.getTextCoordinate())
            && Objects.equals(a.getLabel(), b.getLabel());
    }

    // ------------------------------------------------------------------
    // Toolbar of the editor
    // ------------------------------------------------------------------

    /** Parses the edited file in the background; the views refresh when the results arrive. */
    @FXML
    public void handleParseText() {
        editorController.parseEditedFile().exceptionally(e -> {
            Platform.runLater(() -> ErrorReporter.report(window(), "Parsing failed", e));
            return null;
        });
    }

    /** Removes the displayed file from the project if it was added alone. */
    @FXML
    public void handleCloseFile() {
        editorController.getSelectedDocument().ifPresent(d -> {
            workbench.files().removeFile(d);
            if (project != null && !project.getDerivedFileList().contains(d)) {
                firstFile(fileTreeView.getRoot()).ifPresent(this::selectItem);
            }
        });
    }

    @FXML
    public void handleSaveCurrentFile() {
        try {
            editorController.saveEditedFile();
        } catch (IOException e) {
            ErrorReporter.report(window(), "Error saving file", e);
        }
    }

    public EditorController getEditorController() {
        return editorController;
    }

    private Window window() {
        return root.getScene() == null ? workbench.window() : root.getScene().getWindow();
    }
}
