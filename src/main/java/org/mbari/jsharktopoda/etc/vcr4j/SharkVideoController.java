package org.mbari.jsharktopoda.etc.vcr4j;


import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.media.MediaPlayer;
import org.mbari.jsharktopoda.MovieStageController;
import org.mbari.jsharktopoda.localization.RemoteNotifier;
import org.mbari.jsharktopoda.localization.VideoLocalizations;
import org.mbari.vcr4j.remote.control.commands.FrameCapture;
import org.mbari.vcr4j.remote.control.commands.VideoInfo;
import org.mbari.vcr4j.remote.player.VideoController;
import org.mbari.vcr4j.remote.player.VideoResult;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SharkVideoController implements VideoController {

    private final Map<UUID, MovieStageController> controllers = new ConcurrentHashMap<>();
    private final Map<UUID, VideoLocalizations> localizations = new ConcurrentHashMap<>();
    private final Map<UUID, ReversePlayback> reversing = new ConcurrentHashMap<>();
    /** The rate last asked for. MediaPlayer.currentRate lags and is wrong before the first play, so it is not trusted. */
    private final Map<UUID, Double> requestedRates = new ConcurrentHashMap<>();
    private final RemoteNotifier notifier;
    private volatile UUID lastShown;
    private static final long OPEN_TIMEOUT_SECONDS = 60;
    private static final long FX_TIMEOUT_SECONDS = 5;
    private static final double DEFAULT_FRAME_RATE = 30D;
    private static final double MAX_FORWARD_RATE = 8D;
    private static final System.Logger log = System.getLogger(SharkVideoController.class.getName());

    public SharkVideoController(RemoteNotifier notifier) {
        this.notifier = notifier;
    }

    public Optional<VideoLocalizations> findLocalizations(UUID videoUuid) {
        return Optional.ofNullable(localizations.get(videoUuid));
    }

    /** True while a video is loading or open, so localization commands for it are accepted. */
    @Override
    public boolean hasVideo(UUID videoUuid) {
        return videoUuid != null && controllers.containsKey(videoUuid);
    }

    public Optional<MovieStageController> findController(UUID videoUuid) {
        return Optional.ofNullable(controllers.get(videoUuid));
    }

    public Optional<Map.Entry<UUID, MovieStageController>> findControllerByUrl(URL url) {
        var urlString = url.toExternalForm();
        return controllers
                .entrySet()
                .stream()
                .filter(e -> e.getValue().getMediaPlayer().getMedia().getSource().equals(urlString))
                .findFirst();
    }

    @Override
    public boolean open(UUID videoUuid, URL url) {
        return openVideo(videoUuid, url).ok();
    }

    /**
     * Blocks until the video is ready to play, since vcr4j sends 'open done' as soon as this returns.
     * The UDP handler calls this off the FX thread. If a caller on the FX thread (the toolbar) opens a
     * video it must not wait, or the window would never get a chance to load.
     */
    @Override
    public VideoResult openVideo(UUID videoUuid, URL url) {
        if (videoUuid == null || url == null) {
            return VideoResult.failed(VideoResult.INVALID_MESSAGE);
        }
        if (controllers.containsKey(videoUuid)) {
            show(videoUuid);
            return VideoResult.success();
        }

        var ready = new CompletableFuture<Void>();
        localizations.putIfAbsent(videoUuid, new VideoLocalizations(videoUuid, notifier));
        MovieStageController stageController = MovieStageController.newInstance(url.toExternalForm());
        stageController.readyProperty().addListener((ovs, oldv, newv) -> {
            try {
                stageController.getStage().show();
                // A player that is still READY ignores seeks, so move it to PAUSED
                stageController.getMediaPlayer().pause();
                lastShown = videoUuid;
                var vl = localizations.get(videoUuid);
                if (vl != null) {
                    vl.attach(stageController.getMediaPaneController(),
                            stageController.getMediaPlayer(),
                            stageController.getStage().getScene());
                }
                // closing the window with its own close button must release the video, its player and its localizations
                stageController.getStage().setOnHidden(e -> closeIfCurrent(videoUuid, stageController));
                log.log(System.Logger.Level.DEBUG, () -> "Opened video controller for " + videoUuid + " at " +
                        stageController.getSource() + ", duration is " +
                        stageController.getMediaPlayer().getMedia().getDuration());
                ready.complete(null);
            }
            catch (RuntimeException e) {
                ready.completeExceptionally(e);
            }
        });

        stageController.stageProperty()
                .addListener((obs, oldStage, newStage) -> newStage.sceneProperty()
                        .addListener((obs2, oldScene, newScene) ->
                                newScene.getWindow().setOnCloseRequest(e -> closeIfCurrent(videoUuid, stageController))));

        controllers.put(videoUuid, stageController);

        if (Platform.isFxApplicationThread()) {
            return VideoResult.success();
        }
        try {
            ready.get(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return VideoResult.success();
        }
        catch (TimeoutException e) {
            log.log(System.Logger.Level.WARNING, "Timed out opening " + url + " for " + videoUuid);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "Failed to open " + url + " for " + videoUuid, e);
        }
        abandon(videoUuid);
        return VideoResult.failed("Unable to open video");
    }

    /**
     * Window events arrive after the fact. If the video was closed and opened again under the same uuid in
     * the meantime, the old window must not close the new one.
     */
    private void closeIfCurrent(UUID videoUuid, MovieStageController stageController) {
        if (controllers.get(videoUuid) == stageController) {
            close(videoUuid);
        }
    }

    /** Drops a video that never finished loading. Its window and player may not exist yet. */
    private void abandon(UUID videoUuid) {
        controllers.remove(videoUuid);
        var vl = localizations.remove(videoUuid);
        if (vl != null) {
            Platform.runLater(vl::dispose);
        }
    }

    @Override
    public boolean close(UUID videoUuid) {
        if (videoUuid != null && controllers.containsKey(videoUuid)) {
            MovieStageController controller = controllers.remove(videoUuid);
            if (controller != null) {
                stopReverse(videoUuid);
                requestedRates.remove(videoUuid);
                log.log(System.Logger.Level.DEBUG, () -> "Removing video controller for " + videoUuid);
                if (controller.isReady()) {
                    controller.close();
                }
                var vl = localizations.remove(videoUuid);
                if (vl != null) {
                    Platform.runLater(vl::dispose);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean show(UUID videoUuid) {
        var opt = findReadyController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            lastShown = videoUuid;
            Platform.runLater(() -> {
                var stage = controller.getStage();
                if (stage != null) {
                    stage.toFront();
                    stage.requestFocus();
                }
            });
            return true;
        }
        return false;
    }

    /** A controller whose media has loaded, so it has a player to talk to. */
    private Optional<MovieStageController> findReadyController(UUID videoUuid) {
        return findController(videoUuid).filter(MovieStageController::isReady);
    }

    /**
     * The video that has focus. If no window has focus (e.g. the remote app is in front), the one
     * shown most recently, so that the answer is stable while the player is in the background.
     */
    @Override
    public Optional<VideoInfo> requestVideoInfo() {
        var focused = controllers.entrySet()
                .stream()
                .filter(e -> e.getValue().isReady() && isFocused(e.getValue()))
                .findFirst()
                .map(Map.Entry::getKey);
        return focused
                .or(() -> Optional.ofNullable(lastShown).filter(this::isReady))
                .or(() -> controllers.keySet().stream().filter(this::isReady).findFirst())
                .flatMap(uuid -> findController(uuid).flatMap(c -> toVideoInfo(uuid, c)));
    }

    private boolean isReady(UUID videoUuid) {
        return findReadyController(videoUuid).isPresent();
    }

    private static boolean isFocused(MovieStageController controller) {
        try {
            return controller.getStage().isFocused();
        }
        catch (Exception ex) {
            return false;
        }
    }

    private Optional<VideoInfo> toVideoInfo(UUID videoUuid, MovieStageController stageController) {
        var media = stageController.getMediaPlayer().getMedia();
        try {
            var url = new URL(stageController.getSource());
            var durationMillis = Math.round(media.getDuration().toMillis());
            return Optional.of(new VideoInfoImpl(videoUuid, url, durationMillis, frameRate(media)));
        }
        catch (MalformedURLException ex) {
            log.log(System.Logger.Level.WARNING, "Bad URL, " + stageController.getSource() +
                    ", in controller with UUID = " + videoUuid);
        }
        return Optional.empty();
    }

    /** JavaFX rarely reports a frame rate, so fall back to a typical one rather than failing. */
    private static double frameRate(javafx.scene.media.Media media) {
        if (media.getMetadata().get("framerate") instanceof Number n && n.doubleValue() > 0) {
            return n.doubleValue();
        }
        return media.getTracks()
                .stream()
                .map(t -> t.getMetadata().get("framerate"))
                .filter(v -> v instanceof Number n && n.doubleValue() > 0)
                .map(v -> ((Number) v).doubleValue())
                .findFirst()
                .orElse(DEFAULT_FRAME_RATE);
    }

    @Override
    public List<VideoInfo> requestAllVideoInfos() {
        return controllers.entrySet()
                .stream()
                .filter(e -> e.getValue().isReady())
                .map(e -> toVideoInfo(e.getKey(), e.getValue()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public boolean play(UUID videoUuid, double rate) {
        var opt = findReadyController(videoUuid);
        if (opt.isPresent()) {
            var player = opt.get().getMediaPlayer();
            log.log(System.Logger.Level.DEBUG, "Playing video at rate " + rate);
            stopReverse(videoUuid);
            requestedRates.put(videoUuid, rate);
            if (rate < 0) {
                // MediaPlayer cannot play backwards, so step it back with seeks
                var reverse = new ReversePlayback(player, rate);
                reversing.put(videoUuid, reverse);
                onFxThread(() -> {
                    player.pause();
                    reverse.start();
                });
            }
            else if (rate == 0) {
                onFxThread(player::pause);
            }
            else {
                var playRate = Math.min(rate, MAX_FORWARD_RATE);
                requestedRates.put(videoUuid, playRate);
                onFxThread(() -> {
                    player.setRate(playRate);
                    player.play();
                });
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean pause(UUID videoUuid) {
        var opt = findReadyController(videoUuid);
        if (opt.isPresent()) {
            var player = opt.get().getMediaPlayer();
            stopReverse(videoUuid);
            requestedRates.put(videoUuid, 0D);
            onFxThread(player::pause);
            return true;
        }
        return false;
    }

    @Override
    public Optional<Double> requestRate(UUID videoUuid) {
        var reverse = reversing.get(videoUuid);
        if (reverse != null) {
            return Optional.of(reverse.rate);
        }
        return findReadyController(videoUuid).map(c -> {
            var player = c.getMediaPlayer();
            var status = player.getStatus();
            var playing = status == MediaPlayer.Status.PLAYING || status == MediaPlayer.Status.STALLED;
            return playing ? requestedRates.getOrDefault(videoUuid, player.getCurrentRate()) : 0D;
        });
    }

    @Override
    public Optional<Duration> requestElapsedTime(UUID videoUuid) {
        return findReadyController(videoUuid)
                .map(c -> Duration.ofMillis(Math.round(c.getMediaPlayer().getCurrentTime().toMillis())));
    }

    @Override
    public boolean seekElapsedTime(UUID videoUuid, Duration elapsedTime) {
        var opt = findReadyController(videoUuid);
        if (opt.isPresent()) {
            var player = opt.get().getMediaPlayer();
            stopReverse(videoUuid);
            var time = javafx.util.Duration.millis(elapsedTime.toMillis());
            onFxThread(() -> player.seek(time));
            return true;
        }
        return false;
    }

    @Override
    public VideoResult seekVideo(UUID videoUuid, Duration elapsedTime) {
        var opt = findReadyController(videoUuid);
        if (opt.isEmpty()) {
            return VideoResult.failed(hasVideo(videoUuid) ? "Unable to seek video" : VideoResult.NO_VIDEO_FOR_UUID);
        }
        var durationMillis = opt.get().getMediaPlayer().getMedia().getDuration().toMillis();
        if (elapsedTime.isNegative()) {
            return VideoResult.failed(VideoResult.SEEK_BEFORE_START);
        }
        if (elapsedTime.toMillis() > durationMillis) {
            return VideoResult.failed(VideoResult.SEEK_PAST_END);
        }
        return VideoResult.of(seekElapsedTime(videoUuid, elapsedTime), "Unable to seek video");
    }

    @Override
    public boolean frameAdvance(UUID videoUuid) {
        return advanceFrame(videoUuid, true).ok();
    }

    @Override
    public VideoResult advanceFrame(UUID videoUuid, boolean forward) {
        var opt = findReadyController(videoUuid);
        if (opt.isEmpty()) {
            return VideoResult.failed(hasVideo(videoUuid) ? VideoResult.CANNOT_ADVANCE : VideoResult.NO_VIDEO_FOR_UUID);
        }
        var player = opt.get().getMediaPlayer();
        var media = player.getMedia();
        var stepMillis = 1000D / frameRate(media);
        var durationMillis = media.getDuration().toMillis();
        var currentMillis = player.getCurrentTime().toMillis();
        if (!forward && currentMillis - stepMillis < 0 || forward && currentMillis + stepMillis > durationMillis) {
            return VideoResult.failed(VideoResult.CANNOT_ADVANCE);
        }
        stopReverse(videoUuid);
        requestedRates.put(videoUuid, 0D);
        if (forward) {
            onFxThread(() -> stepForward(player));
        }
        else {
            // JavaFX seeks to the keyframe at or before the target, so this may go back more than one frame
            var targetMillis = currentMillis - stepMillis;
            onFxThread(() -> {
                player.pause();
                player.seek(javafx.util.Duration.millis(targetMillis));
            });
        }
        return VideoResult.success();
    }

    /**
     * Seeking forward by one frame snaps to a keyframe and so goes nowhere, so let the player run until
     * its time advances and then pause it. Must be called on the FX thread.
     */
    private static void stepForward(MediaPlayer player) {
        var start = player.getCurrentTime();
        var stop = new PauseTransition(javafx.util.Duration.seconds(2));
        var listener = new javafx.beans.value.ChangeListener<javafx.util.Duration>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.util.Duration> obs,
                                javafx.util.Duration oldTime, javafx.util.Duration newTime) {
                if (newTime.greaterThan(start)) {
                    player.pause();
                    player.currentTimeProperty().removeListener(this);
                    stop.stop();
                }
            }
        };
        stop.setOnFinished(e -> {
            player.pause();
            player.currentTimeProperty().removeListener(listener);
        });
        player.currentTimeProperty().addListener(listener);
        player.setRate(1);
        player.play();
        stop.play();
    }

    @Override
    public CompletableFuture<FrameCapture> framecapture(UUID videoUuid,
                                                        UUID imageReferenceUuid,
                                                        Path saveLocation) {
        var opt = findReadyController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            return controller.frameCapture(saveLocation)
                    .thenApply(data -> new FrameCaptureImpl(videoUuid,
                            imageReferenceUuid,
                            saveLocation.toString(),
                            data.elapsedTimeMillis()));
        }
        return CompletableFuture.failedFuture(new RuntimeException("No MovieStageController found for video UUID of " + videoUuid));
    }

    private void stopReverse(UUID videoUuid) {
        var reverse = reversing.remove(videoUuid);
        if (reverse != null) {
            Platform.runLater(reverse::stop);
        }
    }

    /**
     * Runs a MediaPlayer operation on the FX thread and waits for it, so the state a command reports
     * afterwards (rate, elapsed time) already reflects it. Callers on the FX thread just run it.
     */
    private static void onFxThread(Runnable operation) {
        if (Platform.isFxApplicationThread()) {
            operation.run();
            return;
        }
        var done = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                operation.run();
                done.complete(null);
            }
            catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        try {
            done.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            log.log(System.Logger.Level.WARNING, "Player operation failed", e);
        }
    }

    /** Emulates reverse playback, which MediaPlayer does not support, by stepping the playhead back. */
    private final class ReversePlayback {
        private static final int TICK_MILLIS = 50;

        private final double rate;
        private final Timeline timeline;
        private double positionMillis;

        ReversePlayback(MediaPlayer player, double rate) {
            this.rate = rate;
            this.timeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(TICK_MILLIS), e -> {
                positionMillis = Math.max(0, positionMillis + rate * TICK_MILLIS);
                player.seek(javafx.util.Duration.millis(positionMillis));
                if (positionMillis == 0) {
                    stop();
                    reversing.values().remove(this);
                }
            }));
            this.timeline.setCycleCount(Timeline.INDEFINITE);
            this.positionMillis = player.getCurrentTime().toMillis();
        }

        void start() {
            timeline.play();
        }

        void stop() {
            timeline.stop();
        }
    }
}
