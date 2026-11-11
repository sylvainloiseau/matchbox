package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearTimeline;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reads the IGTs of a text in their context, in one pass in text order: inherited metadata
 * ({@link InterlinearInheritance}), roles of the lines ({@link LineClassifier}), alignment
 * ({@link InterlinearAlignment}), the lines compared with a dictionary ({@code form=}, {@code gloss=}),
 * timing ({@link InterlinearTiming}). Use {@link InterlinearReadings#of(Text)}, which caches the
 * result per text. Pure.
 */
public final class InterlinearResolver {

    private InterlinearResolver() {}

    /** The readings of the IGTs of a text, by IGT (identity), in text order. */
    public static Map<InterlinearGlossedText, InterlinearReading> resolve(Text text) {
        List<InterlinearGlossedText> igts = text.flatten()
            .filter(InterlinearGlossedText.class::isInstance).map(InterlinearGlossedText.class::cast)
            .sorted(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()))
            .toList();
        Optional<CorpusObject> header = text.objectsOf(TextHeader.class).stream().findFirst().map(CorpusObject.class::cast);
        Map<InterlinearGlossedText, InterlinearReading> result = new IdentityHashMap<>();
        Map<String, MetadataValue> previous = new LinkedHashMap<>();
        for (int i = 0; i < igts.size(); i++) {
            InterlinearGlossedText igt = igts.get(i);
            OptionalLong nextStart = i + 1 < igts.size() ? firstStart(igts.get(i + 1)) : OptionalLong.empty();
            Map<String, MetadataValue> metadata = InterlinearInheritance.effective(igt.metadata(), previous,
                text.enclosingSections(igt), text.properties(), header);
            result.put(igt, read(igt, metadata, nextStart));
            previous = InterlinearInheritance.remember(previous, igt.metadata(), igt);
        }
        return result;
    }

    /** The reading of an IGT alone: its own metadata only, no next IGT. */
    public static InterlinearReading withoutContext(InterlinearGlossedText igt) {
        return read(igt, InterlinearInheritance.effective(igt.metadata(), Map.of(), List.<Section>of(), Map.of(),
            Optional.empty()), OptionalLong.empty());
    }

    static InterlinearReading read(InterlinearGlossedText igt, Map<String, MetadataValue> metadata, OptionalLong nextStart) {
        Optional<String> olang = value(metadata, InterlinearInheritance.OLANG);
        Optional<String> mlang = value(metadata, InterlinearInheritance.MLANG);
        LineClassifier.Classification c = LineClassifier.classify(igt.lines(),
            Optional.ofNullable(metadata.get(InterlinearInheritance.SETTING)), olang, mlang);
        List<InterlinearIssue> issues = new ArrayList<>(c.issues());
        issues.addAll(InterlinearAlignment.check(c.lines()));

        Optional<LineReading> form = pointer(c.lines(), metadata.get(InterlinearInheritance.FORM), LineRole.TRANSCRIPTION, igt, issues);
        Optional<LineReading> gloss = pointer(c.lines(), metadata.get(InterlinearInheritance.GLOSS), LineRole.GLOSS, igt, issues);
        boolean pointed = form.isPresent() && gloss.isPresent();
        if (pointed && !form.get().level().equals(gloss.get().level())) {
            issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.POINTER,
                "form= and gloss= point to lines of different levels: the default lines are compared",
                igt.getTextCoordinate(), -1, -1));
            pointed = false;
        }
        if (!pointed) {
            Level level = c.lines().stream().anyMatch(l -> l.role() == LineRole.GLOSS && l.level().orElse(Level.WORD) == Level.MORPHEME)
                ? Level.MORPHEME : Level.WORD;
            form = firstOf(c.lines(), LineRole.TRANSCRIPTION, level);
            gloss = firstOf(c.lines(), LineRole.GLOSS, level);
        }

        long offset = number(value(metadata, InterlinearInheritance.OFFSET)).orElse(0);
        OptionalLong end = number(value(metadata, InterlinearInheritance.END));
        Map<Level, List<WordTiming>> timings = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            InterlinearTiming.compute(c.lines(), level, offset, end, nextStart).ifPresent(t -> timings.put(level, t));
        }
        return new InterlinearReading(igt, Map.copyOf(metadata), c.lines(), List.copyOf(issues), form, gloss, pointed,
            timings);
    }

    private static Optional<LineReading> firstOf(List<LineReading> lines, LineRole role, Level level) {
        return lines.stream().filter(l -> l.role() == role && l.level().orElse(Level.WORD) == level).findFirst();
    }

    private static Optional<LineReading> pointer(List<LineReading> lines, MetadataValue value, LineRole role,
                                                 InterlinearGlossedText igt, List<InterlinearIssue> issues) {
        if (value == null) return Optional.empty();
        String v = value.value().strip();
        String key = role == LineRole.GLOSS ? "gloss" : "form";
        Optional<LineReading> line = v.matches("\\d+")
            ? lines.stream().filter(l -> l.line().number() == Integer.parseInt(v)).findFirst()
            : lines.stream().filter(l -> l.name().map(v::equals).orElse(false)).findFirst();
        String from = value.isInherited() ? " (inherited)" : "";
        if (line.isEmpty()) {
            issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.POINTER,
                key + "=" + v + from + ": no such line in this IGT", igt.getTextCoordinate(), -1, -1));
            return Optional.empty();
        }
        if (line.get().role() != role) {
            issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.POINTER,
                key + "=" + v + from + ": line " + line.get().line().number() + " is a "
                    + InterlinearAlignment.roleName(line.get()) + ", not a " + (role == LineRole.GLOSS ? "gloss" : "transcription"),
                line.get().line().getTextCoordinate(), line.get().index(), -1));
            return Optional.empty();
        }
        return line;
    }

    /** The absolute first start of an IGT: its first timeline's first value, after its own offset. */
    static OptionalLong firstStart(InterlinearGlossedText igt) {
        for (InterlinearLine l : igt.lines()) {
            if (l instanceof InterlinearTimeline t && !t.values().isEmpty() && !t.values().get(0).isEmpty()) {
                long v = t.values().get(0).get(0);
                if (v < 0) return OptionalLong.empty();
                return OptionalLong.of(number(Optional.ofNullable(igt.metadata().get(InterlinearInheritance.OFFSET))).orElse(0) + v);
            }
        }
        return OptionalLong.empty();
    }

    private static Optional<String> value(Map<String, MetadataValue> metadata, String key) {
        return Optional.ofNullable(metadata.get(key)).map(MetadataValue::value);
    }

    static OptionalLong number(Optional<String> value) {
        if (value.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(value.get().strip()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
