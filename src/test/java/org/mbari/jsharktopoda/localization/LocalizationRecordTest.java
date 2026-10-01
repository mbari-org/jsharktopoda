package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.UUID;

public class LocalizationRecordTest {

    private final UUID id = UUID.randomUUID();

    private Localization full() {
        return new Localization(id, "sponge", 1000L, 25L, 10, 20, 30, 40, "#FF0000");
    }

    @Test
    public void fromRemoteCopiesAllFields() {
        var r = LocalizationRecord.fromRemote(full()).orElseThrow();
        assertEquals(new LocalizationRecord(id, "sponge", 1000, 25, 10, 20, 30, 40, "#FF0000"), r);
    }

    @Test
    public void fromRemoteAppliesDefaults() {
        var l = new Localization(id, null, 5L, null, 1, 2, 3, 4, null);
        var r = LocalizationRecord.fromRemote(l).orElseThrow();
        assertEquals("", r.concept());
        assertEquals(0L, r.durationMillis());
        assertEquals(LocalizationRecord.DEFAULT_COLOR, r.color());
    }

    @Test
    public void fromRemoteRejectsMalformed() {
        assertTrue(LocalizationRecord.fromRemote(null).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(null, "c", 1L, 0L, 1, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", null, 0L, 1, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, null, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, 1, 2, 0, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, 1, 2, 3, -1, null)).isEmpty());
    }

    @Test
    public void mergedWithKeepsExistingForOmittedFields() {
        var base = LocalizationRecord.fromRemote(full()).orElseThrow();
        var partial = new Localization(id, null, null, null, 99, null, null, null, null);
        var merged = base.mergedWith(partial);
        assertEquals(99, merged.x());
        assertEquals(20, merged.y());
        assertEquals(30, merged.width());
        assertEquals("sponge", merged.concept());
        assertEquals("#FF0000", merged.color());
        assertEquals(1000L, merged.elapsedTimeMillis());
    }

    @Test
    public void mergedWithAppliesProvidedFieldsAndIgnoresNonPositiveSize() {
        var base = LocalizationRecord.fromRemote(full()).orElseThrow();
        var partial = new Localization(id, "coral", 2000L, 7L, null, null, 0, 55, "#00FF00");
        var merged = base.mergedWith(partial);
        assertEquals("coral", merged.concept());
        assertEquals(2000L, merged.elapsedTimeMillis());
        assertEquals(7L, merged.durationMillis());
        assertEquals(30, merged.width());   // 0 is invalid, keep old
        assertEquals(55, merged.height());
        assertEquals("#00FF00", merged.color());
    }

    @Test
    public void toRemoteRoundTrips() {
        var r = LocalizationRecord.fromRemote(full()).orElseThrow();
        assertEquals(r, LocalizationRecord.fromRemote(r.toRemote()).orElseThrow());
    }
}
