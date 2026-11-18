package fr.cnrs.lacito.matchbox.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.MediaPaths.Base;
import fr.cnrs.lacito.matchbox.corpus.MediaPaths.Candidate;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Media references: absolute as is; relative in the text's directory, then in the project directory. */
class MediaPathsTest {

    @TempDir
    Path dir;

    private Path project;
    private Path notes;

    @BeforeEach
    void directories() throws Exception {
        // the project directory holds the project file; the texts are in notes/
        project = Files.createDirectories(dir.resolve("project"));
        notes = Files.createDirectories(project.resolve("notes"));
    }

    private static Path file(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        return Files.createFile(path);
    }

    private MediaPaths.Resolution resolve(String value) {
        return MediaPaths.resolve(value, Optional.of(project), Optional.of(notes));
    }

    @Test
    void aFileOnlyInTheTextDirectory() throws Exception {
        Path a = file(notes.resolve("rec/a.wav"));
        var r = resolve("rec/a.wav");
        assertEquals(Optional.of(a), r.path());
        assertTrue(r.exists());
        assertEquals(Optional.of(new Candidate(a, Base.TEXT_DIRECTORY)), r.found());
    }

    @Test
    void aFileOnlyInTheProjectDirectory() throws Exception {
        Path a = file(project.resolve("rec/a.wav"));
        var r = resolve("rec/a.wav");
        assertEquals(Optional.of(a), r.path());
        assertEquals(Base.PROJECT_DIRECTORY, r.found().orElseThrow().base());
    }

    @Test
    void aFileInBothDirectoriesIsTheTextDirectoryOne() throws Exception {
        Path inNotes = file(notes.resolve("rec/a.wav"));
        file(project.resolve("rec/a.wav"));
        assertEquals(Optional.of(inNotes), resolve("rec/a.wav").path());
    }

    @Test
    void aMissingFileListsBothCandidatesTextDirectoryFirst() {
        var r = resolve("none.wav");
        assertFalse(r.exists());
        assertEquals(Optional.of(notes.resolve("none.wav")), r.path(), "first candidate shown");
        assertEquals(List.of(new Candidate(notes.resolve("none.wav"), Base.TEXT_DIRECTORY),
            new Candidate(project.resolve("none.wav"), Base.PROJECT_DIRECTORY)), r.candidates());
        assertEquals(Optional.empty(), r.found());
    }

    @Test
    void withoutProjectDirectoryOnlyTheTextDirectory() throws Exception {
        Path b = file(notes.resolve("b.mp3"));
        var r = MediaPaths.resolve("b.mp3", Optional.empty(), Optional.of(notes));
        assertTrue(r.exists());
        assertEquals(Optional.of(b), r.path());
        assertEquals(1, MediaPaths.resolve("none.mp3", Optional.empty(), Optional.of(notes)).candidates().size());
    }

    @Test
    void absolutePathsAndFileUrisAreUsedAsIs() throws Exception {
        Path elsewhere = file(dir.resolve("other/c.mp4"));
        var r = resolve(elsewhere.toString());
        assertEquals(Optional.of(elsewhere), r.path());
        assertEquals(List.of(new Candidate(elsewhere, Base.ABSOLUTE)), r.candidates());
        assertTrue(resolve(elsewhere.toUri().toString()).exists());
    }

    @Test
    void parentReferencesAndBackslashes() throws Exception {
        Path media = file(project.resolve("media/a.wav"));
        assertEquals(Optional.of(media), resolve("../media/a.wav").path(), "../ from notes/");
        Path rec = file(notes.resolve("rec/b.wav"));
        assertEquals(Optional.of(rec), resolve("rec\\b.wav").path(), "\\ read as a separator on every system");
    }

    @Test
    void chosenFilesAreWrittenRelativeToTheTextDirectoryFirst() throws Exception {
        Path inNotes = file(notes.resolve("rec/a.wav"));
        assertEquals("rec/a.wav", MediaPaths.toValue(inNotes, Optional.of(project), Optional.of(notes)));
        Path inProject = file(project.resolve("media/b.wav"));
        assertEquals("media/b.wav", MediaPaths.toValue(inProject, Optional.of(project), Optional.of(notes)));
        Path elsewhere = dir.resolve("other/c.mp4");
        assertEquals(elsewhere.toString(), MediaPaths.toValue(elsewhere, Optional.of(project), Optional.of(notes)));
        assertEquals("rec/a.wav", MediaPaths.toValue(inNotes, Optional.empty(), Optional.of(notes)));
        // round trip
        for (Path f : List.of(inNotes, inProject)) {
            String value = MediaPaths.toValue(f, Optional.of(project), Optional.of(notes));
            assertEquals(Optional.of(f), resolve(value).path(), value);
        }
    }

    @Test
    void aProjectFileHiddenByATextDirectoryFileIsWrittenAbsolute() throws Exception {
        file(notes.resolve("media/b.wav"));
        Path inProject = file(project.resolve("media/b.wav"));
        assertEquals(inProject.toString(), MediaPaths.toValue(inProject, Optional.of(project), Optional.of(notes)));
    }

    @Test
    void descriptions() throws Exception {
        String sep = File.separator;
        assertEquals("looked in notes" + sep + "none.wav, then none.wav",
            MediaPaths.describe(resolve("none.wav"), Optional.of(project)));
        file(project.resolve("rec/a.wav"));
        assertEquals("rec" + sep + "a.wav (the project directory)",
            MediaPaths.describe(resolve("rec/a.wav"), Optional.of(project)));
        file(notes.resolve("rec/a.wav"));
        assertEquals("notes" + sep + "rec" + sep + "a.wav (the text's directory)",
            MediaPaths.describe(resolve("rec/a.wav"), Optional.of(project)));
        Path elsewhere = file(dir.resolve("other/c.mp4"));
        assertEquals(elsewhere + " (absolute path)", MediaPaths.describe(resolve(elsewhere.toString()), Optional.of(project)));
        assertEquals("", MediaPaths.describe(resolve("https://example.org/a.mp3"), Optional.of(project)));
    }

    @Test
    void urlsAndKinds() {
        assertTrue(MediaPaths.isUrl("https://example.org/a.mp3"));
        assertTrue(MediaPaths.isUrl(" HTTP://x"));
        assertFalse(MediaPaths.isUrl("rec/a.wav"));
        assertTrue(resolve("https://example.org/a.mp3").path().isEmpty());
        assertTrue(resolve("  ").candidates().isEmpty());
        assertEquals(MediaPaths.Kind.AUDIO, MediaPaths.kindOf(Path.of("x.WAV")));
        assertEquals(MediaPaths.Kind.VIDEO, MediaPaths.kindOf(Path.of("x.mp4")));
        assertEquals(MediaPaths.Kind.UNSUPPORTED, MediaPaths.kindOf(Path.of("x.ogg")));
    }
}
