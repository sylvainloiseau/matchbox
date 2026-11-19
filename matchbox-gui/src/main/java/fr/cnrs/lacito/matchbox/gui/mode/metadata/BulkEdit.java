package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.gui.edit.FieldChange;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Edits of several cells at once (fill down, set a value, remove, paste) as one batch of
 * {@link FieldChange}s, with what cannot be done and the text of the preview. No JavaFX.
 *
 * @param changes  the changes, in the order of the cells
 * @param problems why some cells cannot be changed (read-only, a file without header)
 * @param cells    the number of cells changed
 * @param added    how many of them add an entry
 * @param files    the files concerned
 */
public record BulkEdit(List<FieldChange> changes, List<String> problems, int cells, int added, Set<TextId> files) {

    /** A cell of the table: a row and a key. */
    public record Cell(MetadataRow row, String key) {}

    /** The changes giving each cell a new text (empty: remove); a header candidate cannot be in a batch. */
    public static BulkEdit of(List<Cell> cells, List<String> texts) {
        List<FieldChange> changes = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<TextId> files = new LinkedHashSet<>();
        int added = 0;
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            CellEdit edit = CellEdit.of(c.row(), c.key(), texts.get(i), false);
            String where = c.row().text().displayName() + ", " + c.row().type() + " " + c.row().label();
            switch (edit) {
                case CellEdit.Set s -> changes.add(new FieldChange(s.owner(), s.owner().metadataFieldName(s.key()), s.value()));
                case CellEdit.Add a -> {
                    changes.add(FieldChange.add(a.owner(), a.owner().metadataFieldName(a.key()), a.value()));
                    added++;
                }
                case CellEdit.Remove r -> changes.add(FieldChange.remove(r.owner(), r.owner().metadataFieldName(r.key())));
                case CellEdit.AddHeader h -> problems.add(where + ": give the file a header first (one cell)");
                case CellEdit.ReadOnly ro -> problems.add(where + ": " + ro.reason());
                case CellEdit.None n -> { }
            }
            if (!(edit instanceof CellEdit.None)) files.add(c.row().text());
        }
        return new BulkEdit(List.copyOf(changes), List.copyOf(problems), changes.size(), added, files);
    }

    /** The first cell's text into the others. */
    public static BulkEdit fillDown(List<Cell> cells) {
        if (cells.size() < 2) return of(List.of(), List.of());
        String value = cells.get(0).row().value(cells.get(0).key()).orElse("");
        return of(cells.subList(1, cells.size()), cells.subList(1, cells.size()).stream().map(c -> value).toList());
    }

    /** One value into every cell. */
    public static BulkEdit set(List<Cell> cells, String value) {
        return of(cells, cells.stream().map(c -> value).toList());
    }

    /**
     * A pasted grid from a cell: values to the right (in the order of the shown keys) and below (in
     * the order of the shown rows); empty if it does not fit.
     */
    public static Optional<BulkEdit> paste(List<List<String>> grid, List<MetadataRow> rows, int row,
                                           List<String> keys, int key) {
        List<Cell> cells = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int r = 0; r < grid.size(); r++) {
            for (int k = 0; k < grid.get(r).size(); k++) {
                if (row + r >= rows.size() || key + k >= keys.size()) return Optional.empty();
                cells.add(new Cell(rows.get(row + r), keys.get(key + k)));
                texts.add(grid.get(r).get(k));
            }
        }
        return Optional.of(of(cells, texts));
    }

    /** "Sets speaker in 12 blocks of 4 files (adds it to 5 of them)". */
    public String preview(String what) {
        String text = what + " in " + cells + (cells == 1 ? " block" : " blocks") + " of " + files.size()
            + (files.size() == 1 ? " file" : " files");
        return added == 0 ? text : text + " (adds it to " + added + " of them)";
    }
}
