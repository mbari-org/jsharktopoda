package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.RVideoIO;
import org.mbari.vcr4j.remote.control.commands.localization.Localization;
import org.mbari.vcr4j.remote.control.commands.localization.UpdateLocalizationsCmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VideoLocalizationsTest {

    private final UUID video = UUID.randomUUID();
    private final List<String> sends = new ArrayList<>();
    private final RemoteNotifier notifier = new RemoteNotifier(cmd -> sends.add(cmd.getName()), Runnable::run);
    private final VideoLocalizations vl = new VideoLocalizations(video, notifier);

    private static LocalizationRecord rec(UUID id) {
        return new LocalizationRecord(id, "sponge", 100, 0, 10, 20, 30, 40, "#FF0000");
    }

    @Test
    public void commandsBeforeAttachAreKept() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.select(List.of(id));
        assertEquals(1, vl.getStore().size());
        assertEquals(List.of(id), vl.getStore().selected());
    }

    @Test
    public void addOfExistingUuidReplaces() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.add(List.of(new LocalizationRecord(id, "coral", 100, 0, 1, 1, 5, 5, null)));
        assertEquals(1, vl.getStore().size());
        assertEquals("coral", vl.getStore().get(id).orElseThrow().concept());
    }

    @Test
    public void updateKeepsOmittedFields() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.update(List.of(new Localization(id, null, null, null, 99, null, null, null, null)));
        var r = vl.getStore().get(id).orElseThrow();
        assertEquals(99, r.x());
        assertEquals(20, r.y());
        assertEquals("sponge", r.concept());
        assertEquals("#FF0000", r.color());
    }

    @Test
    public void updateIgnoresUnknownAndNullUuid() {
        vl.update(List.of(new Localization(UUID.randomUUID(), "c", 1L, 0L, 1, 1, 1, 1, null),
                new Localization(null, "c", 1L, 0L, 1, 1, 1, 1, null)));
        assertEquals(0, vl.getStore().size());
    }

    @Test
    public void removeClearSelectAffectStore() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        vl.add(List.of(rec(a), rec(b)));
        vl.select(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), vl.getStore().selected());
        vl.remove(List.of(a, UUID.randomUUID()));
        assertEquals(1, vl.getStore().size());
        assertTrue(vl.getStore().selected().isEmpty());
        vl.clear();
        assertEquals(0, vl.getStore().size());
    }

    @Test
    public void remoteOriginatedChangesNeverEcho() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.update(List.of(new Localization(id, "x", 1L, 0L, 1, 1, 1, 1, null)));
        vl.select(List.of(id));
        vl.remove(List.of(id));
        vl.clear();
        assertTrue(sends.isEmpty());
    }

    /** vcr4j's Localization initializes color to #DDDDDD, so through Gson an omitted color is never null. */
    @Test
    public void partialUpdateFromTheWireKeepsColorAndOtherFields() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        var json = "{\"command\":\"update localizations\",\"uuid\":\"" + video + "\","
                + "\"localizations\":[{\"uuid\":\"" + id + "\",\"x\":300}]}";
        var request = RVideoIO.GSON.fromJson(json, UpdateLocalizationsCmd.Request.class);
        vl.update(request.getLocalizations());
        var r = vl.getStore().get(id).orElseThrow();
        assertEquals(300, r.x());
        assertEquals("#FF0000", r.color());
        assertEquals("sponge", r.concept());
        assertEquals(30, r.width());
    }

    @Test
    public void updateFromTheWireStillAppliesAnExplicitColor() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        var json = "{\"command\":\"update localizations\",\"uuid\":\"" + video + "\","
                + "\"localizations\":[{\"uuid\":\"" + id + "\",\"color\":\"#00FF00\"}]}";
        vl.update(RVideoIO.GSON.fromJson(json, UpdateLocalizationsCmd.Request.class).getLocalizations());
        assertEquals("#00FF00", vl.getStore().get(id).orElseThrow().color());
    }
}
