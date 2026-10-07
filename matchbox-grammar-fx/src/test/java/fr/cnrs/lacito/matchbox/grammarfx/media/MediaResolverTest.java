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
        Segment explicit = new Segment(span(id, 45, 50), "other.wav");
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
        assertEquals(Optional.of(project.resolve("rec/inner.wav")), r2.file().path(), "relative to the project first");
        var r3 = MediaResolver.resolve(inOuter, corpus, projectDir);
        assertEquals(Optional.of(notes.resolve("outer.wav")), r3.file().path(), "then relative to the text");
        var r4 = MediaResolver.resolve(outside, corpus, Optional.empty());
        assertEquals(MediaResolver.Source.HEADER, r4.source());
        assertTrue(r4.file().exists());

        List<Path> files = MediaResolver.mediaFiles(corpus.text(id).orElseThrow(), corpus, projectDir);
        assertEquals(List.of(notes.resolve("header.wav"), notes.resolve("outer.wav"), project.resolve("rec/inner.wav"),
            project.resolve("other.wav")), files);
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
