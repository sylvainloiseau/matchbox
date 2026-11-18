package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.MediaPaths;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.MediaSegment;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The media references written in a text whose file does not exist ({@link MediaPaths}: the
 * directory of the text, then the project directory). Only references as written are checked (the
 * file of a media segment, the {@code media} entry of a header, a section or an IGT), not the
 * inherited ones: a missing file gives one warning, where it is written. URLs are not checked.
 * By capability, never by plugin.
 */
public final class MediaCheck implements CorpusCheck {

    public static final String ID = "Media check";
    public static final String CODE = "media.not-found";
    /** The metadata key naming a media file (case ignored). */
    public static final String MEDIA_KEY = "media";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Diagnostic> check(Text text, CheckInputs inputs) {
        Optional<Path> textDirectory = Optional.ofNullable(text.id().path().toAbsolutePath().getParent());
        List<Diagnostic> result = new ArrayList<>();
        Set<Integer> reported = new HashSet<>();   // one warning per written value
        for (CorpusObject o : text.flatten().toList()) {
            if (o instanceof MediaSegment segment) {
                segment.getMediaReference().ifPresent(ref ->
                    check(text, o, ref, field(o, MediaSegment.MEDIA), inputs, textDirectory, reported, result));
            }
            if (o instanceof TextHeader header) {
                metadata(text, o, header.getProperties(), TextHeader::propertyField, inputs, textDirectory, reported, result);
            }
            if (o instanceof Section section) {
                metadata(text, o, section.getMetadata(), Section::metadataField, inputs, textDirectory, reported, result);
            }
            if (o instanceof InterlinearGlossedText igt) {
                metadata(text, o, igt.metadata(), InterlinearGlossedText::metadataField, inputs, textDirectory, reported, result);
            }
        }
        return result;
    }

    private void metadata(Text text, CorpusObject owner, Map<String, String> metadata, Function<String, String> fieldName,
                          CheckInputs inputs, Optional<Path> textDirectory, Set<Integer> reported, List<Diagnostic> result) {
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            if (!e.getKey().strip().equalsIgnoreCase(MEDIA_KEY)) continue;
            check(text, owner, e.getValue(), field(owner, fieldName.apply(e.getKey())), inputs, textDirectory, reported, result);
        }
    }

    private static Optional<Field> field(CorpusObject o, String name) {
        return o instanceof HasFields f ? f.field(name) : Optional.empty();
    }

    private void check(Text text, CorpusObject owner, String reference, Optional<Field> field, CheckInputs inputs,
                       Optional<Path> textDirectory, Set<Integer> reported, List<Diagnostic> result) {
        if (reference == null || reference.isBlank() || MediaPaths.isUrl(reference)) return;
        MediaPaths.Resolution r = MediaPaths.resolve(reference, inputs.projectDirectory(), textDirectory);
        if (r.exists() || r.candidates().isEmpty()) return;
        TextCoordinate span = field.map(Field::span).filter(s -> s.stopIndex() >= s.startIndex())
            .orElse(owner.getTextCoordinate());
        if (!reported.add(span.startIndex())) return;
        int stop = field.isPresent() ? span.stopIndex() : firstLineEnd(text, span);
        result.add(CorpusCheck.warning(text, ID, span.startIndex(), stop, span.startLine(),
            "Media file not found: " + reference.strip() + " (" + MediaPaths.describe(r, inputs.projectDirectory()) + ")",
            CODE));
    }

    /** The end of the first line of an object (the whole object would be too long to underline). */
    private static int firstLineEnd(Text text, TextCoordinate span) {
        String content = text.content();
        int end = span.startIndex();
        while (end < span.stopIndex() && end + 1 < content.length() && content.charAt(end + 1) != '\n'
            && content.charAt(end + 1) != '\r') {
            end++;
        }
        return end;
    }
}
