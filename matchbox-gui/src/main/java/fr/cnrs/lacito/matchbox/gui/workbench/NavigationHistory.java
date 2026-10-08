package fr.cnrs.lacito.matchbox.gui.workbench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * Back/Forward history of the jumps made in the window (show in editor, switching mode…), like a
 * web browser: a jump pushes the place left on the back stack and clears the forward stack.
 * Ordinary selections inside a mode are not recorded. Bounded to {@value #MAX} places.
 *
 * @param <L> a place (in the application: a mode and its focus)
 */
public final class NavigationHistory<L> {

    static final int MAX = 50;

    private final Deque<L> back = new ArrayDeque<>();
    private final Deque<L> forward = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canGoBack = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward = new ReadOnlyBooleanWrapper(false);

    /** Records a jump away from {@code left}. */
    public void jumped(L left) {
        Objects.requireNonNull(left);
        if (!left.equals(back.peek())) {
            back.push(left);
            while (back.size() > MAX) back.removeLast();
        }
        forward.clear();
        update();
    }

    /** Goes back from {@code current}: the place to show, if any. */
    public Optional<L> back(L current) {
        L target = back.poll();
        if (target == null) return Optional.empty();
        forward.push(Objects.requireNonNull(current));
        update();
        return Optional.of(target);
    }

    /** Goes forward from {@code current}: the place to show, if any. */
    public Optional<L> forward(L current) {
        L target = forward.poll();
        if (target == null) return Optional.empty();
        back.push(Objects.requireNonNull(current));
        update();
        return Optional.of(target);
    }

    /** Updates every place (e.g. after a re-parse); a place mapped to empty is dropped. */
    public void replaceAll(Function<L, Optional<L>> mapping) {
        replace(back, mapping);
        replace(forward, mapping);
        update();
    }

    private void replace(Deque<L> places, Function<L, Optional<L>> mapping) {
        List<L> kept = new ArrayList<>();
        for (L place : places) {
            mapping.apply(place).ifPresent(kept::add);
        }
        places.clear();
        places.addAll(kept);
    }

    public void clear() {
        back.clear();
        forward.clear();
        update();
    }

    private void update() {
        canGoBack.set(!back.isEmpty());
        canGoForward.set(!forward.isEmpty());
    }

    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    int backSize() {
        return back.size();
    }
}
