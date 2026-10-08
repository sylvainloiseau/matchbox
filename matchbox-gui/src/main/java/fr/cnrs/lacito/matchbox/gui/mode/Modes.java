package fr.cnrs.lacito.matchbox.gui.mode;

import java.util.List;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * The modes of the application, in toolbar order: the one place where a mode is registered. Modes
 * are not contributed by plugins. A mode is added here when it is implemented (planned: Corpus
 * Mode, "Browse, search and edit the annotated texts in the corpus"; Tag Mode, "Browse, search,
 * edit and organise all the tags in the corpus").
 */
public final class Modes {

    /** Id of File Mode, the mode that owns the text editor ("show in editor"). */
    public static final String FILE = "file";

    private Modes() {
    }

    public static List<ModeDescriptor> builtIn() {
        return List.of(
            new ModeDescriptor(FILE, "File", "File Mode",
                "List the files in the project, edit/add corpus objects and check that they are correctly parsed",
                shortcut(KeyCode.DIGIT1), FileMode::new)
        );
    }

    private static KeyCombination shortcut(KeyCode digit) {
        return new KeyCodeCombination(digit, KeyCombination.SHORTCUT_DOWN);
    }
}
