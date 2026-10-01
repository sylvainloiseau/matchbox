package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.util.Objects;

/** Base class holding the span and the kind of an immutable corpus object. */
public abstract class AbstractCorpusObject implements CorpusObject {

    private final String kind;
    private final TextCoordinate coordinate;

    protected AbstractCorpusObject(String kind, TextCoordinate coordinate) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
    }

    @Override
    public final String getKind() {
        return kind;
    }

    @Override
    public final TextCoordinate getTextCoordinate() {
        return coordinate;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + kind + " " + coordinate + " " + getLabel() + "]";
    }
}
