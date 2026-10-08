package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import java.util.List;
import java.util.Optional;

/**
 * A node of the tag tree: one level of the keywords.
 *
 * @param level    its name ("verb")
 * @param path     the levels from the root (["pos", "verb"]); empty for the root
 * @param own      tags whose path is exactly {@code path}
 * @param total    tags of the whole subtree, {@code own} included
 * @param children sub-nodes, sorted
 */
public record TagNode(String level, List<String> path, int own, int total, List<TagNode> children) {

    public TagNode {
        path = List.copyOf(path);
        children = List.copyOf(children);
    }

    public boolean isRoot() {
        return path.isEmpty();
    }

    /** "pos/verb". */
    public String keyword() {
        return String.join("/", path);
    }

    public Optional<TagNode> child(String name) {
        return children.stream().filter(c -> c.level().equals(name)).findFirst();
    }

    /** The node at {@code path} below this one. */
    public Optional<TagNode> find(List<String> target) {
        if (target.size() < path.size() || !target.subList(0, path.size()).equals(path)) {
            return Optional.empty();
        }
        if (target.size() == path.size()) return Optional.of(this);
        return child(target.get(path.size())).flatMap(c -> c.find(target));
    }
}
