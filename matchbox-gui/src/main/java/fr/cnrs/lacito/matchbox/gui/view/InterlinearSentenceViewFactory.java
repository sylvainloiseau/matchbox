package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;

/**
 * Built-in view of interlinear glossed sentences. When the sentence can be edited (its grammar
 * gives field spans), the three lines are text fields: Enter writes the line into the text,
 * Escape restores it.
 */
public class InterlinearSentenceViewFactory implements ObjectViewFactory<InterlinearSentence> {

    /** Key of the view's properties holding its state. */
    private static final String STATE = InterlinearSentenceViewFactory.class.getName();

    /** The sentence shown, replaced by {@link #updateView} after each parse, and its editors. */
    private static final class State {
        InterlinearSentence sentence;
        final Map<String, TextField> editors = new LinkedHashMap<>();
        final Label position = new Label();
    }

    @Override
    public Class<InterlinearSentence> getType() {
        return InterlinearSentence.class;
    }

    @Override
    public List<ObjectColumn<InterlinearSentence>> getColumns() {
        return List.of(
            ObjectColumn.editable("Transcription", InterlinearSentence.TRANSCRIPTION, InterlinearSentence::getTranscription),
            ObjectColumn.editable("Morphemic glosses", InterlinearSentence.MORPHEMES, InterlinearSentence::getMorphemes),
            ObjectColumn.editable("Translation", InterlinearSentence.FREE_TRANSLATION, InterlinearSentence::getFreeTranslation)
        );
    }

    @Override
    public Node createView(InterlinearSentence igt, ViewContext context) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        grid.setStyle("-fx-padding: 8;");
        State state = new State();
        state.sentence = igt;
        boolean editable = context.canEdit(igt);
        addRow(grid, 0, "Transcription", InterlinearSentence.TRANSCRIPTION, InterlinearSentence::getTranscription, editable, state, context);
        addRow(grid, 1, "Glosses", InterlinearSentence.MORPHEMES, InterlinearSentence::getMorphemes, editable, state, context);
        addRow(grid, 2, "Translation", InterlinearSentence.FREE_TRANSLATION, InterlinearSentence::getFreeTranslation, editable, state, context);
        state.position.setText(GenericObjectViewFactory.position(igt.getTextCoordinate()));
        state.position.setStyle("-fx-text-fill: #777777;");
        state.position.setOnMouseClicked(e -> context.selectInEditor(state.sentence.getTextCoordinate()));
        grid.add(state.position, 1, 3);
        grid.getProperties().put(STATE, state);
        return grid;
    }

    /** Shows the re-parsed sentence in place: the line being edited keeps its text and focus. */
    @Override
    public boolean updateView(Node view, InterlinearSentence igt, ViewContext context) {
        if (!(view.getProperties().get(STATE) instanceof State state)
            || state.editors.isEmpty() != !context.canEdit(igt)) {
            return false;
        }
        state.sentence = igt;
        state.position.setText(GenericObjectViewFactory.position(igt.getTextCoordinate()));
        state.editors.forEach((field, editor) -> {
            if (!editor.isFocused()) {
                editor.setText(rawValue(igt, field));
                clearError(editor);
            }
        });
        return true;
    }

    private static void addRow(GridPane grid, int row, String title, String field,
                               Function<InterlinearSentence, String> value, boolean editable,
                               State state, ViewContext context) {
        Label t = new Label(title + ":");
        t.setStyle("-fx-font-weight: bold;");
        InterlinearSentence igt = state.sentence;
        if (!editable || !(igt instanceof HasFields withFields) || withFields.field(field).isEmpty()) {
            Label v = new Label(value.apply(igt));
            v.setStyle("-fx-font-family: monospace;");
            grid.addRow(row, t, v);
            return;
        }
        TextField editor = new TextField(rawValue(igt, field));
        editor.setStyle("-fx-font-family: monospace;");
        editor.setPrefColumnCount(40);
        editor.setOnAction(e -> {
            EditOutcome outcome = context.setField(state.sentence, field, editor.getText());
            if (outcome.isApplied()) {
                clearError(editor);
            } else {
                editor.setStyle("-fx-font-family: monospace; -fx-border-color: #d33;");
                editor.setTooltip(new Tooltip(outcome.message()));
            }
        });
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                editor.setText(rawValue(state.sentence, field));
                clearError(editor);
            }
        });
        state.editors.put(field, editor);
        grid.addRow(row, t, editor);
    }

    /** The raw text of a field (e.g. the translation without its quotes). */
    private static String rawValue(InterlinearSentence igt, String field) {
        return igt instanceof HasFields withFields
            ? withFields.field(field).map(Field::value).orElse("")
            : "";
    }

    private static void clearError(TextField editor) {
        editor.setStyle("-fx-font-family: monospace;");
        editor.setTooltip(null);
    }
}
