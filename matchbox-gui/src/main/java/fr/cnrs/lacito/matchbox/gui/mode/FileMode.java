package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.SearchDialog;
import fr.cnrs.lacito.matchbox.gui.UnsavedChangesDialog;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.assist.GhostTextController;
import fr.cnrs.lacito.matchbox.gui.assist.SuggestionPanelController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.gui.view.ErrorFilter;
import fr.cnrs.lacito.matchbox.gui.view.GrammarNames;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
    private ListView<TextDocument> fileListView;

    @FXML
    private TextField filterFileTextField;

    @FXML
    private InlineCssTextArea fileContentCodeArea;

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
    private FilteredList<TextDocument> filteredFiles;
    private Menu menu;
    /** What the window's commands target: the displayed file, the caret and the selected object. */
    private final ObjectProperty<Optional<Focus>> focus = new SimpleObjectProperty<>(Optional.empty());
    /** Shows the first file when files are added while none is displayed. */
    private final ListChangeListener<TextDocument> filesAdded = change -> {
        if (editorController.getSelectedDocument().isEmpty() && !fileListView.getItems().isEmpty()) {
            Platform.runLater(() -> {
                if (fileListView.getSelectionModel().isEmpty() && !fileListView.getItems().isEmpty()) {
                    fileListView.getSelectionModel().select(0);
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
        setupFileListView();
        setupObjectsTab();
        setupErrorTab();
        setupAssistant();
        setupFocus();
        this.menu = buildMenu();
        saveButton.disableProperty().bind(editorController.getCurrentTextCouldBeWrittenDownProperty().not());
        closeButton.disableProperty().bind(editorController.getClosableProperty().not());
        parseButton.disableProperty().bind(editorController.getCurrentTextCouldBeParsedAgainProperty().not());
    }

    /** Shows the files of a new project; nothing is displayed until a file is chosen. */
    private void setProject(Project project) {
        if (this.project != null) {
            this.project.getDerivedFileList().removeListener(filesAdded);
        }
        this.project = project;
        editorController.setProject(project);
        filteredFiles = new FilteredList<>(project.getDerivedFileList());
        applyFileFilter(filterFileTextField.getText());
        fileListView.setItems(filteredFiles);
        project.getDerivedFileList().addListener(filesAdded);
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
                // the file may be hidden by the filter of the list
                if (!fileListView.getItems().contains(d)) filterFileTextField.clear();
                fileListView.getSelectionModel().select(d);
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
        MenuItem search = item("Search…", this::openSearch);
        MenuItem parse = item("Parse file", this::handleParseText);
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

        Menu result = new Menu("File Mode", null,
            search, parse,
            new SeparatorMenuItem(),
            suggest, inline, explain, reviewBlock, reviewSelection, reviewFile, panel,
            new SeparatorMenuItem(),
            insert);
        // everything acts on the displayed file
        var noFile = editorController.getClosableProperty().not();
        for (MenuItem i : List.of(search, suggest, explain, reviewBlock, reviewFile, insert)) {
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

    /** Find and replace in the displayed file; the replacements are one undoable change. */
    public void openSearch() {
        new SearchDialog(fileContentCodeArea, editorController::applySearchEdits, window()).show();
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
        filterFileTextField.textProperty().addListener((obs, oldValue, newValue) -> applyFileFilter(newValue));
        fileListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TextDocument item, boolean empty) {
                super.updateItem(item, empty);
                textProperty().unbind();
                styleProperty().unbind();
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // unsaved documents (possibly changed by a view) are marked
                    String path = item.getFilePath().toString();
                    var dirty = item.getCouldBeWrittenBooleanProperty();
                    textProperty().bind(Bindings.when(dirty).then("* " + path).otherwise(path));
                    styleProperty().bind(Bindings.when(dirty)
                        .then("-fx-font-weight: bold; -fx-font-style: italic;").otherwise(""));
                }
            }
        });
        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                editorController.selectEditedFile(newVal.getFilePath());
            } else {
                editorController.clearEditor();
            }
            updateFocus();
        });
    }

    private void applyFileFilter(String filter) {
        if (filteredFiles == null) return;
        if (filter == null || filter.isEmpty()) {
            filteredFiles.setPredicate(item -> true);
        } else {
            String lower = filter.toLowerCase();
            filteredFiles.setPredicate(item -> item.getFilePath().toString().toLowerCase().contains(lower));
        }
    }

    // ------------------------------------------------------------------
    // Errors tab
    // ------------------------------------------------------------------

    private void setupErrorTab() {
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
        filteredErrors.setPredicate(choice == null ? d -> true : choice::accepts);
        parsingErrorPane.setText(ErrorFilter.tabTitle(filteredErrors.size(),
            editorController.getCurrentFileErrors().size(), choice != null && choice.grammarId() != null));
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

    @FXML
    public void handleCloseFile() {
        TextDocument selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null && UnsavedChangesDialog.confirm(window(), List.of(selected), "closing the file")) {
            project.removeFile(selected.getFilePath());
            if (!project.getDerivedFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
        }
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
