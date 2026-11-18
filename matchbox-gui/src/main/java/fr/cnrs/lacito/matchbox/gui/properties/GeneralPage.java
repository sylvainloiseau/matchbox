package fr.cnrs.lacito.matchbox.gui.properties;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Name and description of the project. */
final class GeneralPage implements PropertiesPage {

    private final TextField name;
    private final VBox content;

    GeneralPage(PageContext ctx) {
        name = new TextField(ctx.edit().getName());
        name.setId("projectName");
        name.setPromptText("Enter project name");
        name.textProperty().addListener((obs, o, n) -> {
            ctx.edit().setName(n);
            ctx.changed();
        });
        TextArea description = new TextArea(ctx.edit().getDescription());
        description.setId("projectDescription");
        description.setPromptText("Enter project description");
        description.setWrapText(true);
        description.setPrefRowCount(6);
        description.textProperty().addListener((obs, o, n) -> {
            ctx.edit().setDescription(n);
            ctx.changed();
        });
        VBox.setVgrow(description, Priority.ALWAYS);
        content = DialogParts.page(id(),
            DialogParts.bold("Project name"), name,
            DialogParts.bold("Project description"), description);
    }

    @Override
    public ProjectPropertiesPage id() {
        return ProjectPropertiesPage.GENERAL;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void shown() {
        Platform.runLater(name::requestFocus);
    }
}
