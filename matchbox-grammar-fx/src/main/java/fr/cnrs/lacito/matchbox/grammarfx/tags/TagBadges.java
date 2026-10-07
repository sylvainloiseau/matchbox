package fr.cnrs.lacito.matchbox.grammarfx.tags;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;

/**
 * The tags applying to an object (see {@code Text.targetOf}), as a line of badges
 * ({@code verb › infinitive}); clicking a badge selects the tag in the editor. Shared by the views.
 */
public final class TagBadges {

    private static final String BADGE = "-fx-background-color: #ffd6e8; -fx-background-radius: 10; -fx-padding: 1 6 1 6;";

    private TagBadges() {}

    /** Badges of the tags applying to an object; empty if it has none. */
    public static Optional<Node> of(CorpusObject object, ViewContext context) {
        return context.corpus().textOf(object).flatMap(text -> line("Tags:", text.tagsOf(object), context));
    }

    /** Badges of the tags applying to the whole text; empty if it has none. */
    public static Optional<Node> ofText(Text text, ViewContext context) {
        return line("Tags of the text:", text.textTags(), context);
    }

    private static Optional<Node> line(String title, List<Tag> tags, ViewContext context) {
        if (tags.isEmpty()) return Optional.empty();
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        FlowPane pane = new FlowPane(4, 4, label);
        pane.setAlignment(Pos.CENTER_LEFT);
        for (Tag tag : tags) {
            Label badge = new Label(String.join(" › ", tag.getPath().isEmpty() ? List.of(tag.getName()) : tag.getPath()));
            badge.setStyle(BADGE);
            badge.setTooltip(new Tooltip("#\"" + tag.getName() + "\" — click to show it in the text"));
            badge.setOnMouseClicked(e -> context.selectInEditor(tag.getTextCoordinate()));
            pane.getChildren().add(badge);
        }
        return Optional.of(pane);
    }
}
