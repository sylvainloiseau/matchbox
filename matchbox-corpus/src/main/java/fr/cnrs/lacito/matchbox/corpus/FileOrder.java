package fr.cnrs.lacito.matchbox.corpus;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Comparator;

/**
 * The one order of files and file names of the application: total, and the same on every system.
 *
 * <p>Never sort paths or file names with their natural order ({@code Path.compareTo},
 * {@code Stream.sorted()} on paths, {@code TreeMap<Path, …>}): it depends on the system. Windows
 * ignores case ({@code a.txt} before {@code B.TXT}), macOS and Linux do not ({@code B.TXT} before
 * {@code a.txt}); macOS may give names in another Unicode form (NFD) than Linux and Windows (NFC).
 * The order of files decides the order of the texts of a corpus and the order in which plugins are
 * loaded: it must not change with the system.
 *
 * <p>Order of two names:
 * <ol>
 * <li>the names in Unicode NFC, compared ignoring case ({@code a.txt} &lt; {@code B.TXT});</li>
 * <li>if equal, compared with case, character by character ({@code A.txt} &lt; {@code a.txt}:
 *     both may exist on Linux);</li>
 * <li>if still equal, the original names (two Unicode forms of the same name).</li>
 * </ol>
 * Paths are compared element by element (directory, then sub-directory, then file name), a
 * path before the paths below it; the root (e.g. {@code C:\}) first.
 *
 * <p>On Windows and macOS (case-insensitive file systems by default), two paths differing only by
 * case designate the same file but are different for this order; {@link TextId#of} does not
 * change the case of a path.
 */
public final class FileOrder {

    private FileOrder() {
    }

    /** Order of file and directory names. */
    public static final Comparator<String> NAMES = FileOrder::compareNames;

    /** Order of paths, element by element with {@link #NAMES}. */
    public static final Comparator<Path> PATHS = FileOrder::comparePaths;

    private static int compareNames(String a, String b) {
        String na = Normalizer.normalize(a, Normalizer.Form.NFC);
        String nb = Normalizer.normalize(b, Normalizer.Form.NFC);
        // String.CASE_INSENSITIVE_ORDER compares characters, without locale
        int c = String.CASE_INSENSITIVE_ORDER.compare(na, nb);
        if (c != 0) return c;
        c = na.compareTo(nb);
        return c != 0 ? c : a.compareTo(b);
    }

    private static int comparePaths(Path a, Path b) {
        int c = compareNames(root(a), root(b));
        if (c != 0) return c;
        int n = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < n; i++) {
            c = compareNames(a.getName(i).toString(), b.getName(i).toString());
            if (c != 0) return c;
        }
        return Integer.compare(a.getNameCount(), b.getNameCount());
    }

    private static String root(Path p) {
        Path root = p.getRoot();
        return root == null ? "" : root.toString();
    }
}
