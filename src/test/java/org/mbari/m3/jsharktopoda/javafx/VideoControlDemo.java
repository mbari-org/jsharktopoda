package org.mbari.m3.jsharktopoda.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * @author Brian Schlining
 * @since 2016-08-16T16:41:00
 */
public class VideoControlDemo extends Application {


    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene scene = new Scene(new VideoControls());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
