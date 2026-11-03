package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The order of files is the same on every system (Path.compareTo is not: case on Windows). */
class FileOrderTest {

    private static final String E_NFC = "été.txt";   // "été.txt" as Linux and Windows write it
    private static final String E_NFD = "été.txt"; // the same name as macOS may give it

    private static List<String> sortedNames(String... names) {
        List<String> list = new ArrayList<>(List.of(names));
        list.sort(FileOrder.NAMES);
        return list;
    }

    @Test
    void namesIgnoreCaseFirst() {
        assertEquals(List.of("a.txt", "B.TXT", "c.md"), sortedNames("B.TXT", "c.md", "a.txt"));
    }

    @Test
    void namesDifferingOnlyByCaseHaveAFixedOrder() {
        // both can exist on Linux; upper case first, by character code
        assertEquals(List.of("A.txt", "a.txt", "b.txt"), sortedNames("a.txt", "b.txt", "A.txt"));
    }

    @Test
    void unicodeFormsAreComparedAsTheSameText() {
        assertNotEquals(0, FileOrder.NAMES.compare(E_NFC, E_NFD), "total: different strings");
        assertEquals(sortedNames("f.txt", E_NFC, "e.txt").indexOf(E_NFC),
            sortedNames("f.txt", E_NFD, "e.txt").indexOf(E_NFD), "same place in either form");
    }

    @Test
    void pathsAreComparedElementByElement() {
        // as strings, "a-b.txt" < "a/b.txt" ('-' < '/'); as paths, the directory "a" comes first
        assertTrue(FileOrder.PATHS.compare(Path.of("notes", "a", "b.txt"), Path.of("notes", "a-b.txt")) < 0);
        assertTrue(FileOrder.PATHS.compare(Path.of("notes"), Path.of("notes", "a.txt")) < 0, "a directory before its files");
        assertTrue(FileOrder.PATHS.compare(Path.of("notes", "a.txt"), Path.of("notes", "B.TXT")) < 0);
        assertTrue(FileOrder.PATHS.compare(Path.of("2024", "day1.txt"), Path.of("a.txt")) < 0);
    }

    @Test
    void theOrderIsTotalAndDoesNotDependOnTheInputOrder() {
        List<String> names = List.of("a.txt", "A.txt", "B.TXT", "b.txt", "c.md", E_NFC, E_NFD, "10.txt", "9.txt", "_x", "Z");
        List<String> expected = new ArrayList<>(names);
        expected.sort(FileOrder.NAMES);
        Random random = new Random(42);
        for (int i = 0; i < 20; i++) {
            List<String> shuffled = new ArrayList<>(names);
            Collections.shuffle(shuffled, random);
            shuffled.sort(FileOrder.NAMES);
            assertEquals(expected, shuffled);
        }
        for (String a : names) {
            for (String b : names) {
                int ab = Integer.signum(FileOrder.NAMES.compare(a, b));
                assertEquals(-ab, Integer.signum(FileOrder.NAMES.compare(b, a)), a + " / " + b);
                assertEquals(a.equals(b), ab == 0, a + " / " + b);
            }
        }
    }

    @Test
    void textIdsFollowTheFileOrder() {
        Path dir = Path.of("notes").toAbsolutePath();
        List<TextId> ids = new ArrayList<>(List.of(TextId.of(dir.resolve("B.TXT")), TextId.of(dir.resolve("a.txt"))));
        Collections.sort(ids);
        assertEquals(List.of(TextId.of(dir.resolve("a.txt")), TextId.of(dir.resolve("B.TXT"))), ids);
    }
}
