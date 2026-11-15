package fr.cnrs.lacito.matchbox.grammar.liftpatchbox;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import fr.cnrs.lacito.liftpatchbox.error.SourceRange;
import fr.cnrs.lacito.liftpatchbox.language.LiftPatchBox;
import fr.cnrs.lacito.liftpatchbox.language.Pragma;
import fr.cnrs.lacito.liftpatchbox.language.ScriptCheck;
import fr.cnrs.lacito.liftpatchbox.language.SourceScript;
import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.liftpatchbox.language.error.SyntaxWarningCode;
import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.LexiconMutationCommand;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleLexiconMutationCommand;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a text with lift-patchbox ({@link LiftPatchBox#check}: every syntax and static error,
 * and the commands that parsed) into corpus objects and diagnostics:
 *
 * <ul>
 *   <li>one {@link SimpleLexiconMutationCommand} per top-level item, a block with its inner
 *       commands as children, anchored to the item's {@link SourceRange};</li>
 *   <li>the pragma as a {@link LiftPatchPragma};</li>
 *   <li>syntax errors as {@link Diagnostic.Category#SYNTAX} errors, static errors as
 *       {@link Diagnostic.Category#SEMANTIC} errors, warnings as semantic warnings, all with
 *       the code {@code liftpatch.<code>} ({@link LexiconMutationCommand#DIAGNOSTIC_CODE_PREFIX}).</li>
 * </ul>
 *
 * <p>Which files a plugin reads follows LiftPatch's own rule for the syntax of a script: the
 * {@code syntax=} attribute of the pragma, else the extension ({@code .liftpatchref} for the
 * reference syntax). The reference syntax reads only such files: a file of notes, which every
 * other line of would be a syntax error, is not a reference script. The concise syntax reads every
 * other file. A file whose pragma declares the other syntax gives one warning and no object, so
 * that the two plugins never read the same file. A fatal error (malformed sigil, unsupported
 * version) gives its diagnostic and no object.</p>
 */
public final class LiftPatchReader {

    private final String grammarId;
    private final Syntax syntax;

    /** One per plugin: an instance holds only immutable configuration (thread-safe). */
    private final LiftPatchBox box = new LiftPatchBox();

    public LiftPatchReader(String grammarId, Syntax syntax) {
        this.grammarId = grammarId;
        this.syntax = syntax;
    }

    /** The objects and diagnostics of a text. */
    public ParseResult read(TextId text, String content) {
        Syntax declared = declaredSyntax(content);
        Syntax byName = Syntax.fromFileName(text.path().getFileName() == null ? "" : text.path().getFileName().toString());
        if (declared == null && (syntax == Syntax.REFERENCE ? byName != Syntax.REFERENCE : byName == Syntax.REFERENCE)) {
            return new ParseResult(List.of(), List.of());   // not a script of this syntax
        }
        ScriptCheck check = box.check(content, syntax, text.toString());
        List<Diagnostic> diagnostics = new ArrayList<>();
        SourceScript script = check.script();
        if (script != null && script.syntax() != syntax) {
            SourceRange at = script.pragma() == null ? SourceRange.UNKNOWN : script.pragma().range();
            diagnostics.add(diagnostic(text, content, at,
                "This file declares " + script.syntax().pragmaName() + ": add the "
                    + script.syntax().pragmaName() + " plugin to its file type",
                "liftpatch.OTHER_SYNTAX", Diagnostic.Severity.WARNING, Diagnostic.Category.SEMANTIC));
            return new ParseResult(List.of(), diagnostics);
        }
        for (LiftPatchError e : check.syntaxErrors()) {
            diagnostics.add(diagnostic(text, content, e.range(), e.message(), code(e.code().id()),
                Diagnostic.Severity.ERROR, Diagnostic.Category.SYNTAX));
        }
        for (LiftPatchError e : check.staticErrors()) {
            diagnostics.add(diagnostic(text, content, e.range(), e.message(), code(e.code().id()),
                Diagnostic.Severity.ERROR, Diagnostic.Category.SEMANTIC));
        }
        for (LiftPatchWarning w : check.warnings()) {
            if (w.code() == SyntaxWarningCode.NO_COMMAND_RECOGNIZED) {
                continue;   // a file of notes without any command is the usual case
            }
            diagnostics.add(diagnostic(text, content, w.range(), w.message(), code(w.code().id()),
                Diagnostic.Severity.WARNING, Diagnostic.Category.SEMANTIC));
        }
        List<CorpusObject> objects = new ArrayList<>();
        if (script != null) {
            Pragma pragma = script.pragma();
            if (pragma != null && pragma.range().isKnown()) {
                objects.add(new LiftPatchPragma(grammarId + ":" + LiftPatchKinds.PRAGMA,
                    coordinate(text, pragma.range()), pragma.version()));
            }
            for (Item item : script.script().items()) {
                SimpleLexiconMutationCommand command = command(text, item);
                if (command != null) {
                    objects.add(command);
                }
            }
        }
        return new ParseResult(objects, diagnostics);
    }

    private SimpleLexiconMutationCommand command(TextId text, Item item) {
        if (!item.range().isKnown()) {
            return null;
        }
        List<SimpleLexiconMutationCommand> children = new ArrayList<>();
        if (item instanceof Block block) {
            for (Item inner : block.body()) {
                SimpleLexiconMutationCommand child = command(text, inner);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        return new SimpleLexiconMutationCommand(LiftPatchKinds.of(grammarId, item), coordinate(text, item.range()),
            item, syntax.pragmaName(), children);
    }

    /**
     * The syntax a {@code %liftpatch} pragma declares, read as lift-patchbox reads it: the first
     * line starting with {@code %liftpatch} before any other non-blank, non-comment line.
     */
    static Syntax declaredSyntax(String content) {
        for (String raw : content.split("\\R", 200)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.startsWith("%liftpatch")) return null;
            int at = line.indexOf("syntax=");
            if (at < 0 || at + 8 > line.length()) return null;
            char quote = line.charAt(at + 7);
            int close = line.indexOf(quote, at + 8);
            return close < 0 ? null : Syntax.fromPragmaName(line.substring(at + 8, close));
        }
        return null;
    }

    private static String code(String id) {
        return LexiconMutationCommand.DIAGNOSTIC_CODE_PREFIX + id;
    }

    /** A known range as a span ({@code stopIndex} inclusive; an empty range spans nothing). */
    static TextCoordinate coordinate(TextId text, SourceRange range) {
        int endLine = range.endLine();
        if (range.endColumn() == 1 && endLine > range.startLine()) {
            endLine--;   // the range ends at a line start: its last character is on the line before
        }
        return new TextCoordinate(text, range.startOffset(), range.endOffset() - 1, range.startLine(), endLine);
    }

    private Diagnostic diagnostic(TextId text, String content, SourceRange range, String message, String code,
                                  Diagnostic.Severity severity, Diagnostic.Category category) {
        if (!range.isKnown()) {
            return new Diagnostic(text, grammarId, 1, 0, message, -1, -1, List.of(), severity, category, code,
                List.of());
        }
        int start = Math.min(range.startOffset(), Math.max(0, content.length() - 1));
        // An empty range (a place) is shown on the character there.
        int stop = range.isEmpty() ? start : range.endOffset() - 1;
        if (content.isEmpty()) {
            start = -1;
            stop = -1;
        }
        return new Diagnostic(text, grammarId, range.startLine(), range.startColumn() - 1, message, start, stop,
            List.of(), severity, category, code, List.of());
    }
}
