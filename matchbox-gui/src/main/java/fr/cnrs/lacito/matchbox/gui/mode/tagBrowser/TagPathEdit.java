package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.gui.edit.FieldChange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans a change of the path of tags, without applying it: a rename of a level (plain: no
 * {@code /}, no existing sibling) or a move of a node under another parent (possibly merging with
 * an existing node of the same name). Both rewrite every tag whose path starts with the node's
 * path, computed on the levels, never on the text: renaming pos › verb leaves pos/verbal alone.
 * Rewritten keywords are written with their levels joined by "/".
 */
public final class TagPathEdit {

    /** A planned change, or why it is refused. */
    public sealed interface Plan {}

    /** Why the change is refused (shown to the user). */
    public record Refused(String reason) implements Plan {}

    /**
     * The change: what the user is shown, and the field changes to apply.
     *
     * @param description for the undo journal ("Rename tag pos/verb → pos/action")
     * @param from        the node's path
     * @param to          its new path
     * @param rewrites    old keyword → new keyword, with the number of tags, in order
     * @param merged      tags already at the new path or under it (a move into an existing node)
     * @param files       texts changed
     * @param changes     one per tag
     */
    public record Change(String description, List<String> from, List<String> to, List<Rewrite> rewrites,
                         int merged, Set<TextId> files, List<FieldChange> changes) implements Plan {

        public int count() {
            return changes.size();
        }
    }

    /** A keyword rewritten, and how many tags have it. */
    public record Rewrite(String from, String to, int count) {}

    private TagPathEdit() {
    }

    /** Renames the last level of {@code path} to {@code newLevel}. */
    public static Plan rename(List<TagOccurrence> occurrences, List<String> path, String newLevel) {
        if (path.isEmpty()) return new Refused("Choose a tag");
        String name = newLevel == null ? "" : newLevel.strip();
        if (name.isEmpty()) return new Refused("The name cannot be empty");
        if (name.equals(path.get(path.size() - 1))) return new Refused("The name is unchanged");
        if (name.contains("/")) return new Refused("A rename stays at the same level: the name cannot contain \"/\"");
        List<String> to = new ArrayList<>(path.subList(0, path.size() - 1));
        to.add(name);
        if (occurrences.stream().anyMatch(o -> o.isUnder(to))) {
            return new Refused(display(to) + " exists");
        }
        return plan("Rename tag " + String.join("/", path) + " → " + String.join("/", to), occurrences, path, to);
    }

    /** Moves the node at {@code path} under {@code newParent} (empty: the top level). */
    public static Plan move(List<TagOccurrence> occurrences, List<String> path, List<String> newParent) {
        if (path.isEmpty()) return new Refused("Choose a tag");
        if (newParent.equals(path.subList(0, path.size() - 1))) {
            return new Refused(newParent.isEmpty() ? "It is already at the top level" : "It is already under " + display(newParent));
        }
        if (newParent.size() >= path.size() && newParent.subList(0, path.size()).equals(path)) {
            return new Refused("A tag cannot be moved under itself");
        }
        List<String> to = new ArrayList<>(newParent);
        to.add(path.get(path.size() - 1));
        return plan("Move tag " + String.join("/", path) + " → " + String.join("/", to), occurrences, path, to);
    }

    private static Plan plan(String description, List<TagOccurrence> occurrences, List<String> from, List<String> to) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> targets = new LinkedHashMap<>();
        Set<TextId> files = new LinkedHashSet<>();
        List<FieldChange> changes = new ArrayList<>();
        int merged = 0;
        for (TagOccurrence o : occurrences) {
            if (o.isUnder(from)) {
                List<String> rewritten = new ArrayList<>(to);
                rewritten.addAll(o.path().subList(from.size(), o.path().size()));
                String keyword = String.join("/", rewritten);
                counts.merge(o.keyword(), 1, Integer::sum);
                targets.put(o.keyword(), keyword);
                files.add(o.text());
                changes.add(new FieldChange(o.tag(), Tag.TAG, keyword));
            } else if (o.isUnder(to)) {
                merged++;
            }
        }
        if (changes.isEmpty()) return new Refused("No tag " + String.join("/", from));
        List<Rewrite> rewrites = new ArrayList<>();
        counts.forEach((k, n) -> rewrites.add(new Rewrite(k, targets.get(k), n)));
        return new Change(description, List.copyOf(from), List.copyOf(to), rewrites, merged, files, changes);
    }

    /** "pos › verb". */
    public static String display(List<String> path) {
        return path.isEmpty() ? "the top level" : String.join(" › ", path);
    }

    /** The plan's change, if it is not refused. */
    public static Optional<Change> change(Plan plan) {
        return plan instanceof Change c ? Optional.of(c) : Optional.empty();
    }
}
