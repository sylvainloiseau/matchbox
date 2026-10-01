package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a dependency tree
 */
public final class SimpleDependencyTree extends AbstractCorpusObject implements DependencySentence {

    private final String sentenceId;
    private final Map<String, String> metadata;
    private final List<Map<String, String>> tokens;
    private final List<Integer> heads;

    public SimpleDependencyTree(String kind, TextCoordinate coordinate,
        String sentenceId,
        Map<String, String> metadata,
        List<Map<String, String>> tokens,
        List<Integer> heads
    ) {
        super(kind, coordinate);
        this.sentenceId = sentenceId;
        this.metadata = orderedCopy(metadata);
        this.tokens = tokens.stream().map(SimpleDependencyTree::orderedCopy).toList();
        this.heads = List.copyOf(heads);
    }

    /** Unmodifiable copy keeping the insertion order (unlike {@code Map.copyOf}). */
    private static Map<String, String> orderedCopy(Map<String, String> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    @Override
    public String getSentenceId() {
        return sentenceId;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public List<Map<String, String>> getTokens() {
        return tokens;
    }

    @Override
    public List<Integer> getHeads() {
        return heads;
    }
}
