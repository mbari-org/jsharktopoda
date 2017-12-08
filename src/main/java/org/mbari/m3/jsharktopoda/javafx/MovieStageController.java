package org.mbari.m3.jsharktopoda.javafx;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.mbari.m3.jsharktopoda.FrameCaptureService;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @author Brian Schlining
 * @since 2017-11-07T15:02:00
 */
public class MovieStageController implements FrameCaptureService {

    private MoviePaneController controller;
    private final String movieLocation;
    private final Consumer<MoviePaneController> onReadyRunnable;
    private Stage stage;
    private BooleanProperty ready = new SimpleBooleanProperty(false);


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
            stage = new Stage();
            stage.setScene(scene);
            stage.setOnCloseRequest(evt -> stage.close());
            scene.heightProperty().addListener(obs -> resize(scene, root));
        });
    }

    private void resize(Scene scene, Pane pane) {
        pane.setPrefWidth(scene.getWidth());
        pane.setPrefHeight(scene.getHeight());
    }

    public Stage getStage() {
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

    public BufferedImage frameCapture(File target) throws IOException, InterruptedException, TimeoutException, ExecutionException {
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
                });
                moviePaneController.readyProperty().setValue(true);
            });

        };
        return new MovieStageController(movieLocation, fn);
    }
}


