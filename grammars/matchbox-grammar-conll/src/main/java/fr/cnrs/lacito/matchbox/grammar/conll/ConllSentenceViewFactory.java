package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * View of dependency sentences, shipped with the CoNLL plugin: metadata and a table of tokens.
 * Loaded by the application only when this plugin is installed.
 */
public class ConllSentenceViewFactory implements ObjectViewFactory<DependencySentence> {

    private static final List<String> SHOWN = List.of("ID", "FORM", "LEMMA", "UPOS", "FEATS", "HEAD", "DEPREL");

    @Override
    public Class<DependencySentence> getType() {
        return DependencySentence.class;
    }

    @Override
    public List<ObjectColumn<DependencySentence>> getColumns() {
        return List.of(
            new ObjectColumn<>("Sentence", DependencySentence::getSentenceId),
            new ObjectColumn<>("Text", s -> s.getMetadata().getOrDefault("text", "")),
            new ObjectColumn<>("Tokens", s -> String.valueOf(s.getTokens().size()))
        );
    }

    @Override
    public Node createView(DependencySentence sentence, ViewContext context) {
        Label title = new Label("Sentence " + sentence.getSentenceId());
        title.setStyle("-fx-font-weight: bold;");
        title.setOnMouseClicked(e -> context.selectInEditor(sentence.getTextCoordinate()));

        VBox box = new VBox(4, title);
        sentence.getMetadata().forEach((key, value) -> box.getChildren().add(new Label(key + " = " + value)));

        TableView<Map<String, String>> tokens = new TableView<>();
        for (String field : SHOWN) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(field);
            column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getOrDefault(field, "")).getReadOnlyProperty()
            );
            tokens.getColumns().add(column);
        }
        tokens.getItems().setAll(sentence.getTokens());
        VBox.setVgrow(tokens, Priority.ALWAYS);
        box.getChildren().add(tokens);
        box.setStyle("-fx-padding: 6;");
        return box;
    }
}
