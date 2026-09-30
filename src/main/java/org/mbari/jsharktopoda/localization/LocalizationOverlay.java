package org.mbari.jsharktopoda.localization;

import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.scene.Scene;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import org.mbari.imgfx.BuilderCoordinator;
import org.mbari.imgfx.mediaview.MediaPaneController;
import org.mbari.imgfx.roi.Localization;
import org.mbari.imgfx.roi.RectangleView;
import org.mbari.imgfx.roi.RectangleViewEditor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a video's localizations over the video using imgfx. Only the records inside the current
 * time window exist as scene-graph nodes; the {@link LocalizationStore} is the source of truth.
 * JavaFX application thread only.
 */
public class LocalizationOverlay {

    /** Notified of changes the user makes by interacting with the video (never of remote-originated ones). */
    public interface Listener {
        void userAdded(LocalizationRecord record);

        void userUpdated(LocalizationRecord record);

        void userRemoved(List<UUID> uuids);

        void userSelected(List<UUID> uuids);
    }

    /** One drawn localization. */
    private static final class Shown {
        LocalizationRecord record;
        final Localization<RectangleView, MediaView> localization;
        final RectangleView view;
        final RectangleViewEditor editor;

        Shown(LocalizationRecord record,
              Localization<RectangleView, MediaView> localization,
              RectangleView view,
              RectangleViewEditor editor) {
            this.record = record;
            this.localization = localization;
            this.view = view;
            this.editor = editor;
        }
    }

    private static final System.Logger log = System.getLogger(LocalizationOverlay.class.getName());

    private final LocalizationStore store;
    private final MediaPaneController paneController;
    private final MediaPlayer mediaPlayer;
    private final Scene scene;
    private final Listener listener;
    private final BuilderCoordinator builderCoordinator = new BuilderCoordinator();
    private final Map<UUID, Shown> shown = new LinkedHashMap<>();
    private final Runnable removeStoreListener;
    private long lastPolledMillis = Long.MIN_VALUE;

    /**
     * MediaPlayer.currentTime fires no change events after a seek while the player is not playing,
     * so the playhead is polled once per pulse and the overlay refreshed when the millisecond changes.
     */
    private final AnimationTimer playheadPoller = new AnimationTimer() {
        @Override
        public void handle(long now) {
            var millis = Math.round(currentMillis());
            if (millis != lastPolledMillis) {
                lastPolledMillis = millis;
                refresh();
            }
        }
    };
    private final InvalidationListener scaleListener = obs -> rebuildAll();

    public LocalizationOverlay(LocalizationStore store,
                               MediaPaneController paneController,
                               MediaPlayer mediaPlayer,
                               Scene scene,
                               Listener listener) {
        this.store = store;
        this.paneController = paneController;
        this.mediaPlayer = mediaPlayer;
        this.scene = scene;
        this.listener = listener;

        removeStoreListener = store.addListener(this::refresh);
        var autoscale = paneController.getAutoscale();
        autoscale.scaleXProperty().addListener(scaleListener);
        autoscale.scaleYProperty().addListener(scaleListener);
        refresh();
        playheadPoller.start();
    }

    // ---------------------------------------------------------------- rendering

    private double currentMillis() {
        var millis = mediaPlayer.getCurrentTime().toMillis();
        return Double.isNaN(millis) ? 0D : millis;
    }

    /** Bring the drawn nodes in line with the store, the playhead and the selection. */
    void refresh() {
        var current = currentMillis();
        var visible = store.query(TimeWindow.from(current, TimeWindow.DEFAULT_MILLIS),
                TimeWindow.to(current, TimeWindow.DEFAULT_MILLIS));

        Set<UUID> visibleIds = new HashSet<>();
        for (var r : visible) {
            visibleIds.add(r.uuid());
        }

        shown.entrySet().removeIf(e -> {
            var stale = !visibleIds.contains(e.getKey());
            if (stale) {
                dispose(e.getValue());
            }
            return stale;
        });

        for (var r : visible) {
            var existing = shown.get(r.uuid());
            if (existing != null && existing.record.equals(r)) {
                continue;
            }
            if (existing != null) {
                dispose(existing);
                shown.remove(r.uuid());
            }
            show(r).ifPresent(s -> shown.put(r.uuid(), s));
        }
        applySelection();
    }

    /** The scale changed (window resized): rebuild from the store so imgfx's view/data sync can't drift. */
    private void rebuildAll() {
        shown.values().forEach(this::dispose);
        shown.clear();
        refresh();
    }

    private Optional<Shown> show(LocalizationRecord r) {
        return RectangleView.fromImageCoords((double) r.x(), (double) r.y(),
                        (double) r.width(), (double) r.height(), paneController.getAutoscale())
                .map(view -> {
                    Localization<RectangleView, MediaView> loc =
                            new Localization<>(view, paneController, r.uuid(), "");
                    var editor = new RectangleViewEditor(view, paneController.getPane());
                    applyStyle(view, r, false);
                    loc.setVisible(true);
                    builderCoordinator.addLocalization(loc);
                    return new Shown(r, loc, view, editor);
                });
    }

    private void dispose(Shown s) {
        s.view.setEditing(false);
        s.localization.setVisible(false);
        builderCoordinator.removeLocalization(s.localization);
    }

    private void applySelection() {
        var selected = store.selected();
        var single = selected.size() == 1 ? selected.get(0) : null;
        for (var s : shown.values()) {
            var uuid = s.record.uuid();
            var isSelected = store.isSelected(uuid);
            var shouldEdit = uuid.equals(single);

            if (s.view.isEditing() && !shouldEdit) {
                s.view.setEditing(false);
            }
            s.localization.setLabel(isSelected ? s.record.concept() : "");
            if (!s.view.isEditing()) {
                // imgfx restores the pre-edit look when editing ends, so style before editing starts
                applyStyle(s.view, s.record, isSelected);
                if (shouldEdit) {
                    s.view.setEditing(true);
                }
            }
        }
    }

    private static void applyStyle(RectangleView view, LocalizationRecord r, boolean selected) {
        var c = parseColor(r.color());
        var rect = view.getView();
        rect.setStroke(c);
        rect.setStrokeWidth(selected ? 4 : 2);
        rect.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), selected ? 0.35 : 0.1));
    }

    private static Color parseColor(String color) {
        try {
            return Color.web(color);
        }
        catch (IllegalArgumentException | NullPointerException e) {
            log.log(System.Logger.Level.WARNING, () -> "Bad localization color '" + color + "', using white");
            return Color.WHITE;
        }
    }

    public void dispose() {
        removeStoreListener.run();
        playheadPoller.stop();
        var autoscale = paneController.getAutoscale();
        autoscale.scaleXProperty().removeListener(scaleListener);
        autoscale.scaleYProperty().removeListener(scaleListener);
        shown.values().forEach(this::dispose);
        shown.clear();
    }
}
