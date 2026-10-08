package fr.cnrs.lacito.matchbox.gui.mode;

import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

/**
 * Shows the modes of the window: one toolbar button and one View menu item per mode, the active
 * mode's content in the main area and its menu in the menu bar (after the View menu). Modes are
 * created the first time they are shown and kept; File Mode is created at once (it owns the
 * editor). The focus of the active mode is the window's {@link #focusProperty() focus}.
 */
public final class ModeHost {

    private static final Logger logger = Logger.getLogger(ModeHost.class.getName());

    private final Workbench workbench;
    private final ModeSwitcher switcher;
    private final Pane viewPane;
    private final MenuBar menuBar;
    private final Menu viewMenu;
    private final ToggleGroup buttons = new ToggleGroup();
    private final ToggleGroup viewItems = new ToggleGroup();
    private final Map<String, ToggleButton> buttonById = new HashMap<>();
    private final Map<String, RadioMenuItem> itemById = new HashMap<>();
    private final ReadOnlyObjectWrapper<String> activeId = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Optional<Focus>> focus = new ReadOnlyObjectWrapper<>(Optional.empty());
    private final ChangeListener<Optional<Focus>> focusFollower = (obs, o, n) -> focus.set(n);
    private ObservableValue<Optional<Focus>> followedFocus;
    private Optional<Menu> shownMenu = Optional.empty();
    /** True while the buttons and items are set to the active mode (not a user's choice). */
    private boolean syncing;

    /**
     * @param toolBar  receives one toggle button per mode
     * @param viewMenu receives one item per mode (at its start); the mode menu goes after it
     */
    public ModeHost(Workbench workbench, List<ModeDescriptor> modes, Pane viewPane, ToolBar toolBar,
                    MenuBar menuBar, Menu viewMenu) {
        this.workbench = workbench;
        this.switcher = new ModeSwitcher(modes, d -> d.factory().apply(workbench));
        this.viewPane = viewPane;
        this.menuBar = menuBar;
        this.viewMenu = viewMenu;
        int index = 0;
        for (ModeDescriptor d : modes) {
            ToggleButton button = new ToggleButton(d.title());
            button.setToggleGroup(buttons);
            button.setFocusTraversable(false);
            button.setTooltip(new Tooltip(d.description()
                + (d.shortcut() == null ? "" : " (" + d.shortcut().getDisplayText() + ")")));
            button.setOnAction(e -> choose(d.id(), button.isSelected()));
            toolBar.getItems().add(button);
            buttonById.put(d.id(), button);

            RadioMenuItem item = new RadioMenuItem(d.title());
            item.setToggleGroup(viewItems);
            item.setAccelerator(d.shortcut());
            item.setOnAction(e -> choose(d.id(), true));
            viewMenu.getItems().add(index++, item);
            itemById.put(d.id(), item);
        }
    }

    /** A button or item chosen by the user: a jump (recorded for Back). */
    private void choose(String id, boolean selected) {
        if (syncing) return;
        if (!selected) {
            // clicking the active mode's button keeps it active
            sync(activeId.get());
            return;
        }
        workbench.navigator().switchTo(id);
    }

    /** Shows a mode (without recording a jump: see {@code Navigator.switchTo}). */
    public Mode activate(String id) {
        Optional<Optional<Mode>> changed = switcher.activate(id);
        Mode mode = switcher.get(id);
        if (changed.isPresent()) {
            Node root = mode.root();
            AnchorPane.setTopAnchor(root, 0.0);
            AnchorPane.setBottomAnchor(root, 0.0);
            AnchorPane.setLeftAnchor(root, 0.0);
            AnchorPane.setRightAnchor(root, 0.0);
            viewPane.getChildren().setAll(root);
            showMenu(mode.menu());
            if (followedFocus != null) followedFocus.removeListener(focusFollower);
            followedFocus = mode.focusProperty();
            followedFocus.addListener(focusFollower);
            focus.set(followedFocus.getValue());
            activeId.set(id);
        }
        sync(id);
        return mode;
    }

    private void showMenu(Optional<Menu> menu) {
        int slot = menuBar.getMenus().indexOf(viewMenu) + 1;
        ModeMenus.replace(menuBar.getMenus(), shownMenu, menu, slot);
        shownMenu = menu;
        menu.ifPresent(this::checkAccelerators);
    }

    /** Logs the accelerators of a mode menu that the window's menus already use. */
    private void checkAccelerators(Menu modeMenu) {
        Set<KeyCombination> window = new HashSet<>();
        for (Menu m : menuBar.getMenus()) {
            if (m != modeMenu) accelerators(m, window);
        }
        Set<KeyCombination> mode = new HashSet<>();
        accelerators(modeMenu, mode);
        Set<KeyCombination> clash = ModeMenus.collisions(window, mode);
        if (!clash.isEmpty()) {
            logger.warning("The menu " + modeMenu.getText() + " uses accelerators of the window's menus: " + clash);
        }
    }

    private static void accelerators(Menu menu, Set<KeyCombination> into) {
        for (MenuItem item : menu.getItems()) {
            if (item.getAccelerator() != null) into.add(item.getAccelerator());
            if (item instanceof Menu sub) accelerators(sub, into);
        }
    }

    private void sync(String id) {
        syncing = true;
        try {
            buttons.selectToggle(id == null ? null : buttonById.get(id));
            viewItems.selectToggle(id == null ? null : itemById.get(id));
        } finally {
            syncing = false;
        }
    }

    /** The mode, created if needed (not shown). */
    public Mode get(String id) {
        return switcher.get(id);
    }

    public Optional<String> activeId() {
        return Optional.ofNullable(activeId.get());
    }

    public ReadOnlyObjectProperty<String> activeIdProperty() {
        return activeId.getReadOnlyProperty();
    }

    public Optional<Mode> activeMode() {
        return switcher.active();
    }

    /** The focus of the active mode. */
    public Optional<Focus> focus() {
        return focus.get();
    }

    public ReadOnlyObjectProperty<Optional<Focus>> focusProperty() {
        return focus.getReadOnlyProperty();
    }

    public List<ModeDescriptor> descriptors() {
        return switcher.descriptors();
    }
}
