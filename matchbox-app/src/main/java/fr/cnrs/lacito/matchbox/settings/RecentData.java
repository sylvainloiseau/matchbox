package fr.cnrs.lacito.matchbox.settings;

import java.util.List;
import java.util.Objects;

/**
 * Recently opened files and projects, as stored in {@code recent.json}. Most recent first.
 *
 * @param version        format version of the file
 * @param recentFiles    absolute paths of recently opened files and directories
 * @param recentProjects absolute paths of recently opened project files
 */
public record RecentData(Integer version, List<String> recentFiles, List<String> recentProjects) {

    public static final int CURRENT_VERSION = 1;

    public static RecentData empty() {
        return new RecentData(CURRENT_VERSION, List.of(), List.of());
    }

    /** Copy with missing lists replaced by empty lists and blank entries removed. */
    public RecentData validated() {
        return new RecentData(CURRENT_VERSION, clean(recentFiles), clean(recentProjects));
    }

    private static List<String> clean(List<String> paths) {
        if (paths == null) return List.of();
        return paths.stream().filter(Objects::nonNull).map(String::strip).filter(p -> !p.isEmpty()).toList();
    }
}
