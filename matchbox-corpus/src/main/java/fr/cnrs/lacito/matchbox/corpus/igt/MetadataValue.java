package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.Optional;

/**
 * A metadata value of an IGT, and where it comes from (see {@link InterlinearInheritance}).
 *
 * @param value  the value
 * @param source where it is written
 * @param origin the object it is written in (the previous IGT, the section, the header); empty for its own value
 */
public record MetadataValue(String value, Source source, Optional<CorpusObject> origin) {

    public enum Source { OWN, PREVIOUS_IGT, SECTION, HEADER }

    public boolean isInherited() {
        return source != Source.OWN;
    }
}
