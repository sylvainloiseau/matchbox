package fr.cnrs.lacito.matchbox.gui.mode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Placement of the active mode's menu in the menu bar, and accelerator checks. */
public final class ModeMenus {

    private ModeMenus() {
    }

    /**
     * Replaces the previous mode's menu by the new one, at {@code slot} (the index after the
     * window's Mode menu). The window's menus keep their order.
     */
    public static <M> void replace(List<M> menus, Optional<M> previous, Optional<M> next, int slot) {
        previous.ifPresent(menus::remove);
        next.ifPresent(m -> menus.add(Math.min(slot, menus.size()), m));
    }

    /** The accelerators of a mode menu that the window's menus already use. */
    public static <K> Set<K> collisions(Set<K> window, Set<K> mode) {
        return mode.stream().filter(Objects::nonNull).filter(window::contains).collect(Collectors.toSet());
    }
}
