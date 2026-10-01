package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorReporterTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final Logger logger = Logger.getLogger(ErrorReporter.class.getName());
    private final Handler handler = new Handler() {
        public void publish(LogRecord r) { records.add(r); }
        public void flush() {}
        public void close() {}
    };

    @BeforeEach
    void capture() {
        logger.addHandler(handler);
        ErrorReporter.setDisplay((owner, title, message) -> shown.add(title + " | " + message));
        ErrorReporter.setLogFile(Path.of("/logs/matchbox-0.log"));
    }

    @AfterEach
    void release() {
        logger.removeHandler(handler);
    }

    @Test
    void shownErrorsAreLoggedWithTheirStackTrace() {
        IOException error = new IOException("Permission denied: notes.txt");
        ErrorReporter.report(null, "Error opening file", error);
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertSame(error, records.get(0).getThrown());
        assertEquals(List.of("Error opening file | Permission denied: notes.txt"), shown);
    }

    @Test
    void programmingErrorsPointToTheLogFile() {
        NullPointerException npe = new NullPointerException("Cannot invoke \"Pattern.matcher\" because \"this.pattern\" is null");
        ErrorReporter.report(null, "Error opening file", new CompletionException(npe));
        assertSame(npe, records.get(0).getThrown(), "unwrapped");
        String message = shown.get(0);
        assertTrue(message.contains("unexpected error occurred (NullPointerException"), message);
        assertTrue(message.contains("/logs/matchbox-0.log"), message);
    }

    @Test
    void exceptionsWithoutMessageShowTheirType() {
        assertTrue(ErrorReporter.userMessage(new IOException()).contains("IOException"));
    }

    @Test
    void problemsWithoutExceptionAreLoggedToo() {
        ErrorReporter.report(null, "Some parts of the project could not be loaded", "bad pattern");
        assertEquals(1, records.size());
        assertTrue(records.get(0).getMessage().contains("bad pattern"));
    }
}
