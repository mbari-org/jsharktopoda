package org.mbari.m3.jsharktopoda.javafx;


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
import mbarix4j.awt.image.ImageUtilities;
import org.mbari.m3.jsharktopoda.FrameCaptureService;
import org.mbari.m3.jsharktopoda.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.*;
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

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Text playIcon;
    private Text pauseIcon;

    private final ExecutorService imageWriterExecutor = Executors.newCachedThreadPool();


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        playButton.setText(null);
        playIcon = MaterialIcons.PLAY_ARROW_50PX;
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
            log.debug("PLAY BUTTON TOGGLED: Current mediaPlayer status = " + status);
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

    public BufferedImage frameCapture(File target) throws IOException, InterruptedException, TimeoutException, ExecutionException {

        BufferedImage bufferedImage = null;
        if (mediaView != null) {
            Platform.runLater(() -> {
                WritableImage image = mediaView.snapshot(new SnapshotParameters(), null);
                Runnable r = () -> {
                    log.debug("Saving image to " + target.getAbsolutePath());
                    try {
                        ImageUtilities.saveImage(SwingFXUtils.fromFXImage(image, null), target);
                    } catch (IOException e) {
                        // TODO fix exception handling
                        System.out.println("Failed to write " + target.getAbsolutePath());
                    }
                };
                imageWriterExecutor.execute(r);
            });

            /*
                We are writing the image asynchronously (i.e. Platform.runLater()). Since we're not using any RX
                frameworks, we have to poll the image to see if it's done being written. If we try to read it while
                it's in a partially written state then we will get:

                java.lang.IndexOutOfBoundsException: null
                    at java.io.RandomAccessFile.readBytes(Native Method) ~[na:1.8.0_05]
                    at java.io.RandomAccessFile.read(RandomAccessFile.java:349) ~[na:1.8.0_05]
                    at javax.imageio.stream.FileImageInputStream.read(FileImageInputStream.java:117) ~[na:1.8.0_05]
                    at ...

             */
            java.time.Duration timeout = java.time.Duration.ofSeconds(8);
            CompletableFuture<BufferedImage> imageFuture = org.mbari.m3.jsharktopoda.util.ImageUtilities.readImageAsync(target, timeout);
            bufferedImage = imageFuture.get(timeout.getSeconds(), TimeUnit.SECONDS);

            if (bufferedImage == null) {
                // TODO report error
                //EventBus.publish(Lookup.TOPIC_NONFATAL_ERROR, failure);
            }


        }

        return bufferedImage;
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

