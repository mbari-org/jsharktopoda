package org.mbari.m3.jsharktopoda.javafx;

import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
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
import java.net.URL;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * @author Brian Schlining
 * @since 2017-12-06T10:19:00
 */
public class JSharktopoda extends Application {

    private UdpIO io;
    private CommandService commandService;
    private final Logger log = LoggerFactory.getLogger(JSharktopoda.class);
    private FileChooser fileChooser;
    private TextInputDialog urlDialog;
    private TextInputDialog portDialog;
    private ResourceBundle i18n;

    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        i18n = ResourceBundle.getBundle("i18n", Locale.getDefault());

        List<String> args = getParameters().getRaw();
        if (args.size() == 1) {
            try {
                int port = Integer.parseInt(args.get(0));
                setPort(port);
            }
            catch (Exception e) {
                log.warn("Unable to parse " + args.get(0) + " as a port number");
            }
        }
        else {
            Preferences prefs = Preferences.userNodeForPackage(getClass());
            int port = prefs.getInt("port", 8800);
            setPort(port);
        }

        MaterialIconFactory iconFactory = MaterialIconFactory.get();
        Text powerIcon = iconFactory.createIcon(MaterialIcon.POWER_SETTINGS_NEW, "30px");
        Button powerButton = new Button();
        powerButton.setTooltip(new Tooltip(i18n.getString("app.quit")));
        powerButton.setGraphic(powerIcon);
        powerButton.setOnAction(event -> {
            Platform.exit();
            System.exit(0);
        });

        Text settingsIcon = iconFactory.createIcon(MaterialIcon.SETTINGS, "30px");
        Button settingsButton = new Button();
        settingsButton.setTooltip(new Tooltip(i18n.getString("app.settings")));
        settingsButton.setGraphic(settingsIcon);
        settingsButton.setOnAction(event -> {
            Optional<String> opt = getPortDialog().showAndWait();
            opt.ifPresent(port -> {

            });
        });

        fileChooser = new FileChooser();
        fileChooser.setTitle(i18n.getString("filechooser.title"));
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(i18n.getString("filechooser.filtername"), "*.mp4"));
        Text openFileIcon = iconFactory.createIcon(MaterialIcon.COMPUTER, "30px");
        Button openButton = new Button();
        openButton.setTooltip(new Tooltip(i18n.getString("app.file")));
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

        urlDialog = new TextInputDialog();
        urlDialog.getDialogPane().setPrefWidth(600);
        urlDialog.setTitle(i18n.getString("urlchooser.title"));
        urlDialog.setHeaderText(i18n.getString("urlchooser.header"));
        urlDialog.setContentText(i18n.getString("urlchooser.content"));
        urlDialog.getEditor().setPromptText(i18n.getString("urlchooser.prompt"));
        Text openUrlIcon = iconFactory.createIcon(MaterialIcon.CLOUD, "30px");
        Button openUrlButton = new Button();
        openUrlButton.setTooltip(new Tooltip(i18n.getString("app.url")));
        openUrlButton.setGraphic(openUrlIcon);
        openUrlButton.setOnAction(event -> {
            Optional<String> opt = urlDialog.showAndWait();
            opt.ifPresent(url -> {
                GenericCommand cmd = new GenericCommand();
                cmd.setCommand("open");
                cmd.setUuid(UUID.randomUUID());
                try {
                    cmd.setUrl(new URL(url));
                    io.getCommandSubject().onNext(cmd);
                }
                catch (MalformedURLException e) {
                    log.error("Unable to open file", e);
                }
            });
            urlDialog.getEditor().setText(null);
        });



        VBox vBox = new VBox(powerButton, settingsButton, openButton, openUrlButton);
        Scene scene = new Scene(vBox);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private void setPort(int port) {
        if (io != null) {
            io.close();
        }
        io = new UdpIO(port);
        commandService = new CommandService(io.getCommandSubject(), io.getResponseSubject());
        getPortDialog().getEditor().setText(port + "");
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.putInt("port", port);
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
            portEditor.textProperty().addListener((obs, oldv, newv) ->{
                if (!newv.matches("\\d*")) {
                    portEditor.setText(newv.replaceAll("[^\\d]", ""));
                }
            });
        }
        return portDialog;
    }


}
