package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.mode.ModeHost;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.gui.workbench.WorkbenchCommands;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The main window: its menus, toolbar and status bar, and the window's services
 * ({@link Workbench}) shared by the modes shown in the main area ({@link ModeHost}). The window's
 * menus are mode-independent ({@link WorkbenchCommands}); each mode adds its own menu.
 */
public class MainController {

    private static final Logger logger = Logger.getLogger(MainController.class.getName());

    private static final KeyCombination BACK = new KeyCodeCombination(KeyCode.LEFT,
        KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCombination FORWARD = new KeyCodeCombination(KeyCode.RIGHT,
        KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination REDO_ALT = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

    @FXML
    private VBox mainBorderPane;

    @FXML
    private MenuBar menuBar;

    @FXML
    private Menu viewMenu;

    @FXML
    private ToolBar toolBar;

    @FXML
    private Button backButton;

    @FXML
    private Button forwardButton;

    @FXML
    private Menu openRecentMenu;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private MenuItem backMenuItem;

    @FXML
    private MenuItem forwardMenuItem;

    @FXML
    private MenuItem closeFileMenuItem;

    @FXML
    private MenuItem closeAllFilesItem;

    @FXML
    private MenuItem saveCurrentFileMenuItem;

    @FXML
    private MenuItem saveAllFilesMenuItem;

    @FXML
    private Menu openRecentProjectsMenuItem;

    @FXML
    private MenuItem saveProjectMenuItem;

    @FXML
    private Label statusMessageLabel;

    @FXML
    private Hyperlink statusLink;

    @FXML
    private Label focusLabel;

    @FXML
    private Hyperlink assistStatusLink;

    @FXML
    private Pane viewPane;

    private final UserSettings settings;
    private Stage primaryStage;
    private final List<GrammarPlugin> plugins;
    private final AssistController assist;
    private final Workbench workbench;
    private final WorkbenchCommands commands;
    private ModeHost modes;

    public MainController(UserSettings settings, List<GrammarPlugin> plugins) {
        this.settings = settings;
        this.plugins = plugins;
        this.assist = new AssistController(settings, () -> primaryStage);
        this.workbench = new Workbench(settings, assist, () -> primaryStage, Platform::runLater);
        this.commands = new WorkbenchCommands(workbench);
        Project project = null;
        List<Path> recentProjects = settings.recentProjects().existing();
        if (settings.openLastProjectProperty().get() && !recentProjects.isEmpty()) {
            File lastProjectFile = recentProjects.get(0).toFile();
            try {
                project = Project.loadProject(plugins, lastProjectFile, settings.documentOptions());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot reopen the last project " + lastProjectFile, e);
            }
        }
        if (project == null) {
            project = Project.createDefaultProject(plugins, settings.documentOptions());
        }
        workbench.session().setProject(project);
    }

    private Project project() {
        return workbench.session().project();
    }

    @FXML
    public void initialize() {
        modes = new ModeHost(workbench, Modes.builtIn(), viewPane, toolBar, menuBar, viewMenu);
        workbench.navigator().attach(modes);
        commands.attach(modes);
        modes.activate(Modes.FILE);

        commands.bindMenus(saveCurrentFileMenuItem, saveAllFilesMenuItem, closeFileMenuItem, closeAllFilesItem,
            undoMenuItem, redoMenuItem, backMenuItem, forwardMenuItem);
        backMenuItem.setAccelerator(BACK);
        forwardMenuItem.setAccelerator(FORWARD);
        backButton.disableProperty().bind(backMenuItem.disableProperty());
        forwardButton.disableProperty().bind(forwardMenuItem.disableProperty());
        backButton.setTooltip(new Tooltip("Back (" + BACK.getDisplayText() + ")"));
        forwardButton.setTooltip(new Tooltip("Forward (" + FORWARD.getDisplayText() + ")"));
        // Ctrl+Z not handled by the focused control (editor, table, text field) undoes in the journal
        mainBorderPane.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (UNDO.match(e)) {
                commands.undo();
                e.consume();
            } else if (REDO.match(e) || REDO_ALT.match(e)) {
                commands.redo();
                e.consume();
            }
        });
        setupStatusBar();

        loadRecentFilesMenu();
        loadRecentProjectsMenu();
        settings.recentFiles().items().addListener((Observable o) -> loadRecentFilesMenu());
        settings.recentProjects().items().addListener((Observable o) -> loadRecentProjectsMenu());
        onProjectChangeHook();
        // problems of the project reopened at startup
        Platform.runLater(this::reportLoadProblems);
    }

    /** Messages of the status line on the left; the focused text and the assistant on the right. */
    private void setupStatusBar() {
        var status = workbench.status();
        statusMessageLabel.textProperty().bind(status.messageProperty());
        statusMessageLabel.styleProperty().bind(javafx.beans.binding.Bindings.when(status.warningProperty())
            .then("-fx-font-size: 11; -fx-text-fill: #b00000;").otherwise("-fx-font-size: 11;"));
        statusLink.textProperty().bind(status.linkTextProperty());
        statusLink.visibleProperty().bind(status.linkTextProperty().isNotEmpty());
        statusLink.managedProperty().bind(statusLink.visibleProperty());
        statusLink.setOnAction(e -> {
            statusLink.setVisited(false);
            status.followLink();
        });
        modes.focusProperty().addListener((obs, o, n) -> focusLabel.setText(describe(n)));
        focusLabel.setText(describe(modes.focus()));
        assistStatusLink.textProperty().bind(assist.statusProperty());
        assistStatusLink.setOnAction(e -> {
            assistStatusLink.setVisited(false);
            assist.openSettings();
        });
    }

    /** "notes.txt:12" for the focus of the active mode. */
    private String describe(Optional<Focus> focus) {
        Project project = project();
        if (focus.isEmpty() || project == null) return "";
        return project.document(focus.get().text()).map(d -> {
            String content = d.getCurrentText();
            int end = Math.min(focus.get().start(), content.length());
            int line = 1;
            for (int i = 0; i < end; i++) {
                if (content.charAt(i) == '\n') line++;
            }
            Path name = d.getFilePath().getFileName();
            return (name == null ? d.getFilePath().toString() : name.toString()) + ":" + line;
        }).orElse("");
    }

    private void onProjectChangeHook() {
        saveProjectMenuItem.disableProperty().bind(project().fileProperty().isNotNull());
        project().isProjectDefinitionOutdatedProperty().set(true);
    }

    // ---------------------------------------------
    // Assistant
    // ---------------------------------------------

    public void handleAiAssistant() {
        assist.openSettings();
    }

    public void handleRefreshExamples() {
        assist.refreshExamples();
    }

    /** Stops the background threads and closes the media players. */
    public void shutdown() {
        workbench.close();
    }

    // ---------------------------------------------
    // Edit and View
    // ---------------------------------------------

    public void handlePlaySegmentMenuItem() {
        commands.playSegment();
    }

    public void handleUndoMenuItem() {
        commands.undo();
    }

    public void handleRedoMenuItem() {
        commands.redo();
    }

    public void handleBack() {
        commands.back();
    }

    public void handleForward() {
        commands.forward();
    }

    public void handleExportDictionary() {
        //TODO
    }

    // ---------------------------------------------
    // Files
    // ---------------------------------------------

    public void handleOpenFileWithFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        commands.addFileOrDirectory(chooser.showOpenDialog(primaryStage));
    }

    public void handleOpenDirectory() {
        commands.addDirectory();
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
                item.setOnAction(e -> commands.addFileOrDirectory(path.toFile()));
                openRecentMenu.getItems().add(item);
            }
            openRecentMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem("Clear Recent Files");
            clearItem.setOnAction(e -> settings.recentFiles().clear());
            openRecentMenu.getItems().add(clearItem);
        }
    }

    public void handleCloseFile() {
        commands.closeFile();
    }

    public void handleSaveCurrentFile() {
        commands.saveCurrentFile();
    }

    /** Saves every file with unsaved changes, displayed or not. */
    public void handleSaveAllFiles() {
        commands.saveAll();
    }

    public void handleCloseAllFiles() {
        commands.closeAllFiles();
    }

    /** Asks what to do with unsaved files before they are dropped; false to cancel. */
    private boolean confirmDropDocuments(String action) {
        return project() == null
            || UnsavedChangesDialog.confirm(primaryStage, List.copyOf(project().getDerivedFileList()), action);
    }

    /** Called when the main window is about to close: false keeps it open. */
    public boolean confirmQuit() {
        return confirmDropDocuments("quitting");
    }

    // ---------------------------------------------
    // Project
    // ---------------------------------------------

    private void openProject(File f) {
        if (!confirmDropDocuments("opening another project")) {
            return;
        }
        try {
            Project project = Project.loadProject(this.plugins, f, settings.documentOptions());
            workbench.session().setProject(project);
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
        if (!project().getLoadProblems().isEmpty()) {
            ErrorReporter.report(
                primaryStage,
                "Some parts of the project could not be loaded",
                String.join("\n\n", project().getLoadProblems()) +
                    "\n\nCorrect them in File › Edit project…"
            );
        }
    }

    public void handleOpenProject() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Matchbox project files", "*.mbx"),
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
        if (project().fileProperty().isNull().get()) {
            handleSaveAsProject();
            return;
        }
        try {
            ProjectXMLSerializer.save(project());
        } catch (Exception e) {
            // the project stays marked as modified
            ErrorReporter.report(primaryStage, "Error saving project", e);
            return;
        }
        project().isProjectDefinitionOutdatedProperty().set(false);
    }

    public void handleNewProject() {
        handleCloseCurrentProject();
        workbench.session().setProject(Project.createDefaultProject(plugins, settings.documentOptions()));
        onProjectChangeHook();
    }

    public void handleEditProject() {
        ProjectPropertiesDialog dialog = new ProjectPropertiesDialog(primaryStage, project());
        dialog.showAndWait();
        if (dialog.isSaved()) {
            onProjectChangeHook();
            // the project may now allow local models only
            assist.updateStatus();
        }
    }

    public void handleCloseCurrentProject() {
        if (!confirmDropDocuments("closing the project")) {
            return;
        }
        if (project().isProjectDefinitionOutdatedProperty().get()) {
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
        workbench.session().setProject(Project.createDefaultProject(plugins, settings.documentOptions()));
        onProjectChangeHook();
    }

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
            openRecentProjectsMenuItem.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem("Clear Recent Projects");
            clearItem.setOnAction(e -> settings.recentProjects().clear());
            openRecentProjectsMenuItem.getItems().add(clearItem);
        }
    }

    public void handleSaveAsProject() {
        CreateProjectDialog dialog = new CreateProjectDialog(primaryStage, project());
        dialog.showAndWait();
    }

    public void handleSettings() {
        SettingsDialog dialog = new SettingsDialog(settings, primaryStage);
        dialog.showAndWait();
    }

    public void handleQuit() {
        if (confirmQuit()) {
            Platform.exit();
        }
    }

    /** Lets the views open links in the system browser. */
    public void setHostServices(javafx.application.HostServices hostServices) {
        workbench.setHostServices(hostServices);
    }

    /** The window's services (for the GUI tests). */
    Workbench workbench() {
        return workbench;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}
