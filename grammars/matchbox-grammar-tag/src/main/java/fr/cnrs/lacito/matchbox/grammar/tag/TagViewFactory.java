package fr.cnrs.lacito.matchbox.grammar.tag;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Tags in the objects table: Tag (editable: renames this occurrence), Path, Applies to. The detail
 * pane is empty: the table says it all.
 */
public class TagViewFactory implements ObjectViewFactory<Tag> {

    @Override
    public Class<Tag> getType() {
        return Tag.class;
    }

    @Override
    public List<ObjectColumn<Tag>> getColumns() {
        return List.of(
            ObjectColumn.editable("Tag", Tag.TAG, Tag::getName),
            new ObjectColumn<>("Path", t -> String.join(" › ", t.getPath())));
    }

    @Override
    public List<ObjectColumn<Tag>> getColumns(ViewContext context) {
        return List.of(
            ObjectColumn.editable("Tag", Tag.TAG, Tag::getName),
            new ObjectColumn<>("Path", t -> String.join(" › ", t.getPath())),
            new ObjectColumn<>("Applies to", t -> appliesTo(t, context)));
    }

    /** "IGT: a mio-jaflo…", "Section: Session 1", or "(text)". */
    static String appliesTo(Tag tag, ViewContext context) {
        return context.corpus().textOf(tag)
            .map(text -> text.targetOf(tag).map(TagViewFactory::describe).orElse("(text)"))
            .orElse("");
    }

    private static String describe(CorpusObject o) {
        String kind = o.getKind().substring(o.getKind().indexOf(':') + 1).replace('-', ' ');
        String label = Optional.ofNullable(o.getLabel()).orElse("");
        return kind + ": " + (label.length() > 60 ? label.substring(0, 60) + "…" : label);
    }

    @Override
    public Node createView(Tag tag, ViewContext context) {
        return new Pane();
    }
}
