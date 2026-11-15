package fr.cnrs.lacito.matchbox.grammar.liftpatchbox.refsyntax;

import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.matchbox.grammar.liftpatchbox.AbstractLiftPatchGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import java.util.List;

/**
 * Dictionary commands in LiftPatch's reference syntax (LiftPatchRef): a file of commands only
 * ({@code create entry(form@tww = "mami") { create sense(gloss@en = "pig") }}), every line part
 * of the script; typically for a file type {@code .*\.liftpatchref}.
 */
public class LiftPatchRefGrammarPlugin extends AbstractLiftPatchGrammarPlugin {

    public static final String ID = "lift-patchbox-ref-grammar";

    public LiftPatchRefGrammarPlugin() {
        super(Syntax.REFERENCE);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "LiftPatch commands (reference)";
    }

    @Override
    public String getDisplayDescription() {
        return "Dictionary commands in LiftPatchRef, in files holding commands only";
    }

    @Override
    public String getFormatDocumentation() {
        return """
            A script of commands that change the context dictionary (Project properties › Inherited
            context), in LiftPatch's reference syntax. Every line belongs to the script: use this plugin
            alone, for a file type such as `.*\\.liftpatchref`. The commands are applied in the project's
            file order, then in the order of the text; see the Dictionary History mode.

                create entry(form@tww = "mami") {
                    create sense(gloss@en = "pig")
                }
                set definition@en = "A pig" on sense[gloss@en = "pig"] of entry[form@tww = "mami"]

            - Verbs: create, upsert, ensure, delete, move, set, update, clear; `{ … }` blocks; labels
              (`as $mami`); `#` comments.
            - Syntax errors are listed in Parsing errors, the other problems in Semantic issues. While
              any error remains, no command of the project is applied.
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("An entry with a sense",
                "create entry(form@tww = \"mami\") {\n    create sense(gloss@en = \"pig\")\n}\n"),
            new GrammarExample("A label",
                "create entry(form@tww = \"mimi\") as $new\ncreate relation(type = \"synonym\", target = $new) under entry[form@tww = \"memi\"]\n"));
    }
}
