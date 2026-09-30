package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything localization-related for one video. Created when the video is opened (before its
 * window is ready) so commands that arrive while the video loads are kept in the store and shown
 * once the window exists. JavaFX application thread only.
 */
public class VideoLocalizations implements LocalizationTarget {

    private final UUID videoUuid;
    private final RemoteNotifier notifier;
    private final LocalizationStore store = new LocalizationStore();

    public VideoLocalizations(UUID videoUuid, RemoteNotifier notifier) {
        this.videoUuid = videoUuid;
        this.notifier = notifier;
    }

    public UUID getVideoUuid() {
        return videoUuid;
    }

    public LocalizationStore getStore() {
        return store;
    }

    @Override
    public void add(List<LocalizationRecord> records) {
        store.putAll(records);
    }

    @Override
    public void update(List<Localization> partials) {
        var merged = partials.stream()
                .filter(p -> p != null && p.getUuid() != null)
                .map(p -> store.get(p.getUuid()).map(existing -> existing.mergedWith(p)))
                .flatMap(Optional::stream)
                .toList();
        store.updateAll(merged);
    }

    @Override
    public void remove(List<UUID> uuids) {
        store.remove(uuids);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public void select(List<UUID> uuids) {
        store.select(uuids);
    }
}
