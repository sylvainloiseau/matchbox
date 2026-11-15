package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.BlockHeader;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;

/**
 * A command that changes a dictionary (LiftPatch: create an entry, add a sense, set a gloss,
 * delete a form…), written in a text in one of the notations of the LiftPatch language. The
 * commands of a project, in the project's file order and then in text order, are applied to
 * the project's context dictionary, as a history of that dictionary.
 *
 * <p>The command is held as the syntax-independent command model of lift-patchbox-core
 * ({@link Item}: a {@link Command}, a {@link Block} or a {@link Directive}), whatever the
 * notation that produced it; its source range is the object's {@code TextCoordinate}. A block's
 * inner commands are its {@linkplain #getChildren() children}; its {@link #item()} holds them
 * too.</p>
 *
 * <p>Gate of the history: a plugin producing mutation commands reports its syntax and static
 * errors with diagnostic codes starting with {@link #DIAGNOSTIC_CODE_PREFIX}; while any such
 * error exists in the project, the commands are not applied at all.</p>
 */
public interface LexiconMutationCommand extends CorpusObject {

    /**
     * The prefix of the codes of the syntax and static errors of mutation commands (e.g.
     * {@code liftpatch.SYNTAX_ERROR}, {@code liftpatch.UNKNOWN_LABEL}).
     */
    String DIAGNOSTIC_CODE_PREFIX = "liftpatch.";

    /** The command model: a command, a block of commands, or a declaration directive. */
    Item item();

    /** The notation it was written in, as LiftPatch names it ("LiftPatchShort", "LiftPatchRef"). */
    String syntax();

    /** The command as written (a block: its heading command; a directive: its rendering). */
    default String sourceText() {
        return switch (item()) {
            case Command c -> c.sourceText() == null ? c.toString() : c.sourceText();
            case Block b -> b.header() instanceof BlockHeader.Anchor a && a.command().sourceText() != null
                ? a.command().sourceText()
                : b.header().toString();
            case Directive d -> d.toString();
        };
    }

    @Override
    default String getLabel() {
        return sourceText();
    }
}
