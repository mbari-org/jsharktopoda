package org.mbari.m3.jsharktopoda.javafx;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * @author Brian Schlining
 * @since 2017-11-07T15:02:00
 */
public class MovieStageController {

    private BorderPane borderPane;
    private MoviePaneController controller;
    private final String movieLocation;
    private final Consumer<MoviePaneController> onReadyRunnable;

    public MovieStageController(String movieLocation, Consumer<MoviePaneController> onReadyRunnable) {
        this.movieLocation = movieLocation;
        this.onReadyRunnable = onReadyRunnable;
        controller = MoviePaneController.newInstance();
        init();
    }

    private void init() {
        Platform.runLater(() -> {

            controller.setMediaLocation(movieLocation, onReadyRunnable);
            AnchorPane root = controller.getRoot();
            Scene scene = new Scene(root);
            scene.getStylesheets().add("/styles/JFXMovieFrame.css");
            Stage stage = new Stage();
            stage.setScene(scene);
            scene.heightProperty().addListener(obs -> resize(scene, root));
        });
    }

    private void resize(Scene scene, Pane pane) {
        pane.setPrefWidth(scene.getWidth());
        pane.setPrefHeight(scene.getHeight());
    }

    public BorderPane getBorderPane() {
        return borderPane;
    }
}
