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
import fr.cnrs.lacito.matchbox.gui.view.ErrorFilter;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.OverlapRows;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.OverlapsPane;
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
    private TableView<Diagnostic> errorTableView;

    @FXML
    private TableColumn<Diagnostic, String> severityColumn;

    @FXML
    private TableColumn<Diagnostic, Number> lineColumn;

    @FXML
    private TableColumn<Diagnostic, Number> charPositionColumn;

    @FXML
    private TableColumn<Diagnostic, String> messageColumn;

    @FXML
    private TableColumn<Diagnostic, String> pluginColumn;

    /** Filters the error table by plugin. */
    @FXML
    private ChoiceBox<ErrorFilter.Choice> errorPluginChoice;

    /** The table also lists warnings (remembered in the editor settings). */
    @FXML
    private javafx.scene.control.CheckBox showWarningsCheck;

    @FXML
    private TabPane bottomTabPane;

    /** Overlapping readings of the whole project. */
    private OverlapsPane overlapsPane;
    private Tab overlapsTab;
    /** True when the corpus changed while the Overlaps tab was not shown. */
    private boolean overlapsStale = true;

    private FilteredList<Diagnostic> filteredErrors;
    /** True while the choices are rebuilt (their selection must not filter yet). */
    private boolean updatingErrorChoices;

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
        setupErrorTab();
        setupOverlapsTab();
        setupAssistant();
        setupFocus();
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
        }
        this.project = project;
        editorController.setProject(project);
        expanded.clear();
        if (project != null) {
            project.getDerivedFileList().addListener(filesAdded);
            project.definitionRevisionProperty().addListener(definitionListener);
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

    /** Opens the file of the focus, then selects its object or range. */
    @Override
    public boolean reveal(Focus target) {
        if (project == null) return false;
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
        Menu insert = new Menu("Insert", null,
            new MenuItem("Insert intermorphemic gloss"),
            new MenuItem("Insert lexical information"));

        MenuItem directorySettings = item("Directory settings…",
            () -> selectedDirectory().ifPresent(workbench.files()::editSettings));
        MenuItem removeDirectory = item("Remove directory from project",
            () -> selectedDirectory().ifPresent(workbench.files()::removeDirectory));
        directorySettings.disableProperty().bind(directorySelected.not());
        removeDirectory.disableProperty().bind(directorySelected.not());

        Menu result = new Menu("File Mode", null,
            search, findNext, findPrevious, replace, goToLine, specialCharacter, parse, showOverlaps,
            new SeparatorMenuItem(),
            lines, editorOptions,
            new SeparatorMenuItem(),
            directorySettings, removeDirectory,
            new SeparatorMenuItem(),
            suggest, inline, explain, reviewBlock, reviewSelection, reviewFile, panel,
            new SeparatorMenuItem(),
            insert);
        // everything acts on the displayed file
        var noFile = editorController.getClosableProperty().not();
        result.getItems().addAll(new SeparatorMenuItem(), shortcuts);
        for (MenuItem i : List.of(search, findNext, findPrevious, replace, goToLine, specialCharacter, lines,
                suggest, explain, reviewBlock, reviewFile, insert)) {
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
            editorSplitPane.setDividerPositions(0.72);
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
        if (project != null) {
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
                    textProperty().bind(Bindings.when(dirty).then("* " + f.name()).otherwise(f.name()));
                    styleProperty().bind(Bindings.when(dirty)
                        .then("-fx-font-weight: bold; -fx-font-style: italic;").otherwise(""));
                    if (f.directory().isEmpty() && f.path().getParent() != null) {
                        Label where = new Label(abbreviate(f.path().getParent()));
                        where.setStyle("-fx-text-fill: #888888; -fx-font-size: 0.9em;");
                        setGraphic(where);
                        setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
                    }
                    setTooltip(new Tooltip(f.path().toString()));
                    setContextMenu(fileMenu(f));
                }
                case FileTree.TopDirectory d -> {
                    directoryLook(abbreviate(d.path()));
                    String settings = project == null ? "" : project.directorySettings(d.path()).map(FileMode::describe).orElse("");
                    setTooltip(new Tooltip(d.path() + (settings.isEmpty() ? "" : "\n" + settings)));
                    setContextMenu(topDirectoryMenu(d));
                }
                case FileTree.SubDirectory d -> {
                    directoryLook(d.name());
                    setTooltip(new Tooltip(d.path().toString()));
                    setContextMenu(subDirectoryMenu(d));
                }
            }
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

    private void setupErrorTab() {
        severityColumn.setCellValueFactory(cellData -> new SimpleStringProperty(ErrorFilter.severityLabel(cellData.getValue())));
        severityColumn.setCellFactory(column -> new TableCell<>() {
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
                setAccessibleText(label);
            }
        });
        showWarningsCheck.setSelected(!Boolean.FALSE.equals(settings.editorProperty().get().showWarnings()));
        showWarningsCheck.setTooltip(new Tooltip(
            "Also list the warnings, e.g. a reading not kept because a grammar listed earlier reads the same text"));
        showWarningsCheck.selectedProperty().addListener((obs, o, n) -> {
            if (!n.equals(settings.editorProperty().get().showWarnings())) {
                settings.editorProperty().set(settings.editorProperty().get().withShowWarnings(n));
            }
            applyErrorFilter();
        });
        settings.editorProperty().addListener((obs, o, n) -> {
            if (n != null && n.showWarnings() != null && n.showWarnings() != showWarningsCheck.isSelected()) {
                showWarningsCheck.setSelected(n.showWarnings());
            }
        });
        lineColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().line()));
        charPositionColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().column()));
        messageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().message()));
        // the plugin that reported the error: display name and id
        pluginColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
            GrammarNames.label(cellData.getValue().grammarId(), PluginManager.getAvailableGrammars())));
        pluginColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String label, boolean empty) {
                super.updateItem(label, empty);
                setText(empty ? null : label);
                setTooltip(empty || label == null || label.isEmpty() ? null : new Tooltip(label));
            }
        });

        filteredErrors = new FilteredList<>(editorController.getCurrentFileErrors());
        SortedList<Diagnostic> sortedErrors = new SortedList<>(filteredErrors);
        sortedErrors.comparatorProperty().bind(errorTableView.comparatorProperty());
        editorController.getCurrentFileErrors().addListener(
            (ListChangeListener<Diagnostic>) change -> refreshErrorChoices());
        errorPluginChoice.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (!updatingErrorChoices) applyErrorFilter();
        });
        refreshErrorChoices();

        errorTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldError, newError) -> {
            if (newError != null) {
                editorController.selectLinesInEditor(newError);
            }
        });
        errorTableView.setItems(sortedErrors);

        MenuItem explain = new MenuItem("Explain");
        explain.setOnAction(e -> {
            Diagnostic error = errorTableView.getSelectionModel().getSelectedItem();
            if (error != null) {
                setSuggestionPanelVisible(true);
                suggestionPanel.showError(error, true);
            }
        });
        MenuItem show = new MenuItem("Show in suggestions panel");
        show.setOnAction(e -> {
            Diagnostic error = errorTableView.getSelectionModel().getSelectedItem();
            if (error != null) {
                setSuggestionPanelVisible(true);
                suggestionPanel.showError(error, false);
            }
        });
        ContextMenu contextMenu = new ContextMenu(explain, show);
        contextMenu.setOnShowing(e -> {
            boolean none = errorTableView.getSelectionModel().getSelectedItem() == null;
            explain.setDisable(none);
            show.setDisable(none);
        });
        errorTableView.setContextMenu(contextMenu);
    }

    /** Rebuilds the plugin choices from the errors of the current file, keeping the chosen plugin if possible. */
    private void refreshErrorChoices() {
        ErrorFilter.Choice previous = errorPluginChoice.getSelectionModel().getSelectedItem();
        var choices = ErrorFilter.choices(editorController.getCurrentFileErrors(), PluginManager.getAvailableGrammars());
        updatingErrorChoices = true;
        try {
            errorPluginChoice.getItems().setAll(choices);
            errorPluginChoice.getSelectionModel().select(
                ErrorFilter.keep(choices, previous == null ? null : previous.grammarId()));
        } finally {
            updatingErrorChoices = false;
        }
        applyErrorFilter();
    }

    private void applyErrorFilter() {
        ErrorFilter.Choice choice = errorPluginChoice.getSelectionModel().getSelectedItem();
        boolean warnings = showWarningsCheck.isSelected();
        filteredErrors.setPredicate(d -> (warnings || d.isError()) && (choice == null || choice.accepts(d)));
        boolean filtered = (choice != null && choice.grammarId() != null)
            || (!warnings && editorController.getCurrentFileErrors().stream().anyMatch(Diagnostic::isWarning));
        parsingErrorPane.setText(ErrorFilter.tabTitle(filteredErrors.size(),
            editorController.getCurrentFileErrors(), filtered));
    }

    // ------------------------------------------------------------------
    // Overlaps tab
    // ------------------------------------------------------------------

    private void setupOverlapsTab() {
        overlapsPane = new OverlapsPane(this::grammarLabel, this::showBoth);
        overlapsTab = new Tab(OverlapRows.tabTitle(0), overlapsPane);
        overlapsTab.setId("overlapsTab");
        overlapsTab.setTooltip(new Tooltip("Readings of the same text by several grammars, in the whole project"));
        int errorsIndex = bottomTabPane.getTabs().indexOf(parsingErrorPane);
        bottomTabPane.getTabs().add(errorsIndex + 1, overlapsTab);
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
        bottomTabPane.getSelectionModel().select(overlapsTab);
        if (overlapsStale) refreshOverlaps();
        overlapsPane.table().requestFocus();
        if (overlapsPane.table().getSelectionModel().isEmpty() && !overlapsPane.table().getItems().isEmpty()) {
            overlapsPane.table().getSelectionModel().select(0);
        }
    }

    /** Shows both readings of an overlap: selects the one kept, outlines the other. */
    void showBoth(OverlapRows.Row row) {
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
