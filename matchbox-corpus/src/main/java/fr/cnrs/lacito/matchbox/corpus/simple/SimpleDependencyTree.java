package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.HasIssues;
import fr.cnrs.lacito.matchbox.corpus.Issue;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A dependency tree: one sentence of tokens and the head of each.
 *
 * <p>It checks its own structure ({@link HasIssues}): token IDs {@code 1..n} in order, heads
 * between 0 and n, exactly one root, no cycle. Multiword ranges ({@code 1-2}) and empty nodes
 * ({@code 1.1}) are not checked; an unspecified head ({@code _}) is not reported.
 */
public final class SimpleDependencyTree extends AbstractCorpusObject implements DependencySentence, HasIssues {

    /** Codes of the issues (see {@link Issue#code()}). */
    public static final String CODE_IDS = "dependency.ids";
    public static final String CODE_HEAD = "dependency.head";
    public static final String CODE_HEAD_RANGE = "dependency.head-range";
    public static final String CODE_NO_ROOT = "dependency.no-root";
    public static final String CODE_ROOTS = "dependency.several-roots";
    public static final String CODE_CYCLE = "dependency.cycle";

    private final String sentenceId;
    private final Map<String, String> metadata;
    private final List<Map<String, String>> tokens;
    private final List<Integer> heads;
    private final List<TextCoordinate> tokenSpans;

    public SimpleDependencyTree(String kind, TextCoordinate coordinate,
        String sentenceId,
        Map<String, String> metadata,
        List<Map<String, String>> tokens,
        List<Integer> heads
    ) {
        this(kind, coordinate, sentenceId, metadata, tokens, heads, List.of());
    }

    /**
     * @param heads      head of each token, -1 when not a number
     * @param tokenSpans span of the line of each token (same size as {@code tokens}), used to place
     *                   the issues; empty: issues are placed on the whole sentence
     */
    public SimpleDependencyTree(String kind, TextCoordinate coordinate,
        String sentenceId,
        Map<String, String> metadata,
        List<Map<String, String>> tokens,
        List<Integer> heads,
        List<TextCoordinate> tokenSpans
    ) {
        super(kind, coordinate);
        this.sentenceId = sentenceId;
        this.metadata = orderedCopy(metadata);
        this.tokens = tokens.stream().map(SimpleDependencyTree::orderedCopy).toList();
        this.heads = List.copyOf(heads);
        this.tokenSpans = tokenSpans.size() == tokens.size() ? List.copyOf(tokenSpans) : List.of();
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

    @Override
    public List<Issue> issues() {
        // the words: tokens with an integer ID (not "1-2" ranges nor "1.1" empty nodes)
        List<Integer> words = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String id = tokens.get(i).getOrDefault("ID", "");
            if (id.matches("\\d+")) words.add(i);
        }
        int n = words.size();
        List<Issue> issues = new ArrayList<>();
        for (int w = 0; w < n; w++) {
            String id = tokens.get(words.get(w)).get("ID");
            if (Integer.parseInt(id) != w + 1) {
                issues.add(at(Issue.error("Token ID " + id + " where " + (w + 1)
                    + " was expected: IDs must be 1, 2, 3… in order", CODE_IDS), words.get(w)));
                return issues; // heads cannot be checked against wrong IDs
            }
        }
        List<Integer> roots = new ArrayList<>();
        boolean allHeadsKnown = true;
        for (int w = 0; w < n; w++) {
            int t = words.get(w);
            String raw = tokens.get(t).getOrDefault("HEAD", "_").strip();
            int head = t < heads.size() ? heads.get(t) : -1;
            if (head < 0) {
                allHeadsKnown = false;
                if (!raw.equals("_")) {
                    issues.add(at(Issue.error("HEAD '" + raw + "' of token " + (w + 1) + " is not a number", CODE_HEAD), t));
                }
            } else if (head > n) {
                allHeadsKnown = false;
                issues.add(at(Issue.error("HEAD " + head + " of token " + (w + 1) + " is outside the sentence (0–" + n + ")",
                    CODE_HEAD_RANGE), t));
            } else if (head == 0) {
                roots.add(w + 1);
            }
        }
        if (n > 0 && allHeadsKnown && roots.isEmpty()) {
            issues.add(Issue.error("No root: no token has HEAD 0", CODE_NO_ROOT));
        }
        if (roots.size() > 1) {
            issues.add(at(Issue.warning("Several roots (HEAD 0): tokens "
                + roots.stream().map(String::valueOf).collect(Collectors.joining(", ")), CODE_ROOTS),
                words.get(roots.get(1) - 1)));
        }
        issues.addAll(cycles(words));
        return issues;
    }

    /** One issue per cycle of heads (a token whose chain of heads never reaches 0). */
    private List<Issue> cycles(List<Integer> words) {
        int n = words.size();
        int[] state = new int[n + 1]; // 0 unvisited, 1 on the current path, 2 done
        List<Issue> issues = new ArrayList<>();
        for (int start = 1; start <= n; start++) {
            List<Integer> path = new ArrayList<>();
            int w = start;
            while (w >= 1 && w <= n && state[w] == 0) {
                state[w] = 1;
                path.add(w);
                int t = words.get(w - 1);
                w = t < heads.size() ? heads.get(t) : -1;
            }
            if (w >= 1 && w <= n && state[w] == 1) {
                List<Integer> cycle = path.subList(path.indexOf(w), path.size());
                String chain = cycle.stream().map(String::valueOf).collect(Collectors.joining(" → ")) + " → " + w;
                issues.add(at(Issue.error("Cycle of heads: " + chain, CODE_CYCLE), words.get(w - 1)));
            }
            for (int p : path) state[p] = 2;
        }
        return issues;
    }

    private Issue at(Issue issue, int token) {
        return tokenSpans.isEmpty() ? issue : issue.at(tokenSpans.get(token));
    }
}
