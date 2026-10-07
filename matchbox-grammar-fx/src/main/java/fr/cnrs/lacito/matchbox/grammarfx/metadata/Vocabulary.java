package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Known values of a list field (e.g. the speakers of a project). */
public interface Vocabulary {

    /** Values offered by the "+" button of a list. */
    List<Choice> choices();

    /** The known value equal to {@code value}, if any; a recognised value is shown as a filled badge. */
    default Optional<Choice> lookup(String value) {
        return choices().stream().filter(c -> c.value().equals(value)).findFirst();
    }

    /**
     * @param value       the value written in the text
     * @param label       text of the badge
     * @param description tooltip
     */
    record Choice(String value, String label, String description) {
        public Choice {
            Objects.requireNonNull(value, "value");
            label = label == null || label.isBlank() ? value : label;
            description = description == null ? "" : description;
        }
    }

    /** No known value: every value is free. */
    Vocabulary EMPTY = List::of;

    /** A fixed list of choices. */
    static Vocabulary of(List<Choice> choices) {
        List<Choice> copy = List.copyOf(choices);
        return () -> copy;
    }
}
