package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.mode.FileModeController;
import fr.cnrs.lacito.matchbox.settings.AssistSettingsData;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Main controller for the Notebook application
 */
public class MainController {

    private static final Logger logger = Logger.getLogger(MainController.class.getName());

    @FXML
    private VBox mainBorderPane;

    @FXML
    private Menu fileMenu;

    @FXML
    private SplitPane topSplitPane;

    @FXML
    private MenuItem saveAsProjectMenuItem;

    @FXML
    private Menu openRecentMenu;

    @FXML
    private Menu editMenu;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private MenuItem searchMenuItem;

    @FXML
    private MenuItem closeFileMenuItem;

    @FXML
    private MenuItem saveCurrentFileMenuItem;

    @FXML
    private MenuItem openProjectMenuItem;

    @FXML
    private Menu openRecentProjectsMenuItem;

    @FXML
    private MenuItem closeCurrentProjectMenuItem;

    @FXML
    private MenuItem newProjectMenuItem;

    @FXML
    private MenuItem saveProjectMenuItem;

    @FXML
    private Label statusBarRightLabel;

    @FXML
    private CheckMenuItem inlineSuggestionsMenuItem;

    @FXML
    private CheckMenuItem suggestionPanelMenuItem;

    @FXML
    private Pane viewPane;

    @FXML
    ToggleButton tagViewToggleButton;

    @FXML
    ToggleButton fileModeToggleButton;

    @FXML
    ToggleButton corpusViewToggleButton;

    @FXML
    ToggleGroup viewToggleGroup;

    SplitPane fileModePane = null;

    private UserSettings settings;
    private Stage primaryStage;

    private FileModeController fileModeController;
    // TODO should not be visible here... should be accessed via fileModeController
    private EditorController editorController;
    // TODO should not be visible here... Should be passing of property instead.
    private InlineCssTextArea fileContentCodeArea;

    ChangeListener<Toggle> toggleGroupListener;

    private List<GrammarPlugin> plugins;

    private Project project;

    private final AssistController assist;

    public MainController(
        UserSettings settings,
        List<GrammarPlugin> plugins
    ) {
        this.settings = settings;
        this.plugins = plugins;
        this.assist = new AssistController(settings, () -> primaryStage);
        List<Path> recentProjects = settings.recentProjects().existing();
        if (settings.openLastProjectProperty().get() && !recentProjects.isEmpty()) {
            File lastProjectFile = recentProjects.get(0).toFile();
            try {
                this.project = Project.loadProject(plugins, lastProjectFile, settings.documentOptions());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot reopen the last project " + lastProjectFile, e);
            }
        }
        if (this.project == null) {
            this.project = Project.createDefaultProject(plugins, settings.documentOptions());
        }

        // Load file mode controller, editor controller and fileContentCodeArea
        fileModeController = loadFileMode();
        fileModeController.setProject(this.project);
        fileContentCodeArea = fileModeController.getFileContentArea();
        editorController = fileModeController.getEditorController();

        toggleGroupListener = new ChangeListener<Toggle>() {
            @Override
            public void changed(
                ObservableValue<? extends Toggle> ov,
                Toggle toggle,
                Toggle new_toggle
            ) {
                if (
                    ((ToggleButton) viewToggleGroup.getSelectedToggle()).equals(
                        fileModeToggleButton
                    )
                ) {
                    switchToFileMode();
                }
            }
        };
    }

    private void onProjectChangeHook() {
        saveProjectMenuItem
            .disableProperty()
            .bind(project.fileProperty().isNotNull());
        project.isProjectDefinitionOutdatedProperty().set(true);
    }

    private FileModeController loadFileMode() {
        FileModeController fileModeController = new FileModeController(
            settings,
            assist
        );
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(
            getClass().getResource(
                "/fr/cnrs/lacito/matchbox/gui/mode/FileMode.fxml"
            )
        );
        loader.setController(fileModeController);
        try {
            fileModePane = loader.load();
        } catch (IOException e) {
            // the main view cannot work without it
            throw new java.io.UncheckedIOException("Cannot load the file mode view", e);
        }
        return fileModeController;
    }

    @FXML
    public void initialize() {
        viewToggleGroup
            .selectedToggleProperty()
            .addListener(toggleGroupListener);
        viewToggleGroup.selectToggle(fileModeToggleButton);
        loadRecentFilesMenu();
        loadRecentProjectsMenu();
        settings.recentFiles().items().addListener((Observable o) -> loadRecentFilesMenu());
        settings.recentProjects().items().addListener((Observable o) -> loadRecentProjectsMenu());
        // loadRecentFileSetsMenu();
        wireMenuItem();
        wireAssistantMenu();
        switchToFileMode();
        // problems of the project reopened at startup
        Platform.runLater(this::reportLoadProblems);
    }

    private void wireAssistantMenu() {
        AssistSettingsData d = settings.assistProperty().get();
        inlineSuggestionsMenuItem.setSelected(d.inlineEnabled());
        suggestionPanelMenuItem.setSelected(d.suggestionPanelVisible());
        settings.assistProperty().addListener((obs, o, n) -> {
            inlineSuggestionsMenuItem.setSelected(n.inlineEnabled());
            suggestionPanelMenuItem.setSelected(n.suggestionPanelVisible());
        });
        inlineSuggestionsMenuItem.setOnAction(e -> settings.assistProperty().set(
            settings.assistProperty().get().withInlineEnabled(inlineSuggestionsMenuItem.isSelected())));
        suggestionPanelMenuItem.setOnAction(e ->
            fileModeController.setSuggestionPanelVisible(suggestionPanelMenuItem.isSelected()));
    }

    // ---------------------------------------------
    // Assistant
    // ---------------------------------------------

    public void handleAiAssistant() {
        assist.openSettings();
    }

    public void handleSuggestCompletion() {
        fileModeController.requestCompletion();
    }

    public void handleExplainError() {
        fileModeController.explainErrorAtCaret();
    }

    public void handleReviewBlock() {
        fileModeController.review("Current block");
    }

    public void handleReviewSelection() {
        fileModeController.review("Selection");
    }

    public void handleReviewFile() {
        fileModeController.review("Whole file");
    }

    public void handleRefreshExamples() {
        assist.refreshExamples();
    }

    /** Stops the assistant's background threads. */
    public void shutdown() {
        assist.close();
    }

    private void switchToFileMode() {
        viewPane.getChildren().clear();
        viewPane.getChildren().add(fileModePane);
        // bind undo/redo to CodeArea's undo/redo availability
        if (undoMenuItem != null && fileContentCodeArea != null) {
            undoMenuItem.disableProperty().unbind();
            undoMenuItem
                .disableProperty()
                .bind(fileContentCodeArea.undoAvailableProperty());
        }
        if (redoMenuItem != null && fileContentCodeArea != null) {
            redoMenuItem.disableProperty().unbind();
            redoMenuItem
                .disableProperty()
                .bind(fileContentCodeArea.redoAvailableProperty());
        }
        // enable search only when the code area has focus
        if (searchMenuItem != null && fileContentCodeArea != null) {
            searchMenuItem.disableProperty().bind(
                fileContentCodeArea.focusedProperty() //.not()
            );
        }
    }

    private void wireMenuItem() {
        saveCurrentFileMenuItem
            .disableProperty()
            .bind(
                editorController
                    .getCurrentTextCouldBeWrittenDownProperty()
                    .not()
            );
        closeFileMenuItem
            .disableProperty()
            .bind(editorController.getClosableProperty().not());
    }

    public void handleUndoMenuItem() {
        editorController.dismissGhost();
        if (
            fileContentCodeArea != null && fileContentCodeArea.isUndoAvailable()
        ) {
            fileContentCodeArea.undo();
        }
    }

    public void handleRedoMenuItem() {
        editorController.dismissGhost();
        if (
            fileContentCodeArea != null && fileContentCodeArea.isRedoAvailable()
        ) {
            fileContentCodeArea.redo();
        }
    }

    public void handleSearchMenuItem() {
        // Show search dialog bound to current code area
        SearchDialog dialog = new SearchDialog(
            fileContentCodeArea,
            primaryStage
        );
        dialog.show();
    }

    public void handleExportDictionary() {
        //TODO
    }

    // start open file submenus
    //
    public void handleOpenFileWithFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser
            .getExtensionFilters()
            .addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
        File selected = chooser.showOpenDialog(primaryStage);
        openFileOrDirectory(selected);
    }
    public void handleOpenDirectory() {
        try {
            fileModeController.handleOpenDirectory(primaryStage);
        } catch (Exception e) {
            ErrorReporter.report(primaryStage, "Error opening directory", e);
        }
    }

    private void loadRecentFilesMenu() {
        this.openRecentMenu.getItems().clear();
        List<Path> recent = settings.recentFiles().existing();
        if (recent.isEmpty()) {
            MenuItem noRecentItem = new MenuItem("No recent files");
            noRecentItem.setDisable(true);
            openRecentMenu.getItems().add(noRecentItem);
        } else {
            for (Path path : recent) {
                MenuItem item = new MenuItem(path.toString());
                item.setOnAction(e -> openFileOrDirectory(path.toFile()));
                openRecentMenu.getItems().add(item);
            }
            // Add separator
            openRecentMenu.getItems().add(new SeparatorMenuItem());
            // Add clear recent files item
            MenuItem clearItem = new MenuItem("Clear Recent Files");
            clearItem.setOnAction(e -> {
                settings.recentFiles().clear();
            });
            openRecentMenu.getItems().add(clearItem);
        }
    }

    /**
    */

    // End open file submenus

    private void openFileOrDirectory(File f) {
        try {
            fileModeController.openFileOrDirectory(f);
        } catch (Exception e) {
            ErrorReporter.report(primaryStage, "Error opening file", e);
        }
    }

    // for the button, no need of that, directly called in fileModeController
    public void handleCloseFile() {
        fileModeController.handleCloseFile();
    }

    public void handleSaveCurrentFile() {
        try {
            fileModeController.handleSaveCurrentFile();
        } catch (IOException e) {
            ErrorReporter.report(primaryStage, "Error saving file", e);
        }
    }

    // ---------------------------------------------
    // Project
    // ---------------------------------------------

    private void openProject(File f) {
        try {
            this.project = Project.loadProject(this.plugins, f, settings.documentOptions());
            fileModeController.setProject(project);
            settings.recentProjects().add(f.toPath());
            project.parseProject();
            onProjectChangeHook();
            reportLoadProblems();
        } catch (Exception e) {
            ErrorReporter.report(primaryStage, "Error opening project", e);
        }
    }

    /** Shows the problems found when the project was loaded (unreadable files, invalid patterns). */
    private void reportLoadProblems() {
        if (!project.getLoadProblems().isEmpty()) {
            ErrorReporter.report(
                primaryStage,
                "Some parts of the project could not be loaded",
                String.join("\n\n", project.getLoadProblems()) +
                    "\n\nCorrect them in File › Edit project…"
            );
        }
    }

    public void handleOpenProject() {
        FileChooser chooser = new FileChooser();
        chooser
            .getExtensionFilters()
            .addAll(
                new FileChooser.ExtensionFilter(
                    "Matchbox project files",
                    "*.mbx"
                ),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
        File selected = chooser.showOpenDialog(primaryStage);
        if (selected != null) {
            openProject(selected);
        }
    }

    public void handleOpenRecentFileset() {
        // TODO implement
    }

    public void handleSaveCurrentProject() {
        if (project.fileProperty().isNull().get()) {
            handleSaveAsProject();
            return;
        }
        try {
            ProjectXMLSerializer.save(project);
        } catch (Exception e) {
            // the project stays marked as modified
            ErrorReporter.report(primaryStage, "Error saving project", e);
            return;
        }
        project.isProjectDefinitionOutdatedProperty().set(false);
    }

    public void handleNewProject() {
        handleCloseCurrentProject();
        Project newProject = Project.createDefaultProject(plugins, settings.documentOptions());
        project = newProject;
        onProjectChangeHook();
    }

    public void handleEditProject() {
        ProjectPropertiesDialog dialog = new ProjectPropertiesDialog(
            primaryStage,
            this.project,
            fileModeController
        );
        dialog.showAndWait();
        if (dialog.isSaved()) {
            onProjectChangeHook();
            // the project may now allow local models only
            assist.updateStatus();
        }
    }

    public void handleCloseCurrentProject() {
        if (project.isProjectDefinitionOutdatedProperty().get()) {
            ButtonType ok = new ButtonType("foo", ButtonBar.ButtonData.OK_DONE);
            ButtonType no = new ButtonType("bar", ButtonBar.ButtonData.NO);
            ButtonType cancel = new ButtonType("bar", ButtonBar.ButtonData.CANCEL_CLOSE);

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "would you like to discard change in current project?",
                ok, no, cancel);
            alert.setTitle("Project Definition Outdated");
            alert.setHeaderText("The project definition is outdated.");
            alert.setContentText(
                "Do you want to save the changes before closing?"
            );
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                handleSaveCurrentProject();
            } else if (result.isPresent() && result.get() == ButtonType.CANCEL) {
                return;
            }
        }
        //project.close();
        this.project = Project.createDefaultProject(plugins, settings.documentOptions());
        onProjectChangeHook();
    }

    public void handleCloseAllFiles() {
        // TODO implement
    }

    private void openFileset(File f) {
        // TODO implement
    }

    // private void loadRecentFileSetsMenu() {
    //     this.openRecentFilesetMenuItem.getItems().clear();
    //     List<String> recentFileSets = applicationSettings.getRecentFileSets();
    //     if (recentFileSets.isEmpty()) {
    //         MenuItem noRecentItem = new MenuItem("No recent file sets");
    //         noRecentItem.setDisable(true);
    //         openRecentFilesetMenuItem.getItems().add(noRecentItem);
    //     } else {
    //         for (String filepath : recentFileSets) {
    //             MenuItem item = new MenuItem(filepath);
    //             item.setOnAction(e -> openFileset(new File(filepath)));
    //             openRecentFilesetMenuItem.getItems().add(item);
    //         }
    //         // Add separator
    //         openRecentFilesetMenuItem.getItems().add(new SeparatorMenuItem());
    //         // Add clear recent files item
    //         MenuItem clearItem = new MenuItem("Clear Recent Files");
    //         clearItem.setOnAction(e -> {
    //             applicationSettings.clearRecentFilesets();
    //             loadRecentFileSetsMenu();
    //         });
    //         openRecentFilesetMenuItem.getItems().add(clearItem);
    //     }
    // }

    private void loadRecentProjectsMenu() {
        this.openRecentProjectsMenuItem.getItems().clear();
        List<Path> recent = settings.recentProjects().existing();
        if (recent.isEmpty()) {
            MenuItem noRecentItem = new MenuItem("No recent projects");
            noRecentItem.setDisable(true);
            openRecentProjectsMenuItem.getItems().add(noRecentItem);
        } else {
            for (Path path : recent) {
                MenuItem item = new MenuItem(path.toString());
                item.setOnAction(e -> openProject(path.toFile()));
                openRecentProjectsMenuItem.getItems().add(item);
            }
            // Add separator
            openRecentProjectsMenuItem.getItems().add(new SeparatorMenuItem());
            // Add clear recent projects item
            MenuItem clearItem = new MenuItem("Clear Recent Projects");
            clearItem.setOnAction(e -> {
                settings.recentProjects().clear();
            });
            openRecentProjectsMenuItem.getItems().add(clearItem);
        }
    }

    public void handleSaveAsProject() {
        CreateProjectDialog dialog = new CreateProjectDialog(
            primaryStage,
            fileModeController,
            this.project
        );
        dialog.showAndWait();
    }

    public void handleSettings() {
        SettingsDialog dialog = new SettingsDialog(
            settings,
            primaryStage
        );
        dialog.showAndWait();
    }

    public void handleQuit() {
        Platform.exit();
    }


    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}
