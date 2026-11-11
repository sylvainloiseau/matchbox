package fr.cnrs.lacito.matchbox.grammar.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Interlinear glossed text (IGT) between two {@code :::} lines, with an optional metadata block;
 * every other line is ignored. Produces {@code InterlinearGlossedText}s as written; how their lines
 * are read (roles, inherited metadata, alignment) is computed by {@code corpus.igt}.
 */
public class IgtGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "igt-grammar";
    public static final String KIND_IGT = ID + ":igt";
    public static final String KIND_METADATA = ID + ":metadata";
    public static final String KIND_ORTHOGRAPHIC = ID + ":orthographic-line";
    public static final String KIND_LINE = ID + ":line";
    public static final String KIND_TRANSLATION = ID + ":translation";
    public static final String KIND_TIMELINE = ID + ":timeline";

    /** Codes of the diagnostics. */
    public static final String CODE_METADATA = "igt.metadata";
    public static final String CODE_UNCLOSED = "igt.unclosed";
    public static final String CODE_DUPLICATE_KEY = "igt.duplicate-key";

    private static final ObjectEditor EDITOR = new IgtObjectEditor();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public String getDisplayName() {
        return "Interlinear glossed text";
    }

    @Override
    public String getDisplayDescription() {
        return "IGT between ::: lines: orthographic, transcription, gloss, translation and timeline lines, with metadata";
    }

    @Override
    public List<String> getObjectKinds() {
        return List.of(KIND_IGT, KIND_METADATA, KIND_ORTHOGRAPHIC, KIND_LINE, KIND_TRANSLATION, KIND_TIMELINE);
    }

    @Override
    public DisplayStyle getDefaultDisplayStyle(String kind) {
        if (kind == null) return DisplayStyle.EMPTY;
        return switch (kind) {
            case KIND_IGT -> new DisplayStyle("Monospaced", null, null, null, null, null, "#eef4ff");
            case KIND_METADATA -> new DisplayStyle(null, null, null, null, null, "#6b7a90", null);
            case KIND_ORTHOGRAPHIC -> new DisplayStyle(null, null, null, null, null, "#3d4f63", null);
            case KIND_TRANSLATION -> new DisplayStyle(null, null, true, null, null, null, null);
            case KIND_TIMELINE -> new DisplayStyle(null, null, null, null, null, "#b8b8b8", null);
            default -> DisplayStyle.EMPTY;
        };
    }

    @Override
    public ObjectEditor getObjectEditor() {
        return EDITOR;
    }

    @Override
    public String getFormatDocumentation() {
        return """
            An interlinear glossed text (IGT) is written between two lines `:::`; every other line of the file is
            ignored. The first `:::` may be followed by a metadata block: `:::{id="1.1", title="an example"}`
            (keys without `=`, `,`, quotes, braces nor spaces; values between double quotes, `\\"` and `\\\\` for a
            quote and a backslash). `::: note` (a Pandoc div) is not an IGT. A block without its closing `:::`
            is reported and not read.

            The lines of a block, in this order (Leipzig Glossing Rules):
            - orthographic lines (optional): the sentence in a spelling whose words need not match the
              transcription; not segmented;
            - the word level: transcription lines, gloss lines, then at most two timelines (starts, ends);
              words are separated by spaces;
            - the morpheme level: the same, words cut into morphemes with `-` (affix), `=` (clitic),
              `~` (reduplication) and `<…>` (infix); `.` `_` `\\` `:` `;` `>` belong to a gloss;
            - translation lines, between single quotes ('…' or ‘…’).
            Every segmented line has the same number of words; at the morpheme level, each word the same
            number of morphemes. A timeline has only numbers (milliseconds after the `offset` metadata).

            The role of a line is guessed (IPA or non-Latin letters: transcription; grammatical labels such
            as 1SG, PST: gloss; a leading line whose word count differs: orthographic), or declared by the
            `setting` metadata: `setting="o@fra,t'IPA'@fra,g@en"` (o orthographic, t transcription, g gloss;
            optional `:w` / `:m` level, 'name' and @language).

            Metadata: `id`, `title`, `olang`, `mlang` (languages), `media` (audio or video file), `offset`
            and `end` (ms), `form` and `gloss` (the lines compared with a dictionary: a line number or a
            name of the setting), `setting`. `setting`, `olang`, `mlang`, `form`, `gloss` and `media` are
            inherited from the previous IGTs, the enclosing sections and the file header.

            Problems reported: `igt.metadata`, `igt.unclosed` (parsing errors); `igt.duplicate-key` (semantic
            warning, fix: remove the later entry). The alignment of the lines is checked by the application.
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("A sentence with its orthography, IPA, glosses, timing and translation", """
                :::{id="1.1", olang="fra", mlang="en", media="O1.WAV", offset="1000"}
                je crois pas qu'elle est assez curieuse
                j'crois pas qu'elle est assez curieuse
                1 90 300 420 610 700
                ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
                1SG=believe not COMPL=3SG.F be\\3SG.PRES enough curious-FEM
                'I don't think she is curious enough'
                :::
                """),
            new GrammarExample("A transcription, its glosses and a translation", """
                :::
                a mio-jaflo-m-wi-j
                1sg down-canoe-top-m.sg-pred
                'I went down in canoe'
                :::
                """));
    }

    @Override
    public String getGrammarSource() {
        StringBuilder s = new StringBuilder();
        for (String name : List.of("IgtLexer.g4", "IgtParser.g4")) {
            try (InputStream in = IgtGrammarPlugin.class.getResourceAsStream(name)) {
                if (in != null) s.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                return "";
            }
        }
        return s.toString();
    }

    @Override
    public ParseResult getResult(TextId text, String content) {
        List<CorpusObject> objects = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            var lexer = new IgtLexer(CharStreams.fromString(content));
            var parser = new IgtParser(new CommonTokenStream(lexer));
            var errors = new CollectingErrorListener(text, ID);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.removeErrorListeners();
            parser.addErrorListener(errors);
            IgtBuilder builder = new IgtBuilder(text, content, diagnostics);
            for (IgtParser.BlockContext block : parser.document().block()) {
                if (block.exception != null || block.START() == null) continue;
                builder.build(block).ifPresent(objects::add);
            }
            diagnostics.addAll(0, errors.getDiagnostics());
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.internal(text, ID, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, diagnostics);
    }
}
