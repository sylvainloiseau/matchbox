package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.gui.mode.FileModeController;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.project.ProjectXMLSerializer;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;

/**
 * Dialog for creating a new project
 */
public class CreateProjectDialog {
    private Stage parentStage;
    private FileModeController fileModeController;
    private File projectFile;
    private Project project;

    public CreateProjectDialog(Stage parentStage, FileModeController fileModeController, Project project) {
        this.parentStage = parentStage;
        this.fileModeController = fileModeController;
        this.project = project;
    }

    public void showAndWait() {
        // Step 1: Show file chooser for .mbx filename
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Create New Project");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Notebook Project Files", "*.mbx")
        );
        projectFile = fileChooser.showSaveDialog(parentStage);

        if (projectFile != null) {
            // Step 2: Show project properties dialog
            ProjectPropertiesDialog propertiesDialog = new ProjectPropertiesDialog(parentStage, project, fileModeController);
            propertiesDialog.showAndWait();

            // Step 3: If Save was clicked, save the project
            if (propertiesDialog.isSaved()) {
                try {
                    ProjectXMLSerializer.save(project, projectFile);
                    showInfo("Success", "Project saved successfully to " + projectFile.getAbsolutePath());
                } catch (Exception e) {
                    showError("Error saving project", e.getMessage());
                }
            }
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
