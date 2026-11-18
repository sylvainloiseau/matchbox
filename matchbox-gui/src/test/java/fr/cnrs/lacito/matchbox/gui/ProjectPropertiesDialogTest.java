package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.properties.ProjectPropertiesDialog;
import fr.cnrs.lacito.matchbox.gui.properties.ProjectPropertiesPage;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/**
 * The tabs of Project properties (shown non-modal for the test): each tab, the markers, Save with
 * problems, the links between tabs and to tabs. Controls are fired, not clicked.
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
    private ProjectPropertiesDialog properties;

    private void open(FxRobot robot) {
        open(robot, null);
    }

    private void open(FxRobot robot, ProjectPropertiesPage page) {
        robot.interact(() -> {
            properties = new ProjectPropertiesDialog(stage, project(), page);
            dialog = properties.show();
        });
    }

    private void select(FxRobot robot, ProjectPropertiesPage page) {
        robot.interact(() -> properties.select(page));
    }

    private TabPane tabPane() {
        return (TabPane) dialog.getScene().lookup("#projectPropertiesTabs");
    }

    private Tab tab(ProjectPropertiesPage page) {
        return tabPane().getTabs().stream().filter(t -> page.nodeId().equals(t.getId())).findFirst().orElseThrow();
    }

    private ProjectPropertiesPage selected() {
        return (ProjectPropertiesPage) tabPane().getSelectionModel().getSelectedItem().getUserData();
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
            select(robot, ProjectPropertiesPage.PLUGINS);
            Button display = child(row(robot, "#pluginParameters", name), Button.class);
            assertEquals("Display…", display.getText());
            assertFalse(display.isDisable(), "checked in a file type");

            select(robot, ProjectPropertiesPage.FILE_TYPES);
            robot.interact(child(row(robot, "#grammarList", name), CheckBox.class)::fire);
            select(robot, ProjectPropertiesPage.PLUGINS);
            waitUntil(() -> child(row(robot, "#pluginParameters", name), Button.class).isDisable());
            select(robot, ProjectPropertiesPage.FILE_TYPES);
            robot.interact(child(row(robot, "#grammarList", name), CheckBox.class)::fire);
            select(robot, ProjectPropertiesPage.PLUGINS);
            waitUntil(() -> !child(row(robot, "#pluginParameters", name), Button.class).isDisable());
            select(robot, ProjectPropertiesPage.FILE_TYPES);

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
        open(robot, ProjectPropertiesPage.PLUGINS);
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

    @Test
    void theInheritedContextShowsTheFilesAndCanBeCleared(FxRobot robot) {
        Path lift = Path.of("context", "dictionary.lift").toAbsolutePath();
        robot.interact(() -> project().setContextDictionary(lift));
        open(robot, ProjectPropertiesPage.CORPUS);
        try {
            TextField dictionary = robot.lookup("#contextDictionaryField").queryAs(TextField.class);
            TextField corpus = robot.lookup("#contextCorpusField").queryAs(TextField.class);
            assertEquals(lift.toString(), dictionary.getText());
            assertFalse(dictionary.isEditable(), "chosen with the file chooser only");
            assertEquals("", corpus.getText());
            assertTrue(robot.lookup("#contextCorpusClear").queryButton().isDisable(), "nothing to clear");
            assertTrue(robot.lookup(".label").queryAllAs(Label.class).stream()
                .anyMatch(l -> "Inherited context".equals(l.getText())));

            robot.interact(robot.lookup("#contextDictionaryClear").queryButton()::fire);
            assertEquals("", dictionary.getText());
            assertEquals(Optional.of(lift), project().getContextDictionary(), "applied only by Save");
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void theSectionsAreInFiveTabsAndTheDialogOpensOnTheOneAskedFor(FxRobot robot) {
        open(robot);
        try {
            assertEquals(List.of("General", "File types", "Plugins", "Corpus", "AI assistant"),
                tabPane().getTabs().stream().map(Tab::getText).toList());
            assertEquals(ProjectPropertiesPage.GENERAL, selected(), "General by default");
            assertTrue(tabPane().getTabs().stream().noneMatch(Tab::isClosable));
        } finally {
            robot.interact(dialog::close);
        }
        open(robot, ProjectPropertiesPage.CORPUS);
        try {
            assertEquals(ProjectPropertiesPage.CORPUS, selected());
            select(robot, ProjectPropertiesPage.ASSISTANT);
            assertEquals(ProjectPropertiesPage.ASSISTANT, properties.selectedPage(), "remembered by the window");
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void saveAndCancelStayVisibleBelowTheTabs(FxRobot robot) {
        open(robot, ProjectPropertiesPage.CORPUS);
        try {
            robot.interact(() -> {
                dialog.setWidth(dialog.getMinWidth());
                dialog.setHeight(dialog.getMinHeight());
            });
            for (String id : List.of("#projectPropertiesSave", "#projectPropertiesCancel")) {
                Button b = (Button) dialog.getScene().lookup(id);
                for (Node n = b; n != null; n = n.getParent()) {
                    assertFalse(n instanceof ScrollPane || n instanceof TabPane, id + " is outside the tabs and scroll panes");
                }
                var bounds = b.localToScene(b.getBoundsInLocal());
                assertTrue(bounds.getMaxY() <= dialog.getScene().getHeight() + 0.5, id + " fits the dialog");
            }
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void aChangedTabIsMarkedUntilTheValueIsPutBack(FxRobot robot) {
        String name = project().getName();
        open(robot);
        try {
            TextField field = (TextField) dialog.getScene().lookup("#projectName");
            robot.interact(() -> field.setText("Other name"));
            assertEquals("General •", tab(ProjectPropertiesPage.GENERAL).getText());
            assertEquals("Corpus", tab(ProjectPropertiesPage.CORPUS).getText());
            robot.interact(() -> field.setText(name == null ? "" : name));
            assertEquals("General", tab(ProjectPropertiesPage.GENERAL).getText());
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void saveWithAProblemShowsItsFieldAndSavesNothing(FxRobot robot) {
        String regex = project().getFileTypes().get(0).getRegex();
        String name = project().getName();
        open(robot, ProjectPropertiesPage.FILE_TYPES);
        try {
            TextField regexField = (TextField) dialog.getScene().lookup("#fileTypeRegex");
            robot.interact(() -> regexField.setText("*.md("));
            assertEquals("File types • ⚠", tab(ProjectPropertiesPage.FILE_TYPES).getText());
            assertTrue(dialog.getScene().lookup("#projectPropertiesProblem").isVisible());
            select(robot, ProjectPropertiesPage.GENERAL);
            robot.interact(() -> ((TextField) dialog.getScene().lookup("#projectName")).setText("Changed"));
            select(robot, ProjectPropertiesPage.ASSISTANT);

            robot.interact(((Button) dialog.getScene().lookup("#projectPropertiesSave"))::fire);
            assertTrue(dialog.isShowing(), "not saved");
            assertEquals(ProjectPropertiesPage.FILE_TYPES, selected());
            waitUntil(() -> dialog.getScene().getFocusOwner() == regexField);
            assertEquals(regex, project().getFileTypes().get(0).getRegex());
            assertEquals(name, project().getName(), "nothing applied");
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void cancelAfterEditsInSeveralTabsLeavesTheProjectUnchanged(FxRobot robot) {
        String name = project().getName();
        boolean cloud = project().isCloudAssistAllowed();
        open(robot);
        try {
            robot.interact(() -> ((TextField) dialog.getScene().lookup("#projectName")).setText("Changed"));
            select(robot, ProjectPropertiesPage.ASSISTANT);
            robot.interact(((CheckBox) dialog.getScene().lookup("#cloudAssistAllowed"))::fire);
            assertEquals("AI assistant •", tab(ProjectPropertiesPage.ASSISTANT).getText());
            robot.interact(((Button) dialog.getScene().lookup("#projectPropertiesCancel"))::fire);
            assertFalse(dialog.isShowing());
            assertEquals(name, project().getName());
            assertEquals(cloud, project().isCloudAssistAllowed());
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void linksGoFromOneTabToTheOther(FxRobot robot) {
        GrammarPlugin plugin = onlyInFirstFileType();
        String name = plugin.getDisplayName();
        open(robot, ProjectPropertiesPage.FILE_TYPES);
        try {
            robot.interact(child(row(robot, "#grammarList", name), Hyperlink.class)::fire);
            assertEquals(ProjectPropertiesPage.PLUGINS, selected(), "Display… of a checked plugin");
            @SuppressWarnings("unchecked")
            var list = (javafx.scene.control.ListView<Object>) dialog.getScene().lookup("#pluginParameters");
            assertEquals(name, String.valueOf(list.getSelectionModel().getSelectedItem()), "its row is selected");

            select(robot, ProjectPropertiesPage.FILE_TYPES);
            robot.interact(child(row(robot, "#grammarList", name), CheckBox.class)::fire);
            select(robot, ProjectPropertiesPage.PLUGINS);
            waitUntil(() -> child(row(robot, "#pluginParameters", name), Hyperlink.class).isVisible());
            robot.interact(child(row(robot, "#pluginParameters", name), Hyperlink.class)::fire);
            assertEquals(ProjectPropertiesPage.FILE_TYPES, selected(), "\"not used\" leads to File types");
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void theDictionaryCheckSaysWhenThereIsNoContextDictionary(FxRobot robot) {
        Path lift = Path.of("context", "dictionary.lift").toAbsolutePath();
        open(robot, ProjectPropertiesPage.CORPUS);
        try {
            Node hint = dialog.getScene().lookup("#glossDictionaryHint");
            CheckBox check = (CheckBox) dialog.getScene().lookup("#glossDictionaryCheck");
            assertTrue(hint.isVisible(), "no context dictionary");
            assertFalse(check.isDisable(), "the setting is kept for later");
        } finally {
            robot.interact(dialog::close);
        }
        robot.interact(() -> project().setContextDictionary(lift));
        open(robot, ProjectPropertiesPage.CORPUS);
        try {
            Node hint = dialog.getScene().lookup("#glossDictionaryHint");
            assertFalse(hint.isVisible(), "a context dictionary is chosen");
            robot.interact(robot.lookup("#contextDictionaryClear").queryButton()::fire);
            assertTrue(hint.isVisible(), "cleared in the same tab");
            assertEquals("Corpus •", tab(ProjectPropertiesPage.CORPUS).getText());
        } finally {
            robot.interact(dialog::close);
        }
    }

    @Test
    void theStatusLineLinkOpensTheCorpusTab(FxRobot robot) {
        List<ProjectPropertiesPage> opened = new ArrayList<>();
        robot.interact(() -> {
            controller.setProjectPropertiesOpener(opened::add);
            controller.handleReloadDictionary();
        });
        assertEquals("Choose…", workbench().status().linkTextProperty().get());
        robot.interact(() -> workbench().status().followLink());
        assertEquals(List.of(ProjectPropertiesPage.CORPUS), opened);
    }
}
