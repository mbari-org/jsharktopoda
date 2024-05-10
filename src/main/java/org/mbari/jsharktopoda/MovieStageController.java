package org.mbari.jsharktopoda;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.mbari.jsharktopoda.etc.javafx.JFXUtilities;
import org.mbari.jsharktopoda.etc.vcr4j.FrameCaptureData;

import java.awt.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author Brian Schlining
 * @since 2017-11-07T15:02:00
 */
public class MovieStageController implements FrameCaptureService {

    private static final System.Logger log = System.getLogger(MovieStageController.class.getName());

    private MoviePaneController controller;
    private final String movieLocation;
    private final Consumer<MoviePaneController> onReadyRunnable;

    private final ObjectProperty<Stage> stage = new SimpleObjectProperty<>();
    private final BooleanProperty ready = new SimpleBooleanProperty(false);


    public MovieStageController(String movieLocation, Consumer<MoviePaneController> onReadyRunnable) {
        this.movieLocation = movieLocation;
        this.onReadyRunnable = onReadyRunnable.andThen(moviePaneController -> ready.setValue(true));
        controller = MoviePaneController.newInstance();
        init();
    }

    private void init() {
        Platform.runLater(() -> {
            controller.setMediaLocation(movieLocation, onReadyRunnable);
            AnchorPane root = controller.getRoot();
            Scene scene = new Scene(root);
            scene.getStylesheets().add("/css/MoviePane.css");
            var newStage = new Stage();
            newStage.setScene(scene);
            newStage.setOnCloseRequest(evt -> newStage.close());
            scene.heightProperty().addListener(obs -> resize(scene, root));
            stage.set(newStage);
        });
    }

    private void resize(Scene scene, Pane pane) {
        pane.setPrefWidth(scene.getWidth());
        pane.setPrefHeight(scene.getHeight());
    }

    public Stage getStage() {
        return stage.get();
    }

    public ObjectProperty<Stage> stageProperty() {
        return stage;
    }

    public boolean isReady() {
        return ready.get();
    }

    public BooleanProperty readyProperty() {
        return ready;
    }

    /**
     * Retrieve the source URI of the media
     * @return the media source URI as a String
     */
    public String getSource() {
        return controller.getMediaPlayer().getMedia().getSource();
    }

    public void close() {
        Platform.runLater(() -> {
            getStage().close();
            controller.getMediaPlayer().dispose();
        });

    }

    public MediaView getMediaView() {
        return controller.getMediaView();
    }

    public MediaPlayer getMediaPlayer() {
        return controller.getMediaPlayer();
    }

    public CompletableFuture<FrameCaptureData> frameCapture(Path target) {
        return controller.frameCapture(target);
    }

    /**
     * Should be called from a JavaFX application!!
     */
    public static MovieStageController newInstance(String movieLocation) {
        Consumer<MoviePaneController> fn = moviePaneController -> {
            Platform.runLater(() -> {
                moviePaneController.updateValues();
                Duration totalTime = moviePaneController.getMediaPlayer()
                        .getMedia()
                        .getDuration();
                moviePaneController.getMaxTimeLabel()
                        .setText(JFXUtilities.formatSeconds(Math.round(totalTime.toSeconds())));
                moviePaneController.readyProperty().addListener(obs -> {
                    Media media = moviePaneController.getMediaPlayer().getMedia();
                    MediaView mediaView = moviePaneController.getMediaView();
                    Window root = mediaView.getScene().getWindow();
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    double width = media.getWidth() > screenSize.getWidth() ?
                            screenSize.getWidth() : media.getWidth();
                    double height = media.getHeight() > screenSize.getHeight() ?
                            screenSize.getHeight() : media.getHeight();
                    root.setWidth(width);
                    root.setHeight(height);

                    if (log.isLoggable(System.Logger.Level.DEBUG)) {
                        log.log(System.Logger.Level.DEBUG, "Media source: " + media.getSource());
                        log.log(System.Logger.Level.DEBUG, "Media duration: " + totalTime);
                        media.getTracks()
                            .forEach(t -> {
                                var metadata = t.getMetadata();
                                for (var e: metadata.entrySet()) {
                                    log.log(System.Logger.Level.DEBUG, "Media metadata: " + e.getKey() + " - " + e.getValue());
                                }
                            });
                    }
                });
                moviePaneController.readyProperty().setValue(true);
            });

        };
        return new MovieStageController(movieLocation, fn);
    }
}


