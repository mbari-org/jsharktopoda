package org.mbari.jsharktopoda;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.mbari.jsharktopoda.etc.javafx.MaterialIcons;
import org.mbari.jsharktopoda.etc.vcr4j.SharkVideoController;
import org.mbari.vcr4j.remote.player.VideoControl;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author Brian Schlining
 * @since 2017-12-06T10:19:00
 */
public class JSharktopoda extends Application {

//    private UdpIO io;
//    private CommandService commandService;
    private VideoControl videoControl;

    private SharkVideoController videoController;
    private final System.Logger log = System.getLogger(JSharktopoda.class.getName());
    private FileChooser fileChooser;
    private TextInputDialog urlDialog;
    private TextInputDialog portDialog;
    private ResourceBundle i18n;
    private final Class prefNodeKey = getClass();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        i18n = ResourceBundle.getBundle("i18n", Locale.getDefault());
        videoController = new SharkVideoController();

        List<String> args = getParameters().getRaw();
        if (args.size() == 1) {
            try {
                int port = Integer.parseInt(args.get(0));
                setPort(port);
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING, "Unable to parse " + args.get(0) + " as a port number");
            }
        } else {
            Preferences prefs = Preferences.userNodeForPackage(prefNodeKey);
            int port = prefs.getInt("port", 8800);
            setPort(port);
        }

        Text powerIcon = MaterialIcons.POWER_SETTINGS_NEW;
        Button powerButton = new Button();
        powerButton.setTooltip(new Tooltip(i18n.getString("app.quit")));
        powerButton.setGraphic(powerIcon);
        powerButton.setOnAction(event -> {
            Platform.exit();
            System.exit(0);
        });

        Text settingsIcon = MaterialIcons.SETTINGS;
        Button settingsButton = new Button();
        settingsButton.setTooltip(new Tooltip(i18n.getString("app.settings")));
        settingsButton.setGraphic(settingsIcon);
        settingsButton.setOnAction(event -> {
            Optional<String> opt = getPortDialog().showAndWait();
            opt.ifPresent(port -> setPort(Integer.parseInt(port)));
        });

        fileChooser = new FileChooser();
        fileChooser.setTitle(i18n.getString("filechooser.title"));
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(i18n.getString("filechooser.filtername"), "*.mp4"));
        Text openFileIcon = MaterialIcons.COMPUTER;
        Button openButton = new Button();
        openButton.setTooltip(new Tooltip(i18n.getString("app.file")));
        openButton.setGraphic(openFileIcon);
        openButton.setOnAction(event -> {
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                try {
                    var url = file.toURI().toURL();
                    var opt = videoController.findControllerByUrl(url);
                    if (opt.isPresent()) {
                        videoController.show(opt.get().getKey());
                    }
                    else {
                        videoController.open(UUID.randomUUID(), url);
                    }
                } catch (MalformedURLException e) {
                    log.log(System.Logger.Level.ERROR, "Unable to open file", e);
                }
            }
        });

        urlDialog = new TextInputDialog();
        urlDialog.getDialogPane().setPrefWidth(600);
        urlDialog.setTitle(i18n.getString("urlchooser.title"));
        urlDialog.setHeaderText(i18n.getString("urlchooser.header"));
        urlDialog.setContentText(i18n.getString("urlchooser.content"));
        urlDialog.getEditor().setPromptText(i18n.getString("urlchooser.prompt"));
        Text openUrlIcon = MaterialIcons.CLOUD;
        Button openUrlButton = new Button();
        openUrlButton.setTooltip(new Tooltip(i18n.getString("app.url")));
        openUrlButton.setGraphic(openUrlIcon);
        openUrlButton.setOnAction(event -> {
            Optional<String> opt = urlDialog.showAndWait();
            opt.ifPresent(urlString -> {
                try {
                    var url = new URL(urlString);
                    var opt2 = videoController.findControllerByUrl(url);
                    if (opt2.isPresent()) {
                        videoController.show(opt2.get().getKey());
                    }
                    else {
                        videoController.open(UUID.randomUUID(), url);
                    }
                } catch (MalformedURLException e) {
                    log.log(System.Logger.Level.ERROR, "Unable to open file", e);
                }
            });
            urlDialog.getEditor().setText(null);
        });

        HBox pane = new HBox(powerButton, settingsButton, openButton, openUrlButton);
        Scene scene = new Scene(pane);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private void setPort(int port) {
        if (videoControl != null) {
            videoControl.close();
        }
        videoControl = new VideoControl.Builder()
                .port(port)
                .videoController(videoController)
                .build()
                .get();

//        commandService = new CommandService(io.getCommandSubject(), io.getResponseSubject());
        getPortDialog().getEditor().setText(port + "");
        Preferences prefs = Preferences.userNodeForPackage(prefNodeKey);
        prefs.putInt("port", port);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            log.log(System.Logger.Level.WARNING, "Failed to save port number to prefs", e);
        }
    }

    private TextInputDialog getPortDialog() {
        if (portDialog == null) {
            portDialog = new TextInputDialog();
            portDialog.setTitle(i18n.getString("portchooser.title"));
            portDialog.setHeaderText(i18n.getString("portchooser.header"));
            portDialog.setContentText(i18n.getString("portchooser.content"));
            TextField portEditor = portDialog.getEditor();
            portEditor.setPromptText(i18n.getString("portchooser.prompt"));
            // Accept numbers only
            portEditor.textProperty().addListener((obs, oldv, newv) -> {
                if (!newv.matches("\\d*")) {
                    portEditor.setText(newv.replaceAll("[^\\d]", ""));
                }
            });
        }
        return portDialog;
    }

}
