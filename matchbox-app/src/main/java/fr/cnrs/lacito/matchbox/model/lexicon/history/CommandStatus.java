package fr.cnrs.lacito.matchbox.model.lexicon.history;

/** What became of a command when the history was computed. */
public enum CommandStatus {
    /** It ran, in a file that was committed. */
    APPLIED("Applied"),
    /** It ran in a committed file and changed nothing (an ensure, a "make sure" that found the entry). */
    NO_CHANGE("No change"),
    /** It has its own error (syntax, static or dynamic). */
    ERROR("Error"),
    /** It ran, but a later command of its file failed: the file was rolled back. */
    ROLLED_BACK("Rolled back"),
    /** It comes after the first dynamic error of the project: not run. */
    NOT_APPLIED("Not applied"),
    /** The project has syntax or static errors: no command is run. */
    NOT_TRIED("Not tried"),
    /** The history is being computed. */
    PENDING("Pending");

    private final String label;

    CommandStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** How bad it is, to give a block the worst status of its commands. */
    int severity() {
        return switch (this) {
            case NO_CHANGE -> 0;
            case APPLIED -> 1;
            case PENDING -> 2;
            case NOT_TRIED -> 3;
            case NOT_APPLIED -> 4;
            case ROLLED_BACK -> 5;
            case ERROR -> 6;
        };
    }

    /** The worse of two statuses. */
    public CommandStatus worst(CommandStatus other) {
        return other.severity() > severity() ? other : this;
    }
}
