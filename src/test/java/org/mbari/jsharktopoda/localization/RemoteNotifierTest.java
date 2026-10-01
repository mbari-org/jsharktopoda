package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.RCommand;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemoteNotifierTest {

    private final List<RCommand<?, ?>> sent = new ArrayList<>();
    private final UUID video = UUID.randomUUID();
    private final LocalizationRecord rec =
            new LocalizationRecord(UUID.randomUUID(), "c", 10, 0, 1, 2, 3, 4, "#FFFFFF");

    private RemoteNotifier notifier(boolean connected) {
        return new RemoteNotifier(cmd -> {
            if (connected) {
                sent.add(cmd);
            }
            return connected;
        }, Runnable::run);
    }

    @Test
    public void sendsAdd() {
        notifier(true).added(video, List.of(rec));
        var cmd = (AddLocalizationsCmd) sent.get(0);
        assertEquals(video, cmd.getValue().getUuid());
        assertEquals(List.of(rec.toRemote()), cmd.getValue().getLocalizations());
    }

    @Test
    public void sendsUpdate() {
        notifier(true).updated(video, List.of(rec));
        var cmd = (UpdateLocalizationsCmd) sent.get(0);
        assertEquals(List.of(rec.toRemote()), cmd.getValue().getLocalizations());
    }

    @Test
    public void sendsRemoveAndSelect() {
        var n = notifier(true);
        n.removed(video, List.of(rec.uuid()));
        n.selected(video, List.of(rec.uuid()));
        assertEquals(List.of(rec.uuid()), ((RemoveLocalizationsCmd) sent.get(0)).getValue().getLocalizations());
        assertEquals(List.of(rec.uuid()), ((SelectLocalizationsCmd) sent.get(1)).getValue().getLocalizations());
    }

    @Test
    public void noConnectionDropsQuietly() {
        notifier(false).added(video, List.of(rec));
        assertTrue(sent.isEmpty());
    }

    @Test
    public void failingSenderDoesNotThrow() {
        new RemoteNotifier(cmd -> { throw new IllegalStateException("boom"); }, Runnable::run)
                .added(video, List.of(rec));
    }

    @Test
    public void emptyPayloadsAreNotSent() {
        var n = notifier(true);
        n.added(video, List.of());
        n.removed(video, List.of());
        assertTrue(sent.isEmpty());
    }

    @Test
    public void emptySelectionIsSent() {
        notifier(true).selected(video, List.of());
        assertEquals(1, sent.size());
    }
}
