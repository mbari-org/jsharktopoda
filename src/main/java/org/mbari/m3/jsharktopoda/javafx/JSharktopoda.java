package org.mbari.m3.jsharktopoda.javafx;

import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.mbari.m3.jsharktopoda.udp.UdpIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Brian Schlining
 * @since 2017-12-06T10:19:00
 */
public class JSharktopoda extends Application {

    private static UdpIO io;
    private static CommandService commandService;
    private static final Logger log = LoggerFactory.getLogger(JSharktopoda.class);

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        io = new UdpIO(port);
        commandService = new CommandService(io.getCommandSubject(), io.getResponseSubject());
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        MaterialIconFactory iconFactory = MaterialIconFactory.get();
        Text powerIcon = iconFactory.createIcon(MaterialIcon.POWER_SETTINGS_NEW, "30px");
        Button powerButton = new Button();
        powerButton.setGraphic(powerIcon);
        powerButton.setOnAction(event -> {
            Platform.exit();
            System.exit(0);
        });

        Text settingsIcon = iconFactory.createIcon(MaterialIcon.SETTINGS, "30px");
        Button settingsButton = new Button();
        settingsButton.setGraphic(settingsIcon);
        settingsButton.setOnAction(event -> {
            // TODO show settings dialog
        });

        VBox vBox = new VBox(powerButton, settingsButton);
        Scene scene = new Scene(vBox);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }
}
