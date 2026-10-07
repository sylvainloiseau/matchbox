package fr.cnrs.lacito.matchbox.grammarapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TimeCodesTest {

    @Test
    void parsesMillisecondsAndClockTimes() {
        assertEquals(OptionalLong.of(12000), TimeCodes.parse("12000"));
        assertEquals(OptionalLong.of(12000), TimeCodes.parse("0:12"));
        assertEquals(OptionalLong.of(12500), TimeCodes.parse("0:12.5"));
        assertEquals(OptionalLong.of(12050), TimeCodes.parse("0:12.05"));
        assertEquals(OptionalLong.of(3723250), TimeCodes.parse("1:02:03.250"));
        assertEquals(OptionalLong.of(75000), TimeCodes.parse("1:15"));
        assertEquals(OptionalLong.of(4500000), TimeCodes.parse("75:00"), "minutes without hours may exceed 59");
        for (String bad : new String[] { "", "0:75", "1:60:00", "0:12.5000", "abc", "12.5", "-3" }) {
            assertTrue(TimeCodes.parse(bad).isEmpty(), bad);
        }
    }

    @Test
    void ranges() {
        assertEquals(Optional.of(new TimeRange(12000, 15500)), TimeCodes.parseRange("12000-15500"));
        assertEquals(Optional.of(new TimeRange(12000, 15500)), TimeCodes.parseRange("0:12 – 0:15.5"));
        assertTrue(TimeCodes.parseRange("15500-12000").isEmpty());
        assertTrue(TimeCodes.parseRange("12000").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new TimeRange(5, 5));
        assertEquals("0:12.000-0:15.500", TimeCodes.write(new TimeRange(12000, 15500)));
    }

    @Test
    void writingKeepsTheFormatWithoutLosingPrecision() {
        assertEquals("12100", TimeCodes.write(12100, TimeCodes.formatOf("12000")));
        assertEquals("0:12", TimeCodes.write(12000, TimeCodes.formatOf("0:15")));
        assertEquals("0:12.1", TimeCodes.write(12100, TimeCodes.formatOf("0:15")));
        assertEquals("0:12.100", TimeCodes.write(12100, TimeCodes.formatOf("0:15.000")));
        assertEquals("0:01:02.250", TimeCodes.write(62250, TimeCodes.formatOf("1:02:03.250")));
        assertEquals("1:02:03.250", TimeCodes.display(3723250));
        assertEquals("0:00.000", TimeCodes.display(0));
        assertEquals("1:15:00", TimeCodes.write(4500000, TimeCodes.formatOf("75:00")), "an hour or more gets the hours");
        for (String s : new String[] { "0:12.5", "1:02:03.250", "12000", "5:00" }) {
            assertEquals(s, TimeCodes.write(TimeCodes.parse(s).getAsLong(), TimeCodes.formatOf(s)), s);
        }
    }
}
