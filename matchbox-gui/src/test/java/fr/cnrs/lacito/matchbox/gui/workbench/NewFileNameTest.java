package fr.cnrs.lacito.matchbox.gui.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewFileNameTest {

    @TempDir
    Path dir;

    @Test
    void theBaseNameWhenFree() {
        assertEquals("untitled.txt", NewFileName.firstFree(dir, NewFileName.DEFAULT));
        assertEquals("untitled.txt", NewFileName.firstFree(null, NewFileName.DEFAULT));
    }

    @Test
    void theFirstFreeNumberedName() throws Exception {
        Files.writeString(dir.resolve("untitled.txt"), "");
        assertEquals("untitled-2.txt", NewFileName.firstFree(dir, NewFileName.DEFAULT));
        Files.writeString(dir.resolve("untitled-2.txt"), "");
        assertEquals("untitled-3.txt", NewFileName.firstFree(dir, NewFileName.DEFAULT));
    }

    @Test
    void aNameWithoutExtension() throws Exception {
        Files.writeString(dir.resolve("notes"), "");
        assertEquals("notes-2", NewFileName.firstFree(dir, "notes"));
    }
}
