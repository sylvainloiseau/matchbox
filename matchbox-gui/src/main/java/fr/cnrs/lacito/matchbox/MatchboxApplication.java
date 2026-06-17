package fr.cnrs.lacito.matchbox;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.MainController;
import fr.cnrs.lacito.matchbox.model.ApplicationSettings;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.logging.XMLFormatter;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main entry point for the Notebook application
 */
public class MatchboxApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        ApplicationSettings applicationSettings = new ApplicationSettings();
        initLoggingService(applicationSettings.getLogFile());

        List<GrammarPlugin> plugins = PluginManager.loadPlugins();
            
        // Load the FXML file
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fr/cnrs/lacito/matchbox/gui/MainView.fxml")
        );

        // set the controller and set up dependencies
        MainController mainController = new MainController(applicationSettings, plugins);
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

        // Create scene and set up stage
        Scene scene = new Scene((Parent) root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Notebook Application");
        primaryStage.show();
    }

    private static void initLoggingService(Path logFile)
        throws FileNotFoundException {
        OutputStream logOutputStream = new FileOutputStream(logFile.toFile());
        StreamHandler sh = new StreamHandler(
            logOutputStream,
            new XMLFormatter()
        );
        Logger.getLogger("").addHandler(sh);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
