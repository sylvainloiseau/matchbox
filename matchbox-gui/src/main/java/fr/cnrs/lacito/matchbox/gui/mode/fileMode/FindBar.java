package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FindModel.Match;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.FindModel.Query;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.InlineCssTextArea;
import org.reactfx.Subscription;

/**
 * The find and replace bar above the editor: match case, whole word, regular expression; all the
 * matches highlighted with their count, the current one stronger; Replace and Replace all (one
 * undo entry). Enter: next match, Shift+Enter: previous, Esc: closes the bar.
 */
public final class FindBar extends VBox {

    private final EditorController editor;
    private final InlineCssTextArea area;
    private final Consumer<String> status;
    private final TextField findField = new TextField();
    private final TextField replaceField = new TextField();
    private final Label count = new Label();
    private final ToggleButton matchCase = toggle("Aa", "Match case");
    private final ToggleButton wholeWord = toggle("W", "Whole word");
    private final ToggleButton regex = toggle(".*", "Regular expression ($1… in the replacement)");
    private final ToggleButton showReplace = new ToggleButton("Replace ▸");
    private final HBox replaceRow;
    private FindModel.Result result = new FindModel.Result(List.of(), null);
    private int current = -1;
    private Subscription textChanges;

    /** @param status shows a message on the status line */
    public FindBar(EditorController editor, Consumer<String> status) {
        this.editor = editor;
        this.area = editor.getFileContentArea();
        this.status = status;
        setId("findBar");
        setSpacing(3);
        setPadding(new Insets(3, 6, 3, 6));
        setStyle("-fx-background-color: #ececec; -fx-border-color: transparent transparent #cccccc transparent;");

        findField.setId("findField");
        findField.setPromptText("Find");
        findField.setPrefColumnCount(22);
        count.setId("findCount");
        count.setMinWidth(90);
        Button previous = small("↑", "Previous match (⇧Enter)");
        previous.setOnAction(e -> previous());
        Button next = small("↓", "Next match (Enter)");
        next.setOnAction(e -> next());
        showReplace.setTooltip(new Tooltip("Show the replace field"));
        Button close = small("×", "Close (Esc)");
        close.setOnAction(e -> close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox findRow = new HBox(4, new Label("Find"), findField, count, previous, next, matchCase, wholeWord, regex,
            showReplace, spacer, close);
        findRow.setAlignment(Pos.CENTER_LEFT);

        replaceField.setId("replaceField");
        replaceField.setPromptText("Replace with");
        replaceField.setPrefColumnCount(22);
        Button replace = small("Replace", "Replace the current match and go to the next");
        replace.setOnAction(e -> replace());
        Button replaceAll = small("Replace all", "Replace every match (one undo step)");
        replaceAll.setId("replaceAll");
        replaceAll.setOnAction(e -> replaceAll());
        replaceRow = new HBox(4, new Label("Replace"), replaceField, replace, replaceAll);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.visibleProperty().bind(showReplace.selectedProperty());
        replaceRow.managedProperty().bind(replaceRow.visibleProperty());
        getChildren().addAll(findRow, replaceRow);

        findField.textProperty().addListener((obs, o, n) -> search(true));
        for (ToggleButton t : List.of(matchCase, wholeWord, regex)) t.selectedProperty().addListener((obs, o, n) -> search(true));
        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) previous(); else next();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                replace();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        setVisible(false);
        managedProperty().bind(visibleProperty());
    }

    private static ToggleButton toggle(String text, String tip) {
        ToggleButton t = new ToggleButton(text);
        t.setTooltip(new Tooltip(tip));
        t.setFocusTraversable(false);
        t.setStyle("-fx-font-size: 0.85em; -fx-padding: 1 5 1 5;");
        return t;
    }

    private static Button small(String text, String tip) {
        Button b = new Button(text);
        b.setTooltip(new Tooltip(tip));
        b.setStyle("-fx-font-size: 0.85em; -fx-padding: 1 6 1 6;");
        return b;
    }

    /** Opens the bar, with the selection (one line) as query; {@code replace}: shows the replace field. */
    public void open(boolean replace) {
        String selected = area.getSelectedText();
        if (!selected.isEmpty() && !selected.contains("\n")) findField.setText(selected);
        if (replace) showReplace.setSelected(true);
        if (!isVisible()) {
            setVisible(true);
            // the matches follow the edits while the bar is open
            textChanges = editor.userChanges().subscribe(c -> search(false));
        }
        search(false);
        findField.requestFocus();
        findField.selectAll();
    }

    public void close() {
        setVisible(false);
        if (textChanges != null) {
            textChanges.unsubscribe();
            textChanges = null;
        }
        editor.setFindHighlights(List.of(), -1);
        area.requestFocus();
    }

    private Query query() {
        return new Query(findField.getText(), matchCase.isSelected(), wholeWord.isSelected(), regex.isSelected());
    }

    /** Finds the matches again; {@code jump}: goes to the first match from the caret. */
    private void search(boolean jump) {
        if (!isVisible()) return;
        result = FindModel.find(editor.currentText(), query());
        if (result.problem() != null) {
            findField.setStyle("-fx-border-color: #d32f2f;");
            findField.setTooltip(new Tooltip(result.problem()));
        } else {
            findField.setStyle("");
            findField.setTooltip(null);
        }
        current = result.matches().isEmpty() ? -1
            : jump ? result.nextFrom(area.getSelection().getStart()) : Math.min(current, result.matches().size() - 1);
        if (jump && current >= 0) select(current);
        showCount();
    }

    private void showCount() {
        int n = result.matches().size();
        count.setText(findField.getText().isEmpty() ? "" : n == 0 ? (result.problem() != null ? "Invalid" : "No results")
            : (current >= 0 ? (current + 1) + " of " + n : n + (n == 1 ? " match" : " matches")));
        editor.setFindHighlights(result.matches(), current);
    }

    private void select(int index) {
        current = index;
        Match m = result.matches().get(index);
        area.selectRange(m.start(), m.end());
        area.requestFollowCaret();
        showCount();
    }

    public void next() {
        if (!isVisible()) open(false);
        if (result.matches().isEmpty()) return;
        int from = area.getSelection().getLength() > 0 ? area.getSelection().getEnd() : area.getCaretPosition();
        if (current >= 0 && matchesSelection(current)) from = result.matches().get(current).end();
        select(result.nextFrom(from));
    }

    public void previous() {
        if (!isVisible()) open(false);
        if (result.matches().isEmpty()) return;
        select(result.previousFrom(area.getSelection().getStart()));
    }

    private boolean matchesSelection(int index) {
        Match m = result.matches().get(index);
        return area.getSelection().getStart() == m.start() && area.getSelection().getEnd() == m.end();
    }

    /** Replaces the current match (if it is selected), then goes to the next one. */
    void replace() {
        if (current < 0 || !matchesSelection(current)) {
            next();
            return;
        }
        String text = editor.currentText();
        TextEdit edit;
        try {
            edit = FindModel.replace(text, query(), result.matches().get(current), replaceField.getText());
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            status.accept("Invalid replacement: " + e.getMessage());
            return;
        }
        if (editor.applySearchEdits(List.of(edit))) {
            area.moveTo(edit.start() + edit.replacement().length());
            search(false);
            next();
        }
    }

    /** Replaces every match, as one undo entry. */
    void replaceAll() {
        String text = editor.currentText();
        List<TextEdit> edits;
        try {
            edits = FindModel.replaceAll(text, query(), replaceField.getText());
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            status.accept("Invalid replacement: " + e.getMessage());
            return;
        }
        if (edits.isEmpty()) {
            status.accept("Nothing to replace");
            return;
        }
        if (editor.applySearchEdits(edits)) {
            status.accept("Replaced " + edits.size() + (edits.size() == 1 ? " match" : " matches"));
            search(false);
        }
    }
}
