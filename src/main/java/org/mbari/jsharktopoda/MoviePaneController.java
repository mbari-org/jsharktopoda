package org.mbari.jsharktopoda;


/**
 * Created by brian on 4/29/14.
 */
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.mbari.jcommons.awt.ImageUtil;
import org.mbari.jsharktopoda.etc.javafx.JFXUtilities;
import org.mbari.jsharktopoda.etc.javafx.MaterialIcons;
import org.mbari.jsharktopoda.etc.vcr4j.FrameCaptureData;
import org.mbari.jsharktopoda.etc.jdk.Preconditions;


import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MoviePaneController implements Initializable, FrameCaptureService {

    @FXML
    private AnchorPane anchorPane;

    @FXML
    private MediaView mediaView;

    @FXML
    private Button playButton;

    @FXML
    private Label timeLabel;

    @FXML
    private Slider scrubber;

    @FXML
    private Label maxTimeLabel;

    private MediaPlayer mediaPlayer;

    private BooleanProperty ready = new SimpleBooleanProperty(false);

    private final System.Logger log = System.getLogger(MoviePaneController.class.getName());

    private Text playIcon;
    private Text pauseIcon;

    private final ExecutorService imageWriterExecutor = Executors.newCachedThreadPool();

    private record PlayState(MediaPlayer.Status status, double rate) {}
    private AtomicReference<PlayState> prescrubState = new AtomicReference<>();


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        playButton.setText(null);
        playIcon = MaterialIcons.PLAY_ARROW;
        pauseIcon = MaterialIcons.PAUSE;
        playButton.setGraphic(playIcon);
        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(anchorPane.widthProperty());
        mediaView.fitHeightProperty().bind(anchorPane.heightProperty());
    }

    public AnchorPane getRoot() {
        return anchorPane;
    }

    public AnchorPane getAnchorPane() {
        return anchorPane;
    }

    public MediaView getMediaView() {
        return mediaView;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public Label getMaxTimeLabel() {
        return maxTimeLabel;
    }

    public void setMediaLocation(String mediaLocation, Consumer<MoviePaneController> onReadyRunnable) {

        Preconditions.checkArgument(mediaLocation != null, "The medialocation can not be null");

        scrubber.setDisable(true);
        Media media = null;
        try {
            media = new Media(mediaLocation);
        }
        catch (MediaException e) {
            e.printStackTrace();
            return;
        }
        //final ObservableMap<String,Object> metadata = media.getMetadata();
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        // --- Configure MediaPlayer
        mediaPlayer.currentTimeProperty().addListener(observable -> updateValues());

        mediaPlayer.setOnPlaying(() -> playButton.setGraphic(pauseIcon));

        mediaPlayer.setOnPaused(() -> playButton.setGraphic(playIcon));

        mediaPlayer.setOnReady(() -> onReadyRunnable.accept(this));

        mediaPlayer.setOnEndOfMedia(() -> playButton.setGraphic(playIcon));

        mediaPlayer.currentTimeProperty().addListener((obs, oldv, newv) -> {
            String time = JFXUtilities.formatSeconds(Math.round(newv.toSeconds()));
            timeLabel.setText(time);
        });

        // ---  Configure play button
        playButton.setOnAction((e) -> {
            MediaPlayer.Status status = mediaPlayer.getStatus();

            log.log(System.Logger.Level.DEBUG, "PLAY BUTTON TOGGLED: Current mediaPlayer status = " + status);
            System.out.println(status);
            if (status == MediaPlayer.Status.UNKNOWN ||  status == MediaPlayer.Status.HALTED) {
                // Do nothing
                return;
            }

            if (status == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
            }
            else {
                mediaPlayer.play();
            }

        });

        // --- Configure Scrubber
        scrubber.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            var state = new PlayState(mediaPlayer.getStatus(), mediaPlayer.getCurrentRate());
            prescrubState.set(state);
            mediaPlayer.pause();
        });

        scrubber.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            var state = prescrubState.get();
            if (state != null) {
                if (state.status == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.play();
                    mediaPlayer.setRate(state.rate);
                }
            }
        });
        scrubber.valueProperty().addListener(observable -> {
            if (scrubber.isValueChanging()) {
                Media m = mediaPlayer.getMedia();
                // multiply duration by percentage calculated by slider position
                mediaPlayer.seek(m.getDuration().multiply(scrubber.getValue() / 100D));
            }
            else {
                updateValues();
            }

        });

    }


    public boolean isReady() {
        return ready.get();
    }

    public BooleanProperty readyProperty() {
        return ready;
    }


    public void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    public CompletableFuture<FrameCaptureData> frameCapture(Path target) {

        var future = new CompletableFuture<FrameCaptureData>();
        if (mediaView != null) {
            Platform.runLater(() -> {
                var currentTime = mediaView.getMediaPlayer().getCurrentTime();
                WritableImage image = mediaView.snapshot(new SnapshotParameters(), null);
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
                Runnable r = () -> {
                    log.log(System.Logger.Level.DEBUG, "Saving image to " + target);
                    try {
                        ImageUtil.saveImage(bufferedImage, target.toFile());
                        var currentTimeMillis = Math.round(currentTime.toMillis());
                        var data = new FrameCaptureData(target, currentTimeMillis, bufferedImage);
                        future.complete(data);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                };
                imageWriterExecutor.execute(r);
            });
        }
        else {
            future.completeExceptionally(new RuntimeException("No MediaView exists in the MoviePaneController"));
        }

        return future;
    }

    protected void updateValues() {
        if (timeLabel != null && scrubber != null && mediaPlayer != null) {
            Platform.runLater(() -> {
                Duration currentTime = mediaPlayer.getCurrentTime();
                Duration totalTime = mediaPlayer.getMedia().getDuration();
                timeLabel.setText(JFXUtilities.formatSeconds(Math.round(currentTime.toSeconds())));
                scrubber.setDisable(totalTime.isUnknown());
                if (!scrubber.isDisabled() && totalTime.greaterThan(Duration.ZERO) && !scrubber.isValueChanging()) {
                    scrubber.setValue(currentTime.divide(totalTime.toMillis()).toMillis() * 100D);
                }
            });
        }
    }

    public static MoviePaneController newInstance() {
        return JFXUtilities.newInstance(MoviePaneController.class, "/fxml/MoviePane.fxml");
    }
}

