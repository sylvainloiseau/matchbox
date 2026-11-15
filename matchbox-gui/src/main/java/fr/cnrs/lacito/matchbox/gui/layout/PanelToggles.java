package fr.cnrs.lacito.matchbox.gui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;

/**
 * The toggles of the panels of a mode: toggle buttons for the main toolbar (a segmented group)
 * and check items for the mode's menu, both showing and changing one layout property. The
 * toggle of the last shown panel is disabled (at least one panel stays shown).
 *
 * @param <T> the immutable layout
 */
public final class PanelToggles<T> {

    /**
     * One panel.
     *
     * @param title       button and menu item text ("Files")
     * @param description tooltip ("Show or hide the list of files")
     * @param id          id of the button (tests, accessibility); the menu item gets id + "Item"
     * @param accelerator of the menu item; null for none
     */
    public record Panel(String title, String description, String id, KeyCombination accelerator) {
        public Panel {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(id, "id");
            description = description == null ? "" : description;
        }
    }

    private static final String LAST_PANEL = "At least one panel stays shown";

    private final List<Panel> panels;
    private final ObjectProperty<T> layout;
    private final PanelModel<T> model;
    private final List<ToggleButton> buttons = new ArrayList<>();
    private final List<CheckMenuItem> items = new ArrayList<>();
    private final HBox box = new HBox();

    public PanelToggles(List<Panel> panels, ObjectProperty<T> layout, PanelModel<T> model) {
        this.panels = List.copyOf(panels);
        this.layout = layout;
        this.model = model;
        box.getStyleClass().add("panel-toggles");
        box.setId("panelToggles");
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            int index = i;
            ToggleButton button = new ToggleButton(p.title());
            button.setId(p.id());
            button.setFocusTraversable(false);
            button.getStyleClass().add(i == 0 ? "left-pill" : i == panels.size() - 1 ? "right-pill" : "center-pill");
            button.setOnAction(e -> set(index, button.isSelected()));
            buttons.add(button);
            box.getChildren().add(button);

            CheckMenuItem item = new CheckMenuItem(p.title());
            item.setId(p.id() + "Item");
            item.setAccelerator(p.accelerator());
            item.setOnAction(e -> set(index, item.isSelected()));
            items.add(item);
        }
        layout.addListener((obs, o, n) -> refresh());
        refresh();
    }

    /**
     * The accelerator of the panel command {@code n} (1, 2, 3…; 0 for "show all"): ⌥⌘digit on
     * macOS; Ctrl+Shift+F-key elsewhere (F4 for 0), since Ctrl+Alt is AltGr on Windows and types
     * characters on many keyboards. The same keys in every mode.
     */
    public static KeyCombination accelerator(int n) {
        if (fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap.Platform.current()
                == fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorKeymap.Platform.MAC) {
            return new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.valueOf("DIGIT" + n),
                KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
        }
        return new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.valueOf("F" + (n == 0 ? 4 : n)),
            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    }

    /** The buttons, for the main toolbar. */
    public Node toolBarItems() {
        return box;
    }

    /** The check items, for the mode's menu (in panel order). */
    public List<MenuItem> menuItems() {
        return List.copyOf(items);
    }

    private void set(int panel, boolean show) {
        layout.set(model.withShown(layout.get(), panel, show));
        // a refused change leaves the layout as it was: the controls follow it again
        refresh();
    }

    private void refresh() {
        T l = layout.get();
        for (int i = 0; i < panels.size(); i++) {
            boolean shown = model.isShown(l, i);
            boolean locked = shown && !model.canHide(l, i);
            Panel p = panels.get(i);
            String shortcut = p.accelerator() == null ? "" : " (" + p.accelerator().getDisplayText() + ")";
            ToggleButton button = buttons.get(i);
            button.setSelected(shown);
            button.setDisable(locked);
            button.setTooltip(new Tooltip(locked ? LAST_PANEL : p.description() + shortcut));
            CheckMenuItem item = items.get(i);
            item.setSelected(shown);
            item.setDisable(locked);
        }
    }
}
