package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalizationStoreTest {

    private final LocalizationStore store = new LocalizationStore();

    private static LocalizationRecord rec(UUID id, long t) {
        return new LocalizationRecord(id, "c", t, 0, 10, 20, 30, 40, "#FFFFFF");
    }

    @Test
    public void putAndGet() {
        var id = UUID.randomUUID();
        store.put(rec(id, 100));
        assertEquals(100, store.get(id).orElseThrow().elapsedTimeMillis());
        assertEquals(1, store.size());
    }

    @Test
    public void putReplacesAndReindexes() {
        var id = UUID.randomUUID();
        store.put(rec(id, 100));
        store.put(rec(id, 500));
        assertEquals(1, store.size());
        assertTrue(store.query(0, 200).isEmpty());
        assertEquals(1, store.query(400, 600).size());
    }

    @Test
    public void updateIgnoresUnknown() {
        var id = UUID.randomUUID();
        assertFalse(store.update(rec(id, 1)));
        assertEquals(0, store.size());
        store.put(rec(id, 1));
        assertTrue(store.update(rec(id, 2)));
        assertEquals(2, store.get(id).orElseThrow().elapsedTimeMillis());
    }

    @Test
    public void queryIsInclusiveAndHandlesSharedTimes() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        store.put(rec(a, 100));
        store.put(rec(b, 100));
        store.put(rec(c, 200));
        assertEquals(2, store.query(100, 100).size());
        assertEquals(3, store.query(100, 200).size());
        assertEquals(1, store.query(101, 200).size());
        assertTrue(store.query(300, 400).isEmpty());
        assertTrue(store.query(200, 100).isEmpty());
    }

    @Test
    public void removeDropsFromSelectionAndIgnoresUnknown() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.put(rec(a, 1));
        store.put(rec(b, 2));
        store.select(List.of(a, b));
        var removed = store.remove(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), removed);
        assertEquals(List.of(b), store.selected());
        assertTrue(store.query(0, 10).stream().noneMatch(r -> r.uuid().equals(a)));
    }

    @Test
    public void selectReplacesAndIgnoresUnknown() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.put(rec(a, 1));
        store.put(rec(b, 2));
        store.select(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), store.selected());
        store.select(List.of(b));
        assertEquals(List.of(b), store.selected());
        assertTrue(store.isSelected(b));
        assertFalse(store.isSelected(a));
        store.select(List.of());
        assertTrue(store.selected().isEmpty());
    }

    @Test
    public void clearEmptiesEverything() {
        var a = UUID.randomUUID();
        store.put(rec(a, 1));
        store.select(List.of(a));
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.selected().isEmpty());
        assertTrue(store.query(0, 10).isEmpty());
    }

    @Test
    public void listenerFiresOncePerBatchAndNotOnNoOps() {
        var count = new AtomicInteger();
        var remove = store.addListener(count::incrementAndGet);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.putAll(List.of(rec(a, 1), rec(b, 2)));
        assertEquals(1, count.get());
        store.select(List.of(a));
        assertEquals(2, count.get());
        store.select(List.of(a));            // no change
        store.remove(List.of(UUID.randomUUID()));   // nothing removed
        store.updateAll(List.of(rec(UUID.randomUUID(), 3)));   // unknown
        store.putAll(List.of());
        assertEquals(2, count.get());
        store.clear();
        assertEquals(3, count.get());
        store.clear();                        // already empty
        assertEquals(3, count.get());
        remove.run();
        store.put(rec(a, 1));
        assertEquals(3, count.get());
    }

    @Test
    public void handlesFiftyThousandRecords() {
        var records = new ArrayList<LocalizationRecord>();
        for (int i = 0; i < 50_000; i++) {
            records.add(rec(UUID.randomUUID(), i));
        }
        var start = System.nanoTime();
        store.putAll(records);
        for (int i = 0; i < 1000; i++) {
            assertEquals(201, store.query(i * 10L, i * 10L + 200).size());
        }
        var millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50_000, store.size());
        assertTrue("took " + millis + " ms", millis < 5_000);
    }
}
