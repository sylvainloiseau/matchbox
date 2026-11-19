package fr.cnrs.lacito.matchbox.grammar.conll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.MetadataBlock;
import fr.cnrs.lacito.matchbox.corpus.MetadataEntry;
import fr.cnrs.lacito.matchbox.corpus.TextEdit;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.DependencySentence;
import fr.cnrs.lacito.matchbox.grammarapi.EditProposal;
import fr.cnrs.lacito.matchbox.grammarapi.ObjectEditor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The comments of a CoNLL sentence as a metadata block, and their edits. */
class ConllMetadataEditingTest {

    private static final TextId FILE = new TextId(Path.of("a.conllu"));
    private static final String TEXT = """
        # sent_id = s1
        # text = They buy books.
        # newdoc id = d1
        1\tThey\tthey\tPRON\t_\t_\t2\tnsubj\t_\t_
        2\tbuy\tbuy\tVERB\t_\t_\t0\troot\t_\t_
        3\tbooks\tbook\tNOUN\t_\t_\t2\tobj\t_\t_

        """;

    private static DependencySentence parse(String text) {
        var result = new ConllGrammarPlugin().getResult(FILE, text);
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return (DependencySentence) result.objects().get(0);
    }

    private static final ObjectEditor EDITOR = new ConllGrammarPlugin().getObjectEditor();

    private static String apply(EditProposal p, String text) {
        assertInstanceOf(EditProposal.Edits.class, p, p.toString());
        return TextEdit.applyAll(text, ((EditProposal.Edits) p).edits());
    }

    private static String field(String key) {
        return DependencySentence.metadataField(key);
    }

    @Test
    void theCommentsAreTheBlockSentIdIncluded() {
        MetadataBlock block = parse(TEXT).metadataBlock().orElseThrow();
        assertEquals(List.of("sent_id", "text", "newdoc id"), block.entries().stream().map(MetadataEntry::key).toList());
        for (MetadataEntry e : block.entries()) {
            var span = e.valueSpan().orElseThrow();
            assertEquals(e.value(), TEXT.substring(span.startIndex(), span.stopIndex() + 1), e.key());
        }
        assertEquals("They buy books.", block.values().get("text"));
    }

    @Test
    void setAddRemoveRename() {
        DependencySentence s = parse(TEXT);
        String set = apply(EDITOR.setField(s, field("text"), "They sell books.", TEXT), TEXT);
        assertEquals("They sell books.", parse(set).metadataValues().get("text"));

        String added = apply(EDITOR.addField(s, field("speaker"), "Ann", TEXT), TEXT);
        DependencySentence a = parse(added);
        assertEquals(List.of("sent_id", "text", "newdoc id", "speaker"), List.copyOf(a.metadataValues().keySet()));
        assertEquals(3, a.getTokens().size());

        String removed = apply(EDITOR.removeField(s, field("text"), TEXT), TEXT);
        assertEquals(List.of("sent_id", "newdoc id"), List.copyOf(parse(removed).metadataValues().keySet()));

        String renamed = apply(EDITOR.renameField(s, field("newdoc id"), field("doc"), TEXT), TEXT);
        assertTrue(renamed.contains("# doc = d1\n"), renamed);

        String id = apply(EDITOR.setField(s, field("sent_id"), "s9", TEXT), TEXT);
        assertEquals("s9", parse(id).getSentenceId());
    }

    @Test
    void refusals() {
        DependencySentence s = parse(TEXT);
        assertInstanceOf(EditProposal.Refused.class, EDITOR.removeField(s, field("sent_id"), TEXT));
        assertInstanceOf(EditProposal.Refused.class, EDITOR.renameField(s, field("sent_id"), field("id"), TEXT));
        assertInstanceOf(EditProposal.Refused.class, EDITOR.addField(s, field("text"), "x", TEXT), "exists");
        assertInstanceOf(EditProposal.Refused.class, EDITOR.addField(s, field("a=b"), "x", TEXT));
        assertInstanceOf(EditProposal.Refused.class, EDITOR.setField(s, field("text"), "two\nlines", TEXT));
        assertInstanceOf(EditProposal.Refused.class, EDITOR.setField(s, field("text"), " ", TEXT));
        assertInstanceOf(EditProposal.Refused.class, EDITOR.renameField(s, field("text"), field("newdoc id"), TEXT));
    }

    @Test
    void windowsLineEndingsAreKept() {
        String crlf = TEXT.replace("\n", "\r\n");
        DependencySentence s = parse(crlf);
        String added = apply(EDITOR.addField(s, field("speaker"), "Ann", crlf), crlf);
        assertTrue(added.contains("# newdoc id = d1\r\n# speaker = Ann\r\n1\t"), added);
    }
}
