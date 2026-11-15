package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The mutation commands of one text: one script of the history, the commands in text order.
 *
 * @param text     the text
 * @param commands its top-level commands (a block holds its own), in text order
 * @param errors   its syntax and static errors of mutation commands (codes starting with
 *                 {@link LexiconMutationCommand#DIAGNOSTIC_CODE_PREFIX}): gate 1 of the history
 */
public record ScriptSource(TextId text, List<LexiconMutationCommand> commands, List<Diagnostic> errors) {

    public ScriptSource {
        commands = commands.stream()
            .sorted(Comparator.comparingInt(c -> c.getTextCoordinate().startIndex()))
            .toList();
        errors = List.copyOf(errors);
    }

    /** The script of a parsed text; empty commands and errors when it has none. */
    public static ScriptSource of(Text text) {
        List<LexiconMutationCommand> commands = new ArrayList<>();
        for (var o : text.objects()) {
            if (o instanceof LexiconMutationCommand c) {
                commands.add(c);
            }
        }
        List<Diagnostic> errors = text.diagnostics().stream()
            .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
            .filter(d -> d.code() != null && d.code().startsWith(LexiconMutationCommand.DIAGNOSTIC_CODE_PREFIX))
            .toList();
        return new ScriptSource(text.id(), commands, errors);
    }

    /** Whether the text takes part in the history: it has commands or errors of commands. */
    public boolean isEmpty() {
        return commands.isEmpty() && errors.isEmpty();
    }

    /**
     * What the history depends on in this text: the commands (their model and place) and the
     * errors. Typing prose leaves it unchanged.
     */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder(text.toString()).append('\n');
        for (LexiconMutationCommand c : commands) {
            sb.append(c.getTextCoordinate().startIndex()).append(':').append(c.getTextCoordinate().stopIndex())
                .append(' ').append(c.item()).append('\n');
        }
        for (Diagnostic d : errors) {
            sb.append("! ").append(d.code()).append('@').append(d.startIndex()).append(' ').append(d.message()).append('\n');
        }
        return sb.toString();
    }

    /** The script lift-patchbox runs: the commands' items in text order. */
    public Script script() {
        List<Item> items = commands.stream().map(LexiconMutationCommand::item).toList();
        String syntax = commands.isEmpty() ? "LiftPatchShort" : commands.get(0).syntax();
        return new Script(items, text.toString(), syntax);
    }
}
