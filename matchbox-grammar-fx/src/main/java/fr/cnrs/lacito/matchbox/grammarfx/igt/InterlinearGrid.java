package fr.cnrs.lacito.matchbox.grammarfx.igt;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabels;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossParts;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearEdits;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearIssue;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.corpus.igt.SettingSpec;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.ViewFinding;
import fr.cnrs.lacito.matchbox.grammarfx.media.MediaResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The words of an IGT in a grid: one row per segmented line (word level, then morpheme level, the two
 * sharing their columns: word n is the same word on every line), one column per word, a ▶ per word
 * when the IGT has a timeline. A cell edits its word: Enter or focus loss commits when the word fits
 * its column ({@link WordEditCheck}); otherwise a bar offers Keep editing / Revert / Write anyway.
 * Rows with alignment issues get a {@code !} in their margin; words with findings are underlined.
 */
public final class InterlinearGrid extends VBox {

    private static final String CELL = "-fx-font-family: monospace; -fx-padding: 1 3 1 3;";
    private static final String CELL_ERROR = CELL + " -fx-border-color: #d33; -fx-border-width: 1;";

    private final ViewContext context;
    private final GridPane grid = new GridPane();
    private final HBox bar = new HBox(6);
    private final VBox issuesBox = new VBox(2);
    private final VBox findingsBox = new VBox(2);
    private InterlinearReading reading;
    private boolean compact;
    /** The cell being edited with a value that does not fit (kept across a re-parse). */
    private TextField pending;

    public InterlinearGrid(ViewContext context) {
        this.context = context;
        setSpacing(6);
        grid.setId("igtGrid");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setVisible(false);
        bar.setManaged(false);
        bar.setStyle("-fx-padding: 4; -fx-background-color: #fff4e5; -fx-border-color: #e0a040;");
        issuesBox.setStyle("-fx-text-fill: #b07000;");
        getChildren().addAll(toolbar(), grid, bar, findingsBox, issuesBox);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            int[] at = focusedCell();
            if (at == null) return;
            if (new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN).match(e)) {
                insertWord(at[1]);
                e.consume();
            } else if (new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN).match(e)) {
                insertWord(at[1] + 1);
                e.consume();
            } else if (new KeyCodeCombination(KeyCode.DELETE, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN).match(e)
                || new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN).match(e)) {
                deleteWord(at[1]);
                e.consume();
            } else if (new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN).match(e)) {
                format();
                e.consume();
            } else if (new KeyCodeCombination(KeyCode.SPACE, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN).match(e)) {
                play(at[1]);
                e.consume();
            } else if (e.isAltDown() && !e.isShortcutDown()
                && (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)) {
                move(at, e.getCode());
                e.consume();
            }
        });
    }

    private Node toolbar() {
        Button format = new Button("Format");
        format.setId("igtFormat");
        format.setTooltip(new Tooltip("Align the words in columns in the text (Shortcut+Alt+F)"));
        format.setOnAction(e -> format());
        Button more = new Button("⋯");
        more.setTooltip(new Tooltip("More commands"));
        ContextMenu menu = new ContextMenu(
            item("Insert word at start", () -> insertWord(0)),
            item("Insert word at end", () -> insertWord(wordCount())),
            item("Format", this::format),
            item("Declare line roles", this::declareRoles),
            new SeparatorMenuItem(),
            item("Compact / comfortable", () -> {
                compact = !compact;
                render();
            }));
        more.setOnAction(e -> menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0));
        HBox box = new HBox(6, format, more);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** Shows a reading; a cell being edited with a value that does not fit keeps its text. */
    public void show(InterlinearReading reading) {
        this.reading = reading;
        if (pending != null && pending.isFocused()) return;
        pending = null;
        render();
    }

    public InterlinearReading reading() {
        return reading;
    }

    private InterlinearGlossedText igt() {
        return reading.igt();
    }

    private int wordCount() {
        return reading.lines().stream().filter(LineReading::isSegmented).mapToInt(l -> l.words().size()).max().orElse(0);
    }

    private void render() {
        grid.getChildren().clear();
        grid.setHgap(compact ? 4 : 10);
        grid.setVgap(compact ? 1 : 4);
        hideBar();
        if (reading == null) return;
        boolean editable = context.canEdit(igt());
        int columns = wordCount();
        List<ViewFinding> findings = context.findings(igt());

        // header: word numbers and ▶
        for (int w = 0; w < columns; w++) grid.add(header(w), w + 1, 0);
        int row = 1;
        Level current = null;
        for (LineReading l : reading.lines()) {
            if (!l.isSegmented() || l.role() == LineRole.TIMELINE) continue;
            Level level = l.level().orElseThrow();
            if (level != current) {
                Label section = new Label(level == Level.WORD ? "Words (word level)" : "Morphemes");
                section.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
                grid.add(section, 0, row++, Math.max(1, columns + 1), 1);
                current = level;
            }
            grid.add(rowHeader(l), 0, row);
            List<Word> words = l.words();
            for (int w = 0; w < words.size(); w++) {
                grid.add(cell(l, w, words.get(w), editable, findings), w + 1, row);
            }
            row++;
        }
        issuesBox.getChildren().setAll(reading.issues().stream()
            .filter(i -> i.line() < 0 || i.kind() == InterlinearIssue.Kind.ROLE_ORDER || i.kind() == InterlinearIssue.Kind.WORD_COUNT)
            .map(i -> {
                Label label = new Label("⚠ " + i.message());
                label.setWrapText(true);
                label.setStyle("-fx-text-fill: #b07000;");
                return label;
            }).toList());
        renderFindings(findings);
    }

    private Node header(int w) {
        Label number = new Label(String.valueOf(w + 1));
        number.setStyle("-fx-text-fill: #888;");
        Button play = new Button("▶");
        play.setStyle("-fx-padding: 0 4 0 4; -fx-font-size: 10;");
        Optional<TimeRange> range = range(w);
        Optional<Path> file = mediaFile();
        play.setDisable(range.isEmpty() || file.isEmpty());
        play.setTooltip(new Tooltip(range.isEmpty() ? (timeline().isEmpty() ? "No timeline" : "The time of this word is not known")
            : file.isEmpty() ? "No media file for this IGT (media metadata, a preceding @media line, the section or the header)"
            : String.format(java.util.Locale.ROOT, "%.3f–%.3f s", range.get().startMs() / 1000.0, range.get().endMs() / 1000.0)));
        play.setOnAction(e -> play(w));
        play.setVisible(timeline().isPresent());
        play.setManaged(timeline().isPresent());
        VBox box = new VBox(1, number, play);
        box.setAlignment(Pos.CENTER_LEFT);
        ContextMenu menu = new ContextMenu(
            item("Play", () -> play(w)),
            item("Insert word before", () -> insertWord(w)),
            item("Insert word after", () -> insertWord(w + 1)),
            item("Delete word", () -> deleteWord(w)),
            item("Show in editor", () -> reading.lines().stream().filter(LineReading::isSegmented)
                .filter(l -> w < l.words().size()).findFirst()
                .ifPresent(l -> context.selectInEditor(l.words().get(w).span()))));
        number.setContextMenu(menu);
        number.setTooltip(new Tooltip("Word " + (w + 1) + " (right-click: insert, delete, play)"));
        return box;
    }

    private Node rowHeader(LineReading l) {
        String letter = switch (l.role()) {
            case TRANSCRIPTION -> "T";
            case GLOSS -> "G";
            default -> "?";
        };
        String text = letter + l.name().map(n -> " " + n).orElse("") + l.language().map(v -> " · " + v).orElse("");
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        label.setTooltip(new Tooltip(l.reason() + "\nRight-click to read it as another kind of line"));
        ContextMenu menu = new ContextMenu();
        for (LineRole role : List.of(LineRole.TRANSCRIPTION, LineRole.GLOSS, LineRole.ORTHOGRAPHIC)) {
            if (role == l.role()) continue;
            menu.getItems().add(item("Read as " + role.name().toLowerCase(java.util.Locale.ROOT), () -> setRole(l, role)));
        }
        label.setContextMenu(menu);
        List<InterlinearIssue> issues = reading.issues().stream().filter(i -> i.line() == l.index()).toList();
        Label mark = new Label(issues.isEmpty() ? " " : "!");
        mark.setId(issues.isEmpty() ? null : "igtRowIssue");
        mark.setStyle("-fx-text-fill: #d33; -fx-font-weight: bold; -fx-min-width: 10;");
        mark.getProperties().put("initial", mark.getText());
        if (!issues.isEmpty()) {
            mark.setTooltip(new Tooltip(issues.stream().map(InterlinearIssue::message).collect(Collectors.joining("\n"))));
        }
        HBox box = new HBox(4, mark, label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getProperties().put("mark", mark);
        return box;
    }

    private Node cell(LineReading l, int w, Word word, boolean editable, List<ViewFinding> findings) {
        List<ViewFinding> mine = findings.stream().filter(f -> overlaps(f.span(), word.span())).toList();
        String tooltip = tooltip(l, word, mine);
        if (!editable) {
            Label label = new Label(word.text());
            label.setStyle(CELL + underline(mine));
            if (!tooltip.isEmpty()) label.setTooltip(new Tooltip(tooltip));
            return label;
        }
        TextField field = new TextField(word.text());
        field.setPrefColumnCount(Math.max(3, word.text().length()));
        field.setStyle(CELL + underline(mine));
        field.getProperties().put("cell", new int[] {l.index(), w});
        field.setAccessibleText(roleName(l) + " line, word " + (w + 1) + ", " + word.text() + ", "
            + word.morphemes().size() + " morpheme" + (word.morphemes().size() == 1 ? "" : "s"));
        if (!tooltip.isEmpty()) field.setTooltip(new Tooltip(tooltip));
        Optional<Integer> expected = WordEditCheck.expectedMorphemes(reading, l.index(), w);
        Level level = l.level().orElse(Level.WORD);
        field.textProperty().addListener((obs, o, n) -> {
            Optional<String> problem = WordEditCheck.check(n, level, expected);
            field.setStyle((problem.isPresent() ? CELL_ERROR : CELL) + underline(mine));
            field.setTooltip(problem.map(Tooltip::new).orElse(tooltip.isEmpty() ? null : new Tooltip(tooltip)));
            markRow(field, problem.isPresent());
        });
        field.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (" ".equals(e.getCharacter())) {
                e.consume();
                field.setTooltip(new Tooltip("A word has no space: insert a word instead (Shortcut+Alt+I)"));
            }
        });
        field.setOnAction(e -> commit(field, l, w, word, level, expected, false));
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                field.setText(word.text());
                hideBar();
                pending = null;
            }
        });
        field.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused && !field.getText().equals(word.text())) commit(field, l, w, word, level, expected, true);
        });
        return field;
    }

    private void markRow(TextField field, boolean problem) {
        for (Node n : grid.getChildren()) {
            if (GridPane.getRowIndex(n) != null && GridPane.getRowIndex(n).equals(GridPane.getRowIndex(field))
                && GridPane.getColumnIndex(n) != null && GridPane.getColumnIndex(n) == 0
                && n.getProperties().get("mark") instanceof Label mark) {
                mark.setText(problem ? "!" : (String) mark.getProperties().get("initial"));
            }
        }
    }

    private void commit(TextField field, LineReading l, int w, Word word, Level level, Optional<Integer> expected,
                        boolean focusLost) {
        String value = field.getText();
        if (value.equals(word.text())) return;
        Optional<String> problem = WordEditCheck.check(value, level, expected);
        if (problem.isPresent() && (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace))) {
            showBar(problem.get(), field, word, null);
            pending = field;
            return;
        }
        if (problem.isPresent()) {
            pending = field;
            showBar(value + ": " + problem.get() + ".", field, word, () -> write(l, w, value));
            return;
        }
        write(l, w, value);
    }

    private void write(LineReading l, int w, String value) {
        hideBar();
        pending = null;
        EditOutcome outcome = context.setField(igt(), InterlinearGlossedText.wordField(l.index(), w), value);
        if (!outcome.isApplied()) showMessage(outcome.message());
    }

    private void showBar(String message, TextField field, Word word, Runnable writeAnyway) {
        Label label = new Label(message);
        label.setWrapText(true);
        Button keep = new Button("Keep editing");
        keep.setOnAction(e -> {
            hideBar();
            field.requestFocus();
        });
        Button revert = new Button("Revert");
        revert.setOnAction(e -> {
            pending = null;
            field.setText(word.text());
            hideBar();
        });
        bar.getChildren().setAll(label, keep, revert);
        if (writeAnyway != null) {
            Button write = new Button("Write anyway");
            write.setOnAction(e -> writeAnyway.run());
            bar.getChildren().add(write);
        }
        bar.setVisible(true);
        bar.setManaged(true);
    }

    private void showMessage(String message) {
        bar.getChildren().setAll(new Label(message));
        bar.setVisible(true);
        bar.setManaged(true);
    }

    private void hideBar() {
        bar.getChildren().clear();
        bar.setVisible(false);
        bar.setManaged(false);
    }

    /** The bar of findings (gloss check): one line per finding, with its fixes, and "Fix all" when several have one. */
    private void renderFindings(List<ViewFinding> findings) {
        findingsBox.getChildren().clear();
        if (findings.isEmpty()) return;
        List<ViewFinding> fixable = findings.stream().filter(f -> !f.fixes().isEmpty()).toList();
        Label title = new Label("◆ " + findings.size() + " finding" + (findings.size() == 1 ? "" : "s") + " ("
            + findings.get(0).source() + ")");
        title.setStyle("-fx-text-fill: #b07000; -fx-font-weight: bold;");
        HBox head = new HBox(8, title);
        head.setAlignment(Pos.CENTER_LEFT);
        if (fixable.size() > 1) {
            Button all = new Button(fixable.get(0).fixes().get(0).split(" ")[0] + " all (" + fixable.size() + ")");
            all.setId("igtFixAll");
            all.setOnAction(e -> report(context.applyFixes(igt(), fixable, all.getText())));
            head.getChildren().add(all);
        }
        findingsBox.getChildren().add(head);
        for (ViewFinding f : findings) {
            Label message = new Label(f.message());
            message.setWrapText(true);
            HBox line = new HBox(8, message);
            line.setAlignment(Pos.CENTER_LEFT);
            for (int i = 0; i < f.fixes().size(); i++) {
                int index = i;
                Button fix = new Button(f.fixes().get(i));
                fix.setOnAction(e -> report(context.applyFix(igt(), f, index)));
                line.getChildren().add(fix);
            }
            findingsBox.getChildren().add(line);
        }
    }

    private void report(EditOutcome outcome) {
        if (!outcome.isApplied()) showMessage(outcome.message());
    }

    private static boolean overlaps(TextCoordinate a, TextCoordinate b) {
        return a.text().equals(b.text()) && a.startIndex() <= b.stopIndex() && b.startIndex() <= a.stopIndex();
    }

    private static String underline(List<ViewFinding> findings) {
        if (findings.isEmpty()) return "";
        boolean error = findings.stream().anyMatch(f -> f.severity() == Diagnostic.Severity.ERROR);
        return " -fx-border-color: transparent transparent " + (error ? "#d33" : "#e08000") + " transparent;"
            + " -fx-border-style: dotted; -fx-border-width: 0 0 2 0;";
    }

    /** The meanings of the grammatical labels of a gloss, and the findings of the word. */
    private static String tooltip(LineReading l, Word word, List<ViewFinding> findings) {
        List<String> lines = new ArrayList<>();
        if (l.role() == LineRole.GLOSS) {
            for (Morpheme m : word.morphemes()) {
                for (String part : GlossParts.grammatical(m.form())) {
                    GlossLabels.meaning(part).ifPresent(meaning -> lines.add(part + " — " + meaning));
                }
            }
        }
        findings.forEach(f -> lines.add(f.message()));
        return String.join("\n", lines);
    }

    private static String roleName(LineReading l) {
        return l.role().name().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Media
    // ------------------------------------------------------------------

    private Optional<InterlinearTimeline> timeline() {
        for (Level level : List.of(Level.WORD, Level.MORPHEME)) {
            Optional<InterlinearTimeline> t = reading.lines().stream()
                .filter(l -> l.role() == LineRole.TIMELINE && l.level().orElse(Level.WORD) == level)
                .map(LineReading::line).filter(InterlinearTimeline.class::isInstance).map(InterlinearTimeline.class::cast)
                .findFirst();
            if (t.isPresent()) return t;
        }
        return Optional.empty();
    }

    /** The time of a word: word level, else from its first to its last morpheme. */
    Optional<TimeRange> range(int word) {
        Optional<List<WordTiming>> words = reading.timing(Level.WORD);
        if (words.isPresent()) {
            return words.get().stream().filter(t -> t.word() == word).findFirst().flatMap(WordTiming::range);
        }
        List<TimeRange> parts = reading.timing(Level.MORPHEME).orElse(List.of()).stream()
            .filter(t -> t.word() == word).map(WordTiming::range).flatMap(Optional::stream).toList();
        if (parts.isEmpty()) return Optional.empty();
        long start = parts.stream().mapToLong(TimeRange::startMs).min().orElseThrow();
        long end = parts.stream().mapToLong(TimeRange::endMs).max().orElseThrow();
        return end > start ? Optional.of(new TimeRange(start, end)) : Optional.empty();
    }

    private Optional<Path> mediaFile() {
        return timeline().flatMap(t -> MediaResolver.resolve(t, context.corpus(), context.projectDirectory()).file().path());
    }

    private void play(int word) {
        Optional<TimeRange> range = range(word);
        Optional<Path> file = mediaFile();
        if (range.isPresent() && file.isPresent()) context.mediaPlayers().play(file.get(), range);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private void apply(List<TextEdit> edits, String description) {
        if (edits.isEmpty()) return;
        report(context.applyEdits(igt(), edits, description));
    }

    public void insertWord(int index) {
        apply(InterlinearEdits.insertWord(reading, index), "Insert word " + (index + 1));
    }

    public void deleteWord(int index) {
        context.corpus().textOf(igt()).ifPresent(t ->
            apply(InterlinearEdits.deleteWord(reading, index, t.content()), "Delete word " + (index + 1)));
    }

    public void format() {
        context.corpus().textOf(igt()).ifPresent(t -> apply(InterlinearEdits.format(reading, t.content()), "Format IGT"));
    }

    /** Writes the roles of the lines, as they are read, in the IGT's own setting. */
    public void declareRoles() {
        writeSetting(entries(null, null));
    }

    private void setRole(LineReading line, LineRole role) {
        writeSetting(entries(line, role));
    }

    private List<SettingSpec.Entry> entries(LineReading changed, LineRole role) {
        List<SettingSpec.Entry> entries = new ArrayList<>();
        for (LineReading l : reading.lines()) {
            LineRole r = l == changed ? role : l.role();
            if (l.role() != LineRole.ORTHOGRAPHIC && l.role() != LineRole.TRANSCRIPTION && l.role() != LineRole.GLOSS) continue;
            Optional<Level> level = r == LineRole.ORTHOGRAPHIC ? Optional.empty()
                : l.level().filter(v -> !l.line().segmentation().equals(Optional.of(v)));
            entries.add(new SettingSpec.Entry(r, level, l.name(), l.declared() ? l.language() : Optional.empty()));
        }
        return entries;
    }

    private void writeSetting(List<SettingSpec.Entry> entries) {
        String value = SettingSpec.write(entries);
        String field = InterlinearGlossedText.metadataField("setting");
        report(igt().metadata().containsKey("setting") ? context.setField(igt(), field, value)
            : context.addField(igt(), field, value));
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    /** {line index, word} of the focused cell, null if none. */
    private int[] focusedCell() {
        for (Node n : grid.getChildren()) {
            if (n instanceof TextField f && f.isFocused() && f.getProperties().get("cell") instanceof int[] at) return at;
        }
        return null;
    }

    private void move(int[] at, KeyCode code) {
        List<Integer> rows = reading.lines().stream().filter(l -> l.isSegmented() && l.role() != LineRole.TIMELINE)
            .map(LineReading::index).toList();
        int r = rows.indexOf(at[0]);
        int line = at[0];
        int word = at[1];
        switch (code) {
            case LEFT -> word--;
            case RIGHT -> word++;
            case UP -> line = r > 0 ? rows.get(r - 1) : line;
            case DOWN -> line = r + 1 < rows.size() ? rows.get(r + 1) : line;
            default -> { }
        }
        for (Node n : grid.getChildren()) {
            if (n instanceof TextField f && f.getProperties().get("cell") instanceof int[] c && c[0] == line && c[1] == word) {
                f.requestFocus();
                return;
            }
        }
    }

    /** The text fields of the cells (for tests): {line index, word} → field. */
    public List<TextField> cells() {
        return grid.getChildren().stream().filter(TextField.class::isInstance).map(TextField.class::cast).toList();
    }

    /** The field of a cell, if editable. */
    public Optional<TextField> cell(int line, int word) {
        return cells().stream().filter(f -> f.getProperties().get("cell") instanceof int[] c && c[0] == line && c[1] == word)
            .findFirst();
    }

    /** The bar shown after an edit that does not fit (for tests). */
    public HBox messageBar() {
        return bar;
    }
}
