package fr.cnrs.lacito.matchbox.grammar.liftpatchbox;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import java.util.List;

/**
 * The kinds of the objects of a LiftPatch plugin, one per family of verbs, so that each family
 * can have its own style (Project properties › Display…): {@code <grammar id>:create},
 * {@code :delete}, {@code :property}, {@code :move}, {@code :block}, {@code :directive},
 * {@code :pragma}.
 */
public final class LiftPatchKinds {

    public static final String CREATE = "create";
    public static final String DELETE = "delete";
    public static final String PROPERTY = "property";
    public static final String MOVE = "move";
    public static final String BLOCK = "block";
    public static final String DIRECTIVE = "directive";
    public static final String PRAGMA = "pragma";

    private static final List<String> NAMES = List.of(CREATE, DELETE, PROPERTY, MOVE, BLOCK, DIRECTIVE, PRAGMA);

    private LiftPatchKinds() {
    }

    /** The kinds of a grammar, for {@code GrammarPlugin.getObjectKinds()}. */
    public static List<String> all(String grammarId) {
        return NAMES.stream().map(n -> grammarId + ":" + n).toList();
    }

    /** The kind of an item: its verb family, a block or a directive. */
    public static String of(String grammarId, Item item) {
        return grammarId + ":" + switch (item) {
            case Block b -> BLOCK;
            case Directive d -> DIRECTIVE;
            case Command c -> switch (c.verb()) {
                case CREATE, UPSERT, ENSURE -> CREATE;
                case DELETE -> DELETE;
                case SET, UPDATE, CLEAR -> PROPERTY;
                case MOVE -> MOVE;
            };
        };
    }

    /** The default style of a kind (null: of all the grammar's objects, none). */
    public static DisplayStyle defaultStyle(String grammarId, String kind) {
        if (kind == null || !kind.startsWith(grammarId + ":")) {
            return DisplayStyle.EMPTY;
        }
        return switch (kind.substring(grammarId.length() + 1)) {
            case CREATE -> DisplayStyle.background("#e3f4e1");
            case DELETE -> DisplayStyle.EMPTY.withTextColor("#b3261e");
            case PROPERTY -> DisplayStyle.background("#e3eefb");
            case MOVE -> DisplayStyle.EMPTY.withTextColor("#6a3fa0");
            case DIRECTIVE -> DisplayStyle.EMPTY.withTextColor("#666666").withItalic(true);
            case PRAGMA -> DisplayStyle.EMPTY.withTextColor("#777777");
            default -> DisplayStyle.EMPTY;
        };
    }
}
