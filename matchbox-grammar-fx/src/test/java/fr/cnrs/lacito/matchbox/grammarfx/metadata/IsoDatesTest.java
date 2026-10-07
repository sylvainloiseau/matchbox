package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.grammarfx.metadata.IsoDates.IsoDate;
import fr.cnrs.lacito.matchbox.grammarfx.metadata.IsoDates.Precision;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IsoDatesTest {

    private static IsoDate parse(String s) {
        return IsoDates.parse(s).orElseThrow(() -> new AssertionError("not parsed: " + s));
    }

    @Test
    void acceptedForms() {
        IsoDate offset = parse("2024-05-12T14:30:15.250+02:00");
        assertEquals(Precision.DATE_TIME, offset.precision());
        assertEquals(Optional.of(ZoneOffset.ofHours(2)), offset.offset());
        assertTrue(offset.seconds());
        assertEquals(Optional.of(ZoneOffset.UTC), parse("2024-05-12T14:30Z").offset());
        IsoDate local = parse("2024-05-12T14:30");
        assertEquals(Optional.of(LocalTime.of(14, 30)), local.time());
        assertFalse(local.seconds());
        assertEquals(LocalTime.of(14, 30), parse("2024-05-12 14:30").time().orElseThrow());
        assertEquals(Precision.DATE, parse("2024-05-12").precision());
        assertEquals(LocalDate.of(2024, 5, 12), parse("20240512").date());
        assertEquals(Precision.YEAR_MONTH, parse("2024-05").precision());
        assertEquals(Precision.YEAR, parse("2024").precision());
    }

    @Test
    void otherValuesAreNotDates() {
        for (String s : new String[] { "", "yesterday", "2024-13-01", "2024-02-30", "12/05/2024", "24" }) {
            assertTrue(IsoDates.parse(s).isEmpty(), s);
        }
    }

    @Test
    void displayIsLocalised() {
        assertEquals("May 12, 2024", IsoDates.display(parse("2024-05-12"), Locale.US));
        assertTrue(IsoDates.display(parse("2024-05-12T14:30"), Locale.US).contains("2:30"));
        assertTrue(IsoDates.display(parse("2024-05-12T14:30Z"), Locale.US).endsWith("UTC"));
        assertEquals("May 2024", IsoDates.display(parse("2024-05"), Locale.US));
    }

    @Test
    void writtenValuesStayIso() {
        LocalDate d = LocalDate.of(2024, 6, 1);
        assertEquals("2024-06-01", IsoDates.write(d, Optional.empty(), Optional.empty()));
        assertEquals("2024-06-01T09:05", IsoDates.write(d, Optional.of(LocalTime.of(9, 5)), Optional.empty()));
        // offset kept, seconds kept when the time did not change
        IsoDate original = parse("2024-05-12T14:30:15+02:00");
        assertEquals("2024-06-01T14:30:15+02:00", IsoDates.write(d, Optional.of(LocalTime.of(14, 30)), Optional.of(original)));
        assertEquals("2024-06-01T15:00+02:00", IsoDates.write(d, Optional.of(LocalTime.of(15, 0)), Optional.of(original)));
    }
}
