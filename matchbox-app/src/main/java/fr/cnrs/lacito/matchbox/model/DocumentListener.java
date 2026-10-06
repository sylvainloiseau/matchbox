package fr.cnrs.lacito.matchbox.model;

/** Notified after each change of a {@link TextDocument}. */
@FunctionalInterface
public interface DocumentListener {

    void changed(DocumentChange change);
}
