package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.gui.mode.common.lexicon.LexicalEntryController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.model.ApplicationSettings;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchboxgrammarapi.ParseError;
import fr.cnrs.lacito.matchbox.model.TextParser;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleIGT;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleLexicalDefinition;
import java.io.File;
import java.io.IOException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.fxmisc.richtext.InlineCssTextArea;

public class FileModeController {

    @FXML
    private ListView<TextParser> fileListView;

    @FXML
    private TextField filterFileTextField;

    @FXML
    private InlineCssTextArea fileContentCodeArea;

    @FXML
    private TableView<SimpleIGT> igtTableView;

    @FXML
    private TableColumn<SimpleIGT, String> transcriptionCol;

    @FXML
    private TableColumn<SimpleIGT, String> glossCol;

    @FXML
    private TableColumn<SimpleIGT, String> translationCol;

    @FXML
    private VBox lexicalInformationBoxes;

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
    private ApplicationSettings applicationSettings;
    private EditorController editorController;

    public FileModeController(ApplicationSettings conf) {
        this.applicationSettings = conf;
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
        setupIgtTab();
        setupErrorTab();
        setupLexicalEntriesTab();
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
            applicationSettings
        );
    }

    private void setupFileListView() throws Exception {
        FilteredList<TextParser> filteredList = new FilteredList<>(
            project.getPassiveFileList()
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
            new Callback<ListView<TextParser>, ListCell<TextParser>>() {
                @Override
                public ListCell<TextParser> call(ListView<TextParser> param) {
                    return new ListCell<TextParser>() {
                        @Override
                        protected void updateItem(
                            TextParser item,
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
                        editorController.highlightText();
                    } catch (Exception ex) {
                        throw ex;
                    }
                } else {
                    editorController.clearEditor();
                }
            });
    }

    private void setupIgtTab() {
        transcriptionCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getTranscription())
        );
        glossCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getMorpheme())
        );
        translationCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFreeTranslation())
        );

        igtTableView
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldVal, newIgt) -> {
                if (newIgt != null) {
                    editorController.selectLinesInEditor(newIgt);
                }
            });
        igtTableView.setItems(editorController.getCurrentFileIgts());
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

    private void setupLexicalEntriesTab() {
        ObservableList<SimpleLexicalDefinition> lexicalDefinitions =
            editorController.getCurrentFileLexicalDefinition();
        lexicalDefinitions.addListener(
            (
                javafx.collections.ListChangeListener.Change<
                    ? extends SimpleLexicalDefinition
                > change
            ) -> {
                lexicalInformationBoxes.getChildren().clear();
                for (SimpleLexicalDefinition lexDef : lexicalDefinitions) {
                    try {
                        LexicalEntryController controller =
                            new LexicalEntryController(lexDef);
                        FXMLLoader loader = new FXMLLoader();
                        loader.setLocation(
                            getClass().getResource(
                                "/fr/cnrs/lacito/matchbox/gui/mode/common/lexicon/LexicalEntryView.fxml"
                            )
                        );
                        loader.setController(controller);
                        Parent entryView = loader.load();
                        VBox entryBox = (VBox) entryView;
                        lexicalInformationBoxes.getChildren().add(entryBox);
                    } catch (IOException e) {
                        System.err.println(
                            "Error loading lexical entry view: " +
                                e.getMessage()
                        );
                    }
                }
            }
        );
    }

    public void handleParseText() throws Exception {
        try {
            editorController.parseEditedFile();
            editorController.highlightText();
        } catch (Exception e) {
            throw e;
        }
    }

    public InlineCssTextArea getFileContentArea() {
        return editorController.getFileContentArea();
    }

    public void handleCloseFile() {
        TextParser selected = fileListView
            .getSelectionModel()
            .getSelectedItem();
        if (selected != null) {
            project.removeFile(selected.getFilePath());
            if (!project.getPassiveFileList().isEmpty()) {
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
            if (!project.getPassiveFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
            applicationSettings.addToHistory(f.toString());
        }
    }

    public void handleOpenDirectory(Stage primaryStage) throws Exception {
        DirectoryChooser chooser = new DirectoryChooser();
        File selected = chooser.showDialog(primaryStage);
        if (selected != null) {
            project.addDirectory(selected.toPath(), true);
            if (!project.getPassiveFileList().isEmpty()) {
                fileListView.getSelectionModel().select(0);
            }
        }
    }

    public EditorController getEditorController() {
        return editorController;
    }

    public java.util.List<String> getOpenedFiles() {
        java.util.List<String> files = new java.util.ArrayList<>();
        for (TextParser parser : project.getPassiveFileList()) {
            files.add(parser.getFilePath().toString());
        }
        return files;
    }

    public void handleSaveCurrentFile() throws IOException {
        editorController.saveEditedFile();
    }
}
