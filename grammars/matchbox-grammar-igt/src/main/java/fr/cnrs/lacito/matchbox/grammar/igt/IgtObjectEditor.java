package fr.cnrs.lacito.matchbox.grammar.igt;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.igt.Segmenter;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import java.util.List;
import java.util.Optional;

/**
 * Edits of an IGT in its notation: metadata values (escaped), entries added (with the {@code {…}}
 * block when there is none, in the same edit) or removed, lines and words. Refuses a line break, a
 * word with whitespace, a line that would become another kind of line or an anchor.
 */
final class IgtObjectEditor implements ObjectEditor {

    @Override
    public EditProposal setField(CorpusObject object, String fieldName, String value, String content) {
        if (!(object instanceof InterlinearGlossedText igt) || !(object instanceof HasFields withFields)) {
            return new EditProposal.Refused("Not an IGT");
        }
        Optional<Field> field = withFields.field(fieldName);
        if (field.isEmpty()) return new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        TextCoordinate span = field.get().span();
        if (!span.isValidFor(content.length())) return new EditProposal.Refused("The text changed");
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return new EditProposal.Refused("The value must stay on one line");
        }
        if (fieldName.startsWith("meta/")) {
            return edit(content, span, MetadataSyntax.escape(value));
        }
        Optional<InterlinearLine> line = lineOf(igt, fieldName, span);
        if (fieldName.chars().filter(c -> c == '/').count() == 2) {
            if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
                return new EditProposal.Refused("A word cannot be empty nor contain a space: insert a word instead");
            }
            return edit(content, span, value);
        }
        if (line.isPresent()) {
            InterlinearLine l = line.get();
            if (value.isBlank()) return new EditProposal.Refused("A line cannot be empty: delete it in the editor");
            if (l.content() == InterlinearLine.Content.TRANSLATION) {
                return edit(content, span, value);
            }
            if (value.strip().startsWith(":::")) return new EditProposal.Refused("A line of an IGT cannot start with :::");
            if (Segmenter.contentOf(value) != l.content()) {
                return new EditProposal.Refused(l.content() == InterlinearLine.Content.TIMELINE
                    ? "A timeline has only numbers" : "This line would become a " + Segmenter.contentOf(value).name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return edit(content, span, value);
    }

    /** The line of a line/i field, or of a field of InterlinearSentence (by its span). */
    private static Optional<InterlinearLine> lineOf(InterlinearGlossedText igt, String fieldName, TextCoordinate span) {
        if (fieldName.startsWith("line/")) {
            String[] parts = fieldName.split("/");
            int i = Integer.parseInt(parts[1]);
            return i < igt.lines().size() ? Optional.of(igt.lines().get(i)) : Optional.empty();
        }
        return igt.lines().stream().filter(l -> l.getTextCoordinate().encloses(span)).findFirst();
    }

    private static EditProposal edit(String content, TextCoordinate span, String value) {
        return new EditProposal.Edits(List.of(TextEdit.replace(content, span, value)));
    }

    @Override
    public EditProposal addField(CorpusObject object, String fieldName, String value, String content) {
        if (!fieldName.startsWith("meta/")) return new EditProposal.Refused("Only metadata entries can be added");
        String key = fieldName.substring("meta/".length());
        if (!MetadataSyntax.validKey(key)) {
            return new EditProposal.Refused("A key contains no = , \" { } nor spaces");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return new EditProposal.Refused("The value must stay on one line");
        Optional<MetadataSyntax.Block> block = block(object, content);
        if (block.isEmpty()) return new EditProposal.Refused("The text changed");
        return new EditProposal.Edits(List.of(addEntry(object, block.get(), key, value)));
    }

    static TextEdit addEntry(CorpusObject igt, MetadataSyntax.Block block, String key, String value) {
        String entry = MetadataSyntax.entry(key, value);
        if (!block.present() || block.close() < 0) {
            return TextEdit.insert(igt.getTextCoordinate().startIndex() + 3, "{" + entry + "}");
        }
        return TextEdit.insert(block.close(), (block.entries().isEmpty() ? "" : ", ") + entry);
    }

    @Override
    public EditProposal removeField(CorpusObject object, String fieldName, String content) {
        if (!fieldName.startsWith("meta/")) return new EditProposal.Refused("Only metadata entries can be removed");
        String key = fieldName.substring("meta/".length());
        Optional<MetadataSyntax.Block> block = block(object, content);
        if (block.isEmpty()) return new EditProposal.Refused("The text changed");
        List<MetadataSyntax.Entry> entries = block.get().entries();
        for (int i = 0; i < entries.size(); i++) {
            MetadataSyntax.Entry e = entries.get(i);
            if (!e.key().equals(key)) continue;
            int from = e.start();
            int to = e.end();
            if (i > 0) {
                from = entries.get(i - 1).end();
            } else if (i + 1 < entries.size()) {
                to = entries.get(i + 1).start();
            }
            return new EditProposal.Edits(List.of(TextEdit.replace(content, from, to, "")));
        }
        return new EditProposal.Refused("No entry " + key);
    }

    /** The metadata block of the start anchor of an IGT, read again from the content. */
    static Optional<MetadataSyntax.Block> block(CorpusObject igt, String content) {
        int start = igt.getTextCoordinate().startIndex();
        if (start < 0 || !content.startsWith(":::", start)) return Optional.empty();
        int end = start;
        while (end < content.length() && content.charAt(end) != '\n' && content.charAt(end) != '\r') end++;
        return Optional.of(MetadataSyntax.parse(content.substring(start, end), start));
    }
}
