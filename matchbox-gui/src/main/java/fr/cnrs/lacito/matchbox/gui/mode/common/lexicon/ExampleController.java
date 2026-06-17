package fr.cnrs.lacito.matchbox.gui.mode.common.lexicon;

import fr.cnrs.lacito.matchbox.language.corpusObject.SimpleExample;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

/**
 * Controller for displaying a single example
 */
public class ExampleController {
    
    @FXML private TextField transcriptionField;
    @FXML private TextField translationField;
    private final SimpleExample example;
    
    public ExampleController(SimpleExample example) {
        this.example = example;   
    }

    @FXML
    public void initialize() {
        transcriptionField.setText(example.getTranscription());
        translationField.setText(example.getTranslation());
    }

}
