package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import java.util.List;
import java.util.Objects;

/** An immutable mutation command: its command model, its notation, and the commands of a block. */
public final class SimpleLexiconMutationCommand extends AbstractCorpusObject implements LexiconMutationCommand {

    private final Item item;
    private final String syntax;
    private final List<SimpleLexiconMutationCommand> children;

    public SimpleLexiconMutationCommand(String kind, TextCoordinate coordinate, Item item, String syntax) {
        this(kind, coordinate, item, syntax, List.of());
    }

    /** @param children the inner commands of a block, in order; empty for a command or a directive */
    public SimpleLexiconMutationCommand(String kind, TextCoordinate coordinate, Item item, String syntax,
                                        List<SimpleLexiconMutationCommand> children) {
        super(kind, coordinate);
        this.item = Objects.requireNonNull(item, "item");
        this.syntax = Objects.requireNonNull(syntax, "syntax");
        this.children = List.copyOf(children);
    }

    @Override
    public Item item() {
        return item;
    }

    @Override
    public String syntax() {
        return syntax;
    }

    @Override
    public List<SimpleLexiconMutationCommand> getChildren() {
        return children;
    }
}
