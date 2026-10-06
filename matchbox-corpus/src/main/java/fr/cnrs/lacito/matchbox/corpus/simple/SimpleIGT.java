package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import java.util.List;

/** A three-line group: transcription, morphemes, free translation. */
public final class SimpleIGT extends AbstractCorpusObject implements InterlinearSentence, HasFields {

    private final String transcription;
    private final String morphemes;
    private final String freeTranslation;
    private final List<Field> fields;

    public SimpleIGT(String kind, TextCoordinate coordinate,
        String transcription,
        String morphemes,
        String freeTranslation
    ) {
        this(kind, coordinate, transcription, morphemes, freeTranslation, List.of());
    }

    /**
     * @param fields spans of the lines ({@link #TRANSCRIPTION}, {@link #MORPHEMES},
     *               {@link #FREE_TRANSLATION}), making them editable; empty: read-only
     */
    public SimpleIGT(String kind, TextCoordinate coordinate,
        String transcription,
        String morphemes,
        String freeTranslation,
        List<Field> fields
    ) {
        super(kind, coordinate);
        this.transcription = transcription;
        this.morphemes = morphemes;
        this.freeTranslation = freeTranslation;
        this.fields = List.copyOf(fields);
    }

    @Override
    public String getTranscription() {
        return transcription;
    }

    @Override
    public String getMorphemes() {
        return morphemes;
    }

    @Override
    public String getFreeTranslation() {
        return freeTranslation;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }
}
