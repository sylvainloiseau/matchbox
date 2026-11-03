package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.assist.AssistController;
import fr.cnrs.lacito.matchbox.gui.close.CloseAction;
import fr.cnrs.lacito.matchbox.gui.close.CloseProjectDialog;
import fr.cnrs.lacito.matchbox.gui.close.ProjectCloser;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.ModeHost;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.start.StartPage;
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
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanExpression;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The main window: its menus, toolbar and status bar, and the window's services
 * ({@link Workbench}) shared by the modes shown in the main area ({@link ModeHost}). The window's
 * menus are mode-independent ({@link WorkbenchCommands}); each mode adds its own menu.
 *
 * <p>The window may have no project: the main area then shows the {@link StartPage}, and every
 * command that needs a project is disabled. Closing a project (Close, New, Open, Quit) asks what
 * to save ({@link ProjectCloser}).
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
    private static final String APPLICATION = "Matchbox";

    @FXML
    private VBox mainBorderPane;

    @FXML
    private MenuBar menuBar;

    @FXML
    private Menu currentProjectMenu;

    @FXML
    private Menu editMenu;

    @FXML
    private Menu modeMenu;

    @FXML
    private Menu exportMenu;

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
    private MenuItem removeFileMenuItem;

    @FXML
    private MenuItem removeDirectoryMenuItem;

    @FXML
    private MenuItem removeAllFilesMenuItem;

    @FXML
    private MenuItem saveCurrentFileMenuItem;

    @FXML
    private MenuItem saveAllFilesMenuItem;

    @FXML
    private Menu openRecentProjectsMenuItem;

    @FXML
    private MenuItem saveProjectMenuItem;

    @FXML
    private MenuItem saveAsProjectMenuItem;

    @FXML
    private MenuItem closeCurrentProjectMenuItem;

    @FXML
    private MenuItem refreshExamplesItem;

    @FXML
    private Label statusMessageLabel;

    @FXML
    private Hyperlink statusLink;

    @FXML
    private Label focusLabel;

    @FXML
    private Hyperlink assistStatusLink;

    @FXML
    private StackPane mainArea;

    @FXML
    private Pane viewPane;

    private final UserSettings settings;
    private Stage primaryStage;
    private final List<GrammarPlugin> plugins;
    private final AssistController assist;
    private final Workbench workbench;
    private final WorkbenchCommands commands;
    private ModeHost modes;
    private StartPage startPage;
    private ProjectCloser closer;
    /** The last project could not be reopened at startup (reported once the window is shown). */
    private Path startupFailurePath;
    private Exception startupFailure;
    /** Updates the window title when the project's unsaved state changes. */
    private final InvalidationListener titleUpdater = o -> updateTitle();

    public MainController(UserSettings settings, List<GrammarPlugin> plugins) {
        this(settings, plugins, Optional.empty());
    }

    /**
     * @param launchProject the project Matchbox was started with (a double-clicked .mbx file);
     *        opened instead of the last project
     */
    public MainController(UserSettings settings, List<GrammarPlugin> plugins, Optional<Path> launchProject) {
        this.settings = settings;
        this.plugins = plugins;
        this.assist = new AssistController(settings, () -> primaryStage);
        this.workbench = new Workbench(settings, assist, () -> primaryStage, Platform::runLater);
        this.commands = new WorkbenchCommands(workbench);
        this.closer = new ProjectCloser(
            (state, action) -> CloseProjectDialog.ask(primaryStage, state, action, projectDirectory()),
            this::saveProject,
            (title, e) -> ErrorReporter.report(primaryStage, title, e));
        // the project Matchbox was started with, else the last one if it is to be reopened,
        // else no project: the start page is shown
        List<Path> recentProjects = settings.recentProjects().existing();
        Optional<Path> atStart = launchProject.isPresent() ? launchProject
            : settings.openLastProjectProperty().get() && !recentProjects.isEmpty()
                ? Optional.of(recentProjects.get(0)) : Optional.empty();
        atStart.ifPresent(path -> {
            try {
                workbench.session().setProject(Project.loadProject(plugins, path.toFile(), settings.documentOptions()));
                settings.recentProjects().add(path);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot open the project " + path + " at startup", e);
                startupFailurePath = path;
                startupFailure = e;
            }
        });
    }

    private Project project() {
        return workbench.session().project();
    }

    @FXML
    public void initialize() {
        modes = new ModeHost(workbench, Modes.builtIn(), viewPane, toolBar, menuBar, modeMenu);
        workbench.navigator().attach(modes);
        commands.attach(modes);
        modes.activate(Modes.FILE);

        commands.bindMenus(saveCurrentFileMenuItem, saveAllFilesMenuItem, removeFileMenuItem, removeDirectoryMenuItem,
            removeAllFilesMenuItem,
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
        setupStartPage();
        bindToProjectOpen();
        setupProjectDrop();

        loadRecentFilesMenu();
        loadRecentProjectsMenu();
        settings.recentFiles().items().addListener((Observable o) -> loadRecentFilesMenu());
        settings.recentProjects().items().addListener((Observable o) -> loadRecentProjectsMenu());
        workbench.session().projectProperty().addListener((obs, o, n) -> {
            if (o != null) unwatchTitle(o);
            if (n != null) watchTitle(n);
            updateTitle();
        });
        if (project() != null) watchTitle(project());
        // problems of the project reopened at startup, or why it could not be reopened
        Platform.runLater(() -> {
            if (startupFailure != null) {
                reportOpenFailure(startupFailurePath, startupFailure);
                startupFailure = null;
            } else {
                reportLoadProblems();
            }
        });
    }

    /** The start page covers the mode area while no project is open. */
    private void setupStartPage() {
        startPage = new StartPage(settings, this::handleNewProject, this::handleOpenProject,
            p -> openProject(p.toFile()));
        mainArea.getChildren().add(startPage);
        BooleanExpression open = workbench.session().projectOpenProperty();
        startPage.visibleProperty().bind(open.not());
        viewPane.visibleProperty().bind(open);
    }

    /** Everything that needs a project is disabled while none is open. */
    private void bindToProjectOpen() {
        BooleanExpression none = workbench.session().projectOpenProperty().not();
        for (Menu m : List.of(currentProjectMenu, editMenu, modeMenu, exportMenu)) {
            m.disableProperty().bind(none);
        }
        for (MenuItem i : List.of(saveProjectMenuItem, saveAsProjectMenuItem, closeCurrentProjectMenuItem,
                refreshExamplesItem)) {
            i.disableProperty().bind(none);
        }
        modes.enabledProperty().bind(none.not());
    }

    /** A project file (.mbx) dropped on the window is opened. */
    private void setupProjectDrop() {
        mainBorderPane.setOnDragOver(e -> {
            if (droppedProject(e.getDragboard().getFiles()).isPresent()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        mainBorderPane.setOnDragDropped(e -> {
            Optional<File> file = droppedProject(e.getDragboard().getFiles());
            e.setDropCompleted(file.isPresent());
            e.consume();
            // after the drop has returned: opening shows dialogs
            file.ifPresent(f -> Platform.runLater(() -> openProject(f)));
        });
    }

    private static Optional<File> droppedProject(List<File> files) {
        return files == null ? Optional.empty()
            : files.stream().filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".mbx")).findFirst();
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

    // ---------------------------------------------
    // Window title
    // ---------------------------------------------

    private void watchTitle(Project p) {
        p.definitionChangedProperty().addListener(titleUpdater);
        p.anyDocumentDirtyProperty().addListener(titleUpdater);
        p.fileProperty().addListener(titleUpdater);
    }

    private void unwatchTitle(Project p) {
        p.definitionChangedProperty().removeListener(titleUpdater);
        p.anyDocumentDirtyProperty().removeListener(titleUpdater);
        p.fileProperty().removeListener(titleUpdater);
    }

    /** "Matchbox — fieldwork •" ("•": something is unsaved); "Matchbox" without project. */
    private void updateTitle() {
        if (primaryStage != null) primaryStage.setTitle(title(project()));
    }

    static String title(Project p) {
        if (p == null) return APPLICATION;
        String name = p.getName() == null || p.getName().isBlank() ? null : p.getName().strip();
        if (name == null && p.fileProperty().get() != null) {
            name = p.fileProperty().get().getName().replaceFirst("\\.mbx$", "");
        }
        boolean unsaved = p.definitionChangedProperty().get() || p.anyDocumentDirtyProperty().get();
        return APPLICATION + " — " + (name == null ? "New project" : name) + (unsaved ? " •" : "");
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
    // Edit and Mode
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
    // Current project: files
    // ---------------------------------------------

    public void handleOpenFileWithFileChooser() {
        if (project() == null) return;
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files != null) files.forEach(commands::addFileOrDirectory);
    }

    public void handleOpenDirectory() {
        if (project() == null) return;
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

    public void handleRemoveFile() {
        commands.removeFile();
    }

    public void handleSaveCurrentFile() {
        commands.saveCurrentFile();
    }

    /** Saves every file with unsaved changes, displayed or not. */
    public void handleSaveAllFiles() {
        commands.saveAll();
    }

    /** Chooses a directory of the project (the one selected in File Mode first) and removes it. */
    public void handleRemoveDirectory() {
        Project project = project();
        if (project == null || project.getDefinitionDirectories().isEmpty()) return;
        List<Path> directories = project.getDefinitionDirectories();
        Path preselected = modes.get(Modes.FILE) instanceof FileMode fileMode
            ? fileMode.selectedDirectory().filter(directories::contains).orElse(directories.get(0))
            : directories.get(0);
        ChoiceDialog<Path> dialog = new ChoiceDialog<>(preselected, directories);
        if (primaryStage != null) dialog.initOwner(primaryStage);
        dialog.setTitle("Remove directory from project");
        dialog.setHeaderText("Which directory should leave the project? Its files stay on the disk.");
        dialog.setContentText("Directory:");
        dialog.showAndWait().ifPresent(workbench.files()::removeDirectory);
    }

    public void handleRemoveAllFiles() {
        commands.removeAllFiles();
    }

    /** Called when the main window is about to close: false keeps it open. */
    public boolean confirmQuit() {
        return closer.confirm(project(), CloseAction.QUIT);
    }

    // ---------------------------------------------
    // Project
    // ---------------------------------------------

    private Optional<Path> projectDirectory() {
        Project p = project();
        if (p == null || p.fileProperty().get() == null) return Optional.empty();
        return Optional.ofNullable(p.fileProperty().get().toPath().toAbsolutePath().getParent());
    }

    /**
     * Opens a project file Matchbox was asked to open from outside (a .mbx file double-clicked
     * while it runs), after asking what to save of the open project. JavaFX thread.
     */
    public void openProjectFile(File f) {
        if (primaryStage != null) {
            primaryStage.setIconified(false);
            primaryStage.toFront();
        }
        if (modes == null) return;
        openProject(f);
    }

    /** Opens a project file, after asking what to save of the open project. */
    private void openProject(File f) {
        if (!closer.confirm(project(), CloseAction.OPEN)) {
            return;
        }
        Project project;
        try {
            project = Project.loadProject(this.plugins, f, settings.documentOptions());
        } catch (Exception e) {
            reportOpenFailure(f.toPath(), e);
            return;
        }
        // the files were parsed with their grammars when the project was loaded
        workbench.session().setProject(project);
        settings.recentProjects().add(f.toPath());
        modes.activate(Modes.FILE);
        reportLoadProblems();
    }

    /** Shows why a project could not be opened; a recent project can be removed from the list. */
    private void reportOpenFailure(Path path, Exception e) {
        boolean recent = settings.recentProjects().items().contains(path);
        if (!recent) {
            ErrorReporter.report(primaryStage, "Error opening project", e);
            return;
        }
        logger.log(Level.WARNING, "Error opening project " + path, e);
        ButtonType remove = new ButtonType("Remove from recent projects", ButtonBar.ButtonData.OTHER);
        Alert alert = new Alert(Alert.AlertType.ERROR, "", remove, ButtonType.CLOSE);
        if (primaryStage != null) alert.initOwner(primaryStage);
        alert.setTitle("Error opening project");
        alert.setHeaderText("The project " + path + " could not be opened.");
        alert.setContentText(e.getMessage() == null ? e.toString() : e.getMessage());
        if (alert.showAndWait().orElse(ButtonType.CLOSE) == remove) {
            settings.recentProjects().remove(path);
        }
    }

    /** Shows the problems found when the project was loaded (unreadable files, invalid patterns). */
    private void reportLoadProblems() {
        Project project = project();
        if (project != null && !project.getLoadProblems().isEmpty()) {
            ErrorReporter.report(
                primaryStage,
                "Some parts of the project could not be loaded",
                String.join("\n\n", project.getLoadProblems()) +
                    "\n\nCorrect the file types and the display in Current project › Project properties…, the"
                    + " directories in the context menu of File Mode's file tree (Directory settings…); add"
                    + " missing files again with Current project › Add file…"
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

    public void handleSaveCurrentProject() {
        saveProject(project());
    }

    /**
     * Saves the project to its file; a project never saved is saved as (a file chooser).
     *
     * @return false if it was not saved (cancelled or failed)
     */
    private boolean saveProject(Project project) {
        if (project == null) return false;
        if (project.fileProperty().get() == null) {
            return saveProjectAs(project);
        }
        try {
            ProjectXMLSerializer.save(project);
            return true;
        } catch (Exception e) {
            // the project stays marked as modified
            ErrorReporter.report(primaryStage, "Error saving project", e);
            return false;
        }
    }

    public void handleSaveAsProject() {
        saveProjectAs(project());
    }

    /** Chooses a file, then saves the project to it; false if cancelled or failed. */
    private boolean saveProjectAs(Project project) {
        if (project == null) return false;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save project as");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Matchbox project files", "*.mbx"));
        File current = project.fileProperty().get();
        if (current != null && current.getParentFile() != null) {
            chooser.setInitialDirectory(current.getParentFile());
            chooser.setInitialFileName(current.getName());
        }
        File selected = chooser.showSaveDialog(primaryStage);
        if (selected == null) return false;
        if (!selected.getName().contains(".")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".mbx");
        }
        File previous = project.fileProperty().get();
        project.setFile(selected);
        try {
            ProjectXMLSerializer.save(project);
        } catch (Exception e) {
            project.setFile(previous);
            ErrorReporter.report(primaryStage, "Error saving project", e);
            return false;
        }
        settings.recentProjects().add(selected.toPath());
        return true;
    }

    /** A new project with the default file types, after asking what to save of the open one. */
    public void handleNewProject() {
        if (!closer.confirm(project(), CloseAction.NEW)) {
            return;
        }
        workbench.session().setProject(Project.createDefaultProject(plugins, settings.documentOptions()));
        modes.activate(Modes.FILE);
    }

    public void handleEditProject() {
        if (project() == null) return;
        ProjectPropertiesDialog dialog = new ProjectPropertiesDialog(primaryStage, project());
        dialog.showAndWait();
        if (dialog.isSaved()) {
            // the name may have changed
            updateTitle();
            // the project may now allow local models only
            assist.updateStatus();
        }
    }

    /** Closes the project, after asking what to save; the start page is then shown. */
    public void handleCloseCurrentProject() {
        if (project() == null || !closer.confirm(project(), CloseAction.CLOSE)) {
            return;
        }
        workbench.session().closeProject();
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

    /** Replaces the question asked before a project is closed (for the GUI tests). */
    void setCloser(ProjectCloser closer) {
        this.closer = closer;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        updateTitle();
    }
}
