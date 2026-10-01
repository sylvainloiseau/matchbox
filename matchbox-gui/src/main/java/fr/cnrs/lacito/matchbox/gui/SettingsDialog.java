package fr.cnrs.lacito.matchbox.gui;

import fr.cnrs.lacito.matchbox.settings.SettingsData;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
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
 * Dialog for application settings. It edits a copy of the settings: nothing changes until OK,
 * and Cancel discards everything.
 */
public class SettingsDialog {

    private final UserSettings settings;
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
    private ComboBox<String> charsetComboBox;

    @FXML
    private TextArea fileExtensionsArea;

    @FXML
    private TextArea filePatternsArea;

    @FXML
    private TextField recentFilesLimitField;

    @FXML
    private TextField recentProjectsLimitField;

    @FXML
    private Button okButton;

    @FXML
    private Button cancelButton;

    @FXML
    private CheckBox openLastProjectCheckBox;

    public SettingsDialog(
        UserSettings settings,
        Stage parentStage
    ) {
        this.settings = settings;
        this.parentStage = parentStage;
    }

    public void showAndWait() {
        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.setTitle("Settings");
        stage.setWidth(460);
        stage.setHeight(380);

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
            initFromSettings(settings.snapshot());

            stage.showAndWait();
        } catch (Exception ex) {
            ErrorReporter.report(parentStage, "Error loading the settings dialog", ex);
        }
    }

    private void initFromSettings(SettingsData current) {
        initColor(igtColorPicker, igtColorPreview, current.igtColor());
        initColor(lexicalColorPicker, lexicalColorPreview, current.lexicalColor());

        separatorsField.setText(current.morphemeSeparators());

        charsetComboBox.getItems().setAll(Charset.availableCharsets().keySet());
        charsetComboBox.setValue(current.charset());

        fileExtensionsArea.setText(String.join("\n", current.fileExtensions()));
        filePatternsArea.setText(String.join("\n", current.filePatterns()));

        recentFilesLimitField.setText(String.valueOf(current.maxRecentFiles()));
        recentProjectsLimitField.setText(String.valueOf(current.maxRecentProjects()));
        openLastProjectCheckBox.setSelected(current.openLastProject());

        okButton.setOnAction(e -> {
            try {
                settings.apply(edited(current));
            } catch (IllegalArgumentException ex) {
                showError("Invalid value", ex.getMessage());
                return;
            }
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());
    }

    /** The values of the dialog; throws IllegalArgumentException with a message for the user. */
    private SettingsData edited(SettingsData current) {
        String charset = charsetComboBox.getValue();
        if (charset == null || !Charset.isSupported(charset)) {
            throw new IllegalArgumentException("Unknown charset: " + charset);
        }
        return new SettingsData(
            current.version(),
            charset,
            toHex(igtColorPicker.getValue()),
            toHex(lexicalColorPicker.getValue()),
            separatorsField.getText(),
            SettingsData.normalizeExtensions(splitItems(fileExtensionsArea.getText(), "[,\\s]+")),
            splitItems(filePatternsArea.getText(), "\\R"),
            positiveInt(recentFilesLimitField.getText(), "Maximum number of recent files"),
            positiveInt(recentProjectsLimitField.getText(), "Maximum number of recent projects"),
            openLastProjectCheckBox.isSelected(),
            current.logFile(),
            current.assist()
        );
    }

    /** The picker only changes the preview; the value is applied on OK. */
    private static void initColor(ColorPicker picker, Rectangle preview, String css) {
        Color color;
        try {
            color = Color.web(css);
        } catch (IllegalArgumentException e) {
            color = Color.WHITE;
        }
        picker.setValue(color);
        preview.setFill(color);
        picker.setOnAction(ev -> preview.setFill(picker.getValue()));
    }

    private static String toHex(Color c) {
        return String.format(
            "#%02X%02X%02X",
            (int) Math.round(c.getRed() * 255),
            (int) Math.round(c.getGreen() * 255),
            (int) Math.round(c.getBlue() * 255)
        );
    }

    private static List<String> splitItems(String text, String separator) {
        return Arrays.stream(text.split(separator)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static int positiveInt(String text, String name) {
        try {
            int value = Integer.parseInt(text.strip());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException(name + " must be an integer greater than 0");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
