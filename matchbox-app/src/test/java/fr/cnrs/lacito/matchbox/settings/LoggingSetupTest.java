package fr.cnrs.lacito.matchbox.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingSetupTest {

    @Test
    void appendsAcrossInitialisationsInPlainText(@TempDir Path dir) throws Exception {
        AppDirectories dirs = new AppDirectories(dir.resolve("config"), dir.resolve("state"), dir.resolve("logs"));
        Logger logger = Logger.getLogger("matchbox.test");
        try (UserSettings settings = UserSettings.load(dirs, dir.resolve("home"), 10)) {
            Path log = LoggingSetup.init(settings);
            assertEquals(dirs.logDir().resolve("matchbox-0.log"), log);
            logger.info("first session");

            LoggingSetup.init(settings); // next launch
            logger.info("second session");
            LoggingSetup.init(settings); // closes the previous handler, flushing it

            String content = Files.readString(log);
            assertTrue(content.contains("first session"), content);
            assertTrue(content.contains("second session"), content);
            assertTrue(content.lines().anyMatch(l -> l.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d INFO matchbox.test - first session")), content);
        } finally {
            // the log file must be closed before the temporary directory is deleted (Windows)
            LoggingSetup.close();
        }
    }
}
