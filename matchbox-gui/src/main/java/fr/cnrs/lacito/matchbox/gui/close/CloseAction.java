package fr.cnrs.lacito.matchbox.gui.close;

/**
 * What is about to happen to the open project, for the wording of the close dialog.
 *
 * @param gerund in a sentence: "Save them before closing the project?"
 * @param verb   on the buttons: "Save selected and close", "Close without saving"
 */
public record CloseAction(String gerund, String verb) {

    public static final CloseAction CLOSE = new CloseAction("closing the project", "close");
    public static final CloseAction QUIT = new CloseAction("quitting", "quit");
    public static final CloseAction OPEN = new CloseAction("opening another project", "continue");
    public static final CloseAction NEW = new CloseAction("creating a new project", "continue");
}
