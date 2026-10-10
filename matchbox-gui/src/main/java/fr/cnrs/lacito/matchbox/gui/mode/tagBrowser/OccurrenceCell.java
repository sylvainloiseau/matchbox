package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;

/**
 * A row of the occurrence list: a file header, or a tag with a few lines of context (read-only).
 * The tag is highlighted with the tag colour, the lines of the object it applies to are tinted,
 * and a thin rule separates the occurrences. Edit (or a double-click) shows the tag in File Mode.
 */
public final class OccurrenceCell extends ListCell<OccurrenceRow> {

    static final String MONO = "-fx-font-family: monospace;";
    private static final String TARGET_TINT = "-fx-background-color: #f2f6fb;";
    private static final String RULE = "-fx-border-color: transparent transparent #dddddd transparent; -fx-border-width: 0 0 1 0;";
    private static final String HEADER = "-fx-background-color: #eeeeee; -fx-font-weight: bold; -fx-padding: 3 6 3 6;";

    private final Supplier<CorpusView> corpus;
    private final IntSupplier before;
    private final IntSupplier after;
    private final Function<CorpusObject, String> tagColor;
    private final Consumer<TagOccurrence> edit;

    /**
     * @param before   lines shown before the tag
     * @param after    lines shown after the tag
     * @param tagColor highlight colour of a tag (CSS), null for none
     * @param edit     shows an occurrence in File Mode
     */
    public OccurrenceCell(Supplier<CorpusView> corpus, IntSupplier before, IntSupplier after, Function<CorpusObject, String> tagColor,
                          Consumer<TagOccurrence> edit) {
        this.corpus = corpus;
        this.before = before;
        this.after = after;
        this.tagColor = tagColor;
        this.edit = edit;
        setPadding(Insets.EMPTY);
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && getItem() instanceof OccurrenceRow.Occurrence o) {
                edit.accept(o.occurrence());
            }
        });
    }

    @Override
    protected void updateItem(OccurrenceRow row, boolean empty) {
        super.updateItem(row, empty);
        setText(null);
        if (empty || row == null) {
            setGraphic(null);
            return;
        }
        switch (row) {
            case OccurrenceRow.FileHeader h -> {
                Label label = new Label(h.text().displayName() + " — " + h.count() + (h.count() == 1 ? " occurrence" : " occurrences"));
                label.setStyle(HEADER);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setTooltip(new Tooltip(h.text().path().toString()));
                setGraphic(label);
            }
            case OccurrenceRow.Occurrence o -> setGraphic(occurrence(o.occurrence()));
        }
    }

    private VBox occurrence(TagOccurrence o) {
        Optional<Text> text = corpus.get().text(o.text());
        TextCoordinate span = o.tag().getTextCoordinate();

        Label location = new Label(o.text().displayName() + ":" + span.startLine());
        location.setStyle("-fx-font-weight: bold;");
        Label type = new Label("· " + o.targetType() + o.target().map(t -> " · " + shorten(t.getLabel())).orElse(""));
        type.setStyle("-fx-text-fill: #555555;");
        HBox header = new HBox(6, location, type);
        header.setAlignment(Pos.CENTER_LEFT);
        // the other tags of the same object
        text.ifPresent(t -> otherTags(t, o).forEach(other -> {
            Label badge = new Label(String.join(" › ", TagOccurrence.pathOf(other)));
            badge.setStyle("-fx-background-color: " + color(other) + "; -fx-background-radius: 8; -fx-padding: 0 5 0 5; -fx-font-size: 0.85em;");
            header.getChildren().add(badge);
        }));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button("Edit");
        editButton.setStyle("-fx-font-size: 0.85em; -fx-padding: 1 8 1 8;");
        editButton.setTooltip(new Tooltip("Show this tag in File Mode (double-click, Enter)"));
        editButton.setFocusTraversable(false);
        editButton.setOnAction(e -> edit.accept(o));
        header.getChildren().addAll(spacer, editButton);

        VBox lines = new VBox();
        if (text.isPresent()) {
            String content = text.get().content();
            int tagStart = span.startIndex();
            int tagEnd = Math.max(tagStart, span.stopIndex() + 1);
            ContextExcerpt excerpt = ContextExcerpt.of(content, tagStart, tagEnd, before.getAsInt(), after.getAsInt());
            int digits = Integer.toString(excerpt.lines().get(excerpt.lines().size() - 1).number()).length();
            Optional<TextCoordinate> target = o.target().map(CorpusObject::getTextCoordinate);
            for (ContextExcerpt.Line line : excerpt.lines()) {
                lines.getChildren().add(line(line, digits, tagStart, tagEnd, target, color(o.tag())));
            }
        } else {
            lines.getChildren().add(new Label("The text is no longer in the project"));
        }

        VBox box = new VBox(2, header, lines);
        box.setPadding(new Insets(4, 6, 6, 6));
        box.setStyle(RULE);
        return box;
    }

    private String color(CorpusObject tag) {
        String c = tagColor.apply(tag);
        return c == null ? "transparent" : c;
    }

    /** A line: its number, then its text with the tag highlighted; tinted if it belongs to the target. */
    private HBox line(ContextExcerpt.Line line, int digits, int tagStart, int tagEnd, Optional<TextCoordinate> target,
                      String color) {
        Label number = new Label(String.format("%" + digits + "d", line.number()));
        number.setStyle(MONO + "-fx-text-fill: #999999;");
        number.setMinWidth(Region.USE_PREF_SIZE);
        TextFlow flow = new TextFlow();
        String s = line.text();
        int from = Math.max(0, Math.min(s.length(), tagStart - line.start()));
        int to = Math.max(from, Math.min(s.length(), tagEnd - line.start()));
        if (from > 0) flow.getChildren().add(plain(s.substring(0, from)));
        if (to > from) {
            Label tag = new Label(s.substring(from, to));
            tag.setStyle(MONO + "-fx-background-color: " + color + ";");
            flow.getChildren().add(tag);
        }
        if (to < s.length()) flow.getChildren().add(plain(s.substring(to)));
        HBox.setHgrow(flow, Priority.ALWAYS);
        HBox box = new HBox(8, number, flow);
        boolean inTarget = target.map(t -> t.startIndex() <= line.end() && t.stopIndex() >= line.start()).orElse(false);
        if (inTarget) box.setStyle(TARGET_TINT);
        return box;
    }

    private static javafx.scene.text.Text plain(String s) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setStyle(MONO);
        return t;
    }

    /** The other tags applying to the same object (or to the whole text). */
    private static List<Tag> otherTags(Text text, TagOccurrence o) {
        List<Tag> all = o.target().map(text::tagsOf).orElseGet(text::textTags);
        return all.stream().filter(t -> t != o.tag()).toList();
    }

    static String shorten(String label) {
        if (label == null) return "";
        String one = label.replaceAll("\\s+", " ").strip();
        return one.length() > 40 ? one.substring(0, 39) + "…" : one;
    }
}
