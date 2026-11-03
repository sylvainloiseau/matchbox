package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.gui.close.CloseChoice;
import fr.cnrs.lacito.matchbox.gui.close.ProjectCloser;
import fr.cnrs.lacito.matchbox.gui.close.UnsavedProjectState;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree;
import fr.cnrs.lacito.matchbox.model.project.DirectorySettings;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.AppDirectories;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The real main window, as {@code MatchboxApplication} builds it, shown off-screen (Monocle, see
 * the surefire configuration) with settings in a temporary directory and the grammars of the test
 * class path. A new window for each test. Subclasses declare {@code @Start} and {@code @Stop}
 * methods calling {@link #startWindow} and {@link #stopWindow}.
 *
 * <p>Tagged {@code gui}: {@code mvn test -DexcludedGroups=gui} leaves these tests out. Where JavaFX
 * cannot start headless, they are skipped (not failed), with the reason.
 */
@Tag("gui")
@ExtendWith(ApplicationExtension.class)
abstract class MainWindowFixture {

    /** Skips the tests of the class if the JavaFX toolkit cannot start here. */
    @BeforeAll
    static void requireJavaFx() {
        try {
            FxToolkit.registerPrimaryStage();
        } catch (Throwable e) {
            // surefire shows a class skipped here as "Tests run: 0": say why in the build log
            String reason = "GUI tests skipped: JavaFX cannot start headless on this machine (" + e + ")";
            System.err.println("WARNING: " + reason);
            Assumptions.abort(reason);
        }
    }

    @TempDir
    Path dir;
    protected MainController controller;
    protected Stage stage;
    protected UserSettings settings;
    /** The questions asked before a project was closed (the dialog is not shown in tests). */
    protected final List<UnsavedProjectState> closeQuestions = new ArrayList<>();
    /** The answer to those questions: by default, save nothing and go on. */
    protected Optional<CloseChoice> closeAnswer = Optional.of(new CloseChoice(false, List.of()));
    /** The directory "chosen" by Add files in directory… (no file chooser in tests). */
    protected java.io.File chosenDirectory;
    /** The answer of the directory settings dialog: by default, the settings it was given. */
    protected java.util.function.UnaryOperator<DirectorySettings> directorySettingsAnswer = s -> s;
    /** The confirmations asked by the project files service (all accepted). */
    protected final List<String> confirmations = new ArrayList<>();

    /** Builds the window; call it from the test class's {@code @Start} method (TestFX ignores inherited ones). */
    protected void startWindow(Stage stage) throws IOException {
        this.stage = stage;
        Path settingsDir = Files.createDirectories(dir.resolve("settings"));
        // loading the settings imports and deletes the legacy files of user.home: use a temporary one
        String home = System.getProperty("user.home");
        System.setProperty("user.home", settingsDir.toString());
        try {
            settings = UserSettings.load(new AppDirectories(settingsDir, settingsDir, settingsDir));
        } finally {
            System.setProperty("user.home", home);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fr/cnrs/lacito/matchbox/gui/MainView.fxml"));
        controller = new MainController(settings, PluginManager.loadPlugins(), launchProject());
        loader.setController(controller);
        Parent root = loader.load();
        controller.setPrimaryStage(stage);
        controller.setCloser(new ProjectCloser((state, action) -> {
            closeQuestions.add(state);
            return closeAnswer;
        }, p -> true, (title, e) -> {
            throw new AssertionError(title, e);
        }));
        controller.workbench().files().setDialogs(
            (directory, initial, current, adding) -> Optional.ofNullable(directorySettingsAnswer.apply(initial)),
            (title, header, content, action) -> confirmations.add(header),
            (documents, action) -> true,
            () -> chosenDirectory);
        if (openProjectAtStart()) {
            controller.workbench().session().setProject(
                Project.createDefaultProject(PluginManager.loadPlugins(), settings.documentOptions()));
        }
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
    }

    /**
     * True (default) to start with a new project, as most tests need; false to start like the
     * application without "Reopen the last project": the start page.
     */
    protected boolean openProjectAtStart() {
        return true;
    }

    /** The project Matchbox is started with (a double-clicked .mbx file); none by default. */
    protected Optional<Path> launchProject() throws IOException {
        return Optional.empty();
    }

    /** Call it from the test class's {@code @Stop} method. */
    protected void stopWindow() {
        controller.shutdown();
        settings.close();
    }

    protected Workbench workbench() {
        return controller.workbench();
    }

    protected Project project() {
        return workbench().session().project();
    }

    /** Writes a file in the temporary directory and adds it to the project (it is parsed at once). */
    protected Path addFile(FxRobot robot, String name, String content) throws IOException {
        Path file = Files.writeString(dir.resolve(name), content);
        robot.interact(() -> {
            try {
                project().addFile(file);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        return file;
    }

    protected TextDocument document(Path file) {
        return project().getParsedText(file);
    }

    /** Waits (5 s at most) until the condition holds on the JavaFX thread. */
    protected static void waitUntil(BooleanSupplier condition) {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
                WaitForAsyncUtils.waitForFxEvents();
                return WaitForAsyncUtils.asyncFx(condition::getAsBoolean).get();
            });
        } catch (TimeoutException e) {
            throw new AssertionError("Condition not met in 5 s", e);
        }
    }

    /** The cell of a node of File Mode's file tree (it must be visible). */
    protected Node treeCell(FxRobot robot, Path path) {
        return robot.lookup("#fileTreeView .tree-cell").match((Node n) ->
            n instanceof javafx.scene.control.TreeCell<?> c && c.getItem() instanceof FileTree.Node node
                && node.path().equals(path)).query();
    }

    /** Shows a file in File Mode by clicking it in the file tree. */
    protected void showFile(FxRobot robot, Path file) {
        robot.clickOn(treeCell(robot, file));
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(file).getId())).orElse(false));
    }
}
