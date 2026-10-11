package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;

/**
 * The keys of the caret movements and deletions of the editor, following the conventions of each
 * system: on macOS ⌘←/→ line, ⌘↑/↓ file, ⌥←/→ word (Home/End and ⌘Home/⌘End also work); elsewhere
 * Home/End, Ctrl+Home/End, Ctrl+←/→. Every movement also exists with Shift (extends the
 * selection). Pure: the modifiers are explicit (no "shortcut" key), so no JavaFX toolkit is needed.
 */
public final class EditorKeymap {

    /** The conventions to follow. */
    public enum Platform {
        MAC, OTHER;

        public static Platform current() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? MAC : OTHER;
        }
    }

    /** A key and what it does; {@code select}: the movement extends the selection. */
    public record Binding(KeyCodeCombination key, EditorAction action, boolean select) {}

    private EditorKeymap() {
    }

    public static List<Binding> forPlatform(Platform platform) {
        List<Binding> b = new ArrayList<>();
        Modifier cmd = KeyCombination.META_DOWN;
        Modifier ctrl = KeyCombination.CONTROL_DOWN;
        Modifier alt = KeyCombination.ALT_DOWN;
        move(b, EditorAction.LINE_START, KeyCode.HOME);
        move(b, EditorAction.LINE_END, KeyCode.END);
        if (platform == Platform.MAC) {
            move(b, EditorAction.LINE_START, KeyCode.LEFT, cmd);
            move(b, EditorAction.LINE_END, KeyCode.RIGHT, cmd);
            move(b, EditorAction.LINE_START, KeyCode.A, ctrl);
            move(b, EditorAction.LINE_END, KeyCode.E, ctrl);
            move(b, EditorAction.FILE_START, KeyCode.UP, cmd);
            move(b, EditorAction.FILE_END, KeyCode.DOWN, cmd);
            move(b, EditorAction.FILE_START, KeyCode.HOME, cmd);
            move(b, EditorAction.FILE_END, KeyCode.END, cmd);
            move(b, EditorAction.WORD_LEFT, KeyCode.LEFT, alt);
            move(b, EditorAction.WORD_RIGHT, KeyCode.RIGHT, alt);
            b.add(new Binding(new KeyCodeCombination(KeyCode.BACK_SPACE, cmd), EditorAction.DELETE_TO_LINE_START, false));
            b.add(new Binding(new KeyCodeCombination(KeyCode.BACK_SPACE, alt), EditorAction.DELETE_WORD_LEFT, false));
            b.add(new Binding(new KeyCodeCombination(KeyCode.DELETE, alt), EditorAction.DELETE_WORD_RIGHT, false));
        } else {
            move(b, EditorAction.FILE_START, KeyCode.HOME, ctrl);
            move(b, EditorAction.FILE_END, KeyCode.END, ctrl);
            move(b, EditorAction.WORD_LEFT, KeyCode.LEFT, ctrl);
            move(b, EditorAction.WORD_RIGHT, KeyCode.RIGHT, ctrl);
            b.add(new Binding(new KeyCodeCombination(KeyCode.BACK_SPACE, ctrl), EditorAction.DELETE_WORD_LEFT, false));
            b.add(new Binding(new KeyCodeCombination(KeyCode.DELETE, ctrl), EditorAction.DELETE_WORD_RIGHT, false));
        }
        return List.copyOf(b);
    }

    /** A movement and its Shift variant. */
    private static void move(List<Binding> b, EditorAction action, KeyCode code, Modifier... modifiers) {
        b.add(new Binding(new KeyCodeCombination(code, modifiers), action, false));
        Modifier[] withShift = java.util.Arrays.copyOf(modifiers, modifiers.length + 1);
        withShift[modifiers.length] = KeyCombination.SHIFT_DOWN;
        b.add(new Binding(new KeyCodeCombination(code, withShift), action, true));
    }

    /** "⌘→" / "Ctrl+Right" for the shortcuts window. */
    public static String display(Binding binding, Platform platform) {
        KeyCodeCombination k = binding.key();
        StringBuilder s = new StringBuilder();
        boolean mac = platform == Platform.MAC;
        if (k.getControl() == KeyCombination.ModifierValue.DOWN) s.append(mac ? "⌃" : "Ctrl+");
        if (k.getAlt() == KeyCombination.ModifierValue.DOWN) s.append(mac ? "⌥" : "Alt+");
        if (k.getShift() == KeyCombination.ModifierValue.DOWN) s.append(mac ? "⇧" : "Shift+");
        if (k.getMeta() == KeyCombination.ModifierValue.DOWN) s.append(mac ? "⌘" : "Meta+");
        s.append(switch (k.getCode()) {
            case LEFT -> mac ? "←" : "Left";
            case RIGHT -> mac ? "→" : "Right";
            case UP -> mac ? "↑" : "Up";
            case DOWN -> mac ? "↓" : "Down";
            case BACK_SPACE -> mac ? "⌫" : "Backspace";
            case DELETE -> mac ? "⌦" : "Delete";
            default -> k.getCode().getName();
        });
        return s.toString();
    }
}
