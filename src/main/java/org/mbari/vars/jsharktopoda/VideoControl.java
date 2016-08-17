package org.mbari.vars.jsharktopoda;

import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * @author Brian Schlining
 * @since 2016-08-16T15:28:00
 */
public class VideoControl extends Pane {


    Slider volumeSlider;
    Slider scrubber;
    Button rewindButton;
    Button fastForwardButton;
    Button playButon;
    Label elapsedTimeLabel = new Label("00:00");
    Label durationLabel = new Label("00:00");
    Text volumeUpIcon = MaterialIconFactory.get().createIcon(MaterialIcon.VOLUME_UP, "2em");
    Text volumeDownIcon = MaterialIconFactory.get().createIcon(MaterialIcon.VOLUME_DOWN, "2em");

    public VideoControl() {
        // orange: "0xBF360C"
        Color color = Color.ANTIQUEWHITE;
        setStyle("-fx-background-color: #263238;");
        setPrefSize(400, 100);
        volumeUpIcon.relocate(80, 15);
        volumeUpIcon.setFill(color);
        volumeDownIcon.relocate(5, 15);
        volumeDownIcon.setFill(color);


        getChildren().addAll(volumeDownIcon, volumeUpIcon);
    }

}
