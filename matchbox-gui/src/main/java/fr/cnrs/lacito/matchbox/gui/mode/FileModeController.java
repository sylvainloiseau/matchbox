package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.UnsavedChangesDialog;
import fr.cnrs.lacito.matchbox.gui.edit.ViewEditService;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.assist.GhostTextController;
import fr.cnrs.lacito.matchbox.gui.assist.SuggestionPanelController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.gui.view.ErrorFilter;
import fr.cnrs.lacito.matchbox.gui.view.GrammarNames;
import fr.cnrs.lacito.matchbox.gui.view.Kinds;
import fr.cnrs.lacito.matchbox.gui.view.ObjectViewRegistry;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.TextDocument;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javafx.application.HostServices;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.collections.ListChangeListener;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.fxmisc.richtext.InlineCssTextArea;

public class FileModeController {

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
    private Hyperlink assistStatusLink;

    @FXML
    private Label assistMessageLabel;

    @FXML
    private TableView<Diagnostic> errorTableView;

    @FXML
    private TableColumn<Diagnostic, Number> lineColumn;

    @FXML
    private TableColumn<
        Diagnostic,
        Number
    > charPositionColumn;

    @FXML
    private TableColumn<
        Diagnostic,
        String
    > messageColumn;

    @FXML
    private TableColumn<Diagnostic, String> pluginColumn;

    /** Filters the error table by plugin. */
    @FXML
    private ChoiceBox<ErrorFilter.Choice> errorPluginChoice;

    private FilteredList<Diagnostic> filteredErrors;
    /** True while the choices are rebuilt (their selection must not filter yet). */
    private boolean updatingErrorChoices;

    private Project project;
    private Corpus corpus;
    private UserSettings settings;
    private EditorController editorController;
    private ObjectViewRegistry viewRegistry;
    private final AssistController assist;
    private GhostTextController ghostText;
    private SuggestionPanelController suggestionPanel;
    private ViewEditService viewEdits;
    /** Opens links in the system browser; set by the application. */
    private HostServices hostServices;

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

    public FileModeController(UserSettings settings, AssistController assist) {
        this.settings = settings;
        this.assist = assist;
    }

    /** This method should be called after initialization 
     * to set the model. */
    public void setProject(Project project) {
        this.project = project;
        this.corpus = project.getCorpus();
        if (editorController != null) {
            editorController.setProject(project);
        }
        setupFileListView();
        project.setApplyExecutor(Platform::runLater);
        saveButton
            .disableProperty()
            .bind(
                editorController
                    .getCurrentTextCouldBeWrittenDownProperty()
                    .not()
            );
        closeButton
            .disableProperty()
            .bind(editorController.getClosableProperty().not());
        parseButton
            .disableProperty()
            .bind(
                editorController
                    .getCurrentTextCouldBeParsedAgainProperty()
                    .not()
            );

        this.editorController.setProject(project);
        assist.setProject(project);
    }

    @FXML
    public void initialize() throws Exception {
        this.editorController = new EditorController(
            fileContentCodeArea,
            settings
        );
        this.viewRegistry = ObjectViewRegistry.load();
        this.viewEdits = new ViewEditService(() -> project, editorController, settings);
        editorController.setTextActions(PluginManager.loadServices(TextActionProvider.class), viewContext,
            assistMessageLabel::setText);
        setupObjectsTab();
        setupErrorTab();
        setupAssistant();
    }

    // ------------------------------------------------------------------
    // Assistant
    // ------------------------------------------------------------------

    private void setupAssistant() {
        ghostText = new GhostTextController(editorController, assist, assistMessageLabel::setText);
        suggestionPanel = new SuggestionPanelController(editorController, assist);

        assistStatusLink.textProperty().bind(assist.statusProperty());
        assistStatusLink.setOnAction(e -> {
            assistStatusLink.setVisited(false);
            assist.openSettings();
        });

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
        ContextMenu menu = new ContextMenu(suggest, explain, new SeparatorMenuItem(), reviewBlock, reviewSelection);
        menu.setOnShowing(e -> {
            explain.setDisable(editorController.errorAt(fileContentCodeArea.getCaretPosition()).isEmpty());
            reviewSelection.setDisable(fileContentCodeArea.getSelection().getLength() == 0);
        });
        fileContentCodeArea.setContextMenu(menu);
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

    public AssistController getAssist() {
        return assist;
    }

    private void setupFileListView() {
        FilteredList<TextDocument> filteredList = new FilteredList<>(
            project.getDerivedFileList()
        );
        filterFileTextField
            .textProperty()
            .addListener((obs, oldValue, newValue) -> {
                if (newValue == null || newValue.isEmpty()) {
                    filteredList.setPredicate(item -> true);
                } else {
                    String filterText = newValue.toLowerCase();
                    filteredList.setPredicate(item ->
                        item
                            .getFilePath()
                            .toString()
                            .toLowerCase()
                            .contains(filterText)
                    );
                }
            });

        fileListView.setItems(filteredList);
        fileListView.setCellFactory(
            new Callback<ListView<TextDocument>, ListCell<TextDocument>>() {
                @Override
                public ListCell<TextDocument> call(ListView<TextDocument> param) {
                    return new ListCell<TextDocument>() {
                        @Override
                        protected void updateItem(
                            TextDocument item,
                            boolean empty
                        ) {
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
                    };
                }
            }
        );
        fileListView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    editorController.selectEditedFile(newVal.getFilePath());
                } else {
                    editorController.clearEditor();
                }
            });
    }

    private void setupErrorTab() {
        lineColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(cellData.getValue().line())
        );
        charPositionColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(
                cellData.getValue().column()
            )
        );
        messageColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().message())
        );
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

        errorTableView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldError, newError) -> {
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
        ContextMenu menu = new ContextMenu(explain, show);
        menu.setOnShowing(e -> {
            boolean none = errorTableView.getSelectionModel().getSelectedItem() == null;
            explain.setDisable(none);
            show.setDisable(none);
        });
        errorTableView.setContextMenu(menu);
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
                table.getColumns().setAll(viewRegistry.columnsFor(type, viewContext, assistMessageLabel::setText));
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

    /** Services offered to object views. */
    private final ViewContext viewContext = new ViewContext() {
        @Override
        public void selectInEditor(TextCoordinate span) {
            if (span == null || project == null) return;
            if (!isDisplayed(span)) {
                project.document(span.text()).ifPresent(d -> fileListView.getSelectionModel().select(d));
            }
            if (isDisplayed(span)) {
                editorController.selectInEditor(span);
            }
        }

        private boolean isDisplayed(TextCoordinate span) {
            return editorController.getSelectedDocument().map(d -> d.getId().equals(span.text())).orElse(false);
        }

        @Override
        public void select(CorpusObject object) {
            selectInEditor(object.getTextCoordinate());
            editorController.selectedObjectProperty().set(object);
        }

        @Override
        public String highlightColor(CorpusObject object) {
            return editorController.getHighlightStyles().colorFor(object);
        }

        @Override
        public CorpusView corpus() {
            return corpus;
        }

        @Override
        public boolean canEdit(CorpusObject object) {
            return viewEdits.canEdit(object);
        }

        @Override
        public EditOutcome setField(CorpusObject object, String fieldName, String value) {
            return viewEdits.setField(object, fieldName, value);
        }

        @Override
        public EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits) {
            return viewEdits.applyEdits(object, edits);
        }

        @Override
        public EditOutcome addField(CorpusObject object, String fieldName, String value) {
            return viewEdits.addField(object, fieldName, value);
        }

        @Override
        public EditOutcome removeField(CorpusObject object, String fieldName) {
            return viewEdits.removeField(object, fieldName);
        }

        @Override
        public EditOutcome runTextAction(TextAction action) {
            return editorController.getSelectedDocument()
                .map(d -> viewEdits.runTextAction(d.getId(), action))
                .orElseGet(() -> new EditOutcome.NotEditable("No file is displayed"));
        }

        @Override
        public Optional<Path> projectDirectory() {
            if (project == null || project.fileProperty().get() == null) return Optional.empty();
            return Optional.ofNullable(project.fileProperty().get().toPath().toAbsolutePath().getParent());
        }

        @Override
        public void showDocument(String uri) {
            if (hostServices != null) {
                hostServices.showDocument(uri);
            }
        }

        @Override
        public boolean undo(CorpusObject object) {
            return viewEdits.undo(object);
        }

        @Override
        public boolean redo(CorpusObject object) {
            return viewEdits.redo(object);
        }
    };

    /** Parses the edited file in the background; the views refresh when the results arrive. */
    public void handleParseText() {
        editorController.parseEditedFile().exceptionally(e -> {
            Platform.runLater(() -> ErrorReporter.report(
                fileContentCodeArea.getScene() == null ? null : fileContentCodeArea.getScene().getWindow(),
                "Parsing failed", e));
            return null;
        });
    }

    public InlineCssTextArea getFileContentArea() {
        return editorController.getFileContentArea();
    }

    public void handleCloseFile() {
        TextDocument selected = fileListView
            .getSelectionModel()
            .getSelectedItem();
        if (selected != null && UnsavedChangesDialog.confirm(window(), List.of(selected), "closing the file")) {
            project.removeFile(selected.getFilePath());
            if (!project.getDerivedFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
        }
    }

    public void openFileOrDirectory(File f) throws Exception {
        if (f != null) {
            if (f.isDirectory()) {
                project.addDirectory(f.toPath(), true);
            } else {
                project.addFile(f.toPath());
            }
            if (!project.getDerivedFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
            settings.recentFiles().add(f.toPath());
        }
    }

    public void handleOpenDirectory(Stage primaryStage) throws Exception {
        DirectoryChooser chooser = new DirectoryChooser();
        File selected = chooser.showDialog(primaryStage);
        if (selected != null) {
            project.addDirectory(selected.toPath(), true);
            if (!project.getDerivedFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
        }
    }

    public EditorController getEditorController() {
        return editorController;
    }

    public java.util.List<String> getOpenedFiles() {
        java.util.List<String> files = new java.util.ArrayList<>();
        for (TextDocument parser : project.getDerivedFileList()) {
            files.add(parser.getFilePath().toString());
        }
        return files;
    }

    public void handleSaveCurrentFile() throws IOException {
        editorController.saveEditedFile();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /** Saves every document with unsaved changes (some may have been changed by views only). */
    public void handleSaveAll() throws IOException {
        if (project != null) {
            project.saveAll();
        }
    }

    private javafx.stage.Window window() {
        return fileContentCodeArea.getScene() == null ? null : fileContentCodeArea.getScene().getWindow();
    }

    /** Stops the background threads of the view editing service. */
    public void shutdown() {
        viewEdits.close();
    }
}
