package fr.cnrs.lacito.matchbox.grammar.liftpatchbox;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;

/** The version pragma of a LiftPatch script ({@code %liftpatch 1.1 …}): shown and styled, never applied. */
public final class LiftPatchPragma extends AbstractCorpusObject {

    private final String version;

    public LiftPatchPragma(String kind, TextCoordinate coordinate, String version) {
        super(kind, coordinate);
        this.version = version;
    }

    /** The language version the script declares ("1.1"). */
    public String version() {
        return version;
    }

    @Override
    public String getLabel() {
        return "%liftpatch " + version;
    }
}
