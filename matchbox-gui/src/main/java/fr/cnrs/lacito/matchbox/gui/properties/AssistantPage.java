package fr.cnrs.lacito.matchbox.gui.properties;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;

/** Whether cloud AI providers may be used for the project. */
final class AssistantPage implements PropertiesPage {

    private final VBox content;

    AssistantPage(PageContext ctx) {
        CheckBox allow = new CheckBox("Allow cloud AI providers for this project");
        allow.setId("cloudAssistAllowed");
        allow.setSelected(ctx.edit().isCloudAssistAllowed());
        allow.selectedProperty().addListener((obs, o, n) -> {
            ctx.edit().setCloudAssistAllowed(n);
            ctx.changed();
        });
        content = DialogParts.page(id(), allow, DialogParts.help(
            "Uncheck if the notes must not leave this computer (e.g. personal data of consultants): "
                + "only a local model (Ollama) can then be used for this project."));
    }

    @Override
    public ProjectPropertiesPage id() {
        return ProjectPropertiesPage.ASSISTANT;
    }

    @Override
    public Node content() {
        return content;
    }
}
