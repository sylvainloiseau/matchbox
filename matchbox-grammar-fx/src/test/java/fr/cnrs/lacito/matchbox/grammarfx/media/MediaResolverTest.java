package fr.cnrs.lacito.matchbox.grammarfx.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaResolverTest {

    static final class Segment extends AbstractCorpusObject implements MediaSegment {
        private final String reference;

        Segment(TextCoordinate span, String reference) {
            super("g:segment", span);
            this.reference = reference;
        }

        public Optional<String> getMediaReference() { return Optional.ofNullable(reference); }
        public Optional<TimeRange> getRange() { return Optional.of(new TimeRange(0, 1000)); }
    }

    @TempDir
    Path dir;

    @Test
    void explicitThenSectionThenHeader() throws Exception {
        Path notes = Files.createDirectories(dir.resolve("notes"));
        Path project = Files.createDirectories(dir.resolve("project"));
        Files.createFile(notes.resolve("header.wav"));
        Files.createFile(notes.resolve("outer.wav"));
        Files.createDirectories(project.resolve("rec"));
        Files.createFile(project.resolve("rec/inner.wav"));
        TextId id = new TextId(notes.resolve("t.md"));
        String content = "x".repeat(200);
        SimpleTextHeader header = new SimpleTextHeader("s:header", span(id, 0, 9), Map.of("media", "header.wav"));
        SimpleSection inner = new SimpleSection("s:section", span(id, 40, 79), "Inner", Map.of("Media", "rec/inner.wav"), List.of());
        SimpleSection outer = new SimpleSection("s:section", span(id, 20, 119), "Outer", Map.of("media", "outer.wav"), List.of(inner));
        Segment explicit = new Segment(span(id, 56, 60), "other.wav"); // after inInner: not a preceding file
        Segment inInner = new Segment(span(id, 50, 55), null);
        Segment inOuter = new Segment(span(id, 100, 105), null);
        Segment outside = new Segment(span(id, 150, 155), null);
        Corpus corpus = new Corpus();
        corpus.put(Text.builder(id, content).add("s", List.of(header, outer), List.of())
            .add("g", List.of(explicit, inInner, inOuter, outside), List.of()).build());
        Optional<Path> projectDir = Optional.of(project);

        var r1 = MediaResolver.resolve(explicit, corpus, projectDir);
        assertEquals(MediaResolver.Source.EXPLICIT, r1.source());
        assertTrue(!r1.file().exists());
        var r2 = MediaResolver.resolve(inInner, corpus, projectDir);
        assertEquals(MediaResolver.Source.SECTION, r2.source());
        assertEquals(Optional.of("Inner"), r2.sourceTitle());
        assertEquals(Optional.of(project.resolve("rec/inner.wav")), r2.file().path(), "not in the text's directory: the project's");
        var r3 = MediaResolver.resolve(inOuter, corpus, projectDir);
        assertEquals(Optional.of(notes.resolve("outer.wav")), r3.file().path(), "the text's directory");
        var r4 = MediaResolver.resolve(outside, corpus, Optional.empty());
        assertEquals(MediaResolver.Source.HEADER, r4.source());
        assertTrue(r4.file().exists());

        List<Path> files = MediaResolver.mediaFiles(corpus.text(id).orElseThrow(), corpus, projectDir);
        assertEquals(List.of(notes.resolve("header.wav"), notes.resolve("outer.wav"), project.resolve("rec/inner.wav"),
            notes.resolve("other.wav")), files);
    }

    @Test
    void aFileInBothDirectoriesIsTheOneOfTheTextDirectory() throws Exception {
        Path project = Files.createDirectories(dir.resolve("project"));
        Path notes = Files.createDirectories(project.resolve("notes"));
        for (Path base : List.of(notes, project)) {
            Files.createDirectories(base.resolve("rec"));
            Files.createFile(base.resolve("rec/a.wav"));
            Files.createFile(base.resolve("rec/h.wav"));
            Files.createFile(base.resolve("rec/s.wav"));
        }
        TextId id = new TextId(notes.resolve("t.md"));
        SimpleTextHeader header = new SimpleTextHeader("s:header", span(id, 0, 9), Map.of("media", "rec/h.wav"));
        SimpleSection section = new SimpleSection("s:section", span(id, 100, 199), "S", Map.of("media", "rec/s.wav"), List.of());
        Segment explicit = new Segment(span(id, 60, 65), "rec/a.wav"); // after fromHeader: not its file
        Segment fromHeader = new Segment(span(id, 50, 55), null);
        Segment fromSection = new Segment(span(id, 150, 155), null);
        Corpus corpus = new Corpus();
        corpus.put(Text.builder(id, "x".repeat(200)).add("s", List.of(header, section), List.of())
            .add("g", List.of(explicit, fromHeader, fromSection), List.of()).build());
        Optional<Path> projectDir = Optional.of(project);
        assertEquals(Optional.of(notes.resolve("rec/a.wav")), MediaResolver.resolve(explicit, corpus, projectDir).file().path());
        assertEquals(Optional.of(notes.resolve("rec/h.wav")), MediaResolver.resolve(fromHeader, corpus, projectDir).file().path());
        assertEquals(Optional.of(notes.resolve("rec/s.wav")), MediaResolver.resolve(fromSection, corpus, projectDir).file().path());
    }

    @Test
    void aPrecedingSegmentOfTheSameSectionGivesItsFile() {
        TextId id = new TextId(dir.resolve("t.md"));
        String content = "x".repeat(200);
        SimpleSection first = new SimpleSection("s:section", span(id, 0, 99), "One", Map.of("media", "section.wav"), List.of());
        SimpleSection second = new SimpleSection("s:section", span(id, 100, 199), "Two", Map.of(), List.of());
        Segment file = new Segment(span(id, 10, 15), "a.wav");
        Segment range = new Segment(span(id, 20, 25), null);
        Segment later = new Segment(span(id, 30, 35), "b.wav");
        Segment last = new Segment(span(id, 40, 45), null);
        Segment otherSection = new Segment(span(id, 110, 115), null);
        Corpus corpus = new Corpus();
        corpus.put(Text.builder(id, content).add("s", List.of(first, second), List.of())
            .add("g", List.of(file, range, later, last, otherSection), List.of()).build());
        var r = MediaResolver.resolve(range, corpus, Optional.empty());
        assertEquals(MediaResolver.Source.PRECEDING, r.source());
        assertEquals(Optional.of("a.wav"), r.reference());
        assertEquals(Optional.of("b.wav"), MediaResolver.resolve(last, corpus, Optional.empty()).reference(), "the nearest");
        assertEquals(MediaResolver.Source.NONE, MediaResolver.resolve(otherSection, corpus, Optional.empty()).source(),
            "not across sections");
    }

    @Test
    void noMediaAnywhere() {
        TextId id = new TextId(dir.resolve("t.md"));
        Segment s = new Segment(span(id, 0, 3), null);
        Corpus corpus = new Corpus();
        corpus.put(Text.builder(id, "abcd").add("g", List.of(s), List.of()).build());
        var r = MediaResolver.resolve(s, corpus, Optional.empty());
        assertEquals(MediaResolver.Source.NONE, r.source());
        assertTrue(r.file().path().isEmpty());
    }

    private static TextCoordinate span(TextId id, int start, int stop) {
        return new TextCoordinate(id, start, stop, 1, 1);
    }
}
