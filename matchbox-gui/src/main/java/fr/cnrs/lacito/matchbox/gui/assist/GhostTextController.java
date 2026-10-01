package fr.cnrs.lacito.matchbox.gui.assist;

import fr.cnrs.lacito.matchbox.assist.AssistContext;
import fr.cnrs.lacito.matchbox.assist.AssistService;
import fr.cnrs.lacito.matchbox.assist.Completion;
import fr.cnrs.lacito.matchbox.assist.privacy.PrivacyPolicy.Decision;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import fr.cnrs.lacito.matchbox.settings.AssistSettingsData;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Inline suggestions: the completion proposed by the model is inserted at the cursor as grey
 * provisional text. {@code Tab} accepts it, {@code Ctrl+Right} accepts its next word, {@code Esc},
 * a click or any other key removes it; typing its first character accepts that character.
 *
 * <p>The provisional text is inserted through {@link EditorController#runGhostOperation}, so it
 * reaches neither the model nor the undo history; the editor removes it before saving, parsing or
 * highlighting.
 */
public final class GhostTextController {

    static final String GHOST_STYLE = "-fx-fill: #8c8c8c; -fx-font-style: italic;";
    public static final KeyCombination REQUEST = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
    static final KeyCombination ACCEPT_WORD = new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN);

    private final EditorController editor;
    private final InlineCssTextArea area;
    private final AssistController assist;
    private final Consumer<String> status;
    private final PauseTransition pause = new PauseTransition();

    /** The provisional text and where it is. */
    private String ghost;
    private int ghostStart;

    private CompletableFuture<?> pending;
    private long sequence;

    /**
     * @param status receives short messages for the user ("Suggesting…", errors), "" to clear
     */
    public GhostTextController(EditorController editor, AssistController assist, Consumer<String> status) {
        this.editor = editor;
        this.area = editor.getFileContentArea();
        this.assist = assist;
        this.status = status;
        editor.setGhostDismisser(this::dismiss);

        area.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        area.addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> dismiss());
        area.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) dismiss();
        });
        pause.setOnFinished(e -> automaticRequest());
        // typing restarts the pause before an automatic suggestion
        editor.userChanges().subscribe(change -> {
            cancelPending();
            AssistSettingsData d = assist.data();
            if (d.inlineEnabled() && AssistSettingsData.AUTO.equals(d.trigger()) && area.isFocused()) {
                pause.setDuration(Duration.millis(d.debounceMs()));
                pause.playFromStart();
            }
        });
    }

    public boolean isShown() {
        return ghost != null;
    }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    private void onKeyPressed(KeyEvent e) {
        if (REQUEST.match(e)) {
            e.consume();
            request(true);
            return;
        }
        if (ghost == null || e.getCode().isModifierKey()) {
            return;
        }
        if (e.getCode() == KeyCode.TAB && !e.isShiftDown() && !e.isShortcutDown() && !e.isAltDown()) {
            e.consume();
            accept(ghost.length());
        } else if (e.getCode() == KeyCode.ESCAPE) {
            e.consume();
            dismiss();
        } else if (e.getCode() == KeyCode.ENTER) {
            // the editor inserts line breaks on KEY_PRESSED
            if (ghost.startsWith("\n") && !e.isShortcutDown() && !e.isAltDown()) {
                e.consume();
                accept(1);
            } else {
                dismiss();
            }
        } else if (ACCEPT_WORD.match(e)) {
            e.consume();
            accept(nextWordLength(ghost));
        } else if (e.getText().isEmpty() || e.isShortcutDown() || e.isControlDown() || e.isAltDown()) {
            // navigation, deletion, shortcuts: on the real text
            dismiss();
        }
        // a printable character is handled on KEY_TYPED
    }

    private void onKeyTyped(KeyEvent e) {
        if (ghost == null || e.getCharacter().isEmpty() || e.isShortcutDown()) {
            return;
        }
        String typed = e.getCharacter().equals("\r") ? "\n" : e.getCharacter();
        if (ghost.startsWith(typed)) {
            // typing the suggestion: keep the rest of it
            e.consume();
            accept(typed.length());
        } else {
            dismiss();
        }
    }

    static int nextWordLength(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
        return Math.max(1, i);
    }

    // ------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------

    private void automaticRequest() {
        // never replace a suggestion being typed
        if (ghost == null && area.isFocused() && area.getSelection().getLength() == 0) {
            request(false);
        }
    }

    /**
     * Asks for a suggestion at the cursor.
     *
     * @param explicit true when the user asked for it: privacy questions and errors are shown;
     *                 automatic requests are silently skipped instead
     */
    public void request(boolean explicit) {
        dismiss();
        cancelPending();
        if (!editor.isEditing() || !area.isEditable()) {
            return;
        }
        if (!explicit && (!assist.data().inlineEnabled() || assist.decision() != Decision.ALLOWED)) {
            return;
        }
        if (explicit && !assist.ensureAllowed()) {
            return;
        }
        Optional<AssistService> service = assist.service();
        if (service.isEmpty()) {
            if (explicit) status.accept(assist.whyUnavailable());
            return;
        }
        Path file = editor.getSelectedFilePath();
        String text = editor.currentText();
        int caret = area.getCaretPosition();
        AssistContext ctx = assist.context(file, text, caret);
        long seq = ++sequence;
        status.accept("Suggesting…");
        CompletableFuture<Optional<Completion>> future = service.get().complete(ctx);
        pending = future;
        FxAssist.onFx(future, completion -> {
            if (seq != sequence) return;
            pending = null;
            status.accept("");
            // dropped if anything changed meanwhile
            boolean unchanged = file.equals(editor.getSelectedFilePath()) && area.getCaretPosition() == caret
                && ghost == null && area.getText().equals(text);
            if (completion.isEmpty()) {
                if (explicit) status.accept("No suggestion.");
            } else if (unchanged) {
                show(caret, completion.get().text());
            }
        }, error -> {
            if (seq != sequence) return;
            pending = null;
            status.accept(explicit ? "Suggestion failed: " + FxAssist.message(error) : "");
        });
    }

    private void cancelPending() {
        pause.stop();
        if (pending != null) {
            pending.cancel(true);
            pending = null;
            sequence++;
            status.accept("");
        }
    }

    // ------------------------------------------------------------------
    // Provisional text
    // ------------------------------------------------------------------

    private void show(int position, String text) {
        editor.runGhostOperation(() -> {
            area.insertText(position, text);
            area.setStyle(position, position + text.length(), GHOST_STYLE);
            area.moveTo(position);
        });
        ghost = text;
        ghostStart = position;
        area.requestFollowCaret();
    }

    /** Removes the provisional text. */
    public void dismiss() {
        if (ghost == null) return;
        String text = ghost;
        int start = ghostStart;
        ghost = null;
        editor.runGhostOperation(() -> {
            int end = Math.min(start + text.length(), area.getLength());
            if (area.getText(start, end).equals(text)) {
                area.deleteText(start, end);
            } else {
                java.util.logging.Logger.getLogger(GhostTextController.class.getName())
                    .warning("Inline suggestion not found where it was inserted; left in the text");
            }
            area.moveTo(Math.min(start, area.getLength()));
        });
    }

    /** Turns the first {@code n} characters of the provisional text into real text. */
    private void accept(int n) {
        String text = ghost;
        int start = ghostStart;
        String accepted = text.substring(0, Math.min(n, text.length()));
        String rest = text.substring(accepted.length());
        dismiss();
        // a normal change: it reaches the model and the undo history
        area.insertText(start, accepted);
        area.clearStyle(start, start + accepted.length());
        area.moveTo(start + accepted.length());
        if (!rest.isEmpty()) {
            show(start + accepted.length(), rest);
        }
    }
}
