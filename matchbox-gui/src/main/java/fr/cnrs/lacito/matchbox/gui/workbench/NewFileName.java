package fr.cnrs.lacito.matchbox.gui.workbench;

import java.nio.file.Files;
import java.nio.file.Path;

/** The name proposed for a new file: {@code untitled.txt}, else {@code untitled-2.txt}, {@code untitled-3.txt}… */
public final class NewFileName {

    /** The name proposed when nothing else is known. */
    public static final String DEFAULT = "untitled.txt";

    private NewFileName() {
    }

    /**
     * The first name, from {@code base} then {@code <stem>-2.<ext>}, {@code <stem>-3.<ext>}…, of no
     * existing file of the directory (any file if the directory is null). The file system decides
     * whether two names are the same (case ignored on macOS and Windows).
     */
    public static String firstFree(Path directory, String base) {
        if (directory == null || !Files.isDirectory(directory)) return base;
        int dot = base.lastIndexOf('.');
        String stem = dot <= 0 ? base : base.substring(0, dot);
        String extension = dot <= 0 ? "" : base.substring(dot);
        String name = base;
        for (int n = 2; Files.exists(directory.resolve(name)); n++) {
            name = stem + "-" + n + extension;
        }
        return name;
    }
}
