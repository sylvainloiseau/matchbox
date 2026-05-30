package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.model.ApplicationSettings;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchboxgrammarapi.ParseError;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.model.TextParser;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleIGT;
import fr.cnrs.lacito.matchboxlanguage.corpusObject.SimpleLexicalDefinition;
import java.io.IOException;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import org.fxmisc.richtext.InlineCssTextArea;

/**
 * Controller for managing filePath editing and parsing
 */
public class EditorController {

    private InlineCssTextArea textArea;
    private Path selectedFilePath;
    //private String selectedFileContent = "";
    private long lastParseTime;
    private static final long PARSE_DELAY_MS = 500;
    private Corpus corpus;
    private Project project;

    private ApplicationSettings applicationSettings;
    private SimpleBooleanProperty currentTextCouldBeWrittenDownProperty =
        new SimpleBooleanProperty(false);
    private SimpleBooleanProperty currentTextCouldBeReparsedProperty =
        new SimpleBooleanProperty(false);
    private SimpleBooleanProperty closableProperty = new SimpleBooleanProperty(
        false
    );

    private final ChangeListener<? super String> changeListener;

    private final ObservableList<SimpleIGT> currentFileIgts =
        FXCollections.observableArrayList();
    private final ObservableList<
        SimpleLexicalDefinition
    > currentFileLexicalDefinition = FXCollections.observableArrayList();
    private final ObservableList<ParseError> currentFileErrors =
        FXCollections.observableArrayList();

    public EditorController(
        InlineCssTextArea codeArea,
        ApplicationSettings applicationSettings
    ) {
        this.changeListener = (obs, oldText, newText) -> {
            System.out.println("Editor text changed.");
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastParseTime > PARSE_DELAY_MS) {
                lastParseTime = currentTime;
                project.updateFileContent(newText, selectedFilePath);
            }
        };
        this.textArea = codeArea;

        this.lastParseTime = 0;
        this.applicationSettings = applicationSettings;
        clearEditor();

        this.textArea.setOnMouseClicked(
            new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent ME) {
                    handleCodeAreaClick(ME);
                }
            }
        );
    }

    public void setProject(Project project) {
        this.project = project;
        this.corpus = project.getCorpus();
    }

    private void handleCodeAreaClick(MouseEvent ME) {
        int carretPosition = textArea.getCaretPosition();
        if (selectedFilePath == null) return;
        for (SimpleIGT igt : corpus.getIgtForFile(selectedFilePath)) {
            if (
                carretPosition >= igt.getTextCoordinate().startIndex() &&
                carretPosition <= igt.getTextCoordinate().stopIndex()
            ) {
                System.out.println(
                    "Clicked inside IGT: " + igt.getFreeTranslation()
                );
                // TODO: notify MainController to select this IGT in the list view
                return;
            }
        }
    }

    public void highlightText() {
        if (
            textArea == null 
            // ||
            // lastParsedEntries == null ||
            // lastParsedEntries.isEmpty()
        ) {
            return;
        }
        String igtColorHex = applicationSettings.getIgtParagraphColor();
        for (SimpleIGT igt : corpus.getIgtForFile(selectedFilePath)) {
            textArea.setStyle(
                igt.getTextCoordinate().startIndex(),
                igt.getTextCoordinate().stopIndex() + 1,
                "-fx-background-color: " + igtColorHex + ";"
            );
            textArea.setStyle(
                igt.getTextCoordinate().startIndex(),
                igt.getTextCoordinate().stopIndex() + 1,
                "-fx-font-color: red;"
            );
        }
        String lexColorHex = applicationSettings.getLexicalDescriptionColor();
        for (SimpleLexicalDefinition lex : corpus.getLexicalDefinitionsForFile(
            selectedFilePath
        )) {
            textArea.setStyle(
                lex.getTextCoordinate().startIndex(),
                lex.getTextCoordinate().stopIndex() + 1,
                "-fx-background-color: " + lexColorHex + ";"
            );
        }
    }

    public void selectLinesInEditor(SimpleIGT igt) {
        if (igt == null) return;
        if (!igt.getTextCoordinate().path().equals(selectedFilePath)) {
            throw new IllegalArgumentException(
                "The group does not belong to the current file."
            );
        }
        int start = igt.getTextCoordinate().startIndex();
        int end = igt.getTextCoordinate().stopIndex();
        selectLineInEditor(start, end);
    }

    public void selectLinesInEditor(ParseError error) {
        int startLine = error.getLine();
        int posInLine = error.getCharPositionInLine();
        String[] lines = this.project
            .getParsedText(selectedFilePath)
            .getCurrentText()
            .split("\\n");
        int lineStartPosition = 0;
        for (int i = 0; i < startLine; i++) {
            lineStartPosition += lines[i].length();
        }
        int startPosition = lineStartPosition + posInLine;
        int endPosition = startPosition + 1;
        selectLineInEditor(startPosition, endPosition);
    }

    private void selectLineInEditor(int start, int end) {
        Platform.runLater(() -> {
            try {
                textArea.moveTo(start);
                textArea.selectRange(start, end + 1);
                textArea.requestFocus();
            } catch (Exception e) {
                System.err.println("Error selecting group: " + e.getMessage());
            }
        });
    }

    public void selectEditedFile(Path filePath) {
        clearEditor();
        this.selectedFilePath = filePath;
        String selectedFileContent = project
            .getParsedText(selectedFilePath)
            .getCurrentText();
        textArea.replaceText(selectedFileContent);
        textArea.setEditable(true);
        this.currentFileLexicalDefinition.addAll(
            corpus.getLexicalDefinitionsForFile(selectedFilePath)
        );
        this.currentFileIgts.addAll(corpus.getIgtForFile(selectedFilePath));
        this.currentFileErrors.addAll(corpus.getErrorsForFile(selectedFilePath));
        highlightText();

        this.textArea.textProperty().addListener(changeListener);
        currentTextCouldBeWrittenDownProperty.bind(
            project.getParsedText(filePath).getCouldBeWrittenBooleanProperty()
        );
        currentTextCouldBeReparsedProperty.bind(
            project.getParsedText(filePath).getCouldBeReparsedBooleanProperty()
        );
        closableProperty.set(true);
    }

    public void clearEditor() {
        currentTextCouldBeWrittenDownProperty.unbind();
        currentTextCouldBeWrittenDownProperty.set(false);
        currentTextCouldBeReparsedProperty.unbind();
        currentTextCouldBeReparsedProperty.set(false);
        closableProperty.set(false);
        this.selectedFilePath = null;
        this.textArea.textProperty().removeListener(changeListener);
        if (textArea != null) {
            textArea.clear();
            textArea.setEditable(false);
        }
        this.currentFileLexicalDefinition.clear();
        this.currentFileIgts.clear();
        this.currentFileErrors.clear();
    }

    public BooleanExpression getCurrentTextCouldBeWrittenDownProperty() {
        return currentTextCouldBeWrittenDownProperty;
    }

    public BooleanExpression getClosableProperty() {
        return closableProperty;
    }

    public BooleanExpression getCurrentTextCouldBeParsedAgainProperty() {
        return currentTextCouldBeReparsedProperty;
    }

    public void saveEditedFile() throws IOException {
        if (selectedFilePath == null) {
            throw new IOException("No file is currently being edited.");
        }
        TextParser text = project.getParsedText(selectedFilePath);
        if (text == null) {
            throw new IOException(
                "No parser found for the current edited file."
            );
        }
        if (!text.getCouldBeWritten()) {
            System.out.println(
                "No changes to save for file: " + selectedFilePath.toString()
            );
            return;
        }
        project.saveFile(selectedFilePath, textArea.getText());
    }

    public void parseEditedFile() {
        project.parsePath(selectedFilePath);
    }

    public ObservableList<SimpleIGT> getCurrentFileIgts() {
        return this.currentFileIgts;
    }

    public ObservableList<SimpleLexicalDefinition> getCurrentFileLexicalDefinition() {
        return this.currentFileLexicalDefinition;
    }

    public ObservableList<ParseError> getCurrentFileErrors() {
        return this.currentFileErrors;
    }

    public InlineCssTextArea getFileContentArea() {
        return textArea;
    }
}
