package fr.cnrs.lacito.matchbox.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * A text file of the project: its current (possibly unsaved) content.
 *
 * <p>The file is read with the charset given when it is opened, and saved with the same charset.
 * Bytes that are not valid in that charset are reported instead of being silently replaced
 * (which would corrupt the file when it is saved).
 */
public class TextDocument {

    private volatile String content;
    private volatile long version;
    private Path filePath;
    private final Charset charset;

    public SimpleBooleanProperty couldBeWritten = new SimpleBooleanProperty(
        false
    );
    public SimpleBooleanProperty couldBeReParsed = new SimpleBooleanProperty(
        false
    );

    public TextDocument(Path filePath) throws IOException {
        this(filePath, StandardCharsets.UTF_8);
    }

    /**
     * Reads a file.
     *
     * @throws IOException if the file cannot be read or is not valid text in {@code charset}
     */
    public TextDocument(Path filePath, Charset charset) throws IOException {
        this.filePath = filePath;
        this.charset = charset;
        this.content = read(filePath, charset);
    }

    /** Decodes a file, reporting the offset of the first byte that is not valid in the charset. */
    static String read(Path file, Charset charset) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(bytes);
        CharBuffer out = CharBuffer.allocate((int) Math.ceil(bytes.length * (double) decoder.maxCharsPerByte()) + 1);
        CoderResult result = decoder.decode(in, out, true);
        if (!result.isError()) {
            result = decoder.flush(out);
        }
        if (result.isError()) {
            throw new CharacterCodingException() {
                @Override
                public String getMessage() {
                    return "The file " + file + " is not valid " + charset.name() + " text (invalid byte at offset " +
                        in.position() + "). Choose the charset of this file in Settings, then open it again.";
                }
            };
        }
        out.flip();
        return out.toString();
    }

    /** Charset the file was read with, and is saved with. */
    public Charset getCharset() {
        return charset;
    }

    /** Marks the document as parsed (called when parse results matching its text are applied). */
    public void markParsed() {
        couldBeReParsed.set(false);
    }

    public void updateText(String newText) {
        if (newText.equals(this.content)) {
            return;
        }
        this.content = newText;
        this.version++;
        couldBeWritten.set(true);
        couldBeReParsed.set(true);
    }

    /** Incremented at each change of the text; used to discard results of outdated parses. */
    public long getVersion() {
        return version;
    }

    /**
     * Writes content to a file
     */
    public void writeFile() throws IOException {
        try {
            Files.writeString(filePath, content, charset);
        } catch (CharacterCodingException e) {
            throw new IOException(
                "The text of " + filePath + " contains characters that cannot be written in " + charset.name() +
                    ". Remove them, or convert the file to another charset (e.g. UTF-8).", e
            );
        }
        couldBeWritten.set(false);
    }

    public boolean getCouldBeWritten() {
        return couldBeWritten.get();
    }

    public SimpleBooleanProperty getCouldBeWrittenBooleanProperty() {
        return couldBeWritten;
    }

    public boolean getCouldBeReparsed() {
        return couldBeReParsed.get();
    }

    public SimpleBooleanProperty getCouldBeReparsedBooleanProperty() {
        return couldBeReParsed;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getCurrentText() {
        return content;
    }
}
