package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The keyboard shortcuts of the current system: the editor's movements and deletions
 * ({@link EditorKeymap}) and the accelerators of the menus.
 */
public final class ShortcutsWindow {

    /** A row of the table. */
    public record Row(String where, String action, String keys) {}

    private ShortcutsWindow() {
    }

    /** The rows: editor actions (with their keys, Shift variants mentioned), then the menus' items. */
    public static List<Row> rows(EditorKeymap.Platform platform, MenuBar menuBar) {
        List<Row> rows = new ArrayList<>();
        Map<EditorAction, List<String>> keys = new LinkedHashMap<>();
        for (EditorKeymap.Binding b : EditorKeymap.forPlatform(platform)) {
            if (!b.select()) keys.computeIfAbsent(b.action(), a -> new ArrayList<>()).add(EditorKeymap.display(b, platform));
        }
        keys.forEach((action, k) -> rows.add(new Row("Editor", action.label() + (action.isMove() ? " (with Shift: select)" : ""),
            String.join("   ", k))));
        rows.add(new Row("Editor", "New line keeping the indentation", "Enter"));
        if (menuBar != null) {
            for (Menu menu : menuBar.getMenus()) collect(menu.getText(), menu.getItems(), rows);
        }
        return rows;
    }

    private static void collect(String where, List<MenuItem> items, List<Row> rows) {
        for (MenuItem item : items) {
            if (item instanceof Menu sub) {
                collect(where + " › " + sub.getText(), sub.getItems(), rows);
            } else if (item.getAccelerator() != null && item.getText() != null) {
                rows.add(new Row(where.replace("_", ""), item.getText().replace("_", ""), item.getAccelerator().getDisplayText()));
            }
        }
    }

    public static void show(Window owner, MenuBar menuBar) {
        TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows(EditorKeymap.Platform.current(), menuBar)));
        table.setId("shortcutsTable");
        TableColumn<Row, String> where = new TableColumn<>("Where");
        where.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().where()));
        where.setPrefWidth(150);
        TableColumn<Row, String> action = new TableColumn<>("Action");
        action.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));
        action.setPrefWidth(300);
        TableColumn<Row, String> keys = new TableColumn<>("Keys");
        keys.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().keys()));
        keys.setPrefWidth(200);
        table.getColumns().setAll(List.of(where, action, keys));
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Keyboard shortcuts");
        stage.setScene(new Scene(table, 680, 520));
        stage.show();
    }
}
