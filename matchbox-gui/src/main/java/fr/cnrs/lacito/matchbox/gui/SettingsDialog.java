package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.model.ApplicationSettings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for application settings
 */
public class SettingsDialog {

    private ApplicationSettings applicationSettings;
    private Stage parentStage;
    private Stage stage;

    @FXML
    private Rectangle igtColorPreview;

    @FXML
    private ColorPicker igtColorPicker;

    @FXML
    private Rectangle lexicalColorPreview;

    @FXML
    private ColorPicker lexicalColorPicker;

    @FXML
    private TextField separatorsField;

    @FXML
    private javafx.scene.control.TextArea fileExtensionsArea;

    @FXML
    private javafx.scene.control.TextArea filePatternsArea;

    @FXML
    private TextField recentFilesLimitField;

    @FXML
    private Button okButton;

    @FXML
    private Button cancelButton;

    @FXML
    private CheckBox openLastProjectCheckBox;

    public SettingsDialog(
        ApplicationSettings applicationSettings,
        Stage parentStage
    ) {
        this.applicationSettings = applicationSettings;
        this.parentStage = parentStage;
    }

    public void showAndWait() {
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Settings");
        stage.setWidth(400);
        stage.setHeight(300);

        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(
                getClass().getResource(
                    "/fr/cnrs/lacito/matchbox/gui/SettingsDialog.fxml"
                )
            );
            loader.setController(this);
            Parent root = loader.load();

            Scene scene = new Scene(root);
            stage.setScene(scene);

            // initialize values now that FXML fields are injected
            initFromConfig();

            stage.showAndWait();
        } catch (Exception ex) {
            showError("Error loading dialog", ex.getMessage());
        }
    }

    private void initFromConfig() {
        // color pickers
        try {
            Color igtInitialColor = Color.web(
                applicationSettings.getIgtParagraphColor()
            );
            igtColorPreview.setFill(igtInitialColor);
            igtColorPicker.setValue(igtInitialColor);
            igtColorPicker.setOnAction(ev -> {
                Color c = igtColorPicker.getValue();
                igtColorPreview.setFill(c);
                String hex = String.format(
                    "#%02X%02X%02X",
                    (int) Math.round(c.getRed() * 255),
                    (int) Math.round(c.getGreen() * 255),
                    (int) Math.round(c.getBlue() * 255)
                );
                applicationSettings.setIgtParagraphColor(hex);
            });

            Color lexicalInitialColor = Color.web(
                applicationSettings.getLexicalDescriptionColor()
            );
            lexicalColorPreview.setFill(lexicalInitialColor);
            lexicalColorPicker.setValue(lexicalInitialColor);
            lexicalColorPicker.setOnAction(ev -> {
                Color c = lexicalColorPicker.getValue();
                lexicalColorPreview.setFill(c);
                String hex = String.format(
                    "#%02X%02X%02X",
                    (int) Math.round(c.getRed() * 255),
                    (int) Math.round(c.getGreen() * 255),
                    (int) Math.round(c.getBlue() * 255)
                );
                applicationSettings.setLexicalDescriptionColor(hex);
            });
            this.openLastProjectCheckBox.setSelected(
                applicationSettings.isOpenLastProject()
            );
        } catch (Exception ex) {
            // ignore color parse errors
        }

        // separators
        separatorsField.setText(applicationSettings.getMorphemeSeparators());

        // file patterns / extensions
        String fe = applicationSettings.getFileExtensions();
        if (fe == null || fe.isEmpty()) fe = "txt,md";
        fileExtensionsArea.setText(fe);

        String fp = applicationSettings.getFilePatterns();
        if (fp != null && !fp.isEmpty()) {
            filePatternsArea.setText(fp);
        } else {
            filePatternsArea.setText("");
        }

        // recent files limit
        recentFilesLimitField.setText(
            String.valueOf(applicationSettings.getMaxRecentFiles())
        );

        okButton.setOnAction(e -> {
            try {
                applicationSettings.setMorphemeSeparators(
                    separatorsField.getText()
                );
                // save file extension/pattern settings
                applicationSettings.setFileExtensions(
                    fileExtensionsArea.getText()
                );
                applicationSettings.setFilePatterns(filePatternsArea.getText());
                // save recent files limit
                int limit = Integer.parseInt(recentFilesLimitField.getText());
                if (limit <= 0) {
                    showError(
                        "Invalid value",
                        "Maximum number of recent files must be greater than 0"
                    );
                    return;
                }
                applicationSettings.setMaxRecentFiles(limit);
                applicationSettings.isOpenLastProject(
                    openLastProjectCheckBox.isSelected()
                );
            } catch (NumberFormatException ex) {
                showError(
                    "Invalid value",
                    "Maximum number of recent files must be an integer"
                );
                return;
            } catch (Exception ex) {
                showError("Error", ex.getMessage());
                return;
            }
            applicationSettings.saveConfiguration();
            stage.close();
        });

        cancelButton.setOnAction(e -> stage.close());
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
