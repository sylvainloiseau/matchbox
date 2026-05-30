package fr.cnrs.lacito.matchbox.gui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Small search/replace dialog operating on a provided CodeArea.
 */
public class SearchDialog extends Stage {

    private final InlineCssTextArea codeArea;

    public SearchDialog(InlineCssTextArea codeArea, Window owner) {
        this.codeArea = codeArea;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Search / Replace");

        GridPane root = new GridPane();
        root.setHgap(8);
        root.setVgap(8);
        root.setPadding(new Insets(10));

        Label searchLabel = new Label("Search:");
        TextField searchField = new TextField();
        searchField.setPromptText("search text");

        Label replaceLabel = new Label("Replace:");
        TextField replaceField = new TextField();
        replaceField.setPromptText("replacement text");

        Button closeBtn = new Button("Close");
        Button searchBtn = new Button("Search");
        Button replaceBtn = new Button("Replace");
        Button replaceAllBtn = new Button("Replace all");

        HBox buttons = new HBox(8, closeBtn, searchBtn, replaceBtn, replaceAllBtn);

        GridPane.setHgrow(searchField, Priority.ALWAYS);
        GridPane.setHgrow(replaceField, Priority.ALWAYS);

        root.add(searchLabel, 0, 0);
        root.add(searchField, 1, 0);
        root.add(replaceLabel, 0, 1);
        root.add(replaceField, 1, 1);
        root.add(buttons, 0, 2, 2, 1);

        // Actions
        closeBtn.setOnAction(e -> this.close());

        searchBtn.setOnAction(e -> doSearch(searchField.getText()));

        replaceBtn.setOnAction(e -> doReplace(searchField.getText(), replaceField.getText()));

        replaceAllBtn.setOnAction(e -> doReplaceAll(searchField.getText(), replaceField.getText()));

        searchField.setOnAction(e -> doSearch(searchField.getText()));

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private void doSearch(String term) {
        if (term == null || term.isEmpty()) return;
        String text = codeArea.getText();
        int from = codeArea.getCaretPosition();
        int idx = text.indexOf(term, from);
        if (idx < 0 && from > 0) {
            // wrap around
            idx = text.indexOf(term, 0);
        }
        if (idx >= 0) {
            codeArea.selectRange(idx, idx + term.length());
            codeArea.requestFocus();
        }
    }

    private void doReplace(String term, String replacement) {
        if (term == null || term.isEmpty()) return;
        String selected = codeArea.getSelectedText();
        if (selected != null && selected.equals(term)) {
            int start = codeArea.getSelection().getStart();
            int end = codeArea.getSelection().getEnd();
            codeArea.replaceText(start, end, replacement == null ? "" : replacement);
            // codeArea.positionCaret(start + (replacement == null ? 0 : replacement.length()));
        }
        // search next occurrence after current caret
        doSearch(term);
    }

    private void doReplaceAll(String term, String replacement) {
        if (term == null || term.isEmpty()) return;
        String text = codeArea.getText();
        String replaced = text.replace(term, replacement == null ? "" : replacement);
        if (!replaced.equals(text)) {
            int caret = codeArea.getCaretPosition();
            codeArea.replaceText(replaced);
            // codeArea.positionCaret(Math.min(caret, replaced.length()));
        }
    }
}
