package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.After;
import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LocalizationCommandRouterTest {

    private static class FakeTarget implements LocalizationTarget {
        final List<String> calls = new ArrayList<>();
        final List<List<LocalizationRecord>> added = new ArrayList<>();
        final List<List<Localization>> updated = new ArrayList<>();
        boolean explode = false;

        private void maybeExplode() {
            if (explode) {
                throw new IllegalStateException("boom");
            }
        }

        @Override public void add(List<LocalizationRecord> r) { calls.add("add"); added.add(r); maybeExplode(); }
        @Override public void update(List<Localization> p) { calls.add("update"); updated.add(p); }
        @Override public void remove(List<UUID> u) { calls.add("remove:" + u.size()); }
        @Override public void clear() { calls.add("clear"); }
        @Override public void select(List<UUID> u) { calls.add("select:" + u.size()); }
    }

    private final PublishSubject<LocalizationsCmd<?, ?>> subject = PublishSubject.create();
    private final UUID video = UUID.randomUUID();
    private final UUID otherVideo = UUID.randomUUID();
    private final FakeTarget target = new FakeTarget();
    private final FakeTarget otherTarget = new FakeTarget();
    private final LocalizationCommandRouter router = new LocalizationCommandRouter(
            subject,
            id -> id.equals(video) ? Optional.of(target)
                    : id.equals(otherVideo) ? Optional.of(otherTarget) : Optional.empty(),
            Runnable::run);

    @After
    public void tearDown() {
        router.close();
    }

    private static Localization loc(UUID id) {
        return new Localization(id, "c", 10L, 0L, 1, 2, 3, 4, null);
    }

    @Test
    public void routesOnlyValidRecordsOnAdd() {
        var good = UUID.randomUUID();
        var bad = new Localization(null, "c", 10L, 0L, 1, 2, 3, 4, null);
        var zero = new Localization(UUID.randomUUID(), "c", 10L, 0L, 1, 2, 0, 4, null);
        subject.onNext(new AddLocalizationsCmd(video, List.of(loc(good), bad, zero)));
        assertEquals(1, target.added.size());
        assertEquals(1, target.added.get(0).size());
        assertEquals(good, target.added.get(0).get(0).uuid());
    }

    @Test
    public void nullPayloadListsAreIgnored() {
        subject.onNext(new AddLocalizationsCmd(video, null));
        subject.onNext(new UpdateLocalizationsCmd(video, null));
        subject.onNext(new RemoveLocalizationsCmd(video, null));
        subject.onNext(new SelectLocalizationsCmd(video, null));
        assertEquals(List.of("add", "update", "remove:0", "select:0"), target.calls);
        assertTrue(target.added.get(0).isEmpty());
    }

    @Test
    public void routesUpdateRemoveSelectClear() {
        var id = UUID.randomUUID();
        subject.onNext(new UpdateLocalizationsCmd(video, List.of(loc(id))));
        subject.onNext(new RemoveLocalizationsCmd(video, List.of(id, UUID.randomUUID())));
        subject.onNext(new SelectLocalizationsCmd(video, List.of(id)));
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("update", "remove:2", "select:1", "clear"), target.calls);
        assertEquals(id, target.updated.get(0).get(0).getUuid());
    }

    @Test
    public void routesByVideoUuid() {
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(otherVideo)));
        assertTrue(target.calls.isEmpty());
        assertEquals(List.of("clear"), otherTarget.calls);
    }

    @Test
    public void unknownVideoIsIgnoredAndStreamContinues() {
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(UUID.randomUUID())));
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("clear"), target.calls);
    }

    @Test
    public void targetFailureDoesNotKillTheStream() {
        target.explode = true;
        subject.onNext(new AddLocalizationsCmd(video, List.of(loc(UUID.randomUUID()))));
        target.explode = false;
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("add", "clear"), target.calls);
    }

    @Test
    public void closeStopsRouting() {
        router.close();
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertTrue(target.calls.isEmpty());
    }
}
