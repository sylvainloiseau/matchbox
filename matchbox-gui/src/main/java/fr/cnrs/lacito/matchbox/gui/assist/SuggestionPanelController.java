package fr.cnrs.lacito.matchbox.gui.assist;

import fr.cnrs.lacito.matchbox.assist.AssistContext;
import fr.cnrs.lacito.matchbox.assist.AssistService;
import fr.cnrs.lacito.matchbox.assist.Explanation;
import fr.cnrs.lacito.matchbox.assist.ReviewResult;
import fr.cnrs.lacito.matchbox.assist.prompt.PromptBuilder;
import fr.cnrs.lacito.matchbox.assist.suggestion.GrammarQuickFixes;
import fr.cnrs.lacito.matchbox.assist.suggestion.Paragraphs;
import fr.cnrs.lacito.matchbox.assist.suggestion.Relocation;
import fr.cnrs.lacito.matchbox.assist.suggestion.Suggestion;
import fr.cnrs.lacito.matchbox.assist.suggestion.Suggestion.Source;
import fr.cnrs.lacito.matchbox.assist.suggestion.Suggestion.Status;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.assist.suggestion.TokenNames;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.view.GrammarNames;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.gui.mode.fileMode.EditorController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The suggestions panel, right of the editor: parse errors of the file, corrections derived from
 * the grammars, explanations and corrections of the AI. Every correction can be previewed
 * (pointer over it), applied (one undoable change) or dismissed; corrections follow the text as
 * it is edited, and are marked outdated when their text disappears.
 */
public final class SuggestionPanelController {

    /** At most this many errors get grammar fixes computed (each fix is checked by parsing). */
    static final int MAX_ERRORS_WITH_FIXES = 30;
    static final int MAX_ERRORS = 300;
    /** Size of the passages sent for a review of the whole file. */
    static final int REVIEW_CHUNK = 12_000;

    private final EditorController editor;
    private final AssistController assist;

    private final VBox root = new VBox(6);
    private final ObservableList<Suggestion> items = FXCollections.observableArrayList();
    private final FilteredList<Suggestion> filtered = new FilteredList<>(items);
    private final ListView<Suggestion> list = new ListView<>(new SortedList<>(filtered,
        Comparator.comparingInt(Suggestion::start).thenComparing(s -> s.source().ordinal())));
    private final Label title = new Label("Suggestions");
    private final ToggleButton errorsChip = chip("Errors");
    private final ToggleButton fixesChip = chip("Fixes");
    private final ToggleButton aiChip = chip("AI");
    private final ComboBox<String> scope = new ComboBox<>();
    private final Button reviewButton = new Button("Review with AI");
    private final Label status = new Label();
    private final Hyperlink showAnyway = new Hyperlink("show anyway");

    private Path file;
    private int nextId;
    /** Increased at each rebuild: results of older grammar-fix computations are ignored. */
    private long generation;
    /** Suggestions dismissed by the user, by id (errors keep their id across parses). */
    private final Set<String> dismissed = new HashSet<>();
    /** Explanations received, by error key, kept across parses. */
    private final Map<String, Explanation> explanations = new HashMap<>();
    /** Errors being explained, by suggestion id. */
    private final Set<String> explaining = new HashSet<>();
    /** Errors already explained, by suggestion id. */
    private final Set<String> explained = new HashSet<>();
    private List<ReviewResult.ReviewEdit> lastRejected = List.of();

    public SuggestionPanelController(EditorController editor, AssistController assist) {
        this.editor = editor;
        this.assist = assist;
        buildUi();
        editor.getCurrentFileErrors().addListener((ListChangeListener<Diagnostic>) c -> rebuildFromErrors());
        editor.userChanges().subscribe(change -> relocateAll(change.getPosition(), change.getRemoved().length(),
            change.getInserted().length()));
    }

    public Node getRoot() {
        return root;
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private static ToggleButton chip(String text) {
        ToggleButton b = new ToggleButton(text);
        b.setSelected(true);
        b.setStyle("-fx-font-size: 11;");
        return b;
    }

    private void buildUi() {
        root.setPadding(new Insets(6));
        root.setMinWidth(220);
        title.setStyle("-fx-font-weight: bold;");
        HBox chips = new HBox(4, errorsChip, fixesChip, aiChip);
        for (ToggleButton b : List.of(errorsChip, fixesChip, aiChip)) {
            b.selectedProperty().addListener((obs, o, n) -> updateFilter());
        }
        scope.getItems().setAll("Current block", "Selection", "Whole file");
        scope.setValue("Current block");
        reviewButton.setOnAction(e -> review(scope.getValue()));
        HBox reviewBox = new HBox(4, scope, reviewButton);
        reviewBox.setAlignment(Pos.CENTER_LEFT);
        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #555555; -fx-font-size: 11;");
        showAnyway.setVisible(false);
        showAnyway.managedProperty().bind(showAnyway.visibleProperty());
        showAnyway.setOnAction(e -> showRejected());
        list.setPlaceholder(new Label("No suggestion"));
        list.setCellFactory(l -> new SuggestionCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().addAll(title, chips, reviewBox, new HBox(4, status, showAnyway), list);
        items.addListener((ListChangeListener<Suggestion>) c -> updateCounts());
        updateCounts();
    }

    private void updateFilter() {
        filtered.setPredicate(s -> switch (s.source()) {
            case PARSE_ERROR -> errorsChip.isSelected();
            case GRAMMAR_FIX -> fixesChip.isSelected();
            case AI -> aiChip.isSelected();
        });
    }

    private void updateCounts() {
        title.setText("Suggestions (" + items.size() + ")");
        errorsChip.setText("Errors " + count(Source.PARSE_ERROR));
        fixesChip.setText("Fixes " + count(Source.GRAMMAR_FIX));
        aiChip.setText("AI " + count(Source.AI));
    }

    private long count(Source source) {
        return items.stream().filter(s -> s.source() == source).count();
    }

    private void setStatus(String text) {
        status.setText(text);
        showAnyway.setVisible(false);
    }

    // ------------------------------------------------------------------
    // Parse errors and grammar fixes
    // ------------------------------------------------------------------

    private void rebuildFromErrors() {
        Path current = editor.getSelectedFilePath();
        if (!Objects.equals(current, file)) {
            file = current;
            items.clear();
            dismissed.clear();
            explanations.clear();
            explained.clear();
            lastRejected = List.of();
            setStatus("");
        }
        generation++;
        items.removeIf(s -> s.source() != Source.AI);
        if (current == null) {
            return;
        }
        String text = editor.getFileContentArea().getText();
        List<GrammarPlugin> grammars = assist.grammarsFor(current);
        // warnings (readings not kept) are about the grammars of the file, not a text to fix
        List<Diagnostic> errors = editor.getCurrentFileErrors().stream().filter(Diagnostic::isError)
            .limit(MAX_ERRORS).toList();
        List<Suggestion> added = new ArrayList<>();
        for (Diagnostic error : errors) {
            String key = errorKey(error, text);
            String id = "err:" + key;
            if (dismissed.contains(id)) continue;
            int start = PromptBuilder.errorOffset(text, error);
            int end = error.stopIndex() >= start ? Math.min(text.length(), error.stopIndex() + 1) : start;
            Suggestion s = new Suggestion(id, Source.PARSE_ERROR, start, end, error.message(),
                expectedText(grammars, error), List.of(), Optional.of(error), Status.ACTIVE, "");
            Explanation known = explanations.get(key);
            if (known != null) {
                s = withExplanation(s, known, text);
                explained.add(id);
            }
            added.add(s);
        }
        items.addAll(added);
        computeGrammarFixes(current, text, grammars, errors, generation);
    }

    private static String expectedText(List<GrammarPlugin> grammars, Diagnostic error) {
        if (error.expected().isEmpty()) return "";
        GrammarPlugin g = grammars.stream().filter(x -> x.getId().equals(error.grammarId())).findFirst().orElse(null);
        return "Expected: " + error.expected().stream().map(t -> TokenNames.readable(g, t))
            .collect(Collectors.joining(", "));
    }

    /** Identifies an error across parses: grammar, message and text of its line. */
    private static String errorKey(Diagnostic error, String text) {
        int offset = PromptBuilder.errorOffset(text, error);
        int lineStart = offset == 0 ? 0 : text.lastIndexOf('\n', offset - 1) + 1;
        int lineEnd = text.indexOf('\n', offset);
        String line = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd);
        return error.grammarId() + "|" + error.message() + "|" + line;
    }

    private void computeGrammarFixes(Path current, String text, List<GrammarPlugin> grammars, List<Diagnostic> errors,
                                     long gen) {
        int n = 0;
        for (Diagnostic error : errors) {
            if (n++ == MAX_ERRORS_WITH_FIXES) break;
            String key = errorKey(error, text);
            List<GrammarQuickFixes.Fix> candidates = GrammarQuickFixes.candidates(text, error);
            for (int i = 0; i < candidates.size(); i++) {
                GrammarQuickFixes.Fix fix = candidates.get(i);
                String id = "fix:" + key + ":" + fix.title();
                if (dismissed.contains(id)) continue;
                FxAssist.onFx(assist.validator().checkAsync(fr.cnrs.lacito.matchbox.corpus.TextId.of(current), text, List.of(fix.edit()), grammars), result -> {
                    if (gen != generation || !result.accepted() || result.fixedErrors() == 0) return;
                    String now = editor.getFileContentArea().getText();
                    Optional<TextEdit> edit = Relocation.find(fix.edit(), now);
                    if (edit.isEmpty() || items.stream().anyMatch(s -> s.id().equals(id))) return;
                    items.add(new Suggestion(id, Source.GRAMMAR_FIX, edit.get().start(), edit.get().end(), fix.title(),
                        "For: " + error.message(), List.of(edit.get()), Optional.of(error), Status.ACTIVE,
                        result.fixedErrors() > 1 ? "Fixes " + result.fixedErrors() + " errors" : ""));
                }, error2 -> {});
            }
        }
    }

    // ------------------------------------------------------------------
    // Following the text
    // ------------------------------------------------------------------

    private void relocateAll(int position, int removed, int inserted) {
        if (items.isEmpty()) return;
        String text = editor.getFileContentArea().getText();
        List<Suggestion> moved = items.stream().map(s -> s.relocated(position, removed, inserted, text)).toList();
        if (!moved.equals(items)) {
            items.setAll(moved);
        }
    }

    private void replace(Suggestion updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(updated.id())) {
                items.set(i, updated);
                return;
            }
        }
    }

    private Optional<Suggestion> byId(String id) {
        return items.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    void goTo(Suggestion s) {
        editor.selectRange(s.start(), Math.max(s.end(), s.start() + 1));
    }

    void apply(Suggestion s) {
        if (!s.hasEdits() || s.status() == Status.OUTDATED) return;
        String text = editor.currentText();
        List<TextEdit> edits = new ArrayList<>();
        for (TextEdit e : s.edits()) {
            Optional<TextEdit> found = Relocation.find(e, text);
            if (found.isEmpty()) {
                replace(s.withStatus(Status.OUTDATED));
                setStatus("The text changed: this suggestion no longer applies.");
                return;
            }
            edits.add(found.get());
        }
        items.removeIf(x -> x.id().equals(s.id()));
        if (editor.applyEdits(edits)) {
            // the new parse refreshes errors and fixes
            editor.parseEditedFile();
        } else {
            setStatus("The text changed: this suggestion no longer applies.");
        }
    }

    void dismiss(Suggestion s) {
        dismissed.add(s.id());
        items.removeIf(x -> x.id().equals(s.id()));
        editor.clearPreview();
    }

    /** Asks the AI to explain the error of a suggestion. */
    void explain(Suggestion s) {
        if (s.error().isEmpty() || explaining.contains(s.id()) || !editor.isEditing()) return;
        if (!assist.ensureAllowed()) return;
        Optional<AssistService> service = assist.service();
        if (service.isEmpty()) {
            setStatus(assist.whyUnavailable());
            return;
        }
        String text = editor.currentText();
        AssistContext ctx = assist.context(editor.getSelectedFilePath(), text, editor.getFileContentArea().getCaretPosition());
        String key = errorKey(s.error().get(), text);
        explaining.add(s.id());
        list.refresh();
        FxAssist.onFx(service.get().explain(ctx, s.error().get()), explanation -> {
            explaining.remove(s.id());
            explained.add(s.id());
            explanations.put(key, explanation);
            byId(s.id()).ifPresent(current ->
                replace(withExplanation(current, explanation, editor.getFileContentArea().getText())));
            list.refresh();
        }, error -> {
            explaining.remove(s.id());
            list.refresh();
            setStatus("Explanation failed: " + FxAssist.message(error));
        });
    }

    private static Suggestion withExplanation(Suggestion s, Explanation e, String text) {
        Suggestion updated = s.withDetail(e.text()).withNote(e.note());
        if (e.fix().isPresent()) {
            Optional<TextEdit> fix = Relocation.find(e.fix().get(), text);
            if (fix.isPresent()) {
                updated = updated.withEdits(List.of(fix.get()));
            }
        }
        return updated;
    }

    /** Shows the suggestion of an error (e.g. chosen in the error table) and explains it if asked. */
    public void showError(Diagnostic error, boolean explain) {
        String text = editor.getFileContentArea().getText();
        String key = errorKey(error, text);
        items.stream()
            .filter(s -> s.source() == Source.PARSE_ERROR && s.error().map(e -> e == error || errorKey(e, text).equals(key)).orElse(false))
            .findFirst()
            .ifPresent(s -> {
                errorsChip.setSelected(true);
                list.getSelectionModel().select(s);
                list.scrollTo(s);
                if (explain) explain(s);
            });
    }

    /** Explains the error at the cursor. */
    public void explainErrorAtCaret() {
        int caret = editor.getFileContentArea().getCaretPosition();
        Optional<Diagnostic> error = editor.errorAt(caret);
        if (error.isEmpty()) {
            setStatus("No parse error at the cursor (parse the file first).");
            return;
        }
        showError(error.get(), true);
    }

    /** Selects and shows the next (or previous) suggestion after the cursor. */
    public void next(boolean forward) {
        List<Suggestion> sorted = list.getItems().stream().filter(s -> s.status() == Status.ACTIVE).toList();
        if (sorted.isEmpty()) return;
        int caret = editor.getFileContentArea().getCaretPosition();
        Suggestion target;
        if (forward) {
            target = sorted.stream().filter(s -> s.start() > caret).findFirst().orElse(sorted.get(0));
        } else {
            target = sorted.stream().filter(s -> s.start() < caret).reduce((a, b) -> b).orElse(sorted.get(sorted.size() - 1));
        }
        list.getSelectionModel().select(target);
        list.scrollTo(target);
        goTo(target);
    }

    /** Applies the suggestion selected in the list. */
    public void applySelected() {
        Suggestion s = list.getSelectionModel().getSelectedItem();
        if (s != null) apply(s);
    }

    // ------------------------------------------------------------------
    // Review
    // ------------------------------------------------------------------

    /** Reviews a passage: "Current block", "Selection" or "Whole file". */
    public void review(String what) {
        if (!editor.isEditing()) return;
        String text = editor.currentText();
        int start;
        int end;
        switch (what) {
            case "Selection" -> {
                var sel = editor.getFileContentArea().getSelection();
                if (sel.getLength() == 0) {
                    setStatus("Select a passage first.");
                    return;
                }
                start = sel.getStart();
                end = sel.getEnd();
            }
            case "Whole file" -> {
                start = 0;
                end = text.length();
            }
            default -> {
                int caret = editor.getFileContentArea().getCaretPosition();
                Optional<CorpusObject> block = editor.getCurrentFileObjects().stream()
                    .filter(o -> o.getTextCoordinate().contains(caret) || o.getTextCoordinate().stopIndex() + 1 == caret)
                    .findFirst();
                start = block.map(o -> o.getTextCoordinate().startIndex()).orElse(Paragraphs.start(text, caret));
                end = block.map(o -> Math.min(text.length(), o.getTextCoordinate().stopIndex() + 1))
                    .orElse(Paragraphs.end(text, caret));
            }
        }
        if (start >= end || text.substring(start, end).isBlank()) {
            setStatus("Nothing to review here.");
            return;
        }
        if (!assist.ensureAllowed()) return;
        Optional<AssistService> service = assist.service();
        if (service.isEmpty()) {
            setStatus(assist.whyUnavailable());
            return;
        }
        Path reviewed = editor.getSelectedFilePath();
        AssistContext ctx = assist.context(reviewed, text, editor.getFileContentArea().getCaretPosition());
        List<int[]> chunks = Paragraphs.chunks(text, start, end, REVIEW_CHUNK);
        reviewButton.setDisable(true);
        setStatus("Reviewing" + (chunks.size() > 1 ? " (" + chunks.size() + " parts)" : "") + "…");
        // parts one after the other, to stay within rate limits
        CompletableFuture<List<ReviewResult>> all = CompletableFuture.completedFuture(new ArrayList<>());
        for (int[] c : chunks) {
            all = all.thenCompose(results -> service.get().review(ctx, c[0], c[1]).thenApply(r -> {
                results.add(r);
                return results;
            }));
        }
        FxAssist.onFx(all, results -> {
            reviewButton.setDisable(false);
            if (!reviewed.equals(editor.getSelectedFilePath())) return;
            showReview(results);
        }, error -> {
            reviewButton.setDisable(false);
            setStatus("Review failed: " + FxAssist.message(error));
        });
    }

    private void showReview(List<ReviewResult> results) {
        String now = editor.getFileContentArea().getText();
        int added = 0;
        int unlocated = 0;
        List<ReviewResult.ReviewEdit> rejected = new ArrayList<>();
        for (ReviewResult r : results) {
            unlocated += r.unlocated();
            rejected.addAll(r.rejected());
            for (ReviewResult.ReviewEdit e : r.accepted()) {
                if (addReviewEdit(e, now, e.fixedErrors() > 0 ? "Fixes " + e.fixedErrors() + " parse error(s)" : "")) {
                    added++;
                }
            }
        }
        aiChip.setSelected(true);
        lastRejected = rejected;
        StringBuilder msg = new StringBuilder(added == 0 ? "No correction proposed for this passage." :
            added + " correction" + (added > 1 ? "s" : "") + " proposed.");
        if (unlocated > 0) {
            msg.append(" ").append(unlocated).append(" could not be located in the text.");
        }
        if (!rejected.isEmpty()) {
            msg.append(" ").append(rejected.size()).append(" discarded (would introduce parse errors):");
        }
        status.setText(msg.toString());
        showAnyway.setVisible(!rejected.isEmpty());
    }

    private boolean addReviewEdit(ReviewResult.ReviewEdit e, String text, String note) {
        Optional<TextEdit> edit = Relocation.find(e.edit(), text);
        if (edit.isEmpty()) return false;
        String kind = e.kind().isEmpty() ? "Correction" : Character.toUpperCase(e.kind().charAt(0)) + e.kind().substring(1);
        items.add(new Suggestion("ai:" + (nextId++), Source.AI, edit.get().start(), edit.get().end(), kind,
            e.explanation(), List.of(edit.get()), Optional.empty(), Status.ACTIVE, note));
        return true;
    }

    private void showRejected() {
        String text = editor.getFileContentArea().getText();
        for (ReviewResult.ReviewEdit e : lastRejected) {
            addReviewEdit(e, text, "⚠ Would introduce parse errors");
        }
        lastRejected = List.of();
        setStatus("");
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    private static String lineColumn(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return line + ":" + (offset - lineStart);
    }

    private final class SuggestionCell extends ListCell<Suggestion> {

        SuggestionCell() {
            setOnMouseEntered(e -> {
                Suggestion s = getItem();
                if (s != null && !isEmpty() && editor.isEditing()) editor.previewRange(s.start(), s.end());
            });
            setOnMouseExited(e -> {
                if (editor.isEditing()) editor.clearPreview();
            });
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && getItem() != null) goTo(getItem());
            });
        }

        @Override
        protected void updateItem(Suggestion s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setText(null);
            VBox box = new VBox(3);
            box.setMaxWidth(Double.MAX_VALUE);
            box.prefWidthProperty().bind(list.widthProperty().subtract(30));

            Label badge = new Label(s.source().label());
            badge.setStyle("-fx-text-fill: white; -fx-font-size: 10; -fx-padding: 1 4 1 4; -fx-background-radius: 3; " +
                "-fx-background-color: " + switch (s.source()) {
                    case PARSE_ERROR -> "#c0392b";
                    case GRAMMAR_FIX -> "#2e6da4";
                    case AI -> "#7b4bb8";
                } + ";");
            Label position = new Label(lineColumn(editor.getFileContentArea().getText(), s.start()));
            position.setStyle("-fx-text-fill: #777777; -fx-font-size: 11;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button close = new Button("✕");
            close.setStyle("-fx-font-size: 9; -fx-padding: 0 4 0 4;");
            close.setOnAction(e -> dismiss(s));
            HBox header = new HBox(6, badge, position, spacer, close);
            // errors and grammar fixes: the plugin that reported the error
            s.error().ifPresent(error -> {
                String name = GrammarNames.label(error.grammarId(), PluginManager.getAvailableGrammars());
                if (!name.isEmpty()) {
                    Label plugin = new Label(name);
                    plugin.setStyle("-fx-text-fill: #777777; -fx-font-size: 10;");
                    plugin.setTooltip(new Tooltip(name));
                    header.getChildren().add(2, plugin);
                }
            });
            header.setAlignment(Pos.CENTER_LEFT);
            if (s.status() == Status.OUTDATED) {
                Label outdated = new Label("outdated");
                outdated.setStyle("-fx-text-fill: #999999; -fx-font-size: 10;");
                header.getChildren().add(2, outdated);
            }
            box.getChildren().add(header);

            Label titleLabel = new Label(s.title());
            titleLabel.setWrapText(true);
            titleLabel.setStyle("-fx-font-weight: bold;");
            box.getChildren().add(titleLabel);
            if (!s.detail().isEmpty()) {
                Label detail = new Label(s.detail());
                detail.setWrapText(true);
                box.getChildren().add(detail);
            }
            for (TextEdit edit : s.edits().stream().limit(3).toList()) {
                box.getChildren().add(diff(edit));
            }
            if (!s.note().isEmpty()) {
                Label note = new Label(s.note());
                note.setWrapText(true);
                note.setStyle("-fx-text-fill: #777777; -fx-font-style: italic; -fx-font-size: 11;");
                box.getChildren().add(note);
            }

            HBox actions = new HBox(4);
            if (s.hasEdits() && s.status() == Status.ACTIVE) {
                Button apply = new Button("Apply");
                apply.setOnAction(e -> apply(s));
                actions.getChildren().add(apply);
            }
            if (s.source() == Source.PARSE_ERROR && !explained.contains(s.id())) {
                boolean running = explaining.contains(s.id());
                Button explain = new Button(running ? "Explaining…" : "Explain");
                explain.setDisable(running);
                explain.setOnAction(e -> explain(s));
                actions.getChildren().add(explain);
            }
            Button goTo = new Button("Go to");
            goTo.setOnAction(e -> goTo(s));
            actions.getChildren().add(goTo);
            for (Node b : actions.getChildren()) {
                b.setStyle("-fx-font-size: 11;");
            }
            box.getChildren().add(actions);
            box.setOpacity(s.status() == Status.OUTDATED ? 0.5 : 1);
            setGraphic(box);
        }

        private TextFlow diff(TextEdit edit) {
            TextFlow flow = new TextFlow();
            flow.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");
            if (!edit.expectedOriginal().isEmpty()) {
                Text removed = new Text("− " + edit.expectedOriginal() + "\n");
                removed.setFill(Color.web("#b03a2e"));
                removed.setStrikethrough(true);
                flow.getChildren().add(removed);
            }
            Text addedText = new Text(edit.replacement().isEmpty() ? "(delete)" : "+ " + edit.replacement());
            addedText.setFill(Color.web("#1e8449"));
            flow.getChildren().add(addedText);
            return flow;
        }
    }
}
