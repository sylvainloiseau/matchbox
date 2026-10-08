package fr.cnrs.lacito.matchbox.gui.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.gui.workbench.Focus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

class ModeSwitcherTest {

    private final List<String> calls = new ArrayList<>();

    private final class FakeMode implements Mode {
        final String id;

        FakeMode(String id) {
            this.id = id;
            calls.add("create " + id);
        }

        @Override
        public Node root() {
            return null;
        }

        @Override
        public void activate() {
            calls.add("activate " + id);
        }

        @Override
        public void deactivate() {
            calls.add("deactivate " + id);
        }

        @Override
        public ObservableValue<Optional<Focus>> focusProperty() {
            return new SimpleObjectProperty<>(Optional.empty());
        }
    }

    private ModeDescriptor descriptor(String id) {
        return new ModeDescriptor(id, id, id + " Mode", "", null, w -> null);
    }

    private final ModeSwitcher switcher = new ModeSwitcher(
        List.of(descriptor("a"), descriptor("b")), d -> new FakeMode(d.id()));

    @Test
    void modesAreCreatedOnFirstUseAndKept() {
        assertTrue(switcher.created("a").isEmpty());
        switcher.activate("a");
        Mode a = switcher.get("a");
        switcher.activate("b");
        switcher.activate("a");
        assertSame(a, switcher.get("a"));
        assertEquals(List.of("create a", "activate a", "create b", "deactivate a", "activate b",
            "deactivate b", "activate a"), calls);
    }

    @Test
    void reselectingTheActiveModeDoesNothing() {
        switcher.activate("a");
        assertTrue(switcher.activate("a").isEmpty());
        assertEquals(List.of("create a", "activate a"), calls);
        assertEquals(Optional.of("a"), switcher.activeId());
    }

    @Test
    void aModeCanBeCreatedWithoutBeingActivated() {
        switcher.get("b");
        assertEquals(List.of("create b"), calls);
        assertTrue(switcher.active().isEmpty());
    }

    @Test
    void theBuiltInModesHaveDistinctIdsAndShortcuts() {
        List<ModeDescriptor> modes = Modes.builtIn();
        assertEquals(modes.size(), modes.stream().map(ModeDescriptor::id).distinct().count());
        assertEquals(modes.size(), modes.stream().map(ModeDescriptor::shortcut).distinct().count());
        assertEquals(Modes.FILE, modes.get(0).id());
    }
}
