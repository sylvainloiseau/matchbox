package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import java.io.IOException;
import java.util.List;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/**
 * The Plugin parameters section of Project properties (shown non-modal for the test). The section
 * may be below the visible part of the dialog: its controls are fired, not clicked.
 */
class ProjectPropertiesDialogTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private Stage dialog;

    private void open(FxRobot robot) {
        robot.interact(() -> dialog = new ProjectPropertiesDialog(stage, project()).show());
    }

    /** The row of a plugin, found by its displayed name, in a list of the dialog. */
    private HBox row(FxRobot robot, String list, String name) {
        return (HBox) robot.lookup(list + " .list-cell").match((Node n) -> n instanceof ListCell<?> c
                && c.getGraphic() instanceof HBox box && box.getChildren().stream()
                .anyMatch(ch -> ch instanceof Label l && l.getText() != null && l.getText().contains(name)))
            .queryAs(ListCell.class).getGraphic();
    }

    private static <T> T child(HBox box, Class<T> type) {
        return box.getChildren().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    /** A plugin checked only in the first file type (the one the dialog shows first). */
    private GrammarPlugin onlyInFirstFileType() {
        List<String> others = project().getFileTypes().stream().skip(1).flatMap(t -> t.getGrammars().stream())
            .map(GrammarPlugin::getId).toList();
        return project().getFileTypes().get(0).getGrammars().stream()
            .filter(g -> !others.contains(g.getId())).findFirst().orElseThrow();
    }

    @Test
    void displayIsEnabledForPluginsCheckedInAFileType(FxRobot robot) throws IOException {
        open(robot);
        try {
            assertTrue(robot.lookup(".label").queryAllAs(Label.class).stream().noneMatch(l -> "Fileset".equals(l.getText())),
                "no Fileset section");
            GrammarPlugin plugin = onlyInFirstFileType();
            String name = plugin.getDisplayName();
            Button display = child(row(robot, "#pluginParameters", name), Button.class);
            assertEquals("Display…", display.getText());
            assertFalse(display.isDisable(), "checked in a file type");

            robot.interact(child(row(robot, "#grammarList", name), CheckBox.class)::fire);
            waitUntil(() -> child(row(robot, "#pluginParameters", name), Button.class).isDisable());
            robot.interact(child(row(robot, "#grammarList", name), CheckBox.class)::fire);
            waitUntil(() -> !child(row(robot, "#pluginParameters", name), Button.class).isDisable());

            assertTrue(robot.lookup("#grammarList .scroll-bar").queryAllAs(ScrollBar.class).stream()
                .noneMatch(b -> b.getOrientation() == Orientation.HORIZONTAL && b.isVisible()),
                "the grammar list does not scroll sideways");
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void stylesOfAPluginThatIsNotInstalledCanBeForgotten(FxRobot robot) {
        robot.interact(() -> project().setDisplayStyles(DisplayStyles.NONE
            .with(DisplayStyles.Key.grammar("old-grammar"), DisplayStyle.EMPTY.withBold(true))));
        open(robot);
        try {
            HBox row = row(robot, "#pluginParameters", "old-grammar");
            Button forget = child(row, Button.class);
            assertEquals("Forget", forget.getText());
            robot.interact(forget::fire);
            waitUntil(() -> robot.lookup("#pluginParameters .list-cell").queryAllAs(ListCell.class).stream()
                .noneMatch(c -> c.getItem() != null && c.getItem().toString().contains("old-grammar")));
            assertFalse(project().getDisplayStyles().isEmpty(), "applied only by Save");
        } finally {
            robot.interact(dialog::close);
        }
    }
}
