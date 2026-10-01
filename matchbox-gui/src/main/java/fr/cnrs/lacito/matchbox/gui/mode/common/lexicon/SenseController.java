package fr.cnrs.lacito.matchbox.gui.mode.common.lexicon;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.cnrs.lacito.matchbox.corpus.capability.Example;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;

/**
 * Controller for displaying a definition with its examples
 */
public class SenseController {

    @FXML private TextField definitionField;
    @FXML private VBox examplesContainer;
    private List<ExampleController> exampleControllers = new ArrayList<>();
    private final Sense sense;

    public SenseController(Sense sense) {
       this.sense = sense;
    }

    @FXML
    public void initialize() {
        String t = sense.getDefinition();
        definitionField.setText(t == null ? "" : t);
        addExamples(sense.getExamples());
    }

    public void addExamples(List<? extends Example> examples) {
        if (examples != null) {
            for (Example example : examples) {
                addExample(example);
            }
        }
    }

    private void addExample(Example example) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fr/cnrs/lacito/matchbox/gui/mode/common/lexicon/ExampleView.fxml"));
            ExampleController controller = new ExampleController(example);
            loader.setController(controller);
            Parent exampleView = loader.load();
            exampleControllers.add(controller);
            examplesContainer.getChildren().add(exampleView);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading example view: " + e.getMessage());
        }
    }

    public void clearExamples() {
        examplesContainer.getChildren().clear();
        exampleControllers.clear();
    }
}
