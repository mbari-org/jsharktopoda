package org.mbari.jsharktopoda.localization;

import javafx.scene.Scene;
import javafx.scene.media.MediaPlayer;
import org.mbari.imgfx.mediaview.MediaPaneController;
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
    private LocalizationOverlay overlay;

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

    /** Called on the FX thread once the window and player are ready. Draws whatever is already in the store. */
    public void attach(MediaPaneController paneController, MediaPlayer mediaPlayer, Scene scene) {
        if (overlay != null) {
            return;
        }
        overlay = new LocalizationOverlay(store, paneController, mediaPlayer, scene,
                new LocalizationOverlay.Listener() {
                    @Override
                    public void userAdded(LocalizationRecord record) {
                        notifier.added(videoUuid, List.of(record));
                    }

                    @Override
                    public void userUpdated(LocalizationRecord record) {
                        notifier.updated(videoUuid, List.of(record));
                    }

                    @Override
                    public void userRemoved(List<UUID> uuids) {
                        notifier.removed(videoUuid, uuids);
                    }

                    @Override
                    public void userSelected(List<UUID> uuids) {
                        notifier.selected(videoUuid, uuids);
                    }
                });
    }

    public void dispose() {
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
    }
}
