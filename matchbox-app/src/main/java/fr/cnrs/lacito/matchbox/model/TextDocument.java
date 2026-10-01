package fr.cnrs.lacito.matchbox.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Parser for extracting three-line groups from text using ANTLR
 */
public class TextDocument {

    private volatile String content;
    private volatile long version;
    private Path filePath;

    public SimpleBooleanProperty couldBeWritten = new SimpleBooleanProperty(
        false
    );
    public SimpleBooleanProperty couldBeReParsed = new SimpleBooleanProperty(
        false
    );

    public TextDocument(Path filePath) {
        this.filePath = filePath;
        this.content = readFile();
    }

    /**
     * Reads the content of a file
     */
    protected String readFile() {
        if (couldBeWritten.get()) {
            throw new IllegalStateException(
                "Cannot read file while there are unsaved changes. Please save the file before reading."
            );
        }
        try {
            this.content = new String(Files.readAllBytes(filePath));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return this.content;
    }

    /** Marks the document as parsed (called when parse results matching its text are applied). */
    public void markParsed() {
        couldBeReParsed.set(false);
    }

    public void updateText(String newText) {
        if (newText.equals(this.content)) {
            return;
        }
        this.content = newText;
        this.version++;
        couldBeWritten.set(true);
        couldBeReParsed.set(true);
    }

    /** Incremented at each change of the text; used to discard results of outdated parses. */
    public long getVersion() {
        return version;
    }

    /**
     * Writes content to a file
     */
    public void writeFile() throws IOException {
        Files.write(filePath, content.getBytes());
        couldBeWritten.set(false);
    }

    public boolean getCouldBeWritten() {
        return couldBeWritten.get();
    }

    public SimpleBooleanProperty getCouldBeWrittenBooleanProperty() {
        return couldBeWritten;
    }

    public boolean getCouldBeReparsed() {
        return couldBeReParsed.get();
    }

    public SimpleBooleanProperty getCouldBeReparsedBooleanProperty() {
        return couldBeReParsed;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getCurrentText() {
        return content;
    }
}
