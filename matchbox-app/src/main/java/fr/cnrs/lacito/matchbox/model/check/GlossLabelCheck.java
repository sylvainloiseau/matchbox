package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabels;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossParts;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import java.util.ArrayList;
import java.util.List;

/** The grammatical labels of the glosses of IGTs that are not in the project's list (Leipzig, Lehmann, both) nor its own labels. */
public final class GlossLabelCheck implements CorpusCheck {

    public static final String ID = "Gloss check";
    public static final String CODE = "gloss.unknown-label";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Diagnostic> check(Text text, CheckInputs inputs) {
        GlossCheckSettings settings = inputs.settings();
        if (settings.labels() == GlossLabelList.NONE) return List.of();
        String list = switch (settings.labels()) {
            case LEIPZIG -> "the Leipzig list";
            case LEHMANN -> "Lehmann's list";
            default -> "the Leipzig and Lehmann lists";
        };
        List<Diagnostic> result = new ArrayList<>();
        for (InterlinearReading r : InterlinearReadings.of(text).all()) {
            for (LineReading l : r.lines(LineRole.GLOSS)) {
                for (Word w : l.words()) {
                    for (Morpheme m : w.morphemes()) {
                        for (String part : GlossParts.grammatical(m.form())) {
                            if (GlossLabels.isLabel(part, settings.labels(), settings.projectLabels())) continue;
                            result.add(CorpusCheck.warning(text, ID, m.span().startIndex(),
                                Math.max(m.span().startIndex(), m.span().stopIndex()), m.span().startLine(),
                                part + " is not in " + list + ": add it to the project's labels (Project properties › Gloss check)",
                                CODE));
                        }
                    }
                }
            }
        }
        return result;
    }
}
