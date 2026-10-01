package fr.cnrs.lacito.matchbox.gui.view;

/** Display helpers for object kinds ({@code "<grammarId>:<name>"}). */
public final class Kinds {

    private Kinds() {}

    /** {@code "notebook-grammar:lexical-entry"} → {@code "Lexical entry"}. */
    public static String title(String kind) {
        String name = kind.substring(kind.indexOf(':') + 1).replace('-', ' ').replace('_', ' ');
        return name.isEmpty() ? kind : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
