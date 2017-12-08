package org.mbari.m3.jsharktopoda.javafx;

import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.mbari.m3.jsharktopoda.udp.GenericCommand;
import org.mbari.m3.jsharktopoda.udp.UdpIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.util.UUID;

/**
 * @author Brian Schlining
 * @since 2017-12-06T10:19:00
 */
public class JSharktopoda extends Application {

    private static UdpIO io;
    private static CommandService commandService;
    private static final Logger log = LoggerFactory.getLogger(JSharktopoda.class);
    private FileChooser fileChooser;

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

        fileChooser = new FileChooser();
        fileChooser.setTitle("Open Movie");
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Video files", "*.mp4"));
        Text openFileIcon = iconFactory.createIcon(MaterialIcon.PERSONAL_VIDEO, "30px");
        Button openButton = new Button();
        openButton.setGraphic(openFileIcon);
        openButton.setOnAction(event -> {
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                GenericCommand cmd = new GenericCommand();
                cmd.setCommand("open");
                cmd.setUuid(UUID.randomUUID());
                try {
                    cmd.setUrl(file.toURI().toURL());
                    io.getCommandSubject().onNext(cmd);
                }
                catch (MalformedURLException e) {
                    log.error("Unable to open file", e);
                }
            }
        });


        HBox pane = new HBox(powerButton, settingsButton, openButton);
        Scene scene = new Scene(pane);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }
}
