package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaPathsTest {

    @TempDir
    Path dir;

    @Test
    void relativePathsTryTheProjectThenTheTextDirectory() throws Exception {
        Path project = Files.createDirectories(dir.resolve("project"));
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Path inProject = Files.createFile(Files.createDirectories(project.resolve("rec")).resolve("a.wav"));
        Path inNotes = Files.createFile(notes.resolve("b.mp3"));

        var r1 = MediaPaths.resolve("rec/a.wav", Optional.of(project), Optional.of(notes));
        assertEquals(Optional.of(inProject), r1.path());
        assertTrue(r1.exists());
        var r2 = MediaPaths.resolve("b.mp3", Optional.of(project), Optional.of(notes));
        assertEquals(Optional.of(inNotes), r2.path());
        var r3 = MediaPaths.resolve("b.mp3", Optional.empty(), Optional.of(notes));
        assertTrue(r3.exists(), "project not saved: text directory");
        var missing = MediaPaths.resolve("none.wav", Optional.of(project), Optional.of(notes));
        assertFalse(missing.exists());
        assertEquals(Optional.of(project.resolve("none.wav")), missing.path(), "first candidate shown");
        assertEquals(Optional.of(inNotes), MediaPaths.resolve(inNotes.toString(), Optional.empty(), Optional.empty()).path());
        assertTrue(MediaPaths.resolve(inNotes.toUri().toString(), Optional.empty(), Optional.empty()).exists());
    }

    @Test
    void chosenFilesAreWrittenRelativeWhenPossible() {
        Path project = dir.resolve("project");
        Path notes = dir.resolve("notes");
        assertEquals("rec/a.wav", MediaPaths.toValue(project.resolve("rec/a.wav"), Optional.of(project), Optional.of(notes)));
        assertEquals("b.mp3", MediaPaths.toValue(notes.resolve("b.mp3"), Optional.empty(), Optional.of(notes)));
        Path elsewhere = dir.resolve("other/c.mp4");
        assertEquals(elsewhere.toString(), MediaPaths.toValue(elsewhere, Optional.of(project), Optional.of(notes)));
    }

    @Test
    void urlsAndKinds() {
        assertTrue(MediaPaths.isUrl("https://example.org/a.mp3"));
        assertTrue(MediaPaths.isUrl(" HTTP://x"));
        assertFalse(MediaPaths.isUrl("rec/a.wav"));
        assertTrue(MediaPaths.resolve("https://example.org/a.mp3", Optional.empty(), Optional.empty()).path().isEmpty());
        assertEquals(MediaPaths.Kind.AUDIO, MediaPaths.kindOf(Path.of("x.WAV")));
        assertEquals(MediaPaths.Kind.VIDEO, MediaPaths.kindOf(Path.of("x.mp4")));
        assertEquals(MediaPaths.Kind.UNSUPPORTED, MediaPaths.kindOf(Path.of("x.ogg")));
    }
}
