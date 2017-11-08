package org.mbari.m3.jsharktopoda.javafx;

import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

/**
 *
 * @author Jens Deters
 */
public class MaterialIconsDemoApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FlowPane iconsPane = new FlowPane(3,3);
        for (MaterialIcon icon : MaterialIcon.values()) {
            iconsPane.getChildren().add(MaterialIconFactory.get().createIcon(icon, "3em"));
        }
        Scene scene = new Scene(new ScrollPane(iconsPane), 500, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("FontAwesomeFX: MaterialIcons Demo: " + MaterialIcon.values().length + " Icons");
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}