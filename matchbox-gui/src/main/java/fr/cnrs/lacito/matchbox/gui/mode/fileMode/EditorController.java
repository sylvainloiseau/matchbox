package fr.cnrs.lacito.matchbox.gui.mode.fileMode;

import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.MatchRole;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.gui.view.DisplayStyleCss;
import fr.cnrs.lacito.matchbox.settings.EditorSettingsData;
import fr.cnrs.lacito.matchbox.gui.view.HighlightStyles;
import fr.cnrs.lacito.matchbox.gui.view.StyleLayers;
import fr.cnrs.lacito.matchbox.gui.workbench.SelectionTracker;
import fr.cnrs.lacito.matchbox.gui.workbench.Workbench;
import fr.cnrs.lacito.matchbox.model.DocumentChange;
import fr.cnrs.lacito.matchbox.model.DocumentListener;
import fr.cnrs.lacito.matchbox.model.EditOrigin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import java.util.Set;
import java.util.TreeMap;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.NavigationActions.SelectionPolicy;
import org.fxmisc.richtext.MultiChangeBuilder;
import org.fxmisc.richtext.model.StyleSpansBuilder;
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
 * in the code area. Undo and redo use the project's journal
 * ({@link fr.cnrs.lacito.matchbox.model.EditJournal}), which records the changes of every document.
 * The selected object is kept across re-parses by a {@link SelectionTracker}.
 */
public class EditorController {

    private static final Logger logger = Logger.getLogger(EditorController.class.getName());

    private final InlineCssTextArea textArea;
    private TextDocument document;
    private CorpusView corpus;
    private Project project;
    private final Workbench workbench;

    private final HighlightStyles highlightStyles;
    private final SimpleBooleanProperty currentTextCouldBeWrittenDownProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty currentTextCouldBeReparsedProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty closableProperty = new SimpleBooleanProperty(false);

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
    private static final DisplayStyle PLAYING_STYLE = DisplayStyle.background("#c8f0c8");
    private final DocumentListener mirror = this::mirror;

    /** Top-level corpus objects of the current file, grammars in rank order. */
    private final ObservableList<CorpusObject> currentFileObjects = FXCollections.observableArrayList();
    private final ObservableList<Diagnostic> currentFileErrors = FXCollections.observableArrayList();
    /** Object selected in the editor, a table or a view (may be a nested object, of any file). */
    private final SelectionTracker selection;

    /** Options of the editor (wrap, line numbers, font…), from the user settings. */
    private EditorSettingsData options = EditorSettingsData.defaults();
    /** Where the user was in each file of the project. */
    private final CaretMemory caretMemory = new CaretMemory();
    /** Markers of the margin by line (▶, ✚, ●), also shown by the error ruler. */
    private final ObjectProperty<Map<Integer, GutterLayout.Markers>> markers = new SimpleObjectProperty<>(Map.of());
    /** Digits of the line numbers of the margin as installed (the margin is rebuilt when they change). */
    private int gutterDigits;
    /** Matches of the find bar, highlighted; the current one stronger (-1: none). */
    private List<FindModel.Match> findMatches = List.of();
    private int findCurrent = -1;
    /** Paragraph with the current-line background, -1 if none. */
    private int highlightedParagraph = -1;
    private static final String CURRENT_LINE_STYLE = "-fx-background-color: #f2f6fc;";
    private static final DisplayStyle INVISIBLE_STYLE = DisplayStyle.background("#e4e4ec");
    private static final DisplayStyle MATCH_STYLE = DisplayStyle.background("#fff59d");
    private static final DisplayStyle CURRENT_MATCH_STYLE = DisplayStyle.background("#ffb74d");
    /** Outline of a reading that was not kept (hover of ◆, Show both of the Overlaps tab). */
    private static final DisplayStyle DROPPED_STYLE = DisplayStyle.background("#ffe0a3").withUnderline(true);
    /** The range outlined with {@link #DROPPED_STYLE} ({@code [start, end)}), null if none. */
    private int[] outlined;
    /** Lines of the section containing the caret, shown as a band in the margin. */
    private final ObjectProperty<Optional<SectionBand.Lines>> sectionBand = new SimpleObjectProperty<>(Optional.empty());

    /** Actions plugins offer on the displayed text, shown in the margin of their line. */
    private List<TextActionProvider> actionProviders = List.of();
    private final Set<TextActionProvider> failedProviders = Collections.newSetFromMap(new IdentityHashMap<>());

    public EditorController(InlineCssTextArea codeArea, Workbench workbench) {
        this.textArea = codeArea;
        this.workbench = workbench;
        this.selection = new SelectionTracker(workbench.editHints());
        // the undo history is the project's journal
        this.textArea.setUndoManager(UndoUtils.noOpUndoManager());
        Nodes.addInputMap(textArea, InputMap.sequence(
            InputMap.consume(EventPattern.keyPressed(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), e -> undo()),
            InputMap.consume(EventPattern.keyPressed(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> redo()),
            InputMap.consume(EventPattern.keyPressed(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), e -> redo()),
            // play/pause the media segment at the caret
            InputMap.consume(EventPattern.keyPressed(KeyCode.SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                e -> playSegmentAtCaret())
        ));
        // caret movements and deletions of the system's conventions; Enter keeps the indentation
        List<InputMap<KeyEvent>> keys = new ArrayList<>();
        for (EditorKeymap.Binding b : EditorKeymap.forPlatform(EditorKeymap.Platform.current())) {
            keys.add(InputMap.consume(EventPattern.keyPressed(b.key()), e -> perform(b.action(), b.select())));
        }
        keys.add(InputMap.consumeWhen(EventPattern.keyPressed(KeyCode.ENTER),
            () -> options.autoIndent() && document != null, e -> newline()));
        Nodes.addInputMap(textArea, InputMap.sequence(keys.toArray(InputMap[]::new)));
        // the options follow the settings
        var editorSettings = workbench.settings().editorProperty();
        editorSettings.addListener((obs, o, n) -> applyOptions(n));
        textArea.currentParagraphProperty().addListener((obs, o, n) -> updateCurrentLine());
        textArea.caretPositionProperty().addListener((obs, o, n) -> updateSectionBand());
        textArea.multiPlainChanges().subscribe(c -> {
            // a new digit in the line count widens the margin
            if (document != null && GutterLayout.digits(textArea.getParagraphs().size()) != gutterDigits) {
                refreshTextActions(displayedText());
            }
        });
        // typing → document
        this.textArea.multiPlainChanges()
            .filter(changes -> !ghostOperation && !mirroring && document != null)
            .subscribe(this::forward);

        this.highlightStyles = workbench.highlightStyles();
        // show the display styles edited in the project properties immediately
        highlightStyles.addListener(this::highlightText);
        clearEditor();
        workbench.session().addCorpusListener((id, before, after) -> onCorpusTextChanged(id));
        // play buttons and the highlight follow what is playing
        workbench.mediaPlayers().playingProperty().addListener((obs, o, n) -> {
            refreshTextActions(displayedText());
            highlightText();
        });

        this.textArea.setOnMouseClicked(e -> handleCodeAreaClick());
        applyOptions(editorSettings.get());
    }

    // ------------------------------------------------------------------
    // Options
    // ------------------------------------------------------------------

    /** Wrapping, font, margin, current line, invisible characters. */
    private void applyOptions(EditorSettingsData newOptions) {
        this.options = newOptions == null ? EditorSettingsData.defaults() : newOptions;
        textArea.setWrapText(options.wrapLines());
        String family = options.fontFamily() == null ? "monospace"
            : "\"" + options.fontFamily().replaceAll("[\"';{}]", "") + "\"";
        textArea.setStyle("-fx-font-family: " + family + "; -fx-font-size: " + options.fontSize() + "px;");
        refreshTextActions(displayedText());
        highlightText();
        highlightedParagraph = clearCurrentLine();
        updateCurrentLine();
    }

    public EditorSettingsData options() {
        return options;
    }

    /** The font of the text (for the margin's numbers). */
    private Font editorFont() {
        return Font.font(options.fontFamily() == null ? "Monospaced" : options.fontFamily(), options.fontSize());
    }

    /** The caret's line gets a light background (when the option is on). */
    private void updateCurrentLine() {
        int paragraph = document == null || !options.highlightCurrentLine() ? -1 : textArea.getCurrentParagraph();
        if (paragraph == highlightedParagraph) return;
        clearCurrentLine();
        if (paragraph >= 0 && paragraph < textArea.getParagraphs().size()) {
            textArea.setParagraphStyle(paragraph, CURRENT_LINE_STYLE);
        }
        highlightedParagraph = paragraph;
    }

    /** The band of the margin follows the section containing the caret. */
    private void updateSectionBand() {
        Optional<SectionBand.Lines> lines = displayedText().flatMap(t -> SectionBand.linesOf(t, textArea.getCaretPosition()));
        if (!lines.equals(sectionBand.get())) {
            sectionBand.set(lines);
        }
    }

    /** Lines of the section containing the caret (the band of the margin), for tests. */
    public Optional<SectionBand.Lines> sectionBand() {
        return sectionBand.get();
    }

    private int clearCurrentLine() {
        if (highlightedParagraph >= 0 && highlightedParagraph < textArea.getParagraphs().size()) {
            textArea.setParagraphStyle(highlightedParagraph, "");
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Keyboard: movements, deletions, Enter, line commands
    // ------------------------------------------------------------------

    /** A movement or deletion of the keymap; {@code select}: the movement extends the selection. */
    void perform(EditorAction action, boolean select) {
        dismissGhost();
        SelectionPolicy policy = select ? SelectionPolicy.ADJUST : SelectionPolicy.CLEAR;
        int paragraph = textArea.getCurrentParagraph();
        switch (action) {
            case LINE_START -> textArea.moveTo(paragraph,
                Indentation.smartHome(textArea.getParagraph(paragraph).getText(), textArea.getCaretColumn()), policy);
            case LINE_END -> textArea.moveTo(paragraph, textArea.getParagraphLength(paragraph), policy);
            case FILE_START -> textArea.start(policy);
            case FILE_END -> textArea.end(policy);
            case WORD_LEFT -> textArea.wordBreaksBackwards(2, policy);
            case WORD_RIGHT -> textArea.wordBreaksForwards(2, policy);
            case DELETE_TO_LINE_START, DELETE_WORD_LEFT, DELETE_WORD_RIGHT -> {
                if (!textArea.isEditable()) return;
                if (textArea.getSelection().getLength() == 0) {
                    switch (action) {
                        case DELETE_TO_LINE_START -> textArea.moveTo(paragraph, 0, SelectionPolicy.ADJUST);
                        case DELETE_WORD_LEFT -> textArea.wordBreaksBackwards(2, SelectionPolicy.ADJUST);
                        default -> textArea.wordBreaksForwards(2, SelectionPolicy.ADJUST);
                    }
                }
                textArea.replaceSelection("");
            }
        }
        textArea.requestFollowCaret();
    }

    /** Enter: a newline and the indentation of the line. */
    private void newline() {
        dismissGhost();
        int paragraph = textArea.getCurrentParagraph();
        int column = textArea.getSelection().getLength() == 0 ? textArea.getCaretColumn()
            : textArea.offsetToPosition(textArea.getSelection().getStart(), TwoDimensional.Bias.Forward).getMinor();
        textArea.replaceSelection(Indentation.newline(textArea.getParagraph(paragraph).getText(), column));
        textArea.requestFollowCaret();
    }

    public void duplicateLines() {
        lineCommand("Duplicate line", t -> Optional.of(LineCommands.duplicate(t, selectionStart(), selectionEnd())));
    }

    public void deleteLines() {
        lineCommand("Delete line", t -> Optional.of(LineCommands.delete(t, selectionStart(), selectionEnd())));
    }

    public void moveLinesUp() {
        lineCommand("Move line up", t -> LineCommands.moveUp(t, selectionStart(), selectionEnd()));
    }

    public void moveLinesDown() {
        lineCommand("Move line down", t -> LineCommands.moveDown(t, selectionStart(), selectionEnd()));
    }

    private int selectionStart() {
        return textArea.getSelection().getStart();
    }

    private int selectionEnd() {
        return textArea.getSelection().getEnd();
    }

    /** A command on lines: one change of the document, one undo entry, then the new selection. */
    private void lineCommand(String description, java.util.function.Function<String, Optional<LineCommands.Result>> command) {
        if (document == null) return;
        dismissGhost();
        command.apply(document.getCurrentText()).ifPresent(r -> {
            if (document.applyEdits(List.of(r.edit()), EditOrigin.of(EditOrigin.Kind.COMMAND, "editor", description))) {
                int length = textArea.getLength();
                textArea.selectRange(Math.min(r.selectionStart(), length), Math.min(r.selectionEnd(), length));
                textArea.requestFollowCaret();
            }
        });
    }

    /** Inserts text at the caret, replacing the selection (as typing). */
    public void insertText(String text) {
        if (document == null || !textArea.isEditable()) return;
        dismissGhost();
        textArea.replaceSelection(text);
        textArea.requestFollowCaret();
        textArea.requestFocus();
    }

    /** Offset of the start of a line (1-based, clamped). */
    public int offsetOfLine(int line) {
        int paragraph = Math.max(0, Math.min(textArea.getParagraphs().size() - 1, line - 1));
        return textArea.getAbsolutePosition(paragraph, 0);
    }

    public int lineCount() {
        return textArea.getParagraphs().size();
    }

    /** Markers of the margin by line (▶, ✚, ●). */
    public ReadOnlyObjectProperty<Map<Integer, GutterLayout.Markers>> markersProperty() {
        return markers;
    }

    /** Matches of the find bar to highlight; {@code current}: index of the current one, -1 if none. */
    public void setFindHighlights(List<FindModel.Match> matches, int current) {
        this.findMatches = List.copyOf(matches);
        this.findCurrent = current;
        highlightText();
    }

    public void setProject(Project project) {
        clearEditor();
        caretMemory.clear();
        this.project = project;
        this.corpus = project == null ? null : project.getCorpus();
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
            if (corpus != null) {
                selection.textChanged(id, corpus, false);
            }
        }
    }

    private Optional<Text> displayedText() {
        return document == null || corpus == null ? Optional.empty() : corpus.text(document.getId());
    }

    /** Reloads objects and errors of the current file and keeps the selection if possible. */
    private void refreshFromCorpus() {
        Optional<Text> text = displayedText();
        currentFileObjects.setAll(text.map(Text::objects).orElse(List.of()));
        currentFileErrors.setAll(text.map(Text::diagnostics).orElse(List.of()));
        outlined = null;
        highlightText();
        updateSectionBand();
        if (document != null) {
            selection.textChanged(document.getId(), corpus, true);
        }
        refreshTextActions(text);
    }

    /** ▶ (or ⏸ while it plays) for a media segment; greyed, with the reason as tooltip, if it cannot be played. */
    private Button playButton(MediaSegment segment) {
        boolean playing = workbench.playback().isPlaying(segment);
        Button button = new Button(playing ? "⏸" : "▶");
        button.setStyle("-fx-font-size: 0.8em; -fx-padding: 0 4 0 4;");
        button.setFocusTraversable(false);
        String problem = workbench.playback().problem(segment);
        if (problem != null) {
            button.setOpacity(0.4);
            button.setTooltip(new Tooltip(problem));
        } else {
            button.setTooltip(new Tooltip((playing ? "Pause " : "Play ")
                + (segment.getCaption().isEmpty() ? "the segment" : segment.getCaption()) + " (⇧⌘Space)"));
        }
        button.setOnAction(e -> workbench.playback().toggle(segment));
        return button;
    }

    // ------------------------------------------------------------------
    // Text actions offered by plugins
    // ------------------------------------------------------------------

    /**
     * @param providers actions offered by plugins on the displayed text, run through the window's
     *                  {@code ViewContext}; messages go to the status line
     */
    public void setTextActions(List<TextActionProvider> providers) {
        this.actionProviders = List.copyOf(providers);
        refreshTextActions(displayedText());
    }

    // ------------------------------------------------------------------
    // Media segments
    // ------------------------------------------------------------------

    /** Plays (or pauses, if it is playing) the media segment at the caret. */
    public void playSegmentAtCaret() {
        dismissGhost();
        Optional<MediaSegment> segment = displayedText().flatMap(t -> SegmentAtCaret.find(t, textArea.getCaretPosition()));
        if (segment.isEmpty()) {
            workbench.status().info("No media segment at the caret");
            return;
        }
        workbench.playback().toggle(segment.get());
    }

    /**
     * Builds the margin: line numbers and the markers of each line — media segments (▶), actions
     * of plugins (✚, never in the text), parse errors (●) — all with the same width.
     */
    private void refreshTextActions(Optional<Text> text) {
        List<MediaSegment> segments = new ArrayList<>();
        List<TextAction> actions = new ArrayList<>();
        var actionContext = workbench.viewContext();
        if (text.isPresent()) {
            segments.addAll(text.get().objectsOf(MediaSegment.class));
            for (TextActionProvider provider : actionProviders) {
                try {
                    List<TextAction> offered = provider.actionsFor(text.get(), actionContext);
                    if (offered != null) actions.addAll(offered);
                } catch (RuntimeException | LinkageError e) {
                    if (failedProviders.add(provider)) {
                        logger.log(Level.WARNING, "Text action provider " + provider.getClass().getName() + " failed", e);
                    }
                }
            }
        }
        LineIndex index = new LineIndex(textArea.getText());
        Map<Integer, GutterLayout.Markers> byLine = GutterLayout.markers(index, segments, actions,
            text.map(Text::diagnostics).orElse(List.of()));
        markers.set(byLine);
        gutterDigits = GutterLayout.digits(textArea.getParagraphs().size());
        boolean sections = text.map(t -> !t.sections().isEmpty()).orElse(false);
        if (document == null || (!options.lineNumbers() && byLine.isEmpty() && !sections)) {
            textArea.setParagraphGraphicFactory(null);
            return;
        }
        textArea.setParagraphGraphicFactory(EditorGutter.factory(options.lineNumbers(), gutterDigits, editorFont(), byLine,
            this::playButton, action -> {
                EditOutcome outcome = actionContext.runTextAction(text.get().id(), action);
                if (outcome.isApplied()) {
                    workbench.status().clear();
                } else {
                    workbench.status().warn(outcome.message());
                }
            }, sectionBand, warning -> outline(warning == null ? null
                : new int[] {warning.startIndex(), warning.stopIndex() + 1})));
    }

    /**
     * Outlines a range of the displayed text ({@code [start, end)}), e.g. a reading that was not
     * kept; null removes the outline. It also goes away at the next parse.
     */
    public void outline(int[] range) {
        if (range != null && (range[0] < 0 || range[1] <= range[0])) range = null;
        if (java.util.Arrays.equals(range, outlined)) return;
        outlined = range;
        highlightText();
    }

    /** The outlined range ({@code [start, end)}), if any (for tests). */
    public Optional<int[]> outlinedRange() {
        return Optional.ofNullable(outlined);
    }

    private void handleCodeAreaClick() {
        dismissGhost();
        int caretPosition = textArea.getCaretPosition();
        displayedText().flatMap(t -> t.objectAt(caretPosition)).ifPresent(selection::set);
    }

    /**
     * Highlights every corpus object of the file in drawing order ({@link Text#layered()}): scopes
     * (on their first line only), then exclusive objects, then annotations such as tags; within a
     * role, grammars in rank order and parents before children.
     */
    public void highlightText() {
        if (textArea == null || document == null || corpus == null) {
            return;
        }
        // spans refer to the real text: remove provisional text first
        dismissGhost();
        int length = textArea.getLength();
        // scopes underneath, then exclusive objects, annotations (tags) on top; each layer
        // overrides only the properties it sets
        StyleLayers layers = new StyleLayers();
        String content = textArea.getText();
        displayedText().ifPresent(text -> text.layered().forEach(o -> {
            TextCoordinate span = o.getTextCoordinate();
            // spans refer to the parsed text; the editor text may have changed since
            if (span.stopIndex() < length) {
                int end = span.stopIndex() + 1;
                if (o.getMatchRole() == MatchRole.SCOPE) {
                    // a section is painted on its heading line only: its extent is the margin's band
                    int newline = content.indexOf('\n', span.startIndex());
                    if (newline >= 0 && newline < end) end = newline;
                }
                layers.add(span.startIndex(), end, highlightStyles.styleFor(text, o));
            }
        }));
        // invisible characters, then the matches of the find bar
        if (options.showInvisibles()) {
            for (Invisibles.Span span : Invisibles.spans(textArea.getText())) {
                layers.add(span.start(), span.end(), INVISIBLE_STYLE);
            }
        }
        for (int i = 0; i < findMatches.size(); i++) {
            FindModel.Match m = findMatches.get(i);
            if (m.end() <= length) layers.add(m.start(), m.end(), i == findCurrent ? CURRENT_MATCH_STYLE : MATCH_STYLE);
        }
        // the media segments being played
        displayedText().ifPresent(text -> text.objectsOf(MediaSegment.class).stream()
            .filter(workbench.playback()::isPlaying)
            .forEach(s -> layers.add(s.getTextCoordinate().startIndex(),
                Math.min(length, s.getTextCoordinate().stopIndex() + 1), PLAYING_STYLE)));
        // the reading not kept, while its warning is hovered
        if (outlined != null && outlined[1] <= length) {
            layers.add(outlined[0], outlined[1], DROPPED_STYLE);
        }
        if (length > 0) {
            StyleSpansBuilder<String> spans = new StyleSpansBuilder<>();
            for (StyleLayers.Run run : layers.runs(length)) {
                spans.add(DisplayStyleCss.editor(run.style()), run.length());
            }
            textArea.setStyleSpans(0, spans.create());
        }
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
        textArea.setEditable(true);
        selected.addChangeListener(mirror);
        refreshFromCorpus();
        // back where the user was in this file
        Optional<CaretMemory.Place> place = caretMemory.recall(selected.getId(), textArea.getLength());
        textArea.selectRange(place.map(CaretMemory.Place::anchor).orElse(0), place.map(CaretMemory.Place::caret).orElse(0));
        int firstLine = place.map(CaretMemory.Place::firstVisibleLine).orElse(0);
        Platform.runLater(() -> {
            if (document == selected && firstLine < textArea.getParagraphs().size()) textArea.showParagraphAtTop(firstLine);
        });
        updateCurrentLine();

        currentTextCouldBeWrittenDownProperty.bind(selected.getCouldBeWrittenBooleanProperty());
        currentTextCouldBeReparsedProperty.bind(selected.getCouldBeReparsedBooleanProperty());
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
            rememberPlace();
        }
        highlightedParagraph = clearCurrentLine();
        findMatches = List.of();
        findCurrent = -1;
        this.document = null;
        textArea.setParagraphGraphicFactory(null);
        currentTextCouldBeWrittenDownProperty.unbind();
        currentTextCouldBeWrittenDownProperty.set(false);
        currentTextCouldBeReparsedProperty.unbind();
        currentTextCouldBeReparsedProperty.set(false);
        closableProperty.set(false);
        mirroring = true;
        try {
            textArea.clear();
        } finally {
            mirroring = false;
        }
        textArea.setEditable(false);
        this.selection.set(null);
        this.currentFileObjects.clear();
        this.currentFileErrors.clear();
        markers.set(Map.of());
    }

    /** Remembers the caret, selection and scroll position of the displayed file. */
    private void rememberPlace() {
        int firstVisible = 0;
        try {
            firstVisible = textArea.firstVisibleParToAllParIndex();
        } catch (RuntimeException e) {
            // not laid out yet: no visible paragraph
        }
        caretMemory.remember(document.getId(),
            new CaretMemory.Place(textArea.getCaretPosition(), textArea.getAnchor(), firstVisible));
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

    /** Undoes the last change of the project (the journal is project-wide). */
    public void undo() {
        dismissGhost();
        workbench.undo();
    }

    /** Applies again the last undone change of the project. */
    public void redo() {
        dismissGhost();
        workbench.redo();
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
        return selection.selectedProperty();
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
        if (!document.applyEdits(edits, EditOrigin.of(EditOrigin.Kind.ASSIST, "assistant", "Apply suggestion"))) {
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
        return document.applyEdits(edits, EditOrigin.of(EditOrigin.Kind.SEARCH, "search", "Replace"));
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

    /** The parse error at an offset (its span contains it, or it is on the same line); warnings ignored. */
    public Optional<Diagnostic> errorAt(int offset) {
        String text = textArea.getText();
        int line = lineOf(text, offset);
        return currentFileErrors.stream()
            .filter(Diagnostic::isError)
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

    /** Selects a range given by offsets ({@code end} exclusive); an empty range only places the caret. */
    public void selectRange(int start, int end) {
        if (end <= start) {
            Platform.runLater(() -> {
                dismissGhost();
                textArea.moveTo(Math.min(start, textArea.getLength()));
                textArea.requestFollowCaret();
                textArea.requestFocus();
            });
            return;
        }
        selectRangeInEditor(start, end - 1);
    }
}
