package org.mbari.m3.jsharktopoda.udp;

import com.google.gson.Gson;
import io.reactivex.subjects.Subject;
import javafx.scene.media.MediaPlayer;
import org.mbari.m3.jsharktopoda.javafx.MovieStageController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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

    public CommandService(Subject<GenericCommand> commandSubject, Subject<GenericResponse> responseSubject) {
        this.commandSubject = commandSubject;
        this.responseSubject = responseSubject;
        commandSubject.subscribe(this::handleCommand);
    }

    private void handleCommand(GenericCommand cmd) {
        String c = cmd.getCommand().toLowerCase();
        switch (c) {
            case "connect":
                // TODO configure framecapture UDP. This should be done in the UdpIO?
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
                doFramecapture(cmd);
                break;
            case "frame advance":
                doFrameAdvance(cmd);
                break;
            default:

        }
    }


    private void doOpen(GenericCommand cmd) {
        GenericResponse r = new GenericResponse("open");
        if (cmd.getUrl() != null && cmd.getUuid() != null) {
            MovieStageController stageController = MovieStageController.newInstance(cmd.getUrl().toString());
            stageController.readyProperty().addListener((ovs, oldv, newv) -> stageController.getStage().show());
            controllers.put(cmd.getUuid(), stageController);
            r.setStatus("ok");
        }
        else {
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
            controller.getStage().requestFocus();
        }
        responseSubject.onNext(r);
    }

    private void doRequestVideoInfo(GenericCommand cmd) {
        GenericResponse r = new GenericResponse("request video information");
        Optional<Map.Entry<UUID, MovieStageController>> focused = controllers.entrySet()
                .stream()
                .filter(e -> e.getValue().getStage().isFocused())
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
        GenericResponse r = new GenericResponse("request all information");
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
            else if (status == MediaPlayer.Status.PAUSED ||
                    status == MediaPlayer.Status.STOPPED ||
                    status == MediaPlayer.Status.STALLED ||
                    status == MediaPlayer.Status.HALTED) {
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
        GenericResponse r = new GenericResponse(cmd.getCommand());
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

    private void doFramecapture(GenericCommand cmd) {
        GenericResponse r = new GenericResponse(cmd.getCommand());
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
                    controller.frameCapture(new File(cmd.getImageLocation()));
                    r.setImageLocation(cmd.getImageLocation());
                    r.setImageReferenceUuid(cmd.getImageReferenceUuid());
                    r.setElapsedTime(Duration.ofMillis(Math.round(currentTime.toMillis())));
                    r.setStatus("ok");
                } catch (Exception e) {
                    log.warn("Failed to capture image and save to " + cmd.getImageLocation(), e);
                }
                responseSubject.onNext(r);
            });
            thread.setDaemon(true);
            thread.run();
        }
    }




}
