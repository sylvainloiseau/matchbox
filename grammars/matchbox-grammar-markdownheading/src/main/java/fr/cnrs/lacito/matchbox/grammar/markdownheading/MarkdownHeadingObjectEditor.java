package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes titles and metadata values in Markdown/YAML: refuses what would change the structure
 * (line breaks, a title read as something else), quotes YAML values when needed, adds and removes
 * {@code key: value} lines (creating the block of a section that has none).
 */
final class MarkdownHeadingObjectEditor implements ObjectEditor {

    private static final String METADATA = Section.metadataField("");
    private static final String PROPERTY = TextHeader.propertyField("");

    @Override
    public EditProposal setField(CorpusObject object, String fieldName, String value, String content) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return new EditProposal.Refused("Line breaks are not allowed here");
        }
        Optional<Field> field = object instanceof HasFields h ? h.field(fieldName) : Optional.empty();
        if (field.isEmpty()) {
            return new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        }
        TextCoordinate span = field.get().span();
        if (!span.isValidFor(content.length())) {
            return new EditProposal.Refused("The text changed");
        }
        if (object instanceof MarkdownSection section && fieldName.equals(Section.TITLE)) {
            return setTitle(section, span, value.strip(), content);
        }
        return new EditProposal.Edits(List.of(valueEdit(span, value, content)));
    }

    private static EditProposal setTitle(MarkdownSection section, TextCoordinate span, String title, String content) {
        if (title.matches("(.*[ \\t])?#+")) {
            return new EditProposal.Refused("A title cannot end with ' #' (it would be read as the end of the heading)");
        }
        if (section.getStyle() == MarkdownSection.Style.SETEXT) {
            if (title.isEmpty()) {
                return new EditProposal.Refused("The title of this heading cannot be empty");
            }
            if (title.startsWith("#") || title.startsWith("```") || title.startsWith("~~~") || title.equals(":::")
                || title.matches("[-=]+")) {
                return new EditProposal.Refused("This title would be read as another line of Markdown");
            }
        }
        if (span.isEmpty() && section.getStyle() == MarkdownSection.Style.ATX) {
            // "#" or "#   ": the title needs a space after the marks
            int end = LineIndex.lineEnd(content, span.startIndex());
            String replacement = title.isEmpty() ? "" : " " + title;
            return new EditProposal.Edits(List.of(TextEdit.replace(content, span.startIndex(), end, replacement)));
        }
        return new EditProposal.Edits(List.of(TextEdit.replace(content, span, title)));
    }

    /** Replaces a raw value, its quotes included, by the value written plain or quoted. */
    private static TextEdit valueEdit(TextCoordinate span, String value, String content) {
        int start = span.startIndex();
        int end = span.isEmpty() ? start : span.stopIndex() + 1;
        if (start > 0 && end < content.length()) {
            char before = content.charAt(start - 1);
            if ((before == '"' || before == '\'') && content.charAt(end) == before) {
                start--;
                end++;
            }
        }
        String written = write(value);
        if (!written.isEmpty() && start > 0 && content.charAt(start - 1) == ':') {
            written = " " + written;
        }
        return TextEdit.replace(content, start, end, written);
    }

    /** A YAML value: plain when it reads back unchanged, double-quoted otherwise. */
    static String write(String value) {
        if (value.isEmpty()) return "";
        boolean quote = !value.equals(value.strip())
            || value.contains(": ") || value.contains(" #") || value.contains("\t#")
            || "\"'-[{>|&*!%@`#".indexOf(value.charAt(0)) >= 0
            || value.endsWith(":");
        return quote ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }

    // ------------------------------------------------------------------
    // Adding and removing entries
    // ------------------------------------------------------------------

    @Override
    public EditProposal addField(CorpusObject object, String fieldName, String value, String content) {
        Optional<String> key = keyOf(object, fieldName);
        if (key.isEmpty()) {
            return new EditProposal.Refused("Entries cannot be added to " + object.getKind());
        }
        String k = key.get().strip();
        if (k.isEmpty() || k.contains(":") || k.indexOf('\n') >= 0 || k.indexOf('\r') >= 0
            || k.startsWith("#") || k.startsWith("-")) {
            return new EditProposal.Refused("Invalid key: it cannot be empty, contain ':' or start with '#' or '-'");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return new EditProposal.Refused("Line breaks are not allowed here");
        }
        if (entries(object).containsKey(k)) {
            return new EditProposal.Refused("The key '" + k + "' already exists: edit its value");
        }
        String eol = LineIndex.lineEnding(content);
        String written = write(value.strip());
        String line = written.isEmpty() ? k + ":" : k + ": " + written;
        Optional<TextCoordinate> block = blockOf(object);
        if (block.isPresent()) {
            // before the closing "---"
            int closing = lastLineStart(content, block.get());
            return new EditProposal.Edits(List.of(TextEdit.insert(closing, line + eol)));
        }
        if (object instanceof MarkdownSection section) {
            // a new block right after the heading
            int after = section.getHeadingCoordinate().stopIndex() + 1;
            boolean endsLine = after > 0 && (content.charAt(after - 1) == '\n' || content.charAt(after - 1) == '\r');
            String inserted = (endsLine ? "" : eol) + "---" + eol + line + eol + "---" + (endsLine ? eol : "");
            return new EditProposal.Edits(List.of(TextEdit.insert(after, inserted)));
        }
        return new EditProposal.Refused("Entries cannot be added to " + object.getKind());
    }

    @Override
    public EditProposal removeField(CorpusObject object, String fieldName, String content) {
        if (fieldName.equals(Section.TITLE)) {
            return new EditProposal.Refused("The title cannot be removed");
        }
        Optional<Field> field = object instanceof HasFields h && keyOf(object, fieldName).isPresent()
            ? h.field(fieldName) : Optional.empty();
        if (field.isEmpty()) {
            return new EditProposal.Refused("The field " + fieldName + " cannot be removed");
        }
        int start = LineIndex.lineStart(content, field.get().span().startIndex());
        int end = LineIndex.nextLineStart(content, start);
        return new EditProposal.Edits(List.of(TextEdit.replace(content, start, end, "")));
    }

    /** The key of a metadata field name of this object, if it is one. */
    private static Optional<String> keyOf(CorpusObject object, String fieldName) {
        if (object instanceof MarkdownSection && fieldName.startsWith(METADATA)) {
            return Optional.of(fieldName.substring(METADATA.length()));
        }
        if (object instanceof TextHeader && fieldName.startsWith(PROPERTY)) {
            return Optional.of(fieldName.substring(PROPERTY.length()));
        }
        return Optional.empty();
    }

    private static Map<String, String> entries(CorpusObject object) {
        if (object instanceof MarkdownSection s) return s.getMetadata();
        if (object instanceof TextHeader h) return h.getProperties();
        return Map.of();
    }

    private static Optional<TextCoordinate> blockOf(CorpusObject object) {
        if (object instanceof MarkdownSection s) return s.getMetadataCoordinate();
        if (object instanceof TextHeader h) return Optional.of(h.getTextCoordinate());
        return Optional.empty();
    }

    /** Start of the last line of a block (its closing "---"). */
    private static int lastLineStart(String content, TextCoordinate block) {
        int i = block.stopIndex();
        while (i > block.startIndex() && (content.charAt(i) == '\n' || content.charAt(i) == '\r')) i--;
        return LineIndex.lineStart(content, i);
    }
}
