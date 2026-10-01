package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/** Built-in view of interlinear glossed sentences. */
public class InterlinearSentenceViewFactory implements ObjectViewFactory<InterlinearSentence> {

    @Override
    public Class<InterlinearSentence> getType() {
        return InterlinearSentence.class;
    }

    @Override
    public List<ObjectColumn<InterlinearSentence>> getColumns() {
        return List.of(
            new ObjectColumn<>("Transcription", InterlinearSentence::getTranscription),
            new ObjectColumn<>("Morphemic glosses", InterlinearSentence::getMorphemes),
            new ObjectColumn<>("Translation", InterlinearSentence::getFreeTranslation)
        );
    }

    @Override
    public Node createView(InterlinearSentence igt, ViewContext context) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        grid.setStyle("-fx-padding: 8;");
        addRow(grid, 0, "Transcription", igt.getTranscription());
        addRow(grid, 1, "Glosses", igt.getMorphemes());
        addRow(grid, 2, "Translation", igt.getFreeTranslation());
        Label position = new Label(GenericObjectViewFactory.position(igt.getTextCoordinate()));
        position.setStyle("-fx-text-fill: #777777;");
        position.setOnMouseClicked(e -> context.selectInEditor(igt.getTextCoordinate()));
        grid.add(position, 1, 3);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String title, String value) {
        Label t = new Label(title + ":");
        t.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setStyle("-fx-font-family: monospace;");
        grid.addRow(row, t, v);
    }
}
