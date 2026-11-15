package fr.cnrs.lacito.matchbox.grammar.liftpatchbox.shortsyntax;

import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.matchbox.grammar.liftpatchbox.AbstractLiftPatchGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import java.util.List;

/**
 * Dictionary commands in LiftPatch's concise syntax (LiftPatchShort), mixed with any other
 * lines of the notes: a line is a command when it starts with a command letter followed by
 * what a command can start with ({@code c e("mami", s("pig"))}, {@code p /mami/pig}); indented
 * lines below a command form a block. Every other line is ignored.
 */
public class LiftPatchShortGrammarPlugin extends AbstractLiftPatchGrammarPlugin {

    public static final String ID = "lift-patchbox-short-grammar";

    public LiftPatchShortGrammarPlugin() {
        super(Syntax.CONCISE);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "LiftPatch commands (concise)";
    }

    @Override
    public String getDisplayDescription() {
        return "Dictionary commands in LiftPatchShort, among the other lines of the notes";
    }

    @Override
    public String getFormatDocumentation() {
        return """
            Commands that change the context dictionary (Project properties › Inherited context), one per
            line, among the other lines of the notes (LiftPatch, concise syntax). They are applied in the
            project's file order, then in the order of the text; see the Dictionary History mode.

                c e("mami", s("pig"))          create the entry mami with a sense "pig"
                p /mami/pig                    make sure mami has a sense "pig" (create what is missing)
                s /mami/pig (d@en = "A pig")   set the definition of that sense
                d /efe                         delete the entry efe

            - The command letter: `c` create, `p` upsert, `e` ensure, `d` delete, `m` move, `s` set,
              `u` update, `l` clear; `p /form/gloss` makes sure the entry has that sense.
            - A path `/form/gloss/example` goes down from an entry to a sense and an example.
            - Lines indented under a command form a block: their paths start at its component.
            - Every other line is ignored. A line of prose that looks like a command (`p /a/ …`) is read as
              one and gives a syntax error: declare a sigil on the first line (`%liftpatch 1.1 sigil=">"`)
              and start each command with it (`> c e("mami")`): the ✚ "Declare LiftPatch sigil" action does it.
            - Syntax errors are listed in Parsing errors, the other problems (unknown label, missing
              entry…) in Semantic issues. While any error remains, no command of the project is applied.
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("A new entry with a sense", "c e(\"mami\", s(\"pig\"))\n"),
            new GrammarExample("Make sure a form has a gloss", "p /mami/pig\n"),
            new GrammarExample("A block: the definition of the new sense",
                "c e(\"mimi\")\n  c s(\"lizard\")\n    s (d@en = \"A lizard\")\n"),
            new GrammarExample("Commands among notes, with a sigil",
                "%liftpatch 1.1 sigil=\">\"\nThe pig is /mami/ in Tuwari.\n> p /mami/pig\n"));
    }
}
