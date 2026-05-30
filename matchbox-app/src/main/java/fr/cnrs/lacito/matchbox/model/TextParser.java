package fr.cnrs.lacito.matchbox.model;

import fr.cnrs.lacito.matchbox.model.project.FileType;
import fr.cnrs.lacito.matchboxgrammarapi.ParseError;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleIGT;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleLexicalDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Parser for extracting three-line groups from text using ANTLR
 */
public class TextParser {

    private String content;
    private Path filePath;

    public SimpleBooleanProperty couldBeWritten = new SimpleBooleanProperty(
        false
    );
    public SimpleBooleanProperty couldBeReParsed = new SimpleBooleanProperty(
        false
    );

    public TextParser(Path filePath) {
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

    protected void isParsed() {
        couldBeReParsed.set(false);
    }

    public void updateText(String newText) {
        this.content = newText;
        couldBeWritten.set(true);
        couldBeReParsed.set(true);
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
