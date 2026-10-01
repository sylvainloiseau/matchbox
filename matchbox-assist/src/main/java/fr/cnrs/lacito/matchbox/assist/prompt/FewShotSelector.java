package fr.cnrs.lacito.matchbox.assist.prompt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Chooses examples from the parsed files of a project: the first interlinear sentences and lexical
 * entries (by file path, then offset) whose span contains no parse error. The choice is
 * deterministic, so that the prompt prefix, and the provider's cache, stay stable.
 */
public final class FewShotSelector {

    public static final int PER_KIND = 3;
    static final int MAX_LENGTH = 800;

    /** What the selector needs to know about a parsed file. */
    public record ParsedFile(Path path, String text, List<CorpusObject> objects, List<ParseError> errors) {}

    private FewShotSelector() {}

    public static List<FewShotExample> select(List<ParsedFile> files) {
        List<ParsedFile> sorted = files.stream().sorted(Comparator.comparing(f -> f.path().toString())).toList();
        List<FewShotExample> result = new ArrayList<>();
        result.addAll(pick(sorted, InterlinearSentence.class, "interlinear sentence"));
        result.addAll(pick(sorted, LexicalEntry.class, "lexical entry"));
        return result;
    }

    private static List<FewShotExample> pick(List<ParsedFile> files, Class<?> type, String kind) {
        List<FewShotExample> picked = new ArrayList<>();
        for (ParsedFile f : files) {
            List<CorpusObject> candidates = f.objects().stream()
                .flatMap(FewShotSelector::selfAndDescendants)
                .filter(type::isInstance)
                .sorted(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()))
                .toList();
            for (CorpusObject o : candidates) {
                if (picked.size() == PER_KIND) {
                    return picked;
                }
                TextCoordinate span = o.getTextCoordinate();
                if (!span.isValidFor(f.text().length()) || hasError(span, f.errors())) {
                    continue;
                }
                String text = f.text().substring(span.startIndex(), span.stopIndex() + 1).strip();
                if (!text.isEmpty() && text.length() <= MAX_LENGTH) {
                    picked.add(new FewShotExample(kind, text));
                }
            }
        }
        return picked;
    }

    private static boolean hasError(TextCoordinate span, List<ParseError> errors) {
        for (ParseError e : errors) {
            if (e.getStartIndex() >= 0 ? span.contains(e.getStartIndex())
                    : e.getLine() >= span.startLine() && e.getLine() <= span.endLine()) {
                return true;
            }
        }
        return false;
    }

    private static java.util.stream.Stream<CorpusObject> selfAndDescendants(CorpusObject o) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(o),
            o.getChildren().stream().flatMap(FewShotSelector::selfAndDescendants));
    }

    /** Convenience for callers holding maps. */
    public static List<FewShotExample> select(Map<Path, String> texts, Map<Path, List<CorpusObject>> objects,
                                              Map<Path, List<ParseError>> errors) {
        List<ParsedFile> files = new ArrayList<>();
        texts.forEach((path, text) -> files.add(new ParsedFile(path, text,
            objects.getOrDefault(path, List.of()), errors.getOrDefault(path, List.of()))));
        return select(files);
    }
}
