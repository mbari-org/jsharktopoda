package org.mbari.jsharktopoda.localization;

import io.reactivex.rxjava3.disposables.Disposable;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import org.mbari.imgfx.etc.rx.EventBus;
import org.mbari.imgfx.etc.rx.events.AddRectangleEvent;
import org.mbari.imgfx.mediaview.MediaPaneController;
import org.mbari.imgfx.roi.Localization;
import org.mbari.imgfx.roi.RectangleBuilder;
import org.mbari.imgfx.roi.RectangleData;
import org.mbari.imgfx.roi.RectangleView;
import org.mbari.imgfx.roi.RectangleViewEditor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a video's localizations over the video using imgfx and lets the user select, edit, create
 * and delete them. Only the records inside the current time window exist as scene-graph nodes; the
 * {@link LocalizationStore} is the source of truth. JavaFX application thread only.
 */
public class LocalizationOverlay {

    /** Notified of changes the user makes by interacting with the video (never of remote-originated ones). */
    public interface Listener {
        void userAdded(LocalizationRecord record);

        void userUpdated(LocalizationRecord record);

        void userRemoved(List<UUID> uuids);

        void userSelected(List<UUID> uuids);
    }

    /**
     * An imgfx view plus its editor. imgfx registers a listener on the video's bounds for every
     * RectangleView it creates and never removes it, so views are pooled and reused rather than
     * created for every localization that scrolls into the time window.
     */
    private static final class Pooled {
        final RectangleView view;
        final RectangleViewEditor editor;

        Pooled(RectangleView view, RectangleViewEditor editor) {
            this.view = view;
            this.editor = editor;
        }
    }

    /** One drawn localization. */
    private static final class Shown {
        LocalizationRecord record;
        final Localization<RectangleView, MediaView> localization;
        final Pooled pooled;

        Shown(LocalizationRecord record, Localization<RectangleView, MediaView> localization, Pooled pooled) {
            this.record = record;
            this.localization = localization;
            this.pooled = pooled;
        }

        RectangleView view() {
            return pooled.view;
        }
    }

    private static final System.Logger log = System.getLogger(LocalizationOverlay.class.getName());

    private final LocalizationStore store;
    private final MediaPaneController paneController;
    private final MediaPlayer mediaPlayer;
    private final Scene scene;
    private final Listener listener;
    private final Map<UUID, Shown> shown = new LinkedHashMap<>();
    private final Deque<Pooled> pool = new ArrayDeque<>();
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

    /**
     * imgfx turns editing off for every view whenever the video's bounds change (window resize) and
     * leaves the views sized for the old layout. After the layout settles, rebuild from the store.
     */
    private boolean rebuildQueued;
    private final ChangeListener<Bounds> boundsListener = (obs, oldBounds, newBounds) -> {
        if (!rebuildQueued) {
            rebuildQueued = true;
            Platform.runLater(() -> {
                rebuildQueued = false;
                rebuildAll();
            });
        }
    };

    private final EventBus eventBus = new EventBus();
    private RectangleBuilder builder;
    private Disposable builtSubscription;
    private final EventHandler<MouseEvent> pressedFilter = this::onPressed;
    private final EventHandler<MouseEvent> releasedFilter = this::onReleased;
    private final EventHandler<KeyEvent> keyFilter = this::onKey;

    /**
     * Every imgfx editor registers a scene-level press handler that turns its own box's editing on/off
     * by click position. With overlapping boxes that fights the store's selection (and tears down the
     * handles being dragged), so presses are consumed at the pane, after imgfx's builder and editors
     * have seen them and before the scene does.
     */
    private final EventHandler<MouseEvent> consumePress = MouseEvent::consume;

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
        paneController.getAutoscale().getView().boundsInParentProperty().addListener(boundsListener);
        installGestures();
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

    private void rebuildAll() {
        shown.values().forEach(this::dispose);
        shown.clear();
        refresh();
    }

    private Pooled acquire() {
        var pooled = pool.poll();
        if (pooled == null) {
            var view = new RectangleView(new RectangleData(), paneController.getAutoscale());
            pooled = new Pooled(view, new RectangleViewEditor(view, paneController.getPane()));
        }
        return pooled;
    }

    private Optional<Shown> show(LocalizationRecord r) {
        var clipped = RectangleData.clip(r.x(), r.y(), r.width(), r.height(), paneController.getAutoscale());
        if (clipped.isEmpty()) {
            return Optional.empty();   // entirely outside the video
        }
        var pooled = acquire();
        var data = pooled.view.getData();
        var c = clipped.get();
        data.setX(c.getX());
        data.setY(c.getY());
        data.setWidth(c.getWidth());
        data.setHeight(c.getHeight());
        pooled.view.updateView();
        applyStyle(pooled.view, r, false);

        Localization<RectangleView, MediaView> loc =
                new Localization<>(pooled.view, paneController, r.uuid(), "");
        loc.setVisible(true);
        return Optional.of(new Shown(r, loc, pooled));
    }

    private void dispose(Shown s) {
        s.view().setEditing(false);
        s.localization.setVisible(false);
        pool.push(s.pooled);
    }

    private void applySelection() {
        var selected = store.selected();
        var single = selected.size() == 1 ? selected.get(0) : null;
        for (var s : shown.values()) {
            var uuid = s.record.uuid();
            var isSelected = store.isSelected(uuid);
            var shouldEdit = uuid.equals(single);

            if (s.view().isEditing() && !shouldEdit) {
                s.view().setEditing(false);
            }
            s.localization.setLabel(isSelected ? s.record.concept() : "");
            if (!s.view().isEditing()) {
                // imgfx restores the pre-edit look when editing ends, so style before editing starts
                applyStyle(s.view(), s.record, isSelected);
                if (shouldEdit) {
                    s.view().setEditing(true);
                }
            }
        }
        // a new box can only be drawn when no box is being edited
        builder.setDisabled(shown.values().stream().anyMatch(s -> s.view().isEditing()));
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
        playheadPoller.stop();
        uninstallGestures();
        removeStoreListener.run();
        paneController.getAutoscale().getView().boundsInParentProperty().removeListener(boundsListener);
        shown.values().forEach(this::dispose);
        shown.clear();
    }

    // ---------------------------------------------------------------- gestures

    private void installGestures() {
        builder = new RectangleBuilder(paneController, eventBus);
        builder.setEditColor(Color.color(1, 1, 1, 0.25));
        builder.setDisabled(false);
        builtSubscription = eventBus.toObserverable()
                .ofType(AddRectangleEvent.class)
                .subscribe(this::onBuilt);

        var pane = paneController.getPane();
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedFilter);
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, consumePress);
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedFilter);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
    }

    private void uninstallGestures() {
        var pane = paneController.getPane();
        pane.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressedFilter);
        pane.removeEventHandler(MouseEvent.MOUSE_PRESSED, consumePress);
        pane.removeEventFilter(MouseEvent.MOUSE_RELEASED, releasedFilter);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        if (builtSubscription != null) {
            builtSubscription.dispose();
        }
        builder.setDisabled(true);
    }

    private boolean isEditorNode(Object target) {
        // getNodes() always includes the box's own rectangle, so only count boxes that are being edited
        return shown.values().stream()
                .anyMatch(s -> s.view().isEditing() && s.pooled.editor.getNodes().contains(target));
    }

    /** A press on the video: pause, then select the box under the pointer or clear the selection. */
    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (isEditorNode(e.getTarget())) {
            return;   // the imgfx editor handles drags on the selected box and its handles
        }
        var autoscale = paneController.getAutoscale();
        Point2D p = autoscale.sceneToUnscaled(new Point2D(e.getSceneX(), e.getSceneY()));
        if (p.getX() < 0 || p.getY() < 0
                || p.getX() > autoscale.getUnscaledWidth() || p.getY() > autoscale.getUnscaledHeight()) {
            return;   // letterbox area, not on the video
        }

        mediaPlayer.pause();   // localization actions started here pause playback

        var records = shown.values().stream().map(s -> s.record).toList();
        var hit = Picking.pick(records, p.getX(), p.getY());
        var selected = store.selected();
        if (hit.isPresent()) {
            var uuid = hit.get();
            if (selected.size() == 1 && selected.get(0).equals(uuid)) {
                return;
            }
            store.select(List.of(uuid));
            listener.userSelected(List.of(uuid));
        }
        else if (!selected.isEmpty()) {
            store.select(List.of());
            listener.userSelected(List.of());
        }
    }

    /** End of a drag: if the edited box moved or was resized, write it to the store and tell the remote app. */
    private void onReleased(MouseEvent e) {
        for (var s : List.copyOf(shown.values())) {
            if (!s.view().isEditing() || !store.isSelected(s.record.uuid())) {
                continue;
            }
            s.view().updateData();   // converts the view back to video pixels and clips to the video
            var d = s.view().getData();
            var updated = new LocalizationRecord(s.record.uuid(),
                    s.record.concept(),
                    s.record.elapsedTimeMillis(),
                    s.record.durationMillis(),
                    (int) Math.round(d.getX()),
                    (int) Math.round(d.getY()),
                    (int) Math.max(1, Math.round(d.getWidth())),
                    (int) Math.max(1, Math.round(d.getHeight())),
                    s.record.color());
            if (!updated.equals(s.record)) {
                s.record = updated;       // set first so refresh() doesn't rebuild the node being edited
                s.view().updateView();    // show the clipped result
                store.update(updated);
                listener.userUpdated(updated);
            }
        }
    }

    /** imgfx finished drawing a new rectangle: make it a localization at the current frame and select it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onBuilt(AddRectangleEvent event) {
        var view = (RectangleView) event.localization().getDataView();
        var d = view.getData();
        var record = new LocalizationRecord(UUID.randomUUID(),
                LocalizationRecord.DEFAULT_CONCEPT,
                Math.round(currentMillis()),
                0,
                (int) Math.round(d.getX()),
                (int) Math.round(d.getY()),
                (int) Math.max(1, Math.round(d.getWidth())),
                (int) Math.max(1, Math.round(d.getHeight())),
                LocalizationRecord.DEFAULT_COLOR);
        store.put(record);
        store.select(List.of(record.uuid()));
        listener.userAdded(record);
        listener.userSelected(List.of(record.uuid()));
    }

    /** Cmd-Delete (macOS) / Ctrl-Delete (Windows, Linux): remove the selected localizations. */
    private void onKey(KeyEvent e) {
        if (!DeleteShortcut.matches(e.getCode(), e.isShortcutDown())) {
            return;
        }
        var selected = store.selected();
        if (selected.isEmpty()) {
            return;
        }
        var removed = store.remove(selected);
        if (!removed.isEmpty()) {
            listener.userRemoved(removed);
        }
        e.consume();
    }
}
