package fr.cnrs.lacito.matchbox.gui.workbench;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * The message line of the window's status bar, shared by all modes: short messages for the user
 * ("No media segment at the caret", refused edits, assistant progress), optionally with a link
 * (e.g. "Show", "Save all"). A new message replaces the previous one; "" clears it.
 */
public final class StatusLine {

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper warning = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper linkText = new ReadOnlyStringWrapper("");
    private final ReadOnlyObjectWrapper<Runnable> linkAction = new ReadOnlyObjectWrapper<>();

    /** Shows an information message; "" or null clears the line. */
    public void info(String text) {
        set(text, false, "", null);
    }

    /** Shows a message about something that did not work. */
    public void warn(String text) {
        set(text, true, "", null);
    }

    /** Shows a message with a link running {@code action} (the link disappears with the message). */
    public void info(String text, String link, Runnable action) {
        set(text, false, link, Objects.requireNonNull(action, "action"));
    }

    public void clear() {
        info("");
    }

    private void set(String text, boolean warn, String link, Runnable action) {
        message.set(text == null ? "" : text);
        warning.set(warn);
        linkText.set(link == null ? "" : link);
        linkAction.set(action);
    }

    /** Runs the action of the link, if any. */
    public void followLink() {
        Runnable action = linkAction.get();
        if (action != null) {
            action.run();
        }
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty warningProperty() {
        return warning.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty linkTextProperty() {
        return linkText.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Runnable> linkActionProperty() {
        return linkAction.getReadOnlyProperty();
    }
}
