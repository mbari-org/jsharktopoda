package org.mbari.m3.jsharktopoda.javafx;


import javafx.application.Platform;
import org.mbari.vcr4j.remote.control.commands.FrameCapture;
import org.mbari.vcr4j.remote.control.commands.VideoInfo;
import org.mbari.vcr4j.remote.player.VideoController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class SharkVideoController implements VideoController {

    private final Map<UUID, MovieStageController> controllers = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(SharkVideoController.class);

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
        if (videoUuid != null && url != null) {
            if (controllers.containsKey(videoUuid)) {
                return true;
            }

            MovieStageController stageController = MovieStageController.newInstance(url.toExternalForm());
            stageController.readyProperty().addListener((ovs, oldv, newv) -> {
                stageController.getStage().show();
                log.atDebug()
                        .log("Opening video controller for " + videoUuid + " at " +
                                stageController.getMediaView()
                                        .getMediaPlayer()
                                        .getMedia()
                                        .getSource());

                log.atDebug()
                    .log(stageController.getMediaView()
                    .getMediaPlayer()
                    .getMedia().getDuration() + " is the duration");

            });



            stageController.stageProperty()
                            .addListener((obs, oldStage, newStage) -> {
                                newStage.sceneProperty()
                                        .addListener((obs2, oldScene, newScene) -> {
                                            newScene.getWindow()
                                                    .setOnCloseRequest(e -> {
                                                        close(videoUuid);
                                                    });
                                        });
                            });

            controllers.put(videoUuid, stageController);
            return true;
        }
        return false;
    }

    @Override
    public boolean close(UUID videoUuid) {
        if (videoUuid != null && controllers.containsKey(videoUuid)) {
            MovieStageController controller = controllers.remove(videoUuid);
            if (controller != null) {
                log.atDebug().log("Removing video controller for " + videoUuid + " at " +
                        controller.getMediaView()
                                .getMediaPlayer()
                                .getMedia()
                                .getSource());
                controller.close();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean show(UUID videoUuid) {
        if (videoUuid != null && controllers.containsKey(videoUuid)) {
            MovieStageController controller = controllers.get(videoUuid);
            Platform.runLater(() -> controller.getStage().requestFocus());
//            controller.getStage().requestFocus();
            return true;
        }
        return false;
    }

    @Override
    public Optional<VideoInfo> requestVideoInfo() {
        Optional<Map.Entry<UUID, MovieStageController>> focused = controllers.entrySet()
                .stream()
                .filter(e ->  {
                    try {
                        return e.getValue().getStage().isFocused();
                    }
                    catch (Exception ex) {
                        return false;
                    }})
                .findFirst();

        if (focused.isPresent()) {
            var e = focused.get();
            var videoUuid = e.getKey();
            var stageController = e.getValue();
            return toVideoInfo(videoUuid, stageController);
        }
        return Optional.empty();
    }

    private Optional<VideoInfo> toVideoInfo(UUID videoUuid, MovieStageController stageController) {
        var media =  stageController.getMediaPlayer().getMedia();
        var duration = media.getDuration();
        var frameRate = (Double) media.getMetadata().get("framerate");
        try {
            var url = new URL(stageController.getSource());
            var durationMillis = Math.round(duration.toMillis());
            var videoInfo = new VideoInfoImpl(videoUuid, url, durationMillis, frameRate);
            return Optional.of(videoInfo);
        }
        catch (MalformedURLException ex) {
            log.warn("Bad URL, {}, in controller with UUID = {}",
                    stageController.getSource(), videoUuid);
        }
        return Optional.empty();
    }

    @Override
    public List<VideoInfo> requestAllVideoInfos() {
        return controllers.entrySet()
                .stream()
                .map(e -> {
                    var videoUuid  = e.getKey();
                    var stageController = e.getValue();
                    return toVideoInfo(videoUuid, stageController);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }





    @Override
    public boolean play(UUID videoUuid, double rate) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            var player = controller.getMediaPlayer();
            log.atDebug().log("Playing video at rate " + rate);
            player.play();
            player.setRate(rate);
            return true;
        }
        return false;
    }

    @Override
    public boolean pause(UUID videoUuid) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            var player = controller.getMediaPlayer();
            player.pause();
            return true;
        }
        return false;
    }

    @Override
    public Optional<Double> requestRate(UUID videoUuid) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            var player = controller.getMediaPlayer();
            var rate = player.getCurrentRate();
            return Optional.of(rate);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Duration> requestElapsedTime(UUID videoUuid) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            var player = controller.getMediaPlayer();
            var currentTime = player.getCurrentTime();
            var currentTimeMillis = Math.round(currentTime.toMillis());
            var duration = Duration.ofMillis(currentTimeMillis);
            return Optional.of(duration);
        }
        return Optional.empty();
    }

    @Override
    public boolean seekElapsedTime(UUID videoUuid, Duration elapsedTime) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            var controller = opt.get();
            var player = controller.getMediaPlayer();
            var time = javafx.util.Duration.millis(elapsedTime.toMillis());
            Platform.runLater(() -> player.seek(time));
            return true;
        }
        return false;
    }

    @Override
    public boolean frameAdvance(UUID videoUuid) {
        var opt = findController(videoUuid);
        if (opt.isPresent()) {
            try {
                var controller = opt.get();
                var player = controller.getMediaPlayer();
                player.pause();
                var currentTime = player.getCurrentTime();
                log.atDebug().log("Current time is " + currentTime);
                player.getMedia().getMetadata().forEach((k, v) -> {
                    log.atDebug().log(k + " -> " + v);
                });
                var framerate = (Double) player.getMedia().getMetadata().get("framerate");
                var dt = 1 / framerate;
                var seekTime = currentTime.add(javafx.util.Duration.millis(dt));
                player.seek(seekTime);
                return true;
            }
            catch (Exception e) {
                log.atDebug()
                        .setCause(e)
                        .log("Frame advance failed");
                return false;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<FrameCapture> framecapture(UUID videoUuid,
                                                        UUID imageReferenceUuid,
                                                        Path saveLocation) {
        var opt = findController(videoUuid);
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
}
