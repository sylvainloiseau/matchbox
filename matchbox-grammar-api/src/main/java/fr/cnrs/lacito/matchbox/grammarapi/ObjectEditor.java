package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import java.util.List;
import java.util.Optional;

/**
 * Turns a change of value into text edits, in the notation of a grammar (escaping, quotes, line
 * breaks). No JavaFX: usable by the GUI, the assistant or a command line tool.
 *
 * <p>The application never changes corpus objects: it applies the edits to the text and parses it
 * again.
 */
public interface ObjectEditor {

    /**
     * Edits that set a field of an object.
     *
     * @param object    an object of the grammar, implementing {@link HasFields}
     * @param fieldName name of one of its fields
     * @param value     the new value
     * @param content   the text the object was parsed from ({@code Text.content()})
     */
    EditProposal setField(CorpusObject object, String fieldName, String value, String content);

    /**
     * Edits adding a field (e.g. a metadata entry) to an object. Refused by default.
     *
     * @param fieldName name of the new field (e.g. {@code Section.metadataField("date")})
     */
    default EditProposal addField(CorpusObject object, String fieldName, String value, String content) {
        return new EditProposal.Refused("Fields cannot be added to " + object.getKind());
    }

    /** Edits removing a field (e.g. a metadata entry) of an object. Refused by default. */
    default EditProposal removeField(CorpusObject object, String fieldName, String content) {
        return new EditProposal.Refused("Fields cannot be removed from " + object.getKind());
    }

    /**
     * Edits renaming a field (e.g. the key of a metadata entry), its value kept. Refused by default.
     *
     * @param newFieldName the new name (e.g. {@code Section.metadataField("speakers")})
     */
    default EditProposal renameField(CorpusObject object, String fieldName, String newFieldName, String content) {
        return new EditProposal.Refused("Fields of " + object.getKind() + " cannot be renamed");
    }

    /**
     * Edits giving a text without header a header holding one property ({@code TextHeader}), in the
     * grammar's notation. Refused by default (the grammar writes no header, or the text has one).
     */
    default EditProposal addTextProperty(fr.cnrs.lacito.matchbox.corpus.TextId text, String key, String value,
                                         String content) {
        return new EditProposal.Refused("This grammar writes no header");
    }

    /**
     * Replaces the raw text of the field's span by the value (an empty field: inserts it at the
     * field's position). Refuses a line break in a field written on one line.
     */
    ObjectEditor VERBATIM = (object, fieldName, value, content) -> {
        Optional<Field> field = object instanceof HasFields withFields ? withFields.field(fieldName) : Optional.empty();
        if (field.isEmpty()) {
            return new EditProposal.Refused("The field " + fieldName + " of " + object.getKind() + " cannot be edited");
        }
        TextCoordinate span = field.get().span();
        if (!span.isValidFor(content.length())) {
            return new EditProposal.Refused("The text changed");
        }
        if (span.startLine() == span.endLine() && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)) {
            return new EditProposal.Refused("The field " + fieldName + " must stay on one line");
        }
        return new EditProposal.Edits(List.of(TextEdit.replace(content, span, value)));
    };
}
