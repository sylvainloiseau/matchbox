package fr.cnrs.lacito.matchbox.gui.lexicon;

import fr.cnrs.lacito.matchbox.model.lexicon.history.CommandStatus;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/** A coloured label for what became of a command (Applied, Error, Rolled back…). */
public final class StatusBadge extends Label {

    public StatusBadge(CommandStatus status, String tooltip) {
        super(symbol(status) + " " + status.label());
        setStyle("-fx-padding: 1 7 1 7; -fx-background-radius: 8; -fx-font-size: 0.92em; -fx-text-fill: white; "
            + "-fx-background-color: " + color(status) + ";");
        setAccessibleText("Status: " + status.label() + (tooltip == null || tooltip.isBlank() ? "" : ". " + tooltip));
        if (tooltip != null && !tooltip.isBlank()) {
            setTooltip(new Tooltip(tooltip));
        }
    }

    /** The colour of a status, for badges. */
    public static String color(CommandStatus status) {
        return switch (status) {
            case APPLIED -> "#2e7d32";
            case NO_CHANGE -> "#1565c0";
            case ERROR -> "#c62828";
            case ROLLED_BACK -> "#e09000";
            case NOT_APPLIED, NOT_TRIED -> "#757575";
            case PENDING -> "#9e9e9e";
        };
    }

    /** A symbol of a status, for the plain text of tables. */
    public static String symbol(CommandStatus status) {
        return switch (status) {
            case APPLIED, NO_CHANGE, ERROR -> "●";
            case ROLLED_BACK -> "◐";
            case NOT_APPLIED, NOT_TRIED, PENDING -> "○";
        };
    }
}
