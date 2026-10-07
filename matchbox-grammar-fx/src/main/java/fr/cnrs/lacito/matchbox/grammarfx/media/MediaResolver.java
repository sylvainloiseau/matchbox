package fr.cnrs.lacito.matchbox.grammarfx.media;

import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.MediaPaths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The media file of a segment: its explicit file, otherwise the {@code media} metadata of the
 * innermost enclosing section that has one, otherwise of the text header; then resolved like the
 * {@code media} values of the metadata editor (project directory, then directory of the text).
 * No JavaFX.
 */
public final class MediaResolver {

    /** The metadata key giving the media file of the segments of a section or text. */
    public static final String MEDIA_KEY = "media";

    /** Where the file of a segment comes from. */
    public enum Source { EXPLICIT, SECTION, HEADER, NONE }

    /**
     * @param reference   the file as written, if any
     * @param source      where it comes from
     * @param sourceTitle title of the section, for {@link Source#SECTION}
     * @param file        the resolved file (path and whether it exists)
     */
    public record Resolution(Optional<String> reference, Source source, Optional<String> sourceTitle,
                             MediaPaths.Resolution file) {

        /** True for an http(s) URL. */
        public boolean isUrl() {
            return reference.map(MediaPaths::isUrl).orElse(false);
        }
    }

    private MediaResolver() {}

    public static Resolution resolve(MediaSegment segment, CorpusView corpus, Optional<Path> projectDirectory) {
        Optional<Path> textDirectory = textDirectory(segment);
        if (segment.getMediaReference().isPresent()) {
            String ref = segment.getMediaReference().get();
            return new Resolution(Optional.of(ref), Source.EXPLICIT, Optional.empty(),
                MediaPaths.resolve(ref, projectDirectory, textDirectory));
        }
        Optional<Text> text = corpus.textOf(segment);
        if (text.isPresent()) {
            List<Section> enclosing = text.get().enclosingSections(segment);
            for (int i = enclosing.size() - 1; i >= 0; i--) {
                Optional<String> ref = media(enclosing.get(i).getMetadata());
                if (ref.isPresent()) {
                    return new Resolution(ref, Source.SECTION, Optional.of(enclosing.get(i).getLabel()),
                        MediaPaths.resolve(ref.get(), projectDirectory, textDirectory));
                }
            }
            Optional<String> ref = media(text.get().properties());
            if (ref.isPresent()) {
                return new Resolution(ref, Source.HEADER, Optional.empty(),
                    MediaPaths.resolve(ref.get(), projectDirectory, textDirectory));
            }
        }
        return new Resolution(Optional.empty(), Source.NONE, Optional.empty(),
            new MediaPaths.Resolution(Optional.empty(), false));
    }

    /** Distinct resolved files used by a text: its media segments and its "media" metadata (header, sections). */
    public static List<Path> mediaFiles(Text text, CorpusView corpus, Optional<Path> projectDirectory) {
        Set<Path> files = new LinkedHashSet<>();
        Optional<Path> textDirectory = Optional.ofNullable(text.id().path().toAbsolutePath().getParent());
        media(text.properties()).flatMap(r -> MediaPaths.resolve(r, projectDirectory, textDirectory).path())
            .ifPresent(files::add);
        for (Section s : text.sections()) {
            media(s.getMetadata()).flatMap(r -> MediaPaths.resolve(r, projectDirectory, textDirectory).path())
                .ifPresent(files::add);
        }
        for (MediaSegment segment : text.objectsOf(MediaSegment.class)) {
            resolve(segment, corpus, projectDirectory).file().path().ifPresent(files::add);
        }
        return new ArrayList<>(files);
    }

    private static Optional<String> media(Map<String, String> metadata) {
        return metadata.entrySet().stream()
            .filter(e -> e.getKey().strip().equalsIgnoreCase(MEDIA_KEY) && !e.getValue().isBlank())
            .map(e -> e.getValue().strip())
            .findFirst();
    }

    private static Optional<Path> textDirectory(MediaSegment segment) {
        return Optional.ofNullable(segment.getTextCoordinate().text().path().toAbsolutePath().getParent());
    }
}
