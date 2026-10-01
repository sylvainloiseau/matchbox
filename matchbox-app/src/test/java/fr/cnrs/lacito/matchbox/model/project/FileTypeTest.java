package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTypeTest {

    @TempDir
    Path dir;

    @Test
    void invalidRegexIsRejectedAndThePreviousOneKept() {
        FileType t = new FileType("notes", ".*\\.txt");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> t.setRegex("*.md"));
        assertTrue(e.getMessage().contains("'notes'"), e.getMessage());
        assertTrue(e.getMessage().contains("Did you mean .*\\.md ?"), e.getMessage());
        assertEquals(".*\\.txt", t.getRegex());
        assertTrue(t.matches(Path.of("a.txt")));
        assertThrows(IllegalArgumentException.class, () -> new FileType("x", "*.txt"));
    }

    @Test
    void wildcardPatternsGetASuggestion() {
        assertEquals(".*\\.conll", FileType.globToRegex("*.conll"));
        assertEquals("notes-..\\.txt", FileType.globToRegex("notes-??.txt"));
        assertNull(FileType.globToRegex("[abc"));
        assertNull(FileType.checkRegex(".+"));
        assertTrue(FileType.checkRegex("[abc").contains("not a valid regular expression"));
        assertFalse(FileType.checkRegex("[abc").contains("Did you mean"));
    }

    @Test
    void invalidRegexOfAProjectFileIsReportedAndMatchesNothing() throws Exception {
        Path file = dir.resolve("p.mbx");
        Files.writeString(file, """
            <project>
              <fileset/>
              <pattern><name>test</name><regex>*.txt</regex></pattern>
              <pattern><name>all</name><regex>.+</regex></pattern>
            </project>""");
        Project p = ProjectXMLSerializer.load(file.toFile(), List.of());
        FileType test = p.getFileTypes().get(0);
        assertFalse(test.isValid());
        assertEquals("*.txt", test.getRegex(), "kept so that it can be corrected");
        assertFalse(test.matches(Path.of("a.txt")));
        assertTrue(p.getFileTypes().get(1).matches(Path.of("a.txt")));
        assertEquals(1, p.getLoadProblems().size());
        assertTrue(p.getLoadProblems().get(0).contains("'test' matches no file"), p.getLoadProblems().get(0));
        // a file can be opened: the invalid file type is ignored
        Path note = Files.writeString(dir.resolve("n.txt"), "x");
        assertTrue(p.getGrammarsForFile(note).isEmpty());

        // saved back unchanged
        p.setFile(file.toFile());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(file).contains("<regex>*.txt</regex>"));
    }
}
