package org.mbari.m3.jsharktopoda.javafx;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Created by brian on 10/29/15.
 */
public class JMovieFrame extends JFrame {

    private final JFXPanel panel = new JFXPanel();
    private volatile MoviePaneController controller;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public JMovieFrame() throws HeadlessException {
        setLayout(new BorderLayout());
        add(panel);
        setSize(480, 320);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (controller != null) {
                    MediaView mediaView = controller.getMediaView();
                    mediaView.setFitHeight(getHeight());
                    mediaView.setFitWidth(getWidth());
                }
            }
        });
    }

    public CompletableFuture<MoviePaneController> setMediaLocation(final String location,
                                                                   Consumer<MoviePaneController> onReadyRunnable) {
        CompletableFuture<MoviePaneController> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                disposeJFXNodes();
                future.complete(initJFXNodes(location, onReadyRunnable));
            } catch (IOException e) {
                log.error("Failed to initialize JavaFX scene", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void disposeJFXNodes() {
        if (controller!= null) {
            panel.setScene(new Scene(new Pane(), Color.BLACK));
            //controller.dispose();
            controller = null;
        }
    }

    private MoviePaneController initJFXNodes(final String location, Consumer<MoviePaneController> onReadyRunnable) throws IOException {
        URL controllerLocation = getClass().getResource("/fxml/MoviePane.fxml");
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(controllerLocation);
        loader.setBuilderFactory(new JavaFXBuilderFactory());

        Parent root = loader.load(controllerLocation.openStream());
        controller = loader.getController();
        controller.setMediaLocation(location, onReadyRunnable);

        Scene scene = new Scene(root);
        scene.getStylesheets().add("/styles/JFXMovieFrame.css");
        panel.setScene(scene);

        MediaView mediaView = controller.getMediaView();
        mediaView.setFitWidth(getWidth());
        mediaView.setFitHeight(getHeight());
        return controller;
    }


}
