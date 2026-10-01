package org.mbari.jsharktopoda;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.mbari.jsharktopoda.etc.javafx.MaterialIcons;
import org.mbari.jsharktopoda.etc.vcr4j.SharkVideoController;
import org.mbari.jsharktopoda.localization.LocalizationCommandRouter;
import org.mbari.jsharktopoda.localization.LocalizationRecord;
import org.mbari.jsharktopoda.localization.LocalizationSettings;
import org.mbari.jsharktopoda.localization.LocalizationTarget;
import org.mbari.jsharktopoda.localization.TimeWindow;
import org.mbari.jsharktopoda.localization.RemoteNotifier;
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
    private volatile VideoControl videoControl;
    private LocalizationCommandRouter localizationRouter;

    private SharkVideoController videoController;
    private int currentPort = 8800;
    private final System.Logger log = System.getLogger(JSharktopoda.class.getName());
    private FileChooser fileChooser;
    private TextInputDialog urlDialog;
    private Dialog<ButtonType> settingsDialog;
    private TextField portEditor;
    private TextField conceptEditor;
    private TextField windowEditor;
    private ColorPicker selectedPicker;
    private ColorPicker unselectedPicker;
    private ColorPicker editPicker;
    // what the pickers will apply on OK: null means each localization's own color
    private Color pendingSelected;
    private Color pendingUnselected;
    private Color pendingEdit = LocalizationSettings.DEFAULT_EDIT_COLOR;
    private ResourceBundle i18n;
    private final Class prefNodeKey = getClass();
    private static final String CONCEPT_PREF = "defaultConcept";
    private static final String WINDOW_PREF = "timeWindowMillis";
    private static final String SELECTED_COLOR_PREF = "selectedColor";
    private static final String UNSELECTED_COLOR_PREF = "unselectedColor";
    private static final String EDIT_COLOR_PREF = "editColor";
    private static final Color DEFAULT_PICKER_COLOR = Color.web(LocalizationRecord.DEFAULT_COLOR);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        i18n = ResourceBundle.getBundle("i18n", Locale.getDefault());
        LocalizationSettings.setDefaultConcept(
                Preferences.userNodeForPackage(prefNodeKey).get(CONCEPT_PREF, null));
        LocalizationSettings.setSelectedColor(
                LocalizationSettings.fromText(Preferences.userNodeForPackage(prefNodeKey).get(SELECTED_COLOR_PREF, null)));
        LocalizationSettings.setUnselectedColor(
                LocalizationSettings.fromText(Preferences.userNodeForPackage(prefNodeKey).get(UNSELECTED_COLOR_PREF, null)));
        LocalizationSettings.setEditColor(
                LocalizationSettings.fromText(Preferences.userNodeForPackage(prefNodeKey).get(EDIT_COLOR_PREF, null)));
        LocalizationSettings.setTimeWindowMillis(
                Preferences.userNodeForPackage(prefNodeKey).getLong(WINDOW_PREF, TimeWindow.DEFAULT_MILLIS));
        var notifier = RemoteNotifier.forConnection(() -> videoControl == null
                ? Optional.empty()
                : videoControl.getLifeCycle().get());
        videoController = new SharkVideoController(notifier);

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
            var dialog = getSettingsDialog();
            portEditor.setText(String.valueOf(currentPort));
            conceptEditor.setText(LocalizationSettings.getDefaultConcept());
            windowEditor.setText(String.valueOf(LocalizationSettings.getTimeWindowMillis()));
            pendingSelected = LocalizationSettings.getSelectedColor();
            pendingUnselected = LocalizationSettings.getUnselectedColor();
            pendingEdit = LocalizationSettings.getEditColor();
            showPendingColors();
            dialog.showAndWait()
                    .filter(bt -> bt == ButtonType.OK)
                    .ifPresent(bt -> applySettings());
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
        currentPort = port;
        if (localizationRouter != null) {
            localizationRouter.close();
        }
        if (videoControl != null) {
            videoControl.close();
        }
        videoControl = new VideoControl.Builder()
                .port(port)
                .videoController(videoController)
                .build()
                .get();
        localizationRouter = new LocalizationCommandRouter(
                videoControl.getRequestHandler().getLocalizationsCmdObservable(),
                id -> videoController.findLocalizations(id).map(vl -> (LocalizationTarget) vl),
                Platform::runLater);

//        commandService = new CommandService(io.getCommandSubject(), io.getResponseSubject());
        Preferences prefs = Preferences.userNodeForPackage(prefNodeKey);
        prefs.putInt("port", port);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            log.log(System.Logger.Level.WARNING, "Failed to save port number to prefs", e);
        }
    }

    private void applySettings() {
        String portText = portEditor.getText();
        if (!portText.isBlank()) {
            int port = Integer.parseInt(portText);
            if (port != currentPort) {
                setPort(port);
            }
        }
        LocalizationSettings.setDefaultConcept(conceptEditor.getText());
        String windowText = windowEditor.getText();
        if (!windowText.isBlank()) {
            try {
                LocalizationSettings.setTimeWindowMillis(Long.parseLong(windowText));
            } catch (NumberFormatException e) {
                LocalizationSettings.setTimeWindowMillis(0);
            }
        } else {
            LocalizationSettings.setTimeWindowMillis(0);
        }
        LocalizationSettings.setSelectedColor(pendingSelected);
        LocalizationSettings.setUnselectedColor(pendingUnselected);
        LocalizationSettings.setEditColor(pendingEdit);
        Preferences prefs = Preferences.userNodeForPackage(prefNodeKey);
        putOrRemove(prefs, SELECTED_COLOR_PREF, LocalizationSettings.toText(pendingSelected));
        putOrRemove(prefs, UNSELECTED_COLOR_PREF, LocalizationSettings.toText(pendingUnselected));
        prefs.put(EDIT_COLOR_PREF, LocalizationSettings.toText(LocalizationSettings.getEditColor()));
        prefs.put(CONCEPT_PREF, LocalizationSettings.getDefaultConcept());
        prefs.putLong(WINDOW_PREF, LocalizationSettings.getTimeWindowMillis());
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            log.log(System.Logger.Level.WARNING, "Failed to save default concept to prefs", e);
        }
    }

    private static void putOrRemove(Preferences prefs, String key, String value) {
        if (value == null) {
            prefs.remove(key);
        }
        else {
            prefs.put(key, value);
        }
    }

    private void showPendingColors() {
        selectedPicker.setValue(pendingSelected != null ? pendingSelected : DEFAULT_PICKER_COLOR);
        unselectedPicker.setValue(pendingUnselected != null ? pendingUnselected : DEFAULT_PICKER_COLOR);
        editPicker.setValue(pendingEdit);
    }

    private Dialog<ButtonType> getSettingsDialog() {
        if (settingsDialog == null) {
            settingsDialog = new Dialog<>();
            settingsDialog.setTitle(i18n.getString("settings.title"));
            settingsDialog.setHeaderText(i18n.getString("settings.header"));
            settingsDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            portEditor = new TextField();
            portEditor.setPromptText(i18n.getString("portchooser.prompt"));
            // Accept numbers only
            portEditor.textProperty().addListener((obs, oldv, newv) -> {
                if (!newv.matches("\\d*")) {
                    portEditor.setText(newv.replaceAll("[^\\d]", ""));
                }
            });

            conceptEditor = new TextField();
            conceptEditor.setPromptText(LocalizationSettings.FALLBACK_CONCEPT);

            windowEditor = new TextField();
            windowEditor.setPromptText(String.valueOf(TimeWindow.DEFAULT_MILLIS));
            windowEditor.textProperty().addListener((obs, oldv, newv) -> {
                if (!newv.matches("\\d{0,12}")) {
                    windowEditor.setText(oldv);
                }
            });

            // setValue() in showPendingColors() doesn't fire onAction, so only a user's pick is recorded
            selectedPicker = new ColorPicker(DEFAULT_PICKER_COLOR);
            selectedPicker.setOnAction(e -> pendingSelected = selectedPicker.getValue());
            unselectedPicker = new ColorPicker(DEFAULT_PICKER_COLOR);
            unselectedPicker.setOnAction(e -> pendingUnselected = unselectedPicker.getValue());
            editPicker = new ColorPicker(LocalizationSettings.DEFAULT_EDIT_COLOR);
            editPicker.setOnAction(e -> pendingEdit = editPicker.getValue());
            var resetButton = new Button(i18n.getString("settings.resetcolors"));
            resetButton.setTooltip(new Tooltip(i18n.getString("settings.resetcolors.tooltip")));
            resetButton.setOnAction(e -> {
                pendingSelected = null;
                pendingUnselected = null;
                pendingEdit = LocalizationSettings.DEFAULT_EDIT_COLOR;
                showPendingColors();
            });

            var grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.addRow(0, new Label(i18n.getString("portchooser.content")), portEditor);
            grid.addRow(1, new Label(i18n.getString("settings.concept")), conceptEditor);
            grid.addRow(2, new Label(i18n.getString("settings.window")), windowEditor);
            grid.addRow(3, new Label(i18n.getString("settings.selectedcolor")), selectedPicker);
            grid.addRow(4, new Label(i18n.getString("settings.unselectedcolor")), unselectedPicker);
            grid.addRow(5, new Label(i18n.getString("settings.editcolor")), editPicker);
            grid.add(resetButton, 1, 6);
            settingsDialog.getDialogPane().setContent(grid);
        }
        return settingsDialog;
    }

}
