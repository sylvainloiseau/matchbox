package fr.cnrs.lacito.matchbox.model.lexicon.history;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.BlockHeader;
import fr.cnrs.lacito.liftpatchbox.ast.Chain;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Constructor;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Initializer;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.ast.Predicate;
import fr.cnrs.lacito.liftpatchbox.ast.Step;
import fr.cnrs.lacito.liftpatchbox.ast.Value;
import java.util.Optional;

/**
 * The form of the entry a command is about, read from the command model alone: before the history
 * is computed, or for a command that was not applied. The entry created by
 * {@code create entry(form = …)}, or the {@code entry[form = …]} step of its target or parent
 * chain (the first step of a {@code p /form/gloss} path).
 */
public final class CommandTargets {

    private CommandTargets() {
    }

    public static Optional<String> entryForm(Item item) {
        return switch (item) {
            case Block b -> b.header() instanceof BlockHeader.Anchor a ? entryForm(a.command())
                : b.header() instanceof BlockHeader.Selector s ? form(s.chain()) : Optional.empty();
            case Directive d -> Optional.empty();
            case Command.Create c -> constructor(c.constructor()).or(() -> form(c.parent()));
            case Command.Upsert c -> constructor(c.constructor()).or(() -> form(c.parent()));
            case Command.Ensure c -> step(c.target()).or(() -> form(c.parent()));
            case Command.Delete c -> step(c.target()).or(() -> form(c.parent()));
            case Command.Move c -> step(c.target()).or(() -> form(c.sourceParent())).or(() -> form(c.destinationParent()));
            case Command.Set c -> form(c.target());
            case Command.Update c -> form(c.target());
            case Command.Clear c -> form(c.target());
        };
    }

    private static Optional<String> constructor(Constructor c) {
        if (c == null || !"entry".equals(c.componentType())) return Optional.empty();
        for (Initializer i : c.initializers()) {
            if (i instanceof Initializer.Assignment a && "form".equals(a.property().name())
                && a.value() instanceof Value.Str s) {
                return Optional.of(s.text());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> form(Chain chain) {
        if (chain == null) return Optional.empty();
        for (Step s : chain.steps()) {
            Optional<String> f = step(s);
            if (f.isPresent()) return f;
        }
        return Optional.empty();
    }

    private static Optional<String> step(Step step) {
        if (step instanceof Step.Component c && "entry".equals(c.componentType())) {
            for (Predicate p : c.predicates()) {
                if (p instanceof Predicate.Comparison cmp && "form".equals(cmp.property().name())
                    && cmp.value() instanceof Value.Str s) {
                    return Optional.of(s.text());
                }
            }
        }
        return Optional.empty();
    }
}
