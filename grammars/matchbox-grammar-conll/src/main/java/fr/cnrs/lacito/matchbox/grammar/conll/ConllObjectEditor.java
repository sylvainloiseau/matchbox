package fr.cnrs.lacito.matchbox.grammar.conll;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import java.util.List;
import java.util.Optional;

/**
 * Writes the comments of a CoNLL sentence ({@code # key = value}): sets a value, adds a comment line
 * after the last one, removes or renames one. The sentence id ({@code sent_id}) can be changed, not
 * removed nor renamed.
 */
final class ConllObjectEditor implements ObjectEditor {

    static final ConllObjectEditor INSTANCE = new ConllObjectEditor();

    private static final String PREFIX = DependencySentence.metadataField("");

    private ConllObjectEditor() {
    }

    @Override
    public EditProposal setField(CorpusObject object, String fieldName, String value, String content) {
        Optional<Field> field = field(object, fieldName);
        if (field.isEmpty()) return new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return new EditProposal.Refused("A comment stays on one line");
        }
        String v = value.strip();
        if (v.isEmpty()) {
            return new EditProposal.Refused(isSentId(fieldName) ? "The sentence id cannot be empty"
                : "A comment needs a value: remove the comment instead");
        }
        TextCoordinate span = field.get().span();
        if (!span.isValidFor(content.length())) return new EditProposal.Refused("The text changed");
        return new EditProposal.Edits(List.of(TextEdit.replace(content, span, v)));
    }

    @Override
    public EditProposal addField(CorpusObject object, String fieldName, String value, String content) {
        if (!(object instanceof DependencySentence) || !fieldName.startsWith(PREFIX)) {
            return new EditProposal.Refused("Only comments can be added");
        }
        String key = fieldName.substring(PREFIX.length()).strip();
        Optional<EditProposal.Refused> invalid = invalid(key, value);
        if (invalid.isPresent()) return invalid.get();
        if (field(object, PREFIX + key).isPresent()) {
            return new EditProposal.Refused("The comment '" + key + "' already exists: edit its value");
        }
        List<Field> comments = comments(object);
        if (comments.isEmpty()) return new EditProposal.Refused("The text changed");
        Field last = comments.get(comments.size() - 1);
        if (!last.span().isValidFor(content.length())) return new EditProposal.Refused("The text changed");
        int at = nextLineStart(content, last.span().stopIndex());
        String eol = lineEnding(content);
        String line = "# " + key + " = " + value.strip() + eol;
        // the last comment line ends the file without a line break
        if (at == content.length() && !content.isEmpty() && !endsWithBreak(content)) line = eol + "# " + key + " = " + value.strip();
        return new EditProposal.Edits(List.of(TextEdit.insert(at, line)));
    }

    @Override
    public EditProposal removeField(CorpusObject object, String fieldName, String content) {
        if (isSentId(fieldName)) return new EditProposal.Refused("The sentence id cannot be removed");
        Optional<Field> field = field(object, fieldName);
        if (field.isEmpty()) return new EditProposal.Refused("The field " + fieldName + " cannot be removed");
        if (!field.get().span().isValidFor(content.length())) return new EditProposal.Refused("The text changed");
        int start = lineStart(content, field.get().span().startIndex());
        int end = nextLineStart(content, field.get().span().stopIndex());
        return new EditProposal.Edits(List.of(TextEdit.replace(content, start, end, "")));
    }

    @Override
    public EditProposal renameField(CorpusObject object, String fieldName, String newFieldName, String content) {
        if (isSentId(fieldName) || isSentId(newFieldName)) {
            return new EditProposal.Refused("The sentence id cannot be renamed");
        }
        if (!newFieldName.startsWith(PREFIX)) return new EditProposal.Refused("Only comments can be renamed");
        String key = newFieldName.substring(PREFIX.length()).strip();
        Optional<EditProposal.Refused> invalid = invalid(key, "x");
        if (invalid.isPresent()) return invalid.get();
        if (field(object, PREFIX + key).isPresent()) {
            return new EditProposal.Refused("The comment '" + key + "' already exists");
        }
        Optional<Field> field = field(object, fieldName);
        if (field.isEmpty() || !field.get().span().isValidFor(content.length())) {
            return new EditProposal.Refused("The text changed");
        }
        int lineStart = lineStart(content, field.get().span().startIndex());
        int hash = content.indexOf('#', lineStart);
        int equals = content.lastIndexOf('=', field.get().span().startIndex());
        if (hash < 0 || equals < hash) return new EditProposal.Refused("The text changed");
        int start = hash + 1;
        while (start < equals && Character.isWhitespace(content.charAt(start))) start++;
        int end = equals;
        while (end > start && Character.isWhitespace(content.charAt(end - 1))) end--;
        return new EditProposal.Edits(List.of(TextEdit.replace(content, start, end, key)));
    }

    private static Optional<EditProposal.Refused> invalid(String key, String value) {
        if (key.isEmpty() || key.contains("=") || key.startsWith("#") || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
            return Optional.of(new EditProposal.Refused("Invalid key: it cannot be empty, contain '=' or start with '#'"));
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return Optional.of(new EditProposal.Refused("A comment stays on one line"));
        }
        if (value.strip().isEmpty()) return Optional.of(new EditProposal.Refused("A comment needs a value"));
        return Optional.empty();
    }

    private static boolean isSentId(String fieldName) {
        return fieldName.equals(PREFIX + ConllGrammarPlugin.SENT_ID);
    }

    private static Optional<Field> field(CorpusObject object, String name) {
        return object instanceof HasFields f ? f.field(name) : Optional.empty();
    }

    private static List<Field> comments(CorpusObject object) {
        return object instanceof HasFields f
            ? f.getFields().stream().filter(x -> x.name().startsWith(PREFIX)).toList() : List.of();
    }

    private static int lineStart(String content, int offset) {
        int i = Math.min(offset, content.length());
        while (i > 0 && content.charAt(i - 1) != '\n' && content.charAt(i - 1) != '\r') i--;
        return i;
    }

    /** The start of the line after the one holding {@code offset} (the end of the content if none). */
    private static int nextLineStart(String content, int offset) {
        int i = Math.max(0, offset);
        while (i < content.length() && content.charAt(i) != '\n' && content.charAt(i) != '\r') i++;
        if (i < content.length() && content.charAt(i) == '\r') {
            i++;
            if (i < content.length() && content.charAt(i) == '\n') i++;
        } else if (i < content.length() && content.charAt(i) == '\n') {
            i++;
        }
        return i;
    }

    private static boolean endsWithBreak(String content) {
        char c = content.charAt(content.length() - 1);
        return c == '\n' || c == '\r';
    }

    private static String lineEnding(String content) {
        int i = content.indexOf('\n');
        return i > 0 && content.charAt(i - 1) == '\r' ? "\r\n" : "\n";
    }
}
