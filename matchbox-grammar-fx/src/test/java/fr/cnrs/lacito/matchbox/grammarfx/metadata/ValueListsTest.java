package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValueListsTest {

    @Test
    void splitAndJoin() {
        assertEquals(List.of("Elaisa", "John Smith"), ValueLists.split("Elaisa, John Smith"));
        assertEquals(List.of("Smith, John", "Elaisa"), ValueLists.split("\"Smith, John\", Elaisa"));
        assertEquals(List.of("a", "b"), ValueLists.split("[a, b]"));
        assertTrue(ValueLists.split("  ").isEmpty());
        assertEquals(List.of("a"), ValueLists.split("a, ,"));
        assertEquals("Elaisa, \"Smith, John\"", ValueLists.join(List.of("Elaisa", "Smith, John"), false));
        assertEquals("[a, b]", ValueLists.join(List.of("a", "b"), true));
        List<String> items = List.of("x \"y\"", "z, w");
        assertEquals(items, ValueLists.split(ValueLists.join(items, false)));
    }

    @Test
    void defaultSchema() {
        assertInstanceOf(ValueType.Date.class, MetadataSchema.DEFAULT.typeOf("Date"));
        assertInstanceOf(ValueType.Media.class, MetadataSchema.DEFAULT.typeOf(" media "));
        assertEquals(new ValueType.ValueList("speakers"), MetadataSchema.DEFAULT.typeOf("speakers"));
        assertInstanceOf(ValueType.Plain.class, MetadataSchema.DEFAULT.typeOf("place"));
        assertTrue(MetadataSchema.DEFAULT.vocabulary("speakers").choices().isEmpty());
    }
}
