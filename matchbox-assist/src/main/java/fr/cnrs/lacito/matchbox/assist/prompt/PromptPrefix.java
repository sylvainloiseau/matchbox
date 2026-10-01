package fr.cnrs.lacito.matchbox.assist.prompt;

import fr.cnrs.lacito.matchbox.assist.provider.ChatRequest.SystemBlock;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarExample;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stable part of every prompt for a file: role and rules, documentation of the file's
 * grammars, examples from the project. It is shared by all tasks (completion, explanation,
 * review), so that providers can cache it; its text depends only on its inputs (no time stamp,
 * fixed order).
 */
public final class PromptPrefix {

    static final String ROLE = """
        You assist a linguist who edits plain-text field notes in Matchbox. Parts of the notes \
        follow formal notations, described below, that Matchbox parses to extract lexical entries, \
        interlinear glossed texts and other data; the rest is free text.

        General rules:
        - Preserve the author's language, transcription, gloss abbreviations, separators and layout.
        - Never invent data (forms, glosses, translations, examples) that the author has not \
        written or clearly implied. When unsure, propose less.
        - Follow the notations exactly, so that the text parses without errors.""";

    private final List<SystemBlock> blocks;
    private final String key;

    private PromptPrefix(List<SystemBlock> blocks, String key) {
        this.blocks = List.copyOf(blocks);
        this.key = key;
    }

    /** The system blocks; the last one is the cache breakpoint. */
    public List<SystemBlock> blocks() {
        return blocks;
    }

    /** Identifies the inputs the prefix was built from. */
    public String key() {
        return key;
    }

    /** Full text, e.g. for tests and logs. */
    public String text() {
        StringBuilder b = new StringBuilder();
        for (SystemBlock block : blocks) {
            if (!b.isEmpty()) b.append("\n\n");
            b.append(block.text());
        }
        return b.toString();
    }

    public static PromptPrefix build(List<GrammarPlugin> grammars, List<FewShotExample> examples,
                                     boolean includeGrammarSource) {
        StringBuilder doc = new StringBuilder(ROLE);
        if (grammars.isEmpty()) {
            doc.append("\n\n# Notations\n\nNo notation is associated with this file: it is free text.");
        } else {
            doc.append("\n\n# Notations used in this file\n");
            for (GrammarPlugin g : grammars) {
                doc.append("\n## ").append(g.getDisplayName()).append(" (grammar id: ").append(g.getId()).append(")\n");
                appendIfPresent(doc, g.getDisplayDescription());
                appendIfPresent(doc, g.getFormatDocumentation());
                List<GrammarExample> grammarExamples = g.getExamples();
                if (!grammarExamples.isEmpty()) {
                    doc.append("\nCanonical examples:\n");
                    for (GrammarExample e : grammarExamples) {
                        doc.append("<example caption=\"").append(e.caption().replace("\"", "'")).append("\">\n")
                            .append(e.text().strip()).append("\n</example>\n");
                    }
                }
                if (includeGrammarSource && !g.getGrammarSource().isBlank()) {
                    doc.append("\nANTLR grammar:\n<grammar-source>\n").append(g.getGrammarSource().strip())
                        .append("\n</grammar-source>\n");
                }
            }
        }
        if (!examples.isEmpty()) {
            doc.append("\n# Examples from this project\n\nThese passages of the author's own notes parse without errors; ")
                .append("follow their conventions.\n");
            for (FewShotExample e : examples) {
                doc.append("<example kind=\"").append(e.kind()).append("\">\n").append(e.text()).append("\n</example>\n");
            }
        }
        String key = grammars.stream().map(GrammarPlugin::getId).toList() + "|" + includeGrammarSource + "|" +
            Integer.toHexString(doc.toString().hashCode());
        return new PromptPrefix(List.of(new SystemBlock(doc.toString().strip(), true)), key);
    }

    private static void appendIfPresent(StringBuilder doc, String text) {
        if (text != null && !text.isBlank()) {
            doc.append(text.strip()).append("\n");
        }
    }

    /** Memoises prefixes, so that the same inputs give the same instance (and the same bytes). */
    public static final class Cache {

        private record Key(List<String> grammarIds, List<FewShotExample> examples, boolean includeSource) {}

        private final Map<Key, PromptPrefix> prefixes = new ConcurrentHashMap<>();

        public PromptPrefix get(List<GrammarPlugin> grammars, List<FewShotExample> examples, boolean includeSource) {
            Key k = new Key(grammars.stream().map(GrammarPlugin::getId).toList(), List.copyOf(examples), includeSource);
            return prefixes.computeIfAbsent(k, x -> build(grammars, examples, includeSource));
        }

        public void clear() {
            prefixes.clear();
        }
    }
}
