package fr.cnrs.lacito.matchbox.gui.mode.metadata;

import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.gui.edit.FieldChange;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The renames of a key in every block having it: blocks that already have the new key are skipped
 * (said in the preview). No JavaFX.
 *
 * @param changes the renames
 * @param skipped the blocks having both keys
 * @param files   the files changed
 */
public record KeyRename(List<FieldChange> changes, List<MetadataRow> skipped, Set<TextId> files) {

    public static KeyRename of(List<MetadataRow> rows, String key, String newKey) {
        List<FieldChange> changes = new ArrayList<>();
        List<MetadataRow> skipped = new ArrayList<>();
        Set<TextId> files = new LinkedHashSet<>();
        for (MetadataRow row : rows) {
            if (row.owner().isEmpty() || row.value(key).isEmpty()) continue;
            if (row.value(newKey).isPresent()) {
                skipped.add(row);
                continue;
            }
            var owner = row.owner().get();
            changes.add(FieldChange.rename(owner, owner.metadataFieldName(key), owner.metadataFieldName(newKey)));
            files.add(row.text());
        }
        return new KeyRename(List.copyOf(changes), List.copyOf(skipped), files);
    }

    /** "Renames speaker to speakers in 41 blocks of 9 files; 2 blocks already have speakers: not renamed there". */
    public String preview(String key, String newKey) {
        String text = "Renames " + key + " to " + newKey + " in " + changes.size()
            + (changes.size() == 1 ? " block" : " blocks") + " of " + files.size() + (files.size() == 1 ? " file" : " files");
        if (!skipped.isEmpty()) {
            text += "; " + skipped.size() + (skipped.size() == 1 ? " block already has " : " blocks already have ") + newKey
                + ": not renamed there";
        }
        return text;
    }
}
