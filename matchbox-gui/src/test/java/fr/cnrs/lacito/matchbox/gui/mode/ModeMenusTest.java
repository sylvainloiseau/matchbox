package fr.cnrs.lacito.matchbox.gui.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModeMenusTest {

    @Test
    void theModeMenuTakesThePlaceOfThePreviousOne() {
        List<String> menus = new ArrayList<>(List.of("File", "Edit", "Mode", "Assistant"));
        ModeMenus.replace(menus, Optional.empty(), Optional.of("File Mode"), 3);
        assertEquals(List.of("File", "Edit", "Mode", "File Mode", "Assistant"), menus);
        ModeMenus.replace(menus, Optional.of("File Mode"), Optional.of("Tag Mode"), 3);
        assertEquals(List.of("File", "Edit", "Mode", "Tag Mode", "Assistant"), menus);
        ModeMenus.replace(menus, Optional.of("Tag Mode"), Optional.empty(), 3);
        assertEquals(List.of("File", "Edit", "Mode", "Assistant"), menus);
    }

    @Test
    void collisionsAreTheModeAcceleratorsUsedByTheWindow() {
        assertEquals(Set.of("Ctrl+S"), ModeMenus.collisions(Set.of("Ctrl+S", "Ctrl+Q"), Set.of("Ctrl+S", "Ctrl+F")));
    }
}
