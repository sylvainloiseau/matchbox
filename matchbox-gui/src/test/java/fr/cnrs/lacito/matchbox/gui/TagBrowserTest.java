package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.TagBrowserMode;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.OccurrenceRow;
import fr.cnrs.lacito.matchbox.gui.mode.tagBrowser.TagNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The Tag Browser on the real window (TestFX, headless). */
class TagBrowserTest extends MainWindowFixture {

    private static final String A = """
        <mami> {pig} #"pos/noun"

        :::
        a mio-jaflo-m-wi-j
        1sg down-canoe-top-m.sg-pred
        'I went down in canoe' #"pos/verb"
        :::
        """;
    private static final String B = """
        line one
        notes #"pos/verb" and #"pos/verb/transitive"
        #"lexical"
        """;

    private Path a;
    private Path b;

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private void openTags(FxRobot robot) throws IOException {
        a = addFile(robot, "a.txt", A);
        b = addFile(robot, "b.txt", B);
        ToggleButton tags = robot.lookup(".toggle-button").match((Node n) ->
            n instanceof ToggleButton t && "Tags".equals(t.getText())).queryAs(ToggleButton.class);
        robot.clickOn(tags);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(TagBrowserMode.ID));
    }

    private TreeCell<?> cell(FxRobot robot, String level) {
        waitUntil(() -> !robot.lookup(".tree-cell").match((Node n) -> isCell(n, level)).queryAll().isEmpty());
        return robot.lookup(".tree-cell").match((Node n) -> isCell(n, level)).queryAs(TreeCell.class);
    }

    /** Expands a node (the cell is looked up off the JavaFX thread). */
    private void expand(FxRobot robot, String level) {
        TreeCell<?> c = cell(robot, level);
        robot.interact(() -> c.getTreeItem().setExpanded(true));
    }

    private static boolean isCell(Node n, String level) {
        return n instanceof TreeCell<?> c && c.getItem() instanceof TagNode t && t.level().equals(level);
    }

    @SuppressWarnings("unchecked")
    private ListView<OccurrenceRow> occurrences(FxRobot robot) {
        return robot.lookup("#tagOccurrences").queryAs(ListView.class);
    }

    private List<String> menus(FxRobot robot) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream().map(Menu::getText).toList();
    }

    private Label counts(FxRobot robot) {
        return robot.lookup("#tagCounts").queryAs(Label.class);
    }

    // ------------------------------------------------------------------

    @Test
    void theTagsButtonShowsTheModeAndItsMenu(FxRobot robot) throws IOException {
        openTags(robot);
        assertEquals(List.of("File", "Current project", "Edit", "Mode", "Tag Browser", "Assistant", "Export"), menus(robot));
        robot.press(KeyCode.SHORTCUT, KeyCode.DIGIT1).release(KeyCode.DIGIT1, KeyCode.SHORTCUT);
        waitUntil(() -> menus(robot).contains("File Mode"));
        assertEquals(Modes.FILE, workbench().modes().orElseThrow().activeId().orElseThrow());
    }

    @Test
    void theTreeCountsTheTagsOfEachNode(FxRobot robot) throws IOException {
        openTags(robot);
        TagNode pos = (TagNode) cell(robot, "pos").getItem();
        assertEquals(4, pos.total());
        expand(robot, "pos");
        TagNode verb = (TagNode) cell(robot, "verb").getItem();
        assertEquals(2, verb.own());
        assertEquals(3, verb.total());
        assertTrue(cell(robot, "lexical").getItem() != null);
    }

    @Test
    void aNodeListsItsOccurrencesFilteredAndWithContext(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.clickOn(cell(robot, "verb"));
        waitUntil(() -> counts(robot).getText().startsWith("2 occurrences in 2 files"));
        ListView<OccurrenceRow> list = occurrences(robot);
        assertEquals(4, list.getItems().size(), "a header and an occurrence per file");

        // Include sub-tags
        robot.clickOn("#includeSubTags");
        waitUntil(() -> counts(robot).getText().startsWith("3 occurrences in 2 files"));

        // Applies to: only interlinear sentences
        @SuppressWarnings("unchecked")
        ChoiceBox<Object> appliesTo = robot.lookup("#appliesTo").queryAs(ChoiceBox.class);
        robot.interact(() -> appliesTo.setValue(appliesTo.getItems().stream()
            .filter(i -> i.toString().startsWith("Interlinear sentence")).findFirst().orElseThrow()));
        waitUntil(() -> counts(robot).getText().endsWith("· 1 shown"));

        // the lines of context are remembered
        @SuppressWarnings("unchecked")
        Spinner<Integer> before = robot.lookup("#contextBefore").queryAs(Spinner.class);
        robot.interact(() -> before.getValueFactory().setValue(0));
        assertEquals(0, workbench().settings().tagBrowserProperty().get().contextBefore());
    }

    @Test
    void renameChangesEveryFileInOneUndoStep(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.clickOn(cell(robot, "verb"));
        robot.type(KeyCode.F2);
        waitUntil(() -> !robot.lookup(".dialog-pane .text-field").queryAll().isEmpty());
        TextField name = robot.lookup(".dialog-pane .text-field").queryAs(TextField.class);
        robot.interact(() -> name.setText("action"));
        robot.clickOn("Rename");
        waitUntil(() -> document(a).getCurrentText().contains("#\"pos/action\"")
            && document(b).getCurrentText().contains("#\"pos/action/transitive\""));
        assertTrue(document(a).getCurrentText().contains("#\"pos/noun\""), "other tags untouched");
        MenuItem undo = robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "Edit".equals(m.getText())).findFirst().orElseThrow().getItems().get(0);
        assertEquals("Undo Rename tag pos/verb → pos/action (2 files)", undo.getText());
        assertTrue(robot.lookup("#statusMessageLabel").queryAs(Label.class).getText()
            .startsWith("Renamed 3 tags in 2 files (unsaved)"));
        // the renamed node is selected once the texts are parsed again
        waitUntil(() -> robot.lookup(".tree-cell").match((Node n) -> isCell(n, "action")
            && ((TreeCell<?>) n).isSelected()).queryAll().size() == 1);

        robot.interact(undo::fire);
        waitUntil(() -> document(a).getCurrentText().equals(A) && document(b).getCurrentText().equals(B));
    }

    @Test
    void editShowsTheTagInFileModeAndBackReturns(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.clickOn(cell(robot, "noun"));
        waitUntil(() -> counts(robot).getText().startsWith("1 occurrence in 1 file"));
        Button edit = robot.lookup(".button").match((Node n) -> n instanceof Button bt && "Edit".equals(bt.getText()))
            .queryAs(Button.class);
        robot.clickOn(edit);
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(Modes.FILE));
        int tagStart = A.indexOf("#\"pos/noun\"");
        waitUntil(() -> workbench().focus().map(f -> f.text().equals(document(a).getId()) && f.start() == tagStart).orElse(false));

        robot.clickOn("#backButton");
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(TagBrowserMode.ID));
        waitUntil(() -> workbench().focus().map(f -> f.start() == tagStart).orElse(false));
    }

    @Test
    void moveToChoosesTheNewParentAndConfirms(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.clickOn(cell(robot, "verb"));
        robot.type(KeyCode.F6);
        // the parent chooser: pos (the current parent) and verb's own sub-nodes are refused
        waitUntil(() -> chooserCell(robot, "lexical") != null);
        assertTrue(chooserCell(robot, "pos").isDisable());
        robot.clickOn(chooserCell(robot, "lexical"));
        robot.clickOn("Choose");
        // the confirmation shows what will change
        waitUntil(() -> robot.lookup(".dialog-pane .label").queryAll().stream()
            .anyMatch(n -> n instanceof Label l && l.getText() != null && l.getText().startsWith("Moves 3 tags in 2 files")));
        robot.clickOn("Move");
        waitUntil(() -> document(a).getCurrentText().contains("#\"lexical/verb\"")
            && document(b).getCurrentText().contains("#\"lexical/verb/transitive\""));
        assertTrue(document(b).getCurrentText().contains("#\"lexical\"\n"), "the target keeps its own tag");
        MenuItem undo = robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "Edit".equals(m.getText())).findFirst().orElseThrow().getItems().get(0);
        assertEquals("Undo Move tag pos/verb → lexical/verb (2 files)", undo.getText());
    }

    /** A cell of the Move to… chooser (a dialog's tree). */
    private TreeCell<?> chooserCell(FxRobot robot, String level) {
        return robot.lookup(".dialog-pane .tree-cell").match((Node n) -> isCell(n, level)).tryQueryAs(TreeCell.class).orElse(null);
    }

    /** Drag and drop needs a native toolkit: Monocle (headless) has none. */
    @Test
    @DisabledIfSystemProperty(named = "testfx.headless", matches = "true")
    void moveByDragAndDrop(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.drag(cell(robot, "verb")).dropTo(cell(robot, "lexical"));
        waitUntil(() -> !robot.lookup(".dialog-pane").queryAll().isEmpty());
        robot.clickOn("Move");
        waitUntil(() -> document(a).getCurrentText().contains("#\"lexical/verb\""));
    }

    @Test
    @DisabledIfSystemProperty(named = "testfx.headless", matches = "true")
    void aDropOnADescendantIsRefused(FxRobot robot) throws IOException {
        openTags(robot);
        expand(robot, "pos");
        robot.drag(cell(robot, "pos")).dropTo(cell(robot, "verb"));
        robot.sleep(300);
        assertTrue(robot.lookup(".dialog-pane").queryAll().isEmpty());
        assertEquals(A, document(a).getCurrentText());
    }

    @Test
    void showAllTagsFromABadgeOpensTheTagBrowser(FxRobot robot) throws IOException {
        a = addFile(robot, "a.txt", A);
        showFile(robot, a);
        // the IGT's detail view shows its tag as a badge
        var igt = workbench().session().corpus().text(document(a).getId()).orElseThrow()
            .objectsOf(fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence.class).get(0);
        robot.interact(() -> workbench().viewContext().select(igt));
        Label badge = robot.lookup(".label").match((Node n) -> n instanceof Label l && "pos › verb".equals(l.getText()))
            .queryAs(Label.class);
        robot.rightClickOn(badge);
        robot.clickOn("Show all #\"pos/verb\" tags");
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(TagBrowserMode.ID));
        waitUntil(() -> robot.lookup(".tree-cell").match((Node n) -> isCell(n, "verb") && ((TreeCell<?>) n).isSelected())
            .queryAll().size() == 1);
        robot.clickOn("#backButton");
        waitUntil(() -> workbench().modes().orElseThrow().activeId().orElse("").equals(Modes.FILE));
    }
}
