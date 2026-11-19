package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.corpus.igt.MetadataValue;
import java.util.List;
import java.util.Optional;

/**
 * The value an object inherits for a key it does not write: for an IGT, what its reading inherits
 * (previous IGT, section, header: {@link InterlinearReadings}); for any object, the metadata of the
 * innermost enclosing section that has the key, else the text's header. No JavaFX.
 */
public final class InheritedValues {

    /** An inherited value and where it is written. */
    public record Inherited(String value, String source, Optional<CorpusObject> origin) {}

    private InheritedValues() {
    }

    public static Optional<Inherited> of(Text text, MetadataRow row, String key) {
        if (row.owner().isEmpty() || row.value(key).isPresent()) return Optional.empty();
        CorpusObject owner = row.owner().get();
        if (owner instanceof TextHeader) return Optional.empty();
        if (owner instanceof InterlinearGlossedText igt) {
            Optional<MetadataValue> v = InterlinearReadings.of(text).of(igt)
                .flatMap(r -> Optional.ofNullable(r.metadata().get(key))).filter(MetadataValue::isInherited);
            if (v.isPresent()) {
                return Optional.of(new Inherited(v.get().value(), source(v.get().source(), v.get().origin()), v.get().origin()));
            }
        }
        List<Section> sections = text.enclosingSections(owner);
        for (int i = sections.size() - 1; i >= 0; i--) {
            String value = sections.get(i).getMetadata().get(key);
            if (value != null) {
                return Optional.of(new Inherited(value, "from section “" + sections.get(i).getLabel() + "”, line "
                    + sections.get(i).getTextCoordinate().startLine(), Optional.of(sections.get(i))));
            }
        }
        String value = text.properties().get(key);
        if (value != null) {
            Optional<CorpusObject> header = text.flatten().filter(TextHeader.class::isInstance).findFirst();
            return Optional.of(new Inherited(value, "from the header", header));
        }
        return Optional.empty();
    }

    private static String source(MetadataValue.Source source, Optional<CorpusObject> origin) {
        String where = origin.map(o -> ", line " + o.getTextCoordinate().startLine()).orElse("");
        return switch (source) {
            case PREVIOUS_IGT -> "from IGT " + origin.map(CorpusObject::getLabel).orElse("above") + where;
            case SECTION -> "from section “" + origin.map(CorpusObject::getLabel).orElse("") + "”" + where;
            case HEADER -> "from the header";
            case OWN -> "";
        };
    }
}
