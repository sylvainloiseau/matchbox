package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Times in media files, written in milliseconds ({@code 12000}) or as clock times ({@code m:ss},
 * {@code m:ss.f}, {@code h:mm:ss}, {@code h:mm:ss.fff}, up to 3 decimals). Shared by grammars
 * (reading, writing back) and views (display).
 */
public final class TimeCodes {

    /**
     * How a time is written.
     *
     * @param clock    false for milliseconds
     * @param hours    true if the hours are written ({@code h:mm:ss})
     * @param decimals number of decimals of the seconds (0–3)
     */
    public record Format(boolean clock, boolean hours, int decimals) {
        public static final Format MILLISECONDS = new Format(false, false, 0);
        /** {@code m:ss.mmm}, the format of new times. */
        public static final Format CLOCK_MS = new Format(true, false, 3);
    }

    private static final Pattern MILLIS = Pattern.compile("\\d{1,12}");
    private static final Pattern CLOCK = Pattern.compile("(?:(\\d+):)?(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?");
    private static final Pattern RANGE = Pattern.compile("\\s*(\\S+?)\\s*[-–]\\s*(\\S+)\\s*");

    private TimeCodes() {}

    /** The time in milliseconds, if the value is a time. */
    public static OptionalLong parse(String value) {
        if (value == null) return OptionalLong.empty();
        String v = value.strip();
        if (MILLIS.matcher(v).matches()) {
            return OptionalLong.of(Long.parseLong(v));
        }
        Matcher m = CLOCK.matcher(v);
        if (!m.matches()) return OptionalLong.empty();
        long hours = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long minutes = Long.parseLong(m.group(2));
        long seconds = Long.parseLong(m.group(3));
        if (seconds >= 60 || (m.group(1) != null && minutes >= 60)) return OptionalLong.empty();
        String fraction = m.group(4) == null ? "" : m.group(4);
        long millis = fraction.isEmpty() ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
        return OptionalLong.of(((hours * 60 + minutes) * 60 + seconds) * 1000 + millis);
    }

    /** {@code start-end} (or with an en dash, spaces allowed), if it is a valid range. */
    public static Optional<TimeRange> parseRange(String value) {
        if (value == null) return Optional.empty();
        Matcher m = RANGE.matcher(value);
        if (!m.matches()) return Optional.empty();
        OptionalLong start = parse(m.group(1));
        OptionalLong end = parse(m.group(2));
        if (start.isEmpty() || end.isEmpty() || end.getAsLong() <= start.getAsLong()) return Optional.empty();
        return Optional.of(new TimeRange(start.getAsLong(), end.getAsLong()));
    }

    /** How a time is written; milliseconds if it is not a clock time. */
    public static Format formatOf(String value) {
        Matcher m = CLOCK.matcher(value == null ? "" : value.strip());
        if (!m.matches()) return Format.MILLISECONDS;
        return new Format(true, m.group(1) != null, m.group(4) == null ? 0 : m.group(4).length());
    }

    /**
     * A time in a format. A clock time gets more decimals than the format when needed, so that no
     * precision is lost (e.g. 12100 ms in {@code m:ss} is written {@code 0:12.1}).
     */
    public static String write(long ms, Format format) {
        if (!format.clock()) return Long.toString(ms);
        long millis = ms % 1000;
        int needed = millis == 0 ? 0 : millis % 100 == 0 ? 1 : millis % 10 == 0 ? 2 : 3;
        int decimals = Math.max(format.decimals(), needed);
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds / 60) % 60;
        long seconds = totalSeconds % 60;
        StringBuilder b = new StringBuilder();
        if (format.hours() || hours > 0) {
            b.append(hours).append(':').append(String.format("%02d", minutes));
        } else {
            b.append(totalSeconds / 60);
        }
        b.append(':').append(String.format("%02d", seconds));
        if (decimals > 0) {
            b.append('.').append(String.format("%03d", millis), 0, decimals);
        }
        return b.toString();
    }

    /** A time for display: {@code m:ss.mmm}, with hours when needed. */
    public static String display(long ms) {
        return write(ms, Format.CLOCK_MS);
    }

    /** A range written with clock times ({@code m:ss.mmm-m:ss.mmm}). */
    public static String write(TimeRange range) {
        return display(range.startMs()) + "-" + display(range.endMs());
    }
}
