package fr.cnrs.lacito.matchbox.grammar.igt;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearEdits;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.grammarfx.TextAction;
import fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider;
import fr.cnrs.lacito.matchbox.grammarfx.ViewContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Actions on the start anchor line of each IGT of this grammar: Format IGT, Insert word at start /
 * at end, Add metadata block (when there is none), Declare line roles (when a role is guessed), and,
 * on the first IGT, Number the IGTs of this file.
 */
public class IgtTextActions implements TextActionProvider {

    @Override
    public List<TextAction> actionsFor(Text text, ViewContext context) {
        List<InterlinearGlossedText> igts = text.objects(IgtGrammarPlugin.ID).stream()
            .filter(InterlinearGlossedText.class::isInstance).map(InterlinearGlossedText.class::cast)
            .sorted(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex())).toList();
        if (igts.isEmpty()) return List.of();
        InterlinearReadings readings = InterlinearReadings.of(text);
        String content = text.content();
        List<TextAction> actions = new ArrayList<>();
        for (InterlinearGlossedText igt : igts) {
            InterlinearReading r = readings.of(igt).orElse(null);
            if (r == null) continue;
            int at = igt.getTextCoordinate().startIndex();
            add(actions, "Format IGT", "Align the words of the segmented lines in columns", at, InterlinearEdits.format(r, content));
            int words = r.lines().stream().filter(LineReading::isSegmented).mapToInt(l -> l.words().size()).max().orElse(0);
            add(actions, "Insert word at start", "Insert a word (" + InterlinearEdits.PLACEHOLDER + ") at the start of every segmented line",
                at, InterlinearEdits.insertWord(r, 0));
            add(actions, "Insert word at end", "Insert a word (" + InterlinearEdits.PLACEHOLDER + ") at the end of every segmented line",
                at, InterlinearEdits.insertWord(r, words));
            if (IgtObjectEditor.block(igt, content).map(b -> !b.present()).orElse(false)) {
                add(actions, "Add metadata block", "Insert {} after :::", at, IgtEdits.addMetadataBlock(igt));
            }
            if (r.lines().stream().anyMatch(l -> !l.declared() && l.role() != fr.cnrs.lacito.matchbox.corpus.capability.LineRole.TRANSLATION
                && l.role() != fr.cnrs.lacito.matchbox.corpus.capability.LineRole.TIMELINE)) {
                add(actions, "Declare line roles", "Write the roles of the lines, as they are read, in the setting: " + IgtEdits.setting(r),
                    at, IgtEdits.declareRoles(r, content));
            }
        }
        InterlinearGlossedText first = igts.get(0);
        add(actions, "Number the IGTs of this file", "Write id=\"1\", id=\"2\"… on the " + igts.size() + " IGTs, in order",
            first.getTextCoordinate().startIndex(), IgtEdits.number(igts, content));
        return actions;
    }

    private static void add(List<TextAction> actions, String label, String description, int offset, List<TextEdit> edits) {
        if (!edits.isEmpty()) actions.add(new TextAction(label, description, offset, edits, offset));
    }
}
