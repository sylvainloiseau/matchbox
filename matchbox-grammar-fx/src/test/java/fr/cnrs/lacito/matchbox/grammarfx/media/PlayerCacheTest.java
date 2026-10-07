package fr.cnrs.lacito.matchbox.grammarfx.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCacheTest {

    private final List<String> opened = new ArrayList<>();
    private final List<String> closed = new ArrayList<>();

    private PlayerCache<String> cache(int capacity) {
        return new PlayerCache<>(capacity, p -> {
            if (p.getFileName().toString().startsWith("bad")) throw new IllegalArgumentException("unsupported");
            opened.add(p.getFileName().toString());
            return p.getFileName().toString();
        }, closed::add);
    }

    @Test
    void eachFileIsOpenedOnce() {
        PlayerCache<String> cache = cache(8);
        String a = cache.get(Path.of("a.wav")).orElseThrow();
        assertSame(a, cache.get(Path.of("x/../a.wav")).orElseThrow());
        assertEquals(List.of("a.wav"), opened);
    }

    @Test
    void theLeastRecentlyUsedIsClosedButNotTheOneInUse() {
        PlayerCache<String> cache = cache(2);
        cache.get(Path.of("a.wav"));
        cache.setInUse(Path.of("a.wav"));
        cache.get(Path.of("b.wav"));
        cache.get(Path.of("c.wav"));
        assertEquals(List.of("b.wav"), closed, "a is in use, b is the least recently used other one");
        assertEquals(2, cache.size());
        cache.closeAll();
        assertEquals(0, cache.size());
        assertTrue(closed.containsAll(List.of("a.wav", "c.wav")));
    }

    @Test
    void filesThatCannotBeOpenedAreReported() {
        PlayerCache<String> cache = cache(8);
        assertTrue(cache.get(Path.of("bad.ogg")).isEmpty());
        assertEquals("unsupported", cache.error(Path.of("bad.ogg")).orElseThrow());
        assertTrue(cache.get(Path.of("bad.ogg")).isEmpty(), "not retried until closeAll");
    }
}
