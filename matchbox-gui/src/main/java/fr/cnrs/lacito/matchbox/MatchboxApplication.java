package fr.cnrs.lacito.matchbox;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.ErrorReporter;
import fr.cnrs.lacito.matchbox.gui.MainController;
import fr.cnrs.lacito.matchbox.settings.AppDirectories;
import fr.cnrs.lacito.matchbox.settings.LoggingSetup;
import fr.cnrs.lacito.matchbox.settings.UserSettings;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

/**
 * Main entry point for the Notebook application
 */
public class MatchboxApplication extends Application {

    private static final Logger logger = Logger.getLogger(MatchboxApplication.class.getName());

    private UserSettings settings;
    private MainController mainController;

    @Override
    public void start(Stage primaryStage) throws IOException {
        settings = UserSettings.load(AppDirectories.forCurrentUser());
        try {
            ErrorReporter.setLogFile(LoggingSetup.init(settings));
        } catch (IOException e) {
            // the application still works without a log file
            logger.log(Level.WARNING, "Cannot write the log file", e);
        }

        // exceptions that nothing caught are logged and shown
        ErrorReporter.installUncaughtExceptionHandler();

        List<GrammarPlugin> plugins = PluginManager.loadPlugins();

        // Load the FXML file
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fr/cnrs/lacito/matchbox/gui/MainView.fxml")
        );

        // set the controller and set up dependencies
        mainController = new MainController(settings, plugins);
        loader.setController(mainController);
        var root = loader.load();

        primaryStage.getIcons().clear();
        // primaryStage
        //     .getIcons()
        //     .add(
        //         new javafx.scene.image.Image(
        //             getClass().getResourceAsStream(
        //                 "/fr/cnrs/lacito/matchbox.png"
        //             )
        //         )
        //     );
        mainController.setPrimaryStage(primaryStage);
        mainController.setHostServices(getHostServices());

        // Create scene and set up stage
        Scene scene = new Scene((Parent) root, 1200, 800);
        primaryStage.setScene(scene);
        // unsaved files (possibly changed by object views only) are not lost silently
        primaryStage.setOnCloseRequest(e -> {
            if (!mainController.confirmQuit()) {
                e.consume();
            }
        });
        primaryStage.show();

        List<String> warnings = settings.getLoadWarnings();
        if (!warnings.isEmpty()) {
            Platform.runLater(() -> showSettingsWarnings(primaryStage, warnings));
        }
    }

    private static void showSettingsWarnings(Stage owner, List<String> warnings) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle("Settings");
        alert.setHeaderText("Some settings could not be read; default values are used.");
        TextArea details = new TextArea(String.join("\n", warnings));
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setContent(details);
        alert.show();
    }

    @Override
    public void stop() {
        if (mainController != null) {
            mainController.shutdown();
        }
        if (settings != null) {
            settings.close();
        }
        // flushes and releases the log file
        LoggingSetup.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
