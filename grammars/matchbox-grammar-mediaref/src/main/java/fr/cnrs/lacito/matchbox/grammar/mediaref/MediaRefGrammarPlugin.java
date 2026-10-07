package fr.cnrs.lacito.matchbox.grammar.mediaref;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.AbstractGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.CollectingErrorListener;
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
 * Media markers: lines {@code @media [<file>] [<start>-<end>] [<caption>]} referring to a media
 * file, or a segment of it, as {@link MediaMarker}s. Every other line is ignored. The file of a
 * marker without file is given by the "media" metadata of its section or text header, resolved by
 * the application after parsing.
 */
public class MediaRefGrammarPlugin extends AbstractGrammarPlugin {

    public static final String ID = "mediaref-grammar";
    public static final String KIND_MARKER = ID + ":marker";

    private static final ObjectEditor EDITOR = new MediaRefObjectEditor();

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
        return "Media references";
    }

    @Override
    public String getDisplayDescription() {
        return "@media lines referring to audio or video files and segments of them";
    }

    @Override
    public String getFormatDocumentation() {
        return """
            Media markers refer to an audio or video file, or to a segment of it, on one line:

                @media <file> [<start>-<end>] [<caption>]
                @media <start>-<end> [<caption>]

            - The line starts with `@media`. A marker has a file, a time range, or both.
            - `<file>`: a path relative to the project or to the note, an absolute path or a `file:` URI;
              a path containing spaces, or that looks like a time range, is written between double quotes.
              Without a file, the file is the `media` metadata of the enclosing section, or of the text header.
            - `<start>-<end>`: two times in milliseconds (`12000`) or as clock times (`0:12.5`, `1:02:03.250`),
              separated by `-`; the end is after the start. Without a range, the marker refers to the whole file.
            - `<caption>`: optional free text describing the segment, up to the end of the line.
            - Every other line is ignored.
            """;
    }

    @Override
    public List<GrammarExample> getExamples() {
        return List.of(
            new GrammarExample("A segment of a file", "@media recordings/s1.wav 12000-15500\n"),
            new GrammarExample("Clock times and a caption, the file being given by the metadata",
                "@media 0:12.000-0:15.500 greeting\n"),
            new GrammarExample("A file with spaces in its name", "@media \"field trip/s1.wav\" 1:02:03.250-1:02:07\n"),
            new GrammarExample("A whole (short) file, with a caption", "@media recordings/jaflo.wav pronunciation of jaflo\n"));
    }

    @Override
    public String getGrammarSource() {
        StringBuilder source = new StringBuilder();
        for (String name : List.of("MediaRefLexer.g4", "MediaRefParser.g4")) {
            try (InputStream in = getClass().getResourceAsStream(name)) {
                if (in != null) {
                    source.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                }
            } catch (IOException e) {
                // the source is optional
            }
        }
        return source.toString();
    }

    @Override
    public ObjectEditor getObjectEditor() {
        return EDITOR;
    }

    @Override
    public ParseResult getResult(TextId text, String content) {
        List<CorpusObject> objects = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            var lexer = new MediaRefLexer(CharStreams.fromString(content));
            var parser = new MediaRefParser(new CommonTokenStream(lexer));
            var errors = new CollectingErrorListener(text, ID);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errors);
            parser.removeErrorListeners();
            parser.addErrorListener(errors);
            var reader = new MediaRefReader(text, content);
            parser.document().accept(reader);
            objects.addAll(reader.markers());
            diagnostics.addAll(errors.getDiagnostics());
            diagnostics.addAll(reader.diagnostics());
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.at(text, ID, 0, 0, "Internal error of the grammar: " + e));
        }
        return new ParseResult(objects, diagnostics);
    }
}
