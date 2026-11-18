package fr.cnrs.lacito.matchbox.gui.properties;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * Settings of the corpus as a whole: inherited context, file order, gloss check. Each is a group of
 * its own: a new corpus setting adds a group.
 */
final class CorpusPage implements PropertiesPage {

    private final ScrollPane content;
    private final GlossCheckGroup glossCheck;

    CorpusPage(PageContext ctx) {
        glossCheck = new GlossCheckGroup(ctx);
        InheritedContextGroup context = new InheritedContextGroup(ctx, glossCheck::updateDictionaryHint);
        FileOrderGroup fileOrder = new FileOrderGroup(ctx);
        content = new ScrollPane(DialogParts.page(id(),
            context.node(), DialogParts.separator(),
            fileOrder.node(), DialogParts.separator(),
            glossCheck.node()));
        content.setFitToWidth(true);
        content.setId("corpusPageScroll");
    }

    @Override
    public ProjectPropertiesPage id() {
        return ProjectPropertiesPage.CORPUS;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void shown() {
        glossCheck.updateDictionaryHint();
    }
}
