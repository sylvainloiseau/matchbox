package fr.cnrs.lacito.matchbox.grammar.tag;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import java.util.List;

/** Renames a tag (this occurrence): the whole tag is rewritten as {@code #"keyword"}, with straight quotes. */
final class TagObjectEditor implements ObjectEditor {

    @Override
    public EditProposal setField(CorpusObject object, String fieldName, String value, String content) {
        if (!(object instanceof Tag) || !fieldName.equals(Tag.TAG)) {
            return new EditProposal.Refused("The field " + fieldName + " cannot be edited");
        }
        String name = value.strip();
        if (name.isEmpty()) {
            return new EditProposal.Refused("A tag cannot be empty");
        }
        if (name.indexOf('"') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            return new EditProposal.Refused("A tag cannot contain a quote or a line break");
        }
        TextCoordinate span = object.getTextCoordinate();
        if (!span.isValidFor(content.length())) {
            return new EditProposal.Refused("The text changed");
        }
        return new EditProposal.Edits(List.of(TextEdit.replace(content, span, "#\"" + name + "\"")));
    }
}
