package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.HasMetadata;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The rows of the Metadata mode, kept per text and updated one text at a time: every
 * {@link HasMetadata} object of a text, in text order, and a header candidate for a text without
 * header that a grammar can give one. No JavaFX.
 */
public final class MetadataIndex {

    /** Texts in {@link TextId} order (FileOrder: the same on every system). */
    private final Map<TextId, List<MetadataRow>> rows = new TreeMap<>();
    private final Predicate<Text> canAddHeader;

    /** @param canAddHeader true for a text without header one of whose grammars can write a header */
    public MetadataIndex(Predicate<Text> canAddHeader) {
        this.canAddHeader = canAddHeader;
    }

    /** Replaces the rows of a text (none if the text left the corpus). */
    public void update(TextId id, Optional<Text> text) {
        if (text.isEmpty()) {
            rows.remove(id);
        } else {
            rows.put(id, rowsOf(text.get()));
        }
    }

    public void clear() {
        rows.clear();
    }

    /** All the rows, texts in file order, rows in text order. */
    public List<MetadataRow> all() {
        List<MetadataRow> all = new ArrayList<>();
        rows.values().forEach(all::addAll);
        return all;
    }

    public List<MetadataRow> of(TextId id) {
        return rows.getOrDefault(id, List.of());
    }

    List<MetadataRow> rowsOf(Text text) {
        List<CorpusObject> owners = text.flatten().filter(HasMetadata.class::isInstance)
            .sorted(Comparator.comparingInt((CorpusObject o) -> o.getTextCoordinate().startIndex())
                .thenComparingInt(o -> -o.getTextCoordinate().stopIndex()))
            .toList();
        List<MetadataRow> result = new ArrayList<>();
        boolean hasHeader = owners.stream().anyMatch(TextHeader.class::isInstance);
        if (!hasHeader && canAddHeader.test(text)) result.add(MetadataRow.headerCandidate(text.id()));
        for (CorpusObject o : owners) {
            HasMetadata m = (HasMetadata) o;
            String type = MetadataTypes.of(o);
            String label = o instanceof TextHeader ? "—" : o.getLabel();
            int line = m.metadataBlock().flatMap(b -> b.span()).map(s -> s.startLine())
                .orElse(o.getTextCoordinate().startLine());
            result.add(new MetadataRow(text.id(), Optional.of(m), type, label, line, result.size()));
        }
        return List.copyOf(result);
    }
}
