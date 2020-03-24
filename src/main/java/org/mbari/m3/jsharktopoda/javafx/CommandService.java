package org.mbari.m3.jsharktopoda.javafx;

import com.google.gson.Gson;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.Subject;
import javafx.scene.media.MediaPlayer;
import mbarix4j.io.FileUtilities;
import org.mbari.m3.jsharktopoda.udp.GenericCommand;
import org.mbari.m3.jsharktopoda.udp.GenericResponse;
import org.mbari.m3.jsharktopoda.udp.UdpIO;
import org.mbari.m3.jsharktopoda.udp.Video;
import mbarix4j.net.URLUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * @author Brian Schlining
 * @since 2017-12-05T13:54:00
 */
public class CommandService {

    private final Subject<GenericCommand> commandSubject;
    private final Subject<GenericResponse> responseSubject;
    private final Map<UUID, MovieStageController> controllers = new ConcurrentHashMap<>();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Gson gson = UdpIO.newGson();
    private final FramecaptureUdpIO framecaptureIO;

    public CommandService(Subject<GenericCommand> commandSubject, Subject<GenericResponse> responseSubject) {
        this.commandSubject = commandSubject;
        this.responseSubject = responseSubject;
        this.framecaptureIO = new FramecaptureUdpIO(this, commandSubject);
        Scheduler scheduler = Schedulers.from(new PlatformExecutor());
        commandSubject.observeOn(scheduler)
                .subscribe(this::handleCommand);
    }

    public Gson getGson() {
        return gson;
    }

    private void handleCommand(GenericCommand cmd) {
        String c = cmd.getCommand().toLowerCase();
        switch (c) {
            case "connect":
                // Handled in FramecaptureUdpIO
                break;
            case "open":
                doOpen(cmd);
                break;
            case "close":
                doClose(cmd);
                break;
            case "show":
                doShow(cmd);
                break;
            case "request video information":
                doRequestVideoInfo(cmd);
                break;
            case "request all information":
                doRequestAllVideoInfos(cmd);
                break;
            case "play":
                doPlay(cmd);
                break;
            case "pause":
                doPause(cmd);
                break;
            case "request elapsed time":
                doRequestElapsedTime(cmd);
                break;
            case "request status":
                doRequestStatus(cmd);
                break;
            case "seek elapsed time":
                doSeekElapsedTime(cmd);
                break;
            case "framecapture":
                //doFramecapture(cmd);
                break;
            case "frame advance":
                doFrameAdvance(cmd);
                break;
            default:

        }
    }


    private void doOpen(GenericCommand cmd) {
        GenericResponse r = new GenericResponse(cmd);
        if (cmd.getUrl() != null && cmd.getUuid() != null) {
                MovieStageController stageController = MovieStageController.newInstance(cmd.getUrl().toString());
                stageController.readyProperty().addListener((ovs, oldv, newv) -> {
                    stageController.getStage().show();
                });
                controllers.put(cmd.getUuid(), stageController);
            r.setStatus("ok");
        } else {
            log.warn("Bad command: {}", gson.toJson(cmd));
            r.setStatus("failed");
        }
        responseSubject.onNext(r);
    }

    private void doClose(GenericCommand cmd) {
        GenericResponse r = new GenericResponse(); // Do nothing response
        if (cmd.getUuid() != null && controllers.containsKey(cmd.getUuid())) {
            MovieStageController controller = controllers.remove(cmd.getUuid());
            controller.close();
        }
        responseSubject.onNext(r);
    }

    private void doShow(GenericCommand cmd) {
        GenericResponse r = new GenericResponse();
        if (cmd.getUuid() != null && controllers.containsKey(cmd.getUuid())) {
                MovieStageController controller = controllers.get(cmd.getUuid());
                //Platform.runLater(() -> controller.getStage().requestFocus());
            controller.getStage().requestFocus();
        }
        responseSubject.onNext(r);
    }

    private void doRequestVideoInfo(GenericCommand cmd) {
        GenericResponse r = new GenericResponse(cmd);
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

        focused.ifPresent(e -> {
            r.setUuid(e.getKey());
            try {
                r.setUrl(new URL(e.getValue().getSource()));
            }
            catch (MalformedURLException ex) {
                log.warn("Bad URL, {}, in controller with UUID = {}",
                        e.getValue().getSource(), e.getKey());
            }
            responseSubject.onNext(r);
        });
    }

    private void doRequestAllVideoInfos(GenericCommand cmd) {
        GenericResponse r = new GenericResponse(cmd);
        Video[] videos = controllers.entrySet()
                .stream()
                .map(e -> {
                    try {
                        URL url = new URL(e.getValue().getSource());
                        return Optional.of(new Video(e.getKey(), url));
                    }
                    catch (MalformedURLException ex) {
                        log.warn("Bad URL, {}, in controller with UUID = {}",
                                e.getValue().getSource(), e.getKey());
                        return Optional.empty();
                    }
                })
                .toArray(i -> new Video[i]);
        r.setVideos(videos);
        responseSubject.onNext(r);
    }

    private void doPlay(GenericCommand cmd) {
        doAction(cmd, (mediaPlayer, genericResponse) -> {
            double rate = cmd.getRate() == null ? 1.0 : cmd.getRate();
                mediaPlayer.setRate(rate);
                mediaPlayer.play();
            genericResponse.setStatus("ok");
        });
    }

    private void doPause(GenericCommand cmd) {
        doAction(cmd, ((mediaPlayer, genericResponse) -> {
            mediaPlayer.pause();
            genericResponse.setStatus("ok");
        }));
    }

    private void doRequestElapsedTime(GenericCommand cmd) {
        doAction(cmd, (mediaPlayer, genericResponse) -> {
            javafx.util.Duration currentTime = mediaPlayer.getCurrentTime();
            genericResponse.setElapsedTime(Duration.ofMillis(Math.round(currentTime.toMillis())));
            genericResponse.setStatus(null);
        });
    }

    private void doRequestStatus(GenericCommand cmd) {
        doAction(cmd, (mediaPlayer, genericResponse) -> {
            MediaPlayer.Status status = mediaPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                double rate = mediaPlayer.getRate();
                if (rate - 1.0 < 0.01) {
                    genericResponse.setStatus("playing");
                }
                else if (rate > 0) {
                    genericResponse.setStatus("shuttling forward");
                }
                else {
                    genericResponse.setStatus("shuttling reverse");
                }
            }
            else {
                genericResponse.setStatus("paused");
            }
        });
    }

    private void doSeekElapsedTime(GenericCommand cmd) {
        doAction(cmd, (mediaPlayer, genericResponse) -> {
            if (cmd.getElapsedTime() != null) {
                mediaPlayer.seek(javafx.util.Duration.millis(cmd.getElapsedTime().toMillis()));
                genericResponse.setResponse(null); // No response is expected
            }
        });
    }

    private void doFrameAdvance(GenericCommand cmd) {
        doAction(cmd, (mediaPlayer, genericResponse) -> {
            mediaPlayer.pause();
            javafx.util.Duration currentTime = mediaPlayer.getCurrentTime();
            // 29.97 fps is about 33 millis per frame. So we'll just use that.
            javafx.util.Duration seekTime = currentTime.add(javafx.util.Duration.millis(33));
            mediaPlayer.seek(seekTime);
            genericResponse.setResponse(null); // No response is expected
        });
    }

    private void doAction(GenericCommand cmd, BiConsumer<MediaPlayer, GenericResponse> fn) {
        GenericResponse r = new GenericResponse(cmd);
        r.setStatus("failed");
        r.setUuid(r.getUuid());
        if (cmd.getUuid() != null && controllers.containsKey(cmd.getUuid())) {
            try {
                MovieStageController controller = controllers.get(cmd.getUuid());
                MediaPlayer mediaPlayer = controller.getMediaPlayer();
                fn.accept(mediaPlayer, r);
            }
            catch (Exception e) {
                r.setStatus("failed");
                log.warn("Failed to execute " + gson.toJson(cmd), e);
            }
        }
        else {
            r.setStatus("failed");
        }
        responseSubject.onNext(r);
    }

    public CompletableFuture<GenericResponse> doFramecapture(GenericCommand cmd) {
        CompletableFuture<GenericResponse> f = new CompletableFuture<>();
        GenericResponse r = new GenericResponse(cmd);
        r.setImageReferenceUuid(cmd.getImageReferenceUuid());
        r.setStatus("failed");
        if (cmd.getUuid() != null &&
                controllers.containsKey(cmd.getUuid()) &&
                cmd.getImageReferenceUuid() != null &&
                cmd.getImageLocation() != null) {

            MovieStageController controller = controllers.get(cmd.getUuid());

            // Fire and forget thread
            Thread thread = new Thread(() -> {
                try {
                    javafx.util.Duration currentTime = controller.getMediaPlayer().getCurrentTime();
                    URL url = new URL(cmd.getImageLocation());
                    File file = URLUtilities.toFile(url);
                    controller.frameCapture(file);
                    r.setImageLocation(cmd.getImageLocation());
                    r.setElapsedTime(Duration.ofMillis(Math.round(currentTime.toMillis())));
                    r.setStatus("ok");
                } catch (Exception e) {
                    log.warn("Failed to capture image and save to " + cmd.getImageLocation(), e);
                }
                f.complete(r);
            });
            thread.setDaemon(true);
            thread.run();
        }
        return f;
    }




}
