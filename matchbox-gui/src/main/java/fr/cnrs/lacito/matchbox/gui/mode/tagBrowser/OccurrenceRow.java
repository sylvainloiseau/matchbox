package fr.cnrs.lacito.matchbox.gui.mode.tagBrowser;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import java.util.ArrayList;
import java.util.List;

/** A row of the occurrence list: a file header, or an occurrence. */
public sealed interface OccurrenceRow {

    /** The first row of a file's occurrences. */
    record FileHeader(TextId text, int count) implements OccurrenceRow {}

    record Occurrence(TagOccurrence occurrence) implements OccurrenceRow {}

    /** Rows for occurrences sorted by text: a header before the occurrences of each text. */
    static List<OccurrenceRow> of(List<TagOccurrence> occurrences) {
        List<OccurrenceRow> rows = new ArrayList<>();
        int i = 0;
        while (i < occurrences.size()) {
            TextId text = occurrences.get(i).text();
            int j = i;
            while (j < occurrences.size() && occurrences.get(j).text().equals(text)) j++;
            rows.add(new FileHeader(text, j - i));
            for (int k = i; k < j; k++) {
                rows.add(new Occurrence(occurrences.get(k)));
            }
            i = j;
        }
        return rows;
    }
}
