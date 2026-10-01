package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.assist.suggestion.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.MultiChangeBuilder;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.TextChange;
import org.fxmisc.richtext.util.UndoUtils;
import org.fxmisc.undo.UndoManagerFactory;
import org.reactfx.EventStream;

/**
 * Controller for managing filePath editing and parsing
 */
public class EditorController {

    private InlineCssTextArea textArea;
    private Path selectedFilePath;
    private long lastParseTime;
    private static final long PARSE_DELAY_MS = 500;
    private Corpus corpus;
    private Project project;

    private final HighlightStyles highlightStyles;
    private SimpleBooleanProperty currentTextCouldBeWrittenDownProperty =
        new SimpleBooleanProperty(false);
    private SimpleBooleanProperty currentTextCouldBeReparsedProperty =
        new SimpleBooleanProperty(false);
    private SimpleBooleanProperty closableProperty = new SimpleBooleanProperty(
        false
    );

    private final ChangeListener<? super String> changeListener;

    /**
     * True while provisional text (an inline suggestion shown in grey) is inserted or removed:
     * such changes must not reach the model, the undo history or the suggestion anchors.
     */
    private boolean ghostOperation;
    /** Removes the provisional text, if any; set by the inline suggestion controller. */
    private Runnable ghostDismisser = () -> {};
    /** Style of the temporary highlight of a suggestion's range. */
    private static final String PREVIEW_STYLE = "-rtfx-background-color: #ffd8a8;";
    private final Consumer<Path> corpusListener = this::onCorpusFileChanged;

    /** Top-level corpus objects of the current file, grammars in rank order. */
    private final ObservableList<CorpusObject> currentFileObjects =
        FXCollections.observableArrayList();
    private final ObservableList<ParseError> currentFileErrors =
        FXCollections.observableArrayList();
    /** Object selected in the editor, a table or a view (may be a nested object). */
    private final ObjectProperty<CorpusObject> selectedObject =
        new SimpleObjectProperty<>();

    public EditorController(
        InlineCssTextArea codeArea,
        UserSettings settings
    ) {
        this.changeListener = (obs, oldText, newText) -> {
            if (ghostOperation) return;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastParseTime > PARSE_DELAY_MS) {
                lastParseTime = currentTime;
                project.updateFileContent(newText, selectedFilePath);
            }
        };
        this.textArea = codeArea;
        // the default undo manager, except for the changes of provisional text
        this.textArea.setUndoManager(UndoManagerFactory.unlimitedHistoryFactory().createMultiChangeUM(
            textArea.multiPlainChanges().filter(changes -> !ghostOperation),
            TextChange::invert,
            UndoUtils.applyMultiPlainTextChange(textArea),
            TextChange::mergeWith,
            TextChange::isIdentity,
            UndoUtils.DEFAULT_PREVENT_MERGE_DELAY
        ));

        this.lastParseTime = 0;
        this.highlightStyles = new HighlightStyles(settings);
        // show colour changes made in the settings immediately
        settings.igtColorProperty().addListener((obs, o, n) -> highlightText());
        settings.lexicalColorProperty().addListener((obs, o, n) -> highlightText());
        clearEditor();

        this.textArea.setOnMouseClicked(e -> handleCodeAreaClick());
    }

    public void setProject(Project project) {
        if (this.corpus != null) {
            this.corpus.removeFileListener(corpusListener);
        }
        this.project = project;
        this.corpus = project.getCorpus();
        this.corpus.addFileListener(corpusListener);
    }

    public HighlightStyles getHighlightStyles() {
        return highlightStyles;
    }

    // ------------------------------------------------------------------
    // Corpus objects of the current file
    // ------------------------------------------------------------------

    private void onCorpusFileChanged(Path file) {
        if (file.equals(selectedFilePath)) {
            refreshFromCorpus();
        }
    }

    /** Reloads objects and errors of the current file and keeps the selection if possible. */
    private void refreshFromCorpus() {
        CorpusObject previous = selectedObject.get();
        currentFileObjects.setAll(corpus.objectsFor(selectedFilePath));
        currentFileErrors.setAll(corpus.errorsFor(selectedFilePath));
        highlightText();
        selectedObject.set(previous == null ? null : findSuccessor(previous).orElse(null));
    }

    /**
     * After a re-parse all objects are new instances: find the object that replaces
     * {@code previous}. Preferably an object of the same kind and label (the closest one to the
     * previous position, which may have moved if text was inserted before it), otherwise the
     * object of the same kind containing the previous start offset.
     */
    private Optional<CorpusObject> findSuccessor(CorpusObject previous) {
        if (!Objects.equals(previous.getTextCoordinate().path(), selectedFilePath)) {
            return Optional.empty();
        }
        int start = previous.getTextCoordinate().startIndex();
        List<CorpusObject> sameKind = corpus.flatten(selectedFilePath)
            .filter(o -> o.getKind().equals(previous.getKind()))
            .toList();
        return sameKind.stream()
            .filter(o -> Objects.equals(o.getLabel(), previous.getLabel()))
            .min(Comparator.comparingInt(o -> Math.abs(o.getTextCoordinate().startIndex() - start)))
            .or(() -> sameKind.stream().filter(o -> o.getTextCoordinate().contains(start)).findFirst());
    }

    private void handleCodeAreaClick() {
        dismissGhost();
        if (selectedFilePath == null || corpus == null) return;
        int caretPosition = textArea.getCaretPosition();
        corpus.objectAt(selectedFilePath, caretPosition).ifPresent(selectedObject::set);
    }

    /** Highlights every corpus object of the file: grammars in rank order, parents before children. */
    public void highlightText() {
        if (textArea == null || selectedFilePath == null || corpus == null) {
            return;
        }
        // spans refer to the real text: remove provisional text first
        dismissGhost();
        int length = textArea.getLength();
        if (length > 0) {
            textArea.clearStyle(0, length);
        }
        corpus.flatten(selectedFilePath).forEach(o -> {
            String color = highlightStyles.colorFor(o);
            TextCoordinate span = o.getTextCoordinate();
            // spans refer to the parsed text; the editor text may have changed since
            if (color != null && span.stopIndex() < length) {
                textArea.setStyle(
                    span.startIndex(),
                    span.stopIndex() + 1,
                    "-rtfx-background-color: " + color + ";"
                );
            }
        });
    }

    // ------------------------------------------------------------------
    // Selection in the editor
    // ------------------------------------------------------------------

    /** Selects a span of the current file. */
    public void selectInEditor(TextCoordinate span) {
        if (span == null) return;
        if (!Objects.equals(span.path(), selectedFilePath)) {
            throw new IllegalArgumentException(
                "The span does not belong to the current file: " + span
            );
        }
        selectRangeInEditor(span.startIndex(), span.stopIndex());
    }

    public void selectLinesInEditor(ParseError error) {
        if (error.getStartIndex() >= 0) {
            selectRangeInEditor(error.getStartIndex(), Math.max(error.getStartIndex(), error.getStopIndex()));
            return;
        }
        String text = textArea.getText();
        // ANTLR lines are 1-based; line 0 is used for errors without position
        int line = Math.max(1, error.getLine());
        int lineStart = 0;
        for (int i = 1; i < line && lineStart >= 0; i++) {
            int newline = text.indexOf('\n', lineStart);
            lineStart = newline < 0 ? -1 : newline + 1;
        }
        if (lineStart < 0) return;
        int start = Math.min(text.length(), lineStart + Math.max(0, error.getCharPositionInLine()));
        selectRangeInEditor(start, start);
    }

    /** Selects from {@code start} to {@code stop}, both included. */
    private void selectRangeInEditor(int start, int stop) {
        Platform.runLater(() -> {
            dismissGhost();
            int length = textArea.getLength();
            int from = Math.min(start, length);
            int to = Math.min(stop + 1, length);
            textArea.moveTo(from);
            textArea.selectRange(from, to);
            textArea.requestFollowCaret();
            textArea.requestFocus();
        });
    }

    // ------------------------------------------------------------------
    // Edited file
    // ------------------------------------------------------------------

    public void selectEditedFile(Path filePath) {
        clearEditor();
        this.selectedFilePath = filePath;
        TextDocument document = project.getParsedText(selectedFilePath);
        textArea.replaceText(document.getCurrentText());
        // undoing must not go back to the text of the previous file
        textArea.getUndoManager().forgetHistory();
        textArea.setEditable(true);
        refreshFromCorpus();

        this.textArea.textProperty().addListener(changeListener);
        currentTextCouldBeWrittenDownProperty.bind(
            document.getCouldBeWrittenBooleanProperty()
        );
        currentTextCouldBeReparsedProperty.bind(
            document.getCouldBeReparsedBooleanProperty()
        );
        closableProperty.set(true);
    }

    public Path getSelectedFilePath() {
        return selectedFilePath;
    }

    public void clearEditor() {
        dismissGhost();
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
        this.selectedObject.set(null);
        this.currentFileObjects.clear();
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
        dismissGhost();
        TextDocument text = project.getParsedText(selectedFilePath);
        // the text area pushes its text at most every PARSE_DELAY_MS
        text.updateText(textArea.getText());
        if (!text.getCouldBeWritten()) {
            return;
        }
        project.saveFile(selectedFilePath, textArea.getText());
    }

    /**
     * Parses the current editor text in the background; the views are refreshed when the
     * results are applied.
     */
    public CompletableFuture<Void> parseEditedFile() {
        if (selectedFilePath == null) {
            return CompletableFuture.completedFuture(null);
        }
        dismissGhost();
        // the text area pushes its text at most every PARSE_DELAY_MS: push the latest text
        project.updateFileContent(textArea.getText(), selectedFilePath);
        return project.parseInBackground(selectedFilePath);
    }

    public ObservableList<CorpusObject> getCurrentFileObjects() {
        return this.currentFileObjects;
    }

    public ObservableList<ParseError> getCurrentFileErrors() {
        return this.currentFileErrors;
    }

    public ObjectProperty<CorpusObject> selectedObjectProperty() {
        return selectedObject;
    }

    public InlineCssTextArea getFileContentArea() {
        return textArea;
    }

    // ------------------------------------------------------------------
    // Assistant
    // ------------------------------------------------------------------

    /** Runs a change of provisional text: it reaches neither the model, nor the undo history. */
    public void runGhostOperation(Runnable change) {
        boolean previous = ghostOperation;
        ghostOperation = true;
        try {
            change.run();
        } finally {
            ghostOperation = previous;
        }
    }

    public boolean isGhostOperation() {
        return ghostOperation;
    }

    public void setGhostDismisser(Runnable dismisser) {
        this.ghostDismisser = Objects.requireNonNull(dismisser);
    }

    /** Removes the provisional text of an inline suggestion, if any. */
    public void dismissGhost() {
        if (!ghostOperation) {
            ghostDismisser.run();
        }
    }

    /** Changes of the real text (provisional text excluded). */
    public EventStream<PlainTextChange> userChanges() {
        return textArea.plainTextChanges().filter(change -> !ghostOperation);
    }

    /** The real text of the editor (provisional text removed). */
    public String currentText() {
        dismissGhost();
        return textArea.getText();
    }

    public boolean isEditing() {
        return selectedFilePath != null;
    }

    /**
     * Applies edits as one undoable change, if the text still holds what they expect.
     *
     * @return false if the text changed and the edits no longer apply
     */
    public boolean applyEdits(List<TextEdit> edits) {
        if (selectedFilePath == null || edits.isEmpty()) return false;
        dismissGhost();
        String text = textArea.getText();
        if (!edits.stream().allMatch(e -> e.matches(text))) {
            return false;
        }
        textArea.getUndoManager().preventMerge();
        // in reverse order, so that the offsets of the next edits stay valid
        List<TextEdit> sorted = edits.stream().sorted(Comparator.comparingInt(TextEdit::start).reversed()).toList();
        MultiChangeBuilder<String, String, String> change = textArea.createMultiChange(sorted.size());
        for (TextEdit e : sorted) {
            change.replaceTextAbsolutely(e.start(), e.end(), e.replacement());
        }
        change.commit();
        textArea.getUndoManager().preventMerge();
        TextEdit first = sorted.get(sorted.size() - 1);
        textArea.moveTo(Math.min(textArea.getLength(), first.start() + first.replacement().length()));
        textArea.requestFollowCaret();
        return true;
    }

    /** Highlights a range temporarily (e.g. while the pointer is over a suggestion). */
    public void previewRange(int start, int end) {
        highlightText();
        int length = textArea.getLength();
        int from = Math.min(start, length);
        int to = Math.min(Math.max(end, start + 1), length);
        if (from < to) {
            textArea.setStyle(from, to, PREVIEW_STYLE);
        }
    }

    public void clearPreview() {
        highlightText();
    }

    /** The parse error at an offset (its span contains it, or it is on the same line). */
    public Optional<ParseError> errorAt(int offset) {
        String text = textArea.getText();
        int line = lineOf(text, offset);
        return currentFileErrors.stream()
            .filter(e -> e.span().map(sp -> sp.contains(offset)).orElse(false) || e.getLine() == line)
            .min(Comparator.comparingInt(e -> Math.abs(PromptBuilder.errorOffset(text, e) - offset)));
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** Selects a range given by offsets ({@code end} exclusive). */
    public void selectRange(int start, int end) {
        selectRangeInEditor(start, Math.max(start, end - 1));
    }
}
