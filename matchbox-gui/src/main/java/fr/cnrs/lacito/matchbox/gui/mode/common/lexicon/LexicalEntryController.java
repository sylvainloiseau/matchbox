package fr.cnrs.lacito.matchbox.gui.mode.common.lexicon;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.cnrs.lacito.matchbox.language.corpusObject.SimpleLexicalDefinition;
import fr.cnrs.lacito.matchbox.language.corpusObject.SimpleSense;

/**
 * Controller for displaying a lexical entry with its definitions and examples
 */
public class LexicalEntryController {

    @FXML private TextField entryWordField;
    @FXML private VBox senseContainer;
    private List<SenseController> senseControllers = new ArrayList<>();
    private final SimpleLexicalDefinition lexicalDefinition;

    public LexicalEntryController(SimpleLexicalDefinition lexDef) {
        if (lexDef == null) throw new IllegalArgumentException("LexicalDefinition cannot be null");
        this.lexicalDefinition = lexDef;
    }

    @FXML
    public void initialize() {
        entryWordField.setText(lexicalDefinition.getHeadword());
        clearSenses();
        // Add definitions
        List<SimpleSense> senses = lexicalDefinition.getSenses();
        if (senses != null) {
            for (SimpleSense sense : senses) {
                addSense(sense);
            }
        }
    }

    public void addSense(SimpleSense sense) {
        if (sense == null) return;
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fr/cnrs/lacito/matchbox/gui/mode/common/lexicon/SenseView.fxml"));
            SenseController senseController = new SenseController(sense);
            loader.setController(senseController);
            Parent senseView = loader.load();
            senseControllers.add(senseController);
            senseContainer.getChildren().add(senseView);
        } catch (IOException e) {
            System.err.println("Error loading definition view: " + e.getMessage());            
        }
    }

    public void clearSenses() {
        senseContainer.getChildren().clear();
        senseControllers.clear();
    }
}
