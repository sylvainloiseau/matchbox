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
import java.util.concurrent.atomic.AtomicLong;
import fr.cnrs.lacito.matchbox.corpus.EditRebase;
import java.util.Optional;
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

    /**
     * Source of the versions of all the documents: a version names one state of one document,
     * even across documents and a file removed and added again.
     */
    private static final AtomicLong VERSIONS = new AtomicLong();

    private volatile String content;
    private volatile long version = VERSIONS.incrementAndGet();
    private final ChangeLog changeLog = new ChangeLog();
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

    /**
     * Changed at each change of the text; used to discard results of outdated parses and to move
     * edits computed against an earlier version ({@link #rebase}). Unique across all documents;
     * only equality is meaningful.
     */
    public long getVersion() {
        return version;
    }

    /**
     * The changes made since {@code version}, oldest first (an empty list for the current
     * version); empty if {@code version} is not a version of this document or its changes were
     * forgotten (at most {@link ChangeLog#CAPACITY} changes are kept).
     */
    public Optional<List<DocumentChange>> changesSince(long version) {
        return changeLog.since(version, this.version);
    }

    /** Result of {@link #rebase}. */
    public sealed interface Rebased {
        /** The edits, relative to the current text, in the order given. */
        record Moved(List<TextEdit> edits) implements Rebased {}

        /** An edit meets characters changed since the version; {@code line} (1-based, current text) of the change. */
        record Conflict(int line) implements Rebased {}

        /** The changes since the version are not known. */
        record Unknown() implements Rebased {}
    }

    /**
     * Moves edits computed against version {@code version} of this document, whose text was
     * {@code textAtVersion}, to the current text: through the net change since then
     * ({@link EditRebase#compose}), by position only. Typing inside an edit's range and undoing it
     * is no conflict. Read from the thread that changes the document.
     */
    public Rebased rebase(List<TextEdit> edits, long version, String textAtVersion) {
        Optional<List<DocumentChange>> since = changesSince(version);
        if (since.isEmpty()) {
            return new Rebased.Unknown();
        }
        if (since.get().isEmpty()) {
            return new Rebased.Moved(List.copyOf(edits));
        }
        List<TextEdit> net;
        try {
            net = EditRebase.compose(textAtVersion, since.get().stream().map(DocumentChange::edits).toList());
            if (!TextEdit.applyAll(textAtVersion, net).equals(content)) {
                return new Rebased.Unknown();
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // textAtVersion was not the text of that version
            return new Rebased.Unknown();
        }
        return switch (EditRebase.rebase(edits, List.of(net))) {
            case EditRebase.Result.Moved m -> new Rebased.Moved(m.edits());
            case EditRebase.Result.Conflict c -> {
                String current = content;
                int at = EditRebase.offset(c.change().start(), EditRebase.Bias.BEFORE, List.of(net));
                yield new Rebased.Conflict(lineOf(current, Math.min(at, current.length())));
            }
        };
    }

    /**
     * An offset of version {@code from} moved to version {@code to} (a later version of this
     * document, or the current one); empty if the changes between them are not known.
     */
    public java.util.OptionalInt moveOffset(int offset, EditRebase.Bias bias, long from, long to) {
        Optional<List<DocumentChange>> since = changesSince(from);
        if (since.isEmpty()) {
            return java.util.OptionalInt.empty();
        }
        List<List<TextEdit>> changes = new ArrayList<>();
        boolean reached = from == to;
        for (DocumentChange c : since.get()) {
            if (reached) break;
            changes.add(c.edits());
            reached = c.versionAfter() == to;
        }
        return reached ? java.util.OptionalInt.of(EditRebase.offset(offset, bias, changes)) : java.util.OptionalInt.empty();
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
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
        version = VERSIONS.incrementAndGet();
        couldBeWritten.set(!isSaved());
        couldBeReParsed.set(true);
        DocumentChange change = new DocumentChange(this, sortedEdits, origin, versionBefore, version);
        changeLog.record(change);
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
