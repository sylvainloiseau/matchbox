package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.ShortcutsWindow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The text editor of File Mode: scroll pane, options, margin, keys, line commands, find bar, places. */
class EditorGuiTest extends MainWindowFixture {

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private InlineCssTextArea area(FxRobot robot) {
        return robot.lookup("#fileContentCodeArea").queryAs(InlineCssTextArea.class);
    }

    private Menu fileModeMenu(FxRobot robot) {
        return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "File Mode".equals(m.getText())).findFirst().orElseThrow();
    }

    private MenuItem item(List<MenuItem> items, String text) {
        for (MenuItem i : items) {
            if (text.equals(i.getText())) return i;
            if (i instanceof Menu sub) {
                try {
                    return item(sub.getItems(), text);
                } catch (AssertionError e) {
                    // not in this sub-menu
                }
            }
        }
        throw new AssertionError("No item " + text);
    }

    /** Toggles a check item as a click does (fire() alone does not change it). */
    private void toggle(FxRobot robot, String text) {
        CheckMenuItem item = (CheckMenuItem) item(robot, text);
        robot.interact(() -> {
            item.setSelected(!item.isSelected());
            item.fire();
        });
    }

    private MenuItem item(FxRobot robot, String text) {
        return item(fileModeMenu(robot).getItems(), text);
    }

    /** A key press as the platform's keyboard sends it. */
    private void press(FxRobot robot, KeyCode code, boolean shift, boolean control, boolean alt, boolean meta) {
        robot.interact(() -> Event.fireEvent(area(robot),
            new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, meta)));
    }

    private Path show(FxRobot robot, String name, String content) throws IOException {
        Path file = addFile(robot, name, content);
        waitUntil(() -> robot.lookup("#fileTreeView .tree-cell").queryAll().stream()
            .anyMatch(n -> n instanceof javafx.scene.control.TreeCell<?> c && c.getItem() != null
                && file.equals(((fr.cnrs.lacito.matchbox.gui.mode.fileMode.FileTree.Node) c.getItem()).path())));
        showFile(robot, file);
        return file;
    }

    @Test
    void theEditorScrollsAndShowsAnEmptyStateWithoutFile(FxRobot robot) throws IOException {
        assertTrue(robot.lookup("#editorEmptyState").query().isVisible());
        assertInstanceOf(VirtualizedScrollPane.class, area(robot).getParent());
        show(robot, "long.txt", "line\n".repeat(500));
        assertFalse(robot.lookup("#editorEmptyState").query().isVisible());
        boolean mac = EditorKeymap.Platform.current() == EditorKeymap.Platform.MAC;
        // file end: ⌘↓ on macOS, Ctrl+End elsewhere
        press(robot, mac ? KeyCode.DOWN : KeyCode.END, false, !mac, false, mac);
        assertEquals(area(robot).getLength(), area(robot).getCaretPosition());
        press(robot, mac ? KeyCode.UP : KeyCode.HOME, false, !mac, false, mac);
        assertEquals(0, area(robot).getCaretPosition());
    }

    @Test
    void wrappingAndLineNumbersAreOptionsRemembered(FxRobot robot) throws IOException {
        show(robot, "a.txt", "one\ntwo\n");
        assertFalse(area(robot).isWrapText());
        toggle(robot, "Wrap lines");
        assertTrue(area(robot).isWrapText());
        assertTrue(settings.editorProperty().get().wrapLines());

        assertNotNull(area(robot).getParagraphGraphicFactory(), "line numbers by default");
        Node first = area(robot).getParagraphGraphicFactory().apply(0);
        Node second = area(robot).getParagraphGraphicFactory().apply(1);
        assertEquals(first.prefWidth(-1), second.prefWidth(-1), 0.01, "same margin on every line");
        assertTrue(((javafx.scene.layout.HBox) first).getChildren().stream()
            .anyMatch(n -> n instanceof Label l && "1".equals(l.getText())));
        toggle(robot, "Show line numbers");
        // the margin stays for the markers (✚ of the text actions), without the numbers
        var factory = area(robot).getParagraphGraphicFactory();
        assertTrue(factory == null || ((javafx.scene.layout.HBox) factory.apply(0)).getChildren().stream()
            .noneMatch(n -> n instanceof Label l && "1".equals(l.getText())), "no line numbers");
        robot.interact(item(robot, "Zoom in")::fire);
        assertEquals(14, settings.editorProperty().get().fontSize());
    }

    @Test
    void smartHomeAndAutoIndent(FxRobot robot) throws IOException {
        show(robot, "a.txt", "    abc");
        robot.interact(() -> area(robot).moveTo(7));
        press(robot, KeyCode.HOME, false, false, false, false);
        assertEquals(4, area(robot).getCaretPosition(), "first non-blank character");
        press(robot, KeyCode.HOME, false, false, false, false);
        assertEquals(0, area(robot).getCaretPosition(), "then column 1");
        robot.interact(() -> area(robot).moveTo(7));
        press(robot, KeyCode.ENTER, false, false, false, false);
        waitUntil(() -> area(robot).getText().equals("    abc\n    "));
    }

    @Test
    void lineCommandsAreOneUndoStepEach(FxRobot robot) throws IOException {
        Path a = show(robot, "a.txt", "one\ntwo\n");
        robot.interact(() -> area(robot).moveTo(1));
        robot.interact(item(robot, "Duplicate line")::fire);
        waitUntil(() -> document(a).getCurrentText().equals("one\none\ntwo\n"));
        robot.interact(item(robot, "Move line down")::fire);
        waitUntil(() -> document(a).getCurrentText().equals("one\ntwo\none\n"));
        MenuItem undo = robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .filter(m -> "Edit".equals(m.getText())).findFirst().orElseThrow().getItems().get(0);
        assertTrue(undo.getText().contains("Move line down"), undo.getText());
        robot.interact(undo::fire);
        waitUntil(() -> document(a).getCurrentText().equals("one\none\ntwo\n"));
    }

    @Test
    void theFindBarCountsAndReplacesInOneStep(FxRobot robot) throws IOException {
        Path a = show(robot, "a.txt", "mio Mio mio\n");
        robot.interact(item(robot, "Replace…")::fire);
        waitUntil(() -> robot.lookup("#findBar").query().isVisible());
        TextField find = robot.lookup("#findField").queryAs(TextField.class);
        robot.interact(() -> find.setText("mio"));
        waitUntil(() -> robot.lookup("#findCount").queryAs(Label.class).getText().endsWith("of 3"));
        robot.interact(() -> robot.lookup("#replaceField").queryAs(TextField.class).setText("mi"));
        robot.interact(() -> robot.lookup("#replaceAll").queryButton().fire());
        waitUntil(() -> document(a).getCurrentText().equals("mi mi mi\n"));
        assertTrue(statusText(robot).startsWith("Replaced 3"));
    }

    private String statusText(FxRobot robot) {
        return robot.lookup("#statusMessageLabel").queryAs(Label.class).getText();
    }

    @Test
    void thePlaceInAFileIsKeptAndGoToLineMovesTheCaret(FxRobot robot) throws IOException {
        Path a = show(robot, "a.txt", "l1\nl2\nl3\nl4\n");
        Path b = show(robot, "b.txt", "other\n");
        showFile(robot, a);
        robot.interact(() -> area(robot).moveTo(4));
        showFile(robot, b);
        showFile(robot, a);
        assertEquals(4, area(robot).getCaretPosition(), "caret restored");
        FileMode fileMode = (FileMode) workbench().modes().orElseThrow().get(Modes.FILE);
        robot.interact(() -> fileMode.goToLine(3));
        waitUntil(() -> area(robot).getCaretPosition() == 6);
        assertEquals("Ln 3, Col 1", robot.lookup("#caretPosition").queryAs(javafx.scene.control.Hyperlink.class).getText());
    }

    @Test
    void theShortcutsListTheEditorKeysAndTheMenus(FxRobot robot) {
        List<ShortcutsWindow.Row> rows = ShortcutsWindow.rows(EditorKeymap.Platform.current(),
            robot.lookup(".menu-bar").queryAs(MenuBar.class));
        assertTrue(rows.stream().anyMatch(r -> r.action().startsWith("End of file")));
        assertTrue(rows.stream().anyMatch(r -> r.action().equals("Find…")));
        assertTrue(rows.stream().anyMatch(r -> r.action().equals("Duplicate line") && r.where().contains("Lines")));
    }
}
