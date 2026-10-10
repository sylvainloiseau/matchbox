package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The kinds of objects of a grammar that can be given a display style: those the grammar declares,
 * those found in the corpus, and those already styled in the project (e.g. a kind of an older
 * version of the grammar), sorted.
 */
public final class KindCatalog {

    private KindCatalog() {
    }

    public static List<String> kinds(String grammarId, Collection<String> declared, CorpusView corpus,
                                     DisplayStyles styles) {
        TreeSet<String> kinds = new TreeSet<>();
        if (declared != null) declared.stream().filter(k -> k != null && !k.isBlank()).forEach(kinds::add);
        if (corpus != null) {
            for (Text text : corpus.texts()) {
                text.objects(grammarId).stream().flatMap(KindCatalog::withChildren)
                    .map(CorpusObject::getKind).forEach(kinds::add);
            }
        }
        styles.asMap().keySet().stream()
            .filter(k -> k.grammarId().equals(grammarId) && k.kind() != null)
            .forEach(k -> kinds.add(k.kind()));
        return List.copyOf(kinds);
    }

    private static Stream<CorpusObject> withChildren(CorpusObject o) {
        return Stream.concat(Stream.of(o), o.getChildren().stream().flatMap(KindCatalog::withChildren));
    }

    /** {@code "igt"} for {@code "notebook-grammar:igt"}. */
    public static String shortName(String kind) {
        int colon = kind.indexOf(':');
        return colon < 0 ? kind : kind.substring(colon + 1);
    }
}
