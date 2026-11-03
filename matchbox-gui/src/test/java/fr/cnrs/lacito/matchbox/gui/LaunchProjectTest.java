package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** Matchbox started with a project file (a double-clicked .mbx), then asked to open another one. */
class LaunchProjectTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    @Override
    protected boolean openProjectAtStart() {
        return false;
    }

    @Override
    protected Optional<Path> launchProject() throws IOException {
        return Optional.of(save("fieldwork"));
    }

    private Path save(String name) throws IOException {
        Project p = Project.createDefaultProject(PluginManager.loadPlugins());
        p.setName(name);
        Path mbx = dir.resolve(name + ".mbx");
        p.setFile(mbx.toFile());
        try {
            ProjectXMLSerializer.save(p);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return mbx;
    }

    @Test
    void theLaunchProjectIsOpenInsteadOfTheStartPage(FxRobot robot) {
        assertNotNull(project());
        assertEquals("fieldwork", project().getName());
        assertFalse(robot.lookup("#startPage").query().isVisible());
        assertEquals(dir.resolve("fieldwork.mbx"), settings.recentProjects().items().getFirst());
    }

    @Test
    void aProjectFileReceivedLaterReplacesTheOpenProject(FxRobot robot) throws IOException {
        Path other = save("other");
        robot.interact(() -> controller.openProjectFile(other.toFile()));
        waitUntil(() -> project() != null && "other".equals(project().getName()));
        assertEquals("Matchbox — other", stage.getTitle());
    }
}
