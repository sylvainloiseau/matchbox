package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabels;
import fr.cnrs.lacito.matchbox.model.check.GlossCheckSettings;
import java.util.Arrays;
import java.util.stream.Collectors;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** The gloss check of the IGTs: list of labels, the project's labels, the dictionary check. */
final class GlossCheckGroup {

    private final PageContext ctx;
    private final Label dictionaryHint;
    private final VBox node;

    GlossCheckGroup(PageContext ctx) {
        this.ctx = ctx;
        GlossCheckSettings settings = ctx.edit().getGlossCheck();
        ToggleGroup group = new ToggleGroup();
        HBox lists = new HBox(10);
        lists.setId("glossLabelLists");
        for (GlossLabelList list : GlossLabelList.values()) {
            RadioButton b = new RadioButton(switch (list) {
                case LEIPZIG -> "Leipzig Glossing Rules (" + GlossLabels.leipzig().size() + ")";
                case LEHMANN -> "Christian Lehmann's list (" + GlossLabels.lehmann().size() + ")";
                case BOTH -> "Both";
                case NONE -> "Do not check the labels";
            });
            b.setUserData(list);
            b.setToggleGroup(group);
            b.setSelected(list == settings.labels());
            lists.getChildren().add(b);
        }
        TextField own = new TextField(String.join(", ", settings.projectLabels()));
        own.setId("glossProjectLabels");
        own.setPromptText("Labels of this project, separated by commas (e.g. COMPL2, TAM1)");
        CheckBox dictionary = new CheckBox("Check form / gloss pairs against the context dictionary");
        dictionary.setId("glossDictionaryCheck");
        dictionary.setSelected(settings.dictionary());
        dictionaryHint = new Label("(no context dictionary chosen: choose one under Inherited context above)");
        dictionaryHint.setId("glossDictionaryHint");
        dictionaryHint.setStyle("-fx-text-fill: #888888;");
        dictionaryHint.setWrapText(true);
        dictionaryHint.setMinWidth(0);
        dictionaryHint.managedProperty().bind(dictionaryHint.visibleProperty());
        HBox dictionaryRow = new HBox(8, dictionary, dictionaryHint);
        dictionaryRow.setAlignment(Pos.CENTER_LEFT);
        Runnable write = () -> {
            ctx.edit().setGlossCheck(new GlossCheckSettings(
                (GlossLabelList) group.getSelectedToggle().getUserData(),
                Arrays.stream(own.getText().split("[,\\s]+")).filter(t -> !t.isBlank()).collect(Collectors.toSet()),
                dictionary.isSelected()));
            ctx.changed();
        };
        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n != null) write.run();
        });
        own.textProperty().addListener((obs, o, n) -> write.run());
        dictionary.selectedProperty().addListener((obs, o, n) -> write.run());
        node = DialogParts.group("Gloss check",
            new Label("Grammatical labels:"), lists, new Label("Project labels:"), own, dictionaryRow,
            DialogParts.help("The grammatical labels of the glosses of interlinear glossed texts (1SG, PST…) are "
                + "checked against the chosen list and the project's labels; the (form, gloss) pairs, against the "
                + "context dictionary when there is one. The findings are listed in the Semantic issues tab."));
        updateDictionaryHint();
    }

    VBox node() {
        return node;
    }

    /** Shows the hint while no context dictionary is chosen (the check box stays enabled: the setting is kept). */
    void updateDictionaryHint() {
        dictionaryHint.setVisible(ctx.edit().getContextDictionary().isEmpty());
    }
}
