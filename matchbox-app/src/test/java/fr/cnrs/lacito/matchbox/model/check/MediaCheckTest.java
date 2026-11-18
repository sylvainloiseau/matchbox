package fr.cnrs.lacito.matchbox.model.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.markdownheading.MarkdownHeadingGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammar.mediaref.MediaRefGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The media check on real parses: references as written, text directory then project directory. */
class MediaCheckTest {

    @TempDir
    Path dir;

    private Path project;
    private Path notes;

    static final String TEXT = """
        ---
        media: header.wav
        ---

        Session
        =======

        ---
        media: section.wav
        ---

        @media rec/a.wav 0:01-0:04 Greeting
        @media 0:05-0:06 inherits the file above
        @media https://example.org/a.mp3 0:01-0:02 remote

        :::{media="igt.wav"}
        a b
        c d
        'x'
        :::
        """;

    @BeforeEach
    void directories() throws Exception {
        project = Files.createDirectories(dir.resolve("project"));
        notes = Files.createDirectories(project.resolve("notes"));
    }

    private Text parse() {
        return TextParser.parse(new TextId(notes.resolve("day1.md")), TEXT,
            List.of(new MarkdownHeadingGrammarPlugin(), new MediaRefGrammarPlugin(), new IgtGrammarPlugin()));
    }

    private List<Diagnostic> check(Optional<Path> projectDirectory) {
        return new MediaCheck().check(parse(), new CheckInputs(GlossCheckSettings.defaults(), Optional.empty(),
            Optional.empty(), projectDirectory));
    }

    private static String underlined(Diagnostic d) {
        return TEXT.substring(d.startIndex(), d.stopIndex() + 1);
    }

    @Test
    void everyMissingWrittenReferenceOnceOnItsValue() {
        List<Diagnostic> ds = check(Optional.of(project));
        assertEquals(List.of("header.wav", "section.wav", "rec/a.wav", "igt.wav"), ds.stream().map(MediaCheckTest::underlined).toList(),
            "no issue for the inherited file nor the URL");
        for (Diagnostic d : ds) {
            assertEquals(MediaCheck.CODE, d.code());
            assertEquals(MediaCheck.ID, d.grammarId());
            assertTrue(d.isSemantic());
            assertEquals(Diagnostic.Severity.WARNING, d.severity());
        }
        String sep = File.separator;
        assertEquals("Media file not found: rec/a.wav (looked in notes" + sep + "rec" + sep + "a.wav, then rec" + sep + "a.wav)",
            ds.get(2).message());
        assertEquals(12, ds.get(2).line());
    }

    @Test
    void filesFoundInEitherDirectoryAreNotReported() throws Exception {
        Files.createFile(notes.resolve("header.wav"));
        Files.createFile(project.resolve("section.wav"));
        Files.createDirectories(notes.resolve("rec"));
        Files.createFile(notes.resolve("rec/a.wav"));
        Files.createFile(project.resolve("igt.wav"));
        assertEquals(List.of(), check(Optional.of(project)));
    }

    @Test
    void withoutProjectDirectoryOnlyTheTextDirectoryIsNamed() throws Exception {
        Files.createFile(project.resolve("section.wav"));
        List<Diagnostic> ds = check(Optional.empty());
        Diagnostic section = ds.stream().filter(d -> underlined(d).equals("section.wav")).findFirst().orElseThrow();
        assertTrue(section.message().endsWith("(looked in " + notes.resolve("section.wav") + ")"), section.message());
    }

    @Test
    void theProjectDirectoryIsAnInputOfTheChecks() {
        ProjectChecks checks = new ProjectChecks();
        Text text = parse();
        assertEquals(4, checks.of(text).stream().filter(d -> MediaCheck.ID.equals(d.grammarId())).count());
        int[] told = {0};
        checks.addListener(() -> told[0]++);
        checks.setInputs(new CheckInputs(GlossCheckSettings.defaults(), Optional.empty(), Optional.empty(), Optional.of(project)));
        assertEquals(1, told[0], "a new project directory drops the findings");
    }
}
