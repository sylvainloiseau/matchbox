package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The metadata of an IGT in its context. A key the IGT does not give is looked up, nearest first,
 * in the previous IGTs of the text (nearest first, back to the start of the text), then in the
 * enclosing sections (innermost first), then in the file header. Only {@link #INHERITED} keys are
 * inherited: {@code id}, {@code title}, {@code offset}, {@code end} and any other key belong to one
 * IGT. Pure.
 */
public final class InterlinearInheritance {

    public static final String SETTING = "setting";
    public static final String OLANG = "olang";
    public static final String MLANG = "mlang";
    public static final String FORM = "form";
    public static final String GLOSS = "gloss";
    public static final String MEDIA = "media";
    public static final String OFFSET = "offset";
    public static final String END = "end";
    public static final String ID = "id";
    public static final String TITLE = "title";

    /** The keys an IGT inherits from its context. */
    public static final Set<String> INHERITED = Set.of(SETTING, OLANG, MLANG, FORM, GLOSS, MEDIA);

    private InterlinearInheritance() {}

    /**
     * @param own      the IGT's own metadata
     * @param previous the inherited keys written in the previous IGTs, nearest value per key
     * @param sections the enclosing sections, outermost first
     * @param header   the properties of the file header
     */
    public static Map<String, MetadataValue> effective(Map<String, String> own, Map<String, MetadataValue> previous,
                                                       List<Section> sections, Map<String, String> header,
                                                       Optional<CorpusObject> headerObject) {
        Map<String, MetadataValue> result = new LinkedHashMap<>();
        own.forEach((k, v) -> result.put(k, new MetadataValue(v, MetadataValue.Source.OWN, Optional.empty())));
        for (String key : INHERITED.stream().sorted().toList()) {
            if (result.containsKey(key)) continue;
            MetadataValue value = previous.get(key);
            for (int i = sections.size() - 1; value == null && i >= 0; i--) {
                String v = sections.get(i).getMetadata().get(key);
                if (v != null) value = new MetadataValue(v, MetadataValue.Source.SECTION, Optional.of(sections.get(i)));
            }
            if (value == null && header.containsKey(key)) {
                value = new MetadataValue(header.get(key), MetadataValue.Source.HEADER, headerObject);
            }
            if (value != null) result.put(key, value);
        }
        return result;
    }

    /** The inherited keys of an IGT's own metadata, added to those of the previous IGTs (nearest wins). */
    public static Map<String, MetadataValue> remember(Map<String, MetadataValue> previous, Map<String, String> own,
                                                      CorpusObject igt) {
        Map<String, MetadataValue> next = new LinkedHashMap<>(previous);
        own.forEach((k, v) -> {
            if (INHERITED.contains(k)) next.put(k, new MetadataValue(v, MetadataValue.Source.PREVIOUS_IGT, Optional.of(igt)));
        });
        return next;
    }
}
