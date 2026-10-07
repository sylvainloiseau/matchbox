package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.CorpusListener;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaPlayers;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaResolver;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MediaPaths;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.model.DocumentChange;
import fr.cnrs.lacito.matchbox.model.DocumentListener;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.TextHistory;
import fr.cnrs.lacito.matchbox.model.project.Project;
import fr.cnrs.lacito.matchbox.settings.UserSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.MultiChangeBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.util.UndoUtils;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import org.reactfx.EventStream;

/**
 * Shows one {@link TextDocument} in the code area and keeps both in sync.
 *
 * <p>The document owns the text: the user's typing is forwarded to it at once (one change per
 * keystroke, paste…), and the changes made by others (views, assistant, search, undo) are mirrored
 * in the code area. The undo history is the document's ({@link TextHistory}), so it survives a
 * change of file and contains the changes made while the document was not displayed.
 */
public class EditorController {

    private static final Logger logger = Logger.getLogger(EditorController.class.getName());

    private final InlineCssTextArea textArea;
    private TextDocument document;
    private Corpus corpus;
    private Project project;

    private final HighlightStyles highlightStyles;
    private final SimpleBooleanProperty currentTextCouldBeWrittenDownProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty currentTextCouldBeReparsedProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty closableProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty undoAvailable = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty redoAvailable = new SimpleBooleanProperty(false);

    /**
     * True while provisional text (an inline suggestion shown in grey) is inserted or removed:
     * such changes must not reach the document, the undo history or the suggestion anchors.
     */
    private boolean ghostOperation;
    /** True while a change of the document is copied into the code area (not forwarded back). */
    private boolean mirroring;
    /** Removes the provisional text, if any; set by the inline suggestion controller. */
    private Runnable ghostDismisser = () -> {};
    /** Style of the temporary highlight of a suggestion's range. */
    private static final String PREVIEW_STYLE = "-rtfx-background-color: #ffd8a8;";
    /** Style of the line of a media segment while it plays. */
    private static final String PLAYING_STYLE = "-rtfx-background-color: #c8f0c8;";
    private final CorpusListener corpusListener = (id, before, after) -> onCorpusTextChanged(id);
    private final DocumentListener mirror = this::mirror;

    /** Top-level corpus objects of the current file, grammars in rank order. */
    private final ObservableList<CorpusObject> currentFileObjects = FXCollections.observableArrayList();
    private final ObservableList<Diagnostic> currentFileErrors = FXCollections.observableArrayList();
    /** Object selected in the editor, a table or a view (may be a nested object, of any file). */
    private final ObjectProperty<CorpusObject> selectedObject = new SimpleObjectProperty<>();
    /** Text and offset of the last change made by a view, to re-select the edited object. */
    private TextId lastEditText;
    private int lastEditOffset = -1;
    /** Object to select after the next parse of a text (set by a text action). */
    private TextId pendingSelectText;
    private int pendingSelectOffset = -1;

    /** Actions plugins offer on the displayed text, shown in the margin of their line. */
    private List<TextActionProvider> actionProviders = List.of();
    private ViewContext actionContext;
    private Consumer<String> status = message -> {};
    private final Set<TextActionProvider> failedProviders = Collections.newSetFromMap(new IdentityHashMap<>());

    public EditorController(
        InlineCssTextArea codeArea,
        UserSettings settings
    ) {
        this.textArea = codeArea;
        // the undo history is the document's
        this.textArea.setUndoManager(UndoUtils.noOpUndoManager());
        Nodes.addInputMap(textArea, InputMap.sequence(
            InputMap.consume(EventPattern.keyPressed(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), e -> undo()),
            InputMap.consume(EventPattern.keyPressed(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> redo()),
            InputMap.consume(EventPattern.keyPressed(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), e -> redo()),
            // play/pause the media segment at the caret
            InputMap.consume(EventPattern.keyPressed(KeyCode.SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                e -> playSegmentAtCaret())
        ));
        // typing → document
        this.textArea.multiPlainChanges()
            .filter(changes -> !ghostOperation && !mirroring && document != null)
            .subscribe(this::forward);

        this.highlightStyles = new HighlightStyles(settings);
        // show colour changes made in the settings immediately
        settings.igtColorProperty().addListener((obs, o, n) -> highlightText());
        settings.lexicalColorProperty().addListener((obs, o, n) -> highlightText());
        clearEditor();

        this.textArea.setOnMouseClicked(e -> handleCodeAreaClick());
    }

    public void setProject(Project project) {
        if (this.corpus != null) {
            this.corpus.removeListener(corpusListener);
        }
        clearEditor();
        this.project = project;
        this.corpus = project.getCorpus();
        this.corpus.addListener(corpusListener);
    }

    public HighlightStyles getHighlightStyles() {
        return highlightStyles;
    }

    // ------------------------------------------------------------------
    // Synchronisation with the document
    // ------------------------------------------------------------------

    /** Sends the user's changes to the document. */
    private void forward(List<PlainTextChange> changes) {
        List<TextEdit> edits = new ArrayList<>();
        for (PlainTextChange c : changes) {
            edits.add(toEdit(c));
        }
        if (!document.applySequential(edits, EditOrigin.of(EditOrigin.Kind.TYPING, this))) {
            logger.warning("The editor and the document of " + document.getFilePath() + " differ: resynchronised");
            resync();
        }
    }

    /** A change of the code area as an edit of the text before it. */
    static TextEdit toEdit(PlainTextChange c) {
        return new TextEdit(c.getPosition(), c.getPosition() + c.getRemoved().length(), c.getRemoved(), c.getInserted());
    }

    /** Copies into the code area a change of the document made by someone else. */
    private void mirror(DocumentChange change) {
        if (change.origin().source() == this || change.document() != document) {
            return;
        }
        // provisional text would shift the offsets
        dismissGhost();
        List<TextEdit> edits = change.edits();
        mirroring = true;
        try {
            MultiChangeBuilder<String, String, String> multi = textArea.createMultiChange(edits.size());
            // in reverse order, so that the offsets of the next edits stay valid
            for (int i = edits.size() - 1; i >= 0; i--) {
                TextEdit e = edits.get(i);
                multi.replaceTextAbsolutely(e.start(), e.end(), e.replacement());
            }
            multi.commit();
        } finally {
            mirroring = false;
        }
        if (textArea.getLength() != document.getCurrentText().length()) {
            logger.warning("The editor and the document of " + document.getFilePath() + " differ: resynchronised");
            resync();
        }
    }

    /** Replaces the text of the code area by the document's. */
    private void resync() {
        mirroring = true;
        try {
            int caret = textArea.getCaretPosition();
            textArea.replaceText(document.getCurrentText());
            textArea.moveTo(Math.min(caret, textArea.getLength()));
        } finally {
            mirroring = false;
        }
    }

    // ------------------------------------------------------------------
    // Corpus objects of the current file
    // ------------------------------------------------------------------

    private void onCorpusTextChanged(TextId id) {
        if (document != null && id.equals(document.getId())) {
            refreshFromCorpus();
        } else {
            // the selected object may belong to a text that is not displayed (edited by a view)
            CorpusObject previous = selectedObject.get();
            if (previous != null && previous.getTextCoordinate().text().equals(id)
                && corpus.textOf(previous).isEmpty()) {
                selectedObject.set(findSuccessor(previous).orElse(null));
            }
        }
    }

    private Optional<Text> displayedText() {
        return document == null || corpus == null ? Optional.empty() : corpus.text(document.getId());
    }

    /** Reloads objects and errors of the current file and keeps the selection if possible. */
    private void refreshFromCorpus() {
        CorpusObject previous = selectedObject.get();
        Optional<Text> text = displayedText();
        currentFileObjects.setAll(text.map(Text::objects).orElse(List.of()));
        currentFileErrors.setAll(text.map(Text::diagnostics).orElse(List.of()));
        highlightText();
        if (previous != null && corpus.textOf(previous).isEmpty()) {
            selectedObject.set(findSuccessor(previous).orElse(null));
        }
        if (document != null && document.getId().equals(pendingSelectText)) {
            int offset = pendingSelectOffset;
            pendingSelectText = null;
            text.flatMap(t -> t.objectAt(offset)).ifPresent(selectedObject::set);
        }
        refreshTextActions(text);
    }

    /** ▶ (or ⏸ while it plays) for a media segment; greyed, with the reason as tooltip, if it cannot be played. */
    private Button playButton(MediaSegment segment) {
        boolean playing = isPlaying(segment);
        Button button = new Button(playing ? "⏸" : "▶");
        button.setStyle("-fx-font-size: 0.8em; -fx-padding: 0 4 0 4;");
        button.setFocusTraversable(false);
        String problem = playProblem(segment);
        if (problem != null) {
            button.setOpacity(0.4);
            button.setTooltip(new Tooltip(problem));
        } else {
            button.setTooltip(new Tooltip((playing ? "Pause " : "Play ")
                + (segment.getCaption().isEmpty() ? "the segment" : segment.getCaption()) + " (⇧⌘Space)"));
        }
        button.setOnAction(e -> toggle(segment));
        return button;
    }

    /** After the next parse of {@code text}, selects the object at {@code offset}. */
    public void selectAfterParse(TextId text, int offset) {
        this.pendingSelectText = text;
        this.pendingSelectOffset = offset;
    }

    // ------------------------------------------------------------------
    // Text actions offered by plugins
    // ------------------------------------------------------------------

    /**
     * @param providers actions offered by plugins on the displayed text
     * @param context   runs the actions ({@link ViewContext#runTextAction})
     * @param status    receives the message of an action that could not be applied
     */
    public void setTextActions(List<TextActionProvider> providers, ViewContext context, Consumer<String> status) {
        this.actionProviders = List.copyOf(providers);
        this.actionContext = context;
        this.status = status;
        // play buttons and the highlight follow what is playing
        context.mediaPlayers().playingProperty().addListener((obs, o, n) -> {
            refreshTextActions(displayedText());
            highlightText();
        });
    }

    // ------------------------------------------------------------------
    // Media segments
    // ------------------------------------------------------------------

    /** Plays (or pauses, if it is playing) the media segment at the caret. */
    public void playSegmentAtCaret() {
        dismissGhost();
        Optional<MediaSegment> segment = displayedText().flatMap(t -> SegmentAtCaret.find(t, textArea.getCaretPosition()));
        if (segment.isEmpty()) {
            status.accept("No media segment at the caret");
            return;
        }
        toggle(segment.get());
    }

    /** Plays a segment, or pauses it if it is playing; problems go to the status line. */
    private void toggle(MediaSegment segment) {
        if (actionContext == null) return;
        Optional<Path> file = playableFile(segment, true);
        if (file.isEmpty()) return;
        MediaPlayers players = actionContext.mediaPlayers();
        if (players.isPlaying(file.get(), segment.getRange())) {
            players.pause();
        } else {
            players.play(file.get(), segment.getRange());
            players.error(file.get()).ifPresent(e -> status.accept("Cannot play " + file.get().getFileName() + ": " + e));
        }
    }

    /** The resolved file of a segment if it can be played; otherwise the reason (reported if asked). */
    private Optional<Path> playableFile(MediaSegment segment, boolean report) {
        String problem = playProblem(segment);
        if (problem != null) {
            if (report) status.accept(problem);
            return Optional.empty();
        }
        status.accept("");
        return MediaResolver.resolve(segment, actionContext.corpus(), actionContext.projectDirectory()).file().path();
    }

    /** Why a segment cannot be played, or null. */
    private String playProblem(MediaSegment segment) {
        MediaResolver.Resolution r = MediaResolver.resolve(segment, actionContext.corpus(), actionContext.projectDirectory());
        if (r.source() == MediaResolver.Source.NONE) {
            return "No media file: write one after @media, or a \"media:\" entry in the section or the header";
        }
        if (r.isUrl()) return "Remote media are not played";
        if (!r.file().exists()) return "File not found: " + r.file().path().map(Path::toString).orElse("");
        if (MediaPaths.kindOf(r.file().path().get()) == MediaPaths.Kind.UNSUPPORTED) {
            return "Format not supported by the player";
        }
        return null;
    }

    private boolean isPlaying(MediaSegment segment) {
        if (actionContext == null || actionContext.mediaPlayers().playingProperty().get().isEmpty()) return false;
        return MediaResolver.resolve(segment, actionContext.corpus(), actionContext.projectDirectory()).file().path()
            .map(p -> actionContext.mediaPlayers().isPlaying(p, segment.getRange()))
            .orElse(false);
    }

    /** Shows the actions of the parsed text as buttons in the margin of their line (never in the text). */
    private void refreshTextActions(Optional<Text> text) {
        Map<Integer, List<TextAction>> byParagraph = new TreeMap<>();
        Map<Integer, List<MediaSegment>> segmentsByParagraph = new TreeMap<>();
        if (text.isPresent() && actionContext != null) {
            for (MediaSegment segment : text.get().objectsOf(MediaSegment.class)) {
                int offset = Math.max(0, Math.min(segment.getTextCoordinate().startIndex(), textArea.getLength()));
                int paragraph = textArea.offsetToPosition(offset, TwoDimensional.Bias.Forward).getMajor();
                segmentsByParagraph.computeIfAbsent(paragraph, k -> new ArrayList<>()).add(segment);
            }
            for (TextActionProvider provider : actionProviders) {
                List<TextAction> actions;
                try {
                    actions = provider.actionsFor(text.get(), actionContext);
                } catch (RuntimeException | LinkageError e) {
                    if (failedProviders.add(provider)) {
                        logger.log(Level.WARNING, "Text action provider " + provider.getClass().getName() + " failed", e);
                    }
                    continue;
                }
                for (TextAction action : actions == null ? List.<TextAction>of() : actions) {
                    int offset = Math.max(0, Math.min(action.offset(), textArea.getLength()));
                    int paragraph = textArea.offsetToPosition(offset, TwoDimensional.Bias.Forward).getMajor();
                    byParagraph.computeIfAbsent(paragraph, k -> new ArrayList<>()).add(action);
                }
            }
        }
        if (byParagraph.isEmpty() && segmentsByParagraph.isEmpty()) {
            textArea.setParagraphGraphicFactory(null);
            return;
        }
        textArea.setParagraphGraphicFactory(paragraph -> {
            List<TextAction> actions = byParagraph.getOrDefault(paragraph, List.of());
            List<MediaSegment> segments = segmentsByParagraph.getOrDefault(paragraph, List.of());
            if (actions.isEmpty() && segments.isEmpty()) return null;
            HBox box = new HBox(2);
            for (MediaSegment segment : segments) {
                box.getChildren().add(playButton(segment));
            }
            for (TextAction action : actions) {
                Button button = new Button(action.label());
                button.setStyle("-fx-font-size: 0.8em; -fx-padding: 0 4 0 4;");
                if (!action.description().isBlank()) button.setTooltip(new Tooltip(action.description()));
                button.setFocusTraversable(false);
                button.setOnAction(e -> {
                    EditOutcome outcome = actionContext.runTextAction(action);
                    status.accept(outcome.isApplied() ? "" : outcome.message());
                });
                box.getChildren().add(button);
            }
            return box;
        });
    }

    /** Records where a view changed a text, so that the edited object is selected again. */
    public void rememberEdit(TextId text, int offset) {
        this.lastEditText = text;
        this.lastEditOffset = offset;
    }

    /**
     * After a re-parse all objects are new instances: find the object that replaces
     * {@code previous}. After a view edit, the object of the same kind containing the edit;
     * otherwise preferably an object of the same kind and label (the closest one to the previous
     * position, which may have moved if text was inserted before it), otherwise the object of the
     * same kind containing the previous start offset.
     */
    private Optional<CorpusObject> findSuccessor(CorpusObject previous) {
        TextId id = previous.getTextCoordinate().text();
        Optional<Text> text = corpus.text(id);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        int start = previous.getTextCoordinate().startIndex();
        List<CorpusObject> sameKind = text.get().flatten()
            .filter(o -> o.getKind().equals(previous.getKind()))
            .toList();
        Optional<CorpusObject> edited = Optional.empty();
        if (id.equals(lastEditText) && lastEditOffset >= 0) {
            edited = sameKind.stream()
                .filter(o -> o.getTextCoordinate().contains(lastEditOffset)
                    || o.getTextCoordinate().stopIndex() + 1 == lastEditOffset)
                .reduce((a, b) -> b);
        }
        return edited
            .or(() -> sameKind.stream()
                .filter(o -> Objects.equals(o.getLabel(), previous.getLabel()))
                .min(Comparator.comparingInt(o -> Math.abs(o.getTextCoordinate().startIndex() - start))))
            .or(() -> sameKind.stream().filter(o -> o.getTextCoordinate().contains(start)).findFirst());
    }

    private void handleCodeAreaClick() {
        dismissGhost();
        int caretPosition = textArea.getCaretPosition();
        displayedText().flatMap(t -> t.objectAt(caretPosition)).ifPresent(selectedObject::set);
    }

    /** Highlights every corpus object of the file: grammars in rank order, parents before children. */
    public void highlightText() {
        if (textArea == null || document == null || corpus == null) {
            return;
        }
        // spans refer to the real text: remove provisional text first
        dismissGhost();
        int length = textArea.getLength();
        if (length > 0) {
            textArea.clearStyle(0, length);
        }
        displayedText().ifPresent(text -> text.flatten().forEach(o -> {
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
        }));
        // the media segments being played
        displayedText().ifPresent(text -> text.objectsOf(MediaSegment.class).stream()
            .filter(this::isPlaying)
            .forEach(s -> {
                TextCoordinate span = s.getTextCoordinate();
                int end = Math.min(length, span.stopIndex() + 1);
                if (span.startIndex() < end) {
                    textArea.setStyle(span.startIndex(), end, PLAYING_STYLE);
                }
            }));
    }

    // ------------------------------------------------------------------
    // Selection in the editor
    // ------------------------------------------------------------------

    /** Selects a span of the current file. */
    public void selectInEditor(TextCoordinate span) {
        if (span == null) return;
        if (document == null || !span.text().equals(document.getId())) {
            throw new IllegalArgumentException(
                "The span does not belong to the current file: " + span
            );
        }
        selectRangeInEditor(span.startIndex(), span.stopIndex());
    }

    public void selectLinesInEditor(Diagnostic error) {
        if (error.startIndex() >= 0) {
            selectRangeInEditor(error.startIndex(), Math.max(error.startIndex(), error.stopIndex()));
            return;
        }
        String text = textArea.getText();
        // ANTLR lines are 1-based; line 0 is used for errors without position
        int line = Math.max(1, error.line());
        int lineStart = 0;
        for (int i = 1; i < line && lineStart >= 0; i++) {
            int newline = text.indexOf('\n', lineStart);
            lineStart = newline < 0 ? -1 : newline + 1;
        }
        if (lineStart < 0) return;
        int start = Math.min(text.length(), lineStart + Math.max(0, error.column()));
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
        TextDocument selected = project.getParsedText(filePath);
        this.document = selected;
        resync();
        textArea.moveTo(0);
        textArea.setEditable(true);
        selected.addChangeListener(mirror);
        refreshFromCorpus();

        currentTextCouldBeWrittenDownProperty.bind(selected.getCouldBeWrittenBooleanProperty());
        currentTextCouldBeReparsedProperty.bind(selected.getCouldBeReparsedBooleanProperty());
        project.history(selected.getId()).ifPresent(h -> {
            undoAvailable.bind(h.undoAvailableProperty());
            redoAvailable.bind(h.redoAvailableProperty());
        });
        closableProperty.set(true);
    }

    public Path getSelectedFilePath() {
        return document == null ? null : document.getFilePath();
    }

    /** The displayed document, if any. */
    public Optional<TextDocument> getSelectedDocument() {
        return Optional.ofNullable(document);
    }

    public void clearEditor() {
        dismissGhost();
        if (document != null) {
            document.removeChangeListener(mirror);
        }
        this.document = null;
        textArea.setParagraphGraphicFactory(null);
        currentTextCouldBeWrittenDownProperty.unbind();
        currentTextCouldBeWrittenDownProperty.set(false);
        currentTextCouldBeReparsedProperty.unbind();
        currentTextCouldBeReparsedProperty.set(false);
        undoAvailable.unbind();
        undoAvailable.set(false);
        redoAvailable.unbind();
        redoAvailable.set(false);
        closableProperty.set(false);
        mirroring = true;
        try {
            textArea.clear();
        } finally {
            mirroring = false;
        }
        textArea.setEditable(false);
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

    public BooleanExpression undoAvailableProperty() {
        return undoAvailable;
    }

    public BooleanExpression redoAvailableProperty() {
        return redoAvailable;
    }

    /** Undoes the last change of the displayed document. */
    public void undo() {
        dismissGhost();
        if (document != null) {
            project.history(document.getId()).ifPresent(TextHistory::undo);
        }
    }

    /** Applies again the last undone change of the displayed document. */
    public void redo() {
        dismissGhost();
        if (document != null) {
            project.history(document.getId()).ifPresent(TextHistory::redo);
        }
    }

    public void saveEditedFile() throws IOException {
        if (document == null) {
            throw new IOException("No file is currently being edited.");
        }
        dismissGhost();
        if (document.getCouldBeWritten()) {
            document.writeFile();
        }
    }

    /**
     * Parses the current document in the background; the views are refreshed when the results
     * are applied.
     */
    public CompletableFuture<Void> parseEditedFile() {
        if (document == null) {
            return CompletableFuture.completedFuture(null);
        }
        dismissGhost();
        return project.parseInBackground(document.getFilePath());
    }

    public ObservableList<CorpusObject> getCurrentFileObjects() {
        return this.currentFileObjects;
    }

    public ObservableList<Diagnostic> getCurrentFileErrors() {
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

    /** Runs a change of provisional text: it reaches neither the document, nor the undo history. */
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

    /** Changes of the real text shown (provisional text excluded), whoever made them. */
    public EventStream<PlainTextChange> userChanges() {
        return textArea.plainTextChanges().filter(change -> !ghostOperation);
    }

    /** The text of the displayed document (without provisional text). */
    public String currentText() {
        dismissGhost();
        return document == null ? textArea.getText() : document.getCurrentText();
    }

    public boolean isEditing() {
        return document != null;
    }

    /**
     * Applies edits of an AI suggestion to the displayed document, as one undoable change, if the
     * text still holds what they expect.
     *
     * @return false if the text changed and the edits no longer apply
     */
    public boolean applyEdits(List<TextEdit> edits) {
        if (document == null || edits.isEmpty()) return false;
        dismissGhost();
        if (!document.applyEdits(edits, EditOrigin.of(EditOrigin.Kind.ASSIST, "assistant"))) {
            return false;
        }
        TextEdit first = edits.stream().min(Comparator.comparingInt(TextEdit::start)).orElseThrow();
        int shift = edits.stream().filter(e -> e.start() < first.start()).mapToInt(TextEdit::delta).sum();
        textArea.moveTo(Math.min(textArea.getLength(), first.start() + shift + first.replacement().length()));
        textArea.requestFollowCaret();
        return true;
    }

    /** Applies search-and-replace edits to the displayed document, as one undoable change. */
    public boolean applySearchEdits(List<TextEdit> edits) {
        if (document == null || edits.isEmpty()) return false;
        dismissGhost();
        return document.applyEdits(edits, EditOrigin.of(EditOrigin.Kind.SEARCH, "search"));
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
    public Optional<Diagnostic> errorAt(int offset) {
        String text = textArea.getText();
        int line = lineOf(text, offset);
        return currentFileErrors.stream()
            .filter(e -> e.span().map(sp -> sp.contains(offset)).orElse(false) || e.line() == line)
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
