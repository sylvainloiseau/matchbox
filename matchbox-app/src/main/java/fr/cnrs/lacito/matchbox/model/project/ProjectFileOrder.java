package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.corpus.FileOrder;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * The one order of the files of a project: the order of the history of the dictionary (the
 * commands of the files are applied in it) and of the file list. Total and the same on every
 * system: every criterion ends with {@link FileOrder#PATHS}.
 */
public enum ProjectFileOrder {

    /**
     * By path, element by element ({@link FileOrder#PATHS}: names in NFC, case ignored then case):
     * the files and sub-directories of a directory sorted together by name, so {@code a/b/x.txt}
     * comes before {@code a/y.txt}.
     */
    PATH("path", "By path (folders and file names)"),

    /**
     * By the last modification time of the file on disk, oldest first, then by path. Saving a
     * file moves it to the end; a copy or a checkout may change the times.
     */
    MODIFIED("modified", "By last modification date"),

    /**
     * By the {@code date} of the file's header (an ISO 8601 date, {@code 2025-07-14}, or date and
     * time), oldest first, then by path; files without a valid date come last, by path.
     */
    HEADER_DATE("header-date", "By date in the file header");

    private final String xmlName;
    private final String label;

    ProjectFileOrder(String xmlName, String label) {
        this.xmlName = xmlName;
        this.label = label;
    }

    /** The value of {@code <fileOrder by="…"/>}. */
    public String xmlName() {
        return xmlName;
    }

    /** The name shown in Project properties. */
    public String label() {
        return label;
    }

    /** The order of a {@code by} attribute; empty for an unknown value. */
    public static Optional<ProjectFileOrder> fromXml(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(PATH);
        }
        for (ProjectFileOrder o : values()) {
            if (o.xmlName.equals(value.strip().toLowerCase(Locale.ROOT))) {
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    /**
     * Files in this order.
     *
     * @param files the files (absolute paths)
     * @param date  the date of a file for {@link #MODIFIED} and {@link #HEADER_DATE} (empty: none);
     *              not called for {@link #PATH}
     * @return a new list, ordered
     */
    public List<Path> order(Collection<Path> files, Function<Path, Optional<Instant>> date) {
        List<Path> out = new ArrayList<>(files);
        out.sort(comparator(date));
        return out;
    }

    /** The comparison of this order (see {@link #order}). */
    public Comparator<Path> comparator(Function<Path, Optional<Instant>> date) {
        if (this == PATH) {
            return FileOrder.PATHS;
        }
        // Dates are read once per comparison: callers give a cached function for large sets.
        Comparator<Path> byDate = (a, b) -> {
            Optional<Instant> da = date.apply(a);
            Optional<Instant> db = date.apply(b);
            if (da.isPresent() && db.isPresent()) return da.get().compareTo(db.get());
            if (da.isPresent()) return -1;      // dated files first, files without date last
            if (db.isPresent()) return 1;
            return 0;
        };
        return byDate.thenComparing(FileOrder.PATHS);
    }

    /**
     * The instant of a header {@code date}: {@code 2025-07-14} (the start of that day, UTC),
     * {@code 2025-07-14T10:30[:00]} (UTC), or with an offset ({@code 2025-07-14T10:30+02:00}).
     *
     * @param value the value written
     * @return empty when it is not such a date
     */
    public static Optional<Instant> parseHeaderDate(String value) {
        if (value == null) return Optional.empty();
        String v = value.strip();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1).strip();
        }
        try {
            return Optional.of(LocalDate.parse(v).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            // not a date alone
        }
        try {
            return Optional.of(OffsetDateTime.parse(v).toInstant());
        } catch (DateTimeParseException e) {
            // no offset
        }
        try {
            return Optional.of(LocalDateTime.parse(v).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
