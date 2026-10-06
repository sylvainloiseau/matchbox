package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectColumn;
import fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import fr.cnrs.lacito.matchbox.gui.mode.common.lexicon.LexicalEntryController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

/** Built-in view of lexical entries, with their senses and examples. */
public class LexicalEntryViewFactory implements ObjectViewFactory<LexicalEntry> {

    @Override
    public Class<LexicalEntry> getType() {
        return LexicalEntry.class;
    }

    @Override
    public List<ObjectColumn<LexicalEntry>> getColumns() {
        return List.of(
            ObjectColumn.editable("Headword", LexicalEntry.HEADWORD, LexicalEntry::getHeadword),
            new ObjectColumn<>("Senses", e -> e.getSenses().stream()
                .map(s -> s.getDefinition().strip())
                .collect(Collectors.joining(" ; ")))
        );
    }

    @Override
    public Node createView(LexicalEntry entry, ViewContext context) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/fr/cnrs/lacito/matchbox/gui/mode/common/lexicon/LexicalEntryView.fxml"
            ));
            loader.setController(new LexicalEntryController(entry));
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
