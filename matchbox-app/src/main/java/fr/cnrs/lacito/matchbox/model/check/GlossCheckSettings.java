package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The gloss check of a project (stored in the .mbx file): the list of grammatical labels the glosses
 * are checked against, the project's own labels, and whether (form, gloss) pairs are checked against
 * the context dictionary.
 */
public record GlossCheckSettings(GlossLabelList labels, Set<String> projectLabels, boolean dictionary) {

    public GlossCheckSettings {
        labels = labels == null ? GlossLabelList.LEIPZIG : labels;
        TreeSet<String> upper = new TreeSet<>();
        if (projectLabels != null) {
            projectLabels.stream().map(l -> l.strip().toUpperCase(Locale.ROOT)).filter(l -> !l.isEmpty()).forEach(upper::add);
        }
        projectLabels = Set.copyOf(upper);
    }

    /** A new project: the Leipzig list, the dictionary checked. */
    public static GlossCheckSettings defaults() {
        return new GlossCheckSettings(GlossLabelList.LEIPZIG, Set.of(), true);
    }

    public GlossCheckSettings withLabel(String label) {
        TreeSet<String> next = new TreeSet<>(projectLabels);
        next.add(label);
        return new GlossCheckSettings(labels, next, dictionary);
    }
}
