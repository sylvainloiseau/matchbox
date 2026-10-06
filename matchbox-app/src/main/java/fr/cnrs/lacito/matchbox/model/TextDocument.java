package fr.cnrs.lacito.matchbox.model;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * A text file of the project: the owner of its current (possibly unsaved) content.
 *
 * <p>Every change of the text goes through this class ({@link #applyEdits}, typing included): the
 * editor forwards the user's typing and mirrors the changes made by others (views, assistant,
 * search, undo), so that the editor, the document and the views cannot drift apart. Each applied
 * change is sent to the {@linkplain #addChangeListener listeners} as a {@link DocumentChange}.
 * Changes are made from one thread (the JavaFX thread in the GUI); {@link #getCurrentText()} and
 * {@link #getVersion()} can be read from any thread.
 *
 * <p>The file is read with the charset given when it is opened, and saved with the same charset.
 * Bytes that are not valid in that charset are reported instead of being silently replaced
 * (which would corrupt the file when it is saved).
 *
 * <p>Not to be confused with {@link fr.cnrs.lacito.matchbox.corpus.Text}, the immutable result of
 * one parse of this document.
 */
public class TextDocument {

    private volatile String content;
    private volatile long version;
    /** The text as last read or written: the document is dirty when it differs. */
    private String savedContent;
    private final Path filePath;
    private final TextId id;
    private final Charset charset;

    private final ReadOnlyBooleanWrapper couldBeWritten = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper couldBeReParsed = new ReadOnlyBooleanWrapper(false);
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();

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
        this.id = TextId.of(filePath);
        this.charset = charset;
        this.content = read(filePath, charset);
        this.savedContent = content;
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

    /** Id of the text, in the spans of its corpus objects. */
    public TextId getId() {
        return id;
    }

    /** Charset the file was read with, and is saved with. */
    public Charset getCharset() {
        return charset;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getCurrentText() {
        return content;
    }

    /** Incremented at each change of the text; used to discard results of outdated parses. */
    public long getVersion() {
        return version;
    }

    // ------------------------------------------------------------------
    // Changes
    // ------------------------------------------------------------------

    /**
     * Applies non-overlapping edits, all relative to the current text, as one change.
     *
     * @return false, and nothing is applied, if an edit no longer matches the text
     * @throws IllegalArgumentException if edits overlap
     */
    public boolean applyEdits(List<TextEdit> edits, EditOrigin origin) {
        if (edits.isEmpty()) {
            return true;
        }
        String before = content;
        if (!edits.stream().allMatch(e -> e.matches(before))) {
            return false;
        }
        String after = TextEdit.applyAll(before, edits);
        List<TextEdit> sorted = edits.stream().sorted(Comparator.comparingInt(TextEdit::start)).toList();
        commit(after, sorted, origin);
        return true;
    }

    /**
     * Applies edits one after the other (each relative to the text left by the previous one) as
     * one change, e.g. a selection moved by drag-and-drop.
     *
     * @return false, and nothing is applied, if an edit does not match the text it applies to
     */
    public boolean applySequential(List<TextEdit> edits, EditOrigin origin) {
        if (edits.isEmpty()) {
            return true;
        }
        if (edits.size() == 1) {
            return applyEdits(edits, origin);
        }
        String text = content;
        for (TextEdit e : edits) {
            if (!e.matches(text)) {
                return false;
            }
            text = e.apply(text);
        }
        replaceAll(text, origin);
        return true;
    }

    /** Replaces the whole text, as one minimal edit (the common prefix and suffix are kept). */
    public void replaceAll(String newText, EditOrigin origin) {
        minimalEdit(content, newText).ifPresent(e -> commit(newText, List.of(e), origin));
    }

    /** The smallest edit turning {@code before} into {@code after}; empty if they are equal. */
    static java.util.Optional<TextEdit> minimalEdit(String before, String after) {
        if (before.equals(after)) {
            return java.util.Optional.empty();
        }
        int prefix = 0;
        int max = Math.min(before.length(), after.length());
        while (prefix < max && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix
            && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        return java.util.Optional.of(TextEdit.replace(before, prefix, before.length() - suffix,
            after.substring(prefix, after.length() - suffix)));
    }

    private void commit(String newContent, List<TextEdit> sortedEdits, EditOrigin origin) {
        long versionBefore = version;
        content = newContent;
        version = versionBefore + 1;
        couldBeWritten.set(!isSaved());
        couldBeReParsed.set(true);
        DocumentChange change = new DocumentChange(this, sortedEdits, origin, versionBefore, version);
        for (DocumentListener l : listeners) {
            l.changed(change);
        }
    }

    private boolean isSaved() {
        return content.length() == savedContent.length() && content.equals(savedContent);
    }

    public void addChangeListener(DocumentListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(DocumentListener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------
    // Parse and save state
    // ------------------------------------------------------------------

    /** Marks the document as parsed if {@code parsedVersion} is still its version. */
    public void markParsed(long parsedVersion) {
        if (parsedVersion == version) {
            couldBeReParsed.set(false);
        }
    }

    /**
     * Writes the content to the file.
     */
    public void writeFile() throws IOException {
        String text = content;
        try {
            Files.writeString(filePath, text, charset);
        } catch (CharacterCodingException e) {
            throw new IOException(
                "The text of " + filePath + " contains characters that cannot be written in " + charset.name() +
                    ". Remove them, or convert the file to another charset (e.g. UTF-8).", e
            );
        }
        savedContent = text;
        couldBeWritten.set(!isSaved());
    }

    /** True if the text differs from the text last read or written. */
    public boolean getCouldBeWritten() {
        return couldBeWritten.get();
    }

    public ReadOnlyBooleanProperty getCouldBeWrittenBooleanProperty() {
        return couldBeWritten.getReadOnlyProperty();
    }

    /** True if the text changed since the last applied parse. */
    public boolean getCouldBeReparsed() {
        return couldBeReParsed.get();
    }

    public ReadOnlyBooleanProperty getCouldBeReparsedBooleanProperty() {
        return couldBeReParsed.getReadOnlyProperty();
    }

    @Override
    public String toString() {
        return "TextDocument[" + filePath + "]";
    }

    /** Edits of a change, made relative to the text after the previous edits (used by undo). */
    static List<TextEdit> shiftedForward(List<TextEdit> sortedEdits) {
        List<TextEdit> result = new ArrayList<>();
        int delta = 0;
        for (TextEdit e : sortedEdits) {
            result.add(e.shifted(delta));
            delta += e.delta();
        }
        return result;
    }
}
