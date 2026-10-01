package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class PickingTest {

    private static LocalizationRecord box(int x, int y, int w, int h) {
        return new LocalizationRecord(UUID.randomUUID(), "c", 0, 0, x, y, w, h, null);
    }

    @Test
    public void missReturnsEmpty() {
        assertTrue(Picking.pick(List.of(box(10, 10, 20, 20)), 5, 5).isEmpty());
        assertTrue(Picking.pick(List.of(), 5, 5).isEmpty());
    }

    @Test
    public void edgesAreInclusive() {
        var b = box(10, 10, 20, 20);
        assertEquals(b.uuid(), Picking.pick(List.of(b), 10, 10).orElseThrow());
        assertEquals(b.uuid(), Picking.pick(List.of(b), 30, 30).orElseThrow());
    }

    @Test
    public void nearestEdgeWinsWhenBoxesOverlap() {
        var outer = box(0, 0, 100, 100);
        var inner = box(40, 40, 20, 20);
        // point (45,50): 5 from inner's left edge, 45 from outer's nearest edge
        assertEquals(inner.uuid(), Picking.pick(List.of(outer, inner), 45, 50).orElseThrow());
        // point (5,50): only inside outer
        assertEquals(outer.uuid(), Picking.pick(List.of(outer, inner), 5, 50).orElseThrow());
        // point (50,50): inner center is 10 from its edge, outer center 50 from its edge
        assertEquals(inner.uuid(), Picking.pick(List.of(outer, inner), 50, 50).orElseThrow());
    }

    @Test
    public void tieGoesToSmallerArea() {
        var big = box(0, 0, 100, 100);
        var small = box(0, 0, 50, 50);
        // (10,10) is 10 from both boxes' nearest edge
        assertEquals(small.uuid(), Picking.pick(List.of(big, small), 10, 10).orElseThrow());
    }
}
