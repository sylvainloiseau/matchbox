package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.gui.view.Kinds;
import fr.cnrs.lacito.matchbox.gui.view.ObjectViewRegistry;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.TextDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
    private TableView<ParseError> errorTableView;

    @FXML
    private TableColumn<ParseError, Number> lineColumn;

    @FXML
    private TableColumn<
        ParseError,
        Number
    > charPositionColumn;

    @FXML
    private TableColumn<
        ParseError,
        String
    > messageColumn;

    private Project project;
    private Corpus corpus;
    private UserSettings settings;
    private EditorController editorController;
    private ObjectViewRegistry viewRegistry;

    /** One tab per kind of top-level object of the current file, in order of appearance. */
    private final Map<String, Tab> tabsByKind = new LinkedHashMap<>();
    /** Object shown in the detail pane. */
    private CorpusObject displayedObject;
    /** True while the selection is propagated, to avoid feedback between table and editor. */
    private boolean propagatingSelection;

    public FileModeController(UserSettings settings) {
        this.settings = settings;
    }

    /** This method should be called after initialization 
     * to set the model. */
    public void setProject(Project project) {
        this.project = project;
        this.corpus = project.getCorpus();
        if (editorController != null) {
            editorController.setProject(project);
        }
        try {
            setupFileListView();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
    }

    @FXML
    public void initialize() throws Exception {
        this.editorController = new EditorController(
            fileContentCodeArea,
            settings
        );
        this.viewRegistry = ObjectViewRegistry.load();
        setupObjectsTab();
        setupErrorTab();
    }

    private void setupFileListView() throws Exception {
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
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                setText(item.getFilePath().toString());
                                item.getCouldBeWrittenBooleanProperty().addListener(
                                    (obs, oldValue, newValue) -> {
                                        if (newValue) {
                                            setStyle("-fx-font-style: italic;");
                                            setStyle("-fx-font-shape: bold;");
                                        } else {
                                            setStyle("font-style: normal;");
                                        }
                                    }
                                );
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
                    try {
                        System.out.println(
                            "Selected file in file list view: " +
                                newVal.toString()
                        );
                        editorController.selectEditedFile(newVal.getFilePath());
                    } catch (Exception ex) {
                        throw ex;
                    }
                } else {
                    editorController.clearEditor();
                }
            });
    }

    private void setupErrorTab() {
        lineColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(cellData.getValue().getLine())
        );
        charPositionColumn.setCellValueFactory(cellData ->
            new SimpleIntegerProperty(
                cellData.getValue().getCharPositionInLine()
            )
        );
        messageColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getMessage())
        );

        errorTableView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldError, newError) -> {
                if (newError != null) {
                    editorController.selectLinesInEditor(newError);
                }
            });
        errorTableView.setItems(editorController.getCurrentFileErrors());
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
                table.getColumns().setAll(viewRegistry.columnsFor(type));
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
        displayedObject = object;
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
            if (span == null) return;
            if (!Objects.equals(span.path(), editorController.getSelectedFilePath())) {
                project.getDerivedFileList().stream()
                    .filter(d -> d.getFilePath().equals(span.path()))
                    .findFirst()
                    .ifPresent(d -> fileListView.getSelectionModel().select(d));
            }
            if (Objects.equals(span.path(), editorController.getSelectedFilePath())) {
                editorController.selectInEditor(span);
            }
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
    };

    /** Parses the edited file in the background; the views refresh when the results arrive. */
    public void handleParseText() {
        editorController.parseEditedFile().exceptionally(e -> {
            Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, "Parsing failed: " + e.getMessage()).showAndWait()
            );
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
        if (selected != null) {
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
}
