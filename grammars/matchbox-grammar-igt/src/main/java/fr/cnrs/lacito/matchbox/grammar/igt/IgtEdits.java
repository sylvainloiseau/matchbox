package fr.cnrs.lacito.matchbox.grammar.igt;

import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.corpus.igt.SettingSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Edits of IGTs in the notation of this grammar: metadata entries, the roles of the lines in the
 * setting, numbering. The edits of words (insert, delete, format) are notation-independent:
 * {@link fr.cnrs.lacito.matchbox.corpus.igt.InterlinearEdits}. Pure: edits relative to the parsed content.
 */
public final class IgtEdits {

    private IgtEdits() {}

    /** The setting describing the roles of the text lines as they are read ({@code o@fra,t@fra,g@en}). */
    public static String setting(InterlinearReading r) {
        List<SettingSpec.Entry> entries = new ArrayList<>();
        for (LineReading l : r.lines()) {
            if (l.role() != LineRole.ORTHOGRAPHIC && l.role() != LineRole.TRANSCRIPTION && l.role() != LineRole.GLOSS) continue;
            Optional<Level> level = l.level().filter(v -> !l.line().segmentation().equals(Optional.of(v)));
            Optional<String> lang = l.language().filter(v -> !inheritedLanguage(r, l, v));
            entries.add(new SettingSpec.Entry(l.role(), level, l.name(), lang));
        }
        return SettingSpec.write(entries);
    }

    /** True when the language is the default of the IGT (olang / mlang): no need to write it. */
    private static boolean inheritedLanguage(InterlinearReading r, LineReading l, String language) {
        String key = l.role() == LineRole.GLOSS ? "mlang" : "olang";
        return r.value(key).map(language::equals).orElse(false);
    }

    /** Sets (or adds) the setting of an IGT to the roles read. */
    public static List<TextEdit> declareRoles(InterlinearReading r, String content) {
        return setMetadata(r.igt(), "setting", setting(r), content);
    }

    /** Sets or adds a metadata entry of an IGT. */
    public static List<TextEdit> setMetadata(InterlinearGlossedText igt, String key, String value, String content) {
        Optional<MetadataSyntax.Block> block = IgtObjectEditor.block(igt, content);
        if (block.isEmpty()) return List.of();
        Optional<MetadataSyntax.Entry> entry = block.get().entry(key);
        if (entry.isPresent()) {
            if (entry.get().value().equals(value)) return List.of();
            return List.of(TextEdit.replace(content, entry.get().valueStart(), entry.get().valueEnd(), MetadataSyntax.escape(value)));
        }
        return List.of(IgtObjectEditor.addEntry(igt, block.get(), key, value));
    }

    /** {@code id="1"}, {@code id="2"}… on the IGTs, in text order. */
    public static List<TextEdit> number(List<InterlinearGlossedText> igts, String content) {
        List<TextEdit> edits = new ArrayList<>();
        for (int i = 0; i < igts.size(); i++) {
            edits.addAll(setMetadata(igts.get(i), "id", String.valueOf(i + 1), content));
        }
        return edits;
    }

    /** Inserts an empty {@code {}} block after the start anchor. */
    public static List<TextEdit> addMetadataBlock(InterlinearGlossedText igt) {
        return List.of(TextEdit.insert(igt.getTextCoordinate().startIndex() + 3, "{}"));
    }
}
