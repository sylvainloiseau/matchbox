package fr.cnrs.lacito.matchbox.gui.mode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which mode is active and the lifecycle calls, without JavaFX: modes are created on first use,
 * one instance each, and kept; switching deactivates the previous mode before activating the new
 * one; reselecting the active mode does nothing.
 */
public final class ModeSwitcher {

    private final Map<String, ModeDescriptor> descriptors = new LinkedHashMap<>();
    private final Function<ModeDescriptor, Mode> create;
    private final Map<String, Mode> instances = new LinkedHashMap<>();
    private String activeId;

    public ModeSwitcher(List<ModeDescriptor> descriptors, Function<ModeDescriptor, Mode> create) {
        for (ModeDescriptor d : descriptors) {
            if (this.descriptors.put(d.id(), d) != null) {
                throw new IllegalArgumentException("Two modes with the id " + d.id());
            }
        }
        this.create = create;
    }

    public List<ModeDescriptor> descriptors() {
        return List.copyOf(descriptors.values());
    }

    public Optional<ModeDescriptor> descriptor(String id) {
        return Optional.ofNullable(descriptors.get(id));
    }

    /** The mode, created if needed (not activated). */
    public Mode get(String id) {
        ModeDescriptor d = descriptors.get(id);
        if (d == null) {
            throw new IllegalArgumentException("No mode " + id);
        }
        return instances.computeIfAbsent(id, k -> create.apply(d));
    }

    /** The mode, if it was created. */
    public Optional<Mode> created(String id) {
        return Optional.ofNullable(instances.get(id));
    }

    /**
     * Makes a mode the active one.
     *
     * @return the previous active mode if it changed; empty if {@code id} was already active
     */
    public Optional<Optional<Mode>> activate(String id) {
        if (id.equals(activeId)) {
            return Optional.empty();
        }
        Mode next = get(id);
        Optional<Mode> previous = active();
        previous.ifPresent(Mode::deactivate);
        activeId = id;
        next.activate();
        return Optional.of(previous);
    }

    public Optional<String> activeId() {
        return Optional.ofNullable(activeId);
    }

    public Optional<Mode> active() {
        return activeId == null ? Optional.empty() : Optional.of(instances.get(activeId));
    }
}
