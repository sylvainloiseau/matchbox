package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Example;
import java.util.List;

public final class SimpleExample extends AbstractCorpusObject implements Example, HasFields {

    private final String transcription;
    private final String translation;
    private final List<Field> fields;

    public SimpleExample(String kind, TextCoordinate coordinate, String transcription, String translation) {
        this(kind, coordinate, transcription, translation, List.of());
    }

    /**
     * @param fields spans of the transcription and the translation ({@link #TRANSCRIPTION},
     *               {@link #TRANSLATION}), making them editable; empty: read-only
     */
    public SimpleExample(String kind, TextCoordinate coordinate, String transcription, String translation,
                         List<Field> fields) {
        super(kind, coordinate);
        this.transcription = transcription;
        this.translation = translation;
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getTranscription() {
        return transcription;
    }

    @Override
    public String getTranslation() {
        return translation;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
