package org.mbari.jsharktopoda.etc.javafx;


import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.mbari.vcr4j.time.Timecode;

import java.time.Duration;
import java.util.ResourceBundle;

/**
 * @author Brian Schlining
 * @since 2016-05-16T14:16:00
 */
public class JFXUtilities {

    public static double FAUX_FRAMERATE = 100D;

    private JFXUtilities() {
        // No instantiation allowed
    }

    public static Timecode jfxDurationToTimecode(javafx.util.Duration jfxDuration) {
        return new Timecode(jfxDuration.toMillis() / 10D, FAUX_FRAMERATE);
    }

    public static Timecode durationToTimecode(java.time.Duration duration) {
        return new Timecode(duration.toMillis() / 10D, FAUX_FRAMERATE);
    }

    public static java.time.Duration timecodeToDuration(Timecode timecode) {
        Timecode completeTimecode = timecode.isComplete() ? timecode :
                new Timecode(timecode.toString(), 100D);
        return Duration.ofMillis(Math.round(completeTimecode.getSeconds() * 1000L));
    }

    public static String formatSeconds(long seconds) {
        return String.format("%02d:%02d", (seconds % 3600) / 60, (seconds % 60));
    }

    /**
     *
     * @param clazz The controller class
     * @param fxmlPath The path to use to look up the fxml file for the controller
     * @param <T> The type of the controller class
     * @return A controller loaded from the FXML file. The i18n bundle will be injected into it.
     */
    public static <T> T newInstance(Class<T> clazz, String fxmlPath, ResourceBundle i18nBundle) {
        FXMLLoader loader = new FXMLLoader(clazz.getResource(fxmlPath), i18nBundle);
        try {
            loader.load();
            return loader.getController();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlPath, e);
        }
    }

    /**
     *
     * @param clazz The controller class
     * @param fxmlPath The path to use to look up the fxml file for the controller
     * @param <T> The type of the controller class
     * @return A controller loaded from the FXML file. The i18n bundle will be injected into it.
     */
    public static <T> T newInstance(Class<T> clazz, String fxmlPath) {
        FXMLLoader loader = new FXMLLoader(clazz.getResource(fxmlPath));
        try {
            loader.load();
            return loader.getController();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlPath, e);
        }
    }

    public static void runOnFXThread(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        }
        else {
            Platform.runLater(r);
        }
    }
}
