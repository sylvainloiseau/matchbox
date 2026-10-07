package fr.cnrs.lacito.matchbox.corpus;

import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The object each tag of a text applies to (see {@link Text#targetOf}):
 * <ol>
 *   <li>{@code C} = the innermost object containing the tag, if any;</li>
 *   <li>the neighbour lines of the tag are the line above and the line below its line, skipping
 *       lines made only of tags; a blank line stops the search. An object is adjacent if it ends on
 *       the neighbour above or starts on the neighbour below, does not contain the tag and lies
 *       inside {@code C} (anywhere without {@code C}); the outermost one is taken on each side;</li>
 *   <li>target: the adjacent object above, else below, else {@code C}, else the text.</li>
 * </ol>
 */
final class TagAttachment {

    /** An object with its lines (computed from the offsets) and its rank for ties. */
    private record Placed(CorpusObject object, int startLine, int endLine, int grammarRank, int depth) {}

    private final String content;
    private final int[] lineStarts;
    private final List<Placed> objects = new ArrayList<>();
    private final List<Tag> tags = new ArrayList<>();
    private final Map<Tag, CorpusObject> targets = new IdentityHashMap<>();
    private final Map<CorpusObject, List<Tag>> byTarget = new IdentityHashMap<>();
    private final List<Tag> textTags = new ArrayList<>();

    TagAttachment(String content, Map<String, List<CorpusObject>> objectsByGrammar) {
        this.content = content;
        this.lineStarts = lineStarts(content);
        int rank = 0;
        for (List<CorpusObject> list : objectsByGrammar.values()) {
            for (CorpusObject o : list) {
                place(o, rank, 0);
            }
            rank++;
        }
        for (Tag tag : tags) {
            CorpusObject target = targetOf(tag);
            if (target == null) {
                textTags.add(tag);
            } else {
                targets.put(tag, target);
                byTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(tag);
            }
        }
    }

    private void place(CorpusObject o, int rank, int depth) {
        if (o instanceof Tag tag) {
            tags.add(tag);
        } else {
            TextCoordinate span = o.getTextCoordinate();
            objects.add(new Placed(o, lineOf(span.startIndex()), lineOf(lastCharOfLine(span)), rank, depth));
        }
        for (CorpusObject child : o.getChildren()) {
            place(child, rank, depth + 1);
        }
    }

    Optional<CorpusObject> target(Tag tag) {
        return Optional.ofNullable(targets.get(tag));
    }

    List<Tag> tagsOf(CorpusObject object) {
        return Collections.unmodifiableList(byTarget.getOrDefault(object, List.of()));
    }

    List<Tag> textTags() {
        return Collections.unmodifiableList(textTags);
    }

    private CorpusObject targetOf(Tag tag) {
        TextCoordinate span = tag.getTextCoordinate();
        Placed container = innermostContaining(span);
        int line = lineOf(span.startIndex());
        Placed above = adjacent(neighbour(line, -1), true, span, container);
        if (above != null) return above.object();
        Placed below = adjacent(neighbour(line, +1), false, span, container);
        if (below != null) return below.object();
        return container == null ? null : container.object();
    }

    /** Innermost object containing the span: smallest span, then later grammar, then deeper. */
    private Placed innermostContaining(TextCoordinate span) {
        Placed best = null;
        for (Placed p : objects) {
            if (!p.object().getTextCoordinate().encloses(span)) continue;
            if (best == null || smaller(p, best)) best = p;
        }
        return best;
    }

    private static boolean smaller(Placed a, Placed b) {
        int la = a.object().getTextCoordinate().length();
        int lb = b.object().getTextCoordinate().length();
        if (la != lb) return la < lb;
        if (a.grammarRank() != b.grammarRank()) return a.grammarRank() > b.grammarRank();
        return a.depth() > b.depth();
    }

    /** The outermost object ending on (above) or starting on (below) the line, inside the container. */
    private Placed adjacent(int line, boolean above, TextCoordinate tagSpan, Placed container) {
        if (line < 1) return null;
        Placed best = null;
        for (Placed p : objects) {
            if ((above ? p.endLine() : p.startLine()) != line) continue;
            TextCoordinate s = p.object().getTextCoordinate();
            if (s.encloses(tagSpan)) continue;
            if (container != null && (container == p || !container.object().getTextCoordinate().encloses(s))) continue;
            if (best == null || s.length() > best.object().getTextCoordinate().length()) best = p;
        }
        return best;
    }

    /** The line above (-1) or below (+1), skipping lines made only of tags; -1 if blank or none. */
    private int neighbour(int line, int direction) {
        int l = line + direction;
        while (l >= 1 && l <= lineStarts.length) {
            if (isBlank(l)) return -1;
            if (!isTagsOnly(l)) return l;
            l += direction;
        }
        return -1;
    }

    private boolean isBlank(int line) {
        return lineText(line).isBlank();
    }

    /** True if the line contains tags and nothing else but spaces. */
    private boolean isTagsOnly(int line) {
        int start = lineStarts[line - 1];
        int end = start + lineText(line).length();
        StringBuilder rest = new StringBuilder(content.substring(start, end));
        boolean any = false;
        for (Tag t : tags) {
            TextCoordinate s = t.getTextCoordinate();
            if (s.startIndex() >= start && s.stopIndex() < end) {
                any = true;
                for (int i = s.startIndex(); i <= s.stopIndex(); i++) rest.setCharAt(i - start, ' ');
            }
        }
        return any && rest.toString().isBlank();
    }

    private String lineText(int line) {
        int start = lineStarts[line - 1];
        int end = start;
        while (end < content.length() && content.charAt(end) != '\n' && content.charAt(end) != '\r') end++;
        return content.substring(start, end);
    }

    /** The last character of a span that is not a line ending (so a span ending with "\n" ends on its line). */
    private int lastCharOfLine(TextCoordinate span) {
        int i = span.stopIndex();
        while (i > span.startIndex() && (content.charAt(i) == '\n' || content.charAt(i) == '\r')) i--;
        return i;
    }

    private int lineOf(int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }
}
