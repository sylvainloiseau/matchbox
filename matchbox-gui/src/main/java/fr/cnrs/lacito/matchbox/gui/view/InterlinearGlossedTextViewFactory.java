package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearResolver;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.corpus.igt.MetadataValue;
import fr.cnrs.lacito.matchbox.grammarfx.EditOutcome;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.grammarfx.igt.InterlinearGrid;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataPane;
import fr.cnrs.lacito.matchbox.grammarfx.tags.TagBadges;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Built-in view of interlinear glossed texts, whatever their notation (plan
 * {@code IGT_PLUGIN_PLAN.md} §2.2): header, metadata (own and inherited, with their source), the
 * orthographic lines, the words in a grid ({@link InterlinearGrid}), the translations. Every change
 * is a field edit or text edits asked to the {@link ViewContext}.
 */
public class InterlinearGlossedTextViewFactory implements ObjectViewFactory<InterlinearGlossedText> {

    private static final String STATE = InterlinearGlossedTextViewFactory.class.getName();

    /** The view's parts, refreshed in place after each parse. */
    private static final class State {
        InterlinearGlossedText igt;
        final Label title = new Label();
        final Hyperlink position = new Hyperlink();
        final VBox tags = new VBox();
        MetadataPane metadata;
        final FlowPane inherited = new FlowPane(6, 2);
        final VBox orthographic = new VBox(4);
        InterlinearGrid grid;
        final VBox translations = new VBox(4);
    }

    @Override
    public Class<InterlinearGlossedText> getType() {
        return InterlinearGlossedText.class;
    }

    @Override
    public List<ObjectColumn<InterlinearGlossedText>> getColumns(ViewContext context) {
        return List.of(
            new ObjectColumn<>("Id", igt -> igt.metadata().getOrDefault("id", "")),
            ObjectColumn.editable("Transcription", InterlinearSentence.TRANSCRIPTION, InterlinearSentence::getTranscription),
            ObjectColumn.editable("Glosses", InterlinearSentence.MORPHEMES, InterlinearSentence::getMorphemes),
            ObjectColumn.editable("Translation", InterlinearSentence.FREE_TRANSLATION, InterlinearSentence::getFreeTranslation),
            new ObjectColumn<>("Words", igt -> String.valueOf(reading(igt, context).lines().stream()
                .filter(LineReading::isSegmented).mapToInt(l -> l.words().size()).max().orElse(0))),
            new ObjectColumn<>("Issues", igt -> {
                int n = reading(igt, context).issues().size();
                return n == 0 ? "" : String.valueOf(n);
            }));
    }

    /** The reading of an IGT in its text, else alone. */
    static InterlinearReading reading(InterlinearGlossedText igt, ViewContext context) {
        Optional<Text> text = context.corpus().textOf(igt);
        return text.flatMap(t -> InterlinearReadings.of(t).of(igt)).orElseGet(() -> InterlinearResolver.withoutContext(igt));
    }

    @Override
    public Node createView(InterlinearGlossedText igt, ViewContext context) {
        State state = new State();
        state.igt = igt;
        InterlinearReading reading = reading(igt, context);
        state.title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        state.position.setOnAction(e -> context.selectInEditor(state.igt.getTextCoordinate()));
        HBox header = new HBox(10, state.title, state.position);

        state.metadata = new MetadataPane(igt, igt.metadata(), inherited(reading), InterlinearGlossedText::metadataField, context);
        state.grid = new InterlinearGrid(context);
        state.grid.setId("igtGridView");

        VBox root = new VBox(8, header, state.tags, section("Metadata"), state.metadata, state.inherited,
            state.orthographic, section("Words"), state.grid, state.translations);
        root.setStyle("-fx-padding: 8;");
        root.setId("igtView");
        root.getProperties().put(STATE, state);
        refresh(state, igt, reading, context);
        return root;
    }

    @Override
    public boolean updateView(Node view, InterlinearGlossedText igt, ViewContext context) {
        if (!(view.getProperties().get(STATE) instanceof State state)) return false;
        state.igt = igt;
        InterlinearReading reading = reading(igt, context);
        state.metadata.update(igt, igt.metadata(), inherited(reading));
        refresh(state, igt, reading, context);
        return true;
    }

    private static void refresh(State state, InterlinearGlossedText igt, InterlinearReading reading, ViewContext context) {
        state.title.setText("IGT " + igt.getLabel());
        state.position.setText(GenericObjectViewFactory.position(igt.getTextCoordinate()));
        state.tags.getChildren().setAll(TagBadges.of(igt, context).map(List::of).orElse(List.of()));
        state.inherited.getChildren().clear();
        reading.metadata().forEach((key, value) -> {
            if (!value.isInherited()) return;
            Hyperlink link = new Hyperlink(key + " = " + value.value() + " — " + source(value));
            link.setTooltip(new Tooltip("Inherited: click to show where it is written"));
            value.origin().ifPresent(o -> link.setOnAction(e -> context.selectInEditor(o.getTextCoordinate())));
            state.inherited.getChildren().add(link);
        });
        lines(state.orthographic, "Orthographic transcription", reading, LineRole.ORTHOGRAPHIC, igt, context);
        state.grid.show(reading);
        lines(state.translations, "Translations", reading, LineRole.TRANSLATION, igt, context);
    }

    private static String source(MetadataValue value) {
        String origin = value.origin().map(o -> o.getLabel() + " (line " + o.getTextCoordinate().startLine() + ")").orElse("");
        return switch (value.source()) {
            case PREVIOUS_IGT -> "from IGT " + origin;
            case SECTION -> "from section " + origin;
            case HEADER -> "from the file header";
            case OWN -> "";
        };
    }

    /** The values the IGT inherits, for the metadata pane (greyed). */
    private static Map<String, String> inherited(InterlinearReading reading) {
        Map<String, String> map = new LinkedHashMap<>();
        reading.metadata().forEach((k, v) -> {
            if (v.isInherited()) map.put(k, v.value());
        });
        return map;
    }

    /** The field of a whole line: the first translation is the free translation of the sentence. */
    private static String fieldOf(InterlinearGlossedText igt, LineReading l, List<LineReading> lines) {
        if (l.role() == LineRole.TRANSLATION && l == lines.get(0) && igt instanceof fr.cnrs.lacito.matchbox.corpus.HasFields f
            && f.field(InterlinearSentence.FREE_TRANSLATION).isPresent()) {
            return InterlinearSentence.FREE_TRANSLATION;
        }
        return InterlinearGlossedText.lineField(l.index());
    }

    private static Label section(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        return label;
    }

    /** Whole-line text fields of the lines of a role (orthographic lines, translations). */
    private static void lines(VBox box, String title, InterlinearReading reading, LineRole role, InterlinearGlossedText igt,
                              ViewContext context) {
        List<LineReading> lines = reading.lines(role);
        boolean focused = box.getChildren().stream().anyMatch(n -> n instanceof HBox h
            && h.getChildren().stream().anyMatch(Node::isFocused));
        if (focused) return; // keep what is being typed
        box.getChildren().clear();
        if (lines.isEmpty()) return;
        box.getChildren().add(section(title));
        boolean editable = context.canEdit(igt);
        for (LineReading l : lines) {
            Label name = new Label((role == LineRole.ORTHOGRAPHIC ? "O" : l.name().orElse("free"))
                + l.language().map(v -> " · " + v).orElse(""));
            name.setMinWidth(70);
            name.setTooltip(new Tooltip(l.reason()));
            TextField field = new TextField(l.line().text());
            field.setEditable(editable);
            field.setId(role == LineRole.ORTHOGRAPHIC ? "igtOrthographic" : "igtTranslation");
            HBox.setHgrow(field, Priority.ALWAYS);
            String original = l.line().text();
            field.setOnAction(e -> {
                EditOutcome outcome = context.setField(igt, fieldOf(igt, l, lines), field.getText());
                field.setStyle(outcome.isApplied() ? "" : "-fx-border-color: #d33;");
                field.setTooltip(outcome.isApplied() ? null : new Tooltip(outcome.message()));
            });
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    field.setText(original);
                    field.setStyle("");
                }
            });
            box.getChildren().add(new HBox(8, name, field));
        }
    }
}
