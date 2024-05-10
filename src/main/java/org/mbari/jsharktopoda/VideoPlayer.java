package org.mbari.jsharktopoda;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * @author Brian Schlining
 * @since 2017-12-05T09:52:00
 */
public class VideoPlayer extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {}

    public static void main(String[] args) {
        String movieLocation = args[0];
        MovieStageController controller = MovieStageController.newInstance(movieLocation);
        controller.readyProperty().addListener((obs, oldv, newv) -> controller.getStage().show());
    }
}
