package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tree of the keywords of the corpus: one node per level, with the number of tags of the node
 * and of its subtree. Grouping is exact (case-sensitive, levels stripped of spaces). Immutable.
 */
public final class TagTree {

    /** Order of the children of a node. */
    public enum Sort {
        /** A–Z, then by case. */
        NAME,
        /** Most tags first, ties A–Z. */
        COUNT
    }

    private TagTree() {
    }

    /** The root (level "", path []) of the tree of these occurrences. */
    public static TagNode of(List<TagOccurrence> occurrences, Sort sort) {
        Builder root = new Builder("");
        for (TagOccurrence o : occurrences) {
            Builder node = root;
            node.total++;
            for (String level : o.path()) {
                node = node.children.computeIfAbsent(level, Builder::new);
                node.total++;
            }
            node.own++;
        }
        return root.build(List.of(), comparator(sort));
    }

    private static Comparator<TagNode> comparator(Sort sort) {
        Comparator<TagNode> byName = Comparator.comparing((TagNode n) -> n.level().toLowerCase(Locale.ROOT))
            .thenComparing(TagNode::level);
        return sort == Sort.COUNT
            ? Comparator.comparingInt(TagNode::total).reversed().thenComparing(byName)
            : byName;
    }

    /**
     * The nodes whose keyword contains {@code text} (ignoring case), with their ancestors; the
     * counts are unchanged. An empty text keeps the tree as it is.
     */
    public static TagNode filter(TagNode root, String text) {
        if (text == null || text.isBlank()) return root;
        String needle = text.strip().toLowerCase(Locale.ROOT);
        return filtered(root, needle);
    }

    private static TagNode filtered(TagNode node, String needle) {
        List<TagNode> kept = new ArrayList<>();
        for (TagNode child : node.children()) {
            TagNode f = filtered(child, needle);
            if (f != null) kept.add(f);
        }
        boolean matches = !node.isRoot() && node.keyword().toLowerCase(Locale.ROOT).contains(needle);
        if (!node.isRoot() && !matches && kept.isEmpty()) return null;
        // a matching node keeps its whole subtree
        return new TagNode(node.level(), node.path(), node.own(), node.total(), matches ? node.children() : kept);
    }

    private static final class Builder {
        final String level;
        int own;
        int total;
        final Map<String, Builder> children = new LinkedHashMap<>();

        Builder(String level) {
            this.level = level;
        }

        TagNode build(List<String> path, Comparator<TagNode> order) {
            List<TagNode> built = new ArrayList<>();
            for (Builder child : children.values()) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(child.level);
                built.add(child.build(childPath, order));
            }
            built.sort(order);
            return new TagNode(level, path, own, total, built);
        }
    }
}
