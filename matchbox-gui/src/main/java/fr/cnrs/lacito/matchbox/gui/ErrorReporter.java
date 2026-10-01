package fr.cnrs.lacito.matchbox.gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/**
 * Reports errors to the user: every error shown in a dialog is first written to the log, with its
 * stack trace. Also installs the handler of exceptions that nothing else caught.
 */
public final class ErrorReporter {

    private static final Logger logger = Logger.getLogger(ErrorReporter.class.getName());

    /** Shows a message to the user; replaced in tests. */
    interface Display {
        void show(Window owner, String title, String message);
    }

    private static Display display = ErrorReporter::showAlert;
    private static Path logFile;
    private static final java.util.concurrent.atomic.AtomicBoolean uncaughtShown =
        new java.util.concurrent.atomic.AtomicBoolean();

    private ErrorReporter() {}

    /** The log file, mentioned in the messages of unexpected errors. */
    public static void setLogFile(Path file) {
        logFile = file;
    }

    static void setDisplay(Display d) {
        display = Objects.requireNonNull(d);
    }

    /**
     * Logs an error with its stack trace, then shows it.
     *
     * @param title what failed, e.g. "Error opening file"
     */
    public static void report(Window owner, String title, Throwable error) {
        Throwable cause = unwrap(error);
        logger.log(Level.WARNING, title, cause);
        display.show(owner, title, userMessage(cause));
    }

    /** Shows a problem that is not an exception (e.g. files that could not be read), and logs it. */
    public static void report(Window owner, String title, String message) {
        logger.warning(title + ": " + message);
        display.show(owner, title, message);
    }

    /**
     * The message shown to the user: the exception's own message for expected failures; for
     * programming errors (whose messages mean nothing to users), a note that details are in the log.
     */
    static String userMessage(Throwable error) {
        String message = error.getMessage();
        boolean bug = error instanceof NullPointerException || error instanceof ClassCastException
            || error instanceof IndexOutOfBoundsException || error instanceof IllegalStateException
            || error instanceof UnsupportedOperationException || error instanceof Error;
        if (bug || message == null || message.isBlank()) {
            String what = error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
            return "An unexpected error occurred (" + what + ").\n\nDetails were written to the log file" +
                (logFile == null ? "." : ":\n" + logFile);
        }
        return message;
    }

    static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /** Logs and shows the exceptions that nothing caught, including those of the JavaFX thread. */
    public static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            logger.log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), error);
            // one dialog at a time, shown outside layout and animation processing
            if (!uncaughtShown.compareAndSet(false, true)) {
                return;
            }
            try {
                Platform.runLater(() -> {
                    try {
                        display.show(null, "Unexpected error", userMessage(unwrap(error)));
                    } finally {
                        uncaughtShown.set(false);
                    }
                });
            } catch (RuntimeException e) {
                // the toolkit is not running (e.g. at shutdown): the error is logged
                uncaughtShown.set(false);
            }
        });
    }

    private static void showAlert(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (message.length() > 300 || message.lines().count() > 6) {
            TextArea area = new TextArea(message);
            area.setEditable(false);
            area.setWrapText(true);
            alert.getDialogPane().setContent(area);
        } else {
            alert.setContentText(message);
        }
        alert.showAndWait();
    }
}
