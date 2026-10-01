package fr.cnrs.lacito.matchbox.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentCharsetTest {

    private static final String TEXT = "ŋa éè ʔu\n";
    private static final String LATIN1_TEXT = "café à l'été\n";

    @TempDir
    Path dir;

    static class NoOpGrammar implements GrammarPlugin {
        public String getId() { return Project.DEFAULT_PLUGIN_ID; }
        public String getDisplayName() { return "noop"; }
        public String getDisplayDescription() { return ""; }
        public String getFormatDocumentation() { return ""; }
        public List<GrammarExample> getExamples() { return List.of(); }
        public String getGrammarSource() { return ""; }
        public ParseResult getResult(Path file, String content) { return ParseResult.empty(); }
        public int getApiVersion() { return API_VERSION; }
    }

    @Test
    void utf8RoundTrip() throws IOException {
        Path f = Files.writeString(dir.resolve("a.txt"), TEXT, StandardCharsets.UTF_8);
        TextDocument d = new TextDocument(f, StandardCharsets.UTF_8);
        assertEquals(TEXT, d.getCurrentText());
        d.updateText(TEXT + "ʃ\n");
        d.writeFile();
        assertEquals(TEXT + "ʃ\n", Files.readString(f, StandardCharsets.UTF_8));
    }

    @Test
    void latin1RoundTripKeepsTheEncoding() throws IOException {
        Path f = Files.writeString(dir.resolve("l.txt"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        byte[] original = Files.readAllBytes(f);
        TextDocument d = new TextDocument(f, StandardCharsets.ISO_8859_1);
        assertEquals(LATIN1_TEXT, d.getCurrentText());
        d.updateText(LATIN1_TEXT + "x");
        d.updateText(LATIN1_TEXT);
        d.writeFile();
        assertArrayEquals(original, Files.readAllBytes(f), "saved in ISO-8859-1, not converted to UTF-8");
    }

    @Test
    void latin1FileReadAsUtf8IsReportedNotAltered() throws IOException {
        Path f = Files.writeString(dir.resolve("l.txt"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        byte[] original = Files.readAllBytes(f);
        IOException e = assertThrows(IOException.class, () -> new TextDocument(f, StandardCharsets.UTF_8));
        assertTrue(e.getMessage().contains("not valid UTF-8"), e.getMessage());
        assertTrue(e.getMessage().contains("offset 3"), e.getMessage()); // "caf" then the é byte
        assertArrayEquals(original, Files.readAllBytes(f));
    }

    @Test
    void charactersThatCannotBeSavedAreReported() throws IOException {
        Path f = Files.writeString(dir.resolve("l.txt"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        TextDocument d = new TextDocument(f, StandardCharsets.ISO_8859_1);
        d.updateText("ŋ is not in Latin-1");
        IOException e = assertThrows(IOException.class, d::writeFile);
        assertTrue(e.getMessage().contains("cannot be written in ISO-8859-1"), e.getMessage());
        assertEquals(LATIN1_TEXT, Files.readString(f, StandardCharsets.ISO_8859_1), "file unchanged");
    }

    @Test
    void extensionMatching() {
        List<String> ext = List.of("txt", "md");
        assertTrue(DocumentOptions.hasExtension(Path.of("a.TXT"), ext));
        assertTrue(DocumentOptions.hasExtension(Path.of("dir/b.Md"), ext));
        assertFalse(DocumentOptions.hasExtension(Path.of("c.xml"), ext));
        assertFalse(DocumentOptions.hasExtension(Path.of("txt"), ext));
        assertFalse(DocumentOptions.hasExtension(Path.of(".txt"), ext), "hidden file without extension");
        assertTrue(DocumentOptions.hasExtension(Path.of("anything"), List.of()), "empty list = all files");
    }

    @Test
    void projectUsesCharsetAndExtensions() throws IOException {
        Files.writeString(dir.resolve("a.notes"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        Files.writeString(dir.resolve("b.txt"), "ignored", StandardCharsets.ISO_8859_1);
        Project p = Project.createDefaultProject(List.of(new NoOpGrammar()),
            DocumentOptions.of(StandardCharsets.ISO_8859_1, List.of("notes")));
        p.addDirectory(dir, false);
        assertEquals(1, p.getDerivedFileList().size());
        TextDocument d = p.getDerivedFileList().get(0);
        assertEquals(LATIN1_TEXT, d.getCurrentText());
        assertEquals(StandardCharsets.ISO_8859_1, d.getCharset());
    }

    @Test
    void unreadableFilesOfADirectoryAreReportedOthersAdded() throws IOException {
        Files.writeString(dir.resolve("good.txt"), TEXT, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("bad.txt"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        Project p = Project.createDefaultProject(List.of(new NoOpGrammar()));
        IOException e = assertThrows(IOException.class, () -> p.addDirectory(dir, false));
        assertTrue(e.getMessage().contains("1 file(s)"), e.getMessage());
        assertTrue(e.getMessage().contains("bad.txt"), e.getMessage());
        assertEquals(1, p.getDerivedFileList().size());
    }

    @Test
    void unreadableOrMissingFilesOfAProjectFileAreRecorded() throws Exception {
        Files.writeString(dir.resolve("bad.txt"), LATIN1_TEXT, StandardCharsets.ISO_8859_1);
        Path mbx = Files.writeString(dir.resolve("p.mbx"), """
            <project><fileset>
              <file path="%s"/>
              <file path="%s"/>
            </fileset></project>
            """.formatted(dir.resolve("bad.txt"), dir.resolve("missing.txt")));
        Project p = Project.loadProject(List.of(new NoOpGrammar()), mbx.toFile());
        assertEquals(2, p.getLoadProblems().size(), p.getLoadProblems().toString());
        assertTrue(p.getDerivedFileList().isEmpty());
    }
}
