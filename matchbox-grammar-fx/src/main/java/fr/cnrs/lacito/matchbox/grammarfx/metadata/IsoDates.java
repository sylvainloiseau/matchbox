package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reading, displaying and writing ISO 8601 dates of metadata values. No JavaFX.
 *
 * <p>Accepted forms, tried in order: offset date-time ({@code 2024-05-12T14:30:15.250+02:00},
 * {@code …Z}; seconds and fraction optional), local date-time ({@code 2024-05-12T14:30[:15]}, a
 * space instead of {@code T} accepted), date ({@code 2024-05-12}), basic date ({@code 20240512}),
 * year-month ({@code 2024-05}), year ({@code 2024}).
 */
public final class IsoDates {

    /** How much of the date was written. */
    public enum Precision { YEAR, YEAR_MONTH, DATE, DATE_TIME }

    /**
     * A parsed value.
     *
     * @param date      the date (first day of the month or year for coarser precisions)
     * @param time      the time, for {@link Precision#DATE_TIME}
     * @param offset    the offset, if one was written
     * @param precision what was written
     * @param seconds   true if seconds were written
     */
    public record IsoDate(LocalDate date, Optional<LocalTime> time, Optional<ZoneOffset> offset,
                          Precision precision, boolean seconds) {}

    private static final Pattern SPACE_SEPARATOR = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:)");
    private static final DateTimeFormatter BASIC = DateTimeFormatter.ofPattern("uuuuMMdd")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("uuuu-MM")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern TIME_WITH_SECONDS = Pattern.compile(".*T\\d{2}:\\d{2}:\\d{2}.*");

    private IsoDates() {}

    /** The date written in {@code value}, if it is one of the accepted ISO 8601 forms. */
    public static Optional<IsoDate> parse(String value) {
        if (value == null) return Optional.empty();
        String v = SPACE_SEPARATOR.matcher(value.strip()).replaceFirst("$1T$2");
        if (v.isEmpty()) return Optional.empty();
        boolean seconds = TIME_WITH_SECONDS.matcher(v).matches();
        try {
            OffsetDateTime odt = OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return Optional.of(new IsoDate(odt.toLocalDate(), Optional.of(odt.toLocalTime()),
                Optional.of(odt.getOffset()), Precision.DATE_TIME, seconds));
        } catch (DateTimeException ignored) {
            // next form
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return Optional.of(new IsoDate(ldt.toLocalDate(), Optional.of(ldt.toLocalTime()),
                Optional.empty(), Precision.DATE_TIME, seconds));
        } catch (DateTimeException ignored) {
            // next form
        }
        for (DateTimeFormatter f : new DateTimeFormatter[] { DateTimeFormatter.ISO_LOCAL_DATE, BASIC }) {
            try {
                LocalDate d = LocalDate.parse(v, f);
                return Optional.of(new IsoDate(d, Optional.empty(), Optional.empty(), Precision.DATE, false));
            } catch (DateTimeException ignored) {
                // next form
            }
        }
        try {
            YearMonth ym = YearMonth.parse(v, YEAR_MONTH);
            return Optional.of(new IsoDate(ym.atDay(1), Optional.empty(), Optional.empty(), Precision.YEAR_MONTH, false));
        } catch (DateTimeException ignored) {
            // next form
        }
        if (YEAR.matcher(v).matches()) {
            return Optional.of(new IsoDate(Year.parse(v).atDay(1), Optional.empty(), Optional.empty(), Precision.YEAR, false));
        }
        return Optional.empty();
    }

    /** The date in the given locale, e.g. "12 May 2024, 14:30" or "May 2024". */
    public static String display(IsoDate d, Locale locale) {
        return switch (d.precision()) {
            case YEAR -> String.valueOf(d.date().getYear());
            case YEAR_MONTH -> DateTimeFormatter.ofPattern("LLLL uuuu", locale).format(d.date());
            case DATE -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(d.date());
            case DATE_TIME -> {
                LocalDateTime ldt = LocalDateTime.of(d.date(), d.time().orElse(LocalTime.MIDNIGHT));
                String s = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(locale).format(ldt);
                yield d.offset().map(o -> s + (o.equals(ZoneOffset.UTC) ? " UTC" : " (" + o.getId() + ")")).orElse(s);
            }
        };
    }

    /**
     * The ISO 8601 value of a date chosen by the user: {@code yyyy-MM-dd}, or
     * {@code yyyy-MM-ddTHH:mm} with a time. The offset of the original value is kept, and its
     * seconds if they were written and the time did not change.
     */
    public static String write(LocalDate date, Optional<LocalTime> time, Optional<IsoDate> original) {
        if (time.isEmpty()) {
            return date.toString();
        }
        LocalTime t = time.get();
        boolean keepSeconds = original.map(o -> o.seconds() && o.time().map(ot -> ot.withNano(0).withSecond(0)
            .equals(t.withNano(0).withSecond(0))).orElse(false)).orElse(false);
        String timeText = keepSeconds
            ? original.get().time().get().withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            : t.format(DateTimeFormatter.ofPattern("HH:mm"));
        String offset = original.flatMap(IsoDate::offset).map(ZoneOffset::getId).orElse("");
        return date + "T" + timeText + offset;
    }
}
