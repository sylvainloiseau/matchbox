package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.gui.mode.FileModeController;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.model.ApplicationSettings;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchboxgrammarapi.GrammarPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import javafx.application.Platform;
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

    @FXML
    private VBox mainBorderPane;

    @FXML
    private Menu fileMenu;

    @FXML
    private SplitPane topSplitPane;

    @FXML
    private MenuItem createProjectMenuItem;

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
    private MenuItem openFilesetMenuItem;

    @FXML
    private Menu openRecentProjectsMenuItem;

    @FXML
    private MenuItem closeCurrentFilesetMenuItem;

    @FXML
    private MenuItem saveFilesetMenuItem;

    @FXML
    private MenuItem saveFilesetAsMenuItem;

    @FXML
    private Label statusBarRightLabel;

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

    private ApplicationSettings applicationSettings;
    private Stage primaryStage;

    private FileModeController fileModeController;
    // TODO should not be visible here... should be accessed via fileModeController
    private EditorController editorController;
    // TODO should not be visible here... Should be passing of property instead.
    private InlineCssTextArea fileContentCodeArea;

    ChangeListener<Toggle> toggleGroupListener;

    private List<GrammarPlugin> plugins;

    private Project project;

    public MainController(ApplicationSettings applicationSettings, List<GrammarPlugin> plugins) {
        this.applicationSettings = applicationSettings;
        this.plugins = plugins;
        if (
            applicationSettings.isOpenLastProject() &&
            applicationSettings.getRecentProjects().size() > 0
        ) {
            File lastProjectFile = new File(
                applicationSettings.getRecentProjects().get(0)
            );
            try {
                this.project = Project.loadProject(plugins, lastProjectFile);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            this.project = Project.createDefaultProject(plugins);
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

    private FileModeController loadFileMode() {
        FileModeController fileModeController = new FileModeController(
            applicationSettings
        );
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(
            getClass().getResource("/fr/cnrs/lacito/matchbox/gui/mode/FileMode.fxml")
        );
        loader.setController(fileModeController);
        try {
            fileModePane = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
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
        // loadRecentFileSetsMenu();
        wireMenuItem();
        switchToFileMode();
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
                fileContentCodeArea
                    .focusedProperty() //.not()
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
        if (
            fileContentCodeArea != null && fileContentCodeArea.isUndoAvailable()
        ) {
            fileContentCodeArea.undo();
        }
    }

    public void handleRedoMenuItem() {
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

    private void openProject(File f) {
        try {
            Project project = Project.loadProject(this.plugins, f);
            fileModeController.setProject(project);
            applicationSettings.addToProjectHistory(f.getAbsolutePath());
            loadRecentProjectsMenu();
        } catch (Exception e) {
            showError("Error opening project", e.getMessage());
        }
    }

    private void openFileOrDirectory(File f) {
        try {
                fileModeController.openFileOrDirectory(f);
        } catch (Exception e) {
            showError("Error opening file", e.getMessage());
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
            showError("Error saving file", e.getMessage());
        }
    }

    public void handleOpenDirectory() {
        try {
            fileModeController.handleOpenDirectory(primaryStage);
        } catch (Exception e) {
            showError("Error opening directory", e.getMessage());
        }
    }

    public void handleOpenFileset() {
        // TODO implement
    }

    public void handleOpenRecentFileset() {
        // TODO implement
    }

    public void handleCloseCurrentFileset() {
        // TODO implement
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
        List<String> recent = applicationSettings.getRecentProjects();
        if (recent.isEmpty()) {
            MenuItem noRecentItem = new MenuItem("No recent projects");
            noRecentItem.setDisable(true);
            openRecentProjectsMenuItem.getItems().add(noRecentItem);
        } else {
            for (String filepath : recent) {
                MenuItem item = new MenuItem(filepath);
                item.setOnAction(e -> openProject(new File(filepath)));
                openRecentProjectsMenuItem.getItems().add(item);
            }
            // Add separator
            openRecentProjectsMenuItem.getItems().add(new SeparatorMenuItem());
            // Add clear recent projects item
            MenuItem clearItem = new MenuItem("Clear Recent Projects");
            clearItem.setOnAction(e -> {
                applicationSettings.clearRecentProjects();
                loadRecentProjectsMenu();
            });
            openRecentProjectsMenuItem.getItems().add(clearItem);
        }
    }

    private void loadRecentFilesMenu() {
        this.openRecentMenu.getItems().clear();
        List<String> recent = applicationSettings.getRecentFiles();
        if (recent.isEmpty()) {
            MenuItem noRecentItem = new MenuItem("No recent files");
            noRecentItem.setDisable(true);
            openRecentMenu.getItems().add(noRecentItem);
        } else {
            for (String filepath : recent) {
                MenuItem item = new MenuItem(filepath);
                item.setOnAction(e -> openFileOrDirectory(new File(filepath)));
                openRecentMenu.getItems().add(item);
            }
            // Add separator
            openRecentMenu.getItems().add(new SeparatorMenuItem());
            // Add clear recent files item
            MenuItem clearItem = new MenuItem("Clear Recent Files");
            clearItem.setOnAction(e -> {
                applicationSettings.clearRecentFiles();
                loadRecentFilesMenu();
            });
            openRecentMenu.getItems().add(clearItem);
        }
    }

    public void handleCreateProject() {
        CreateProjectDialog dialog = new CreateProjectDialog(
            primaryStage,
            fileModeController,
            this.project
        );
        dialog.showAndWait();
    }

    public void handleSettings() {
        SettingsDialog dialog = new SettingsDialog(applicationSettings, primaryStage);
        dialog.showAndWait();
    }

    public void handleQuit() {
        Platform.exit();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}
