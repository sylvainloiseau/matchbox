package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An IGT as written: its own metadata, its lines (children), and other children of its grammar
 * (e.g. the span of its metadata block, for its style in the editor).
 */
public final class SimpleInterlinearGlossedText extends AbstractCorpusObject implements InterlinearGlossedText, HasFields {

    private final Map<String, String> metadata;
    private final List<InterlinearLine> lines;
    private final List<CorpusObject> children;
    private final List<Field> fields;
    private volatile InterlinearReading alone;

    /**
     * @param other children that are not lines (e.g. the metadata block), drawn in the editor
     */
    public SimpleInterlinearGlossedText(String kind, TextCoordinate coordinate, Map<String, String> metadata,
                                        List<? extends InterlinearLine> lines, List<? extends CorpusObject> other,
                                        List<Field> fields) {
        super(kind, coordinate);
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.lines = List.copyOf(lines);
        List<CorpusObject> all = new ArrayList<>(other);
        all.addAll(lines);
        all.sort(java.util.Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()));
        this.children = List.copyOf(all);
        this.fields = List.copyOf(fields);
    }

    @Override
    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public List<InterlinearLine> lines() {
        return lines;
    }

    @Override
    public List<CorpusObject> getChildren() {
        return children;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }

    /** The reading without context, for the {@code InterlinearSentence} getters. */
    private InterlinearReading alone() {
        InterlinearReading r = alone;
        if (r == null) alone = r = InterlinearResolver.withoutContext(this);
        return r;
    }

    @Override
    public String getTranscription() {
        return alone().transcription();
    }

    @Override
    public String getMorphemes() {
        return alone().gloss();
    }

    @Override
    public String getFreeTranslation() {
        return alone().translation();
    }

    @Override
    public String getLabel() {
        String id = metadata.getOrDefault("id", "");
        String title = metadata.getOrDefault("title", "");
        if (!id.isEmpty() || !title.isEmpty()) {
            return (id + (id.isEmpty() || title.isEmpty() ? "" : " — ") + title);
        }
        String t = getTranscription();
        return t.isEmpty() ? "IGT" : t;
    }
}
