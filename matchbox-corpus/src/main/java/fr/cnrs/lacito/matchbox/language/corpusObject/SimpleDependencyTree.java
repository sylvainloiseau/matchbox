package fr.cnrs.lacito.matchbox.language.corpusObject;

import java.util.List;
import java.util.Map;

/**
 * Represents a dependency tree
 */
public class SimpleDependencyTree extends CorpusObject {

    public SimpleDependencyTree(TextCoordinate coordinate,
        String sentence_id,
        Map<String, String> metadata,
        List<Map<String, String>> tokens,
        int[] parents
    ) {
        super(coordinate);
    }

}
