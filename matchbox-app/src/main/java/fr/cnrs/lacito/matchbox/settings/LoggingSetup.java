package fr.cnrs.lacito.matchbox.settings;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Sends the application log to a plain-text file that is appended to across launches:
 * {@code matchbox-0.log} … {@code matchbox-4.log} (1 MB each) in the log directory of the OS,
 * or the file chosen by the user ({@link UserSettings#customLogFile()}).
 */
public final class LoggingSetup {

    static final int LIMIT_BYTES = 1024 * 1024;
    static final int COUNT = 5;

    private static Handler installed;

    private LoggingSetup() {}

    /**
     * Installs the file handler on the root logger (replacing one installed before) and logs the
     * messages produced while loading the settings.
     *
     * @return the log file (for the default location, the current file {@code matchbox-0.log})
     */
    public static synchronized Path init(UserSettings settings) throws IOException {
        Logger root = Logger.getLogger("");
        if (installed != null) {
            // close first: the new handler would otherwise find the file locked and use another name
            root.removeHandler(installed);
            installed.close();
            installed = null;
        }
        Path logFile;
        FileHandler handler;
        var custom = settings.customLogFile();
        if (custom.isPresent()) {
            logFile = custom.get().toAbsolutePath();
            Files.createDirectories(logFile.getParent());
            handler = new FileHandler(escape(logFile.toString()), 0, 1, true);
        } else {
            Path dir = settings.directories().logDir();
            Files.createDirectories(dir);
            logFile = dir.resolve("matchbox-0.log");
            handler = new FileHandler(escape(dir.toString()) + "/matchbox-%g.log", LIMIT_BYTES, COUNT, true);
        }
        handler.setEncoding("UTF-8");
        handler.setFormatter(new OneLineFormatter());

        root.addHandler(handler);
        installed = handler;

        Logger logger = Logger.getLogger(LoggingSetup.class.getName());
        logger.info("Log file: " + logFile + "; settings: " + settings.directories());
        settings.getLoadReport().forEach(m -> logger.log(m.level(), m.text()));
        return logFile;
    }

    /** {@code %} has a meaning in {@link FileHandler} patterns. */
    private static String escape(String path) {
        return path.replace("%", "%%");
    }

    /** {@code 2026-10-01 12:00:00 INFO logger - message}, then the stack trace if any. */
    static final class OneLineFormatter extends Formatter {
        private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        @Override
        public String format(LogRecord r) {
            StringBuilder sb = new StringBuilder()
                .append(TIME.format(r.getInstant())).append(' ')
                .append(r.getLevel().getName()).append(' ')
                .append(r.getLoggerName()).append(" - ")
                .append(formatMessage(r))
                .append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
