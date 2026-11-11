package fr.cnrs.lacito.matchbox.model.lexicon;

import java.util.List;
import java.util.Set;

/** A dictionary as the application uses it: entries with forms and the glosses of their senses. */
public interface Lexicon {

    /** A name for the user (the file name). */
    String name();

    Set<String> objectLanguages();

    Set<String> metaLanguages();

    List<LexiconEntry> entries();
}
